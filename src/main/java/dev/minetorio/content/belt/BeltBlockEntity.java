package dev.minetorio.content.belt;

import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.belt.BeltLane;
import dev.minetorio.core.belt.BeltShape;
import dev.minetorio.core.belt.BeltTier;
import dev.minetorio.core.belt.LaneSink;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Two lanes of a transport belt. Lane lengths follow the block's shape (curve: inner 0.5, outer
 * 1.0). Ticked by {@link BeltTicker}: {@code lane.tick(tier.speed(), sinkTo(front))} on both sides;
 * the client runs the same simulation for smooth motion and snaps to the server's lane contents,
 * which are sent whenever an item enters or leaves the belt.
 *
 * <p>Code that changes a lane from outside (inserters, drills) may use {@link #lane} directly;
 * the change is detected and synced after the next tick.
 */
public class BeltBlockEntity extends BlockEntity implements BeltNode {
    private static final String KEY_LEFT = "left";
    private static final String KEY_RIGHT = "right";

    private BeltLane left;
    private BeltLane right;
    private final LaneTracker tracker = new LaneTracker();

    public BeltBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSPORT_BELT.get(), pos, state);
        BeltShape shape = shapeOf(state);
        left = new BeltLane(laneLength(shape, LaneSide.LEFT));
        right = new BeltLane(laneLength(shape, LaneSide.RIGHT));
    }

    private static BeltShape shapeOf(BlockState state) {
        return state.getValue(BeltBlock.SHAPE).core();
    }

    private static double laneLength(BeltShape shape, LaneSide side) {
        boolean inner = shape == BeltShape.CURVE_LEFT ? side == LaneSide.LEFT
                : shape == BeltShape.CURVE_RIGHT && side == LaneSide.RIGHT;
        return shape.laneLength(inner);
    }

    public BeltLane lane(LaneSide side) {
        return side == LaneSide.LEFT ? left : right;
    }

    public BeltTier tier() {
        return ((BeltBlock) getBlockState().getBlock()).tier();
    }

    public Direction facing() {
        return getBlockState().getValue(BeltBlock.FACING);
    }

    public BeltShape shape() {
        return shapeOf(getBlockState());
    }

    public LaneTracker tracker() {
        return tracker;
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
        return List.of(worldPosition);
    }

    @Override
    public List<BlockPos> downstream() {
        return List.of(worldPosition.relative(facing()));
    }

    @Override
    public void tickBelt() {
        if (level == null) return;
        double speed = tier().speed();
        Direction f = facing();
        tracker.beginTick(level);
        tracker.tick(level, left, speed, BeltGeometry.lazySinkTo(level, worldPosition, f, LaneSide.LEFT));
        tracker.tick(level, right, speed, BeltGeometry.lazySinkTo(level, worldPosition, f, LaneSide.RIGHT));
    }

    @Override
    public void afterBeltTick() {
        tracker.afterTick(this, List.of(left, right));
    }

    @Nullable
    @Override
    public LaneSink incoming(BlockPos at, Direction travel, LaneSide side) {
        Direction f = facing();
        BeltShape shape = shape();
        Direction entry = switch (shape) {
            case CURVE_LEFT -> f.getClockWise();          // fed by the left neighbour
            case CURVE_RIGHT -> f.getCounterClockWise();  // fed by the right neighbour
            default -> f;
        };
        if (travel == entry) return lane(side).asSink();
        LaneSide near = BeltGeometry.nearLane(f, travel);
        if (near == null) return null; // head-on
        BeltLane target = lane(near);
        double mid = target.length() / 2;
        return (item, overflow) -> target.insertAt(item, Math.min(mid + overflow, target.length()));
    }

    // --- shape changes, drops --------------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        BeltShape old = shape();
        super.setBlockState(state);
        BeltShape now = shape();
        if (now != old) {
            left = LaneStacks.resize(left, laneLength(now, LaneSide.LEFT), true, level, worldPosition);
            right = LaneStacks.resize(right, laneLength(now, LaneSide.RIGHT), true, level, worldPosition);
            tracker.requestSync();
        }
    }

    /** Drops every item on the lanes (server side) and empties them. */
    public void dropItems() {
        if (level == null) return;
        LaneStacks.drop(level, worldPosition, left);
        LaneStacks.drop(level, worldPosition, right);
        left = new BeltLane(left.length());
        right = new BeltLane(right.length());
    }

    // --- persistence and sync --------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_LEFT, CoreNbt.writeLane(left));
        tag.put(KEY_RIGHT, CoreNbt.writeLane(right));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        BeltLane l = tag.contains(KEY_LEFT) ? CoreNbt.readLane(tag.getCompound(KEY_LEFT)) : left;
        BeltLane r = tag.contains(KEY_RIGHT) ? CoreNbt.readLane(tag.getCompound(KEY_RIGHT)) : right;
        if (level != null && level.isClientSide()) tracker.snap(List.of(left, right), List.of(l, r));
        left = l;
        right = r;
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
