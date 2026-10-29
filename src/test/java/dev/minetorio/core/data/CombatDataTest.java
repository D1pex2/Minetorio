package dev.minetorio.core.data;

import static org.junit.jupiter.api.Assertions.*;
import dev.minetorio.core.recipe.Ingredient;
import dev.minetorio.core.research.ResearchState;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatDataTest {
    @Test void pistolAndMagazinesUseNormalFactorioRecipes() {
        var pistol = Phase1Data.recipes().get("pistol").orElseThrow();
        assertEquals(List.of(new Ingredient("minetorio:copper_plate", 5), new Ingredient("minetorio:iron_plate", 5)), pistol.inputs());
        assertEquals(100, pistol.ticks());
        var ammo = Phase1Data.recipes().get("firearm_magazine").orElseThrow();
        assertEquals(List.of(new Ingredient("minetorio:iron_plate", 4)), ammo.inputs());
        assertEquals(20, ammo.ticks());
        assertTrue(Phase1Data.startRecipes().containsAll(List.of("pistol", "firearm_magazine")));
    }

    @Test void submachineGunRequiresMilitaryResearch() {
        var gun = Phase1Data.recipes().get("submachine_gun").orElseThrow();
        assertEquals(List.of(new Ingredient("minetorio:iron_gear_wheel", 10), new Ingredient("minetorio:copper_plate", 5),
                new Ingredient("minetorio:iron_plate", 10)), gun.inputs());
        assertEquals(200, gun.ticks());
        var state = new ResearchState(Phase1Data.techTree(), Phase1Data.startRecipes());
        assertFalse(state.isRecipeUnlocked("submachine_gun"));
        var military = Phase1Data.techTree().get("military").orElseThrow();
        assertEquals(10, military.units());
        assertEquals(300, military.ticksPerUnit());
        assertTrue(military.prerequisites().isEmpty());
        state.setCurrent("military");
        for (int i = 0; i < 10; i++) state.completeUnit();
        assertTrue(state.isRecipeUnlocked("submachine_gun"));
    }
}
