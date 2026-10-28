package dev.minetorio.core.robot;

/** A construction robot's battery (see {@link RobotStats}): drained by flight, recharged at a roboport. */
public final class RobotBattery {
    private long energy;

    public RobotBattery(long energy) {
        this.energy = Math.max(0, Math.min(RobotStats.BATTERY_J, energy));
    }

    public static RobotBattery full() { return new RobotBattery(RobotStats.BATTERY_J); }

    public long energy() { return energy; }

    public double fraction() { return energy / (double) RobotStats.BATTERY_J; }

    public boolean isEmpty() { return energy <= 0; }

    public boolean isFull() { return energy >= RobotStats.BATTERY_J; }

    /** Below {@link RobotStats#MIN_TO_CHARGE}: should recharge before the next job. */
    public boolean needsCharge() { return fraction() < RobotStats.MIN_TO_CHARGE; }

    /** 1, or {@link RobotStats#LOW_ENERGY_SPEED_FACTOR} once empty. */
    public double speedFactor() { return isEmpty() ? RobotStats.LOW_ENERGY_SPEED_FACTOR : 1.0; }

    /** One tick of flight covering {@code blocks}: the per-block cost plus the constant drain. */
    public void fly(double blocks) {
        long cost = RobotStats.DRAIN_PER_TICK_J + Math.round(blocks * RobotStats.ENERGY_PER_BLOCK_J);
        energy = Math.max(0, energy - cost);
    }

    /** Takes up to {@code offered} joules; returns how much was accepted. */
    public long charge(long offered) {
        long accepted = Math.max(0, Math.min(offered, RobotStats.BATTERY_J - energy));
        energy += accepted;
        return accepted;
    }
}
