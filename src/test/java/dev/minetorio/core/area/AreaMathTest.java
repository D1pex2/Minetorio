package dev.minetorio.core.area;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AreaMathTest {
    @Test void poleSupplyIsAFiveByFiveSquare() {
        GroundRect r = AreaMath.poleSupply(10, -3, 2);
        assertEquals(new GroundRect(8, -5, 12, -1), r);
        assertEquals(5, r.width());
        assertEquals(5, r.depth());
        assertTrue(r.onEdge(8, -3));
        assertFalse(r.onEdge(10, -3));
        assertFalse(r.onEdge(7, -3));
    }

    /** Matches the drill game tests: electric drill at (2,2) facing east covers x 1..5, z 1..5. */
    @Test void electricDrillAreaAroundItsFootprint() {
        assertEquals(new GroundRect(1, 1, 5, 5), AreaMath.drillArea(2, 2, 1, 0, 3, 3, 5));
        // facing north from (2,2): footprint x 2..4, z 0..2 → area x 1..5, z -1..3
        assertEquals(new GroundRect(1, -1, 5, 3), AreaMath.drillArea(2, 2, 0, -1, 3, 3, 5));
    }

    @Test void burnerDrillAreaIsItsFootprint() {
        assertEquals(new GroundRect(2, 2, 3, 3), AreaMath.drillArea(2, 2, 1, 0, 2, 2, 2));
        // facing west: footprint extends to -x and clockwise (north) to -z
        assertEquals(new GroundRect(1, 1, 2, 2), AreaMath.drillArea(2, 2, -1, 0, 2, 2, 2));
    }

    @Test void radarScanIsSevenBySevenChunks() {
        GroundRect r = AreaMath.chunkSquare(0, -1, 3);
        assertEquals(-48, r.minX());
        assertEquals(63, r.maxX());
        assertEquals(-64, r.minZ());
        assertEquals(47, r.maxZ());
        assertEquals(7 * 16, r.width());
    }

    @Test void circleIsClosedAndOnTheRadius() {
        double[] c = AreaMath.circle(1, 2, 7.5, 32);
        assertEquals(33 * 2, c.length);
        assertEquals(c[0], c[c.length - 2], 1e-9);
        assertEquals(c[1], c[c.length - 1], 1e-9);
        for (int i = 0; i < c.length; i += 2) assertEquals(7.5, Math.hypot(c[i] - 1, c[i + 1] - 2), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> AreaMath.circle(0, 0, 1, 2));
    }

    @Test void groundYFindsTheFirstGroundBelow() {
        assertEquals(63, AreaMath.groundY(66, 60, y -> y <= 63, 99));
        assertEquals(66, AreaMath.groundY(66, 60, y -> true, 99));
        assertEquals(99, AreaMath.groundY(66, 60, y -> false, 99));
    }

    @Test void emptyRectRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GroundRect(1, 0, 0, 0));
    }
}
