package dev.minetorio.core.oil;

/** Factorio 1.1 oil: yield 1 means 100%, 10 units/cycle; 300 cycles per percentage point.
 * Floor is the greater of 20% absolute and 20% of the initial yield.
 * Source: https://wiki.factorio.com/Crude_oil (mechanic unchanged since 0.15).
 */
public final class OilYield {
    private final double initialYield;
    private double remainingCycles;
    public OilYield(double initialYield) {
        if (!Double.isFinite(initialYield) || initialYield < .2) throw new IllegalArgumentException("Yield must be >=20%");
        this.initialYield = initialYield;
        remainingCycles = initialYield * 30000;
    }
    public static OilYield restore(double initialYield, double remainingCycles) {
        OilYield result = new OilYield(initialYield);
        if(!Double.isFinite(remainingCycles)) throw new IllegalArgumentException("Invalid cycles");
        result.remainingCycles = Math.clamp(remainingCycles, result.minimumCycles(), initialYield * 30000);
        return result;
    }
    private double minimumCycles() { return Math.max(6000, initialYield * 6000); }
    public double initialYield() { return initialYield; }
    public double remainingCycles() { return remainingCycles; }
    public double yield() { return remainingCycles / 30000; }
    public double amountPerCycle() { return Math.min(1000, this.yield() * 10); }
    /** Call only when a full pumpjack cycle was completed and its output fits. */
    public double extractCycle() {
        double produced = amountPerCycle();
        remainingCycles = Math.max(minimumCycles(), remainingCycles - 1);
        return produced;
    }
}
