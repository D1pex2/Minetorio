package dev.minetorio.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BlueprintDragTest {
    @Test void repeatsWholeFootprintsAndFillsSkippedCursorCells() {
        BlueprintDrag drag = new BlueprintDrag(10, 20, 3, 2);
        assertEquals(List.of(), drag.extend(12, 21, 16));
        assertEquals(List.of(new BlueprintDrag.Cell(13, 20), new BlueprintDrag.Cell(16, 20)), drag.extend(18, 20, 16));
        assertEquals(List.of(new BlueprintDrag.Cell(16, 22), new BlueprintDrag.Cell(16, 24)), drag.extend(16, 24, 16));
    }

    @Test void backtrackingDoesNotPasteTheSameTileAgain() {
        BlueprintDrag drag = new BlueprintDrag(-4, -2, 2, 3);
        assertEquals(List.of(new BlueprintDrag.Cell(-6, -2), new BlueprintDrag.Cell(-8, -2)), drag.extend(-8, -2, 16));
        assertEquals(List.of(), drag.extend(-4, -2, 16));
        assertEquals(List.of(new BlueprintDrag.Cell(-4, -5)), drag.extend(-4, -5, 16));
    }

    @Test void throttledDrainKeepsUnsentTilesAndHandlesRotatedFootprints() {
        BlueprintDrag drag = new BlueprintDrag(0, 0, 2, 5);
        assertEquals(List.of(new BlueprintDrag.Cell(2, 0)), drag.extend(6, 0, 1));
        assertEquals(List.of(new BlueprintDrag.Cell(4, 0)), drag.extend(6, 0, 1));
        assertEquals(List.of(new BlueprintDrag.Cell(6, 0)), drag.extend(6, 0, 1));
        assertEquals(List.of(), drag.extend(6, 4, 1));
        assertEquals(List.of(new BlueprintDrag.Cell(6, 5)), drag.extend(6, 5, 1));
    }
}
