package dev.minetorio.content.character;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Container zero on both sides: the vanilla menu with the additional real slots synchronized. */
public final class CharacterMenu extends InventoryMenu {
    private final Player owner;
    public CharacterMenu(Inventory inventory, boolean active, Player owner) {
        super(inventory, active, owner);
        this.owner = owner;
        for (int i = 0; i < slots.size(); i++) {
            if (i >= 9 && i != 45) continue;
            Slot old = slots.get(i);
            Slot hidden = new Slot(old.container, old.getContainerSlot(), -10000, -10000);
            hidden.index = i;
            slots.set(i, hidden);
        }
        Container main = CharacterInventory.main(inventory);
        for (int i = 0; i < CharacterInventory.MAIN_SIZE; i++) {
            int x = 8 + (i % 10) * 18;
            int y = 36 + (i / 10) * 18;
            Slot slot = new Slot(main, i, x, y);
            if (i < 36) { slot.index = menuIndex(i); slots.set(menuIndex(i), slot); }
            else addSlot(slot);
        }
        addSlot(new Slot(inventory, CharacterInventory.TOOL_SLOT, 8, 204) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        addSlot(new Slot(inventory, CharacterInventory.TRASH_SLOT, 30, 204));
        CharacterShortcuts shortcuts = (CharacterShortcuts) owner;
        for (int i = 0; i < CharacterShortcuts.ROWS * CharacterShortcuts.COLUMNS; i++) {
            final int index = i;
            addDataSlot(new DataSlot() {
                @Override public int get() { return shortcuts.shortcut(index); }
                @Override public void set(int value) { shortcuts.shortcut(index, value); }
            });
        }
        addDataSlot(new DataSlot() {
            @Override public int get() { return shortcuts.selectedRow(); }
            @Override public void set(int value) { shortcuts.selectedRow(value); }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return shortcuts.visibleRows(); }
            @Override public void set(int value) { shortcuts.visibleRows(value); }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return shortcuts.emptyHand() ? 1 : 0; }
            @Override public void set(int value) { shortcuts.emptyHand(value != 0); }
        });
    }

    /** Maps a main inventory index to its unique slot in container zero. */
    public static int menuIndex(int mainIndex) {
        if (mainIndex < 0 || mainIndex >= CharacterInventory.MAIN_SIZE) throw new IndexOutOfBoundsException(mainIndex);
        return mainIndex < 9 ? 36 + mainIndex : mainIndex < 36 ? mainIndex : 46 + mainIndex - 36;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        // The crafting grid and hidden equipment remain inaccessible in the character UI.
        if ((slot >= 0 && slot < 9) || slot == 45 || slot == 90) return;
        super.clicked(slot, button, type, player);
    }


    /** Server-authoritative shortcut actions, carried via the existing container-zero button packet. */
    @Override
    public boolean clickMenuButton(Player player, int action) {
        if (player != owner || !player.isAlive()) return false;
        CharacterShortcuts shortcuts = (CharacterShortcuts) player;
        if (action >= 1000 && action < 1100) {
            ItemStack held = getCarried();
            shortcuts.shortcut(action - 1000, held.isEmpty() ? 0 : Item.getId(held.getItem()));
        } else if (action >= 1100 && action < 1200) {
            shortcuts.shortcut(action - 1100, 0);
        } else if (action >= 2000 && action < 2100) {
            int id = shortcuts.shortcut(action - 2000);
            Container main = CharacterInventory.main(player.getInventory());
            int column = (action - 2000) % CharacterShortcuts.COLUMNS;
            int selected = column < 9 ? column : player.getInventory().selected;
            if (selected < 0 || selected >= 9) return false;
            int source = -1;
            if (id != 0) {
                for (int i = 0; i < main.getContainerSize(); i++) {
                    ItemStack stack = main.getItem(i);
                    if (!stack.isEmpty() && Item.getId(stack.getItem()) == id) { source = i; break; }
                }
            }
            if (source < 0) {
                if (main.getItem(selected).isEmpty()) {
                    shortcuts.emptyHand(false);
                    player.getInventory().selected = selected;
                    if (player instanceof ServerPlayer server) server.connection.send(new ClientboundSetCarriedItemPacket(selected));
                    return true;
                }
                for (int i = 0; i < main.getContainerSize(); i++) if (main.getItem(i).isEmpty()) { source = i; break; }
            }
            if (source < 0) {
                shortcuts.emptyHand(true);
                player.getInventory().selected = selected;
                if (player instanceof ServerPlayer server) server.connection.send(new ClientboundSetCarriedItemPacket(selected));
                broadcastChanges();
                return true;
            }
            shortcuts.emptyHand(false);
            player.getInventory().selected = selected;
            if (player instanceof ServerPlayer server) server.connection.send(new ClientboundSetCarriedItemPacket(selected));
            if (source != selected) {
                ItemStack a = main.getItem(selected), b = main.getItem(source);
                main.setItem(source, a);
                main.setItem(selected, b);
                main.setChanged();
            }
        } else if (action == 3000) {
            shortcuts.selectedRow((shortcuts.selectedRow() + 1) % CharacterShortcuts.ROWS);
        } else if (action >= 3101 && action <= 3104) {
            shortcuts.visibleRows(action - 3100);
        } else return false;
        broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // This menu has no second inventory to transfer to; normal clicks rearrange the main slots.
        return ItemStack.EMPTY;
    }
}
