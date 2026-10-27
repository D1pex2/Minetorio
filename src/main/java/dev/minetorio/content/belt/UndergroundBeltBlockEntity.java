package dev.minetorio.content.belt;

import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.belt.BeltLane;
import dev.minetorio.core.belt.BeltTier;
import dev.minetorio.core.belt.LaneSink;
import java.util.List;
import java.util.OptionalInt;
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
 * Lanes of one underground belt piece. An entrance's lanes run from its back edge to the start of
 * its exit: length {@code distance} when paired (0.5 when not, the item stops under the hood).
 * An exit's lanes have length 1 and start at its back edge; only their front half is visible.
 * Entrance and exit store each other as {@code distance} along the facing. A piece loaded from
 * disk checks its partner on its first ticks: if the partner's position is loaded and holds no
 * piece pairing back (it was removed while this piece was unloaded), it unpairs and looks for a
 * new partner.
 */
public class UndergroundBeltBlockEntity extends BlockEntity implements BeltNode {
    private static final String KEY_LEFT = "left";
    private static final String KEY_RIGHT = "right";
    private static final String KEY_DISTANCE = "distance";
    /** Lane length of an unpaired entrance: the open half. */
    public static final double UNPAIRED_LENGTH = 0.5;
    /** Side-loading positions: the middle of the open half. */
    private static final double SIDE_LOAD_INPUT = 0.25;
    private static final double SIDE_LOAD_OUTPUT = 0.75;

    private BeltLane left;
    private BeltLane right;
    /** Blocks to the partner along the facing (exit: against it); 0 = unpaired. */
    private int distance;
    /** True until NBT is loaded: a freshly placed piece looks for a partner when it loads. */
    private boolean fresh = true;
    /** Set when loaded from NBT: the recorded partner still has to be checked (server, see class doc). */
    private boolean checkPartner;
    private final LaneTracker tracker = new LaneTracker();

