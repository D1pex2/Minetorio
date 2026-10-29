package dev.minetorio.content.fluid;

import dev.minetorio.core.fluid.FluidStore;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * {@link IFluidHandler} view of a {@link FluidStore} (a machine box or a pipe segment, looked up on
 * every call since segments change), so other mods' pumps and tanks can use pipes and machine
 * ports. 1 unit = 1 mB. Only water is exposed: Minetorio's other fluids have no registered
 * Minecraft fluid, so a store holding them reads as empty and refuses everything. Minetorio itself
 * never goes through this.
 */
public final class FluidBoxHandler implements IFluidHandler {
    private static final String WATER = "water";

    private final Supplier<? extends FluidStore> store;
    @Nullable
    private final String filter;
    private final Runnable onChange;

    public FluidBoxHandler(Supplier<? extends FluidStore> store, @Nullable String filter, Runnable onChange) {
        this.store = store;
        this.filter = filter;
        this.onChange = onChange;
    }

    @Override
    public int getTanks() { return 1; }

    @Override
    public FluidStack getFluidInTank(int tank) {
        FluidStore box = store.get();
        if (box == null) return FluidStack.EMPTY;
        int amount = (int) Math.floor(box.amount());
        return WATER.equals(box.fluid()) && amount > 0 ? new FluidStack(Fluids.WATER, amount) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        FluidStore box = store.get();
        return box == null ? 0 : (int) box.capacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        FluidStore box = store.get();
        return stack.is(Fluids.WATER) && FluidPorts.passes(filter, WATER)
                && (!(box instanceof dev.minetorio.core.fluid.FluidBox typed) || typed.accepts(WATER));
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        FluidStore box = store.get();
        if (box == null || resource.isEmpty() || !isFluidValid(0, resource)) return 0;
        if (box.fluid() != null && !WATER.equals(box.fluid())) return 0;
        int amount = Math.min(resource.getAmount(), (int) Math.floor(box.room()));
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
        FluidStore box = store.get();
        if (box == null || maxDrain <= 0 || !WATER.equals(box.fluid())) return FluidStack.EMPTY;
        int amount = Math.min(maxDrain, (int) Math.floor(box.amount()));
        if (amount <= 0) return FluidStack.EMPTY;
        if (action.execute()) {
            box.extract(amount);
            onChange.run();
        }
        return new FluidStack(Fluids.WATER, amount);
    }
}
