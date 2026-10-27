package dev.minetorio.core.belt;

/**
 * Ramp state of a straight belt (R25): like a vanilla rail, a belt rises or falls one block over
 * its one-block run. ASCENDING climbs toward its facing (its exit edge is one block higher than its
 * entry edge); DESCENDING falls toward its facing (its entry edge is one block higher). Lanes keep
 * their length of 1.0 on a ramp, so items cross a ramp in the same time as a flat belt.
 */
public enum BeltSlope {
    FLAT, ASCENDING, DESCENDING;

    /**
     * The slope of a belt: DESCENDING when a belt one block higher behind it feeds down into it,
     * otherwise ASCENDING when it is straight and feeds a belt one block higher in front, otherwise
     * FLAT. Curves are never ramps.
     */
    public static BeltSlope of(boolean fedFromAbove, boolean feedsUpward, boolean straight) {
        if (fedFromAbove) return DESCENDING;
        return feedsUpward && straight ? ASCENDING : FLAT;
    }

    /** Height of the belt surface above the flat plate at lane fraction {@code t} (0 entry .. 1 exit), in blocks. */
    public double rise(double t) {
        return switch (this) {
            case FLAT -> 0;
            case ASCENDING -> t;
            case DESCENDING -> 1 - t;
        };
    }

    /** Level of the entry edge relative to the belt's own block, in blocks. */
    public int entryLevel() {
        return this == DESCENDING ? 1 : 0;
    }

    /** Level of the exit edge relative to the belt's own block, in blocks. */
    public int exitLevel() {
        return this == ASCENDING ? 1 : 0;
    }

    /** Rise per block of run along the travel direction: 0, +1 or −1. */
    public int grade() {
        return exitLevel() - entryLevel();
    }
}
