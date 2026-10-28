package dev.minetorio.core.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RobotFlightPoseTest {
    private static final double FULL = RobotStats.SPEED_BLOCKS_PER_TICK;

    @Test
    void pitchIsZeroWhenHoveringAndFullAtFullSpeed() {
        assertEquals(0f, RobotFlightPose.targetPitch(0, FULL));
        assertEquals(0f, RobotFlightPose.targetPitch(1e-9, FULL));
        assertEquals(0f, RobotFlightPose.targetPitch(0.1, 0));
        assertEquals(RobotFlightPose.MAX_PITCH, RobotFlightPose.targetPitch(FULL, FULL), 1e-4);
        assertEquals(RobotFlightPose.MAX_PITCH, RobotFlightPose.targetPitch(FULL * 3, FULL), 1e-4);
        assertTrue(RobotFlightPose.MAX_PITCH >= 20 && RobotFlightPose.MAX_PITCH <= 25);
    }

    @Test
    void pitchGrowsMonotonicallyWithSpeed() {
        float prev = 0;
        for (int i = 1; i <= 20; i++) {
            float p = RobotFlightPose.targetPitch(FULL * i / 20.0, FULL);
            assertTrue(p > prev, "not increasing at " + i);
            prev = p;
        }
        // battery-empty robots fly at 20 %: a small tilt only
        assertTrue(RobotFlightPose.targetPitch(FULL * 0.2, FULL) < RobotFlightPose.MAX_PITCH * 0.15f);
    }

    @Test
    void stepEasesWithoutOvershootAndSettles() {
        float p = 0, target = RobotFlightPose.MAX_PITCH, lastStep = Float.MAX_VALUE;
        int ticks = 0;
        while (p != target) {
            float next = RobotFlightPose.stepPitch(p, target);
            assertTrue(next > p && next <= target);
            assertTrue(next == target || next - p <= lastStep + 1e-4f, "eases out"); // the last step snaps
            lastStep = next - p;
            p = next;
            assertTrue(++ticks < 100, "never settled");
        }
        assertTrue(ticks > 5, "snapped instead of easing");
        while (p != 0) {
            float next = RobotFlightPose.stepPitch(p, 0);
            assertTrue(next < p && next >= 0);
            p = next;
        }
        assertEquals(0f, RobotFlightPose.stepPitch(0f, 0f));
    }

    @Test
    void rotorTurnsSteadilyAndStaysInRange() {
        float a0 = RobotFlightPose.rotorAngle(10f, 3), a1 = RobotFlightPose.rotorAngle(10.5f, 3);
        float d = (a1 - a0 + 360f) % 360f;
        assertEquals(RobotFlightPose.ROTOR_DEG_PER_TICK * 0.5f, d, 1e-3);
        for (int t = 0; t < 1000; t += 7) {
            float a = RobotFlightPose.rotorAngle(t + 0.3f, -t);
            assertTrue(a >= 0 && a < 360, "out of range " + a);
        }
        assertTrue(RobotFlightPose.ROTOR_DEG_PER_TICK % 45f != 0, "8 blades: a multiple of 45 looks still");
    }
}
