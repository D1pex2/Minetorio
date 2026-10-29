package dev.minetorio.core.blueprint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Repeated blueprint anchors on a footprint-sized grid. */
public final class BlueprintDrag {
    public record Cell(int x, int z) {}
    private final int width, depth;
    private int x, z;
    private final Set<Cell> visited = new HashSet<>();

    public BlueprintDrag(int x, int z, int width, int depth) {
        if (width < 1 || depth < 1) throw new IllegalArgumentException("empty footprint");
        this.x = x;
        this.z = z;
        this.width = width;
        this.depth = depth;
        visited.add(new Cell(x, z));
    }

    /** Advances along footprint-sized tiles, with no gaps when the cursor skips cells. */
    public List<Cell> extend(int targetX, int targetZ, int limit) {
        List<Cell> out = new ArrayList<>();
        while (out.size() < limit) {
            long dx = (long) targetX - x, dz = (long) targetZ - z;
            boolean alongX = Math.abs(dx) >= width, alongZ = Math.abs(dz) >= depth;
            if (!alongX && !alongZ) break;
            if (alongX && (!alongZ || Math.abs(dx) * depth >= Math.abs(dz) * width)) x += Long.signum(dx) * width;
            else z += Long.signum(dz) * depth;
            Cell cell = new Cell(x, z);
            if (visited.add(cell)) out.add(cell);
        }
        return out;
    }
}
