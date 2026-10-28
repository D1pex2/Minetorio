package dev.minetorio.core.power;

import dev.minetorio.core.Units;

/**
 * Factorio 1.1 accumulator: stores {@value #CAPACITY_MJ} MJ, charges and discharges at most
 * {@value #RATE_KW} kW. A {@link PowerStorage}: the network charges it from generator surplus and
 * drains it to cover a deficit. Remembers the net flow of the last settled tick for its lamps.
 */
public final class Accumulator implements PowerStorage {
    public static final double CAPACITY_MJ = 5;
    public static final double RATE_KW = 300;
    public static final long CAPACITY = Units.mj(CAPACITY_MJ);
    /** Joules per tick in or out at most. */
    public static final long RATE = Units.kw(RATE_KW);

    /** What the accumulator did in the last tick. */
    public enum Flow { IDLE, CHARGING, DISCHARGING }

    private long energy;
    private long flowThisTick;
    private long lastFlow;

    public long energy() { return energy; }

    /** Sets the stored energy (loading), clamped to 0..capacity. */
    public void setEnergy(long joules) { energy = Math.max(0, Math.min(CAPACITY, joules)); }

    /** Charge level 0..1. */
    public double level() { return (double) energy / CAPACITY; }

    @Override
    public long maxCharge() { return Math.min(RATE, CAPACITY - energy); }

    @Override
    public long maxDischarge() { return Math.min(RATE, energy); }

    @Override
    public void charge(long joules) {
        long j = Math.max(0, Math.min(joules, maxCharge()));
        energy += j;
        flowThisTick += j;
    }

    @Override
    public void discharge(long joules) {
        long j = Math.max(0, Math.min(joules, maxDischarge()));
        energy -= j;
        flowThisTick -= j;
    }

    /** Ends the tick: the net flow becomes {@link #lastFlow()}. Call once per tick, before the network settles. */
    public void endTick() {
        lastFlow = flowThisTick;
        flowThisTick = 0;
    }

    /** Net joules of the last finished tick: positive charging, negative discharging. */
    public long lastFlow() { return lastFlow; }

    public Flow flow() {
        return lastFlow > 0 ? Flow.CHARGING : lastFlow < 0 ? Flow.DISCHARGING : Flow.IDLE;
    }

    /** Lamps lit of {@code lamps} for the charge level: none when empty, each lamp a full share (rounded up). */
    public static int litLamps(long energy, int lamps) {
        if (energy <= 0) return 0;
        return (int) Math.min(lamps, (energy * lamps + CAPACITY - 1) / CAPACITY);
    }
}
