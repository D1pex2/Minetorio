package dev.minetorio.client.handcraft;

import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.recipe.RecipeBook;
import dev.minetorio.core.recipe.RecipeCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The tabs of the hand-crafting screen (as in Factorio) and which crafting recipe goes in which.
 * A recipe missing from the table is shown under intermediates. Pure (no Minecraft classes).
 */
public final class HandcraftGroups {
    public enum Group {
        LOGISTICS("minetorio:transport_belt"),
        PRODUCTION("minetorio:assembling_machine_1"),
        INTERMEDIATES("minetorio:electronic_circuit"),
        SCIENCE("minetorio:automation_science_pack");

        /** Item shown on the tab. */
        public final String icon;

        Group(String icon) { this.icon = icon; }

        public String key() { return "minetorio.handcraft.group." + name().toLowerCase(Locale.ROOT); }
    }

    private static final Map<String, Group> BY_RECIPE = Map.ofEntries(
            Map.entry("transport_belt", Group.LOGISTICS),
            Map.entry("underground_belt", Group.LOGISTICS),
            Map.entry("splitter", Group.LOGISTICS),
            Map.entry("fast_transport_belt", Group.LOGISTICS),
            Map.entry("fast_underground_belt", Group.LOGISTICS),
            Map.entry("fast_splitter", Group.LOGISTICS),
            Map.entry("burner_inserter", Group.LOGISTICS),
            Map.entry("inserter", Group.LOGISTICS),
            Map.entry("long_handed_inserter", Group.LOGISTICS),
            Map.entry("fast_inserter", Group.LOGISTICS),
            Map.entry("filter_inserter", Group.LOGISTICS),
            Map.entry("stack_inserter", Group.LOGISTICS),
            Map.entry("pipe", Group.LOGISTICS),
            Map.entry("pipe_to_ground", Group.LOGISTICS),
            Map.entry("storage_tank", Group.LOGISTICS),
            Map.entry("pump", Group.LOGISTICS),
            Map.entry("small_electric_pole", Group.LOGISTICS),
            Map.entry("medium_electric_pole", Group.LOGISTICS),
            Map.entry("big_electric_pole", Group.LOGISTICS),
            Map.entry("substation", Group.LOGISTICS),
            Map.entry("solar_panel", Group.PRODUCTION),
            Map.entry("accumulator", Group.PRODUCTION),
            Map.entry("roboport", Group.LOGISTICS),
            Map.entry("construction_robot", Group.LOGISTICS),
            Map.entry("stone_furnace", Group.PRODUCTION),
            Map.entry("burner_mining_drill", Group.PRODUCTION),
            Map.entry("electric_mining_drill", Group.PRODUCTION),
            Map.entry("assembling_machine_1", Group.PRODUCTION),
            Map.entry("assembling_machine_2", Group.PRODUCTION),
            Map.entry("lab", Group.PRODUCTION),
            Map.entry("radar", Group.PRODUCTION),
            Map.entry("offshore_pump", Group.PRODUCTION),
            Map.entry("boiler", Group.PRODUCTION),
            Map.entry("steam_engine", Group.PRODUCTION),
            Map.entry("iron_gear_wheel", Group.INTERMEDIATES),
            Map.entry("copper_cable", Group.INTERMEDIATES),
            Map.entry("electronic_circuit", Group.INTERMEDIATES),
            Map.entry("automation_science_pack", Group.SCIENCE),
            Map.entry("logistic_science_pack", Group.SCIENCE));

    private HandcraftGroups() {}

    public static Group of(String recipeId) { return BY_RECIPE.getOrDefault(recipeId, Group.INTERMEDIATES); }

    static boolean isMapped(String recipeId) { return BY_RECIPE.containsKey(recipeId); }

    /** Ids of the book's crafting recipes in {@code group} that pass {@code visible}, in book order. */
    public static List<String> recipes(Group group, RecipeBook book, Predicate<String> visible) {
        List<String> out = new ArrayList<>();
        for (Recipe r : book.all()) {
            if (r.category() == RecipeCategory.CRAFTING && of(r.id()) == group && visible.test(r.id())) out.add(r.id());
        }
        return out;
    }
}
