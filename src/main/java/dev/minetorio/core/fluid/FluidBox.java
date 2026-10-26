package dev.minetorio.core.fluid;

/** A tank holding one fluid type at a time. Fluid ids: "water", "steam". */
public final class FluidBox {
    private static final double EPS = 1e-9;

    private final double capacity;
    private String fluid;
    private double amount;

    public FluidBox(double capacity) { this.capacity = capacity; }

    /** Current fluid id, or null when empty. */
    public String fluid() { return fluid; }

    public double amount() { return amount; }

    public double capacity() { return capacity; }

    public double fill() { return amount / capacity; }

    /** Inserts up to {@code amount}; returns the amount actually inserted (0 if a different fluid is present). */
    public double insert(String fluid, double amount) {
        if (amount <= 0 || (this.fluid != null && !this.fluid.equals(fluid))) return 0;
        double inserted = Math.min(amount, capacity - this.amount);
        if (inserted <= 0) return 0;
        this.fluid = fluid;
        this.amount += inserted;
        return inserted;
    }

    /** Removes up to {@code amount}; returns the amount actually removed. */
    public double extract(double amount) {
        if (amount <= 0 || this.amount <= 0) return 0;
        double removed = Math.min(amount, this.amount);
        this.amount -= removed;
        if (this.amount <= EPS) {
            this.amount = 0;
            fluid = null;
        }
        return removed;
    }
}
