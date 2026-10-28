package dev.minetorio.core.orefield;

import java.util.ArrayList;
import java.util.List;

/**
 * The cells of a patch's disk as horizontal runs (for a tint) and its outline as unit edges between
 * a disk cell and a cell outside (for a border), in block coordinates. Pure geometry, computed once
 * per patch and cached by the map.
 */
public final class PatchShape {
    /** Cells x0..x1 (inclusive) of row z. */
    public record Run(int z, int x0, int x1) {}

    /** Sides of a cell an outline edge lies on. */
    public static final int NORTH = 0, SOUTH = 1, WEST = 2, EAST = 3;

    /** A unit edge of the outline on side {@code side} of the disk cell (x, z). */
    public record Edge(int x, int z, int side) {}

    private final List<Run> runs;
    private final List<Edge> edges;

    private PatchShape(List<Run> runs, List<Edge> edges) {
        this.runs = List.copyOf(runs);
        this.edges = List.copyOf(edges);
    }

    public List<Run> runs() { return runs; }

    public List<Edge> edges() { return edges; }

    /** Number of disk cells. */
    public int cells() {
        int n = 0;
        for (Run r : runs) n += r.x1() - r.x0() + 1;
        return n;
    }

    public static PatchShape of(OrePatch p) {
        List<Run> runs = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        int r = p.radius();
        for (int z = p.centerZ() - r; z <= p.centerZ() + r; z++) {
            int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE;
            for (int x = p.centerX() - r; x <= p.centerX() + r; x++) {
                if (!p.contains(x, z)) continue;
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                if (!p.contains(x, z - 1)) edges.add(new Edge(x, z, NORTH));
                if (!p.contains(x, z + 1)) edges.add(new Edge(x, z, SOUTH));
                if (!p.contains(x - 1, z)) edges.add(new Edge(x, z, WEST));
                if (!p.contains(x + 1, z)) edges.add(new Edge(x, z, EAST));
            }
            if (x0 <= x1) runs.add(new Run(z, x0, x1));
        }
        return new PatchShape(runs, edges);
    }
}
