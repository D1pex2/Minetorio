package dev.minetorio.core.power;

public interface PowerConsumer {
    /** Joules wanted this tick. */
    long demand();

    /** Fraction of demand satisfied this tick, 0..1. */
    void supply(double satisfaction);
}
