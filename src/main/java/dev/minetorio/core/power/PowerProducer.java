package dev.minetorio.core.power;

public interface PowerProducer {
    /** Maximum joules this producer can output this tick. */
    long maxOutput();

    /** Joules actually produced this tick. */
    void produce(long joules);
}
