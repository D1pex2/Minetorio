package dev.minetorio.content.power;

import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.Units;
import dev.minetorio.core.fluid.FluidBox;
import dev.minetorio.core.fluid.SteamPlant;
import dev.minetorio.core.power.PowerProducer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Steam engine: the core {@link SteamPlant.Engine} (up to 900 kW from its steam) registered with
 * the {@link PowerGridManager} as a {@link PowerProducer} while loaded. It joins a network when a
 * pole covers any of its cells; outside every network it produces nothing and keeps its steam.
 * Working (flywheel spins) = produced energy recently.
 */
public class SteamEngineBlockEntity extends SteamMachineBlockEntity {
    private static final String KEY_STEAM = "steam";
    /** Flywheel spin while working, degrees per tick (client). */
    private static final float FLYWHEEL_DEGREES_PER_TICK = 18f;

    private final SteamPlant.Engine engine = new SteamPlant.Engine();
    /** Joules produced at the end of the last tick (by the grid). */
    private long produced;
    private long lastOutput;

    private final PowerProducer producer = new PowerProducer() {
        @Override
        public long maxOutput() { return engine.maxOutput(); }

        @Override
        public void produce(long joules) {
            engine.produce(joules);
            produced += joules;
            if (joules > 0) setChanged();
        }
    };

    public SteamEngineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEAM_ENGINE.get(), pos, state);
    }

    public FluidBox steam() { return engine.steam(); }

    /** The producer registered with the grid. */
    public PowerProducer producer() { return producer; }

    @Override
    public FluidBox fluidBox(int port) { return engine.steam(); }

    @Override
    protected float animationDegreesPerTick() { return FLYWHEEL_DEGREES_PER_TICK; }

    @Override
    protected void tickMachine(Level level) {
        lastOutput = produced;
        produced = 0;
        setActive(level, lastOutput > 0);
    }

    @Override
    public Component status() {
        return Component.translatable("minetorio.steam_engine.status", units(engine.steam().amount()),
                Math.round((double) lastOutput / Units.JOULES_PER_TICK_PER_KW));
    }

    // --- grid registration -------------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            PowerGridManager.get(server).registerProducer(worldPosition,
                    ElectricConsumerHelper.box(shape().footprint(worldPosition, facing())), producer);
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

    private void unregister() {
        if (level instanceof ServerLevel server) PowerGridManager.get(server).unregister(worldPosition, producer);
    }

    // --- persistence -------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_STEAM, CoreNbt.writeFluid(engine.steam()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_STEAM)) CoreNbt.loadFluidInto(engine.steam(), tag.getCompound(KEY_STEAM));
    }
}
