package dev.minetorio.content.power;

import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.fluid.FluidBox;
import dev.minetorio.core.fluid.SteamPlant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A pipe's fluid ({@link SteamPlant#PIPE_CAPACITY}). Each tick it flows with every connected side
 * once: with a neighbor pipe only if its own position is the smaller one (so each pipe pair flows
 * once per tick), with a machine port always (machines leave pipe connections to the pipe).
 */
public class PipeBlockEntity extends BlockEntity {
    private static final String KEY_FLUID = "fluid";

    private final FluidBox box = new FluidBox(SteamPlant.PIPE_CAPACITY);

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIPE.get(), pos, state);
    }

    public FluidBox box() { return box; }

    /** {@code IFluidHandler} for other mods (all sides share the one box). */
    public IFluidHandler fluidHandler() { return new FluidBoxHandler(box, null, this::setChanged); }

    void tickServer(Level level, BlockState state) {
        boolean moved = false;
        for (Direction d : Direction.values()) {
            if (!state.getValue(PipeBlock.SIDES.get(d))) continue;
            BlockPos n = worldPosition.relative(d);
            if (!level.isLoaded(n)) continue;
            if (level.getBlockEntity(n) instanceof PipeBlockEntity other) {
                if (worldPosition.compareTo(n) < 0 && FluidPorts.flow(box, null, other.box, null)) {
                    other.setChanged();
                    moved = true;
                }
                continue;
            }
            FluidPorts.Port port = FluidPorts.portAt(level, n, d.getOpposite());
            FluidPorts.Box machine = port == null ? null : FluidPorts.owner(level, port);
            if (machine != null && FluidPorts.flow(box, null, machine.fluidBox(port.index()), port.filter())) {
                machine.fluidChanged();
                moved = true;
            }
        }
        if (moved) setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_FLUID, CoreNbt.writeFluid(box));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_FLUID)) CoreNbt.loadFluidInto(box, tag.getCompound(KEY_FLUID));
    }
}
