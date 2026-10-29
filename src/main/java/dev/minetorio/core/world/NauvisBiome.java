package dev.minetorio.core.world;

import static dev.minetorio.core.world.NauvisParams.*;

/**
 * The Minetorio biomes (Factorio 1.1 Nauvis): their climate ranges in the multi-noise biome source
 * of the Minetorio world preset ({@code data/minetorio/worldgen/world_preset/minetorio.json}, checked
 * against this table by a unit test). Temperature is Factorio's "aux", humidity its moisture,
 * continentalness the water value ({@link NauvisTerrain}).
 */
public enum NauvisBiome {
    GRASSLAND("nauvis_grassland", -1.5f, 1.5f, 0.3f, 1.5f, (float) SHORE, 1.5f),
    MEADOW("nauvis_meadow", -1.5f, 1.5f, 0.05f, 0.3f, (float) SHORE, 1.5f),
    STEPPE("nauvis_steppe", -1.5f, 1.5f, -0.15f, 0.05f, (float) SHORE, 1.5f),
    DRY_PLAINS("nauvis_dry_plains", -1.5f, 0.25f, -0.4f, -0.15f, (float) SHORE, 1.5f),
    DESERT("nauvis_desert", -1.5f, 0.25f, -1.5f, -0.4f, (float) SHORE, 1.5f),
    RED_DESERT("nauvis_red_desert", 0.25f, 1.5f, -1.5f, -0.15f, (float) SHORE, 1.5f),
    SHALLOW_WATER("nauvis_shallow_water", -1.5f, 1.5f, -1.5f, 1.5f, (float) DEEP, (float) SHORE),
    DEEP_WATER("nauvis_deep_water", -1.5f, 1.5f, -1.5f, 1.5f, -1.5f, (float) DEEP);

    private final String id;
    final float tMin, tMax, hMin, hMax, cMin, cMax;

    NauvisBiome(String id, float tMin, float tMax, float hMin, float hMax, float cMin, float cMax) {
        this.id = id;
        this.tMin = tMin;
        this.tMax = tMax;
        this.hMin = hMin;
        this.hMax = hMax;
        this.cMin = cMin;
        this.cMax = cMax;
    }

    /** Path of the biome id (namespace {@code minetorio}). */
    public String id() { return id; }

    public float[] temperature() { return new float[] {tMin, tMax}; }

    public float[] humidity() { return new float[] {hMin, hMax}; }

    public float[] continentalness() { return new float[] {cMin, cMax}; }

    private static double dist(double v, float lo, float hi) {
        return v < lo ? lo - v : v > hi ? v - hi : 0;
    }

    /** The biome the multi-noise source picks for these climate values (nearest range, like Minecraft). */
    public static NauvisBiome pick(double temperature, double humidity, double continentalness) {
        NauvisBiome best = GRASSLAND;
        double bestD = Double.MAX_VALUE;
        for (NauvisBiome b : values()) {
            double dt = dist(temperature, b.tMin, b.tMax);
            double dh = dist(humidity, b.hMin, b.hMax);
            double dc = dist(continentalness, b.cMin, b.cMax);
            double d = dt * dt + dh * dh + dc * dc;
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        return best;
    }

    /** The biome of column (x, z). */
    public static NauvisBiome at(NauvisTerrain t, double x, double z) {
        return pick(t.aux(x, z), t.moisture(x, z), t.water(x, z));
    }
}
