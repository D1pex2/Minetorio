package dev.minetorio.core.power;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Poles of any {@link PoleType}, keyed by their footprint's corner ({@link PolePos}). A pole
 * auto-connects to every other pole whose footprint center lies within the smaller of the two wire
 * reaches (Euclidean, 3D, inclusive), as Factorio does; connected poles form components.
 */
public final class PoleGraph {
    private final Map<PolePos, PoleType> poles = new LinkedHashMap<>();
    private Map<PolePos, Integer> components = new HashMap<>();
    private boolean dirty = true;

    public void addPole(PolePos p, PoleType type) {
        if (poles.put(p, type) != type) dirty = true;
    }

    public void removePole(PolePos p) {
        if (poles.remove(p) != null) dirty = true;
    }

    public boolean contains(PolePos p) { return poles.containsKey(p); }

    /** Whether poles {@code a} (of type {@code ta}) and {@code b} (of {@code tb}) are close enough to be wired. */
    public static boolean inReach(PolePos a, PoleType ta, PolePos b, PoleType tb) {
        double reach = Math.min(ta.wireReach(), tb.wireReach());
        double dx = (a.x() + ta.size() / 2.0) - (b.x() + tb.size() / 2.0);
        double dy = a.y() - b.y();
        double dz = (a.z() + ta.size() / 2.0) - (b.z() + tb.size() / 2.0);
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    /** Whether the pole at {@code p} of {@code type} supplies block (x, y, z). */
    public static boolean supplies(PolePos p, PoleType type, int x, int y, int z) {
        return suppliesAny(p, type, x, y, z, x, y, z);
    }

    /** Whether the pole supplies any block of the box (inclusive block coordinates). */
    public static boolean suppliesAny(PolePos p, PoleType type, int x0, int y0, int z0, int x1, int y1, int z1) {
        int m = type.supplyMargin();
        return p.x() - m <= x1 && p.x() + type.size() - 1 + m >= x0
                && p.z() - m <= z1 && p.z() + type.size() - 1 + m >= z0
                && p.y() - type.verticalReach() <= y1 && p.y() + type.verticalReach() >= y0;
    }

    public Set<PolePos> neighbors(PolePos p) {
        Set<PolePos> out = new HashSet<>();
        PoleType tp = poles.get(p);
        if (tp == null) return out;
        for (Map.Entry<PolePos, PoleType> q : poles.entrySet()) {
            if (!q.getKey().equals(p) && inReach(p, tp, q.getKey(), q.getValue())) out.add(q.getKey());
        }
        return out;
    }

    /** Equal for connected poles, different otherwise; may change after add/remove. -1 for unknown poles. */
    public int componentOf(PolePos p) {
        rebuild();
        return components.getOrDefault(p, -1);
    }

    /** Component of the smallest pole ({@link PolePos#compareTo}) supplying block (x, y, z); empty if none does. */
    public OptionalInt componentCovering(int x, int y, int z) {
        return componentCovering(x, y, z, x, y, z);
    }

    /**
     * Component of the smallest pole ({@link PolePos#compareTo}) supplying any block of the box
     * (inclusive block coordinates), so the choice does not depend on insertion order; empty if no pole does.
     */
    public OptionalInt componentCovering(int x0, int y0, int z0, int x1, int y1, int z1) {
        PolePos best = null;
        for (Map.Entry<PolePos, PoleType> e : poles.entrySet()) {
            PolePos p = e.getKey();
            if (suppliesAny(p, e.getValue(), x0, y0, z0, x1, y1, z1) && (best == null || p.compareTo(best) < 0)) best = p;
        }
        return best == null ? OptionalInt.empty() : OptionalInt.of(componentOf(best));
    }

    private void rebuild() {
        if (!dirty) return;
        components = new HashMap<>();
        int next = 0;
        for (PolePos start : poles.keySet()) {
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
