package dev.minetorio.core.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The chunks under continuous observation: the union of the square areas (radius {@code r}, so
 * (2r+1)² chunks) of the registered watchers (working radars), kept incrementally. Each chunk
 * remembers which watchers cover it, so removing one watcher keeps the chunks another still
 * covers. Every change to the union is recorded as a net diff ({@link #drain}): a chunk that
 * became watched and unwatched again since the last drain is in neither list.
 *
 * <p>Watchers and chunks are packed {@code long}s ({@link ChunkKeys} for chunks; any unique id for
 * watchers, e.g. a block position).
 */
public final class WatchCoverage {
    /** No watcher of that id ({@link #centerOf}). */
    public static final long NONE = Long.MIN_VALUE;

    private record Area(long center, int radius) {}

    private final Map<Long, Area> watchers = new HashMap<>();
    private final Map<Long, Set<Long>> coveredBy = new HashMap<>();
    private final Diff pending = new Diff();

    /** A net change of the watched set: chunks that became watched and chunks that stopped being watched. */
    public static final class Diff {
        private final Set<Long> added = new LinkedHashSet<>();
        private final Set<Long> removed = new LinkedHashSet<>();

        void watched(long chunk) {
            if (!removed.remove(chunk)) added.add(chunk);
        }

        void unwatched(long chunk) {
            if (!added.remove(chunk)) removed.add(chunk);
        }

        public Set<Long> added() { return Collections.unmodifiableSet(added); }

        public Set<Long> removed() { return Collections.unmodifiableSet(removed); }

        public boolean isEmpty() { return added.isEmpty() && removed.isEmpty(); }

        Diff copyAndClear() {
            Diff d = new Diff();
            d.added.addAll(added);
            d.removed.addAll(removed);
            added.clear();
            removed.clear();
            return d;
        }
    }

    /**
     * Registers (or moves) a watcher covering the square of {@code radius} chunks around
     * {@code center}. Returns whether anything changed for the watcher.
     */
    public boolean put(long watcher, long center, int radius) {
        Area area = new Area(center, radius);
        Area old = watchers.get(watcher);
        if (area.equals(old)) return false;
        if (old != null) remove(watcher);
        watchers.put(watcher, area);
        for (long k : ChunkKeys.around(ChunkKeys.x(center), ChunkKeys.z(center), radius)) {
            Set<Long> by = coveredBy.computeIfAbsent(k, x -> new HashSet<>(2));
            if (by.isEmpty()) pending.watched(k);
            by.add(watcher);
        }
        return true;
    }

    /** Unregisters a watcher; chunks no other watcher covers stop being watched. Returns whether it was registered. */
    public boolean remove(long watcher) {
        Area area = watchers.remove(watcher);
        if (area == null) return false;
        for (long k : ChunkKeys.around(ChunkKeys.x(area.center), ChunkKeys.z(area.center), area.radius)) {
            Set<Long> by = coveredBy.get(k);
            if (by == null || !by.remove(watcher)) continue;
            if (by.isEmpty()) {
                coveredBy.remove(k);
                pending.unwatched(k);
            }
        }
        return true;
    }

    /** Center chunk of a registered watcher, or {@link #NONE}. */
    public long centerOf(long watcher) {
        Area a = watchers.get(watcher);
        return a == null ? NONE : a.center;
    }

    public boolean isWatched(long chunk) { return coveredBy.containsKey(chunk); }

    /** The watchers covering a chunk (empty if none). */
    public Set<Long> watchedBy(long chunk) {
        Set<Long> by = coveredBy.get(chunk);
        return by == null ? Set.of() : Collections.unmodifiableSet(by);
    }

    /** Number of watched chunks. */
    public int size() { return coveredBy.size(); }

    public int watcherCount() { return watchers.size(); }

    /** A copy of the watched chunks. */
    public List<Long> chunks() { return new ArrayList<>(coveredBy.keySet()); }

    /** The net change since the last drain (then forgotten). */
    public Diff drain() { return pending.copyAndClear(); }

    /** Whether there is an undrained change. */
    public boolean hasChanges() { return !pending.isEmpty(); }

    /** Forgets every watcher; the chunks they covered are recorded as removed. */
    public void clear() {
        for (long k : coveredBy.keySet()) pending.unwatched(k);
        coveredBy.clear();
        watchers.clear();
    }

    /**
     * Splits keys into consecutive pieces of at most {@code max} (at least one piece, possibly empty),
     * for bounded network payloads.
     */
    public static List<long[]> split(Iterable<Long> keys, int max) {
        if (max <= 0) throw new IllegalArgumentException("max " + max);
        List<long[]> out = new ArrayList<>();
        long[] buf = new long[max];
        int n = 0;
        for (long k : keys) {
            buf[n++] = k;
            if (n == max) {
                out.add(buf.clone());
                n = 0;
            }
        }
        if (n > 0 || out.isEmpty()) out.add(java.util.Arrays.copyOf(buf, n));
        return out;
    }
}
