package dev.minetorio.content.fluid;

import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.content.power.ElectricConsumerHelper;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.fluid.FluidBox;
import dev.minetorio.core.fluid.FluidData;
import dev.minetorio.core.fluid.FluidGauge;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pump: one box ({@link FluidData#PUMP_CAPACITY}) between its input and output port. Both ports are
 * pump ports moving up to {@link FluidData#PUMP_RATE_PER_TICK} times the power satisfaction per
 * tick, so an unpowered pump moves nothing.
 */
public class PumpBlockEntity extends MultiblockControllerBlockEntity implements FluidMachine, FluidGaugeSource {
    private static final String KEY_BOX = "box";

    private final FluidBox box = new FluidBox(FluidData.PUMP_CAPACITY);
    private final FluidMachineHelper fluids = new FluidMachineHelper(this);
    private final ElectricConsumerHelper power = new ElectricConsumerHelper(this, () -> FluidData.PUMP_POWER_J,
            () -> ElectricConsumerHelper.box(MultiblockShape.PUMP.footprint(worldPosition, facing())));
    private final DoubleSupplier rate = () -> FluidData.PUMP_RATE_PER_TICK * power.satisfaction();
    private FluidGauge clientGauge = FluidGauge.EMPTY;
    private long railTransferTick=Long.MIN_VALUE;
    private double railTransferred;

    public PumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PUMP.get(), pos, state);
    }

    public Direction facing() { return getBlockState().getValue(MultiblockControllerBlock.FACING); }

    public FluidBox box() { return box; }

    public double satisfaction() { return power.satisfaction(); }

    /** Общий бюджет обоих железнодорожных портов за один Minecraft tick. Чтение чистое. */
    public double railBudget(long tick) {
        double rate=FluidData.PUMP_RATE_PER_TICK*satisfaction();
        return Double.isFinite(rate)&&rate>0?Math.max(0,rate-(railTransferTick==tick?railTransferred:0)):0;
    }
    public void recordRailTransfer(long tick,double amount) {
        if(railTransferTick!=tick){railTransferTick=tick;railTransferred=0;}
        railTransferred+=amount;
    }

    @Override
    public FluidBox fluidBox(int port) { return box; }

    @Override
    public void fluidChanged() { setChanged(); }

    @Override
    public DoubleSupplier portRate(int port) { return rate; }

    @Override
    public boolean pumpPort(int port) { return true; }

    public Component status() {
        return Component.translatable("minetorio.pump.status", FluidText.contents(box), Math.round(power.satisfaction() * 100));
    }

    @Override
    public FluidGauge fluidGauge() { return clientGauge; }

    @Override
    public void onLoad() {
        super.onLoad();
        fluids.onLoad();
        power.onLoad();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        fluids.onRemoved();
        power.onRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        fluids.onRemoved();
        power.onRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_BOX, CoreNbt.writeFluid(box));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_BOX)) CoreNbt.loadFluidInto(box, tag.getCompound(KEY_BOX));
        clientGauge = FluidNode.readGauge(tag, clientGauge);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        FluidNode.writeGauge(tag, FluidGauge.of(box));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
