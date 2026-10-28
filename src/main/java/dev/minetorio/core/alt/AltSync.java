package dev.minetorio.core.alt;

/**
 * Server-side throttle for a machine's alt info: the machine evaluates its {@link AltInfo} only
 * every {@value #PERIOD} ticks (staggered by a per-machine seed so not all machines check in the
 * same tick) and sends it only when it differs from what was last sent. So a machine sends at most
 * one update per period, and none while nothing changes.
 */
public final class AltSync {
    public static final int PERIOD = 10;

    private AltInfo sent;

    /** Whether the machine with {@code seed} (e.g. its packed position) checks its alt info at {@code gameTime}. */
    public static boolean due(long gameTime, long seed) {
        return Math.floorMod(gameTime + Long.hashCode(seed), PERIOD) == 0;
    }

    /** Records {@code now}; true if it differs from the last recorded value (so it must be sent). */
    public boolean offer(AltInfo now) {
        if (now.equals(sent)) return false;
        sent = now;
        return true;
    }

    /** Last value recorded, or null before the first. */
    public AltInfo sent() { return sent; }
}
