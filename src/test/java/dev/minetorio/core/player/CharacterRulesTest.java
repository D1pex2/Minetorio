package dev.minetorio.core.player;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CharacterRulesTest {
    @Test
    void regenStartsTenSecondsAfterDamage() {
        assertEquals(200, CharacterRules.REGEN_DELAY_TICKS);
        assertEquals(0, CharacterRules.regenThisTick(199, 10, 20));
        assertTrue(CharacterRules.regenThisTick(200, 10, 20) > 0);
    }

    @Test
    void regenRateMatchesFactorio() {
        // Factorio: 0.15 HP/tick × 60 = 9 HP/s of 250 → 3.6 %/s → 0.72 HP/s of 20 → 0.036 per MC tick.
        assertEquals(0.036, CharacterRules.regenPerTick(20), 1e-9);
        assertEquals(9.0 / 20, CharacterRules.regenPerTick(250), 1e-9);
    }

    @Test
    void noRegenAtFullHealthOrDeadAndNeverOverheals() {
        assertEquals(0, CharacterRules.regenThisTick(1000, 20, 20));
        assertEquals(0, CharacterRules.regenThisTick(1000, 0, 20));
        assertEquals(0.01, CharacterRules.regenThisTick(1000, 19.99, 20), 1e-9);
    }

    @Test
    void fishHealsEightyOfTwoHundredFifty() {
        assertEquals(6.4, CharacterRules.fishHeal(20), 1e-9);
        assertEquals(80, CharacterRules.fishHeal(250), 1e-9);
        assertEquals(10, CharacterRules.FISH_COOLDOWN_TICKS);
    }

    @Test
    void dayRateGivesFactorioDay() {
        assertEquals(416, CharacterRules.FACTORIO_DAY_SECONDS);
        float rate = CharacterRules.dayTimePerTick(416);
        assertEquals(24000.0 / (416 * 20), rate, 1e-5);
        // a full Minecraft day passes in 416 s of game ticks
        assertEquals(416 * 20, 24000 / rate, 1.0);
        assertEquals(1f, CharacterRules.dayTimePerTick(1200), 1e-6);
        assertTrue(CharacterRules.dayTimePerTick(0) < 0);
    }

    @Test
    void corpseLifetimeAndRows() {
        assertEquals(120L * 60 * 20, CharacterRules.corpseLifetimeTicks(120));
        assertEquals(Long.MAX_VALUE, CharacterRules.corpseLifetimeTicks(0));
        assertEquals(1, CharacterRules.corpseRows(0));
        assertEquals(1, CharacterRules.corpseRows(9));
        assertEquals(2, CharacterRules.corpseRows(10));
        assertEquals(5, CharacterRules.corpseRows(41));
        assertEquals(6, CharacterRules.corpseRows(80));
    }

    @Test
    void spawnPolicy() {
        assertFalse(CharacterRules.allowsSpawn("minecraft", "NATURAL"));
        assertFalse(CharacterRules.allowsSpawn("minecraft", "PATROL"));
        assertFalse(CharacterRules.allowsSpawn("othermod", "CHUNK_GENERATION"));
        assertTrue(CharacterRules.allowsSpawn("minetorio", "NATURAL"));
        assertTrue(CharacterRules.allowsSpawn("minecraft", "SPAWN_EGG"));
        assertTrue(CharacterRules.allowsSpawn("minecraft", "COMMAND"));
    }
}
