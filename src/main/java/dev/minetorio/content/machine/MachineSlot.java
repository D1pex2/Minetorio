package dev.minetorio.content.machine;

import net.minecraft.world.item.ItemStack;

/**
 * A machine GUI slot: what may be placed and how much come from {@link MachineContainer#room}
 * (fuel/input rules, the machine's stack limits; output slots have no room). Holds at most one
 * item type, so placing a different item never swaps.
 */
public class MachineSlot extends net.minecraft.world.inventory.Slot {
    private final MachineContainer machine;

    public MachineSlot(MachineContainer machine, int slot, int x, int y) {
        super(machine, slot, x, y);
        this.machine = machine;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        ItemStack current = getItem();
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) return false;
        return machine.room(getContainerSlot(), stack) > 0;
    }

    @Override
    public int getMaxStackSize() { return 64; }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        int room = Math.max(0, machine.room(getContainerSlot(), stack));
        return Math.min(Math.min(getMaxStackSize(), stack.getMaxStackSize()), getItem().getCount() + room);
    }
}
