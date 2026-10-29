package dev.minetorio.core.fluid;

/** A machine's tank holding one fluid type at a time (fluid ids: see {@link FluidData}). */
public final class FluidBox implements FluidStore {
    private static final double EPS = 1e-9;

    private final double capacity;
    private final String allowedFluid;
    private String fluid;
    private double amount;

    public FluidBox(double capacity) { this(capacity, null); }

    /** A recipe tank can reject other fluids even while empty. Null allows every fluid. */
    public FluidBox(double capacity, String allowedFluid) {
        if (!Double.isFinite(capacity) || capacity < 0) throw new IllegalArgumentException("Invalid tank capacity");
        this.capacity = capacity;
        this.allowedFluid = allowedFluid;
    }

    public boolean accepts(String fluid) { return fluid != null && (allowedFluid == null || allowedFluid.equals(fluid)); }

    @Override
    public String fluid() { return fluid; }

    @Override
    public double amount() { return amount; }

    @Override
    public double capacity() { return capacity; }

    @Override
    public double fill() { return capacity <= 0 ? 0 : amount / capacity; }

    @Override
    public double insert(String fluid, double amount) {
        if (!accepts(fluid) || !Double.isFinite(amount) || amount <= 0 || (this.fluid != null && !this.fluid.equals(fluid))) return 0;
        double inserted = Math.min(amount, capacity - this.amount);
        if (inserted <= 0) return 0;
        this.fluid = fluid;
        this.amount += inserted;
        return inserted;
    }

    @Override
    public double extract(double amount) {
        if (Double.isNaN(amount) || amount <= 0 || this.amount <= 0) return 0;
        double removed = Math.min(amount, this.amount);
        this.amount -= removed;
        if (this.amount <= EPS) {
            this.amount = 0;
            fluid = null;
        }
        return removed;
    }
}
