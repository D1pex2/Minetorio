package dev.minetorio.core.alt;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.Units;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.machine.CraftingMachine;
import dev.minetorio.core.research.Technology;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AltRulesTest {
    private static final String GEAR = "minetorio:iron_gear_wheel";
    private static final String PLATE = "minetorio:iron_plate";
    private static final String ORE = "minetorio:iron_ore";

    @Test void electricStatusFollowsSatisfaction() {
        assertEquals(AltStatus.NO_POWER, AltRules.electric(0));
        assertEquals(AltStatus.LOW_POWER, AltRules.electric(0.5));
        assertEquals(AltStatus.LOW_POWER, AltRules.electric(0.94));
        assertEquals(AltStatus.NONE, AltRules.electric(0.95));
        assertEquals(AltStatus.NONE, AltRules.electric(1));
    }

    @Test void burnerHasFuelWhileItemsOrEnergyRemain() {
        assertFalse(AltRules.hasFuel(Map.of(), 0));
        assertTrue(AltRules.hasFuel(Map.of(), 5));
        assertTrue(AltRules.hasFuel(Map.of("minetorio:coal", 1), 0));
        assertEquals(AltStatus.NO_FUEL, AltRules.burner(false));
        assertEquals(AltStatus.NONE, AltRules.burner(true));
    }

    @Test void assemblerWithoutRecipeWarns() {
        assertEquals(AltInfo.of(null, AltStatus.NO_RECIPE), AltRules.assembler(null, true, AltStatus.NO_POWER));
    }

    @Test void assemblerPriorities() {
        assertEquals(AltInfo.of(GEAR, AltStatus.NONE), AltRules.assembler(GEAR, false, AltStatus.NONE));
        assertEquals(AltInfo.of(GEAR, AltStatus.NO_POWER), AltRules.assembler(GEAR, true, AltStatus.NO_POWER));
        assertEquals(AltInfo.of(GEAR, AltStatus.OUTPUT_FULL), AltRules.assembler(GEAR, true, AltStatus.LOW_POWER));
        assertEquals(AltInfo.of(GEAR, AltStatus.LOW_POWER), AltRules.assembler(GEAR, false, AltStatus.LOW_POWER));
    }

    @Test void furnaceShowsTheSmeltElseTheStoredOutput() {
        assertEquals(AltInfo.of(PLATE, AltStatus.NONE), AltRules.furnace(PLATE, null, false, AltStatus.NONE));
        assertEquals(AltInfo.of(PLATE, AltStatus.NO_FUEL), AltRules.furnace(null, PLATE, false, AltStatus.NO_FUEL));
        assertEquals(AltInfo.of(null, AltStatus.NO_FUEL), AltRules.furnace(null, null, false, AltStatus.NO_FUEL));
        assertEquals(AltInfo.of(PLATE, AltStatus.OUTPUT_FULL), AltRules.furnace(PLATE, PLATE, true, AltStatus.NONE));
    }

    @Test void drillShowsItsOre() {
        assertEquals(AltInfo.of(ORE, AltStatus.NONE), AltRules.drill(ORE, null, AltStatus.NONE));
        assertEquals(AltInfo.of(null, AltStatus.NO_ORE), AltRules.drill(null, null, AltStatus.NO_FUEL));
        assertEquals(AltInfo.of(ORE, AltStatus.OUTPUT_FULL), AltRules.drill(null, ORE, AltStatus.NONE));
        assertEquals(AltInfo.of(ORE, AltStatus.NO_POWER), AltRules.drill(ORE, null, AltStatus.NO_POWER));
        assertEquals(AltInfo.of(ORE, AltStatus.NO_FUEL), AltRules.drill(ORE, ORE, AltStatus.NO_FUEL));
    }

    @Test void labAndBoiler() {
        assertEquals(AltStatus.NO_RESEARCH, AltRules.lab(false, AltStatus.NO_POWER).status());
        assertEquals(AltStatus.NO_POWER, AltRules.lab(true, AltStatus.NO_POWER).status());
        assertEquals(AltInfo.of("minetorio:coal", AltStatus.NONE), AltRules.boiler("minetorio:coal", true));
        assertEquals(AltInfo.of(null, AltStatus.NO_FUEL), AltRules.boiler("minetorio:coal", false));
    }

    @Test void inserterStallsOnlyWhileMovingWithoutEnergy() {
        assertEquals(AltStatus.NONE, AltRules.inserter(false, false, true));
        assertEquals(AltStatus.NONE, AltRules.inserter(true, true, false));
        assertEquals(AltStatus.NO_POWER, AltRules.inserter(true, false, false));
        assertEquals(AltStatus.NO_FUEL, AltRules.inserter(true, false, true));
    }

    @Test void techIconIsTheFirstUnlockedProduct() {
        Technology automation = Phase1Data.techTree().get("automation").orElseThrow();
        assertEquals("minetorio:assembling_machine_1", AltRules.techIcon(automation, Phase1Data.recipes()));
        Technology none = new Technology("x", List.of(), Map.of(), 1, 1, List.of("no_such_recipe"));
        assertEquals(AltRules.DEFAULT_LAB_ICON, AltRules.techIcon(none, Phase1Data.recipes()));
        assertNull(AltRules.labIcon(Optional.empty(), Phase1Data.recipes()));
    }

    @Test void flashingAndIconSize() {
        assertTrue(AltRules.flashOn(0));
        assertFalse(AltRules.flashOn(AltRules.FLASH_ON));
        assertTrue(AltRules.flashOn(AltRules.FLASH_PERIOD));
        assertTrue(AltRules.flashOn(-1) == AltRules.flashOn(AltRules.FLASH_PERIOD - 1));
        assertEquals(1.0, AltRules.iconSize(1, 3), 1e-9);
        assertEquals(2.0, AltRules.iconSize(1, 20), 1e-9);
        assertEquals(AltRules.MAX_GROWTH, AltRules.iconSize(1, 1000), 1e-9);
    }

    @Test void statusEncodingRoundTrips() {
        for (AltStatus s : AltStatus.values()) assertEquals(s, AltStatus.byId(s.ordinal()));
        assertEquals(AltStatus.NONE, AltStatus.byId(-1));
        assertEquals(AltStatus.NONE, AltStatus.byId(999));
        assertTrue(AltInfo.NONE.isEmpty());
        assertNull(AltInfo.of("", AltStatus.NONE).icon());
    }

    @Test void syncSendsOnlyChangesAndIsStaggered() {
        AltSync sync = new AltSync();
        assertTrue(sync.offer(AltInfo.of(GEAR, AltStatus.NONE)));
        assertFalse(sync.offer(AltInfo.of(GEAR, AltStatus.NONE)));
        assertTrue(sync.offer(AltInfo.of(GEAR, AltStatus.NO_POWER)));
        int due = 0;
        for (long t = 0; t < AltSync.PERIOD * 10L; t++) if (AltSync.due(t, 123456789L)) due++;
        assertEquals(10, due);
    }

    @Test void craftingMachineOutputFull() {
        CraftingMachine m = new CraftingMachine(100, Units.kw(75));
        assertFalse(m.isOutputFull());
        m.setRecipe(Phase1Data.recipes().get("iron_gear_wheel").orElseThrow());
        for (int i = 0; i < 2000 && !m.isOutputFull(); i++) {
            m.insertInput(PLATE, 2, false);
            m.tick(1.0);
        }
        assertTrue(m.isOutputFull());
        m.extractOutput(GEAR, 64);
        assertFalse(m.isOutputFull());
    }
}
