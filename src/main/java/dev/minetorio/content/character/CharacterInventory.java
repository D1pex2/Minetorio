package dev.minetorio.content.character;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A view of the eighty main slots; armor and offhand retain their vanilla indices. */
public final class CharacterInventory {
    public static final int MAIN_SIZE = 80;
    public static final int VANILLA_MAIN_SIZE = 36;
    public static final int EXTRA_OFFSET = 41;
    public static final int TOOL_SLOT = 85;
    public static final int TRASH_SLOT = 86;

    private CharacterInventory() {}

    public static int inventoryIndex(int mainIndex) {
        if (mainIndex < 0 || mainIndex >= MAIN_SIZE) throw new IndexOutOfBoundsException(mainIndex);
        return mainIndex < VANILLA_MAIN_SIZE ? mainIndex : EXTRA_OFFSET + mainIndex - VANILLA_MAIN_SIZE;
    }

    /** True only for a view of the supplied player's actual main inventory. */
    public static boolean isMainView(Container container, Inventory inventory) {
        return container instanceof MainView view && view.inventory() == inventory;
    }

    public static Container main(Inventory inventory) {
        return new MainView(inventory);
    }

    private record MainView(Inventory inventory) implements Container {
        @Override public int getContainerSize() { return MAIN_SIZE; }
        @Override public boolean isEmpty() {
            for (int i = 0; i < MAIN_SIZE; i++) if (!getItem(i).isEmpty()) return false;
            return true;
        }
        @Override public ItemStack getItem(int index) { return inventory.getItem(inventoryIndex(index)); }
        @Override public ItemStack removeItem(int index, int count) { return inventory.removeItem(inventoryIndex(index), count); }
        @Override public ItemStack removeItemNoUpdate(int index) { return inventory.removeItemNoUpdate(inventoryIndex(index)); }
        @Override public void setItem(int index, ItemStack stack) { inventory.setItem(inventoryIndex(index), stack); }
        @Override public void setChanged() { inventory.setChanged(); }
        @Override public boolean stillValid(Player player) { return inventory.stillValid(player); }
        @Override public void clearContent() {
            for (int i = 0; i < MAIN_SIZE; i++) setItem(i, ItemStack.EMPTY);
            setChanged();
        }
    }
}
