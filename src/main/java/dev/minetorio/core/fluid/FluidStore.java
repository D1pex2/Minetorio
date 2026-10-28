package dev.minetorio.core.fluid;

/**
 * Something that holds one fluid at a time: a machine's {@link FluidBox} or a pipe segment
 * ({@link FluidNetwork.Segment}). Fluid ids are the plain ids of {@link FluidData#fluids()}
 * ("water", "crude_oil", ...); {@code fluid()} is null while empty.
 */
public interface FluidStore {
    /** Current fluid id, or null when empty. */
    String fluid();

    double amount();

    double capacity();

    /** Inserts up to {@code amount} of {@code fluid}; returns the amount inserted (0 if another fluid is inside). */
    double insert(String fluid, double amount);

    /** Removes up to {@code amount}; returns the amount removed. Emptying clears the fluid. */
    double extract(double amount);

    /** Filled fraction 0..1 (0 for no capacity). */
    default double fill() {
        return capacity() <= 0 ? 0 : amount() / capacity();
    }

    /** Free room. */
    default double room() {
        return Math.max(0, capacity() - amount());
    }
}
