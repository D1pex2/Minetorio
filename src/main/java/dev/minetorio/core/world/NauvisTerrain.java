package dev.minetorio.core.world;

import static dev.minetorio.core.world.NauvisParams.*;

/**
 * The Minetorio ("Nauvis") terrain of one world seed as pure 2D functions of a column: its surface
 * height, water, climate, cliffs and rifts. World generation (density functions, surface rules,
 * features, the spawn) evaluates these, so everything agrees on where the ground is.
 *
 * <ul>
 *   <li><b>Land</b> is gentle: {@link NauvisParams#BASE_LAND} plus low hills (±1–2 blocks over tens of blocks).</li>
 *   <li><b>Lakes</b> are where the water noise is below {@link NauvisParams#SHORE}; the land sinks to
 *       the water surface towards them.</li>
 *   <li><b>Cliffs</b>, as in Factorio, are contour lines of a slow elevation noise: rounding it to
 *       whole levels splits the land into terraces, each step a 2–4 block wall (cliff rock). Where a
 *       gap noise is high the step becomes a ramp: passes. No cliffs near the origin (start area),
 *       near lakes, or in regions of low cliffiness.</li>
 *   <li><b>Rifts</b> are rare narrow chasms several blocks deep along the zero line of a ridge noise,
 *       only where a mask noise is high, never near the origin or lakes.</li>
 * </ul>
 */
public final class NauvisTerrain {
    private final long seed;
    private final Noise2D water;
    private final Noise2D hills;
    private final Noise2D elevation;
    private final Noise2D cliffiness;
    private final Noise2D gaps;
    private final Noise2D rift;
    private final Noise2D riftMask;
    private final Noise2D moisture;
    private final Noise2D aux;
    private final Noise2D forest;
    /** Cumulative height offset of each cliff level, index {@code level + LEVEL_OFFSET}. */
    private final double[] levelHeights;
    private static final int LEVEL_OFFSET = 8;

    public NauvisTerrain(long seed) {
        this.seed = seed;
        water = new Noise2D(seed ^ 0x57A7E5L);
        hills = new Noise2D(seed ^ 0x4111A5L);
        elevation = new Noise2D(seed ^ 0xE1E7A7L);
        cliffiness = new Noise2D(seed ^ 0xC11FF5L);
        gaps = new Noise2D(seed ^ 0x6A9500L);
        rift = new Noise2D(seed ^ 0x41F7L);
        riftMask = new Noise2D(seed ^ 0x41F7AA5CL);
        moisture = new Noise2D(seed ^ 0x3015E7L);
        aux = new Noise2D(seed ^ 0xA0C5L);
        forest = new Noise2D(seed ^ 0xF0E57L);
        levelHeights = new double[LEVEL_OFFSET * 2 + 1];
        java.util.SplittableRandom r = new java.util.SplittableRandom(seed ^ 0x57E95L);
        int[] steps = new int[levelHeights.length];
        for (int i = 0; i < steps.length; i++) steps[i] = CLIFF_MIN_STEP + r.nextInt(CLIFF_MAX_STEP - CLIFF_MIN_STEP + 1);
        // level 0 has height 0; level k > 0 adds steps of levels 1..k, level k < 0 subtracts those of k+1..0
        for (int k = 1; k <= LEVEL_OFFSET; k++) {
            levelHeights[LEVEL_OFFSET + k] = levelHeights[LEVEL_OFFSET + k - 1] + steps[LEVEL_OFFSET + k];
            levelHeights[LEVEL_OFFSET - k] = levelHeights[LEVEL_OFFSET - k + 1] - steps[LEVEL_OFFSET - k];
        }
    }

    private static final java.util.Map<Long, NauvisTerrain> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** The shared terrain of {@code seed} (a world has one; servers have only a few). */
    public static NauvisTerrain of(long seed) {
        if (CACHE.size() > 16) CACHE.clear();
        return CACHE.computeIfAbsent(seed, NauvisTerrain::new);
    }

    /**
     * A terrain seed from a few samples of a seeded noise. Density functions do not get the world
     * seed, only noises instantiated with it; sampling one such noise at fixed points gives a
     * per-world seed, the same wherever the same noise is sampled the same way.
     */
    public static long seedFromSamples(double... samples) {
        long h = 0x9E3779B97F4A7C15L;
        for (double s : samples) {
            h ^= Double.doubleToLongBits(s);
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 31;
        }
        return h;
    }

