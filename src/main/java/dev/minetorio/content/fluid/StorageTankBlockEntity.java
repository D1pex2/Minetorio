package dev.minetorio.content.fluid;

import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.fluid.FluidData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;

/** Storage tank controller: a segment node of {@link FluidData#STORAGE_TANK_CAPACITY} (see {@link FluidNode}). */
public class StorageTankBlockEntity extends MultiblockControllerBlockEntity implements FluidNodeEntity, FluidGaugeSource {
    private final FluidNode node = new FluidNode(this, FluidData.STORAGE_TANK_CAPACITY);

    public StorageTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_TANK.get(), pos, state);
    }

    @Override
    public FluidNode fluidNode() { return node; }

    @Override
    public dev.minetorio.core.fluid.FluidGauge fluidGauge() { return node.gauge(); }

    public Direction facing() { return getBlockState().getValue(MultiblockControllerBlock.FACING); }

    @Override
    public void onLoad() {
        super.onLoad();
        node.onLoad();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        node.onRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        node.onRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        node.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        node.load(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        node.writeUpdate(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
