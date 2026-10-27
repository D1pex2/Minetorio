package dev.minetorio.core.drag;

import static dev.minetorio.core.drag.DragDir.EAST;
import static dev.minetorio.core.drag.DragDir.NORTH;
import static dev.minetorio.core.drag.DragDir.SOUTH;
import static dev.minetorio.core.drag.DragDir.WEST;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class DragLineTest {
    private static void apply(DragLine line, List<DragDir> steps) {
        for (DragDir d : steps) assertTrue(line.step(d), "step " + d + " refused");
    }

    @Test void dirsTurnClockwise() {
        assertEquals(EAST, NORTH.clockWise());
        assertEquals(SOUTH, EAST.clockWise());
        assertEquals(WEST, NORTH.counterClockWise());
        assertEquals(SOUTH, NORTH.opposite());
        assertEquals(1, EAST.dx());
        assertEquals(-1, NORTH.dz());
        for (DragDir d : DragDir.values()) assertEquals(d, DragDir.byId(d.id()));
        assertNull(DragDir.byId(4));
        assertNull(DragDir.byId(-1));
    }

    @Test void newLineIsJustTheStart() {
        DragLine line = new DragLine(3, 7, EAST, 100);
        assertEquals(1, line.size());
        assertEquals(new DragCell(3, 7, EAST), line.head());
        assertEquals(EAST, line.direction());
        assertEquals(0, line.steps());
    }

    @Test void straightRunFollowsTheTarget() {
        DragLine line = new DragLine(0, 0, EAST, 100);
        List<DragDir> plan = line.plan(5, 0, 2, 64);
        assertEquals(List.of(EAST, EAST, EAST, EAST, EAST), plan);
        apply(line, plan);
        assertEquals(6, line.size());
        for (int i = 0; i < 6; i++) assertEquals(new DragCell(i, 0, EAST), line.cell(i));
        assertTrue(line.plan(5, 0, 2, 64).isEmpty(), "already at the target");
    }

    @Test void targetBehindOrSlightlyOffTheLineAddsNothingSideways() {
        DragLine line = new DragLine(0, 0, EAST, 100);
        assertTrue(line.plan(-3, 0, 2, 64).isEmpty());
        assertTrue(line.plan(0, 1, 2, 64).isEmpty(), "one cell off the line is below the turn threshold");
        assertEquals(List.of(EAST, EAST, EAST), line.plan(3, 1, 2, 64), "side offset below the threshold is ignored");
        assertEquals(List.of(EAST, EAST, EAST), line.plan(3, -1, 2, 64));
    }

    @Test void turningMakesTheCornerFaceTheNewWay() {
        DragLine line = new DragLine(0, 0, EAST, 100);
        apply(line, line.plan(4, 0, 2, 64));
        List<DragDir> plan = line.plan(5, -4, 2, 64);
        assertEquals(List.of(EAST, NORTH, NORTH, NORTH, NORTH), plan);
        apply(line, plan);
        assertEquals(new DragCell(4, 0, EAST), line.cell(4));
        assertEquals(new DragCell(5, 0, NORTH), line.cell(5), "corner cell faces the new axis");
        for (int i = 1; i <= 4; i++) assertEquals(new DragCell(5, -i, NORTH), line.cell(5 + i));
        assertEquals(NORTH, line.direction());
    }

    @Test void turnClockwiseGoesToTheRight() {
        DragLine line = new DragLine(0, 0, EAST, 100);
        assertEquals(List.of(EAST, EAST, SOUTH, SOUTH, SOUTH), line.plan(2, 3, 2, 64));
    }

    @Test void targetBehindAndToTheSideTurnsAtTheHead() {
        DragLine line = new DragLine(0, 0, EAST, 100);
        apply(line, line.plan(4, 0, 2, 64));
        List<DragDir> plan = line.plan(2, -3, 2, 64);
        assertEquals(List.of(NORTH, NORTH, NORTH), plan);
        apply(line, plan);
        assertEquals(new DragCell(4, 0, NORTH), line.cell(4));
        assertEquals(new DragCell(4, -3, NORTH), line.head());
    }

    @Test void afterTurningTheOldAxisIsTheSideAndNeedsTheThresholdAgain() {
        DragLine line = new DragLine(0, 0, EAST, 100);
        apply(line, line.plan(3, -3, 2, 64)); // 3 east, 3 north
        assertEquals(new DragCell(3, -3, NORTH), line.head());
        assertEquals(List.of(NORTH, NORTH), line.plan(4, -5, 2, 64), "one cell east of the new line: no turn");
    }

    @Test void reversingIsRefused() {
        DragLine line = new DragLine(0, 0, EAST, 100);
        assertFalse(line.step(WEST));
        assertEquals(1, line.size());
        assertTrue(line.step(NORTH));
        assertFalse(line.step(SOUTH));
        assertTrue(line.step(EAST), "two turns in a row are fine");
        assertEquals(new DragCell(0, 0, NORTH), line.cell(0));
        assertEquals(new DragCell(0, -1, EAST), line.cell(1));
        assertEquals(new DragCell(1, -1, EAST), line.cell(2));
    }

    @Test void lengthIsBounded() {
        DragLine line = new DragLine(0, 0, EAST, 3);
        assertTrue(line.step(EAST));
        assertTrue(line.step(EAST));
        assertFalse(line.step(EAST), "line already has 3 cells");
        assertEquals(3, line.size());
        assertTrue(line.plan(10, 0, 2, 64).isEmpty());
    }

    @Test void planIsCapped() {
        DragLine line = new DragLine(0, 0, EAST, 1000);
        assertEquals(4, line.plan(50, 0, 2, 4).size());
        assertEquals(List.of(EAST, EAST, NORTH), line.plan(2, -5, 2, 3));
        DragLine shortLine = new DragLine(0, 0, EAST, 3);
        assertEquals(2, shortLine.plan(50, 0, 2, 64).size(), "capped by the remaining length");
    }

    @Test void turningBehindTheHeadRetractsTheGhostToTheCorner() {
        DragLine line = new DragLine(0, 0, EAST, 100);
        apply(line, line.plan(5, 0, 2, 64));
        assertEquals(3, line.retraction(2, -3, 2, 1));
        assertEquals(1, line.retraction(2, -3, 2, 5), "cells up to the minimum size are kept");
        assertEquals(0, line.retraction(2, -1, 2, 1), "below the turn threshold: no retraction");
        assertEquals(0, line.retraction(2, 0, 2, 1), "looking back along the line: no retraction");
        assertEquals(0, line.retraction(7, -3, 2, 1), "corner ahead of the head: nothing to drop");
        line.truncate(line.size() - 3);
        assertEquals(new DragCell(2, 0, EAST), line.head());
        assertEquals(List.of(NORTH, NORTH, NORTH), line.plan(2, -3, 2, 64));
    }

    @Test void retractionStopsAtThePreviousCorner() {
        DragLine line = new DragLine(0, 0, EAST, 100);
        apply(line, List.of(EAST, EAST, NORTH, NORTH, NORTH)); // head (2,-3) north, corner (2,0)
        assertEquals(3, line.retraction(4, 2, 2, 1), "only the straight run back to the corner");
        line.truncate(line.size() - 3);
        assertEquals(new DragCell(2, 0, EAST), line.head(), "the corner faces the way it was entered again");
        assertEquals(EAST, line.direction());
        assertEquals(new DragCell(1, 0, EAST), line.cell(1));
    }

    @Test void truncateToTheStartRestoresItsDirection() {
        DragLine line = new DragLine(0, 0, EAST, 100);
        apply(line, List.of(NORTH, NORTH));
        line.truncate(1);
        assertEquals(new DragCell(0, 0, EAST), line.head());
        assertEquals(EAST, line.direction());
        assertThrows(IllegalArgumentException.class, () -> line.truncate(0));
        assertThrows(IllegalArgumentException.class, () -> line.truncate(2));
    }

    @Test void turnThresholdOfOneTurnsOnAnyOffset() {
        DragLine line = new DragLine(0, 0, EAST, 100);
        assertEquals(List.of(EAST, EAST, SOUTH), line.plan(2, 1, 1, 64));
    }
}
