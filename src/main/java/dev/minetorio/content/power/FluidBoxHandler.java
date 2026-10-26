package dev.minetorio.content.power;

import dev.minetorio.core.fluid.FluidBox;
import javax.annotation.Nullable;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * {@link IFluidHandler} view of one {@link FluidBox}, so other mods' pumps and tanks can use pipes
 * and machine ports. 1 unit = 1 mB. Only water is exposed: steam has no registered Fluid in phase 1,
 * so a steam box reads as empty and refuses everything. Minetorio itself never goes through this.
 */
public final class FluidBoxHandler implements IFluidHandler {
    private static final String WATER = "water";

    private final FluidBox box;
    @Nullable
    private final String filter;
    private final Runnable onChange;

    public FluidBoxHandler(FluidBox box, @Nullable String filter, Runnable onChange) {
        this.box = box;
        this.filter = filter;
        this.onChange = onChange;
    }

    @Override
    public int getTanks() { return 1; }

    @Override
    public FluidStack getFluidInTank(int tank) {
        int amount = (int) Math.floor(box.amount());
        return WATER.equals(box.fluid()) && amount > 0 ? new FluidStack(Fluids.WATER, amount) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) { return (int) box.capacity(); }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return stack.is(Fluids.WATER) && FluidPorts.passes(filter, WATER);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !isFluidValid(0, resource)) return 0;
        if (box.fluid() != null && !WATER.equals(box.fluid())) return 0;
        int room = (int) Math.floor(box.capacity() - box.amount());
        int amount = Math.min(resource.getAmount(), room);
        if (amount <= 0) return 0;
        if (action.execute()) {
            box.insert(WATER, amount);
            onChange.run();
        }
        return amount;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !resource.is(Fluids.WATER)) return FluidStack.EMPTY;
        return drain(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0 || !WATER.equals(box.fluid())) return FluidStack.EMPTY;
        int amount = Math.min(maxDrain, (int) Math.floor(box.amount()));
        if (amount <= 0) return FluidStack.EMPTY;
        if (action.execute()) {
            box.extract(amount);
            onChange.run();
        }
        return new FluidStack(Fluids.WATER, amount);
    }
}
