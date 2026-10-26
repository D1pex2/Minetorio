package dev.minetorio.content.multiblock;

import dev.minetorio.content.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;

/**
 * Links a {@link MultiblockPartBlock} to its controller: the controller's position (as an offset)
 * and its block, which supplies the part's drops. Capabilities are delegated to the controller
 * via {@link #controllerCapability} (registered in {@code ModCapabilities}).
 */
public class MultiblockPartBlockEntity extends BlockEntity {
    private static final String KEY_OFFSET = "controller_offset";
    private static final String KEY_BLOCK = "controller_block";

    /** Controller position relative to this part; ZERO means "not linked". */
    private BlockPos controllerOffset = BlockPos.ZERO;
    @Nullable
    private Block controllerBlock;

    public MultiblockPartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MULTIBLOCK_PART.get(), pos, state);
    }

    /** Controller position relative to this part. */
    public BlockPos controllerOffset() { return controllerOffset; }

    /** The controller's block (for drops), or null if unlinked. */
    @Nullable
    public Block controllerBlock() { return controllerBlock; }

    public void link(BlockPos offset, Block controller) {
        controllerOffset = offset.immutable();
        controllerBlock = controller;
        setChanged();
    }

    public boolean isLinked() { return !controllerOffset.equals(BlockPos.ZERO); }

    public BlockPos controllerPos() { return worldPosition.offset(controllerOffset); }

    /** The controller's block entity, or null if unlinked, missing or not loaded. */
    @Nullable
    public MultiblockControllerBlockEntity controller() {
        if (level == null || !isLinked() || !level.isLoaded(controllerPos())) return null;
        return level.getBlockEntity(controllerPos()) instanceof MultiblockControllerBlockEntity c ? c : null;
    }

    /**
     * The controller's capability, or null if unlinked, the controller's chunk is not loaded, or
     * there is no controller block at the linked position.
     */
    @Nullable
    public <T, C> T controllerCapability(BlockCapability<T, C> capability, C context) {
        if (level == null || !isLinked()) return null;
        BlockPos c = controllerPos();
        if (!level.isLoaded(c)) return null;
        if (!(level.getBlockState(c).getBlock() instanceof MultiblockControllerBlock)) return null;
        return level.getCapability(capability, c, context);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_OFFSET, NbtUtils.writeBlockPos(controllerOffset));
        if (controllerBlock != null) tag.putString(KEY_BLOCK, BuiltInRegistries.BLOCK.getKey(controllerBlock).toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerOffset = NbtUtils.readBlockPos(tag, KEY_OFFSET).orElse(BlockPos.ZERO);
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(KEY_BLOCK));
        controllerBlock = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
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
