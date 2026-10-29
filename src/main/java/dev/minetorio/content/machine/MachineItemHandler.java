package dev.minetorio.content.machine;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Automated item access to a crafting machine (the {@code IItemHandler} capability of its controller
 * and parts: hoppers, pipes of other mods, ...). Inserts count as automated, so the machine's
 * automated limits apply (inputs: 2 crafts; fuel: 5); only output slots can be extracted from.
 * Stacks with data components are refused (machines store items by id, see {@link MachineItems}).
 */
public final class MachineItemHandler implements IItemHandler {
    /** The machine's slots as both the GUI and this handler see them. */
    public interface Slots {
        int getContainerSize();

        /** Advertised capacity for automation; machines with larger recipe buffers can override it. */
        default int slotLimit(int slot) { return 64; }

        ItemStack getItem(int slot);

        /** How many more of {@code stack} {@code slot} takes now (0 if not allowed there). */
        int room(int slot, ItemStack stack, boolean automated);

        /** Whether the stack's type belongs in {@code slot}, ignoring its current contents. */
        boolean accepts(int slot, ItemStack stack);

        /** Inserts {@code n} × {@code id} into {@code slot}; returns the amount accepted. */
        int insert(int slot, String id, int n, boolean automated);

        boolean isOutput(int slot);

        /** Takes up to {@code n} from an output slot (nothing from other slots); returns the amount. */
        int extractOutput(int slot, int n, boolean simulate);
    }

    private final Slots slots;

    public MachineItemHandler(Slots slots) {
        this.slots = slots;
    }

    @Override
    public int getSlots() { return slots.getContainerSize(); }

    @Override
    public ItemStack getStackInSlot(int slot) { return slots.getItem(slot); }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !MachineItems.isPlain(stack)) return stack;
        int n = Math.min(stack.getCount(), slots.room(slot, stack, true));
        if (n <= 0) return stack;
        if (!simulate) n = slots.insert(slot, MachineItems.id(stack), n, true);
        return stack.copyWithCount(stack.getCount() - n);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || !slots.isOutput(slot)) return ItemStack.EMPTY;
        ItemStack current = slots.getItem(slot);
        if (current.isEmpty()) return ItemStack.EMPTY;
        int n = slots.extractOutput(slot, Math.min(amount, current.getMaxStackSize()), simulate);
        return current.copyWithCount(n);
    }

    @Override
    public int getSlotLimit(int slot) { return slots.slotLimit(slot); }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slots.accepts(slot, stack);
    }
}
