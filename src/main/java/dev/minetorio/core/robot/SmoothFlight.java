package dev.minetorio.core.robot;

/**
 * Client-side flight of one robot, simulated tick by tick from the synced target and speed so it
 * moves exactly like the server's {@link Flight#step} without needing a packet per tick. The server
 * stays authoritative: its occasional position samples are compared with the simulation and any
 * real drift is blended away over a few ticks (or snapped when far off). The heading turns smoothly
 * toward the direction of travel and the robot banks into turns.
 */
public final class SmoothFlight {
    /** Further than this from a server sample: teleport there. */
    public static final double SNAP_DISTANCE = 8.0;
    /** Share of the remaining correction applied per tick. */
    public static final double BLEND_RATE = 0.2;
    /** Share of the remaining heading change turned per tick, and its cap in degrees. */
    public static final float TURN_RATE = 0.3f;
    public static final float MAX_TURN_PER_TICK = 30f;
    /** Bank (degrees, + = right side down) per degree of yaw change per tick, its cap and smoothing. */
    public static final float BANK_PER_TURN = 0.7f;
    public static final float MAX_BANK = 20f;
    public static final float BANK_RATE = 0.25f;

    private static final Flight.Vec ZERO = new Flight.Vec(0, 0, 0);

    private Flight.Vec pos;
    private Flight.Vec target;
    private double speed;
    private Flight.Vec correction = ZERO;
    private Flight.Vec pendingSample;
    private float yaw;
    private float bank;

    public SmoothFlight(Flight.Vec pos, float yaw) {
        this.pos = pos;
        this.target = pos;
        this.yaw = wrapDegrees(yaw);
    }

    public Flight.Vec pos() { return pos; }

    public float yaw() { return yaw; }

    public float bank() { return bank; }

    /** Where the robot is headed and how fast (blocks per tick); 0 = hold position. */
    public void setTarget(Flight.Vec target, double speed) {
        this.target = target;
        this.speed = speed;
    }

    /** A position the server reported; checked against the simulation on the next tick. */
    public void sample(Flight.Vec serverPos) {
        this.pendingSample = serverPos;
    }

    /**
     * How far the simulation may be from a server sample before it is corrected. In flight the two
     * are time-shifted by network latency along the same path (and re-meet exactly at each waypoint),
     * so only errors bigger than a few ticks of travel are real drift.
     */
    public static double tolerance(double speed, boolean moving) {
        return moving ? 0.75 + 3 * speed : 0.01;
    }

    public void tick() {
        boolean moving = speed > 0 && Flight.distance(pos, target) > 1e-9;
        if (pendingSample != null) {
            Flight.Vec err = sub(pendingSample, pos);
            double e = length(err);
            if (e > SNAP_DISTANCE) {
                pos = pendingSample;
                correction = ZERO;
                moving = speed > 0 && Flight.distance(pos, target) > 1e-9;
            } else if (e > tolerance(speed, moving)) {
                correction = err;
            }
            pendingSample = null;
        }

        Flight.Vec stepped = moving ? Flight.step(pos, target, speed) : pos;
        double dx = stepped.x() - pos.x(), dz = stepped.z() - pos.z();
        Flight.Vec applied = scale(correction, BLEND_RATE);
        correction = sub(correction, applied);
        if (length(correction) < 1e-4) {
            applied = add(applied, correction);
            correction = ZERO;
        }
        pos = add(stepped, applied);

        float before = yaw;
        if (dx * dx + dz * dz > 1e-8) {
            float desired = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            yaw = turnToward(yaw, desired, TURN_RATE, MAX_TURN_PER_TICK);
        }
        float turned = wrapDegrees(yaw - before);
        float wanted = clamp(turned * BANK_PER_TURN, -MAX_BANK, MAX_BANK);
        bank += (wanted - bank) * BANK_RATE;
        if (Math.abs(bank) < 1e-3f) bank = 0;
    }

    /** {@code current} turned toward {@code desired} by {@code fraction} of the gap, at most {@code maxStep} degrees, wrapped to [-180, 180). */
    public static float turnToward(float current, float desired, float fraction, float maxStep) {
        float gap = wrapDegrees(desired - current);
        float step = clamp(gap * fraction, -maxStep, maxStep);
        if (Math.abs(gap) < 0.05f) step = gap;
        return wrapDegrees(current + step);
    }

    /** {@code deg} in [-180, 180). */
    public static float wrapDegrees(float deg) {
        float d = deg % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static Flight.Vec add(Flight.Vec a, Flight.Vec b) { return new Flight.Vec(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }

    private static Flight.Vec sub(Flight.Vec a, Flight.Vec b) { return new Flight.Vec(a.x() - b.x(), a.y() - b.y(), a.z() - b.z()); }

    private static Flight.Vec scale(Flight.Vec a, double k) { return new Flight.Vec(a.x() * k, a.y() * k, a.z() * k); }

    private static double length(Flight.Vec a) { return Flight.distance(ZERO, a); }
}
