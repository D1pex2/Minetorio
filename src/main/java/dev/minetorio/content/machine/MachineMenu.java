package dev.minetorio.content.machine;

import dev.minetorio.content.map.RemoteView;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Base menu of the crafting machines: the machine's {@link MachineSlot}s first, then the player's
 * inventory (27) and hotbar (9), plus synced {@link ContainerData}. Machine slots are views of the
 * machine (server) whose {@code getItem} is a copy, so shift-click moves go through
 * {@link Slot#safeInsert} and {@link Slot#remove} instead of mutating slot stacks.
 */
public abstract class MachineMenu extends AbstractContainerMenu {
    protected final MachineContainer machine;
    protected final ContainerData data;
    private int machineSlots;

    protected MachineMenu(MenuType<?> type, int id, MachineContainer machine, ContainerData data, int dataCount) {
        super(type, id);
        this.machine = machine;
        this.data = data;
        checkContainerDataCount(data, dataCount);
        addDataSlots(data);
    }

    /** Adds a machine slot; all of them must be added before {@link #addPlayerInventory}. */
    protected Slot addMachineSlot(Slot slot) {
        machineSlots++;
        return addSlot(slot);
    }

    /** Adds the player's inventory with its top-left slot at ({@code x}, {@code y}) and the hotbar 58 px lower. */
    protected void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, x + col * 18, y + 58));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        int playerStart = machineSlots;
        int hotbarStart = playerStart + 27;
        int end = slots.size();
        if (index < machineSlots) {
            ItemStack moving = slot.getItem().copy(); // client slots hold real stacks: never shrink them here
            int before = moving.getCount();
            moveItemStackTo(moving, playerStart, end, true);
            int moved = before - moving.getCount();
            if (moved <= 0) return ItemStack.EMPTY;
            slot.remove(moved);
            slot.onTake(player, original.copyWithCount(moved));
            return original;
        }
        ItemStack stack = slot.getItem();
        int before = stack.getCount();
        boolean belongs = false;
        for (int i = 0; i < machineSlots && !stack.isEmpty(); i++) {
            Slot target = slots.get(i);
            if (!target.isActive() || !machine.accepts(target.getContainerSlot(), stack)) continue;
            belongs = true;
            if (target.mayPlace(stack)) target.safeInsert(stack);
        }
        if (stack.getCount() == before) {
            if (belongs) return ItemStack.EMPTY; // machine item but no room: stay put (vanilla furnace)
            boolean inHotbar = index >= hotbarStart;
            if (!moveItemStackTo(stack, inHotbar ? playerStart : hotbarStart, inHotbar ? hotbarStart : end, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    /** After every click, stacks the machine could not store go back to the player (see {@link MachineContainer.View#setItem}). */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (RemoteView.blocksTransfer(player)) return; // a GUI open from the remote view only shows (and sets the recipe)
        super.clicked(slotId, button, clickType, player);
        returnOverflow(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        returnOverflow(player);
    }

    private void returnOverflow(Player player) {
        for (ItemStack s : machine.takeOverflow()) player.getInventory().placeItemBackInInventory(s);
    }

    @Override
    public boolean stillValid(Player player) { return machine.stillValid(player) || RemoteView.menuValid(player, this); }
}
