package dev.minetorio.content.handcraft;

import dev.minetorio.core.Units;
import dev.minetorio.core.handcraft.HandcraftQueue;
import java.util.List;

/**
 * The client's mirror of its own hand-crafting queue, filled by {@link HandcraftPayloads.QueueSync}
 * (on login and whenever the queue changes) and reset on logout. Between syncs the front entry's
 * progress is advanced at 20 ticks per second of real time (capped at 1). Read on the client thread only.
 */
public final class ClientHandcraft {
    private static List<HandcraftQueue.Entry> entries = List.of();
    private static int stepTicks;
    private static long receivedNanos;

    private ClientHandcraft() {}

    public static List<HandcraftQueue.Entry> entries() { return entries; }

    /** Progress (0..1) of the front entry's current step, extrapolated since the last sync. */
    public static double frontProgress() {
        if (entries.isEmpty()) return 0;
        double p = entries.get(0).progress();
        if (stepTicks <= 0) return p;
        double ticks = (System.nanoTime() - receivedNanos) / 1e9 * Units.TICKS_PER_SECOND;
        return Math.min(1.0, p + ticks / stepTicks);
    }

    public static void reset() {
        entries = List.of();
        stepTicks = 0;
    }

    static void apply(HandcraftPayloads.QueueSync p) {
        entries = p.entries();
        stepTicks = p.stepTicks();
        receivedNanos = System.nanoTime();
    }
}
