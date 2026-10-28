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
import dev.minetorio.core.power.PowerProducer;
import dev.minetorio.core.power.SolarLogic;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Solar panel: a {@link PowerProducer} of {@link PowerProducer.Priority#PRIMARY primary} priority
 * (used before steam) whose output follows {@link SolarLogic} — the level's daylight, its rain and
 * thunder, and whether every one of its nine cells sees the sky (no block of any kind
 * above it, glass included). The sky check is cached and redone every
 * {@value SolarLogic#EXPOSURE_RECHECK_TICKS} ticks (staggered per panel) and at once when a block
 * next to the controller changes. Dimensions without a sky light (Nether, End) give nothing.
 */
public class SolarPanelBlockEntity extends MultiblockControllerBlockEntity implements AltSource {
    private final AltSync altSync = new AltSync();
    private AltInfo clientAlt = AltInfo.NONE;
    private boolean exposed;
    private boolean skyDirty = true;
    private long output;
    /** Set by the network each tick it settles this panel; read (and cleared) by the next tick. */
    private boolean settled;
    private boolean connected;
    private long lastProduced;

    private final PowerProducer producer = new PowerProducer() {
        @Override
        public long maxOutput() { return output; }

        @Override
        public void produce(long joules) {
            settled = true;
            lastProduced = joules;
        }

        @Override
        public Priority priority() { return Priority.PRIMARY; }
    };

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL.get(), pos, state);
    }

    public Direction facing() { return getBlockState().getValue(MultiblockControllerBlock.FACING); }

    public MultiblockShape shape() { return ((MultiblockControllerBlock) getBlockState().getBlock()).shape(); }

    public PowerProducer producer() { return producer; }

    /** Joules per tick it can give now (server). */
    public long output() { return output; }

    /** Whether all nine cells see the sky (server, cached). */
    public boolean isExposed() { return exposed; }

    /** Whether a pole's network reached it in the last tick. */
    public boolean isConnected() { return connected; }

    public void markSkyDirty() { skyDirty = true; }

    void tickServer(Level level) {
        connected = settled;
        settled = false;
        if (skyDirty || Math.floorMod(level.getGameTime() + worldPosition.asLong(), SolarLogic.EXPOSURE_RECHECK_TICKS) == 0) {
            skyDirty = false;
            exposed = checkSky(level);
        }
        output = level.dimensionType().hasSkyLight()
                ? SolarLogic.output(level.getTimeOfDay(1f), level.getRainLevel(1f), level.getThunderLevel(1f), exposed)
                : 0;
        AltSource.tickAlt(this, altSync);
    }

    /**
     * Whether no block at all (glass and leaves included) stands above any cell: the world-surface
     * heightmap (top non-air block + 1) of every column is right above the panel's own layer.
     * Unlike sky light this is exact at once, without waiting for light updates.
     */
    private boolean checkSky(Level level) {
        for (BlockPos p : shape().footprint(worldPosition, facing())) {
            if (level.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX(), p.getZ()) > p.getY() + 1) return false;
        }
        return true;
    }

    public Component status() {
        if (!connected) return Component.translatable("minetorio.solar_panel.not_connected", kw(output));
        if (!exposed) return Component.translatable("minetorio.solar_panel.covered");
        return Component.translatable("minetorio.solar_panel.status", kw(output), kw(SolarLogic.PEAK));
    }

    private static long kw(long joulesPerTick) {
        return Math.round((double) joulesPerTick / Units.JOULES_PER_TICK_PER_KW);
    }

    @Override
    public AltInfo computeAlt() { return AltRules.gridMember(connected); }

    @Override
    public AltInfo altInfo() { return clientAlt; }

    // --- grid registration and sync ----------------------------------------------------------------

    private AABB footprint() { return ElectricConsumerHelper.box(shape().footprint(worldPosition, facing())); }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) PowerGridManager.get(server).registerProducer(worldPosition, footprint(), producer);
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
            if (grid != null) grid.unregister(worldPosition, producer);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        clientAlt = AltSource.read(tag, clientAlt);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        AltSource.write(tag, this, altSync);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
