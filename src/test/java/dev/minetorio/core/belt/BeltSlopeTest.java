package dev.minetorio.core.belt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BeltSlopeTest {
    @Test void descendingWinsAndAscendingNeedsAStraight() {
        assertEquals(BeltSlope.DESCENDING, BeltSlope.of(true, true, true));
        assertEquals(BeltSlope.DESCENDING, BeltSlope.of(true, false, false));
        assertEquals(BeltSlope.ASCENDING, BeltSlope.of(false, true, true));
        assertEquals(BeltSlope.FLAT, BeltSlope.of(false, true, false), "a curve never ascends");
        assertEquals(BeltSlope.FLAT, BeltSlope.of(false, false, true));
    }

    @Test void riseFollowsTheLane() {
        for (double t : new double[] {0, 0.25, 0.5, 1}) {
            assertEquals(0, BeltSlope.FLAT.rise(t));
            assertEquals(t, BeltSlope.ASCENDING.rise(t), 1e-12);
            assertEquals(1 - t, BeltSlope.DESCENDING.rise(t), 1e-12);
        }
    }

    @Test void entryAndExitLevels() {
        assertEquals(0, BeltSlope.FLAT.entryLevel());
        assertEquals(0, BeltSlope.FLAT.exitLevel());
        assertEquals(0, BeltSlope.ASCENDING.entryLevel());
        assertEquals(1, BeltSlope.ASCENDING.exitLevel());
        assertEquals(1, BeltSlope.DESCENDING.entryLevel());
        assertEquals(0, BeltSlope.DESCENDING.exitLevel());
    }

    @Test void gradeIsOneBlockPerBlock() {
        assertEquals(0, BeltSlope.FLAT.grade());
        assertEquals(1, BeltSlope.ASCENDING.grade());
        assertEquals(-1, BeltSlope.DESCENDING.grade());
    }
}
