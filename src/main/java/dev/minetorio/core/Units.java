package dev.minetorio.core;

/** Unit conversions: 20 ticks/s, 1 kW = 50 J/tick, energy in joules. */
public final class Units {
    public static final int TICKS_PER_SECOND = 20;
    public static final long JOULES_PER_TICK_PER_KW = 50L;

    private Units() {}

    public static int ticks(double seconds) { return (int) Math.round(seconds * TICKS_PER_SECOND); }

    /** Power in kW to joules per tick. */
    public static long kw(double kw) { return Math.round(kw * JOULES_PER_TICK_PER_KW); }

    /** Energy in MJ to joules. */
    public static long mj(double mj) { return Math.round(mj * 1_000_000d); }
}
