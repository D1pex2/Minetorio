package dev.minetorio.content.handcraft;

import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.character.CharacterInventory;
import dev.minetorio.core.item.Inventory;
import java.util.HashMap;

import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The player's 80 main inventory slots (not armor, offhand, tool or trash) seen as a core
 * {@link Inventory}. Only plain stacks ({@link MachineItems#isPlain}) are counted and removed, so a
 * named or enchanted item is never consumed by hand crafting. {@link #add} inserts plain stacks and
 * drops what does not fit at the player's feet, so it always reports the full amount. Removal takes
 * from the backpack before the hotbar. Read-only use ({@link #count}) is safe on the client.
 */
public final class PlayerInventoryAdapter implements Inventory {
    private final Player player;

    public PlayerInventoryAdapter(Player player) { this.player = player; }

    private Container slots() { return CharacterInventory.main(player.getInventory()); }

    private static boolean matches(ItemStack s, String item) {
        return MachineItems.isPlain(s) && MachineItems.id(s).equals(item);
    }

    @Override
    public int count(String item) {
        int n = 0;
        Container inventory = slots();
        for (int i = 0; i < inventory.getContainerSize(); i++) { ItemStack s = inventory.getItem(i); if (matches(s, item)) n += s.getCount(); }
        return n;
    }

    /** Plain item counts of the main inventory by id (the client's cache key for craftable counts). */
    public static Map<String, Integer> plainCounts(Player player) {
        Map<String, Integer> out = new HashMap<>();
        Container inventory = CharacterInventory.main(player.getInventory());
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (MachineItems.isPlain(s)) out.merge(MachineItems.id(s), s.getCount(), Integer::sum);
        }
        return out;
    }

    @Override
    public int remove(String item, int n) {
        int left = n;
        Container slots = slots();
        for (int i = slots.getContainerSize() - 1; i >= 0 && left > 0; i--) {
            ItemStack s = slots.getItem(i);
            if (!matches(s, item)) continue;
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            if (s.isEmpty()) slots.setItem(i, ItemStack.EMPTY);
            left -= take;
        }
        if (left != n) player.getInventory().setChanged();
        return n - left;
    }

    @Override
    public int add(String item, int n) {
        Item it = MachineItems.item(item);
        if (it == null || n <= 0) return 0;
        int max = new ItemStack(it).getMaxStackSize();
        int left = n;
        Container slots = slots();
        for (int i = 0; i < slots.getContainerSize() && left > 0; i++) {
            ItemStack s = slots.getItem(i);
            if (!matches(s, item) || s.getCount() >= max) continue;
            int put = Math.min(left, max - s.getCount());
            s.grow(put);
            left -= put;
        }
        for (int i = 0; i < slots.getContainerSize() && left > 0; i++) {
            if (!slots.getItem(i).isEmpty()) continue;
            int put = Math.min(left, max);
            slots.setItem(i, new ItemStack(it, put));
            left -= put;
        }
        if (left != n) player.getInventory().setChanged();
        if (left > 0) dropAll(Map.of(item, left));
        return n;
    }

    /** Drops {@code items} at the player's feet in stacks of at most their max size. */
    public void dropAll(Map<String, Integer> items) {
        items.forEach((id, n) -> {
            Item it = MachineItems.item(id);
            if (it == null) return;
            int max = new ItemStack(it).getMaxStackSize();
            for (int left = n; left > 0; left -= max) player.drop(new ItemStack(it, Math.min(left, max)), true, false);
        });
    }
}
