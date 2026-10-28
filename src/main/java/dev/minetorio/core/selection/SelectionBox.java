package dev.minetorio.core.selection;

/**
 * An area selected with a planner or a blueprint: an axis-aligned box of whole cells, both corners
 * inclusive. Shared by every selection tool (first-person corner clicks with face nudging, top-down
 * rectangle drags); pure, so the server can check what a client sends with the same rules.
 *
 * <p>Faces are numbered like Minecraft's {@code Direction} ordinals: 0 down, 1 up, 2 north (-z),
 * 3 south (+z), 4 west (-x), 5 east (+x).
 */
public record SelectionBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    /** Longest side a selection may have, in cells. */
    public static final int MAX_SIDE = 128;
    /** Most cells a selection may hold (128 × 128 × 32). */
    public static final long MAX_VOLUME = 128L * 128L * 32L;
    /** Top-down selections: cells below the lower and above the higher corner a drag covers. */
    public static final int COLUMN_BELOW = 2, COLUMN_ABOVE = 24;

    private static final int[][] NORMALS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    public SelectionBox {
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("inverted box");
    }

    /** The box spanned by two corner cells (any order). */
    public static SelectionBox of(int ax, int ay, int az, int bx, int by, int bz) {
        return new SelectionBox(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz),
                Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz));
    }

    /**
     * A top-down drag from cell {@code a} to cell {@code b}: the rectangle between them, from
     * {@value #COLUMN_BELOW} below the lower corner to {@value #COLUMN_ABOVE} above the higher one
     * (trees and machines stand on the ground the cursor hits).
     */
    public static SelectionBox column(int ax, int ay, int az, int bx, int by, int bz) {
        return new SelectionBox(Math.min(ax, bx), Math.min(ay, by) - COLUMN_BELOW, Math.min(az, bz),
                Math.max(ax, bx), Math.max(ay, by) + COLUMN_ABOVE, Math.max(az, bz));
    }

    public int sizeX() { return maxX - minX + 1; }
    public int sizeY() { return maxY - minY + 1; }
    public int sizeZ() { return maxZ - minZ + 1; }

    public long volume() { return (long) sizeX() * sizeY() * sizeZ(); }

    /** Whether the box is small enough to be applied ({@link #MAX_SIDE}, {@link #MAX_VOLUME}). */
    public boolean withinLimits() {
        return sizeX() <= MAX_SIDE && sizeY() <= MAX_SIDE && sizeZ() <= MAX_SIDE && volume() <= MAX_VOLUME;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public double centerX() { return (minX + maxX + 1) / 2.0; }
    public double centerY() { return (minY + maxY + 1) / 2.0; }
    public double centerZ() { return (minZ + maxZ + 1) / 2.0; }

    /** Outward normal of face {@code face} (0..5). */
    public static int[] normal(int face) { return NORMALS[face].clone(); }

    /**
     * The box with face {@code face} moved outward by {@code by} cells (negative: inward). A face
     * never passes its opposite face: the box keeps at least one cell on that axis.
     */
    public SelectionBox push(int face, int by) {
        int x0 = minX, y0 = minY, z0 = minZ, x1 = maxX, y1 = maxY, z1 = maxZ;
        switch (face) {
            case 0 -> y0 = Math.min(y1, y0 - by);
            case 1 -> y1 = Math.max(y0, y1 + by);
            case 2 -> z0 = Math.min(z1, z0 - by);
            case 3 -> z1 = Math.max(z0, z1 + by);
            case 4 -> x0 = Math.min(x1, x0 - by);
            case 5 -> x1 = Math.max(x0, x1 + by);
            default -> throw new IllegalArgumentException("face " + face);
        }
        return new SelectionBox(x0, y0, z0, x1, y1, z1);
    }

    /**
     * The face a look ray from {@code (ex, ey, ez)} along {@code (dx, dy, dz)} hits: the face it
     * enters through, or (eye inside the box) the face it leaves through; -1 when it misses.
     */
    public int faceHit(double ex, double ey, double ez, double dx, double dy, double dz) {
        double[] lo = {minX, minY, minZ}, hi = {maxX + 1, maxY + 1, maxZ + 1};
        double[] o = {ex, ey, ez}, d = {dx, dy, dz};
        double tNear = Double.NEGATIVE_INFINITY, tFar = Double.POSITIVE_INFINITY;
        int nearFace = -1, farFace = -1;
        for (int axis = 0; axis < 3; axis++) {
            int lowFace = axis == 0 ? 4 : axis == 1 ? 0 : 2;
            if (Math.abs(d[axis]) < 1e-12) {
                if (o[axis] < lo[axis] || o[axis] > hi[axis]) return -1;
                continue;
            }
            double t1 = (lo[axis] - o[axis]) / d[axis], t2 = (hi[axis] - o[axis]) / d[axis];
            int f1 = lowFace, f2 = lowFace + 1;
            if (t1 > t2) {
                double t = t1; t1 = t2; t2 = t;
                int f = f1; f1 = f2; f2 = f;
            }
            if (t1 > tNear) { tNear = t1; nearFace = f1; }
            if (t2 < tFar) { tFar = t2; farFace = f2; }
        }
        if (tNear > tFar || tFar < 0) return -1;
        return tNear >= 0 ? nearFace : farFace;
    }
}
