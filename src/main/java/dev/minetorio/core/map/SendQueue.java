package dev.minetorio.core.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Chunks waiting to be sent to one client, without duplicates, oldest first, plus the client's byte
 * budget: {@link #refill} adds a fixed allowance each tick (up to a cap), a batch may go out while
 * the balance is positive ({@link #canSend}) and its size is then {@link #spend spent} (the balance
 * may go negative), so the average rate never exceeds the allowance.
 */
public final class SendQueue {
    private final LinkedHashSet<Long> pending = new LinkedHashSet<>();
    private long credit;

    public void add(long key) { pending.add(key); }

    /**
     * Adds a backlog (e.g. the whole explored map on login) region by region, nearest region to the
     * chunk (cx, cz) first and nearest chunks first inside each region, so the server reads each
     * region file once while sending it.
     */
    public void addAllByRegion(Collection<Long> keys, int cx, int cz) {
        long home = ChunkKeys.region(ChunkKeys.of(cx, cz));
        List<Long> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.comparingLong((Long k) -> squared(ChunkKeys.region(k), home))
                .thenComparingLong(k -> ChunkKeys.region(k))
                .thenComparingLong(k -> squared(k, ChunkKeys.of(cx, cz))));
        pending.addAll(sorted);
    }

    private static long squared(long a, long b) {
        long dx = (long) ChunkKeys.x(a) - ChunkKeys.x(b), dz = (long) ChunkKeys.z(a) - ChunkKeys.z(b);
        return dx * dx + dz * dz;
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

    /** One tick's allowance of {@code bytes}; the balance never exceeds {@code max}. */
    public void refill(long bytes, long max) { credit = Math.min(max, credit + bytes); }

    public boolean canSend() { return credit > 0; }

    public void spend(long bytes) { credit -= bytes; }
}
