package dev.minetorio.core.power;

import java.util.ArrayList;
import java.util.List;

/**
 * One electric network: its consumers, producers and storages (accumulators), settled once per
 * tick by Factorio's rules:
 * <ol>
 *   <li>Generators cover the consumers' demand and, beyond it, whatever the storages can take:
 *       all {@link PowerProducer.Priority#PRIMARY primary} output (solar) first, then
 *       {@link PowerProducer.Priority#SECONDARY secondary} (steam) for the rest.</li>
 *   <li>Generator energy left after the consumers charges the storages (surplus).</li>
 *   <li>A deficit is drained from the storages, up to their discharge rate.</li>
 * </ol>
 * Shares are split among the members of a group in proportion to their limits, exactly (the
 * joules handed out add up to the total). Consumers get satisfaction = supplied / demand.
 */
public final class PowerNetwork {
    private final List<PowerConsumer> consumers = new ArrayList<>();
    private final List<PowerProducer> producers = new ArrayList<>();
    private final List<PowerStorage> storages = new ArrayList<>();
    private double satisfaction = 1.0;
    private long lastDemand, lastGenerated, lastCharged, lastDischarged;

    public void add(PowerConsumer c) { consumers.add(c); }

    public void add(PowerProducer p) { producers.add(p); }

    public void add(PowerStorage s) { storages.add(s); }

    public void remove(Object o) {
        consumers.remove(o);
        producers.remove(o);
        storages.remove(o);
    }

    public void tick() {
        List<PowerConsumer> cs = new ArrayList<>(consumers);
        List<PowerProducer> ps = new ArrayList<>(producers);
        List<PowerStorage> ss = new ArrayList<>(storages);

        long demand = 0;
        for (PowerConsumer c : cs) demand += Math.max(0, c.demand());

        List<PowerProducer> primary = new ArrayList<>(), secondary = new ArrayList<>();
        for (PowerProducer p : ps) (p.priority() == PowerProducer.Priority.PRIMARY ? primary : secondary).add(p);
        long[] primaryMax = maxOutputs(primary), secondaryMax = maxOutputs(secondary);
        long primaryCap = sum(primaryMax), secondaryCap = sum(secondaryMax);

        long[] room = new long[ss.size()], stored = new long[ss.size()];
        for (int i = 0; i < ss.size(); i++) {
            room[i] = Math.max(0, ss.get(i).maxCharge());
            stored[i] = Math.max(0, ss.get(i).maxDischarge());
        }
        long roomTotal = sum(room), storedTotal = sum(stored);

        long need = demand + roomTotal;
        long fromPrimary = Math.min(primaryCap, need);
        long fromSecondary = Math.min(secondaryCap, need - fromPrimary);
        long generated = fromPrimary + fromSecondary;
        long toConsumers = Math.min(demand, generated);
        long charged = generated - toConsumers;
        long discharged = Math.min(storedTotal, demand - toConsumers);

        satisfaction = demand == 0 ? 1.0 : (double) (toConsumers + discharged) / demand;

        distribute(primary, primaryMax, fromPrimary);
        distribute(secondary, secondaryMax, fromSecondary);
        long[] in = split(charged, room), out = split(discharged, stored);
        for (int i = 0; i < ss.size(); i++) {
            if (in[i] > 0) ss.get(i).charge(in[i]);
            if (out[i] > 0) ss.get(i).discharge(out[i]);
        }
        double supplied = primaryCap + secondaryCap + storedTotal == 0 ? 0.0 : satisfaction;
        for (PowerConsumer c : cs) c.supply(supplied);

        lastDemand = demand;
        lastGenerated = generated;
        lastCharged = charged;
        lastDischarged = discharged;
    }

    public double satisfaction() { return satisfaction; }

    /** Joules the consumers asked for in the last tick. */
    public long lastDemand() { return lastDemand; }

    /** Joules the generators produced in the last tick (for consumers and storages). */
    public long lastGenerated() { return lastGenerated; }

    /** Joules put into storages in the last tick. */
    public long lastCharged() { return lastCharged; }

    /** Joules taken from storages in the last tick. */
    public long lastDischarged() { return lastDischarged; }

    private static long[] maxOutputs(List<PowerProducer> ps) {
        long[] out = new long[ps.size()];
        for (int i = 0; i < out.length; i++) out[i] = Math.max(0, ps.get(i).maxOutput());
        return out;
    }

    private static void distribute(List<PowerProducer> ps, long[] max, long total) {
        long[] share = split(total, max);
        for (int i = 0; i < share.length; i++) ps.get(i).produce(share[i]);
    }

    private static long sum(long[] a) {
        long s = 0;
        for (long v : a) s += v;
        return s;
    }

    /**
     * Splits {@code total} (at most the sum of {@code caps}) in proportion to {@code caps}: each gets
     * the floor of its share, the joules left over go one by one to the first members with room.
     */
    static long[] split(long total, long[] caps) {
        long[] out = new long[caps.length];
        long capTotal = sum(caps);
        if (total <= 0 || capTotal == 0) return out;
        total = Math.min(total, capTotal);
        long given = 0;
        for (int i = 0; i < caps.length; i++) {
            out[i] = (long) Math.floor((double) total * caps[i] / capTotal);
            out[i] = Math.min(out[i], caps[i]);
            given += out[i];
        }
        for (int i = 0; given < total && i < caps.length; i++) {
            long add = Math.min(caps[i] - out[i], total - given);
            out[i] += add;
            given += add;
        }
        return out;
    }
}
