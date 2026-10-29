package dev.minetorio.content.power;

import dev.minetorio.core.power.PoleGraph;
import dev.minetorio.core.power.PolePos;
import dev.minetorio.core.power.PoleType;
import dev.minetorio.core.power.PowerConsumer;
import dev.minetorio.core.power.PowerNetwork;
import dev.minetorio.core.power.PowerProducer;
import dev.minetorio.core.power.PowerStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The electric grids of one server level. Lives in memory only: poles, consumers and producers
 * register themselves from their block entities' {@code onLoad} and unregister when removed or
 * unloaded, so the grids are rebuilt from whatever is loaded (no SavedData).
 *
 * <p>Poles of every {@link PoleType} form a {@link PoleGraph} (wire reach and supply area per
 * type); each connected component is one {@link PowerNetwork}. A consumer, producer or storage
 * (accumulator) joins the network of a pole whose supply area covers any cell of its footprint; if
 * poles of several networks cover it, the one of the covering pole with the smallest footprint
 * corner ({@link BlockPos#compareTo} order) wins, so the choice does not depend on registration order.
 * A pole is registered under its block entity's position; the graph knows it by its footprint corner.
 *
 * <p><b>Ticking</b> ({@link LevelTickEvent.Post}, after every block entity ticked): if anything was
 * added or removed, the networks are rebuilt (and, if the poles changed, every loaded pole is told
 * its wire connections); then every network ticks, and every consumer outside all networks gets
 * {@code supply(0)} explicitly, so no consumer keeps a stale satisfaction after its pole is gone.
 * Block entities therefore run on the satisfaction of the previous tick.
 */
public final class PowerGridManager {
    /** Server thread only; entries are removed when their level unloads. */
    private static final Map<ServerLevel, PowerGridManager> MANAGERS = new HashMap<>();

    private record Member(AABB footprint, Object handle) {}

    private final ServerLevel level;
    private final PoleGraph graph = new PoleGraph();
    /** Block entity position -> footprint corner (the graph's key). */
    private final Map<BlockPos, PolePos> poles = new LinkedHashMap<>();
    private final Map<PolePos, BlockPos> poleByKey = new HashMap<>();
    private final Map<BlockPos, Member> members = new LinkedHashMap<>();
    private List<PowerNetwork> networks = List.of();
    private List<PowerConsumer> unpowered = List.of();
    private Map<BlockPos, PowerNetwork> memberNetworks = Map.of();
    private Map<Integer,PowerNetwork> componentNetworks=Map.of();
    /** Members changed: networks must be rebuilt. */
    private boolean dirty;
    /** Poles changed: wire connections must be re-sent. */
    private boolean polesDirty;

    private PowerGridManager(ServerLevel level) {
        this.level = level;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PowerGridManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener(PowerGridManager::onLevelUnload);
    }

    public static PowerGridManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, PowerGridManager::new);
    }

    /** The level's manager if it exists; never creates one (for unregistering, e.g. while a level unloads). */
    @Nullable
    public static PowerGridManager getIfPresent(ServerLevel level) {
        return MANAGERS.get(level);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        PowerGridManager manager = MANAGERS.get(server);
        if (manager != null) manager.tick();
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel server) MANAGERS.remove(server);
    }

    // --- registration ------------------------------------------------------------------------------

    /** Registers the pole whose block entity is at {@code pos}, of {@code type}, with its footprint corner at {@code footprintMin}. */
    public void registerPole(BlockPos pos, PoleType type, BlockPos footprintMin) {
        PolePos key = pole(footprintMin);
        PolePos old = poles.put(pos.immutable(), key);
        if (old != null && !old.equals(key)) {
            graph.removePole(old);
            poleByKey.remove(old);
        }
        poleByKey.put(key, pos.immutable());
        graph.addPole(key, type);
        dirty = polesDirty = true;
    }

    public void unregisterPole(BlockPos pos) {
        PolePos key = poles.remove(pos);
        if (key != null) {
            graph.removePole(key);
            poleByKey.remove(key);
            dirty = polesDirty = true;
        }
    }

    /** Registers (or replaces) the consumer at {@code pos}, occupying {@code footprint}. */
    public void registerConsumer(BlockPos pos, AABB footprint, PowerConsumer consumer) {
        members.put(pos.immutable(), new Member(footprint, consumer));
        dirty = true;
    }

    /** Registers (or replaces) the producer at {@code pos}, occupying {@code footprint}. */
    public void registerProducer(BlockPos pos, AABB footprint, PowerProducer producer) {
        members.put(pos.immutable(), new Member(footprint, producer));
        dirty = true;
    }

    /** Registers (or replaces) the storage (accumulator) at {@code pos}, occupying {@code footprint}. */
    public void registerStorage(BlockPos pos, AABB footprint, PowerStorage storage) {
        members.put(pos.immutable(), new Member(footprint, storage));
        dirty = true;
    }

    /** Removes the consumer or producer at {@code pos}. */
    public void unregister(BlockPos pos) {
        if (members.remove(pos) != null) dirty = true;
    }

    /** Removes the member at {@code pos} only if it is {@code handle} (a replaced block entity must not remove its successor). */
    public void unregister(BlockPos pos, Object handle) {
        Member m = members.get(pos);
        if (m != null && m.handle() == handle) {
            members.remove(pos);
            dirty = true;
        }
    }

    // --- queries -----------------------------------------------------------------------------------

    /** Poles connected by wire to the pole at {@code pos} (empty if it is not registered). */
    public Set<BlockPos> connections(BlockPos pos) {
        PolePos key = poles.get(pos);
        if (key == null) return Set.of();
        Set<BlockPos> out = new LinkedHashSet<>();
        for (PolePos p : graph.neighbors(key)) out.add(poleByKey.get(p));
        return out;
    }

    /**
     * Component id of the network supplying {@code footprint}: that of the smallest pole whose
     * supply area covers any of its cells; empty if no pole does.
     */
    public OptionalInt networkCovering(AABB footprint) {
        int x0 = (int) Math.floor(footprint.minX), x1 = (int) Math.ceil(footprint.maxX) - 1;
        int y0 = (int) Math.floor(footprint.minY), y1 = (int) Math.ceil(footprint.maxY) - 1;
        int z0 = (int) Math.floor(footprint.minZ), z1 = (int) Math.ceil(footprint.maxZ) - 1;
        return graph.componentCovering(x0, y0, z0, x1, y1, z1);
    }

    /** The network the member registered at {@code pos} belongs to (as of the last rebuild), or null. */
    @Nullable
    public PowerNetwork networkOf(BlockPos pos) {
        return memberNetworks.get(pos);
    }

    /** Real settled network attached to a pole; idle isolated poles have no network members. */
    @Nullable
    public PowerNetwork networkAtPole(BlockPos position) {
        PolePos key=poles.get(position);if(key==null)return null;
        return componentNetworks.get(graph.componentOf(key));
    }

    // --- ticking -----------------------------------------------------------------------------------

    private void tick() {
        if (dirty) rebuild();
        for (PowerNetwork n : networks) n.tick();
        for (PowerConsumer c : unpowered) c.supply(0);
    }

    private void rebuild() {
        dirty = false;
        Map<Integer, PowerNetwork> byComponent = new HashMap<>();
        Map<BlockPos, PowerNetwork> byMember = new HashMap<>();
        List<PowerConsumer> outside = new ArrayList<>();
        for (Map.Entry<BlockPos, Member> e : members.entrySet()) {
            Member m = e.getValue();
            OptionalInt c = networkCovering(m.footprint());
            if (c.isEmpty()) {
                if (m.handle() instanceof PowerConsumer consumer) outside.add(consumer);
                continue;
            }
            PowerNetwork n = byComponent.computeIfAbsent(c.getAsInt(), k -> new PowerNetwork());
            byMember.put(e.getKey(), n);
            if (m.handle() instanceof PowerConsumer consumer) n.add(consumer);
            if (m.handle() instanceof PowerProducer producer) n.add(producer);
            if (m.handle() instanceof PowerStorage storage) n.add(storage);
        }
        componentNetworks=Map.copyOf(byComponent);
        networks = List.copyOf(byComponent.values());
        memberNetworks = byMember;
        unpowered = outside;
        if (polesDirty) {
            polesDirty = false;
            for (BlockPos p : poles.keySet()) {
                if (level.isLoaded(p) && level.getBlockEntity(p) instanceof PowerPoleEntity be) be.setConnections(connections(p));
            }
        }
    }

    private static PolePos pole(BlockPos pos) {
        return new PolePos(pos.getX(), pos.getY(), pos.getZ());
    }
}
