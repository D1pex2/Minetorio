package dev.minetorio.core.drag;

import java.util.ArrayList;
import java.util.List;

/**
 * Pole drag spacing (Factorio): walking along a drag path from the last pole, a pole goes on a
 * cell when the cell after it would be out of wire reach of the last pole, so consecutive poles
 * are as far apart as the wire allows and always connect. Distances are Euclidean in 3D,
 * inclusive, like the pole graph.
 */
public final class PoleSpacer {
    private final double reach;
    private int lx, ly, lz;

    /** A spacer whose last pole stands at ({@code x}, {@code y}, {@code z}). */
    public PoleSpacer(double reach, int x, int y, int z) {
        this.reach = reach;
        placed(x, y, z);
    }

    /** Cells between poles on a straight flat line. */
    public static int straightSpacing(double reach) {
        return (int) Math.floor(reach);
    }

    /**
     * Whether the cell just before ({@code nx}, {@code ny}, {@code nz}) on the path needs a pole:
     * that next cell is beyond wire reach of the last pole.
     */
    public boolean poleBefore(int nx, int ny, int nz) {
        double dx = nx - lx, dy = ny - ly, dz = nz - lz;
        return dx * dx + dy * dy + dz * dz > reach * reach;
    }

    /** A pole was placed at ({@code x}, {@code y}, {@code z}): measure from it from now on. */
    public void placed(int x, int y, int z) {
        lx = x;
        ly = y;
        lz = z;
    }

    /**
     * Indices of the cells of {@code path} ({x, y, z} each; index 0 is the existing first pole)
     * that get a pole, assuming every placement succeeds. The last cell (the head) never does:
     * its pole is decided only once the path goes on.
     */
    public static List<Integer> picks(List<int[]> path, double reach) {
        List<Integer> out = new ArrayList<>();
        if (path.isEmpty()) return out;
        int[] s = path.getFirst();
        PoleSpacer spacer = new PoleSpacer(reach, s[0], s[1], s[2]);
        for (int i = 1; i + 1 < path.size(); i++) {
            int[] n = path.get(i + 1);
            if (spacer.poleBefore(n[0], n[1], n[2])) {
                int[] c = path.get(i);
                out.add(i);
                spacer.placed(c[0], c[1], c[2]);
            }
        }
        return out;
    }
}
