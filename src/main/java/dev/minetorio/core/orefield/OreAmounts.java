package dev.minetorio.core.orefield;

/**
 * Ore per field block, as in Factorio: {@code base × (1 + distance from spawn / 500) × radial},
 * where the radial factor falls linearly from ×2 at the patch center to ×0.5 at its edge.
 */
public final class OreAmounts {
    public static final double DISTANCE_SCALE = 500;
    public static final double CENTER_FACTOR = 2.0;
    public static final double EDGE_FACTOR = 0.5;

    private OreAmounts() {}

    /**
     * Ore in one block {@code distFromSpawn} blocks from spawn and {@code distFromCenter} from the
     * center of a patch of {@code radius} (beyond the edge counts as the edge); at least 1.
     */
    public static long amount(OreType type, double distFromSpawn, double distFromCenter, int radius) {
        double t = radius <= 0 ? 1 : Math.min(1, Math.max(0, distFromCenter / radius));
        double radial = CENTER_FACTOR - (CENTER_FACTOR - EDGE_FACTOR) * t;
        double richness = 1 + Math.max(0, distFromSpawn) / DISTANCE_SCALE;
        return Math.max(1, Math.round(type.baseAmount() * richness * radial));
    }

    /** {@link #amount} of the block in column (x, z) of {@code patch}, spawn at (spawnX, spawnZ). */
    public static long amountAt(OrePatch patch, int x, int z, int spawnX, int spawnZ) {
        double fromSpawn = Math.hypot((double) x - spawnX, (double) z - spawnZ);
        return amount(patch.type(), fromSpawn, Math.sqrt(patch.distSq(x, z)), patch.radius());
    }

    /** A block is shown depleted (paler texture) when less than 10 % of its initial amount is left. */
    public static boolean isDepleted(long remaining, long initial) {
        return remaining * 10 < initial;
    }
}
