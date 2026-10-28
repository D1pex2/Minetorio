package dev.minetorio.core.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoboportAnimationTest {
    @Test
    void doorHoldsOpenAfterActivityThenCloses() {
        int hold = RoboportAnimation.nextHold(0, true);
        assertEquals(RoboportAnimation.DOOR_HOLD_TICKS, hold);
        for (int i = 0; i < RoboportAnimation.DOOR_HOLD_TICKS - 1; i++) {
            hold = RoboportAnimation.nextHold(hold, false);
            assertTrue(hold > 0, "closed early at tick " + i);
        }
        assertEquals(0, RoboportAnimation.nextHold(hold, false));
        assertEquals(0, RoboportAnimation.nextHold(0, false));
    }

    @Test
    void doorMovesSmoothlyAndClamps() {
        float d = 0;
        for (int i = 0; i < RoboportAnimation.DOOR_TICKS; i++) {
            float next = RoboportAnimation.stepDoor(d, true);
            assertTrue(next > d && next - d <= 1f / RoboportAnimation.DOOR_TICKS + 1e-6);
            d = next;
        }
        assertEquals(1f, d, 1e-6);
        assertEquals(1f, RoboportAnimation.stepDoor(d, true));
        assertEquals(1f - 1f / RoboportAnimation.DOOR_TICKS, RoboportAnimation.stepDoor(1f, false), 1e-6);
        assertEquals(0f, RoboportAnimation.stepDoor(0f, false));
    }

    @Test
    void easeIsMonotonicWithFixedEnds() {
        assertEquals(0f, RoboportAnimation.ease(0));
        assertEquals(1f, RoboportAnimation.ease(1));
        float prev = 0;
        for (int i = 1; i <= 20; i++) {
            float e = RoboportAnimation.ease(i / 20f);
            assertTrue(e >= prev);
            prev = e;
        }
    }

    @Test
    void antennaTurnsOnlyWithPower() {
        assertEquals(RoboportAnimation.ANTENNA_DEG_PER_TICK, RoboportAnimation.stepAntenna(0, true));
        assertEquals(10f, RoboportAnimation.stepAntenna(10, false));
        float a = RoboportAnimation.stepAntenna(359f, true);
        assertTrue(a >= 0 && a < 360);
    }

    @Test
    void lampsBlinkOutOfStepWithPowerAndTogetherWithout() {
        int on = 0;
        for (long t = 0; t < 40; t++) if (RoboportAnimation.lampOn(t, 0, true)) on++;
        assertEquals(10, on);
        boolean differ = false;
        for (long t = 0; t < 40; t++) differ |= RoboportAnimation.lampOn(t, 0, true) != RoboportAnimation.lampOn(t, 1, true);
        assertTrue(differ);
        for (long t = 0; t < 120; t++) {
            assertEquals(RoboportAnimation.lampOn(t, 0, false), RoboportAnimation.lampOn(t, 5, false));
        }
        assertFalse(RoboportAnimation.lampOn(30, 0, false));
    }

    @Test
    void chargePulseStaysAtOrAboveTheIdleSize() {
        int flicker = 0;
        for (long t = 0; t < 200; t++) {
            float p = RoboportAnimation.chargePulse(t, 0.5f, 2);
            if (p == 0) flicker++;
            else assertTrue(p >= 1f && p <= 1.12f + 1e-6);
        }
        assertTrue(flicker > 0 && flicker < 20);
    }
}
