package dev.minetorio.core.blueprint;

import dev.minetorio.core.drag.DragDir;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * One building in a blueprint, in blueprint space (x east, z south, y up; all cells non-negative
 * once normalized): {@code block} id, its origin cell (a multiblock's controller, the footprint's
 * left-front-bottom corner; else the block's own cell), the {@code facing} it is placed with (the
 * placer's look, null for blocks without one), its footprint {@code width} (across the facing),
 * {@code depth} (along it) and {@code height}, and a {@code variant} (an underground belt's
 * entrance 0 / exit 1; 0 otherwise). The footprint has the same orientation rule as the
 * multiblocks': width runs along {@code facing.clockWise()}, depth along {@code facing}.
 */
public record BlueprintPart(String block, int x, int y, int z, @Nullable DragDir facing, int width, int depth, int height, int variant) {
    public BlueprintPart {
        if (width < 1 || depth < 1 || height < 1) throw new IllegalArgumentException("size " + width + "x" + depth + "x" + height);
    }

    /** A one-cell part (belt, inserter, pipe). */
    public static BlueprintPart single(String block, int x, int y, int z, @Nullable DragDir facing, int variant) {
        return new BlueprintPart(block, x, y, z, facing, 1, 1, 1, variant);
    }

    /** The facing the footprint is laid out with (NORTH for parts without one). */
    public DragDir layoutFacing() {
        return facing == null ? DragDir.NORTH : facing;
    }

    /** Smallest x of the footprint. */
    public int minX() { return Math.min(x, x + spanX()); }

    public int maxX() { return Math.max(x, x + spanX()); }

    public int minZ() { return Math.min(z, z + spanZ()); }

    public int maxZ() { return Math.max(z, z + spanZ()); }

    /** Signed offset from the origin to the far footprint corner along x. */
    private int spanX() {
        DragDir f = layoutFacing(), r = f.clockWise();
        return r.dx() * (width - 1) + f.dx() * (depth - 1);
    }

    private int spanZ() {
        DragDir f = layoutFacing(), r = f.clockWise();
        return r.dz() * (width - 1) + f.dz() * (depth - 1);
    }

    /** Every cell of the footprint, bottom layer first. */
    public List<int[]> cells() {
        List<int[]> out = new ArrayList<>(width * depth * height);
        for (int dy = 0; dy < height; dy++) {
            for (int cx = minX(); cx <= maxX(); cx++) {
                for (int cz = minZ(); cz <= maxZ(); cz++) out.add(new int[] {cx, y + dy, cz});
            }
        }
        return out;
    }

    /** The bottom layer's cells. */
    public List<int[]> bottom() {
        List<int[]> out = new ArrayList<>(width * depth);
        for (int cx = minX(); cx <= maxX(); cx++) {
            for (int cz = minZ(); cz <= maxZ(); cz++) out.add(new int[] {cx, y, cz});
        }
        return out;
    }

    public BlueprintPart moved(int dx, int dy, int dz) {
        return new BlueprintPart(block, x + dx, y + dy, z + dz, facing, width, depth, height, variant);
    }
}
