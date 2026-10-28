package dev.minetorio.core.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fast-replace groups (Factorio's {@code fast_replaceable_group}): items in one group can replace
 * each other in place, keeping orientation, shape and settings. The upgrade planner only accepts
 * rules within a group. Each group lists its members from lowest to highest tier.
 */
public final class UpgradeGroups {
    private static final String M = "minetorio:";
    private static final Map<String, List<String>> GROUPS = build();
    private static final Map<String, String> GROUP_OF = index();

    private UpgradeGroups() {}

    private static Map<String, List<String>> build() {
        Map<String, List<String>> g = new LinkedHashMap<>();
        g.put("transport_belt", ids("transport_belt", "fast_transport_belt", "express_transport_belt"));
        g.put("underground_belt", ids("underground_belt", "fast_underground_belt", "express_underground_belt"));
        g.put("splitter", ids("splitter", "fast_splitter", "express_splitter"));
        g.put("inserter", ids("burner_inserter", "inserter", "long_handed_inserter", "fast_inserter", "filter_inserter",
                "stack_inserter"));
        g.put("assembling_machine", ids("assembling_machine_1", "assembling_machine_2"));
        return Collections.unmodifiableMap(g);
    }

    private static List<String> ids(String... paths) {
        return java.util.Arrays.stream(paths).map(p -> M + p).toList();
    }

    private static Map<String, String> index() {
        Map<String, String> m = new LinkedHashMap<>();
        GROUPS.forEach((group, items) -> items.forEach(i -> m.put(i, group)));
        return Collections.unmodifiableMap(m);
    }

    /** Group name → members (lowest tier first). */
    public static Map<String, List<String>> groups() { return GROUPS; }

    /** Every item that belongs to a group, group by group. */
    public static List<String> items() { return List.copyOf(GROUP_OF.keySet()); }

    public static Optional<String> groupOf(String item) { return Optional.ofNullable(GROUP_OF.get(item)); }

    /** Whether {@code to} may replace {@code from}: both in the same group and not the same item. */
    public static boolean compatible(String from, String to) {
        String g = GROUP_OF.get(from);
        return g != null && !from.equals(to) && g.equals(GROUP_OF.get(to));
    }

    /** Items that may replace {@code from} (its group without itself). */
    public static List<String> replacements(String from) {
        String g = GROUP_OF.get(from);
        return g == null ? List.of() : GROUPS.get(g).stream().filter(i -> !i.equals(from)).toList();
    }
}
