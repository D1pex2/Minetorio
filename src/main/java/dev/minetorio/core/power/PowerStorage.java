package dev.minetorio.core.power;

/**
 * Energy storage on a network (accumulator): charged from generator surplus, discharged to cover a
 * deficit, never from or into another storage (Factorio's tertiary priority).
 */
public interface PowerStorage {
    /** Joules it can take this tick. */
    long maxCharge();

    /** Joules it can give this tick. */
    long maxDischarge();

    /** Stores {@code joules} (at most {@link #maxCharge()}). */
    void charge(long joules);

    /** Gives {@code joules} (at most {@link #maxDischarge()}). */
    void discharge(long joules);
}
