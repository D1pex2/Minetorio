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

    @Test void inputRoomMatchesInsertInput() {
        CraftingMachine m = assembler1();
        assertEquals(4, m.inputRoom(IRON, true));
        assertEquals(64, m.inputRoom(IRON, false));
        assertEquals(0, m.inputRoom("minecraft:dirt", false));
        m.insertInput(IRON, 3, true);
        assertEquals(1, m.inputRoom(IRON, true));
        assertEquals(61, m.inputRoom(IRON, false));
        assertEquals(0, new CraftingMachine(0.5, Units.kw(75)).inputRoom(IRON, false)); // no recipe
    }

    @Test void smelterInputRoomFollowsRecipeSwitchRules() {
        CraftingMachine m = CraftingMachine.smelter(1.0, Units.kw(90), book);
        assertEquals(4, m.inputRoom("minecraft:cobblestone", true)); // idle: would switch to stone_brick (2 per craft)
        assertEquals(2, m.inputRoom("minecraft:raw_iron", true));
        assertEquals(0, m.inputRoom("minecraft:dirt", true));
        m.insertInput("minecraft:raw_iron", 1, true);
        assertEquals(1, m.inputRoom("minecraft:raw_iron", true));
        assertEquals(0, m.inputRoom("minecraft:raw_copper", false)); // busy with iron
        assertEquals(book.get("iron_plate_from_raw_iron").orElseThrow(), m.recipe());
    }

    @Test void extractInputTakesBackStoredInputs() {
        CraftingMachine m = assembler1();
        m.insertInput(IRON, 5, false);
        assertEquals(3, m.extractInput(IRON, 3));
        assertEquals(Map.of(IRON, 2), m.inputs());
        assertEquals(2, m.extractInput(IRON, 10));
        assertTrue(m.inputs().isEmpty());
        assertEquals(0, m.extractInput(IRON, 1));
        assertEquals(0, m.extractInput("minecraft:dirt", 1));
    }

    @Test void saveLoadRoundTrip() {
        CraftingMachine m = assembler1();
        m.insertInput(IRON, 7, false);
        for (int i = 0; i < 25; i++) m.tick(1.0); // one gear done, second craft in progress
        CraftingMachine copy = new CraftingMachine(0.5, Units.kw(75));
        copy.load(m.recipe(), m.inputs(), m.outputs(), m.pendingReturn(), m.isCrafting(), m.progress());
        assertEquals(m.recipe(), copy.recipe());
        assertEquals(m.inputs(), copy.inputs());
        assertEquals(m.outputs(), copy.outputs());
        assertTrue(copy.isCrafting());
        assertEquals(m.progress(), copy.progress());
        for (int i = 0; i < 40; i++) { m.tick(1.0); copy.tick(1.0); }
        assertEquals(m.outputs(), copy.outputs());
        assertEquals(m.inputs(), copy.inputs());
    }

    @Test void loadWithoutRecipeIsIdleAndKeepsPendingReturn() {
        CraftingMachine m = new CraftingMachine(0.5, Units.kw(75));
        m.load(null, Map.of(), Map.of(GEAR, 2), Map.of(IRON, 3), true, 0.4);
        assertFalse(m.isCrafting());
        assertEquals(0.0, m.progress());
        assertEquals(Map.of(GEAR, 2), m.outputs());
        assertEquals(Map.of(IRON, 3), m.pendingReturn());
        assertEquals(Map.of(IRON, 3), m.drainInputs());
    }
}
