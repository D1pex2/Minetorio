package dev.minetorio.core.rail;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.RailGeometryTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RailGraphTest {
    @Test
    void allEightHeadingsConnectOnlyMatchingTangents() {
        for (int q = 0; q < 4; q++) for (boolean mirrored : new boolean[]{false, true}) {
            for (Shape shape : new Shape[]{Shape.STRAIGHT, Shape.DIAGONAL}) {
                Piece first = piece(1, 0, 0, 0, shape, 0, false);
                Piece next = shape == Shape.STRAIGHT
                        ? piece(2, 0, 0, -2, shape, 0, false)
                        : piece(2, 2, 0, 0, shape, 2, false);
                List<Piece> pieces = symmetry(List.of(first, next), q, mirrored);
                RailGraph graph = new RailGraph(pieces);
                boolean nextForward = shape == Shape.STRAIGHT;
                assertEquals(List.of(new Traversal(next.id(), nextForward)), graph.next(new Traversal(first.id(), true)));
                assertEquals(List.of(new Traversal(first.id(), false)), graph.next(new Traversal(next.id(), !nextForward)));
                assertTrue(graph.next(new Traversal(next.id(), nextForward)).isEmpty());
            }
            // Обе кривые имеют общий A, однако касательные выхода/входа несовместимы.
            List<Piece> wrong = symmetry(List.of(curve(1), piece(2, 2, 0, 0, Shape.CURVE, 0, true)), q, mirrored);
            assertTrue(new RailGraph(wrong).next(new Traversal(wrong.getFirst().id(), false)).isEmpty());
        }
        for (RailDir direction : RailDir.values()) {
            assertEquals(direction, direction.opposite().opposite());
            assertEquals(direction, direction.rotateQuarter(4));
            assertEquals(direction, direction.mirrorX().mirrorX());
            assertEquals(direction.opposite(), direction.rotateQuarter(2));
            assertEquals(direction.rotateQuarter(3), direction.rotateQuarter(-1));
        }
        assertEquals(RailDir.NW, RailDir.NE.mirrorX());
        assertEquals(RailDir.SE, RailDir.NE.rotateQuarter(1));
    }

    @Test
    void crossingConflictsWithoutTurn() {
        Piece north = piece(1, 0, 0, 0, Shape.STRAIGHT, 0, false);
        Piece east = piece(2, 0, 0, -2, Shape.STRAIGHT, 1, false);
        RailGraph graph = new RailGraph(List.of(north, east));
        assertTrue(graph.conflicts(north.id()).contains(east.id()));
        assertTrue(graph.conflicts(east.id()).contains(north.id()));
        assertFalse(graph.conflicts(north.id()).contains(north.id()));
        for (Piece piece : List.of(north, east)) for (boolean forward : new boolean[]{false, true}) {
            assertTrue(graph.next(new Traversal(piece.id(), forward)).isEmpty());
        }
        // Общая клетка сама по себе не означает пересечение полос шириной 1.2.
        Piece separated = piece(3, 2, 0, 0, Shape.STRAIGHT, 0, false);
        RailGraph parallel = new RailGraph(List.of(north, separated));
        assertFalse(parallel.conflicts(north.id()).contains(separated.id()));
        Piece diagonal = piece(4, 0, 0, 0, Shape.DIAGONAL, 0, false);
        Piece nearby = piece(5, 2, 0, 0, Shape.DIAGONAL, 0, false);
        assertFalse(Collections.disjoint(cells(diagonal), cells(nearby)));
        assertFalse(new RailGraph(List.of(diagonal, nearby)).conflicts(diagonal.id()).contains(nearby.id()));
    }

    @Test
    void twoHeightsDoNotConnect() {
        Piece first = curve(1);
        Piece second = piece(2, 8, 1, -14, Shape.CURVE, 2, false);
        RailGraph graph = new RailGraph(List.of(first, second));
        assertTrue(graph.next(new Traversal(first.id(), true)).isEmpty());
        assertTrue(graph.conflicts(first.id()).isEmpty());
        assertFalse(cells(first).contains(new Point(3, 1, -7)));
    }

    @Test
    void junctionHasOnlyBuiltBranches() {
        Piece incoming = piece(1, 0, 0, 2, Shape.STRAIGHT, 0, false);
        Piece straight = piece(3, 0, 0, 0, Shape.STRAIGHT, 0, false);
        Piece turn = curve(2);
        RailGraph graph = new RailGraph(List.of(straight, incoming, turn));
        assertEquals(List.of(new Traversal(turn.id(), true), new Traversal(straight.id(), true)),
                graph.next(new Traversal(incoming.id(), true)));
        graph.rebuild(List.of(incoming, straight));
        assertEquals(List.of(new Traversal(straight.id(), true)), graph.next(new Traversal(incoming.id(), true)));
        assertTrue(graph.piece(turn.id()).isEmpty());
    }

    @Test
    void rebuildIsAtomicAndRevisionChangesAndResultsAreImmutable() {
        Piece first = curve(1);
        RailGraph graph = new RailGraph(List.of(first));
        long initial = graph.revision();
        graph.rebuild(List.of());
        assertTrue(graph.revision() > initial);
        assertTrue(graph.piece(first.id()).isEmpty());
        long empty = graph.revision();
        assertThrows(IllegalArgumentException.class, () -> graph.rebuild(List.of(first, first)));
        assertEquals(empty, graph.revision());
        assertTrue(graph.piece(first.id()).isEmpty());
        assertTrue(graph.next(new Traversal(new UUID(0, 99), true)).isEmpty());
        assertTrue(graph.conflicts(new UUID(0, 99)).isEmpty());
        graph.rebuild(sBend(0));
        assertThrows(UnsupportedOperationException.class,
                () -> graph.next(new Traversal(first.id(), true)).clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.conflicts(first.id()).clear());
    }

    @Test
    void inputOrderDoesNotChangeTransitions() {
        List<Piece> fixture = new ArrayList<>(quarter(3));
        RailGraph first = new RailGraph(fixture);
        Collections.reverse(fixture);
        RailGraph second = new RailGraph(fixture);
        for (Piece piece : fixture) for (boolean forward : new boolean[]{false, true}) {
            Traversal traversal = new Traversal(piece.id(), forward);
            assertEquals(first.next(traversal), second.next(traversal));
            assertEquals(first.conflicts(piece.id()), second.conflicts(piece.id()));
        }
    }
}
