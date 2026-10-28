package dev.minetorio.content.robot;

import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.core.robot.Cell;
import dev.minetorio.core.robot.CellMarks;
import dev.minetorio.core.robot.ConstructionQueue;
import dev.minetorio.core.robot.DeconstructionMarks;
import dev.minetorio.core.robot.UpgradeMarks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Deconstruction and upgrade marks of one server level, saved with the level ({@link SavedData}
 * {@value #NAME}). The pure stores ({@link DeconstructionMarks}, {@link UpgradeMarks}) hold the
 * orders by cell; this wrapper
 * <ul>
 *   <li>marks a machine by its controller (any piece of it may be given) and remembers which block
 *       was marked, so a mark disappears when its block changes: broken by a player
 *       ({@link BlockEvent.BreakEvent}, at once) or anything else (a sweep every
 *       {@value #SWEEP_INTERVAL} ticks over loaded marks);</li>
 *   <li>mirrors the marks into the {@link ConstructionQueue} as deconstruction / upgrade jobs for
 *       the robots ({@link #attach});</li>
 *   <li>syncs them to the clients that see the chunk: the whole chunk's marks when a chunk is sent
 *       to a player, and again after any change in it (batched per tick, {@link MarkPayloads}).</li>
 * </ul>
 * A cell has at most one of the two: marking for deconstruction drops an upgrade order there, an
 * upgrade order is refused on a cell marked for deconstruction.
 */
public final class ConstructionMarks extends SavedData {
    public static final String NAME = "minetorio_construction_marks";
    static final int SWEEP_INTERVAL = 20;

    private final DeconstructionMarks deconstruction = new DeconstructionMarks();
    private final UpgradeMarks upgrades = new UpgradeMarks();
    /** Chunks whose marks changed since the last sync. */
    private final Set<Long> dirty = new HashSet<>();
    @Nullable private ConstructionQueue queue;

    private ConstructionMarks() {
        deconstruction.addListener((cell, block) -> {
            changed(cell);
            if (queue == null) return;
            if (block != null) queue.markForDeconstruction(cell);
            else queue.unmarkDeconstruction(cell);
        });
        upgrades.addListener((cell, order) -> {
            changed(cell);
            if (queue == null) return;
            if (order != null) queue.markForUpgrade(cell, order.toItem(), order.count());
            else queue.unmarkUpgrade(cell);
        });
    }

    private void changed(Cell cell) {
        setDirty();
        dirty.add(CellMarks.chunkKey(cell));
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ConstructionMarks::onLevelTick);
        NeoForge.EVENT_BUS.addListener(ConstructionMarks::onBreak);
        NeoForge.EVENT_BUS.addListener(ConstructionMarks::onChunkSent);
    }

    private static SavedData.Factory<ConstructionMarks> factory() {
        return new SavedData.Factory<>(ConstructionMarks::new, ConstructionMarks::load);
    }

    public static ConstructionMarks get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    /** Mirrors every mark into {@code queue} and keeps it in step from now on (the level's {@link ConstructionManager}). */
    void attach(ConstructionQueue queue) {
        this.queue = queue;
        for (Cell c : deconstruction.cells()) queue.markForDeconstruction(c);
        for (Cell c : upgrades.cells()) {
            UpgradeMarks.Order o = upgrades.get(c);
            if (o != null) queue.markForUpgrade(c, o.toItem(), o.count());
        }
    }

    public DeconstructionMarks deconstruction() { return deconstruction; }

    public UpgradeMarks upgrades() { return upgrades; }

    // --- BlockPos API ---------------------------------------------------------------------------

    /** The cell a mark at {@code pos} goes to: the controller of a machine piece, else {@code pos}. */
    public static BlockPos markPos(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof MultiblockPartBlock) {
            BlockPos c = MultiblockPartBlock.controllerPos(level, pos);
            if (c != null) return c.immutable();
        }
        return pos.immutable();
    }

    public static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /**
     * Marks the block at {@code pos} (a machine: the whole machine) for deconstruction, dropping an
     * upgrade order there; false for air or when it was marked already.
     */
    public boolean mark(ServerLevel level, BlockPos pos) {
        BlockPos at = markPos(level, pos);
        BlockState s = level.getBlockState(at);
        if (s.isAir()) return false;
        Cell c = ConstructionManager.cell(at);
        upgrades.unmark(c);
        return deconstruction.mark(c, blockId(s));
    }

    /** Removes the deconstruction mark of the block at {@code pos}; false if it had none. */
    public boolean unmark(Level level, BlockPos pos) {
        return deconstruction.unmark(ConstructionManager.cell(markPos(level, pos)));
    }

    public boolean isMarked(Level level, BlockPos pos) {
        return deconstruction.isMarked(ConstructionManager.cell(markPos(level, pos)));
    }

    /** Marks the block at {@code pos} to be upgraded to {@code count} × {@code toItem}; false when marked for deconstruction. */
    public boolean markUpgrade(ServerLevel level, BlockPos pos, String toItem, int count) {
        BlockPos at = markPos(level, pos);
        Cell c = ConstructionManager.cell(at);
        if (deconstruction.isMarked(c)) return false;
        BlockState s = level.getBlockState(at);
        if (s.isAir()) return false;
        return upgrades.mark(c, blockId(s), toItem, count);
    }

    public boolean unmarkUpgrade(Level level, BlockPos pos) {
        return upgrades.unmark(ConstructionManager.cell(markPos(level, pos)));
    }

    @Nullable
    public UpgradeMarks.Order upgradeOrder(Level level, BlockPos pos) {
        return upgrades.get(ConstructionManager.cell(markPos(level, pos)));
    }

    // --- static shortcuts -----------------------------------------------------------------------

    public static boolean markForDeconstruction(ServerLevel level, BlockPos pos) { return get(level).mark(level, pos); }

    public static boolean unmarkDeconstruction(ServerLevel level, BlockPos pos) { return get(level).unmark(level, pos); }

    public static boolean isMarkedForDeconstruction(ServerLevel level, BlockPos pos) { return get(level).isMarked(level, pos); }

    // --- upkeep ---------------------------------------------------------------------------------

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ConstructionMarks m = level.getDataStorage().get(factory(), NAME);
        if (m == null) return;
        if (level.getGameTime() % SWEEP_INTERVAL == 0) m.sweep(level);
        m.flush(level);
    }

    /** Drops marks whose block changed (loaded chunks only). */
    void sweep(ServerLevel level) {
        if (deconstruction.isEmpty() && upgrades.isEmpty()) return;
        for (Cell c : deconstruction.cells()) {
            BlockPos p = ConstructionManager.pos(c);
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !deconstruction.stillValid(c, blockId(s))) deconstruction.unmark(c);
        }
        for (Cell c : upgrades.cells()) {
            BlockPos p = ConstructionManager.pos(c);
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !upgrades.stillValid(c, blockId(s))) upgrades.unmark(c);
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ConstructionMarks m = level.getDataStorage().get(factory(), NAME);
        if (m == null) return;
        Cell c = ConstructionManager.cell(markPos(level, event.getPos()));
        m.deconstruction.unmark(c);
        m.upgrades.unmark(c);
    }

    // --- client sync ----------------------------------------------------------------------------

    private void flush(ServerLevel level) {
        if (dirty.isEmpty()) return;
        List<Long> chunks = new ArrayList<>(dirty);
        dirty.clear();
        for (long k : chunks) {
            ChunkPos cp = new ChunkPos(k);
            PacketDistributor.sendToPlayersTrackingChunk(level, cp, chunkPayload(cp.x, cp.z));
        }
    }

    private static void onChunkSent(ChunkWatchEvent.Sent event) {
        ServerLevel level = event.getLevel();
        ConstructionMarks m = level.getDataStorage().get(factory(), NAME);
        if (m == null) return;
        ChunkPos cp = event.getPos();
        if (m.deconstruction.inChunk(cp.x, cp.z).isEmpty() && m.upgrades.inChunk(cp.x, cp.z).isEmpty()) return;
        ServerPlayer player = event.getPlayer();
        PacketDistributor.sendToPlayer(player, m.chunkPayload(cp.x, cp.z));
    }

    /** Every mark in chunk {@code (cx, cz)}. */
    MarkPayloads.Chunk chunkPayload(int cx, int cz) {
        List<MarkPayloads.Entry> out = new ArrayList<>();
        for (Cell c : deconstruction.inChunk(cx, cz)) out.add(new MarkPayloads.Entry(ConstructionManager.pos(c), false, ""));
        for (Cell c : upgrades.inChunk(cx, cz)) {
            UpgradeMarks.Order o = upgrades.get(c);
            if (o != null) out.add(new MarkPayloads.Entry(ConstructionManager.pos(c), true, o.toItem()));
        }
        return new MarkPayloads.Chunk(cx, cz, out);
    }

    // --- persistence ----------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag decon = new ListTag();
        for (Cell c : deconstruction.cells()) {
            CompoundTag e = cellTag(c);
            e.putString("block", deconstruction.block(c));
            decon.add(e);
        }
        tag.put("deconstruct", decon);
        ListTag up = new ListTag();
        for (Cell c : upgrades.cells()) {
            UpgradeMarks.Order o = upgrades.get(c);
            if (o == null) continue;
            CompoundTag e = cellTag(c);
            e.putString("block", o.fromBlock());
            e.putString("to", o.toItem());
            e.putInt("count", o.count());
            up.add(e);
        }
        tag.put("upgrade", up);
        return tag;
    }

    private static CompoundTag cellTag(Cell c) {
        CompoundTag e = new CompoundTag();
        e.putInt("x", c.x());
        e.putInt("y", c.y());
        e.putInt("z", c.z());
        return e;
    }

    private static Cell cellOf(CompoundTag e) {
        return new Cell(e.getInt("x"), e.getInt("y"), e.getInt("z"));
    }

    public static ConstructionMarks load(CompoundTag tag, HolderLookup.Provider registries) {
        ConstructionMarks m = new ConstructionMarks();
        for (Tag t : tag.getList("deconstruct", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            m.deconstruction.mark(cellOf(e), e.getString("block"));
        }
        for (Tag t : tag.getList("upgrade", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            String to = e.getString("to");
            if (to.isEmpty()) continue;
            m.upgrades.mark(cellOf(e), e.getString("block"), to, Math.max(1, e.getInt("count")));
        }
        m.dirty.clear();
        return m;
    }
}
