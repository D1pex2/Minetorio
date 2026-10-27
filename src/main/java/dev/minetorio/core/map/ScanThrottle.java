package dev.minetorio.core.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Keeps re-scans of a chunk at least {@code minInterval} ticks apart: a request too soon after the
 * last scan is remembered once and handed back by {@link #due} when the interval has passed, so a
 * chunk that keeps changing is scanned (and sent to clients) at most once per interval.
 */
public final class ScanThrottle {
    private final int minInterval;
    private final Map<Long, Long> lastScan = new HashMap<>();
    private final Map<Long, Long> deferred = new HashMap<>();

    public ScanThrottle(int minInterval) {
        this.minInterval = minInterval;
    }

    /** Whether the chunk may be scanned now; if not, it is deferred until it may. */
    public boolean request(long key, long now) {
        Long last = lastScan.get(key);
        if (last == null || now - last >= minInterval) return true;
        deferred.putIfAbsent(key, last + minInterval);
        return false;
    }

    public void scanned(long key, long now) {
        lastScan.put(key, now);
        deferred.remove(key);
    }

    /** Deferred chunks whose interval has passed (each handed out once). */
    public List<Long> due(long now) {
        if (deferred.isEmpty()) return List.of();
        List<Long> out = new ArrayList<>();
        for (Iterator<Map.Entry<Long, Long>> it = deferred.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Long> e = it.next();
            if (e.getValue() <= now) {
                out.add(e.getKey());
                it.remove();
            }
        }
        return out;
    }

    /** Forgets scans older than the interval (they no longer restrict anything). */
    public void prune(long now) {
        lastScan.values().removeIf(t -> now - t >= minInterval);
    }

    /** Chunks whose last scan is remembered. */
    public int tracked() { return lastScan.size(); }

    public void clear() {
        lastScan.clear();
        deferred.clear();
    }
}
