package dev.minetorio.core.map;

import dev.minetorio.core.Units;

/**
 * Timing of a radar (Factorio 1.1 radar): it draws {@link #POWER} constantly. While it gets any
 * power it re-scans the chunks within {@value #NEAR_RADIUS} of itself (a 7×7 area) every
 * {@link #NEAR_SCAN_TICKS} ticks, keeping them live; and it charges toward the next distant chunk,
 * one every {@link #REVEAL_TICKS} ticks (30 s) at full satisfaction, proportionally slower with
 * less. Distant chunks come from a {@link RadarFrontier} of radius {@value #FAR_RADIUS}.
 */
public final class RadarLogic {
    public static final long POWER = Units.kw(300);
    public static final int NEAR_RADIUS = 3;
    public static final int NEAR_SCAN_TICKS = Units.ticks(5);
    public static final int REVEAL_TICKS = Units.ticks(30);
    public static final int FAR_RADIUS = 50;
    private static final double DONE = 1 - 1e-9;

    /** Charge toward the next distant chunk, 0..1. */
    private double progress;
    private int nearCooldown;

    /**
     * One tick at the given satisfaction (0..1). Returns whether the near area is to be scanned
     * now (the first powered tick, then every {@link #NEAR_SCAN_TICKS} powered ticks).
     */
    public boolean tick(double satisfaction) {
        if (satisfaction <= 0) return false;
        progress = Math.min(1, progress + satisfaction / REVEAL_TICKS);
        if (nearCooldown > 0) nearCooldown--;
        if (nearCooldown > 0) return false;
        nearCooldown = NEAR_SCAN_TICKS;
        return true;
    }

    /** A distant chunk is due; call {@link #revealed} once one was handed out. */
    public boolean revealDue() { return progress >= DONE; }

    public void revealed() { progress = 0; }

    public double progress() { return progress; }

    public void load(double progress) { this.progress = Math.max(0, Math.min(1, progress)); }
}
