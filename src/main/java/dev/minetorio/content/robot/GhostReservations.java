package dev.minetorio.content.robot;

import dev.minetorio.core.robot.Reservations;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The cells each loaded ghost covers ({@link Ghosts#footprint}: a multiblock's whole footprint, a
 * pole's three blocks, else the ghost's own cell), per level, on both sides: the server refuses
 * placements into them ({@link HandGhosts}), the client draws them red in placement previews.
 * Kept by {@link GhostBlockEntity} as it loads, changes and unloads.
 */
public final class GhostReservations {
    /** Client and server levels live on different threads; each level's own index is used only by its thread. */
    private static final Map<Level, Reservations> BY_LEVEL = Collections.synchronizedMap(new WeakHashMap<>());

    private GhostReservations() {}

    private static Reservations of(Level level) {
        return BY_LEVEL.computeIfAbsent(level, l -> new Reservations());
    }

    static void reserve(Level level, BlockPos owner, List<BlockPos> cells) {
        of(level).reserve(ConstructionManager.cell(owner), cells.stream().map(ConstructionManager::cell).toList());
    }

    static void release(Level level, BlockPos owner) {
        Reservations r = BY_LEVEL.get(level);
        if (r != null) r.release(ConstructionManager.cell(owner));
    }

    /** The ghost cell that reserved {@code cell}, or null. */
    @Nullable
    public static BlockPos owner(Level level, BlockPos cell) {
        Reservations r = BY_LEVEL.get(level);
        var o = r == null ? null : r.owner(ConstructionManager.cell(cell));
        return o == null ? null : ConstructionManager.pos(o);
    }

    /** Whether {@code cell} is free of other ghosts' reservations for a ghost (or block) whose own cell is {@code by}. */
    public static boolean freeFor(Level level, BlockPos cell, @Nullable BlockPos by) {
        Reservations r = BY_LEVEL.get(level);
        return r == null || r.freeFor(ConstructionManager.cell(cell), by == null ? null : ConstructionManager.cell(by));
    }
}
