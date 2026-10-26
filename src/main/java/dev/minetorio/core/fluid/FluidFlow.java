package dev.minetorio.core.fluid;

public final class FluidFlow {
    private static final double FACTOR = 0.4;

    private FluidFlow() {}

    /** Moves fluid from the fuller box towards the emptier one, when the fluids are compatible. */
    public static void flow(FluidBox a, FluidBox b) {
        if (a.fluid() == null && b.fluid() == null) return;
        if (a.fluid() != null && b.fluid() != null && !a.fluid().equals(b.fluid())) return;
        String fluid = a.fluid() != null ? a.fluid() : b.fluid();
        double t = FACTOR * (a.fill() - b.fill()) * Math.min(a.capacity(), b.capacity());
        FluidBox from = t > 0 ? a : b;
        FluidBox to = t > 0 ? b : a;
        double want = Math.abs(t);
        want = Math.min(want, from.amount());
        want = Math.min(want, to.capacity() - to.amount());
        if (want <= 0) return;
        double moved = from.extract(want);
        to.insert(fluid, moved);
    }
}
