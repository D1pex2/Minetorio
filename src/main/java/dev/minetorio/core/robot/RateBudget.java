package dev.minetorio.core.robot;

/**
 * Token bucket against request floods: a burst of {@code burst} requests, refilled by
 * {@code refillPerTick} per game tick. Time is the caller's tick counter.
 */
public final class RateBudget {
    private final int burst;
    private final double refillPerTick;
    private double tokens;
    private long lastTick;
    private boolean started;

    public RateBudget(int burst, double refillPerTick) {
        if (burst < 1 || refillPerTick < 0) throw new IllegalArgumentException("burst " + burst + ", refill " + refillPerTick);
        this.burst = burst;
        this.refillPerTick = refillPerTick;
        this.tokens = burst;
    }

    /** Takes {@code n} tokens at tick {@code now}; false (and nothing taken) when there are not enough. */
    public boolean take(long now, int n) {
        if (started) tokens = Math.min(burst, tokens + Math.max(0, now - lastTick) * refillPerTick);
        started = true;
        lastTick = now;
        if (n > tokens) return false;
        tokens -= n;
        return true;
    }

    /** Tokens left as of the last {@link #take}. */
    public double tokens() {
        return tokens;
    }
}
