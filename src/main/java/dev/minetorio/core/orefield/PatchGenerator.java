package dev.minetorio.core.orefield;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import javax.annotation.Nullable;

/**
 * Deterministic ore patch layout: each 64×64 region holds at most one patch, derived only from
 * (worldSeed, rx, rz), and lies wholly inside its region, so patches never overlap and any chunk
 * can be stamped on its own, in any order. No regular patch comes within {@value #SPAWN_EXCLUSION}
 * blocks of the exclusion center (the starting patches go there instead).
 */
public final class PatchGenerator {
    public static final int REGION = 64;
    public static final double CHANCE = 0.35;
    public static final int MIN_RADIUS = 6;
    public static final int MAX_RADIUS = 14;
    public static final int SPAWN_EXCLUSION = 80;
    public static final int STARTING_DISTANCE = 48;
    public static final int STARTING_RADIUS = 10;

    private final long worldSeed;
    private final int centerX;
    private final int centerZ;

    /** Patches of world {@code worldSeed}, none within {@link #SPAWN_EXCLUSION} of (centerX, centerZ). */
    public PatchGenerator(long worldSeed, int centerX, int centerZ) {
        this.worldSeed = worldSeed;
        this.centerX = centerX;
        this.centerZ = centerZ;
    }

    /** The four starting patches, {@value #STARTING_DISTANCE} blocks from spawn: iron east, copper west, coal south, stone north. */
    public static List<OrePatch> startingPatches(int spawnX, int spawnZ) {
        return List.of(
                new OrePatch(OreType.IRON, spawnX + STARTING_DISTANCE, spawnZ, STARTING_RADIUS),
                new OrePatch(OreType.COPPER, spawnX - STARTING_DISTANCE, spawnZ, STARTING_RADIUS),
                new OrePatch(OreType.COAL, spawnX, spawnZ + STARTING_DISTANCE, STARTING_RADIUS),
                new OrePatch(OreType.STONE, spawnX, spawnZ - STARTING_DISTANCE, STARTING_RADIUS));
    }

    /**
     * Radius of the starting area around spawn: the starting patches plus a margin. Generated
     * patches are laid out around the origin (the spawn is not known while the first chunks
     * generate), so those reaching into this circle are removed when the starting fields are
     * stamped ({@link #patchesInStartingArea}).
     */
    public static final int STARTING_AREA_MARGIN = 8;
    public static final int STARTING_AREA_RADIUS = STARTING_DISTANCE + STARTING_RADIUS + STARTING_AREA_MARGIN;

    /** Whether {@code p} may have a cell within {@link #STARTING_AREA_RADIUS} of spawn. */
    public static boolean overlapsStartingArea(OrePatch p, int spawnX, int spawnZ) {
        return Math.hypot((double) p.centerX() - spawnX, (double) p.centerZ() - spawnZ) <= STARTING_AREA_RADIUS + p.radius();
    }

    /** The generated patches reaching into the starting area around (spawnX, spawnZ). */
    public List<OrePatch> patchesInStartingArea(int spawnX, int spawnZ) {
        int r = STARTING_AREA_RADIUS;
        List<OrePatch> out = new ArrayList<>();
        for (OrePatch p : patchesTouching(spawnX - r, spawnZ - r, spawnX + r, spawnZ + r)) {
            if (overlapsStartingArea(p, spawnX, spawnZ)) out.add(p);
        }
        return out;
    }

    /** Every patch overlapping the box [minX..maxX] × [minZ..maxZ] (inclusive), by region (x, then z). */
    public List<OrePatch> patchesTouching(int minX, int minZ, int maxX, int maxZ) {
        List<OrePatch> out = new ArrayList<>(1);
        for (int rx = Math.floorDiv(minX, REGION); rx <= Math.floorDiv(maxX, REGION); rx++) {
            for (int rz = Math.floorDiv(minZ, REGION); rz <= Math.floorDiv(maxZ, REGION); rz++) {
                OrePatch p = inRegion(rx, rz);
                if (p != null && p.touches(minX, minZ, maxX, maxZ)) out.add(p);
            }
        }
        return out;
    }

    /** The patch of region (rx, rz), or null if it has none. */
    @Nullable
    public OrePatch inRegion(int rx, int rz) {
        SplittableRandom rnd = new SplittableRandom(mix(worldSeed, rx, rz));
        if (rnd.nextDouble() >= CHANCE) return null;
        int span = REGION - 2 * MAX_RADIUS;
        int cx = rx * REGION + MAX_RADIUS + rnd.nextInt(span);
        int cz = rz * REGION + MAX_RADIUS + rnd.nextInt(span);
        OreType type = pickType(rnd.nextInt(totalWeight()));
        int radius = MIN_RADIUS + rnd.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        double dist = Math.hypot((double) cx - centerX, (double) cz - centerZ);
        if (dist - radius < SPAWN_EXCLUSION) return null;
        return new OrePatch(type, cx, cz, radius);
    }

    private static int totalWeight() {
        int sum = 0;
        for (OreType t : OreType.values()) sum += t.weight();
        return sum;
    }

    private static OreType pickType(int roll) {
        for (OreType t : OreType.values()) {
            if (roll < t.weight()) return t;
            roll -= t.weight();
        }
        throw new IllegalStateException();
    }

    private static long mix(long seed, int rx, int rz) {
        long h = splitMix(seed ^ (rx * 0x9E3779B97F4A7C15L));
        return splitMix(h ^ (rz * 0xC2B2AE3D27D4EB4FL));
    }

    private static long splitMix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
