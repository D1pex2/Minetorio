package dev.minetorio.core.orefield;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OreFieldCoreTest {
    private final PatchGenerator gen = new PatchGenerator(12345L, 0, 0);

    // --- amount formula ----------------------------------------------------------------------------

    @Test void baseAmountsAtSpawn() {
        assertEquals(600, OreType.IRON.baseAmount());
        assertEquals(600, OreType.COPPER.baseAmount());
        assertEquals(600, OreType.COAL.baseAmount());
        assertEquals(400, OreType.STONE.baseAmount());
    }

    @Test void amountIsDoubleAtCenterAndHalfAtEdge() {
        assertEquals(1200, OreAmounts.amount(OreType.IRON, 0, 0, 10));
        assertEquals(300, OreAmounts.amount(OreType.IRON, 0, 10, 10));
        assertEquals(750, OreAmounts.amount(OreType.IRON, 0, 5, 10)); // 2 - 1.5 * 0.5 = 1.25
        assertEquals(200, OreAmounts.amount(OreType.STONE, 0, 10, 10));
        assertEquals(300, OreAmounts.amount(OreType.IRON, 0, 30, 10), "beyond the edge clamps to the edge factor");
    }

    @Test void amountGrowsWithDistanceFromSpawn() {
        assertEquals(2400, OreAmounts.amount(OreType.IRON, 500, 0, 10));  // × (1 + 500/500)
        assertEquals(3600, OreAmounts.amount(OreType.COAL, 1000, 0, 10)); // × 3
        assertEquals(Math.round(600 * (1 + 250 / 500.0) * 0.5), OreAmounts.amount(OreType.COPPER, 250, 8, 8));
    }

    @Test void amountAtUsesTheBlocksDistances() {
        OrePatch p = new OrePatch(OreType.IRON, 300, 400, 10); // center 500 from spawn (0, 0)
        assertEquals(2400, OreAmounts.amountAt(p, 300, 400, 0, 0));
        long edge = OreAmounts.amountAt(p, 310, 400, 0, 0);
        assertEquals(OreAmounts.amount(OreType.IRON, Math.hypot(310, 400), 10, 10), edge);
    }

    @Test void depletedBelowTenPercent() {
        assertFalse(OreAmounts.isDepleted(100, 1000));
        assertTrue(OreAmounts.isDepleted(99, 1000));
        assertTrue(OreAmounts.isDepleted(0, 1000));
        assertFalse(OreAmounts.isDepleted(1, 1));
    }

    // --- patch generation ----------------------------------------------------------------------------

    @Test void generationIsDeterministic() {
        PatchGenerator a = new PatchGenerator(7L, 0, 0);
        PatchGenerator b = new PatchGenerator(7L, 0, 0);
        for (int rx = -10; rx < 10; rx++) {
            for (int rz = -10; rz < 10; rz++) assertEquals(a.inRegion(rx, rz), b.inRegion(rx, rz));
        }
    }

    @Test void patchesStayInsideTheirRegionAndFollowTheRules() {
        int n = 0;
        Map<OreType, Integer> byType = new HashMap<>();
        for (int rx = -40; rx < 40; rx++) {
            for (int rz = -40; rz < 40; rz++) {
                OrePatch p = gen.inRegion(rx, rz);
                if (p == null) continue;
                n++;
                byType.merge(p.type(), 1, Integer::sum);
                assertTrue(p.radius() >= PatchGenerator.MIN_RADIUS && p.radius() <= PatchGenerator.MAX_RADIUS, "radius " + p);
                assertTrue(p.centerX() - p.radius() >= rx * 64 && p.centerX() + p.radius() <= rx * 64 + 63, "x " + p);
                assertTrue(p.centerZ() - p.radius() >= rz * 64 && p.centerZ() + p.radius() <= rz * 64 + 63, "z " + p);
                assertTrue(Math.hypot(p.centerX(), p.centerZ()) - p.radius() >= PatchGenerator.SPAWN_EXCLUSION, "near spawn " + p);
            }
        }
        // 35 % of 6400 regions minus the spawn area ≈ 2240
        assertTrue(n > 2000 && n < 2500, "patches " + n);
        assertTrue(byType.get(OreType.IRON) > byType.get(OreType.COPPER), byType.toString());
        assertTrue(byType.get(OreType.COPPER) > byType.get(OreType.COAL), byType.toString());
        assertTrue(byType.get(OreType.COAL) > byType.get(OreType.STONE), byType.toString());
    }

    @Test void startingPatchesAroundSpawn() {
        List<OrePatch> s = PatchGenerator.startingPatches(100, -50);
        assertEquals(List.of(
                new OrePatch(OreType.IRON, 148, -50, 10),
                new OrePatch(OreType.COPPER, 52, -50, 10),
                new OrePatch(OreType.COAL, 100, -2, 10),
                new OrePatch(OreType.STONE, 100, -98, 10)), s);
    }

    @Test void patchesTouchingFindsEveryPatchOverlappingTheBox() {
        for (int rx = -5; rx < 5; rx++) {
            for (int rz = -5; rz < 5; rz++) {
                OrePatch p = gen.inRegion(rx, rz);
                if (p == null) continue;
                List<OrePatch> found = gen.patchesTouching(p.centerX() + p.radius(), p.centerZ(), p.centerX() + p.radius() + 15, p.centerZ() + 15);
                assertTrue(found.contains(p), "missed " + p);
                assertFalse(gen.patchesTouching(p.centerX() + p.radius() + 1, p.centerZ() - 2, p.centerX() + p.radius() + 3, p.centerZ() + 2)
                        .contains(p), "box beside " + p);
            }
        }
    }

    // --- stamping -------------------------------------------------------------------------------------

    /** Stamping chunk by chunk gives exactly the cells of stamping the whole area at once. */
    @Test void stampingIsDeterministicAcrossChunkBoundaries() {
        int minX = -256, minZ = 256, maxX = 255, maxZ = 767;
        List<OrePatch> all = gen.patchesTouching(minX, minZ, maxX, maxZ);
        assertFalse(all.isEmpty());
        Set<FieldStamp.Cell> whole = new HashSet<>(FieldStamp.plan(all, minX, minZ, maxX, maxZ, 0, 0));
        Set<FieldStamp.Cell> byChunk = new HashSet<>();
        int cells = 0;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                int x0 = cx << 4, z0 = cz << 4;
                List<FieldStamp.Cell> plan = FieldStamp.plan(gen.patchesTouching(x0, z0, x0 + 15, z0 + 15), x0, z0, x0 + 15, z0 + 15, 0, 0);
                for (FieldStamp.Cell c : plan) {
                    assertTrue(c.x() >= x0 && c.x() <= x0 + 15 && c.z() >= z0 && c.z() <= z0 + 15, "cell outside its chunk " + c);
                }
                cells += plan.size();
                byChunk.addAll(plan);
            }
        }
        assertEquals(whole, byChunk);
        assertEquals(whole.size(), cells, "a cell stamped twice");
    }

    @Test void planCoversExactlyThePatchDisk() {
        OrePatch p = new OrePatch(OreType.COAL, 8, 8, 6);
        List<FieldStamp.Cell> plan = FieldStamp.plan(List.of(p), -20, -20, 40, 40, 0, 0);
        int disk = 0;
        for (int x = -20; x <= 40; x++) for (int z = -20; z <= 40; z++) if (p.contains(x, z)) disk++;
        assertEquals(disk, plan.size());
        for (FieldStamp.Cell c : plan) {
            assertEquals(OreType.COAL, c.type());
            assertEquals(OreAmounts.amountAt(p, c.x(), c.z(), 0, 0), c.amount());
        }
    }

    @Test void firstPatchWinsWhereTwoOverlap() {
        OrePatch a = new OrePatch(OreType.IRON, 0, 0, 5);
        OrePatch b = new OrePatch(OreType.COPPER, 4, 0, 5);
        for (FieldStamp.Cell c : FieldStamp.plan(List.of(a, b), -10, -10, 10, 10, 0, 0)) {
            assertEquals(a.contains(c.x(), c.z()) ? OreType.IRON : OreType.COPPER, c.type(), c.toString());
        }
    }

    // --- drill order -----------------------------------------------------------------------------------

    @Test void miningOrderIsNearestToTheCenterFirst() {
        List<int[]> order = MiningOrder.nearestFirst(10, 20, 14, 24); // 5×5, center (12, 22)
        assertEquals(25, order.size());
        assertArrayEquals(new int[] {12, 22}, order.get(0));
        List<Double> dist = new ArrayList<>();
        for (int[] c : order) dist.add(Math.hypot(c[0] - 12, c[1] - 22));
        for (int i = 1; i < dist.size(); i++) assertTrue(dist.get(i - 1) <= dist.get(i), "order " + dist);
        assertEquals(order.size(), new HashSet<>(order.stream().map(c -> c[0] + "," + c[1]).toList()).size());
    }

    @Test void miningOrderOfAnEvenSquare() {
        List<int[]> order = MiningOrder.nearestFirst(0, 0, 1, 1);
        assertEquals(4, order.size()); // all four equally near the center (0.5, 0.5): row by row
        assertArrayEquals(new int[] {0, 0}, order.get(0));
        assertArrayEquals(new int[] {1, 1}, order.get(3));
    }
}
