package dev.minetorio.core.rail;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.RailGeometryTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RailRouterTest {
    @Test
    void routeThroughDiagonalAndCurve() {
        List<Piece> pieces = quarter(1);
        RailGraph graph = new RailGraph(pieces);
        Cursor start = new Cursor(new Traversal(pieces.getFirst().id(), true), 0.25);
        Cursor goal = new Cursor(new Traversal(pieces.getLast().id(), false), L - 0.75);
        var route = RailRouter.route(graph, start, Set.of(goal), (from, to) -> true).orElseThrow();
        assertEquals(List.of(new Traversal(pieces.get(0).id(), true),
                new Traversal(pieces.get(1).id(), true), new Traversal(pieces.get(2).id(), false)), route.steps());
        assertEquals(2 * L + Math.sqrt(2) - 1, route.length(), 1e-12);
        assertEquals(graph.revision(), route.graphRevision());
        assertThrows(UnsupportedOperationException.class, () -> route.steps().clear());
        // Обратное движение измеряет distance от B, а не от A.
        var backwards = RailRouter.route(graph,
                new Cursor(new Traversal(pieces.getLast().id(), true), 0.75),
                Set.of(new Cursor(new Traversal(pieces.getFirst().id(), false), L - 0.25)), (from, to) -> true).orElseThrow();
        assertEquals(route.length(), backwards.length(), 1e-12);
        assertEquals(List.of(new Traversal(pieces.get(2).id(), true),
                new Traversal(pieces.get(1).id(), false), new Traversal(pieces.get(0).id(), false)), backwards.steps());
    }

    @Test
    void deadEndReturnsEmpty() {
        Piece first = curve(1);
        Piece absent = piece(2, 20, 0, 20, Shape.STRAIGHT, 0, false);
        RailGraph graph = new RailGraph(List.of(first, absent));
        Cursor start = new Cursor(new Traversal(first.id(), true), 0);
        assertTrue(RailRouter.route(graph, start,
                Set.of(new Cursor(new Traversal(absent.id(), true), 0)), (from, to) -> true).isEmpty());
        assertTrue(RailRouter.route(graph, start, Set.of(), (from, to) -> true).isEmpty());
        assertTrue(RailRouter.route(graph, start,
                Set.of(new Cursor(new Traversal(first.id(), false), 0)), (from, to) -> true).isEmpty());
    }

    @Test
    void samePieceTargetBehindRequiresLoop() {
        List<Piece> loop = loop();
        RailGraph graph = new RailGraph(loop);
        Traversal traversal = new Traversal(loop.getFirst().id(), true);
        Cursor start = new Cursor(traversal, 3);
        Cursor target = new Cursor(traversal, 1);
        var route = RailRouter.route(graph, start, Set.of(target), (from, to) -> true).orElseThrow();
        assertEquals(4 * (2 * L + Math.sqrt(2)) - 2, route.length(), 1e-10);
        assertEquals(13, route.steps().size());
        assertEquals(traversal, route.steps().getFirst());
        assertEquals(traversal, route.steps().getLast());
        var ahead = RailRouter.route(graph, new Cursor(traversal, 1),
                Set.of(new Cursor(traversal, 3)), (from, to) -> true).orElseThrow();
        assertEquals(2, ahead.length(), 0);
        assertEquals(List.of(traversal), ahead.steps());
        var same = RailRouter.route(graph, start, Set.of(start), (from, to) -> true).orElseThrow();
        assertEquals(0, same.length(), 0);
        assertTrue(RailRouter.route(new RailGraph(List.of(loop.getFirst())), start, Set.of(target), (from, to) -> true).isEmpty());
    }

    private static List<Piece> loop() {
        List<Piece> result = new ArrayList<>();
        int[][] translations = {{0, 0}, {12, -12}, {24, 0}, {12, 12}};
        long id = 1;
        for (int q = 0; q < 4; q++) {
            for (Piece source : symmetry(quarter(1), q, false)) {
                Point origin = source.origin();
                result.add(new Piece(new UUID(0, id++),
                        new Point(origin.x() + translations[q][0], 0, origin.z() + translations[q][1]),
                        source.shape(), source.quarterTurns(), source.mirrored()));
            }
        }
        return result;
    }

    @Test
    void equalCostIsDeterministic() {
        Piece incoming = piece(1, 0, 0, 2, Shape.STRAIGHT, 0, false);
        Piece straight = piece(3, 0, 0, 0, Shape.STRAIGHT, 0, false);
        Piece turn = curve(2);
        List<Piece> pieces = new ArrayList<>(List.of(straight, incoming, turn));
        List<Cursor> goals = new ArrayList<>(List.of(new Cursor(new Traversal(straight.id(), true), 2),
                new Cursor(new Traversal(turn.id(), true), 2)));
        for (int i = 0; i < 4; i++) {
            Collections.reverse(pieces);
            if (i % 2 == 0) Collections.reverse(goals);
            var route = RailRouter.route(new RailGraph(pieces), new Cursor(new Traversal(incoming.id(), true), 1),
                    new LinkedHashSet<>(goals), (from, to) -> true).orElseThrow();
            assertEquals(3, route.length(), 0);
            assertEquals(List.of(new Traversal(incoming.id(), true), new Traversal(turn.id(), true)), route.steps());
        }
    }

    @Test
    void disallowedTransitionsAreNotRouted() {
        List<Piece> pieces = quarter(1);
        Cursor start = new Cursor(new Traversal(pieces.getFirst().id(), true), 0);
        Cursor goal = new Cursor(new Traversal(pieces.getLast().id(), false), L);
        assertTrue(RailRouter.route(new RailGraph(pieces), start, Set.of(goal),
                (from, to) -> !to.piece().equals(pieces.get(1).id())).isEmpty());
    }

    @Test
    void policyIsAppliedOnlyToRealTransitionsAndCanChooseAlternative() {
        Piece incoming = piece(1, 0, 0, 2, Shape.STRAIGHT, 0, false);
        Piece straight = piece(3, 0, 0, 0, Shape.STRAIGHT, 0, false);
        Piece turn = curve(2);
        RailGraph graph = new RailGraph(List.of(straight, incoming, turn));
        Cursor start = new Cursor(new Traversal(incoming.id(), true), 0.5);
        int[] calls = {0};
        var within = RailRouter.route(graph, start,
                Set.of(new Cursor(start.traversal(), 1.5)), (from, to) -> {
                    calls[0]++;
                    assertTrue(graph.next(from).contains(to));
                    return false;
                }).orElseThrow();
        assertEquals(1, within.length(), 0);
        assertEquals(0, calls[0]);
        var alternative = RailRouter.route(graph, start,
                Set.of(new Cursor(new Traversal(turn.id(), true), 1),
                        new Cursor(new Traversal(straight.id(), true), 2)), (from, to) -> {
                    assertTrue(graph.next(from).contains(to));
                    return !(from.piece().equals(incoming.id()) && to.piece().equals(turn.id()));
                }).orElseThrow();
        assertEquals(List.of(start.traversal(), new Traversal(straight.id(), true)), alternative.steps());
        assertEquals(3.5, alternative.length(), 0);
    }

    @Test
    void backwardCursorAcceptsBothBoundariesAndMeasuresFromB() {
        Piece first = curve(1);
        Traversal backward = new Traversal(first.id(), false);
        RailGraph graph = new RailGraph(List.of(first));
        assertEquals(0, RailRouter.route(graph, new Cursor(backward, 0),
                Set.of(new Cursor(backward, 0)), (from, to) -> false).orElseThrow().length(), 0);
        assertEquals(L, RailRouter.route(graph, new Cursor(backward, 0),
                Set.of(new Cursor(backward, L)), (from, to) -> false).orElseThrow().length(), 0);
        assertEquals(0, RailRouter.route(graph, new Cursor(backward, L),
                Set.of(new Cursor(backward, L)), (from, to) -> false).orElseThrow().length(), 0);
        assertTrue(RailRouter.route(graph, new Cursor(backward, L),
                Set.of(new Cursor(backward, 0)), (from, to) -> true).isEmpty());
        assertEquals(new Vec(4, 0, -7), sample(first, L).position());
        assertEquals(new Vec(1, 0, 0), sample(first, 0).position());
    }

    @Test
    void invalidCursorsAreRejectedAgainstGraph() {
        Piece piece = curve(1);
        RailGraph graph = new RailGraph(List.of(piece));
        Cursor valid = new Cursor(new Traversal(piece.id(), true), 0);
        Cursor tooFar = new Cursor(new Traversal(piece.id(), true), L + 1e-9);
        Cursor unknown = new Cursor(new Traversal(new UUID(0, 123), true), 0);
        assertThrows(IllegalArgumentException.class, () -> RailRouter.route(graph, tooFar, Set.of(valid), (from, to) -> true));
        assertThrows(IllegalArgumentException.class, () -> RailRouter.route(graph, valid, Set.of(tooFar), (from, to) -> true));
        assertThrows(IllegalArgumentException.class, () -> RailRouter.route(graph, unknown, Set.of(valid), (from, to) -> true));
        assertThrows(IllegalArgumentException.class, () -> RailRouter.route(graph, valid, Set.of(unknown), (from, to) -> true));
    }
}
