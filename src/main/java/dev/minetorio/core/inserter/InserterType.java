package dev.minetorio.core.inserter;

import dev.minetorio.core.Units;

public enum InserterType {
    BURNER(33, 1, true, Units.kw(94.2)),
    BASIC(24, 1, false, Units.kw(13.2)),
    LONG(17, 2, false, Units.kw(20)),
    FAST(9, 1, false, Units.kw(46.6));

    /** Full pickup, carry, return cycle. */
    public final int cycleTicks;
    public final int reach;
    public final boolean burner;
    public final long powerPerTickJ;

    InserterType(int cycleTicks, int reach, boolean burner, long powerPerTickJ) {
        this.cycleTicks = cycleTicks;
        this.reach = reach;
        this.burner = burner;
        this.powerPerTickJ = powerPerTickJ;
    }
}
