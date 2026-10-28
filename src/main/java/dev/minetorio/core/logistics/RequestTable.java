package dev.minetorio.core.logistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A fixed number of request slots (a requester or buffer chest's requests, a storage chest's
 * filter, a player's personal requests). An item is requested in at most one slot: setting it in
 * another slot moves it there. {@link #version} changes with every edit (for syncing to clients).
 */
public final class RequestTable {
    private final LogisticRequest[] slots;
    private int version;

    public RequestTable(int size) {
        this.slots = new LogisticRequest[size];
    }

    public int size() { return slots.length; }

    public int version() { return version; }

    /** The request in slot {@code i}, or null. */
    public LogisticRequest get(int i) {
        return i >= 0 && i < slots.length ? slots[i] : null;
    }

    /** Sets slot {@code i} (null clears it); the same item in another slot is cleared. False for a bad slot. */
    public boolean set(int i, LogisticRequest r) {
        if (i < 0 || i >= slots.length) return false;
        if (r != null) {
            for (int j = 0; j < slots.length; j++) {
                if (j != i && slots[j] != null && slots[j].item().equals(r.item())) slots[j] = null;
            }
        }
        slots[i] = r;
        version++;
        return true;
    }

    /** Changes only the counts of slot {@code i}; false if it is empty. */
    public boolean setCounts(int i, int min, int max) {
        LogisticRequest r = get(i);
        if (r == null) return false;
        slots[i] = new LogisticRequest(r.item(), min, max);
        version++;
        return true;
    }

    public void clear() {
        Arrays.fill(slots, null);
        version++;
    }

    /** Copies every slot from {@code o} (same size or truncated). */
    public void copyFrom(RequestTable o) {
        for (int i = 0; i < slots.length; i++) slots[i] = o.get(i);
        version++;
    }

    /** The set requests by item, in slot order. */
    public Map<String, LogisticRequest> byItem() {
        Map<String, LogisticRequest> out = new LinkedHashMap<>();
        for (LogisticRequest r : slots) if (r != null) out.putIfAbsent(r.item(), r);
        return out;
    }

    /** The set requests, in slot order. */
    public List<LogisticRequest> requests() {
        List<LogisticRequest> out = new ArrayList<>();
        for (LogisticRequest r : slots) if (r != null) out.add(r);
        return out;
    }

    public boolean isEmpty() {
        for (LogisticRequest r : slots) if (r != null) return false;
        return true;
    }
}
