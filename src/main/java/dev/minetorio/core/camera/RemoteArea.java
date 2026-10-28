package dev.minetorio.core.camera;

/**
 * Pure rules of the remote view (looking at a radar-watched place from the map, Factorio's remote
 * view): which chunks it may be over, the area around its focus where it streams chunks and the
 * player may act, and how the flying camera's focus moves without leaving that ground.
 *
 * <p>A chunk is <i>eligible</i> when a working radar watches it or it lies in the player's own
 * view (within his view distance of the chunk he stands in). The view's <i>area</i> is the
 * eligible chunks within {@link #RADIUS} chunks (Chebyshev) of the focus chunk.
 */
public final class RemoteArea {
    /** Chunks around the focus chunk (Chebyshev) the remote view streams and may act in (a 9×9 square). */
    public static final int RADIUS = 4;
    /**
     * View distance handed to vanilla chunk tracking for the remote focus: its circle with the
     * outer ring (Minecraft's {@code ChunkTrackingView}) reaches {@link #RADIUS} chunks on the axes.
     */
    public static final int TRACKING_VIEW_DISTANCE = RADIUS - 1;
    /**
     * Distance of the region ticket at the focus chunk: levels {@code 33 - d + chebyshev}, so every
     * chunk within {@link #RADIUS} is at block-ticking level 32 or below (a chunk the server will
     * send) while the ticket itself is not a simulation ticket (nothing there ticks because of it).
     */
    public static final int TICKET_DISTANCE = RADIUS + 1;
    /** Server budget for focus updates (enter and move): a burst, refilled per tick. */
    public static final int MOVE_BURST = 10;
    public static final double MOVE_REFILL_PER_TICK = 0.5;
    /** The client sends its focus at most this often (ticks), and only when its chunk changed. */
    public static final int CLIENT_MOVE_INTERVAL = 4;
    /** Camera pan speed in blocks per second per block of camera height. */
    public static final double PAN_SPEED_PER_HEIGHT = 1.1;

    /** Whether a chunk is eligible (watched by a radar or in the player's own view). */
    @FunctionalInterface
    public interface ChunkTest {
        boolean test(int chunkX, int chunkZ);
    }

    private RemoteArea() {}

    /** The chunk coordinate of block coordinate {@code block}. */
    public static int chunkOf(double block) {
        return Math.floorDiv((int) Math.floor(block), 16);
    }

    public static int chebyshev(int ax, int az, int bx, int bz) {
        return Math.max(Math.abs(ax - bx), Math.abs(az - bz));
    }

    /** Whether chunk {@code (cx, cz)} lies in the own view of a player in chunk {@code (px, pz)} with {@code viewDistance}. */
    public static boolean ownView(int px, int pz, int viewDistance, int cx, int cz) {
        return chebyshev(px, pz, cx, cz) <= viewDistance;
    }

    /** Whether chunk {@code (cx, cz)} is in the area of a remote view focused on chunk {@code (fx, fz)}. */
    public static boolean inArea(int fx, int fz, int cx, int cz, ChunkTest eligible) {
        return chebyshev(fx, fz, cx, cz) <= RADIUS && eligible.test(cx, cz);
    }

    /**
     * The focus after a camera move by {@code (dx, dz)} from {@code (x, z)}: the move as is when it
     * ends over an eligible chunk; else only its X or only its Z part when that does (sliding along
     * the edge of the watched ground); else no move. Returns {x, z}.
     */
    public static double[] step(double x, double z, double dx, double dz, ChunkTest eligible) {
        if (dx == 0 && dz == 0) return new double[] {x, z};
        if (eligible.test(chunkOf(x + dx), chunkOf(z + dz))) return new double[] {x + dx, z + dz};
        if (dx != 0 && eligible.test(chunkOf(x + dx), chunkOf(z))) return new double[] {x + dx, z};
        if (dz != 0 && eligible.test(chunkOf(x), chunkOf(z + dz))) return new double[] {x, z + dz};
        return new double[] {x, z};
    }

    /** Pan speed (blocks per second) of a camera {@code height} blocks above its focus. */
    public static double panSpeed(double height) {
        return PAN_SPEED_PER_HEIGHT * height;
    }
}
