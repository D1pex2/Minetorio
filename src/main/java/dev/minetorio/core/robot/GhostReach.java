package dev.minetorio.core.robot;

/**
 * How far a player may place and remove ghosts and paste blueprints (Factorio: anywhere on screen;
 * real blocks keep the normal reach): within {@link #MAX_HORIZONTAL} blocks horizontally of the
 * player and within the server's view distance. The remote view has its own area rule instead.
 */
public final class GhostReach {
    /** Farthest a ghost may be placed or removed from the player, horizontally, in blocks. */
    public static final int MAX_HORIZONTAL = 64;

    private GhostReach() {}

    /** The horizontal limit with a server view distance of {@code viewChunks} (0 or less: unknown, no extra limit). */
    public static double limit(int viewChunks) {
        return viewChunks <= 0 ? MAX_HORIZONTAL : Math.min(MAX_HORIZONTAL, viewChunks * 16.0);
    }

    /** Whether a cell centred {@code (dx, dz)} from the player is within ghost reach. */
    public static boolean inReach(double dx, double dz, int viewChunks) {
        double l = limit(viewChunks);
        return dx * dx + dz * dz <= l * l;
    }
}
