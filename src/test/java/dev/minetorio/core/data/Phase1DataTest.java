package dev.minetorio.core.data;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.Units;
import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.recipe.RecipeBook;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Phase1DataTest {
    static final Set<String> BASE = Set.of(
            "minetorio:iron_ore", "minetorio:copper_ore", "minecraft:raw_iron",
            "minecraft:raw_copper", "minecraft:coal", "minecraft:cobblestone", "minecraft:stick");
    final RecipeBook book = Phase1Data.recipes();

    @Test
    void everyIngredientIsProducibleOrBase() {
        Set<String> produced = new HashSet<>();
        for (Recipe r : book.all()) r.outputs().forEach(o -> produced.add(o.item()));
        for (var r : OilData.recipes()) r.itemOutputs().forEach(o -> produced.add(o.item()));
        for (Recipe r : book.all())
            for (var in : r.inputs())
                assertTrue(produced.contains(in.item()) || BASE.contains(in.item()),
                        r.id() + " needs " + in.item());
    }

    @Test void redScienceTicks() { assertEquals(100, book.get("automation_science_pack").get().ticks()); }

    @Test
    void ironPlateSmeltsFromBootstrapRawIron() {
        assertEquals("minetorio:iron_plate",
                book.smeltingFor("minecraft:raw_iron").get().outputs().get(0).item());
    }

    @Test void beltRecipeMakesTwo() { assertEquals(2, book.get("transport_belt").get().outputs().get(0).count()); }
    @Test void coalIs4MJ() { assertEquals(4_000_000L, Phase1Data.fuelValues().get("minecraft:coal")); }

    @Test
    void unitsConvert() {
        assertEquals(64, Units.ticks(3.2));
        assertEquals(4_500L, Units.kw(90));
    }

    /** Factorio 1.1 radar: 5 circuits, 5 gears, 10 iron plates, 0.5 s; craftable from the start. */
    @Test
    void radarRecipeIsFactorioAndUnlockedAtStart() {
        Recipe radar = book.get("radar").orElseThrow();
        assertEquals(java.util.List.of(new dev.minetorio.core.recipe.Ingredient("minetorio:electronic_circuit", 5),
                new dev.minetorio.core.recipe.Ingredient("minetorio:iron_gear_wheel", 5),
                new dev.minetorio.core.recipe.Ingredient("minetorio:iron_plate", 10)), radar.inputs());
        assertEquals(java.util.List.of(new dev.minetorio.core.recipe.Ingredient("minetorio:radar", 1)), radar.outputs());
        assertEquals(10, radar.ticks());
        assertTrue(Phase1Data.startRecipes().contains("radar"));
    }

    /** Construction robotics needs blue science and unlocks the basic logistic chests too. */
    @Test
    void constructionRoboticsUnlocksRobots() {
        var tech = Phase1Data.techTree().get("construction_robotics").orElseThrow();
        assertEquals(java.util.List.of("roboport", "passive_provider_chest", "storage_chest", "construction_robot"), tech.unlocksRecipes());
        assertEquals(java.util.Set.of("minetorio:automation_science_pack", "minetorio:logistic_science_pack", "minetorio:chemical_science_pack"), tech.packsPerUnit().keySet());
        assertFalse(Phase1Data.startRecipes().contains("roboport"));
        assertFalse(Phase1Data.startRecipes().contains("construction_robot"));
        assertEquals(new dev.minetorio.core.recipe.Ingredient("minetorio:roboport", 1), book.get("roboport").orElseThrow().outputs().get(0));
    }

    /** Factorio 1.1 research for solar, accumulators and electric energy distribution 1 & 2; all locked at start. */
    @Test
    void energyResearch() {
        var tree = Phase1Data.techTree();
        assertEquals(java.util.List.of("medium_electric_pole", "big_electric_pole"),
                tree.get("electric_energy_distribution_1").orElseThrow().unlocksRecipes());
        assertEquals(java.util.List.of("substation"), tree.get("electric_energy_distribution_2").orElseThrow().unlocksRecipes());
        assertEquals(java.util.List.of("electric_energy_distribution_1", "chemical_science_pack"),
                tree.get("electric_energy_distribution_2").orElseThrow().prerequisites());
        assertEquals(java.util.List.of("solar_panel"), tree.get("solar_energy").orElseThrow().unlocksRecipes());
        var acc = tree.get("electric_energy_accumulators").orElseThrow();
        assertEquals(java.util.List.of("accumulator"), acc.unlocksRecipes());
        assertEquals(150, acc.units());
        assertEquals(120, tree.get("electric_energy_distribution_1").orElseThrow().units());
        for (String id : java.util.List.of("solar_panel", "accumulator", "medium_electric_pole", "big_electric_pole", "substation")) {
            assertFalse(Phase1Data.startRecipes().contains(id), id);
            assertEquals(new dev.minetorio.core.recipe.Ingredient("minetorio:" + id, 1), book.get(id).orElseThrow().outputs().get(0));
        }
        assertEquals(200, book.get("solar_panel").orElseThrow().ticks()); // 10 s
        assertEquals(200, book.get("accumulator").orElseThrow().ticks());
        assertEquals(10, book.get("substation").orElseThrow().ticks());
    }

    @Test void assemblerLockedAtStart() { assertFalse(Phase1Data.startRecipes().contains("assembling_machine_1")); }

    @Test
    void recipeIdsUniqueAndStartRecipesExist() {
        assertEquals(book.all().size(), book.all().stream().map(Recipe::id).distinct().count());
        for (String id : Phase1Data.startRecipes()) assertTrue(book.get(id).isPresent(), id);
        assertTrue(Phase1Data.startRecipes().contains("inserter"));
    }

    @Test
    void fuelValues() {
        var f = Phase1Data.fuelValues();
        assertEquals(2_000_000L, f.get("minecraft:cherry_log"));
        assertEquals(500_000L, f.get("minecraft:bamboo_planks"));
        assertEquals(250_000L, f.get("minecraft:stick"));
        assertEquals(36_000_000L, f.get("minecraft:coal_block"));
    }
}
