package dev.minetorio.core.belt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BeltMiscTest {
    @Test void shapeCurveOnlyWithSingleSideInput() {
        assertEquals(BeltShape.CURVE_LEFT, BeltShape.of(false, true, false));
        assertEquals(BeltShape.CURVE_RIGHT, BeltShape.of(false, false, true));
        assertEquals(BeltShape.STRAIGHT, BeltShape.of(true, true, false));
        assertEquals(BeltShape.STRAIGHT, BeltShape.of(false, true, true));
        assertEquals(BeltShape.STRAIGHT, BeltShape.of(false, false, false));
    }

    @Test void laneLengths() {
        assertEquals(1.0, BeltShape.STRAIGHT.laneLength(true));
        assertEquals(1.0, BeltShape.STRAIGHT.laneLength(false));
        assertEquals(0.5, BeltShape.CURVE_LEFT.laneLength(true));
        assertEquals(1.0, BeltShape.CURVE_RIGHT.laneLength(false));
    }

    @Test void splitterAlternates() {
        SplitterLogic s = new SplitterLogic();
        int[] r = new int[4];
        for (int i = 0; i < 4; i++) r[i] = s.route(0, true, true);
        assertArrayEquals(new int[] {0, 1, 0, 1}, r);
    }

    @Test void splitterFallsBack() {
        SplitterLogic s = new SplitterLogic();
        assertEquals(1, s.route(0, false, true));
        assertEquals(-1, s.route(0, false, false));
        // fell back to right, toggle flips as if sent normally: next prefers left
        assertEquals(0, s.route(0, true, true));
        // lane 1 has its own toggle
        assertEquals(0, s.route(1, true, true));
    }

    @Test void undergroundPairsWithinRange() {
        int gap = BeltTier.YELLOW.undergroundMaxGap();
        List<Boolean> five = List.of(false, false, false, false, true, false);
        List<Boolean> six = List.of(false, false, false, false, false, true);
        assertEquals(5, UndergroundLink.findEntranceDistance(five, gap).getAsInt());
        assertTrue(UndergroundLink.findEntranceDistance(six, gap).isEmpty());
        assertEquals(2, UndergroundLink.findEntranceDistance(List.of(false, true, true), gap).getAsInt());
    }
}
