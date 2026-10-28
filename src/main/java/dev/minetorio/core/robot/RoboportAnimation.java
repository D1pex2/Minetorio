package dev.minetorio.core.robot;

/**
 * Timing of the roboport's animations (after the Factorio 1.1 roboport): the roof iris opens when a
 * robot launches or comes to the dock and closes {@value #DOOR_HOLD_TICKS} ticks after the last one;
 * the antenna turns while the roboport has power; status lamps blink slowly (green with power, red
 * without); the charging points glow and flicker while robots charge. The server decides only the
 * door's open/closed state, the number of robots charging and the power flag (synced on change); the
 * client moves the door and the antenna smoothly from those.
 */
public final class RoboportAnimation {
    private RoboportAnimation() {}

    /** Ticks the door stays open after the last robot activity at the dock. */
    public static final int DOOR_HOLD_TICKS = 30;
    /** Ticks for the iris to open fully (and to close). */
    public static final int DOOR_TICKS = 10;
    /** Antenna turn per tick while powered, in degrees. */
    public static final float ANTENNA_DEG_PER_TICK = 2f;
    /** Robots within this distance (blocks) of the dock keep the door open. */
    public static final double DOOR_REACH = 2.5;

    /** Server: the door hold counter after one tick; the door is open while it is above zero. */
    public static int nextHold(int hold, boolean activity) {
        return activity ? DOOR_HOLD_TICKS : Math.max(0, hold - 1);
    }

    /** Client: the door's linear progress (0 closed .. 1 open) after one tick toward its target. */
    public static float stepDoor(float door, boolean open) {
        float d = door + (open ? 1f : -1f) / DOOR_TICKS;
        return Math.max(0f, Math.min(1f, d));
    }

    /** Eases linear progress so the blades start and stop softly. */
    public static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3 - 2 * t);
    }

    /** Client: the antenna angle after one tick, in [0, 360). */
    public static float stepAntenna(float deg, boolean powered) {
        return powered ? (deg + ANTENNA_DEG_PER_TICK) % 360f : deg;
    }

    /**
     * Whether status lamp {@code index} is lit at {@code time} (game ticks): with power each lamp
     * blinks a quarter of every 2 s, the lamps out of step with each other; without power all of
     * them blink together, briefly, every 3 s.
     */
    public static boolean lampOn(long time, int index, boolean powered) {
        if (powered) return Math.floorMod(time + index * 11L, 40) < 10;
        return Math.floorMod(time, 60) < 6;
    }

    /**
     * Brightness pulse of a lit charging point (scale of its glow, 1 and up), or 0 for a flicker frame
     * in which it is not drawn.
     */
    public static float chargePulse(long time, float partialTick, int index) {
        if (Math.floorMod(time * 7 + index * 13L, 23) == 0) return 0f;
        double t = time + partialTick + index * 5;
        return 1f + 0.12f * (float) Math.abs(Math.sin(t * 0.55));
    }
}
