package dev.minetorio.core.logistics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits roboports into logistic networks: two roboports are in the same network when their
 * logistic areas touch or overlap, directly or through a chain of others (Factorio 1.1).
 */
public final class LogisticNetworks {
    private LogisticNetworks() {}

    /**
     * Groups of indices into {@code areas}, one group per network. Groups are ordered by their
     * lowest index and list their members in ascending order.
     */
    public static List<List<Integer>> group(List<LogisticArea> areas) {
        int n = areas.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (areas.get(i).touches(areas.get(j))) union(parent, i, j);
            }
        }
        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        return new ArrayList<>(groups.values());
    }

    /**
     * The index of the group whose areas contain the point, or -1. A point lies in at most one
     * network: areas that overlap are joined.
     */
    public static int networkAt(List<List<Integer>> groups, List<LogisticArea> areas, double x, double y, double z) {
        for (int g = 0; g < groups.size(); g++) {
            for (int i : groups.get(g)) if (areas.get(i).contains(x, y, z)) return g;
        }
        return -1;
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) {
            p[i] = p[p[i]];
            i = p[i];
        }
        return i;
    }

    private static void union(int[] p, int a, int b) {
        int ra = find(p, a), rb = find(p, b);
        if (ra == rb) return;
        if (ra < rb) p[rb] = ra;
        else p[ra] = rb;
    }
}
