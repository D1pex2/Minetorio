package dev.minetorio.core.player;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EngineerMiningRulesTest {
    @Test void factorioCharacterMinesOreInTwoSecondsAndSteelAxeHalvesTime() {
        assertEquals(40,EngineerMiningRules.ticks("iron_ore_field",false));
        assertEquals(20,EngineerMiningRules.ticks("iron_ore_field",true));
        assertEquals(22,EngineerMiningRules.ticks("tree_trunk",false),1e-9);
        assertEquals(12,EngineerMiningRules.ticks("electric_mining_drill",false),1e-9);
        assertEquals(120,EngineerMiningRules.ticks("huge_rock",false),1e-9);
    }
}
