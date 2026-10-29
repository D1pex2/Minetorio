package dev.minetorio.core.world;

import static dev.minetorio.core.world.NauvisParams.*;

import dev.minetorio.core.orefield.OrePatch;
import dev.minetorio.core.orefield.PatchGenerator;

/**
 * Picks the world spawn on a Nauvis terrain: a dry, flat spot (no cliff, no rift, height within
 * one block over a square of side {@code 2 × SPAWN_FLAT_HALF + 1}) within
 * {@link NauvisParams#SPAWN_SEARCH_RADIUS} of the origin, preferring spots whose four starting ore
 * fields ({@link PatchGenerator#startingPatches}) land on dry ground, then the closest to the origin.
 */
public final class SpawnFinder {
    private static final int STEP = 4;

    private SpawnFinder() {}

    /** Whether the square around (x, z) is dry and flat. */
    public static boolean isFlatDry(NauvisTerrain t, int x, int z) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int dx = -SPAWN_FLAT_HALF; dx <= SPAWN_FLAT_HALF; dx += 2) {
            for (int dz = -SPAWN_FLAT_HALF; dz <= SPAWN_FLAT_HALF; dz += 2) {
                if (t.isWater(x + dx, z + dz) || t.riftDepth(x + dx, z + dz) > 0) return false;
                int y = t.surfaceY(x + dx, z + dz);
                if (y < LAND_MIN_TOP) return false;
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }
        return max - min <= 1 && !t.isCliffEdge(x, z);
    }

    /** Water samples (out of 9 per field) under the four starting fields of a spawn at (x, z). */
    public static int wetFieldSamples(NauvisTerrain t, int x, int z) {
        int wet = 0;
        for (OrePatch p : PatchGenerator.startingPatches(x, z)) {
            int r = p.radius() * 3 / 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (t.isWater(p.centerX() + dx * r, p.centerZ() + dz * r)) wet++;
                }
            }
        }
        return wet;
    }

    /** The spawn column {x, z}; the origin if no spot qualifies. */
    public static int[] find(NauvisTerrain t) {
        int[] best = {0, 0};
        double bestScore = Double.MAX_VALUE;
        int r = SPAWN_SEARCH_RADIUS;
        for (int x = -r; x <= r; x += STEP) {
            for (int z = -r; z <= r; z += STEP) {
                double d = Math.sqrt((double) x * x + (double) z * z);
                if (d > r) continue;
                double score = d / 16.0 + wetFieldSamples(t, x, z) * 4.0;
                if (score >= bestScore || !isFlatDry(t, x, z)) continue;
                bestScore = score;
                best = new int[] {x, z};
            }
        }
        return best;
    }
}
