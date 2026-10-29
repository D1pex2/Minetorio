package dev.minetorio.core.world;

import static dev.minetorio.core.world.NauvisParams.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.orefield.PatchGenerator;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NauvisTerrainTest {
    private static final long[] SEEDS = {1L, 12345L, -987654321L};

    @Test void sameSeedSameTerrainAndCliffLines() {
        NauvisTerrain a = new NauvisTerrain(42L);
        NauvisTerrain b = new NauvisTerrain(42L);
        NauvisTerrain c = new NauvisTerrain(43L);
        int differ = 0;
        for (int x = -3000; x <= 3000; x += 37) {
            for (int z = -3000; z <= 3000; z += 41) {
                assertEquals(a.surfaceY(x, z), b.surfaceY(x, z));
                assertEquals(a.isCliffEdge(x, z), b.isCliffEdge(x, z));
                assertEquals(a.riftDepth(x, z), b.riftDepth(x, z));
                assertEquals(a.moisture(x, z), b.moisture(x, z));
                if (a.surfaceY(x, z) != c.surfaceY(x, z)) differ++;
            }
        }
        assertTrue(differ > 100, "another seed must give other terrain, differ=" + differ);
    }

    @Test void seedFromSamplesIsStableAndSpread() {
        assertEquals(NauvisTerrain.seedFromSamples(0.1, 0.2), NauvisTerrain.seedFromSamples(0.1, 0.2));
        assertNotEquals(NauvisTerrain.seedFromSamples(0.1, 0.2), NauvisTerrain.seedFromSamples(0.1, 0.20000001));
        assertSame(NauvisTerrain.of(7L), NauvisTerrain.of(7L));
    }

    @Test void cliffStepsAreTwoToFourBlocks() {
        NauvisTerrain t = new NauvisTerrain(5L);
        for (int k = -6; k <= 6; k++) {
            double step = t.levelHeight(k + 1) - t.levelHeight(k);
            assertTrue(step >= CLIFF_MIN_STEP && step <= CLIFF_MAX_STEP, "step " + k + ": " + step);
        }
        assertEquals(0, t.levelHeight(0));
    }

    /** Cliffs form long lines: many edge columns, mostly next to other edge columns. */
    @Test void cliffsExistAsLinesAwayFromTheStart() {
        for (long seed : SEEDS) {
            NauvisTerrain t = new NauvisTerrain(seed);
            int edges = 0;
            int connected = 0;
            for (int x = 600; x < 1400; x++) {
                for (int z = 600; z < 1400; z += 8) {
                    if (!t.isCliffEdge(x, z)) continue;
                    edges++;
                    if (t.isCliffEdge(x + 1, z) || t.isCliffEdge(x - 1, z) || t.isCliffEdge(x, z + 1) || t.isCliffEdge(x, z - 1)
                            || t.isCliffEdge(x + 1, z + 1) || t.isCliffEdge(x - 1, z - 1) || t.isCliffEdge(x + 1, z - 1)
                            || t.isCliffEdge(x - 1, z + 1)) connected++;
                }
            }
            assertTrue(edges > 20, "seed " + seed + ": only " + edges + " cliff columns");
            assertTrue(connected > edges * 0.9, "seed " + seed + ": cliff columns not in lines " + connected + "/" + edges);
        }
    }

    @Test void startAreaHasNoCliffsOrRifts() {
        for (long seed : SEEDS) {
            NauvisTerrain t = new NauvisTerrain(seed);
            int r = SPAWN_SEARCH_RADIUS + PatchGenerator.STARTING_AREA_RADIUS;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > r * r) continue;
                    assertFalse(t.isCliffEdge(x, z), "seed " + seed + ": cliff at " + x + "," + z);
                    assertEquals(0, t.riftDepth(x, z), "seed " + seed + ": rift at " + x + "," + z);
                }
            }
        }
    }

    @Test void riftExclusionRadius() {
        NauvisTerrain t = new NauvisTerrain(99L);
        for (int x = -RIFT_FREE_RADIUS; x <= RIFT_FREE_RADIUS; x += 3) {
            for (int z = -RIFT_FREE_RADIUS; z <= RIFT_FREE_RADIUS; z += 3) {
                if ((long) x * x + (long) z * z <= (long) RIFT_FREE_RADIUS * RIFT_FREE_RADIUS) assertEquals(0, t.riftDepth(x, z));
            }
        }
        assertEquals(0, NauvisTerrain.startFade(0, RIFT_FREE_RADIUS, RIFT_FREE_RADIUS, RIFT_FADE));
        assertEquals(1, NauvisTerrain.startFade(0, RIFT_FREE_RADIUS + RIFT_FADE, RIFT_FREE_RADIUS, RIFT_FADE));
    }

    /** Rifts are rare but exist, a few blocks deep, never reaching the water. */
    @Test void riftsAreRareNarrowAndDry() {
        int rift = 0;
        int total = 0;
        for (long seed : SEEDS) {
            NauvisTerrain t = new NauvisTerrain(seed);
            for (int x = -4000; x <= 4000; x += 2) {
                for (int z = -4000; z <= 4000; z += 50) {
                    total++;
                    double d = t.riftDepth(x, z);
                    if (d <= 0) continue;
                    rift++;
                    assertTrue(d <= RIFT_DEPTH);
                    assertTrue(t.surfaceY(x, z) >= LAND_MIN_TOP, "rift floor under water level");
                }
            }
        }
        assertTrue(rift > 0, "no rifts at all");
        assertTrue(rift < total * 0.02, "rifts too common: " + rift + "/" + total);
    }

    @Test void landIsGentleOffCliffs() {
        NauvisTerrain t = new NauvisTerrain(12345L);
        int steep = 0;
        int n = 0;
        for (int x = -2000; x <= 2000; x += 7) {
            for (int z = -2000; z <= 2000; z += 7) {
                if (t.isWater(x, z) || t.isWater(x + 1, z) || t.isCliffEdge(x, z) || t.isCliffEdge(x + 1, z) || t.riftDepth(x, z) > 0
                        || t.riftDepth(x + 1, z) > 0) continue;
                n++;
                if (Math.abs(t.surfaceY(x, z) - t.surfaceY(x + 1, z)) > 1) steep++;
            }
        }
        assertEquals(0, steep, "steps of 2+ blocks outside cliffs, of " + n);
    }

    @Test void waterCoverageAndLevels() {
        for (long seed : SEEDS) {
            NauvisTerrain t = new NauvisTerrain(seed);
            int water = 0;
            int n = 0;
            for (int x = -3000; x <= 3000; x += 23) {
                for (int z = -3000; z <= 3000; z += 23) {
                    n++;
                    int y = t.surfaceY(x, z);
                    if (t.isWater(x, z)) {
                        water++;
                        assertTrue(y < SEA_LEVEL - 1, "lake bed above the water surface");
                    } else {
                        assertTrue(y >= LAND_MIN_TOP, "land below the water surface at " + x + "," + z + ": " + y);
                        assertTrue(y < MIN_Y + HEIGHT - 32, "land too high: " + y);
                    }
                }
            }
            double f = water / (double) n;
            assertTrue(f > 0.04 && f < 0.3, "seed " + seed + ": water fraction " + f);
        }
    }

    @Test void spawnIsFlatDryAndNearTheOrigin() {
        for (long seed : SEEDS) {
            NauvisTerrain t = new NauvisTerrain(seed);
            int[] s = SpawnFinder.find(t);
            assertArrayEquals(s, SpawnFinder.find(new NauvisTerrain(seed)), "deterministic");
            assertTrue(Math.hypot(s[0], s[1]) <= SPAWN_SEARCH_RADIUS);
            assertTrue(SpawnFinder.isFlatDry(t, s[0], s[1]), "seed " + seed + ": spawn " + s[0] + "," + s[1] + " not flat and dry");
        }
    }

    @Test void unluckySeedsStillHaveDryStartAndAllFourStarterFields() {
        for (long seed : new long[] {132, 0, 1, 89, 12345, -987654321}) {
            NauvisTerrain t = new NauvisTerrain(seed);
            int[] s = SpawnFinder.find(t);
            assertTrue(SpawnFinder.isFlatDry(t, s[0], s[1]), "unsafe seed " + seed);
            assertEquals(0, SpawnFinder.wetFieldSamples(t, s[0], s[1]), "flooded starter field for " + seed);
            for (CrashSite.Piece p : CrashSite.layout()) {
                int x = s[0] + CrashSite.ANCHOR_DX + p.dx();
                int z = s[1] + CrashSite.ANCHOR_DZ + p.dz();
                assertFalse(t.isWater(x, z), "wreck underwater for " + seed);
            }
        }
    }

    @Test void everySeedHasNearbyWaterWithoutFloodingStarterFields() {
        for (long seed : new long[] {0, 1, 132, 12345, -987654321}) {
            NauvisTerrain t = new NauvisTerrain(seed);
            boolean found = false;
            for (int x = -128; x <= 128; x += 8) for (int z = -128; z <= 128; z += 8) {
                if (Math.hypot(x, z) <= 128 && t.isWater(x, z)) found = true;
            }
            assertTrue(found, "no starting water for boilers on seed " + seed);
            assertEquals(0, SpawnFinder.wetFieldSamples(t, 0, 0));
        }
    }

    @Test void crashSiteLayout() {
        Set<String> cells = new HashSet<>();
        int chests = 0;
        for (CrashSite.Piece p : CrashSite.layout()) {
            assertTrue(cells.add(p.dx() + "," + p.dy() + "," + p.dz()), "two pieces in one cell: " + p);
            if (p.kind() == CrashSite.Kind.CHEST) chests++;
            assertTrue(Math.abs(CrashSite.ANCHOR_DX + p.dx()) <= SPAWN_FLAT_HALF + 8, "piece too far: " + p);
        }
        assertEquals(1, chests);
    }
}
