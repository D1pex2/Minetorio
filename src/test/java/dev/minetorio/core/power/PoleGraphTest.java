package dev.minetorio.core.power;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PoleGraphTest {
    private static final PoleType S = PoleType.SMALL;

    @Test void polesWithinReachConnect() {
        PolePos a = new PolePos(0, 0, 0), b = new PolePos(7, 0, 0), c = new PolePos(20, 0, 0), d = new PolePos(8, 0, 0);
        PoleGraph g = new PoleGraph();
        g.addPole(a, S); g.addPole(b, S);
        assertEquals(g.componentOf(a), g.componentOf(b));
        assertTrue(g.neighbors(a).contains(b));
        g.addPole(c, S);
        assertNotEquals(g.componentOf(a), g.componentOf(c));
        PoleGraph h = new PoleGraph();
        h.addPole(a, S); h.addPole(d, S);
        assertNotEquals(h.componentOf(a), h.componentOf(d));
    }

    @Test void removingMiddlePoleSplitsNetwork() {
        PoleGraph g = new PoleGraph();
        PolePos a = new PolePos(0, 0, 0), m = new PolePos(7, 0, 0), b = new PolePos(14, 0, 0);
        g.addPole(a, S); g.addPole(m, S); g.addPole(b, S);
        assertEquals(g.componentOf(a), g.componentOf(b));
        g.removePole(m);
        assertNotEquals(g.componentOf(a), g.componentOf(b));
    }

    @Test void coverage() {
        PoleGraph g = new PoleGraph();
        g.addPole(new PolePos(0, 0, 0), S);
        assertTrue(g.componentCovering(2, 0, -2).isPresent());
        assertTrue(g.componentCovering(0, 2, 0).isPresent());
        assertFalse(g.componentCovering(3, 0, 0).isPresent());
        assertFalse(g.componentCovering(0, 3, 0).isPresent());
    }

    /** Factorio 1.1: wire 7.5 / 9 / 30 / 18, supply 5×5 / 7×7 / 4×4 / 18×18. */
    @Test void factorioNumbers() {
        assertEquals(7.5, PoleType.SMALL.wireReach());
        assertEquals(9, PoleType.MEDIUM.wireReach());
        assertEquals(30, PoleType.BIG.wireReach());
        assertEquals(18, PoleType.SUBSTATION.wireReach());
        assertEquals(5, PoleType.SMALL.supplyArea());
        assertEquals(7, PoleType.MEDIUM.supplyArea());
        assertEquals(4, PoleType.BIG.supplyArea());
        assertEquals(18, PoleType.SUBSTATION.supplyArea());
        assertEquals(1, PoleType.MEDIUM.size());
        assertEquals(2, PoleType.BIG.size());
        assertEquals(2, PoleType.SUBSTATION.size());
    }

    @Test void eachTypeConnectsAtItsOwnReach() {
        for (PoleType t : PoleType.values()) {
            int far = (int) Math.floor(t.wireReach());
            PolePos a = new PolePos(0, 0, 0);
            assertTrue(PoleGraph.inReach(a, t, new PolePos(far, 0, 0), t), t + " at " + far);
            assertFalse(PoleGraph.inReach(a, t, new PolePos(far + 1, 0, 0), t), t + " at " + (far + 1));
        }
    }

    /** Mixed types connect at the smaller reach; 2×2 poles measure from their centers. */
    @Test void mixedTypesUseTheSmallerReach() {
        PolePos big = new PolePos(0, 0, 0); // center (1, 1)
        assertTrue(PoleGraph.inReach(big, PoleType.BIG, new PolePos(7, 0, 1), PoleType.SMALL)); // center (7.5, 1.5): 6.5 x, 0.5 z
        assertFalse(PoleGraph.inReach(big, PoleType.BIG, new PolePos(8, 0, 1), PoleType.SMALL)); // 7.5 x, 0.5 z: just out
        assertTrue(PoleGraph.inReach(big, PoleType.BIG, new PolePos(30, 0, 0), PoleType.BIG));
        assertFalse(PoleGraph.inReach(big, PoleType.BIG, new PolePos(31, 0, 0), PoleType.BIG));
        assertTrue(PoleGraph.inReach(big, PoleType.BIG, new PolePos(18, 0, 0), PoleType.SUBSTATION));
        assertFalse(PoleGraph.inReach(big, PoleType.BIG, new PolePos(19, 0, 0), PoleType.SUBSTATION));
    }

    @Test void supplyAreas() {
        PolePos p = new PolePos(0, 10, 0);
        // medium: 7×7 around a 1×1
        assertTrue(PoleGraph.supplies(p, PoleType.MEDIUM, -3, 10, 3));
        assertFalse(PoleGraph.supplies(p, PoleType.MEDIUM, -4, 10, 0));
        // big: 4×4 around its 2×2 (cells -1..2)
        assertTrue(PoleGraph.supplies(p, PoleType.BIG, -1, 10, 2));
        assertFalse(PoleGraph.supplies(p, PoleType.BIG, 3, 10, 0));
        assertFalse(PoleGraph.supplies(p, PoleType.BIG, -2, 10, 0));
        // substation: 18×18 around its 2×2 (cells -8..9)
        assertTrue(PoleGraph.supplies(p, PoleType.SUBSTATION, -8, 10, 9));
        assertTrue(PoleGraph.supplies(p, PoleType.SUBSTATION, 9, 18, -8));
        assertFalse(PoleGraph.supplies(p, PoleType.SUBSTATION, 10, 10, 0));
        assertFalse(PoleGraph.supplies(p, PoleType.SUBSTATION, 0, 10, -9));
        assertFalse(PoleGraph.supplies(p, PoleType.SUBSTATION, 0, 19, 0));
        int cells = 0;
        for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) if (PoleGraph.supplies(p, PoleType.SUBSTATION, x, 10, z)) cells++;
        assertEquals(18 * 18, cells);
    }

    @Test void coveringPicksTheSmallestPoleRegardlessOfOrder() {
        PolePos a = new PolePos(0, 0, 0), b = new PolePos(30, 0, 0);
        PoleGraph g = new PoleGraph();
        g.addPole(b, PoleType.SUBSTATION);
        g.addPole(a, PoleType.SUBSTATION); // not wired (18 < 30): two components
        assertNotEquals(g.componentOf(a), g.componentOf(b));
        // a box both supply: a (smaller position) wins
        assertEquals(g.componentOf(a), g.componentCovering(9, 0, 0, 22, 0, 0).getAsInt());
    }

    @Test void dragReachKeepsSlackFor2x2() {
        assertEquals(7.5, PoleType.SMALL.dragReach());
        assertEquals(9, PoleType.MEDIUM.dragReach());
        assertTrue(PoleType.BIG.dragReach() < 30 - 1.4 && PoleType.BIG.dragReach() > 28);
    }
}
