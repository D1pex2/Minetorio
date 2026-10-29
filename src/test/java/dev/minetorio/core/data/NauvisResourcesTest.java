package dev.minetorio.core.data;

import static org.junit.jupiter.api.Assertions.*;
import dev.minetorio.core.orefield.OreType;
import dev.minetorio.core.recipe.Ingredient;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NauvisResourcesTest {
    @Test void machineAndFuelSlotsUseFactorioStacks() {
        var burner = new dev.minetorio.core.machine.Burner(100, java.util.Map.of("minetorio:coal", 4_000_000L));
        assertEquals(50, burner.insertFuel("minetorio:coal", 200, false));
        var furnace = dev.minetorio.core.machine.CraftingMachine.smelter(1, 100, Phase1Data.recipes());
        assertEquals(50, furnace.inputRoom("minetorio:iron_ore", false));
        var assembler = new dev.minetorio.core.machine.CraftingMachine(1, 100);
        assembler.setRecipe(Phase1Data.recipes().get("copper_cable").orElseThrow());
        assertEquals(100, assembler.inputRoom("minetorio:copper_plate", false));
    }

    @Test void steelAxeIsTheExactFirstMiningResearch() {
        var tech = Phase1Data.techTree().get("steel_axe").orElseThrow();
        assertEquals(List.of("steel_processing"), tech.prerequisites());
        assertEquals(50, tech.units());
        assertEquals(600, tech.ticksPerUnit());
    }
    @Test void onlyFactorioResourcesAndFuelsParticipateInProgression() {
        assertEquals(Set.of("minetorio:coal", "minetorio:wood", "minetorio:solid_fuel"), Phase1Data.fuelValues().keySet());
        assertEquals(2_000_000L, Phase1Data.fuelValues().get("minetorio:wood"));
        assertEquals("minetorio:coal", OreType.COAL.item());
        assertEquals("minetorio:stone", OreType.STONE.item());
        for (var recipe : Phase1Data.recipes().all()) for (var in : recipe.inputs()) {
            assertTrue(in.item().startsWith("minetorio:"), recipe.id() + " consumes " + in.item());
        }
        for (var recipe : OilData.recipes()) for (var in : recipe.itemInputs()) {
            assertTrue(in.item().startsWith("minetorio:"), recipe.id() + " consumes " + in.item());
        }
        assertTrue(Phase1Data.recipes().smeltingFor("minecraft:raw_iron").isEmpty());
        assertTrue(Phase1Data.recipes().smeltingFor("minecraft:raw_copper").isEmpty());
    }

    @Test void earlyChestsAndPoleHaveExactFactorioCosts() {
        var recipes = Phase1Data.recipes();
        assertEquals(List.of(new Ingredient("minetorio:wood", 2)), recipes.get("wooden_chest").orElseThrow().inputs());
        assertEquals(List.of(new Ingredient("minetorio:iron_plate", 8)), recipes.get("iron_chest").orElseThrow().inputs());
        assertEquals(List.of(new Ingredient("minetorio:wood", 1), new Ingredient("minetorio:copper_cable", 2)),
                recipes.get("small_electric_pole").orElseThrow().inputs());
        assertTrue(Phase1Data.startRecipes().containsAll(Set.of("wooden_chest", "iron_chest")));
    }
}
