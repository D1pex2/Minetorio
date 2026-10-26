package dev.minetorio.core.power;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PoleGraphTest {
    @Test void polesWithinReachConnect() {
        PolePos a = new PolePos(0, 0, 0), b = new PolePos(7, 0, 0), c = new PolePos(20, 0, 0), d = new PolePos(8, 0, 0);
        PoleGraph g = new PoleGraph(7.5, 2);
        g.addPole(a); g.addPole(b);
        assertEquals(g.componentOf(a), g.componentOf(b));
        assertTrue(g.neighbors(a).contains(b));
        g.addPole(c);
        assertNotEquals(g.componentOf(a), g.componentOf(c));
        PoleGraph h = new PoleGraph(7.5, 2);
        h.addPole(a); h.addPole(d);
        assertNotEquals(h.componentOf(a), h.componentOf(d));
    }

    @Test void removingMiddlePoleSplitsNetwork() {
        PoleGraph g = new PoleGraph(7.5, 2);
        PolePos a = new PolePos(0, 0, 0), m = new PolePos(7, 0, 0), b = new PolePos(14, 0, 0);
        g.addPole(a); g.addPole(m); g.addPole(b);
        assertEquals(g.componentOf(a), g.componentOf(b));
        g.removePole(m);
        assertNotEquals(g.componentOf(a), g.componentOf(b));
    }

    @Test void coverage() {
        PoleGraph g = new PoleGraph(7.5, 2);
        g.addPole(new PolePos(0, 0, 0));
        assertTrue(g.componentCovering(2, 0, -2).isPresent());
        assertTrue(g.componentCovering(0, 2, 0).isPresent());
        assertFalse(g.componentCovering(3, 0, 0).isPresent());
        assertFalse(g.componentCovering(0, 3, 0).isPresent());
    }
}
