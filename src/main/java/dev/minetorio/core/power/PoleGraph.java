package dev.minetorio.core.power;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/** Poles auto-connect to every other pole within wire reach (Euclidean, inclusive). */
public final class PoleGraph {
    private final double wireReach;
    private final int supplyRadius;
    private final Set<PolePos> poles = new LinkedHashSet<>();
    private Map<PolePos, Integer> components = new HashMap<>();
    private boolean dirty = true;

    public PoleGraph(double wireReach, int supplyRadius) {
        this.wireReach = wireReach;
        this.supplyRadius = supplyRadius;
    }

    public void addPole(PolePos p) {
        if (poles.add(p)) dirty = true;
    }

    public void removePole(PolePos p) {
        if (poles.remove(p)) dirty = true;
    }

    public Set<PolePos> neighbors(PolePos p) {
        Set<PolePos> out = new HashSet<>();
        for (PolePos q : poles) {
            if (!q.equals(p) && distSq(p, q) <= wireReach * wireReach) out.add(q);
        }
        return out;
    }

    /** Equal for connected poles, different otherwise; may change after add/remove. -1 for unknown poles. */
    public int componentOf(PolePos p) {
        rebuild();
        return components.getOrDefault(p, -1);
    }

    public OptionalInt componentCovering(int x, int y, int z) {
        rebuild();
        for (PolePos p : poles) {
            if (Math.abs(p.x() - x) <= supplyRadius && Math.abs(p.z() - z) <= supplyRadius
                    && Math.abs(p.y() - y) <= supplyRadius) {
                return OptionalInt.of(components.get(p));
            }
        }
        return OptionalInt.empty();
    }

    private static double distSq(PolePos a, PolePos b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private void rebuild() {
        if (!dirty) return;
        components = new HashMap<>();
        int next = 0;
        for (PolePos start : poles) {
            if (components.containsKey(start)) continue;
            int id = next++;
            ArrayDeque<PolePos> queue = new ArrayDeque<>();
            queue.add(start);
            components.put(start, id);
            while (!queue.isEmpty()) {
                PolePos cur = queue.poll();
                for (PolePos n : neighbors(cur)) {
                    if (components.putIfAbsent(n, id) == null) queue.add(n);
                }
            }
        }
        dirty = false;
    }
}
