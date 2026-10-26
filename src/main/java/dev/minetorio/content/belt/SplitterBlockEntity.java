package dev.minetorio.content.belt;

import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.belt.BeltLane;
import dev.minetorio.core.belt.BeltTier;
import dev.minetorio.core.belt.LaneSink;
import dev.minetorio.core.belt.SplitterLogic;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Splitter lanes. Column 0 is the controller (left, looking along the facing), column 1 the part
 * (right). Each column has an input half (lanes of length 0.5 from the back edge) and an output
 * half (0.5 to the front edge). Per lane side, the items reaching the end of either input column
 * are routed by that side's {@link SplitterLogic} to output column 0 (left) or 1 (right),
 * alternating, or to the free one when the other is full; the two input columns take turns.
 */
public class SplitterBlockEntity extends MultiblockControllerBlockEntity implements BeltNode {
    public static final double HALF = 0.5;
    private static final String[] COLUMN_KEYS = {"c0", "c1"};

    /** [column][side] */
    private final BeltLane[][] in = new BeltLane[2][2];
    private final BeltLane[][] out = new BeltLane[2][2];
    /** One router per lane side. */
    private final SplitterLogic[] logic = {new SplitterLogic(), new SplitterLogic()};
    private boolean rightInputFirst;
    private final LaneTracker tracker = new LaneTracker();

    public SplitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPLITTER.get(), pos, state);
        for (int c = 0; c < 2; c++) {
            for (int s = 0; s < 2; s++) {
                in[c][s] = new BeltLane(HALF);
                out[c][s] = new BeltLane(HALF);
            }
        }
    }

    public Direction facing() {
        return getBlockState().getValue(MultiblockControllerBlock.FACING);
    }

    public BeltTier tier() {
        return ((SplitterBlock) getBlockState().getBlock()).tier();
    }

    /** World position of column {@code c} (0 = controller/left, 1 = right). */
    public BlockPos columnPos(int c) {
        return worldPosition.relative(facing().getClockWise(), c);
    }

    public BeltLane input(int column, LaneSide side) { return in[column][side.ordinal()]; }

    public BeltLane output(int column, LaneSide side) { return out[column][side.ordinal()]; }

    public LaneTracker tracker() { return tracker; }

    private List<BeltLane> allLanes() {
        List<BeltLane> all = new ArrayList<>(8);
        for (int c = 0; c < 2; c++) {
            for (int s = 0; s < 2; s++) {
                all.add(in[c][s]);
                all.add(out[c][s]);
            }
        }
        return all;
    }

    // --- ticking -------------------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) BeltTicker.of(level).add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) BeltTicker.of(level).remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) BeltTicker.of(level).remove(this);
    }

    @Override
    public List<BlockPos> occupied() {
        return List.of(columnPos(0), columnPos(1));
    }

    @Override
    public List<BlockPos> downstream() {
        Direction f = facing();
        return List.of(columnPos(0).relative(f), columnPos(1).relative(f));
    }

    @Override
    public void tickBelt() {
        if (level == null) return;
        double speed = tier().speed();
        Direction f = facing();
        tracker.beginTick(level);
        for (int c = 0; c < 2; c++) {
            for (LaneSide side : LaneSide.values()) {
                tracker.tick(level, out[c][side.ordinal()], speed, BeltGeometry.lazySinkTo(level, columnPos(c), f, side));
            }
        }
        int first = rightInputFirst ? 1 : 0;
        rightInputFirst = !rightInputFirst;
        for (int k = 0; k < 2; k++) {
            int c = first ^ k;
            for (LaneSide side : LaneSide.values()) tracker.tick(level, in[c][side.ordinal()], speed, router(side.ordinal()));
        }
    }

    /** Hands an item that reached the end of an input lane to an output column of the same side. */
    private LaneSink router(int s) {
        return (item, overflow) -> {
            double at = Math.max(0, overflow);
            int target = logic[s].route(0, out[0][s].canInsertAt(at), out[1][s].canInsertAt(at));
            return target >= 0 && out[target][s].insertAt(item, at);
        };
    }

    @Override
    public void afterBeltTick() {
        tracker.afterTick(this, allLanes());
    }

    @Nullable
    @Override
    public LaneSink incoming(BlockPos at, Direction travel, LaneSide side) {
        if (travel != facing()) return null;
        for (int c = 0; c < 2; c++) {
            if (columnPos(c).equals(at)) return in[c][side.ordinal()].asSink();
        }
        return null;
    }

    @Override
    protected void dropContents(Level level, BlockPos pos) {
        for (BeltLane lane : allLanes()) LaneStacks.drop(level, pos, lane);
    }

    // --- persistence and sync --------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int c = 0; c < 2; c++) {
            CompoundTag col = new CompoundTag();
            for (LaneSide side : LaneSide.values()) {
                String k = side.name().toLowerCase(Locale.ROOT);
                col.put("in_" + k, CoreNbt.writeLane(in[c][side.ordinal()]));
                col.put("out_" + k, CoreNbt.writeLane(out[c][side.ordinal()]));
            }
            tag.put(COLUMN_KEYS[c], col);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        List<BeltLane> before = allLanes();
        for (int c = 0; c < 2; c++) {
            if (!tag.contains(COLUMN_KEYS[c])) continue;
            CompoundTag col = tag.getCompound(COLUMN_KEYS[c]);
            for (LaneSide side : LaneSide.values()) {
                String k = side.name().toLowerCase(Locale.ROOT);
                if (col.contains("in_" + k)) in[c][side.ordinal()] = CoreNbt.readLane(col.getCompound("in_" + k));
                if (col.contains("out_" + k)) out[c][side.ordinal()] = CoreNbt.readLane(col.getCompound("out_" + k));
            }
        }
        if (level != null && level.isClientSide()) tracker.snap(before, allLanes());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
