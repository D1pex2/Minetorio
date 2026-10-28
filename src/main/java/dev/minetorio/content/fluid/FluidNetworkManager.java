package dev.minetorio.content.fluid;

import dev.minetorio.core.alt.AltSync;
import dev.minetorio.core.fluid.FluidGauge;
import dev.minetorio.core.fluid.FluidNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The fluid system of one server level: a {@link FluidNetwork} of the loaded pipes, underground
 * pipes and tanks, plus the loaded {@link FluidMachine}s. In memory only (like the power grids):
 * nodes save their share of their segment ({@link FluidNode}) and re-register when loaded.
 *
 * <p><b>Changes</b> are queued ({@link #queueNode}, {@link #registerMachine}) and applied at the
 * end of the tick ({@link LevelTickEvent.Post}), after every block was placed: a queued position
 * gets its node added or removed (block entity present or not) and its links set from the block
 * states ({@link FluidConnections#desiredLinks}); then, if anything changed, every machine's ports
 * are re-attached. So placing, breaking, toggling a pipe side or loading a chunk costs work on
 * the affected segments only, and an idle factory costs one {@link FluidNetwork#tick} per tick
 * (per segment with machines, never per pipe).
 *
 * <p>Every {@value AltSync#PERIOD} ticks: segments whose {@link FluidGauge} changed send block
 * updates to their nodes (alt mode), and a player looking at a pipe or tank gets the segment's
 * contents and flow in the action bar.
 */
public final class FluidNetworkManager {
    /** Server thread only; entries are removed when their level unloads. */
    private static final Map<ServerLevel, FluidNetworkManager> MANAGERS = new HashMap<>();
    /** Ticks a hover line stays away after another fluid message (e.g. a side toggle). */
    private static final int HOVER_QUIET_TICKS = 40;

    private final ServerLevel level;
    private final FluidNetwork network = new FluidNetwork();
    private final Set<BlockPos> queue = new LinkedHashSet<>();
    private final Set<BlockPos> machines = new LinkedHashSet<>();
    private final Map<Long, Integer> nodeChunks = new HashMap<>();
    private Map<FluidNetwork.Segment, FluidGauge> sentGauges = new IdentityHashMap<>();
    private final Map<BlockPos, FluidGauge> sentMachineGauges = new HashMap<>();
    private final Map<UUID, Long> hoverQuietUntil = new HashMap<>();
    private boolean attachDirty;

    private FluidNetworkManager(ServerLevel level) {
        this.level = level;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FluidNetworkManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener(FluidNetworkManager::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(FluidNetworkManager::onLevelSave);
        NeoForge.EVENT_BUS.addListener(FluidNetworkManager::onChunkUnload);
    }

    public static FluidNetworkManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, FluidNetworkManager::new);
    }

    /** The level's manager if it exists; never creates one. */
    @Nullable
    public static FluidNetworkManager getIfPresent(ServerLevel level) {
        return MANAGERS.get(level);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        FluidNetworkManager m = MANAGERS.get(server);
        if (m != null) m.tick();
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel server) MANAGERS.remove(server);
    }

    /** Chunks holding nodes are saved with every level save: the shares change without the block entities knowing. */
    private static void onLevelSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        FluidNetworkManager m = MANAGERS.get(server);
        if (m == null) return;
        for (long c : m.nodeChunks.keySet()) {
            ChunkPos p = new ChunkPos(c);
            if (server.hasChunk(p.x, p.z)) server.getChunk(p.x, p.z).setUnsaved(true);
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        FluidNetworkManager m = MANAGERS.get(server);
        ChunkAccess chunk = event.getChunk();
        if (m != null && m.nodeChunks.containsKey(chunk.getPos().toLong())) chunk.setUnsaved(true);
    }

    public FluidNetwork network() { return network; }

    // --- registration --------------------------------------------------------------------------------

    /** The node at {@code pos} was loaded, removed or changed its connections: re-evaluated at the end of the tick. */
    public void queueNode(BlockPos pos) {
        queue.add(pos.immutable());
    }

    public void registerMachine(BlockPos controller) {
        machines.add(controller.immutable());
        attachDirty = true;
    }

    public void unregisterMachine(BlockPos controller) {
        if (machines.remove(controller)) attachDirty = true;
    }

    /** Applies queued node changes now (the tick does it anyway; tests and placement checks may call it). */
    public void flush() {
        while (!queue.isEmpty()) flushBatch();
    }

    /**
     * One batch of the queue. Nodes linked to a present node that are not registered yet (placed
     * this tick: their block entities register only before the next block entity tick) are queued
     * too, so a query right after placing sees the whole connected line.
     */
    private void flushBatch() {
        List<BlockPos> batch = new ArrayList<>(queue);
        queue.clear();
        List<BlockPos> present = new ArrayList<>();
        for (BlockPos pos : batch) {
            long key = pos.asLong();
            BlockEntity be = level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
            if (be instanceof FluidNodeEntity n && !be.isRemoved()) {
                if (!network.has(key)) {
                    FluidNode node = n.fluidNode();
                    network.addNode(key, node.capacity(), node.takeSavedFluid(), node.takeSavedAmount());
                    node.forgetSaved();
                    nodeChunks.merge(ChunkPos.asLong(pos), 1, Integer::sum);
                }
                present.add(pos);
            } else if (network.has(key)) {
                network.removeNode(key);
                nodeChunks.computeIfPresent(ChunkPos.asLong(pos), (k, v) -> v > 1 ? v - 1 : null);
            }
        }
        for (BlockPos pos : present) {
            Set<Long> desired = FluidConnections.desiredLinks(level, pos);
            network.setLinks(pos.asLong(), desired);
            for (long k : desired) {
                if (!network.has(k)) queueIfNode(BlockPos.of(k));
            }
        }
        attachDirty = true;
    }

    /** Queues {@code pos} if a node block entity is there that is not registered yet. */
    private void queueIfNode(BlockPos pos) {
        if (!network.has(pos.asLong()) && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof FluidNodeEntity
                && !level.getBlockEntity(pos).isRemoved()) {
            queue.add(pos.immutable());
        }
    }

    // --- queries -------------------------------------------------------------------------------------

    /** Segment of the node at {@code pos} (a tank: any of its cells), or null. */
    @Nullable
    public FluidNetwork.Segment segmentAt(BlockPos pos) {
        BlockPos node = level.isLoaded(pos) ? FluidConnections.nodeAt(level, pos) : null;
        if (node == null) return null;
        queueIfNode(node);
        flush();
        return network.segment(node.asLong());
    }

    /**
     * Fluids a new connection to the given nodes would bring together: refuse it if there are two
     * or more ({@link #mixMessage}).
     */
    public Set<String> fluidsOf(Iterable<BlockPos> nodes) {
        for (BlockPos p : nodes) queueIfNode(p);
        flush();
        List<Long> keys = new ArrayList<>();
        for (BlockPos p : nodes) keys.add(p.asLong());
        return network.fluidsOf(keys);
    }

    /** "Would mix water with steam" (action bar). */
    public static Component mixMessage(Set<String> fluids) {
        List<String> f = new ArrayList<>(fluids);
        return Component.translatable("minetorio.fluid.would_mix", FluidText.name(f.get(0)), FluidText.name(f.get(1)));
    }

    /** Shows {@code message} in {@code player}'s action bar and keeps the hover line away for a moment. */
    public void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
        hoverQuietUntil.put(player.getUUID(), level.getGameTime() + HOVER_QUIET_TICKS);
    }

    // --- ticking -------------------------------------------------------------------------------------

    private void tick() {
        flush();
        if (attachDirty) rebuildAttachments();
        network.tick();
        long time = level.getGameTime();
        if (time % AltSync.PERIOD == 0) syncGauges();
        if (time % AltSync.PERIOD == 5) hover(time);
    }

    private void rebuildAttachments() {
        attachDirty = false;
        List<FluidNetwork.Attachment> attachments = new ArrayList<>();
        List<FluidNetwork.Contact> contacts = new ArrayList<>();
        for (BlockPos pos : new ArrayList<>(machines)) {
            if (!level.isLoaded(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof FluidMachine machine)) {
                machines.remove(pos);
                continue;
            }
            for (FluidPorts.Port port : FluidPorts.ports(level, pos)) {
                FluidConnections.End end = FluidConnections.endAt(level, port.neighbor(), port.side().getOpposite());
                if (end instanceof FluidConnections.NodeEnd n) {
                    if (network.has(n.pos().asLong())) attachments.add(new FluidNetwork.Attachment(n.pos().asLong(), port(machine, port)));
                } else if (end instanceof FluidConnections.PortEnd other && pos.compareTo(other.port().controller()) < 0
                        && level.getBlockEntity(other.port().controller()) instanceof FluidMachine otherMachine) {
                    contacts.add(new FluidNetwork.Contact(port(machine, port), port(otherMachine, other.port())));
                }
            }
        }
        network.setAttachments(attachments);
        network.setContacts(contacts);
    }

    private static FluidNetwork.Port port(FluidMachine m, FluidPorts.Port p) {
        return new FluidNetwork.Port(m.fluidBox(p.index()), p.spec().kind(), p.filter(), m.portRate(p.index()), m.pumpPort(p.index()),
                m::fluidChanged);
    }

    /** Segments (and machines) whose gauge step changed send block updates to their loaded nodes. */
    private void syncGauges() {
        Map<FluidNetwork.Segment, FluidGauge> now = new IdentityHashMap<>();
        for (FluidNetwork.Segment s : network.segments()) {
            FluidGauge g = FluidGauge.of(s);
            now.put(s, g);
            if (g.equals(sentGauges.get(s))) continue;
            for (long key : s.nodeKeys()) {
                BlockPos p = BlockPos.of(key);
                if (!level.isLoaded(p)) continue;
                BlockState state = level.getBlockState(p);
                level.sendBlockUpdated(p, state, state, Block.UPDATE_CLIENTS);
            }
        }
        sentGauges = now;
        sentMachineGauges.keySet().retainAll(machines);
        for (BlockPos pos : machines) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof FluidMachine m)) continue;
            FluidGauge g = FluidGauge.of(m.altBox());
            if (g.equals(sentMachineGauges.put(pos, g))) continue;
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Players looking at a pipe, underground pipe or tank see its segment in the action bar. */
    private void hover(long time) {
        for (ServerPlayer player : level.players()) {
            Long quiet = hoverQuietUntil.get(player.getUUID());
            if (quiet != null && time < quiet) continue;
            HitResult hit = player.pick(player.blockInteractionRange(), 1, false);
            if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) continue;
            BlockPos node = FluidConnections.nodeAt(level, bh.getBlockPos());
            if (node == null) continue;
            FluidNetwork.Segment s = network.segment(node.asLong());
            if (s != null) player.displayClientMessage(FluidText.segment(s), true);
        }
    }
}
