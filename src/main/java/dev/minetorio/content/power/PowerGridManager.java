package dev.minetorio.content.power;

import dev.minetorio.core.power.PoleGraph;
import dev.minetorio.core.power.PolePos;
import dev.minetorio.core.power.PowerConsumer;
import dev.minetorio.core.power.PowerNetwork;
import dev.minetorio.core.power.PowerProducer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.WeakHashMap;
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
 * <p>Poles form a {@link PoleGraph} (wire reach {@value #WIRE_REACH}, supply radius
 * {@value #SUPPLY_RADIUS}); each connected component is one {@link PowerNetwork}. A consumer or
 * producer joins the network of a pole whose supply area covers any cell of its footprint (the
 * first one found if several do).
 *
 * <p><b>Ticking</b> ({@link LevelTickEvent.Post}, after every block entity ticked): if anything was
 * added or removed, the networks are rebuilt (and, if the poles changed, every loaded pole is told
 * its wire connections); then every network ticks, and every consumer outside all networks gets
 * {@code supply(0)} explicitly, so no consumer keeps a stale satisfaction after its pole is gone.
 * Block entities therefore run on the satisfaction of the previous tick.
 */
public final class PowerGridManager {
    public static final double WIRE_REACH = 7.5;
    public static final int SUPPLY_RADIUS = 2;

    private static final Map<ServerLevel, PowerGridManager> MANAGERS = Collections.synchronizedMap(new WeakHashMap<>());

    private record Member(AABB footprint, Object handle) {}

    private final ServerLevel level;
    private final PoleGraph graph = new PoleGraph(WIRE_REACH, SUPPLY_RADIUS);
    private final Set<BlockPos> poles = new LinkedHashSet<>();
    private final Map<BlockPos, Member> members = new LinkedHashMap<>();
    private List<PowerNetwork> networks = List.of();
    private List<PowerConsumer> unpowered = List.of();
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

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        PowerGridManager manager = MANAGERS.get(server);
        if (manager != null) manager.tick();
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel server) MANAGERS.remove(server);
    }

    // --- registration ------------------------------------------------------------------------------

    public void registerPole(BlockPos pos) {
        if (poles.add(pos.immutable())) {
            graph.addPole(pole(pos));
            dirty = polesDirty = true;
        }
    }

    public void unregisterPole(BlockPos pos) {
        if (poles.remove(pos)) {
            graph.removePole(pole(pos));
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
        if (!poles.contains(pos)) return Set.of();
        Set<BlockPos> out = new LinkedHashSet<>();
        for (PolePos p : graph.neighbors(pole(pos))) out.add(new BlockPos(p.x(), p.y(), p.z()));
        return out;
    }

    /** Component id of the network supplying {@code footprint}, if any pole covers it. */
    public OptionalInt networkCovering(AABB footprint) {
        int x0 = (int) Math.floor(footprint.minX), x1 = (int) Math.ceil(footprint.maxX) - 1;
        int y0 = (int) Math.floor(footprint.minY), y1 = (int) Math.ceil(footprint.maxY) - 1;
        int z0 = (int) Math.floor(footprint.minZ), z1 = (int) Math.ceil(footprint.maxZ) - 1;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    OptionalInt c = graph.componentCovering(x, y, z);
                    if (c.isPresent()) return c;
                }
            }
        }
        return OptionalInt.empty();
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
        List<PowerConsumer> outside = new ArrayList<>();
        for (Member m : members.values()) {
            OptionalInt c = networkCovering(m.footprint());
            if (c.isEmpty()) {
                if (m.handle() instanceof PowerConsumer consumer) outside.add(consumer);
                continue;
            }
            PowerNetwork n = byComponent.computeIfAbsent(c.getAsInt(), k -> new PowerNetwork());
            if (m.handle() instanceof PowerConsumer consumer) n.add(consumer);
            if (m.handle() instanceof PowerProducer producer) n.add(producer);
        }
        networks = List.copyOf(byComponent.values());
        unpowered = outside;
        if (polesDirty) {
            polesDirty = false;
            for (BlockPos p : poles) {
                if (level.isLoaded(p) && level.getBlockEntity(p) instanceof ElectricPoleBlockEntity be) be.setConnections(connections(p));
            }
        }
    }

    private static PolePos pole(BlockPos pos) {
        return new PolePos(pos.getX(), pos.getY(), pos.getZ());
    }
}
