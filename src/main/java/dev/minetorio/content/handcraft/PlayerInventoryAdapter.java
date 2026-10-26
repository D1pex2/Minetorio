package dev.minetorio.content.handcraft;

import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.core.item.Inventory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The player's main inventory (36 slots: hotbar and backpack, not armor or offhand) seen as a core
 * {@link Inventory}. Only plain stacks ({@link MachineItems#isPlain}) are counted and removed, so a
 * named or enchanted item is never consumed by hand crafting. {@link #add} inserts plain stacks and
 * drops what does not fit at the player's feet, so it always reports the full amount. Removal takes
 * from the backpack before the hotbar. Read-only use ({@link #count}) is safe on the client.
 */
public final class PlayerInventoryAdapter implements Inventory {
    private final Player player;

    public PlayerInventoryAdapter(Player player) { this.player = player; }

    private List<ItemStack> slots() { return player.getInventory().items; }

    private static boolean matches(ItemStack s, String item) {
        return MachineItems.isPlain(s) && MachineItems.id(s).equals(item);
    }

    @Override
    public int count(String item) {
        int n = 0;
        for (ItemStack s : slots()) if (matches(s, item)) n += s.getCount();
        return n;
    }

    /** Plain item counts of the main inventory by id (the client's cache key for craftable counts). */
    public static Map<String, Integer> plainCounts(Player player) {
        Map<String, Integer> out = new HashMap<>();
        for (ItemStack s : player.getInventory().items) {
            if (MachineItems.isPlain(s)) out.merge(MachineItems.id(s), s.getCount(), Integer::sum);
        }
        return out;
    }

    @Override
    public int remove(String item, int n) {
        int left = n;
        List<ItemStack> slots = slots();
        for (int i = slots.size() - 1; i >= 0 && left > 0; i--) {
            ItemStack s = slots.get(i);
            if (!matches(s, item)) continue;
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            if (s.isEmpty()) slots.set(i, ItemStack.EMPTY);
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
        List<ItemStack> slots = slots();
        for (int i = 0; i < slots.size() && left > 0; i++) {
            ItemStack s = slots.get(i);
            if (!matches(s, item) || s.getCount() >= max) continue;
            int put = Math.min(left, max - s.getCount());
            s.grow(put);
            left -= put;
        }
        for (int i = 0; i < slots.size() && left > 0; i++) {
            if (!slots.get(i).isEmpty()) continue;
            int put = Math.min(left, max);
            slots.set(i, new ItemStack(it, put));
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
