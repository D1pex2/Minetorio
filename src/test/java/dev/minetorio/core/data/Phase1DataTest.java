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
