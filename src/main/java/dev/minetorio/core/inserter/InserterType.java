package dev.minetorio.core.inserter;

import dev.minetorio.core.Units;

/**
 * Inserter kinds (Factorio 1.1 values). Filter and stack inserters move at fast inserter speed; the
 * stack inserter's hand holds {@code handSize} items (1.1 base, no "inserter capacity bonus"
 * research yet) and, picking up from a belt, waits up to {@code fillTimeoutTicks} for each further
 * item before it swings with a partial hand.
 */
public enum InserterType {
    BURNER(33, 1, true, Units.kw(94.2), 1, 0, 0),
    BASIC(24, 1, false, Units.kw(13.2), 1, 0, 0),
    LONG(17, 2, false, Units.kw(20), 1, 0, 0),
    FAST(9, 1, false, Units.kw(46.6), 1, 0, 0),
    FILTER(9, 1, false, Units.kw(52), 1, 5, 0),
    STACK(9, 1, false, Units.kw(133), 2, 0, 40);

    /** Full pickup, carry, return cycle. */
    public final int cycleTicks;
    public final int reach;
    public final boolean burner;
    public final long powerPerTickJ;
    /** Items carried per swing. */
    public final int handSize;
    /** Item filter slots (0: no filter). */
    public final int filterSlots;
    /** Ticks without a new item after which a partly filled hand leaves a belt (0: never waits). */
    public final int fillTimeoutTicks;

    InserterType(int cycleTicks, int reach, boolean burner, long powerPerTickJ, int handSize, int filterSlots,
                 int fillTimeoutTicks) {
        this.cycleTicks = cycleTicks;
        this.reach = reach;
        this.burner = burner;
        this.powerPerTickJ = powerPerTickJ;
        this.handSize = handSize;
        this.filterSlots = filterSlots;
        this.fillTimeoutTicks = fillTimeoutTicks;
    }
}
