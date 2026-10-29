package dev.minetorio.content.rail;

import net.minecraft.core.HolderLookup;
import dev.minetorio.content.belt.LaneStacks;
import dev.minetorio.content.robot.GhostItem;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class RailItemKeys {
    private RailItemKeys() {}
    public static int limit(String key, HolderLookup.Provider registries) {
        if (key == null || key.getBytes(StandardCharsets.UTF_8).length > LaneStacks.MAX_KEY_BYTES) return 0;
        int hash = key.indexOf('#');
        String base = hash < 0 ? key : key.substring(0, hash);
        ResourceLocation id = ResourceLocation.tryParse(base);
        if (id == null || !id.toString().equals(base) || !BuiltInRegistries.ITEM.containsKey(id)) return 0;
        var item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR || item instanceof GhostItem) return 0;
        ItemStack stack = new ItemStack(item);
        if (hash >= 0) {
            try {
                var patch = DataComponentPatch.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),
                        TagParser.parseTag(key.substring(hash + 1)));
                if (patch.error().isPresent() || patch.result().isEmpty()) return 0;
                stack.applyComponents(patch.result().orElseThrow());
            } catch (Exception invalid) {
                return 0;
            }
        }
        return stack.isEmpty() ? 0 : stack.getMaxStackSize();
    }
}
