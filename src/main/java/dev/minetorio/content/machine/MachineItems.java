package dev.minetorio.content.machine;

import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Item id &lt;-&gt; ItemStack glue for the crafting machines. Machines store crafting materials by
 * plain item id (core), so only stacks without data components ({@link #isPlain}) may enter them:
 * anything else is refused rather than silently losing its data.
 */
public final class MachineItems {
    private MachineItems() {}

    /** Registry id of the stack's item, e.g. "minetorio:iron_plate". */
    public static String id(ItemStack stack) {
        return stack.getItemHolder().getRegisteredName();
    }

    /** Whether the stack carries no data components beyond its item's defaults (empty patch). */
    public static boolean isPlain(ItemStack stack) {
        return !stack.isEmpty() && stack.getComponentsPatch().isEmpty();
    }

    /** The item for {@code id}, or null if unknown. */
    @Nullable
    public static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? null : item;
    }

    /** A stack of {@code count} × {@code id} (EMPTY if unknown or count &lt;= 0). */
    public static ItemStack stack(String id, int count) {
        Item item = item(id);
        return item == null || count <= 0 ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    /** Drops the items at {@code pos} in stacks of at most their max size (server side). */
    public static void drop(Level level, BlockPos pos, Map<String, Integer> items) {
        items.forEach((id, n) -> split(id, n, s ->
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, s)));
    }

    /** Gives the items to the player: into the inventory, the rest dropped at the player's feet. */
    public static void give(Player player, Map<String, Integer> items) {
        items.forEach((id, n) -> split(id, n, s -> player.getInventory().placeItemBackInInventory(s)));
    }

    private static void split(String id, int count, Consumer<ItemStack> out) {
        Item item = item(id);
        if (item == null) return;
        int max = new ItemStack(item).getMaxStackSize();
        for (int left = count; left > 0; left -= max) out.accept(new ItemStack(item, Math.min(max, left)));
    }
}
