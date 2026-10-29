package dev.minetorio.core.combat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CombatRulesTest {
    @Test void magazineRunsOutOnTenthShot() {
        int rounds = 10;
        for (int i = 0; i < 9; i++) rounds = CombatRules.consumeRound(rounds);
        assertEquals(1, rounds);
        assertEquals(0, CombatRules.consumeRound(rounds));
        assertThrows(IllegalArgumentException.class, () -> CombatRules.consumeRound(0));
        assertThrows(IllegalArgumentException.class, () -> CombatRules.consumeRound(11));
    }
    @Test void physicalDamageScalesAndAppliesSpawnerResistance() {
        assertEquals(0.4f, CombatRules.health(CombatRules.physicalDamage(5, 0, 0)), 0.00001);
        assertEquals(0.204f, CombatRules.health(CombatRules.physicalDamage(5, 2, 15)), 0.00001);
        assertEquals(0.16, CombatRules.physicalDamage(0.5, 4, 20), 0.00001);
        assertEquals(0.5, CombatRules.physicalDamage(4, 4, 0), 0.00001);
        assertEquals(1, CombatRules.physicalDamage(5, 4, 0), 0.00001);
        assertEquals(1.0 / 3, CombatRules.physicalDamage(3, 4, 0), 0.00001);
        assertEquals(20, CombatRules.health(250));
    }
    @Test void pistolAndAutomaticFireKeepFactorioCadence() {
        for (var gun : CombatRules.Gun.values()) {
            long next = 0;
            int shots = 0;
            for (long tick = 0; tick < 20; tick++) {
                if (CombatRules.canFire(tick, next)) { shots++; next = tick + gun.cooldownTicks; }
            }
            assertEquals(gun == CombatRules.Gun.PISTOL ? 4 : 10, shots);
        }
        assertFalse(CombatRules.canFire(4, 5));
        assertTrue(CombatRules.canFire(5, 5));
    }
    @Test void removingOwnSlowdownFromFovPreservesSprintAndUserEffectScale() {
        assertEquals(.1, CombatRules.fovCorrection(.9f,.08,.1,.8,1),.00001);
        // Спринт 1.3 остается после компенсации автомата: исходный FOV 0.695 становится 1.15.
        assertEquals(.455, CombatRules.fovCorrection(.695f,.039,.1,.3,1),.00001);
        assertEquals(.2275, CombatRules.fovCorrection(.695f,.039,.1,.3,.5),.00001);
        assertEquals(0, CombatRules.fovCorrection(.695f,.039,.1,.3,0));
    }
}
