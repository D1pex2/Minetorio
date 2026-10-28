package dev.minetorio.core.camera;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LayerCutTest {
    private static final int NONE = LayerCut.NONE;

    @Test
    void blocksAboveTheCutAreHidden() {
        assertFalse(LayerCut.blockHidden(70, 70));
        assertTrue(LayerCut.blockHidden(71, 70));
        assertFalse(LayerCut.blockHidden(-64, 70));
        assertFalse(LayerCut.blockHidden(319, NONE));
        assertTrue(LayerCut.blockHidden(-60, -61));
    }

    @Test
    void entitiesOnTheCutFloorStayVisible() {
        assertFalse(LayerCut.entityHidden(71.0, 70), "standing on the cut block");
        assertFalse(LayerCut.entityHidden(71.2, 70), "item on a belt on the cut block");
        assertTrue(LayerCut.entityHidden(71.5, 70), "on a slab above the cut");
        assertTrue(LayerCut.entityHidden(72.0, 70), "on the floor above");
        assertFalse(LayerCut.entityHidden(1000, NONE));
    }

    @Test
    void clipWithoutCutKeepsTheRay() {
        double[] s = LayerCut.clip(new double[] {1, 100, 2}, new double[] {3, 0, 4}, NONE);
        assertArrayEquals(new double[] {1, 100, 2, 3, 0, 4}, s, 0);
    }

    @Test
    void downwardRayStartsJustUnderTheCutPlane() {
        double[] s = LayerCut.clip(new double[] {0, 100, 0}, new double[] {40, 60, -20}, 70);
        assertNotNull(s);
        double limit = 71 - LayerCut.RAY_EPSILON;
        assertEquals(limit, s[1], 1e-12);
        double t = (100 - limit) / 40;
        assertEquals(40 * t, s[0], 1e-9);
        assertEquals(-20 * t, s[2], 1e-9);
        assertArrayEquals(new double[] {40, 60, -20}, new double[] {s[3], s[4], s[5]}, 0);
        // the first cell the ray enters is the cut block's, never the hidden block above
        assertEquals(70, (int) Math.floor(s[1]));
    }

    @Test
    void rayEntirelyAboveTheCutSeesNothing() {
        assertNull(LayerCut.clip(new double[] {0, 100, 0}, new double[] {10, 80, 0}, 70));
        assertNull(LayerCut.clip(new double[] {0, 100, 0}, new double[] {10, 120, 0}, 70));
    }

    @Test
    void risingRayFromUnderTheCutEndsAtThePlane() {
        double[] s = LayerCut.clip(new double[] {0, 60, 0}, new double[] {0, 100, 50}, 70);
        assertNotNull(s);
        assertArrayEquals(new double[] {0, 60, 0}, new double[] {s[0], s[1], s[2]}, 0);
        assertEquals(71 - LayerCut.RAY_EPSILON, s[4], 1e-12);
        assertEquals(50 * (11 - LayerCut.RAY_EPSILON) / 40, s[5], 1e-9);
    }

    @Test
    void rayAlreadyUnderTheCutIsUnchanged() {
        double[] s = LayerCut.clip(new double[] {0, 65, 0}, new double[] {5, 50, 5}, 70);
        assertArrayEquals(new double[] {0, 65, 0, 5, 50, 5}, s, 0);
    }

    @Test
    void lowerAndRaiseStepTheCut() {
        assertEquals(65, LayerCut.lower(NONE, 64, -64, 319), "first press: waist height over the pivot's block");
        assertEquals(64, LayerCut.lower(65, 64, -64, 319));
        assertEquals(-64, LayerCut.lower(-64, 64, -64, 319), "never under the world");
        assertEquals(319, LayerCut.lower(NONE, 400, -64, 319));
        assertEquals(66, LayerCut.raise(65, 319));
        assertEquals(NONE, LayerCut.raise(318, 319), "reaching the top clears it");
        assertEquals(NONE, LayerCut.raise(NONE, 319));
    }

    @Test
    void dirtySectionsCoverTheChangedBlocks() {
        assertNull(LayerCut.dirtySections(70, 70, -64, 320));
        assertNull(LayerCut.dirtySections(NONE, NONE, -64, 320));
        assertArrayEquals(new int[] {4, 4}, LayerCut.dirtySections(70, 71, -64, 320)); // blocks 70..72
        assertArrayEquals(new int[] {3, 4}, LayerCut.dirtySections(63, 64, -64, 320)); // blocks 63..65
        assertArrayEquals(new int[] {4, 19}, LayerCut.dirtySections(NONE, 70, -64, 320));
        assertArrayEquals(new int[] {4, 19}, LayerCut.dirtySections(70, NONE, -64, 320));
        assertArrayEquals(new int[] {-4, -4}, LayerCut.dirtySections(-64, -63, -64, 320));
    }
}
