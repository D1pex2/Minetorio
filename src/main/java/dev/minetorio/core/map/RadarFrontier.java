package dev.minetorio.core.map;

import java.util.function.LongPredicate;

/**
 * A radar's exploration front: the chunks of the square of radius {@code radius} around the radar,
 * in spiral order — ring after ring outward (ring k = Chebyshev distance k; ring 0 is the radar's
 * own chunk), each ring walked clockwise starting at its north-west corner. The state is a single
 * index into that order ({@link #index}), saved with the radar. Chunks already explored (by any
 * radar or player) are skipped without cost.
 */
public final class RadarFrontier {
    /** {@link #next} result: the frontier has no chunk left. */
    public static final long NONE = Long.MIN_VALUE;
    /** {@link #next} result: the skip budget ran out on explored chunks; call again later. */
    public static final long BUSY = Long.MIN_VALUE + 1;

    private final int radius;
    private int index;

    public RadarFrontier(int radius) {
        this.radius = radius;
    }

    /** Cells of the square of the given radius. */
    public static int cellCount(int radius) { return (2 * radius + 1) * (2 * radius + 1); }

    /** Offset {dx, dz} of the spiral's {@code i}-th cell. */
    public static int[] offset(int i) {
        if (i == 0) return new int[] {0, 0};
        int k = (int) Math.ceil((Math.sqrt(i + 1.0) - 1) / 2);
        while ((2 * k + 1) * (2 * k + 1) <= i) k++;
        while (k > 1 && (2 * k - 1) * (2 * k - 1) > i) k--;
        int p = i - (2 * k - 1) * (2 * k - 1);
        int side = p / (2 * k), t = p % (2 * k);
        return switch (side) {
            case 0 -> new int[] {-k + t, -k};
            case 1 -> new int[] {k, -k + t};
            case 2 -> new int[] {k - t, k};
            default -> new int[] {-k, k - t};
        };
    }

    public int index() { return index; }

    public void setIndex(int i) { index = Math.max(0, Math.min(i, cellCount(radius))); }

    public boolean isExhausted() { return index >= cellCount(radius); }

    /**
     * The next unexplored chunk around (cx, cz), which the front then moves past; skips at most
     * {@code maxSkips} explored chunks per call ({@link #BUSY} if that was not enough, {@link #NONE}
     * once every chunk of the square was handed out or skipped).
     */
    public long next(LongPredicate explored, int cx, int cz, int maxSkips) {
        int n = cellCount(radius);
        int skipped = 0;
        while (index < n) {
            int[] o = offset(index);
            long key = ChunkKeys.of(cx + o[0], cz + o[1]);
            index++;
            if (!explored.test(key)) return key;
            if (++skipped >= maxSkips) return index < n ? BUSY : NONE;
        }
        return NONE;
    }
}