    public long seed() { return seed; }

    static double smoothstep(double lo, double hi, double v) {
        if (v <= lo) return 0;
        if (v >= hi) return 1;
        double t = (v - lo) / (hi - lo);
        return t * t * (3 - 2 * t);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // --- climate ------------------------------------------------------------------------------------

    /** Water value (the biomes' continentalness): below {@link NauvisParams#SHORE} is a lake. */
    public double water(double x, double z) {
        double natural = clamp(water.fbm(x, z, WATER_WAVELENGTH, WATER_OCTAVES) + WATER_BIAS, -1.5, 1.5);
        // Every seed starts on a dry island large enough for the four starter ore fields.
        double naturalAmount = startFade(x, z, SPAWN_SEARCH_RADIUS + dev.minetorio.core.orefield.PatchGenerator.STARTING_AREA_RADIUS, 80);
        double dryStart = lerp(Math.max(INLAND + 0.2, natural), natural, naturalAmount);
        // Steam progression cannot depend on a lucky seed: keep one small lake within walking distance.
        double lakeDistance = Math.hypot(x - STARTER_LAKE_X, z - STARTER_LAKE_Z);
        double outsideLake = smoothstep(STARTER_LAKE_INNER_RADIUS, STARTER_LAKE_OUTER_RADIUS, lakeDistance);
        if (outsideLake >= 1) return dryStart;
        // A bounded shoreline gradient keeps the bank walkable even on seeds with very high water noise.
        return Math.min(dryStart, lerp(DEEP - 0.12, INLAND + 0.2, outsideLake));
    }

    /** Moisture (the biomes' humidity): grass where wet, dirt and desert where dry. */
    public double moisture(double x, double z) {
        return clamp(moisture.fbm(x, z, MOISTURE_WAVELENGTH, CLIMATE_OCTAVES), -1.5, 1.5);
    }

    /** Factorio's "aux" (the biomes' temperature): red desert where high, sand where low. */
    public double aux(double x, double z) {
        return clamp(aux.fbm(x, z, AUX_WAVELENGTH, CLIMATE_OCTAVES), -1.5, 1.5);
    }

    /** Tree density 0..1 (forests are patches where this is high). */
    public double forest(double x, double z) {
        return smoothstep(-0.1, 0.6, forest.fbm(x, z, FOREST_WAVELENGTH, 2));
    }

    public boolean isWater(double x, double z) {
        return water(x, z) < SHORE;
    }

    // --- cliffs -------------------------------------------------------------------------------------

    /** 0 within {@code free} of the origin, 1 beyond {@code free + fade}, smooth in between. */
    static double startFade(double x, double z, double free, double fade) {
        return smoothstep(free, free + fade, Math.sqrt(x * x + z * z));
    }

    /** Elevation in level units: cliff walls stand where it crosses k + 0.5. */
    public double elevationLevel(double x, double z) {
        double e = elevation.fbm(x, z, ELEVATION_WAVELENGTH, ELEVATION_OCTAVES) * CLIFF_LEVELS;
        double amount = smoothstep(CLIFFINESS_LOW, CLIFFINESS_HIGH, cliffiness.fbm(x, z, CLIFFINESS_WAVELENGTH, 2))
                * startFade(x, z, CLIFF_FREE_RADIUS, CLIFF_FADE)
                * smoothstep(SHORE, INLAND + 0.15, water(x, z));
        return clamp(e * amount, -LEVEL_OFFSET + 0.5, LEVEL_OFFSET - 0.5);
    }

    /** The cliff level of a column: its terrace, a whole number. */
    public int level(double x, double z) {
        return (int) Math.round(elevationLevel(x, z));
    }

    /** Height offset of level {@code k} (0 for level 0; each step 2–4 blocks). */
    public double levelHeight(int k) {
        return levelHeights[Math.max(0, Math.min(levelHeights.length - 1, k + LEVEL_OFFSET))];
    }

    /** 0 on a cliff wall, 1 in a full pass (ramp). */
    public double gap(double x, double z) {
        return smoothstep(GAP_LOW, GAP_HIGH, gaps.fbm(x, z, GAP_WAVELENGTH, 2));
    }

    /** Height offset from cliff levels: stepped, or ramped through passes. */
    double terraceOffset(double x, double z) {
        double e = elevationLevel(x, z);
        int k = (int) Math.round(e);
        double stepped = levelHeight(k);
        double g = gap(x, z);
        if (g <= 0) return stepped;
        // ramp: across the nearest boundary (between levels lo and lo + 1), within ±RAMP_HALF_WIDTH of it
        int lo = (int) Math.floor(e);
        double boundary = lo + 0.5;
        double ramp = Math.abs(e - boundary) < RAMP_HALF_WIDTH
                ? lerp(levelHeight(lo), levelHeight(lo + 1), smoothstep(boundary - RAMP_HALF_WIDTH, boundary + RAMP_HALF_WIDTH, e))
                : stepped;
        return lerp(stepped, ramp, g);
    }

    // --- rifts --------------------------------------------------------------------------------------

    /** How much the ground sinks in a rift here (0 outside rifts). */
    public double riftDepth(double x, double z) {
        double fade = startFade(x, z, RIFT_FREE_RADIUS, RIFT_FADE);
        if (fade <= 0) return 0;
        double mask = smoothstep(RIFT_MASK_LOW, RIFT_MASK_HIGH, riftMask.fbm(x, z, RIFT_MASK_WAVELENGTH, 2));
        if (mask <= 0) return 0;
        if (Math.abs(rift.fbm(x, z, RIFT_WAVELENGTH, 2)) >= RIFT_WIDTH) return 0;
        double w = smoothstep(RIFT_MIN_WATER, RIFT_MIN_WATER + 0.15, water(x, z));
        return Math.round(RIFT_DEPTH * mask * fade * w);
    }

    // --- height -------------------------------------------------------------------------------------

    /** Raw surface height (continuous, before rounding to blocks). */
    public double height(double x, double z) {
        double w = water(x, z);
        if (w < SHORE) {
            double depth = Math.min(LAKE_MAX_DEPTH, LAKE_MIN_DEPTH + (SHORE - w) * 10 * LAKE_DEPTH_PER_TENTH);
            return SEA_LEVEL - 0.5 - depth;
        }
        double hill = hills.fbm(x, z, HILL_WAVELENGTH, 1) * HILL_AMPLITUDE + hills.fbm(x + 913, z - 377, BUMP_WAVELENGTH, 1) * BUMP_AMPLITUDE;
        // Keep the crash site and spawn platform flat; fade back into gentle hills outside it.
        hill *= startFade(x, z, STARTER_CLEAR_RADIUS, 24);
        hill *= smoothstep(STARTER_LAKE_OUTER_RADIUS, STARTER_LAKE_OUTER_RADIUS + 16,
                Math.hypot(x - STARTER_LAKE_X, z - STARTER_LAKE_Z));
        double land = BASE_LAND + hill + terraceOffset(x, z);
        double h = lerp(LAND_MIN_TOP + 0.5, Math.max(land, LAND_MIN_TOP + 0.5), smoothstep(SHORE, INLAND, w));
        double rd = riftDepth(x, z);
        if (rd > 0) h = Math.max(LAND_MIN_TOP + 0.5, h - rd);
        return h;
    }

    /** The y of the top solid block of column (x, z). */
    public int surfaceY(int x, int z) {
        return (int) Math.floor(height(x, z));
    }

    /**
     * Whether column (x, z) is a cliff wall: dry land standing at least {@link NauvisParams#CLIFF_MIN_DROP}
     * above one of its 8 dry neighbours (lake shores are not cliffs).
     */
    public boolean isCliffEdge(int x, int z) {
        if (isWater(x, z)) return false;
        return surfaceY(x, z) - lowestAround(x, z) >= CLIFF_MIN_DROP;
    }

    /** The surface y of the lowest of column (x, z)'s 8 dry neighbours (or its own, if lower). */
    public int lowestAround(int x, int z) {
        int low = surfaceY(x, z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && !isWater(x + dx, z + dz)) low = Math.min(low, surfaceY(x + dx, z + dz));
            }
        }
        return low;
    }
}
