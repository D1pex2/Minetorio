package dev.minetorio.core.machine;

import dev.minetorio.core.Units;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.recipe.RecipeBook;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CraftingMachineTest {
    private static final String IRON = "minetorio:iron_plate";
    private static final String GEAR = "minetorio:iron_gear_wheel";
    private final RecipeBook book = Phase1Data.recipes();

    private Recipe gearRecipe() { return book.get("iron_gear_wheel").orElseThrow(); }

    private CraftingMachine assembler1() {
        CraftingMachine m = new CraftingMachine(0.5, Units.kw(75));
        m.setRecipe(gearRecipe());
        return m;
    }

    @Test void assembler1MakesGearIn20Ticks() {
        CraftingMachine m = assembler1();
        m.insertInput(IRON, 4, false);
        for (int i = 0; i < 19; i++) m.tick(1.0);
        assertTrue(m.outputs().isEmpty());
        m.tick(1.0);
        assertEquals(Map.of(GEAR, 1), m.outputs());
    }

    @Test void halfPowerHalfSpeed() {
        CraftingMachine m = assembler1();
        m.insertInput(IRON, 4, false);
        for (int i = 0; i < 39; i++) m.tick(0.5);
        assertTrue(m.outputs().isEmpty());
        m.tick(0.5);
        assertEquals(Map.of(GEAR, 1), m.outputs());
    }

    @Test void automatedInsertCapsAtTwoCrafts() {
        CraftingMachine m = assembler1();
        assertEquals(4, m.insertInput(IRON, 10, true));
        assertEquals(0, m.insertInput(IRON, 1, true));
        assertEquals(60, m.insertInput(IRON, 100, false));
    }

    @Test void rejectsNonIngredient() {
        CraftingMachine m = assembler1();
        assertEquals(0, m.insertInput("minecraft:dirt", 5, false));
        assertTrue(m.inputs().isEmpty());
    }

    @Test void blocksWhenOutputFull() {
        CraftingMachine m = assembler1();
        while (m.outputs().getOrDefault(GEAR, 0) < 64) {
            m.insertInput(IRON, 64, false);
            m.tick(1.0);
        }
        assertEquals(64, m.outputs().get(GEAR));
        m.insertInput(IRON, 4, false);
        double p = m.progress();
        assertEquals(0L, m.powerDemand());
        m.tick(1.0);
        assertEquals(p, m.progress());
        assertEquals(64, m.outputs().get(GEAR));
        assertEquals(10, m.extractOutput(GEAR, 10));
        assertEquals(Units.kw(75), m.powerDemand());
    }

    @Test void powerDemandOnlyWhenRunnable() {
        CraftingMachine m = assembler1();
        assertEquals(0L, m.powerDemand());
        m.insertInput(IRON, 2, false);
        assertEquals(Units.kw(75), m.powerDemand());
        m.tick(1.0);
        assertEquals(Units.kw(75), m.powerDemand()); // in progress, inputs already spent
    }

    @Test void smelterPicksRecipeFromInput() {
        CraftingMachine m = CraftingMachine.smelter(1.0, Units.kw(90), book);
        assertEquals(1, m.insertInput("minecraft:raw_iron", 1, false));
        for (int i = 0; i < 63; i++) m.tick(1.0);
        assertTrue(m.outputs().isEmpty());
        m.tick(1.0);
        assertEquals(Map.of(IRON, 1), m.outputs());
    }

    @Test void smelterRejectsOtherOreWhileBusy() {
        CraftingMachine m = CraftingMachine.smelter(1.0, Units.kw(90), book);
        m.insertInput("minecraft:raw_iron", 1, false);
        assertEquals(0, m.insertInput("minecraft:raw_copper", 1, false));
        m.tick(1.0); // craft in progress, inputs empty
        assertEquals(0, m.insertInput("minecraft:raw_copper", 1, false));
    }

    @Test void smelterSwitchesRecipeWhenEmpty() {
        CraftingMachine m = CraftingMachine.smelter(1.0, Units.kw(90), book);
        m.insertInput("minecraft:raw_iron", 1, false);
        for (int i = 0; i < 64; i++) m.tick(1.0);
        assertEquals(1, m.extractOutput(IRON, 10));
        assertEquals(1, m.insertInput("minecraft:raw_copper", 1, false));
    }

    @Test void recipeChangeRefundsInputsAndInFlightCraft() {
        CraftingMachine m = assembler1();
        m.insertInput(IRON, 6, false);
        m.tick(1.0); // starts craft, 2 plates spent
        m.setRecipe(book.get("pipe").orElseThrow());
        assertEquals(0.0, m.progress());
        assertEquals(Map.of(IRON, 6), m.drainInputs());
        assertTrue(m.inputs().isEmpty());
        assertTrue(m.drainInputs().isEmpty());
    }

    @Test void overshootCarriesBetweenCrafts() {
        CraftingMachine m = new CraftingMachine(0.75, Units.kw(150));
        m.setRecipe(gearRecipe());
        for (int i = 1; i <= 400; i++) {
            m.insertInput(IRON, 64, false);
            m.tick(1.0);
            if (i == 40) assertEquals(3, m.outputs().getOrDefault(GEAR, 0));
        }
        assertEquals(30, m.outputs().getOrDefault(GEAR, 0));
    }

    @Test void resumesAfterBlockedOutputWithoutLossOrDuplication() {
        CraftingMachine m = new CraftingMachine(0.75, Units.kw(150));
        m.setRecipe(gearRecipe());
        int inserted = 0, extracted = 0;
        for (int i = 0; i < 1000; i++) { // output fills to 64 and blocks
            inserted += m.insertInput(IRON, 64, false);
            m.tick(1.0);
        }
        assertEquals(64, m.outputs().get(GEAR));
        assertEquals(0L, m.powerDemand());
        extracted += m.extractOutput(GEAR, 64);
        for (int i = 0; i < 200; i++) {
            inserted += m.insertInput(IRON, 64, false);
            m.tick(1.0);
        }
        assertTrue(m.outputs().get(GEAR) > 0, "production resumed");
        int produced = extracted + m.outputs().getOrDefault(GEAR, 0);
        int consumed = inserted - m.inputs().getOrDefault(IRON, 0);
        assertEquals(produced * 2, consumed - (m.progress() > 0 ? 2 : 0));
    }
}
