package dev.minetorio.core.inserter;

import dev.minetorio.core.belt.LaneItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A filter inserter's item filter (Factorio 1.1): a fixed number of slots, each empty or an item id,
 * and a whitelist/blacklist mode. Matching compares item ids and ignores data components (a stack
 * key's {@link LaneItem#baseId}). An empty whitelist passes nothing; an empty blacklist passes all.
 */
public final class ItemFilter implements Predicate<String> {
    private static final String MODE_WHITELIST = "whitelist";
    private static final String MODE_BLACKLIST = "blacklist";

    private final String[] slots;
    private boolean blacklist;

    public ItemFilter(int size) {
        if (size < 1) throw new IllegalArgumentException("size " + size);
        slots = new String[size];
    }

    public int size() { return slots.length; }

    /** Item id in slot {@code i}, or null if empty. */
    public String get(int i) { return slots[i]; }

    /** Sets slot {@code i} to an item id (stack keys are reduced to their id); null or blank empties it. */
    public void set(int i, String item) {
        slots[i] = item == null || item.isBlank() ? null : LaneItem.baseId(item);
    }

    public boolean isBlacklist() { return blacklist; }

    public void setBlacklist(boolean blacklist) { this.blacklist = blacklist; }

    /** Whether the inserter may move {@code key} (a stack key or item id). */
    public boolean matches(String key) {
        String id = LaneItem.baseId(key);
        boolean listed = false;
        for (String s : slots) {
            if (id.equals(s)) {
                listed = true;
                break;
            }
        }
        return listed != blacklist;
    }

    @Override
    public boolean test(String key) { return matches(key); }

    /** {@code mode} ("whitelist"/"blacklist") and {@code items} (one String per slot, "" for empty). */
    public Map<String, Object> save() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", blacklist ? MODE_BLACKLIST : MODE_WHITELIST);
        List<String> items = new ArrayList<>(slots.length);
        for (String s : slots) items.add(s == null ? "" : s);
        out.put("items", items);
        return out;
    }

    /** Restores {@link #save()} output; missing slots become empty, extra ones are ignored. */
    public void load(Map<String, ?> data) {
        blacklist = MODE_BLACKLIST.equals(data.get("mode"));
        Arrays.fill(slots, null);
        if (data.get("items") instanceof List<?> list) {
            for (int i = 0; i < Math.min(list.size(), slots.length); i++) {
                set(i, list.get(i) instanceof String s ? s : null);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemFilter f && f.blacklist == blacklist && Arrays.equals(f.slots, slots);
    }

    @Override
    public int hashCode() { return Objects.hash(blacklist, Arrays.hashCode(slots)); }

    @Override
    public String toString() { return (blacklist ? "blacklist" : "whitelist") + Arrays.toString(slots); }
}
