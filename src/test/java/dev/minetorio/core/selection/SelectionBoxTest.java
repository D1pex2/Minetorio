package dev.minetorio.core.selection;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SelectionBoxTest {
    @Test
    void cornersInAnyOrderGiveTheSameBox() {
        SelectionBox a = SelectionBox.of(5, 1, -3, -2, 4, 7);
        assertEquals(new SelectionBox(-2, 1, -3, 5, 4, 7), a);
        assertEquals(8, a.sizeX());
        assertEquals(4, a.sizeY());
        assertEquals(11, a.sizeZ());
        assertEquals(8L * 4 * 11, a.volume());
        assertTrue(a.contains(-2, 1, -3) && a.contains(5, 4, 7));
        assertFalse(a.contains(6, 4, 7));
    }

    @Test
    void pushMovesOneFaceAndNeverInverts() {
        SelectionBox b = SelectionBox.of(0, 0, 0, 2, 2, 2);
        assertEquals(new SelectionBox(0, 0, 0, 3, 2, 2), b.push(5, 1)); // east out
        assertEquals(new SelectionBox(-1, 0, 0, 2, 2, 2), b.push(4, 1)); // west out
        assertEquals(new SelectionBox(0, 0, 0, 2, 1, 2), b.push(1, -1)); // top in
        assertEquals(new SelectionBox(0, 1, 0, 2, 2, 2), b.push(0, -1)); // bottom in
        assertEquals(new SelectionBox(0, 0, 0, 2, 2, 0), b.push(3, -10)); // south in: stops at one cell
        assertEquals(new SelectionBox(0, 0, 2, 2, 2, 2), b.push(2, -10)); // north in
    }

    @Test
    void columnSpansBelowAndAboveTheCorners() {
        SelectionBox c = SelectionBox.column(10, 64, 0, 0, 66, 5);
        assertEquals(64 - SelectionBox.COLUMN_BELOW, c.minY());
        assertEquals(66 + SelectionBox.COLUMN_ABOVE, c.maxY());
        assertEquals(11, c.sizeX());
    }

    @Test
    void limits() {
        assertTrue(SelectionBox.of(0, 0, 0, 127, 31, 127).withinLimits());
        assertFalse(SelectionBox.of(0, 0, 0, 128, 0, 0).withinLimits());
        assertFalse(SelectionBox.of(0, 0, 0, 127, 32, 127).withinLimits(), "volume");
    }

    @Test
    void faceHitFindsTheFaceTheLookEnters() {
        SelectionBox b = SelectionBox.of(0, 0, 0, 3, 3, 3); // 0..4 on every axis
        assertEquals(4, b.faceHit(-5, 2, 2, 1, 0, 0), "from the west: west face");
        assertEquals(5, b.faceHit(10, 2, 2, -1, 0, 0), "from the east: east face");
        assertEquals(1, b.faceHit(2, 10, 2, 0, -1, 0), "from above: top");
        assertEquals(3, b.faceHit(2, 2, 2, 0, 0, 1), "inside: the face the look leaves through");
        assertEquals(-1, b.faceHit(-5, 2, 2, -1, 0, 0), "looking away");
        assertEquals(-1, b.faceHit(-5, 10, 2, 1, 0, 0), "passing above");
    }
}
