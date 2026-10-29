package dev.minetorio.content.logistics;

import dev.minetorio.content.character.CharacterInventory;
import net.minecraft.world.Container;
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
        Container main = CharacterInventory.main(inv);
        for (int i = 0; i < main.getContainerSize(); i++) add(out, main.getItem(i));
        for (ItemStack s : inv.offhand) add(out, s);
        return out;
    }

    private static void add(Map<String, Integer> out, ItemStack s) {
        if (dev.minetorio.content.machine.MachineItems.isPlain(s)) out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
    }

    public static int count(Player player, Item item) {
        int n = 0;
        Inventory inv = player.getInventory();
        Container main = CharacterInventory.main(inv);
        for (int i = 0; i < main.getContainerSize(); i++) { ItemStack s = main.getItem(i); if (s.is(item)) n += s.getCount(); }
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
        ItemStack trash = inv.getItem(CharacterInventory.TRASH_SLOT);
        if (trash.is(item) && dev.minetorio.content.machine.MachineItems.isPlain(trash)) {
            int k = Math.min(n, trash.getCount());
            trash.shrink(k);
            got += k;
        }
        Container main = CharacterInventory.main(inv);
        for (int pass = 0; pass < 2 && got < n; pass++) {
            for (int i = 0; i < main.getContainerSize() && got < n; i++) {
                boolean hotbar = i < Inventory.getSelectionSize();
                if (hotbar != (pass == 1)) continue;
                ItemStack s = main.getItem(i);
                if (!s.is(item) || !dev.minetorio.content.machine.MachineItems.isPlain(s)) continue;
                int k = Math.min(n - got, s.getCount());
                s.shrink(k);
                got += k;
            }
        }
        for (ItemStack s : inv.offhand) {
            if (got >= n || !s.is(item) || !dev.minetorio.content.machine.MachineItems.isPlain(s)) continue;
            int k = Math.min(n - got, s.getCount());
            s.shrink(k);
            got += k;
        }
        if (got > 0) inv.setChanged();
        return got;
    }
}
