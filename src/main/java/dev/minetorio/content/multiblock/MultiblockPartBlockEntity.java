package dev.minetorio.content.multiblock;

import dev.minetorio.content.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Links a {@link MultiblockPartBlock} to its controller. Capability providers registered for
 * this block entity type should delegate to {@link #controller()}.
 */
public class MultiblockPartBlockEntity extends BlockEntity {
    private static final String KEY_OFFSET = "controller_offset";

    /** Controller position relative to this part; ZERO means "not linked". */
    private BlockPos controllerOffset = BlockPos.ZERO;

    public MultiblockPartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MULTIBLOCK_PART.get(), pos, state);
    }

    /** Controller position relative to this part. */
    public BlockPos controllerOffset() { return controllerOffset; }

    public void setControllerOffset(BlockPos offset) {
        controllerOffset = offset.immutable();
        setChanged();
    }

    public boolean isLinked() { return !controllerOffset.equals(BlockPos.ZERO); }

    public BlockPos controllerPos() { return worldPosition.offset(controllerOffset); }

    /** The controller's block entity, or null if unlinked, missing or not loaded. */
    @Nullable
    public MultiblockControllerBlockEntity controller() {
        if (level == null || !isLinked()) return null;
        return level.getBlockEntity(controllerPos()) instanceof MultiblockControllerBlockEntity c ? c : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_OFFSET, NbtUtils.writeBlockPos(controllerOffset));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerOffset = NbtUtils.readBlockPos(tag, KEY_OFFSET).orElse(BlockPos.ZERO);
    }

    /** Synced so the client can delegate interaction and picking to the controller. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
