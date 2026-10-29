package dev.minetorio.core.world;

import dev.minetorio.core.orefield.PatchGenerator;

/**
 * Every number of the Minetorio ("Nauvis") terrain in one table. Lengths are in blocks, noise
 * values in the −1..1 range of {@link Noise2D#fbm}. The vertical frame (y −16..128, sea level 63)
 * must match {@code data/minetorio/dimension_type/nauvis.json} and
 * {@code data/minetorio/worldgen/noise_settings/nauvis.json}.
 */
public final class NauvisParams {
    private NauvisParams() {}

    // --- vertical frame -----------------------------------------------------------------------------
    public static final int MIN_Y = -16;
    public static final int HEIGHT = 144;
    /** Water fills every air block below this y (the water surface is at {@code SEA_LEVEL - 1}). */
    public static final int SEA_LEVEL = 63;
    /** Lowest top block of dry land: one above the water surface. */
    public static final int LAND_MIN_TOP = SEA_LEVEL;
    /** Height of open land before hills and cliff levels. */
    public static final double BASE_LAND = 66.0;

    // --- water (lakes) ------------------------------------------------------------------------------
    public static final double WATER_WAVELENGTH = 300;
    public static final int WATER_OCTAVES = 3;
    /** Added to the water noise: the bigger, the less water. */
    public static final double WATER_BIAS = 0.32;
    /** Below this water value a column is a lake (shallow water biome). */
    public static final double SHORE = -0.2;
    /** Below this water value a lake is deep (deep water biome). */
    public static final double DEEP = -0.45;
    /** Land rises from the shore to {@link #BASE_LAND} while the water value goes from SHORE to this. */
    public static final double INLAND = 0.05;
    /** Lake depth at the shore and how much deeper it gets per 0.1 of water value below it. */
    public static final double LAKE_MIN_DEPTH = 1;
    public static final double LAKE_DEPTH_PER_TENTH = 1.4;
    public static final double LAKE_MAX_DEPTH = 8;

    // --- gentle relief ------------------------------------------------------------------------------
    public static final double HILL_WAVELENGTH = 110;
    public static final double HILL_AMPLITUDE = 1.3;
    public static final double BUMP_WAVELENGTH = 36;
    public static final double BUMP_AMPLITUDE = 0.45;

    // --- cliffs (Factorio's cliffs are contour lines of an elevation noise) -------------------------
    public static final double ELEVATION_WAVELENGTH = 340;
    public static final int ELEVATION_OCTAVES = 2;
    /** Elevation is scaled by this before rounding to a level: about ±2 levels. */
    public static final double CLIFF_LEVELS = 1.9;
    /** Regions without cliffs: the cliffiness noise scales the levels from 0 (below LOW) to 1 (above HIGH). */
    public static final double CLIFFINESS_WAVELENGTH = 900;
    public static final double CLIFFINESS_LOW = -0.35;
    public static final double CLIFFINESS_HIGH = 0.15;
    /** Smallest and largest wall of one level step (each step's height is fixed for the world). */
    public static final int CLIFF_MIN_STEP = 2;
    public static final int CLIFF_MAX_STEP = 4;
    /** Passes through cliffs: where the gap noise is above GAP_LOW the wall turns into a ramp (fully above GAP_HIGH). */
    public static final double GAP_WAVELENGTH = 70;
    public static final double GAP_LOW = 0.35;
    public static final double GAP_HIGH = 0.6;
    /** Half the width of a pass ramp, in level units (the elevation changes ~0.016 per block). */
    public static final double RAMP_HALF_WIDTH = 0.3;
    /**
     * Cliff-free start: no cliffs within CLIFF_FREE_RADIUS of the origin, full cliffs beyond
     * CLIFF_FREE_RADIUS + CLIFF_FADE. The spawn is chosen within {@link #SPAWN_SEARCH_RADIUS} of the
     * origin, so the starting area ({@link PatchGenerator#STARTING_AREA_RADIUS}) around it stays clear.
     */
    public static final int CLIFF_FREE_RADIUS = PatchGenerator.STARTING_AREA_RADIUS + 64 + 32;
    public static final int CLIFF_FADE = 80;
    /** A column is a cliff wall (cliff rock) when it stands this much above a neighbour. */
    public static final int CLIFF_MIN_DROP = 2;

    // --- rifts and faults (rare narrow chasms) ------------------------------------------------------
    public static final double RIFT_WAVELENGTH = 260;
    /** A column is in a rift while |rift noise| is below this (about 3 blocks wide). */
    public static final double RIFT_WIDTH = 0.022;
    public static final double RIFT_MASK_WAVELENGTH = 520;
    /** Rifts exist only where the mask noise is above MASK_LOW (full depth above MASK_HIGH): rare. */
    public static final double RIFT_MASK_LOW = 0.5;
    public static final double RIFT_MASK_HIGH = 0.7;
    public static final double RIFT_DEPTH = 6;
    /** No rifts within this distance of the origin; full rifts beyond RIFT_FREE_RADIUS + RIFT_FADE. */
    public static final int RIFT_FREE_RADIUS = CLIFF_FREE_RADIUS + 64;
    public static final int RIFT_FADE = 64;
    /** No rifts where the water value is below this (near lakes). */
    public static final double RIFT_MIN_WATER = 0.15;

    // --- climate (biomes) ---------------------------------------------------------------------------
    public static final double MOISTURE_WAVELENGTH = 520;
    public static final double AUX_WAVELENGTH = 640;
    public static final int CLIMATE_OCTAVES = 3;

    // --- vegetation ---------------------------------------------------------------------------------
    public static final double FOREST_WAVELENGTH = 90;

    // --- spawn --------------------------------------------------------------------------------------
    /** The spawn is the flattest dry spot within this distance of the origin. */
    public static final int SPAWN_SEARCH_RADIUS = 64;
    /** Half the side of the square that must be flat around the spawn. */
    public static final int SPAWN_FLAT_HALF = 6;
    /** Flat clutter-free platform for the engineer and wreck. */
    public static final int STARTER_CLEAR_RADIUS = 24;
    /** Guaranteed boiler/pump water, beyond the four starter patches and the wreck platform. */
    public static final int STARTER_LAKE_X = 96;
    public static final int STARTER_LAKE_Z = 32;
    public static final int STARTER_LAKE_INNER_RADIUS = 10;
    public static final int STARTER_LAKE_OUTER_RADIUS = 44;
}
