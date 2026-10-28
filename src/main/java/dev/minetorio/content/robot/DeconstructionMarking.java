package dev.minetorio.content.robot;

import dev.minetorio.Minetorio;
import dev.minetorio.core.robot.Cell;
import dev.minetorio.core.robot.DeconstructionMarks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Per-level deconstruction marks ({@link DeconstructionMarks}, saved with the level) and the
 * ghosts waiting for a marked block to go. A Shift blueprint paste marks the vanilla blocks in its
 * way ({@link #markable}: plain terrain and plants, no block entity, no fluid, breakable); a ghost
 * whose own cell is such a block waits here ({@link #addPending}) and is placed once the block is
 * gone. Marks of blocks that are gone are dropped.
 *
 * <p>Deliberately minimal (mark / unmark / isMarked, saved): the deconstruction planner and the
 * robots' deconstruction build on it and own how marks are shown and worked off. Until robots
 * deconstruct, marked blocks are removed by hand (normal breaking).
 */
public final class DeconstructionMarking extends SavedData {
    private static final String NAME = Minetorio.MODID + "_deconstruction";
    private static final int CHECK_INTERVAL = 10;

    private final DeconstructionMarks marks = new DeconstructionMarks();
    private final Map<Cell, Pending> pending = new LinkedHashMap<>();

    /** A ghost to place at a cell once its marked block is gone. */
    record Pending(BlockState target, Direction facing, @Nullable CompoundTag settings) {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level && level.getGameTime() % CHECK_INTERVAL == 0) {
                DeconstructionMarking d = getIfLoaded(level);
                if (d != null) d.check(level);
            }
        });
    }

    private static Factory<DeconstructionMarking> factory() {
        return new Factory<>(DeconstructionMarking::new, DeconstructionMarking::load, null);
    }

    public static DeconstructionMarking get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    @Nullable
    private static DeconstructionMarking getIfLoaded(ServerLevel level) {
        return level.getDataStorage().get(factory(), NAME);
    }

    /**
     * Whether a paste may mark the block at {@code pos} for deconstruction: a solid vanilla (non-Minetorio)
     * block without a block entity or fluid that breaks normally (terrain, logs, leaves, plants).
     */
    public static boolean markable(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return markable(s, level.getBlockState(pos).getDestroySpeed(level, pos))
                && !Minetorio.MODID.equals(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace());
    }

    /** {@link #markable(ServerLevel, BlockPos)} for any level (client previews); {@code hardness} the block's destroy speed. */
    public static boolean markable(BlockState s, float hardness) {
        return !s.isAir() && !s.hasBlockEntity() && s.getFluidState().isEmpty() && hardness >= 0;
    }

    public static boolean isMarked(ServerLevel level, BlockPos pos) {
        DeconstructionMarking d = getIfLoaded(level);
        return d != null && d.marks.isMarked(ConstructionManager.cell(pos));
    }

    public static boolean mark(ServerLevel level, BlockPos pos) {
        DeconstructionMarking d = get(level);
        boolean added = d.marks.mark(ConstructionManager.cell(pos));
        if (added) d.setDirty();
        return added;
    }

    public static boolean unmark(ServerLevel level, BlockPos pos) {
        DeconstructionMarking d = get(level);
        boolean removed = d.marks.unmark(ConstructionManager.cell(pos));
        if (removed) d.setDirty();
        return removed;
    }

    /** Places a ghost of {@code target} at {@code pos} as soon as the (marked) block there is gone. */
    public static void addPending(ServerLevel level, BlockPos pos, BlockState target, Direction facing, @Nullable CompoundTag settings) {
        DeconstructionMarking d = get(level);
        d.pending.put(ConstructionManager.cell(pos), new Pending(target, facing, settings == null ? null : settings.copy()));
        d.setDirty();
    }

    public static boolean hasPending(ServerLevel level, BlockPos pos) {
        DeconstructionMarking d = getIfLoaded(level);
        return d != null && d.pending.containsKey(ConstructionManager.cell(pos));
    }

    /** Drops marks of blocks that are gone; places waiting ghosts whose cell is free, forgets those whose mark went. */
    void check(ServerLevel level) {
        for (Cell c : new ArrayList<>(marks.all())) {
            BlockPos p = ConstructionManager.pos(c);
            if (level.isLoaded(p) && level.getBlockState(p).canBeReplaced()) {
                marks.unmark(c);
                setDirty();
            }
        }
        for (Map.Entry<Cell, Pending> e : new ArrayList<>(pending.entrySet())) {
            BlockPos p = ConstructionManager.pos(e.getKey());
            if (!level.isLoaded(p)) continue;
            BlockState now = level.getBlockState(p);
            if (now.canBeReplaced() && now.getFluidState().isEmpty()) {
                Pending g = e.getValue();
                BlockState target = Ghosts.planned(level, p, g.target());
                if (Ghosts.fits(level, p, target)) Ghosts.placePlanned(level, p, target, g.facing(), g.settings());
                pending.remove(e.getKey());
                setDirty();
            } else if (!marks.isMarked(e.getKey())) {
                pending.remove(e.getKey()); // unmarked: the block stays, so does the obstacle
                setDirty();
            }
        }
    }

    // --- saving ----------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("marks", marks.all().stream().mapToLong(c -> ConstructionManager.pos(c).asLong()).toArray());
        ListTag list = new ListTag();
        for (Map.Entry<Cell, Pending> e : pending.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", ConstructionManager.pos(e.getKey()).asLong());
            t.put("target", NbtUtils.writeBlockState(e.getValue().target()));
            t.putString("facing", e.getValue().facing().getSerializedName());
            if (e.getValue().settings() != null) t.put("settings", e.getValue().settings());
            list.add(t);
        }
        tag.put("pending", list);
        return tag;
    }

    private static DeconstructionMarking load(CompoundTag tag, HolderLookup.Provider registries) {
        DeconstructionMarking d = new DeconstructionMarking();
        for (long l : tag.getLongArray("marks")) d.marks.mark(ConstructionManager.cell(BlockPos.of(l)));
        for (Tag raw : tag.getList("pending", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            BlockState target = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), t.getCompound("target"));
            Direction f = Direction.byName(t.getString("facing"));
            if (target.isAir() || f == null || f.getAxis().isVertical()) continue;
            d.pending.put(ConstructionManager.cell(BlockPos.of(t.getLong("pos"))),
                    new Pending(target, f, t.contains("settings") ? t.getCompound("settings") : null));
        }
        return d;
    }
}
