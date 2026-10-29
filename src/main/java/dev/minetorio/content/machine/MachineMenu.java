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
 * inventory (80 unique slots), plus synced {@link ContainerData}. Machine slots are views of the
 * machine (server) whose {@code getItem} is a copy, so shift-click moves go through
 * {@link Slot#safeInsert} and {@link Slot#remove} instead of mutating slot stacks.
 */
public abstract class MachineMenu extends AbstractContainerMenu {
    protected final MachineContainer machine;
    protected final ContainerData data;
    private int machineSlots;
    public static final int INVENTORY_HEIGHT = 8 * 18;

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

    /** Adds the player's inventory with its top-left slot at ({@code x}, {@code y}) and eight rows of ten unique slots. */
    protected void addPlayerInventory(Inventory inventory, int x, int y) {
        var main = dev.minetorio.content.character.CharacterInventory.main(inventory);
        for (int row = 0; row < 8; row++)
            for (int col = 0; col < 10; col++) addSlot(new Slot(main, row * 10 + col, x + col * 18, y + row * 18));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || RemoteView.blocksTransfer(player)) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        int playerStart = machineSlots;
        int end = slots.size();
        if (index < machineSlots) {
            ItemStack moving = slot.getItem().copy(); // client slots hold real stacks: never shrink them here
            int before = moving.getCount();
            moveItemStackTo(moving, playerStart, end, false);
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
            return ItemStack.EMPTY;
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
