package dev.minetorio.content.belt;

import dev.minetorio.core.belt.BeltLane;
import dev.minetorio.core.belt.LaneItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Conversion between lane item ids ("minetorio:iron_plate") and ItemStacks; dropping lane contents. */
public final class LaneStacks {
    private LaneStacks() {}

    /** A one-item stack of {@code id}, or EMPTY if the id is unknown. */
    public static ItemStack stack(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    public static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Drops every item of {@code lane} at {@code pos} (server side). */
    public static void drop(Level level, BlockPos pos, BeltLane lane) {
        for (LaneItem it : lane.items()) drop(level, pos, it.item());
    }

    public static void drop(Level level, BlockPos pos, String id) {
        if (level.isClientSide()) return;
        ItemStack stack = stack(id);
        if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, stack);
    }

    /**
     * A lane of {@code newLength} holding the items of {@code old}, positions scaled by the length
     * ratio and kept {@link BeltLane#SPACING} apart; items that no longer fit are dropped at
     * {@code pos} (server) or discarded (client).
     */
    public static BeltLane resize(BeltLane old, double newLength, boolean scale, Level level, BlockPos pos) {
        BeltLane lane = new BeltLane(newLength);
        double ratio = scale ? newLength / old.length() : 1.0;
        double limit = newLength;
        for (LaneItem it : old.items()) {
            double p = Math.min(it.pos() * ratio, limit);
            if (p >= 0 && lane.insertAt(it.item(), p)) {
                limit = p - BeltLane.SPACING;
            } else if (level != null) {
                drop(level, pos, it.item());
            }
        }
        return lane;
    }
}
