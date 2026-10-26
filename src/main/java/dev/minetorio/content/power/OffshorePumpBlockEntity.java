package dev.minetorio.content.power;

import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.fluid.FluidBox;
import dev.minetorio.core.fluid.SteamPlant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Offshore pump: while a water source is at its back ({@link OffshorePumpBlock#waterPos}), puts
 * {@link SteamPlant#OFFSHORE_WATER_PER_TICK} water per tick into its box (as much as fits). It
 * stops while the water is gone and resumes if a source is put back. No power needed.
 */
public class OffshorePumpBlockEntity extends SteamMachineBlockEntity {
    private static final String KEY_WATER = "water";

    private final FluidBox water = new FluidBox(SteamPlant.PUMP_CAPACITY);

    public OffshorePumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OFFSHORE_PUMP.get(), pos, state);
    }

    public FluidBox water() { return water; }

    /** Whether the water source is still there. */
    public boolean hasWater() {
        return level != null && OffshorePumpBlock.isWaterSource(level, OffshorePumpBlock.waterPos(worldPosition, facing()));
    }

    @Override
    public FluidBox fluidBox(int port) { return water; }

    @Override
    protected void tickMachine(Level level) {
        boolean source = level.isLoaded(OffshorePumpBlock.waterPos(worldPosition, facing())) && hasWater();
        if (source && water.insert("water", SteamPlant.OFFSHORE_WATER_PER_TICK) > 0) setChanged();
        setActive(level, source); // not "inserted > 0": a full box is "working" too
    }

    @Override
    public Component status() {
        return hasWater() ? Component.translatable("minetorio.offshore_pump.status", units(water.amount()))
                : Component.translatable("minetorio.offshore_pump.no_water");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_WATER, CoreNbt.writeFluid(water));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_WATER)) CoreNbt.loadFluidInto(water, tag.getCompound(KEY_WATER));
    }
}
