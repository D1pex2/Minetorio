package dev.minetorio.content.oil;

import dev.minetorio.content.fluid.FluidMachine;
import dev.minetorio.content.fluid.FluidMachineHelper;
import dev.minetorio.content.fluid.FluidGaugeSource;
import dev.minetorio.content.fluid.FluidNode;
import dev.minetorio.core.fluid.FluidGauge;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.content.power.ElectricConsumerHelper;
import dev.minetorio.core.Units;
import dev.minetorio.core.fluid.FluidBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;

public final class PumpjackBlockEntity extends MultiblockControllerBlockEntity implements FluidMachine, FluidGaugeSource {
    private final FluidBox output = new FluidBox(1000, "crude_oil");
    private final FluidMachineHelper fluids = new FluidMachineHelper(this);
    private final ElectricConsumerHelper power = new ElectricConsumerHelper(this, this::demand,
            () -> ElectricConsumerHelper.box(PumpjackBlock.SHAPE.footprint(worldPosition, getBlockState().getValue(MultiblockControllerBlock.FACING))));
    private double progress;
    private boolean working;
    private FluidGauge clientGauge = FluidGauge.EMPTY;
    public PumpjackBlockEntity(BlockPos pos, BlockState state) { super(OilRegistry.PUMPJACK_BE.get(), pos, state); }
    private OilSourceBlockEntity source() {
        if (level == null) return null;
        BlockPos center = PumpjackBlock.SHAPE.anchorOf(worldPosition, getBlockState().getValue(MultiblockControllerBlock.FACING));
        if (!level.hasChunkAt(center.below())) return null;
        return level.getBlockEntity(center.below()) instanceof OilSourceBlockEntity s ? s : null;
    }
    public long demand() { OilSourceBlockEntity source = source(); return source != null && output.room() >= source.amountPerCycle() ? Units.kw(90) : 0; }
    public double satisfaction() { return power.satisfaction(); }
    public boolean isWorking() { return working; }
    @Override public FluidBox fluidBox(int port) { return port == 0 ? output : null; }
    @Override public void fluidChanged() { setChanged(); }
    @Override public FluidGauge fluidGauge() { return clientGauge; }
    public static void serverTick(Level level, BlockPos pos, BlockState state, PumpjackBlockEntity be) {
        OilSourceBlockEntity source = be.source();
        boolean active = source != null && be.demand() > 0 && be.power.satisfaction() > 0;
        if (be.working != active) { be.working=active; level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS); }
        if (!active) return;
        be.progress += be.power.satisfaction();
        if (be.progress >= 20) {
            double amount = source.amountPerCycle();
            if (be.output.room() >= amount && (be.output.fluid() == null || be.output.fluid().equals("crude_oil"))) {
                double generated=be.output.insert("crude_oil", amount);
                dev.minetorio.content.stats.Statistics.record(level,"fluid:crude_oil",generated,0);
                source.extracted(); be.progress -= 20;
            } else be.progress = 20;
        }
        be.setChanged();
    }
    @Override public void onLoad() { super.onLoad(); fluids.onLoad(); power.onLoad(); }
    @Override public void setRemoved() { super.setRemoved(); fluids.onRemoved(); power.onRemoved(); }
    @Override public void onChunkUnloaded() { super.onChunkUnloaded(); fluids.onRemoved(); power.onRemoved(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup); tag.put("output", CoreNbt.writeFluid(output)); tag.putDouble("progress", progress);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup); if (tag.contains("output")) CoreNbt.loadFluidInto(output, tag.getCompound("output")); if(tag.contains("progress"))progress = tag.getDouble("progress"); if(tag.contains("working"))working=tag.getBoolean("working"); clientGauge=FluidNode.readGauge(tag,clientGauge);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) { CompoundTag t=new CompoundTag(); t.putBoolean("working",working); FluidNode.writeGauge(t,FluidGauge.of(output)); return t; }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
