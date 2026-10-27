package dev.minetorio.core.map;

/**
 * When the client asks the server about the column under the map cursor: never more often than
 * every {@code minIntervalMs}, and for the same column again only after {@code refreshMs} (so
 * amounts being mined stay roughly current).
 */
public final class HoverThrottle {
    private final long minIntervalMs;
    private final long refreshMs;
    private boolean asked;
    private int lastX;
    private int lastZ;
    private long lastAt;

    public HoverThrottle(long minIntervalMs, long refreshMs) {
        this.minIntervalMs = minIntervalMs;
        this.refreshMs = refreshMs;
    }

    /** Whether to ask about column (x, z) now ({@code nowMs}); if so, it counts as asked. */
    public boolean shouldAsk(int x, int z, long nowMs) {
        if (asked) {
            long since = nowMs - lastAt;
            if (since < minIntervalMs) return false;
            if (x == lastX && z == lastZ && since < refreshMs) return false;
        }
        asked = true;
        lastX = x;
        lastZ = z;
        lastAt = nowMs;
        return true;
    }

    /** Forgets the last question (e.g. when the map opens again). */
    public void reset() { asked = false; }
}
