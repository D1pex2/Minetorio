package dev.minetorio.core.map;

/**
 * A token bucket counting in server ticks: at most {@code capacity} requests at once, then one
 * more every {@code ticksPerToken} ticks. Used per player to cap client-to-server queries.
 */
public final class RequestBudget {
    private final int capacity;
    private final int ticksPerToken;
    private int tokens;
    /** Tick up to which tokens have been refilled. */
    private long refilledTo = Long.MIN_VALUE;

    public RequestBudget(int capacity, int ticksPerToken) {
        if (capacity < 1 || ticksPerToken < 1) throw new IllegalArgumentException();
        this.capacity = capacity;
        this.ticksPerToken = ticksPerToken;
        this.tokens = capacity;
    }

    /** Takes one request at tick {@code now} if the budget allows it. */
    public boolean tryTake(long now) {
        if (refilledTo == Long.MIN_VALUE) {
            refilledTo = now;
        } else if (now > refilledTo) {
            long gained = (now - refilledTo) / ticksPerToken;
            if (gained > 0) {
                tokens = (int) Math.min(capacity, tokens + gained);
                refilledTo = tokens == capacity ? now : refilledTo + gained * ticksPerToken;
            }
        }
        if (tokens == 0) return false;
        tokens--;
        return true;
    }
}
