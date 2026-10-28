package dev.minetorio.content.fluid;

import dev.minetorio.content.nbt.CopyableSettings;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.fluid.FluidData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A pipe: a segment node of {@link FluidData#PIPE_CAPACITY} (no ticking; the network holds the
 * fluid, see {@link FluidNode}). Its settings for blueprints ({@link CopyableSettings}) are the
 * closed sides, as a mask {@code closed} (bit = {@link net.minecraft.core.Direction#ordinal()},
 * world directions).
 */
public class PipeBlockEntity extends BlockEntity implements FluidNodeEntity, CopyableSettings, FluidGaugeSource {
    public static final String KEY_CLOSED = "closed";

    private final FluidNode node;

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.PIPE.get(), pos, state, FluidData.PIPE_CAPACITY);
    }

    protected PipeBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state, double capacity) {
        super(type, pos, state);
        node = new FluidNode(this, capacity);
    }

    @Override
    public FluidNode fluidNode() { return node; }

    @Override
    public dev.minetorio.core.fluid.FluidGauge fluidGauge() { return node.gauge(); }

    /** {@code IFluidHandler} for other mods: the pipe's whole segment. */
    public IFluidHandler fluidHandler() { return new FluidBoxHandler(node::liveSegment, null, this::setChanged); }

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

    // --- settings -------------------------------------------------------------------------------------

    @Override
    public CompoundTag saveSettings() {
        CompoundTag tag = new CompoundTag();
        if (getBlockState().getBlock() instanceof PipeBlock) tag.putByte(KEY_CLOSED, (byte) PipeBlock.closedMask(getBlockState()));
        return tag;
    }

    @Override
    public void loadSettings(CompoundTag tag) {
        if (!tag.contains(KEY_CLOSED) || level == null || !(getBlockState().getBlock() instanceof PipeBlock)) return;
        BlockState next = PipeBlock.withClosedMask(getBlockState(), tag.getByte(KEY_CLOSED) & 63, level, worldPosition);
        if (next != getBlockState()) level.setBlock(worldPosition, next, Block.UPDATE_ALL);
    }

    // --- persistence and sync -------------------------------------------------------------------------

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

    /** Only the alt-mode gauge. */
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
