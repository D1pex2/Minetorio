package dev.minetorio.core.deposit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CompassTest {
    @Test void cardinal() {
        assertEquals(Compass.N, Compass.of(0, -10));
        assertEquals(Compass.E, Compass.of(10, 0));
        assertEquals(Compass.S, Compass.of(0, 10));
        assertEquals(Compass.W, Compass.of(-10, 0));
    }

    @Test void diagonal() {
        assertEquals(Compass.NE, Compass.of(40, -40));
        assertEquals(Compass.SE, Compass.of(40, 40));
        assertEquals(Compass.SW, Compass.of(-40, 40));
        assertEquals(Compass.NW, Compass.of(-40, -40));
    }

    @Test void sectorBoundaries() {
        // 22.5° either side of north is still N; just past it is NE.
        assertEquals(Compass.N, Compass.of(4, -10));   // ~21.8°
        assertEquals(Compass.NE, Compass.of(5, -10));  // ~26.6°
        assertEquals(Compass.N, Compass.of(-4, -10));
        assertEquals(Compass.NW, Compass.of(-5, -10));
        assertEquals(Compass.S, Compass.of(1, 100));
        assertEquals(Compass.N, Compass.of(0, 0));
    }
}
