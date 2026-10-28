package dev.minetorio.content.power;

import dev.minetorio.content.alt.AltSource;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.Units;
import dev.minetorio.core.alt.AltInfo;
import dev.minetorio.core.alt.AltRules;
import dev.minetorio.core.alt.AltSync;
import dev.minetorio.core.power.Accumulator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Accumulator: the core {@link Accumulator} (5 MJ, 300 kW) registered with the
 * {@link PowerGridManager} as a storage while loaded. The stored energy is saved.
 *
 * <p><b>Client sync.</b> Only the lamps lit ({@link #LAMPS} charge-level lamps, see
 * {@link Accumulator#litLamps}) and whether it is charging, discharging or idle are synced, checked
 * every {@value AltSync#PERIOD} ticks and sent only when they changed; the renderer lights the lamps.
 */
public class AccumulatorBlockEntity extends MultiblockControllerBlockEntity implements AltSource {
    /** Charge-level lamps on the front (art/gen_energy.py: ACC_LAMPS). */
    public static final int LAMPS = 5;
    private static final String KEY_ENERGY = "energy";
    private static final String KEY_LAMPS = "lamps";
    private static final String KEY_FLOW = "flow";

    private final Accumulator accumulator = new Accumulator();
    private final AltSync altSync = new AltSync();
    private AltInfo clientAlt = AltInfo.NONE;
    /** Synced (client: as received; server: as last sent). */
    private int lamps;
    private Accumulator.Flow flow = Accumulator.Flow.IDLE;

    public AccumulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACCUMULATOR.get(), pos, state);
    }

    public Direction facing() { return getBlockState().getValue(MultiblockControllerBlock.FACING); }

    public MultiblockShape shape() { return ((MultiblockControllerBlock) getBlockState().getBlock()).shape(); }

    public Accumulator accumulator() { return accumulator; }

    /** Lamps lit (client: as synced). */
    public int lamps() { return lamps; }

    /** Charging / discharging / idle (client: as synced). */
    public Accumulator.Flow flow() { return flow; }

    public boolean isConnected() {
        return level instanceof ServerLevel server && PowerGridManager.get(server).networkOf(worldPosition) != null;
    }

    void tickServer(Level level) {
        accumulator.endTick();
        if (accumulator.lastFlow() != 0) setChanged();
        if (AltSync.due(level.getGameTime(), worldPosition.asLong())) {
            int nowLamps = Accumulator.litLamps(accumulator.energy(), LAMPS);
            Accumulator.Flow nowFlow = accumulator.flow();
            if (nowLamps != lamps || nowFlow != flow) {
                lamps = nowLamps;
                flow = nowFlow;
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        AltSource.tickAlt(this, altSync);
    }

    public Component status() {
        int percent = (int) Math.floor(accumulator.level() * 100);
        long kw = Math.round((double) Math.abs(accumulator.lastFlow()) / Units.JOULES_PER_TICK_PER_KW);
        String key = switch (accumulator.flow()) {
            case CHARGING -> "minetorio.accumulator.charging";
            case DISCHARGING -> "minetorio.accumulator.discharging";
            default -> "minetorio.accumulator.idle";
        };
        if (!isConnected()) key = "minetorio.accumulator.not_connected";
        return Component.translatable(key, percent, String.format("%.2f", accumulator.energy() / 1e6), kw);
    }

    @Override
    public AltInfo computeAlt() { return AltRules.gridMember(isConnected()); }

    @Override
    public AltInfo altInfo() { return clientAlt; }

    // --- grid registration, persistence and sync ---------------------------------------------------

    private AABB footprint() { return ElectricConsumerHelper.box(shape().footprint(worldPosition, facing())); }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) PowerGridManager.get(server).registerStorage(worldPosition, footprint(), accumulator);
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

    private void unregister() {
        if (level instanceof ServerLevel server) {
            PowerGridManager grid = PowerGridManager.getIfPresent(server);
            if (grid != null) grid.unregister(worldPosition, accumulator);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong(KEY_ENERGY, accumulator.energy());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_ENERGY)) accumulator.setEnergy(tag.getLong(KEY_ENERGY));
        if (tag.contains(KEY_LAMPS)) lamps = tag.getByte(KEY_LAMPS); // client update
        if (tag.contains(KEY_FLOW)) flow = Accumulator.Flow.values()[Math.floorMod(tag.getByte(KEY_FLOW), Accumulator.Flow.values().length)];
        clientAlt = AltSource.read(tag, clientAlt);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (level != null && !level.isClientSide()) {
            lamps = Accumulator.litLamps(accumulator.energy(), LAMPS);
            flow = accumulator.flow();
        }
        tag.putByte(KEY_LAMPS, (byte) lamps);
        tag.putByte(KEY_FLOW, (byte) flow.ordinal());
        AltSource.write(tag, this, altSync);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
