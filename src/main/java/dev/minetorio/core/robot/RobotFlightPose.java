package dev.minetorio.core.robot;

/**
 * Pure pose math for drawing a flying construction robot (ConstructionRobotRenderer): like a drone or
 * helicopter it pitches nose-down into its direction of travel, more the faster it flies, easing in and
 * out instead of snapping; and its fan rotor never stops turning.
 */
public final class RobotFlightPose {
    /** Nose-down pitch (degrees) at full flight speed. */
    public static final float MAX_PITCH = 22f;
    /** Share of the remaining pitch change applied per tick. */
    public static final float PITCH_RATE = 0.18f;
    /** Rotor turn per tick, degrees (8 blades, so 45 degrees would look still). */
    public static final float ROTOR_DEG_PER_TICK = 27f;

    private RobotFlightPose() {}

    /**
     * The pitch a robot moving at {@code speed} blocks per tick should settle at, for a full speed of
     * {@code fullSpeed}: 0 when hovering, MAX_PITCH at (or above) full speed, smoothstepped in between.
     */
    public static float targetPitch(double speed, double fullSpeed) {
        if (!(fullSpeed > 0) || !(speed > 1e-6)) return 0f;
        double f = Math.min(1.0, speed / fullSpeed);
        return (float) (MAX_PITCH * f * f * (3 - 2 * f));
    }

    /** {@code current} eased one tick toward {@code target} (snaps once within a hundredth of a degree). */
    public static float stepPitch(float current, float target) {
        float next = current + (target - current) * PITCH_RATE;
        return Math.abs(target - next) < 0.01f ? target : next;
    }

    /** Rotor angle in degrees, [0, 360), at {@code time} ticks (tick count plus partial tick); {@code seed} desyncs robots. */
    public static float rotorAngle(float time, int seed) {
        float a = (time * ROTOR_DEG_PER_TICK + (seed & 0xFF) * 37f) % 360f;
        return a < 0 ? a + 360f : a;
    }
}
