package dev.minetorio.core.power;

import java.util.ArrayList;
import java.util.List;

public final class PowerNetwork {
    private final List<PowerConsumer> consumers = new ArrayList<>();
    private final List<PowerProducer> producers = new ArrayList<>();
    private double satisfaction = 1.0;

    public void add(PowerConsumer c) { consumers.add(c); }

    public void add(PowerProducer p) { producers.add(p); }

    public void remove(Object o) {
        consumers.remove(o);
        producers.remove(o);
    }

    public void tick() {
        long demand = 0;
        for (PowerConsumer c : consumers) demand += c.demand();
        long capacity = 0;
        for (PowerProducer p : producers) capacity += p.maxOutput();

        satisfaction = demand == 0 ? 1.0 : Math.min(1.0, (double) capacity / demand);
        if (capacity > 0) {
            double load = Math.min(1.0, (double) demand / capacity);
            for (PowerProducer p : new ArrayList<>(producers)) p.produce(Math.round(p.maxOutput() * load));
        }
        double supplied = capacity == 0 ? 0.0 : satisfaction;
        for (PowerConsumer c : new ArrayList<>(consumers)) c.supply(supplied);
    }

    public double satisfaction() { return satisfaction; }
}
