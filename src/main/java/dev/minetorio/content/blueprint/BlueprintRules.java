package dev.minetorio.content.blueprint;

import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.content.robot.GhostBlock;
import dev.minetorio.content.robot.HandGhosts;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.recipe.RecipeCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * What blueprints copy and what research allows (data-driven from the recipe book):
 * <ul>
 *   <li>{@link #capturable}: Minetorio blocks placed by their own Minetorio block item (machines by
 *       their controller, belts, undergrounds, splitters, inserters, pipes, poles...); not ghosts,
 *       multiblock parts, ore fields or vanilla blocks;</li>
 *   <li>{@link #itemUnlocked}: a machine is researched when some unlocked crafting recipe makes it
 *       (an item no recipe makes, e.g. creative-only, counts as available);</li>
 *   <li>{@link #sanitize}: settings lose a recipe that is not unlocked (Factorio 1.1 drops it at paste).</li>
 * </ul>
 */
public final class BlueprintRules {
    private static Map<String, List<String>> makers;

    private BlueprintRules() {}

    /** Whether a blueprint copies {@code block}. */
    public static boolean capturable(Block block) {
        if (block instanceof GhostBlock || block instanceof MultiblockPartBlock) return false;
        Item item = block.asItem();
        return item instanceof BlockItem bi && bi.getBlock() == block && HandGhosts.canGhost(new ItemStack(item));
    }

    /** Crafting recipe ids per output item id. */
    private static synchronized Map<String, List<String>> makers() {
        if (makers == null) {
            Map<String, List<String>> m = new HashMap<>();
            for (Recipe r : Phase1Data.recipes().all()) {
                if (r.category() != RecipeCategory.CRAFTING) continue;
                r.outputs().forEach(o -> m.computeIfAbsent(o.item(), k -> new ArrayList<>()).add(r.id()));
            }
            makers = m;
        }
        return makers;
    }

    /** Whether {@code item} may be pasted given which recipes are unlocked. */
    public static boolean itemUnlocked(Item item, Predicate<String> recipeUnlocked) {
        List<String> recipes = makers().get(BuiltInRegistries.ITEM.getKey(item).toString());
        if (recipes == null || recipes.isEmpty()) return true;
        for (String r : recipes) if (recipeUnlocked.test(r)) return true;
        return false;
    }

    /** {@code settings} without a recipe that is not unlocked; null when nothing is left. */
    @Nullable
    public static CompoundTag sanitize(@Nullable CompoundTag settings, Predicate<String> recipeUnlocked) {
        if (settings == null) return null;
        CompoundTag out = settings.copy();
        String key = AssemblerBlockEntity.SETTINGS_RECIPE;
        if (out.contains(key, Tag.TAG_STRING) && !recipeUnlocked.test(out.getString(key))) out.remove(key);
        return out.isEmpty() ? null : out;
    }
}