    public UndergroundBeltBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UNDERGROUND_BELT.get(), pos, state);
        double len = laneLength();
        left = new BeltLane(len);
        right = new BeltLane(len);
    }

    public UndergroundBeltBlock.Type type() {
        return getBlockState().getValue(UndergroundBeltBlock.TYPE);
    }

    public Direction facing() {
        return getBlockState().getValue(UndergroundBeltBlock.FACING);
    }

    public BeltTier tier() {
        return ((UndergroundBeltBlock) getBlockState().getBlock()).tier();
    }

    public boolean isPaired() { return distance > 0; }

    public int distance() { return distance; }

    @Nullable
    public BlockPos partnerPos() {
        if (!isPaired()) return null;
        Direction toward = type() == UndergroundBeltBlock.Type.INPUT ? facing() : facing().getOpposite();
        return worldPosition.relative(toward, distance);
    }

    public BeltLane lane(LaneSide side) {
        return side == LaneSide.LEFT ? left : right;
    }

    public LaneTracker tracker() { return tracker; }

    private double laneLength() {
        if (type() == UndergroundBeltBlock.Type.OUTPUT) return 1.0;
        return isPaired() ? distance : UNPAIRED_LENGTH;
    }

    // --- pairing ---------------------------------------------------------------------------------

    /** Pairs with the nearest matching unpaired partner, if any (server side, no-op when paired). */
    public void tryLink() {
        if (level == null || level.isClientSide() || isPaired()) return;
        UndergroundBeltBlock block = (UndergroundBeltBlock) getBlockState().getBlock();
        OptionalInt d = block.findPartner(level, worldPosition, facing(), type());
        if (d.isEmpty()) return;
        Direction toward = type() == UndergroundBeltBlock.Type.INPUT ? facing() : facing().getOpposite();
        if (level.getBlockEntity(worldPosition.relative(toward, d.getAsInt())) instanceof UndergroundBeltBlockEntity other) {
            other.setDistance(d.getAsInt());
            setDistance(d.getAsInt());
        }
    }

    private void setDistance(int d) {
        distance = d;
        double len = laneLength();
        if (len != left.length()) {
            left = LaneStacks.resize(left, len, false, level, worldPosition);
            right = LaneStacks.resize(right, len, false, level, worldPosition);
        }
        if (level != null) {
            BeltTicker.of(level).markDirty();
            if (!level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        setChanged();
    }

    /** Set while this piece is being fast-replaced: its removal must neither drop nor unlink. */
    private boolean replacing;

    /** Removes and returns both lanes {left, right} for a fast-replace; removal then keeps the pair. */
    public BeltLane[] takeForReplace() {
        replacing = true;
        BeltLane[] taken = {left, right};
        left = new BeltLane(left.length());
        right = new BeltLane(right.length());
        return taken;
    }

    /** Takes over a replaced piece's pairing distance and lanes. */
    public void restore(BeltLane l, BeltLane r, int d) {
        fresh = false;
        distance = d;
        double len = laneLength();
        left = LaneStacks.resize(l, len, false, level, worldPosition);
        right = LaneStacks.resize(r, len, false, level, worldPosition);
        if (level != null) {
            BeltTicker.of(level).markDirty();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        setChanged();
    }

    /** Drops the lane items and unpairs the partner (block removal, server side). */
    public void dropItemsAndUnlink() {
        if (level == null || replacing) return;
        LaneStacks.drop(level, worldPosition, left);
        LaneStacks.drop(level, worldPosition, right);
        left = new BeltLane(left.length());
        right = new BeltLane(right.length());
        UndergroundBeltBlockEntity other = partner();
        if (other != null) other.setDistance(0);
        distance = 0;
    }

    /** The partner piece if it is loaded and paired back with this one, else null. */
    @Nullable
    private UndergroundBeltBlockEntity partner() {
        BlockPos p = partnerPos();
        return p != null && level != null && level.getBlockEntity(p) instanceof UndergroundBeltBlockEntity other
                && other.type() != type() && other.facing() == facing() && worldPosition.equals(other.partnerPos()) ? other : null;
    }

    /**
     * Unpairs (and looks for a new partner) if the recorded partner's position is loaded but holds no
     * piece pairing back; keeps waiting while that position is unloaded.
     */
    private void checkPartner() {
        BlockPos p = partnerPos();
        if (p == null) {
            checkPartner = false;
            return;
        }
        if (!level.isLoaded(p)) return;
        checkPartner = false;
        if (partner() != null) return;
        setDistance(0);
        tryLink();
    }

    // --- ticking -------------------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null) return;
        BeltTicker.of(level).add(this);
        if (fresh) tryLink();
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
        if (type() == UndergroundBeltBlock.Type.OUTPUT) return List.of(worldPosition.relative(facing()));
        BlockPos p = partnerPos();
        return p == null ? List.of() : List.of(p);
    }

    @Override
    public void tickBelt() {
        if (level == null) return;
        if (checkPartner && !level.isClientSide()) checkPartner();
        double speed = tier().speed();
        tracker.beginTick(level);
        tracker.tick(level, left, speed, next(LaneSide.LEFT));
        tracker.tick(level, right, speed, next(LaneSide.RIGHT));
    }

    @Nullable
    private LaneSink next(LaneSide side) {
        if (type() == UndergroundBeltBlock.Type.OUTPUT) return BeltGeometry.lazySinkTo(level, worldPosition, facing(), side);
        BlockPos p = partnerPos();
        if (p == null) return null;
        return BeltTicker.of(level).at(p) instanceof UndergroundBeltBlockEntity exit
                && exit.type() == UndergroundBeltBlock.Type.OUTPUT
                && worldPosition.equals(exit.partnerPos()) ? exit.lane(side).asSink() : null;
    }

    @Override
    public void afterBeltTick() {
        tracker.afterTick(this, List.of(left, right));
    }

    @Nullable
    @Override
    public LaneSink incoming(BlockPos at, Direction travel, LaneSide side) {
        boolean input = type() == UndergroundBeltBlock.Type.INPUT;
        if (input && travel == facing()) return lane(side).asSink();
        LaneSide near = BeltGeometry.nearLane(facing(), travel);
        if (near == null) return null; // exits are closed at the back; nothing enters head-on
        BeltLane target = lane(near);
        double at0 = input ? SIDE_LOAD_INPUT : SIDE_LOAD_OUTPUT;
        return (item, overflow) -> target.insertAt(item, Math.min(at0 + overflow, target.length()));
    }

    // --- persistence and sync --------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(KEY_DISTANCE, distance);
        tag.put(KEY_LEFT, CoreNbt.writeLane(left));
        tag.put(KEY_RIGHT, CoreNbt.writeLane(right));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fresh = false;
        checkPartner = true;
        distance = tag.getInt(KEY_DISTANCE);
        BeltLane l = tag.contains(KEY_LEFT) ? CoreNbt.readLane(tag.getCompound(KEY_LEFT)) : new BeltLane(laneLength());
        BeltLane r = tag.contains(KEY_RIGHT) ? CoreNbt.readLane(tag.getCompound(KEY_RIGHT)) : new BeltLane(laneLength());
        if (level != null && level.isClientSide()) {
            tracker.snap(List.of(left, right), List.of(l, r));
            BeltTicker.of(level).markDirty();
        }
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
