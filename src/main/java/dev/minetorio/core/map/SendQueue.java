package dev.minetorio.core.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/** Chunks waiting to be sent to one client, without duplicates, oldest first. */
public final class SendQueue {
    private final LinkedHashSet<Long> pending = new LinkedHashSet<>();

    public void add(long key) { pending.add(key); }

    /** Adds a backlog (e.g. the whole explored map on login), nearest to (cx, cz) first. */
    public void addAllNearestFirst(Collection<Long> keys, int cx, int cz) {
        List<Long> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.comparingLong((Long k) -> {
            long dx = ChunkKeys.x(k) - (long) cx, dz = ChunkKeys.z(k) - (long) cz;
            return dx * dx + dz * dz;
        }));
        pending.addAll(sorted);
    }

    /** Removes and returns up to {@code max} chunks. */
    public List<Long> poll(int max) {
        List<Long> out = new ArrayList<>(Math.min(max, pending.size()));
        for (Iterator<Long> it = pending.iterator(); it.hasNext() && out.size() < max; ) {
            out.add(it.next());
            it.remove();
        }
        return out;
    }

    public int size() { return pending.size(); }

    public void clear() { pending.clear(); }
}
