package dev.minetorio.content.robot;

import dev.minetorio.content.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a ghost stands for: the {@link #target() block state} it previews and the {@link #facing()
 * look direction} a player would have placed it with (a block's own facing property may point the
 * other way, and multiblocks and belts are placed from it). {@link #seq()} is the ghost's place in
 * the construction queue. All three are saved and synced to the client (the renderer draws the
 * target).
 *
 * <p>While loaded on the server the ghost is a build job in its level's {@link ConstructionManager}.
 */
public class GhostBlockEntity extends BlockEntity {
    private static final String KEY_TARGET = "target";
    private static final String KEY_FACING = "facing";
    private static final String KEY_SEQ = "seq";
    private static final String KEY_SETTINGS = "settings";

    private BlockState target = Blocks.AIR.defaultBlockState();
    private Direction facing = Direction.NORTH;
    private long seq;
    /** Settings the built block gets ({@link dev.minetorio.content.nbt.CopyableSettings}), or null. Saved, not synced. */
    @javax.annotation.Nullable
    private CompoundTag settings;

    public GhostBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GHOST.get(), pos, state);
    }

    public BlockState target() { return target; }

    public Direction facing() { return facing; }

    public long seq() { return seq; }

    /** Settings applied to the block once built (a blueprint's recipe or filters), or null. */
    @javax.annotation.Nullable
    public CompoundTag settings() { return settings == null ? null : settings.copy(); }

    void setSettings(@javax.annotation.Nullable CompoundTag tag) {
        settings = tag == null || tag.isEmpty() ? null : tag.copy();
        setChanged();
    }

    /** The item that builds this ghost (AIR when the target has none). */
    public Item item() { return target.getBlock().asItem(); }

    public boolean isValid() { return !target.isAir() && item() != Items.AIR; }

    /** Sets what the ghost stands for; {@code seq} 0 = next in the queue (assigned on load). */
    void setup(BlockState target, Direction facing, long seq) {
        this.target = target;
        this.facing = facing;
        this.seq = seq;
        setChanged();
        register();
    }

    /**
     * Changes the previewed state only (a belt's shape or slope after its planned neighbours
     * changed); the item, facing and footprint stay. Synced to clients.
     */
    void retarget(BlockState next) {
        if (next == target) return;
        target = next;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        register();
    }

    private void register() {
        reserve();
        if (level instanceof ServerLevel server && !isRemoved() && isValid()) {
            ConstructionManager m = ConstructionManager.get(server);
            if (seq == 0) {
                seq = m.queue().nextSeq();
                setChanged();
            }
            m.queue().addBuild(ConstructionManager.cell(worldPosition), BuiltInRegistries.ITEM.getKey(item()).toString(), seq);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        unregister();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        unregister();
    }

    /** Reserves the cells this ghost covers (both sides; see {@link GhostReservations}). */
    private void reserve() {
        if (level == null || isRemoved()) return;
        if (isValid()) {
            GhostReservations.reserve(level, worldPosition, Ghosts.footprint(target, worldPosition));
        } else {
            GhostReservations.release(level, worldPosition);
        }
    }

    private void unregister() {
        if (level != null) GhostReservations.release(level, worldPosition);
        if (level instanceof ServerLevel server) {
            ConstructionManager m = ConstructionManager.getIfPresent(server);
            if (m != null) {
                m.queue().get(ConstructionManager.cell(worldPosition))
                        .filter(j -> j.seq() == seq)
                        .ifPresent(j -> m.queue().remove(j.cell()));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_TARGET, NbtUtils.writeBlockState(target));
        tag.putString(KEY_FACING, facing.getSerializedName());
        tag.putLong(KEY_SEQ, seq);
        if (settings != null) tag.put(KEY_SETTINGS, settings.copy());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        target = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound(KEY_TARGET));
        Direction d = Direction.byName(tag.getString(KEY_FACING));
        facing = d == null || d.getAxis().isVertical() ? Direction.NORTH : d;
        seq = tag.getLong(KEY_SEQ);
        settings = tag.contains(KEY_SETTINGS) ? tag.getCompound(KEY_SETTINGS).copy() : null;
        if (level != null && level.isClientSide()) reserve(); // an update from the server
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.remove(KEY_SETTINGS); // the client has no use for them
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
