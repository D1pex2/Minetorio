package dev.minetorio.core.power;

public interface PowerProducer {
    /**
     * Which producers a network draws on first (Factorio's output priorities): all
     * {@link #PRIMARY} output (solar panels) is used before any {@link #SECONDARY} (steam engines).
     */
    enum Priority { PRIMARY, SECONDARY }

    /** Maximum joules this producer can output this tick. */
    long maxOutput();

    /** Joules actually produced this tick (0 included: called every tick while in a network). */
    void produce(long joules);

    default Priority priority() { return Priority.SECONDARY; }
}
