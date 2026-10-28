package dev.minetorio.core.camera;

/**
 * Pure rules of the top-down view's layer cut (multi-floor factories): with a cut at block height
 * {@code cut}, every block with {@code y > cut} is hidden (not drawn, not hit by the cursor ray),
 * so the floor at {@code cut} and everything under it can be seen and built on. {@link #NONE} means
 * no cut. World vectors are {@code double[3]} as {x, y, z}.
 */
public final class LayerCut {
    /** No cut: everything is shown. */
    public static final int NONE = Integer.MAX_VALUE;
    /** The cursor ray starts this far under the cut plane so the hidden block above is never its first cell. */
    public static final double RAY_EPSILON = 1e-4;
    /**
     * Entities count as above the cut when their feet are this far over the cut block's bottom: one
     * standing on the cut floor (feet at {@code cut + 1}) stays visible, one standing a slab or more
     * higher is hidden.
     */
    public static final double ENTITY_MARGIN = 1.5;

    private LayerCut() {}

    public static boolean isSet(int cut) {
        return cut != NONE;
    }

    /** Whether a block at height {@code y} is hidden by {@code cut}. */
    public static boolean blockHidden(int y, int cut) {
        return cut != NONE && y > cut;
    }

    /** Whether an entity with its feet at {@code feetY} is hidden by {@code cut}. */
    public static boolean entityHidden(double feetY, int cut) {
        return cut != NONE && feetY >= cut + ENTITY_MARGIN;
    }

    /** The height of the cut plane: the top of the highest shown block. */
    public static double planeY(int cut) {
        return cut + 1.0;
    }

    /**
     * The part of the ray segment {@code from -> to} that lies under the cut plane (a hair under it,
     * {@link #RAY_EPSILON}), as {fromX, fromY, fromZ, toX, toY, toZ}; the segment itself when there
     * is no cut; null when all of it is above the cut (the ray only sees hidden blocks).
     */
    public static double[] clip(double[] from, double[] to, int cut) {
        if (cut == NONE) return new double[] {from[0], from[1], from[2], to[0], to[1], to[2]};
        double limit = planeY(cut) - RAY_EPSILON;
        boolean fromBelow = from[1] <= limit, toBelow = to[1] <= limit;
        if (fromBelow && toBelow) return new double[] {from[0], from[1], from[2], to[0], to[1], to[2]};
        if (!fromBelow && !toBelow) return null;
        double t = (limit - from[1]) / (to[1] - from[1]);
        double[] cross = {from[0] + (to[0] - from[0]) * t, limit, from[2] + (to[2] - from[2]) * t};
        return fromBelow
                ? new double[] {from[0], from[1], from[2], cross[0], cross[1], cross[2]}
                : new double[] {cross[0], cross[1], cross[2], to[0], to[1], to[2]};
    }

    /**
     * The cut after "layer down": the first press cuts just above the block the pivot stands on
     * (at {@code pivotBlockY + 1}, waist height, like a floor plan), later presses go one block
     * lower, never under {@code minY}.
     */
    public static int lower(int cut, int pivotBlockY, int minY, int maxY) {
        int next = cut == NONE ? pivotBlockY + 1 : cut - 1;
        return Math.max(minY, Math.min(maxY, next));
    }

    /** The cut after "layer up": one block higher; reaching the world's top ({@code maxY}) clears it. Nothing without a cut. */
    public static int raise(int cut, int maxY) {
        if (cut == NONE) return NONE;
        int next = cut + 1;
        return next >= maxY ? NONE : next;
    }

    /**
     * Section-Y range {min, max} (inclusive) whose meshes change when the cut goes from
     * {@code oldCut} to {@code newCut}: the blocks that appear or disappear, plus the highest block
     * still shown below both (its top face and lighting depend on the block above). {@code null}
     * when nothing changes. Block heights run from {@code minY} to {@code maxY} (exclusive).
     */
    public static int[] dirtySections(int oldCut, int newCut, int minY, int maxY) {
        if (oldCut == newCut) return null;
        long lo = Math.min(oldCut, newCut);
        long hi = Math.max(oldCut, newCut) == NONE ? maxY - 1L : Math.max(oldCut, newCut) + 1L;
        lo = Math.max(lo, minY);
        hi = Math.min(hi, maxY - 1L);
        if (lo > hi) return null;
        return new int[] {Math.floorDiv((int) lo, 16), Math.floorDiv((int) hi, 16)};
    }
}
