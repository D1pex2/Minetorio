package dev.minetorio.core.fluid;

/**
 * What alt mode shows for a pipe, tank or machine box: the fluid (null: empty) and its fill level in
 * {@value #STEPS} steps. Coarse on purpose: the server sends it only when the step changes.
 */
public record FluidGauge(String fluid, int level) {
    public static final int STEPS = 10;
    public static final FluidGauge EMPTY = new FluidGauge(null, 0);

    public FluidGauge {
        level = Math.max(0, Math.min(STEPS, level));
        if (fluid == null || fluid.isEmpty()) {
            fluid = null;
            level = 0;
        }
    }

    /** Gauge of a store; a store with any fluid shows at least one step. */
    public static FluidGauge of(FluidStore store) {
        if (store == null) return EMPTY;
        return of(store.fluid(), store.fill());
    }

    public static FluidGauge of(String fluid, double fill) {
        if (fluid == null || fill <= 0) return EMPTY;
        return new FluidGauge(fluid, Math.max(1, (int) Math.round(fill * STEPS)));
    }

    public boolean isEmpty() { return fluid == null; }

    public float fill() { return (float) level / STEPS; }
}
