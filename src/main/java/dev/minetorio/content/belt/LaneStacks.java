package dev.minetorio.content.belt;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.minetorio.core.belt.BeltLane;
import dev.minetorio.core.belt.LaneItem;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Conversion between lane stack keys and one-item ItemStacks; dropping lane contents.
 *
 * <p>A key is the plain item id ("minetorio:iron_plate") when the stack has no component changes,
 * otherwise {@code id + "#" + SNBT of its DataComponentPatch}, so enchantments, names, damage and
 * container contents survive a trip along the belts. {@link LaneItem#baseId} recovers the id.
 */
public final class LaneStacks {
    private LaneStacks() {}

    /** The lane key of one item of {@code stack} (the count is ignored). */
    public static String key(ItemStack stack, HolderLookup.Provider registries) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) return id;
        return DataComponentPatch.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), patch)
                .result()
                .map(tag -> id + "#" + tag)
                .orElse(id);
    }

    /** Longest stack key (UTF-8 bytes) allowed on a lane or in an inserter's hand (R22). */
    public static final int MAX_KEY_BYTES = 32 * 1024;

    /**
     * The key under which one item of {@code stack} may enter a lane or an inserter's hand, or
     * empty if the stack is empty or its key would exceed {@link #MAX_KEY_BYTES} (e.g. a full
     * shulker box): NBT strings are limited to 64 KB, so such items stay where they are.
     * Every item entering the lane world must go through here.
     */
    public static Optional<String> toLane(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) return Optional.empty();
        String key = key(stack, registries);
        if (key.length() > MAX_KEY_BYTES / 3 && key.getBytes(StandardCharsets.UTF_8).length > MAX_KEY_BYTES) return Optional.empty();
        return Optional.of(key);
    }

    /** A one-item stack for {@code key}, or EMPTY if the item is unknown. Unreadable extra data is ignored. */
    public static ItemStack stack(String key, HolderLookup.Provider registries) {
        ResourceLocation rl = ResourceLocation.tryParse(LaneItem.baseId(key));
        if (rl == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        int hash = key.indexOf('#');
        if (hash >= 0) {
            try {
                CompoundTag tag = TagParser.parseTag(key.substring(hash + 1));
                DataComponentPatch.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                        .result()
                        .ifPresent(stack::applyComponents);
            } catch (CommandSyntaxException ignored) {
                // keep the plain item
            }
        }
        return stack;
    }

    /** Drops every item of {@code lane} at {@code pos} (server side). */
    public static void drop(Level level, BlockPos pos, BeltLane lane) {
        for (LaneItem it : lane.items()) drop(level, pos, it.item());
    }

    public static void drop(Level level, BlockPos pos, String key) {
        if (level.isClientSide()) return;
        ItemStack stack = stack(key, level.registryAccess());
        if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, stack);
    }

    /**
     * A lane of {@code newLength} holding the items of {@code old}, positions scaled by the length
     * ratio (if {@code scale}) and kept {@link BeltLane#SPACING} apart; items that no longer fit are
     * dropped at {@code pos} (server) or discarded (client).
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
