package dev.minetorio.core.data;

import dev.minetorio.core.recipe.*;
import dev.minetorio.core.research.Technology;
import java.util.List;
import java.util.Map;

/** Нормальные рецепты Factorio 1.1.110: base/prototypes/recipe.lua и technology.lua. */
public final class CombatData {
    private CombatData() {}
    private static Ingredient in(String id, int count) { return new Ingredient("minetorio:" + id, count); }
    public static List<Recipe> recipes() {
        return List.of(
                new Recipe("pistol", List.of(in("copper_plate", 5), in("iron_plate", 5)), List.of(in("pistol", 1)), 100, RecipeCategory.CRAFTING),
                new Recipe("submachine_gun", List.of(in("iron_gear_wheel", 10), in("copper_plate", 5), in("iron_plate", 10)),
                        List.of(in("submachine_gun", 1)), 200, RecipeCategory.CRAFTING),
                new Recipe("firearm_magazine", List.of(in("iron_plate", 4)), List.of(in("firearm_magazine", 1)), 20, RecipeCategory.CRAFTING));
    }
    /** Первый срез military: дробовик и дробь добавляются в военном блоке. */
    public static List<Technology> technologies() {
        return List.of(new Technology("military", List.of(), Map.of("minetorio:automation_science_pack", 1), 10, 300,
                List.of("submachine_gun")));
    }
}
