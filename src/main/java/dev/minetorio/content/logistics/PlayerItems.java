package dev.minetorio.content.logistics;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Counting and taking plain items in a player's inventory (main slots, hotbar and offhand; no armor). */
public final class PlayerItems {
    private PlayerItems() {}

    /** Item counts by id. */
    public static Map<String, Integer> counts(Player player) {
        Map<String, Integer> out = new HashMap<>();
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.items) add(out, s);
        for (ItemStack s : inv.offhand) add(out, s);
        return out;
    }

    private static void add(Map<String, Integer> out, ItemStack s) {
        if (!s.isEmpty()) out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
    }

    public static int count(Player player, Item item) {
        int n = 0;
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : inv.offhand) if (s.is(item)) n += s.getCount();
        return n;
    }

    /**
     * Removes up to {@code n} of {@code item}, main inventory first (the hotbar last, so what is in hand
     * goes last); returns how many were taken.
     */
    public static int take(Player player, Item item, int n) {
        Inventory inv = player.getInventory();
        int got = 0;
        for (int pass = 0; pass < 2 && got < n; pass++) {
            for (int i = 0; i < inv.items.size() && got < n; i++) {
                boolean hotbar = i < Inventory.getSelectionSize();
                if (hotbar != (pass == 1)) continue;
                ItemStack s = inv.items.get(i);
                if (!s.is(item)) continue;
                int k = Math.min(n - got, s.getCount());
                s.shrink(k);
                got += k;
            }
        }
        for (ItemStack s : inv.offhand) {
            if (got >= n || !s.is(item)) continue;
            int k = Math.min(n - got, s.getCount());
            s.shrink(k);
            got += k;
        }
        if (got > 0) inv.setChanged();
        return got;
    }
}
