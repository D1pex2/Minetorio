package dev.minetorio.core.rail;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.minetorio.core.rail.RailGeometry.*;
import static org.junit.jupiter.api.Assertions.*;

class RailGeometryTest {
    static final double A = 0.14405836993571697;
    static final double B = 7.3113238341979292;
    static final double C = 0.38669902096136721;
    static final double R = 9.3090666300655158;
    static final double L = 7.842081225095013;

    static Piece piece(long id, int x, int y, int z, Shape shape, int q, boolean mirror) {
        return new Piece(new UUID(0, id), new Point(x, y, z), shape, q, mirror);
    }

    static Piece curve(long id) {
        return piece(id, 0, 0, 0, Shape.CURVE, 0, false);
    }

    static List<Piece> sBend(int diagonals) {
        List<Piece> result = new ArrayList<>();
        result.add(curve(1));
        addDiagonals(result, diagonals);
        result.add(piece(100, 8 + diagonals, 0, -14 - diagonals, Shape.CURVE, 2, false));
        return result;
    }

    static List<Piece> quarter(int diagonals) {
        List<Piece> result = new ArrayList<>();
        result.add(curve(1));
        addDiagonals(result, diagonals);
        result.add(piece(100, 11 + diagonals, 0, -11 - diagonals, Shape.CURVE, 3, true));
        return result;
    }

    private static void addDiagonals(List<Piece> result, int count) {
        for (int i = 0; i < count; i++) {
            int x = 4 + i;
            int z = -7 - i;
            result.add(i % 2 == 0
                    ? piece(10 + i, x, 0, z - 1, Shape.DIAGONAL, 0, false)
                    : piece(10 + i, x + 1, 0, z, Shape.DIAGONAL, 2, false));
        }
    }

    static List<Piece> symmetry(List<Piece> pieces, int q, boolean mirrored) {
        return pieces.stream().map(p -> transform(p, q, mirrored)).toList();
    }

    static Point symmetry(Point point, int q, boolean mirrored) {
        int x = mirrored ? -point.x() : point.x();
        int z = point.z();
        for (int i = 0; i < q; i++) {
            int nextX = -z;
            z = x;
            x = nextX;
        }
        return new Point(x, point.y(), z);
    }

    @Test
    void curveThreeSectionsMatchPublishedLengths() {
        Piece curve = curve(1);
        assertEquals(2, length(piece(2, 0, 0, 0, Shape.STRAIGHT, 0, false)), 0);
        assertEquals(Math.sqrt(2), length(piece(3, 0, 0, 0, Shape.DIAGONAL, 0, false)), 0);
        assertEquals(L, length(curve), 1e-12);
        assertPose(sample(curve, 0), 1, 0, 0, -1);
        assertPose(sample(curve, A / 2), 1, -A / 2, 0, -1);
        assertPose(sample(curve, A), 1, -A, 0, -1);
        double angle = B / (2 * R);
        assertPose(sample(curve, A + B / 2), 1 + R * (1 - Math.cos(angle)),
                -A - R * Math.sin(angle), Math.sin(angle), -Math.cos(angle));
        double arcX = 1 + R * (1 - Math.cos(B / R));
        double arcZ = -A - R * Math.sin(B / R);
        assertPose(sample(curve, A + B), arcX, arcZ, Math.sqrt(0.5), -Math.sqrt(0.5));
        assertPose(sample(curve, A + B + C / 2), (arcX + 4) / 2, (arcZ - 7) / 2,
                Math.sqrt(0.5), -Math.sqrt(0.5));
        assertEquals(new Vec(1, 0, 0), sample(curve, 0).position());
        assertEquals(new Vec(4, 0, -7), sample(curve, L).position());
        assertEquals(new Point(1, 0, 0), endpoint(curve, End.A));
        assertEquals(new Point(4, 0, -7), endpoint(curve, End.B));
        assertEquals(RailDir.S, outward(curve, End.A));
        assertEquals(RailDir.NE, outward(curve, End.B));
        assertEquals(List.of(new Port(curve.id(), End.A), new Port(curve.id(), End.B)), ports(curve));
    }

    private static void assertPose(Pose pose, double x, double z, double tx, double tz) {
        assertEquals(x, pose.position().x(), 1e-12);
        assertEquals(0, pose.position().y(), 0);
        assertEquals(z, pose.position().z(), 1e-12);
        assertEquals(tx, pose.tangent().x(), 1e-7);
        assertEquals(0, pose.tangent().y(), 0);
        assertEquals(tz, pose.tangent().z(), 1e-7);
    }

    @Test
    void curveSamplesHaveContinuousTangent() {
        for (double s : new double[]{A, A + B}) {
            Pose left = sample(curve(1), s - 1e-8);
            Pose right = sample(curve(1), s + 1e-8);
            assertEquals(left.position().x(), right.position().x(), 3e-8);
            assertEquals(left.position().z(), right.position().z(), 3e-8);
            assertEquals(left.tangent().x(), right.tangent().x(), 1e-7);
            assertEquals(left.tangent().z(), right.tangent().z(), 1e-7);
        }
    }

    @Test
    void rotatedAndMirroredCurveHasSameLength() {
        Piece original = piece(1, -8, 12, 14, Shape.CURVE, 1, true);
        for (int q = 0; q < 4; q++) {
            for (boolean mirror : new boolean[]{false, true}) {
                Piece changed = transform(original, q, mirror);
                assertEquals(L, length(changed), 1e-12);
                assertEquals(symmetry(endpoint(original, End.A), q, mirror), endpoint(changed, End.A));
                assertEquals(symmetry(endpoint(original, End.B), q, mirror), endpoint(changed, End.B));
                assertEquals(original.id(), changed.id());
                assertEquals(outward(original, End.B).mirrorX().rotateQuarter(q),
                        outward(transform(original, q, true), End.B));
                for (double distance : new double[]{0, A / 2, A + B / 2, A + B + C / 2, L}) {
                    Pose source = sample(original, distance);
                    Pose destination = sample(changed, distance);
                    double x = mirror ? -source.position().x() : source.position().x();
                    double z = source.position().z();
                    double tx = mirror ? -source.tangent().x() : source.tangent().x();
                    double tz = source.tangent().z();
                    for (int turn = 0; turn < q; turn++) {
                        double nextX = -z;
                        z = x;
                        x = nextX;
                        double nextTx = -tz;
                        tz = tx;
                        tx = nextTx;
                    }
                    assertEquals(x, destination.position().x(), 1e-12);
                    assertEquals(z, destination.position().z(), 1e-12);
                    assertEquals(tx, destination.tangent().x(), 1e-12);
                    assertEquals(tz, destination.tangent().z(), 1e-12);
                }
            }
        }
        Piece rotated = original;
        for (int q = 0; q < 4; q++) rotated = transform(rotated, 1, false);
        assertEquals(original, rotated);
        assertEquals(original, transform(transform(original, 0, true), 0, true));
    }

    @Test
    void minimumSOffsetIsSix() {
        for (int k = 0; k <= 5; k++) {
            final int count = k;
            if (k % 2 != 0) {
                assertThrows(IllegalArgumentException.class, () -> sBend(count));
                continue;
            }
            List<Piece> fixture = sBend(k);
            for (int q = 0; q < 4; q++) {
                for (boolean mirrored : new boolean[]{false, true}) {
                    List<Piece> pieces = symmetry(fixture, q, mirrored);
                    assertEquals(symmetry(new Point(1, 0, 0), q, mirrored), endpoint(pieces.getFirst(), End.A));
                    assertEquals(symmetry(new Point(7 + k, 0, -14 - k), q, mirrored),
                            endpoint(pieces.getLast(), End.A));
                    assertFixtureRoute(pieces, 2 * L + k * Math.sqrt(2));
                }
            }
        }
        // Две кривые со сдвигом 2/4 не стыкуются в минимальную S-конструкцию.
        for (int offset : new int[]{2, 4}) {
            List<Piece> wrong = List.of(curve(1), piece(2, 2 + offset, 0, -14, Shape.CURVE, 2, false));
            for (int q = 0; q < 4; q++) for (boolean mirror : new boolean[]{false, true}) {
                List<Piece> pieces = symmetry(wrong, q, mirror);
                assertTrue(new RailGraph(pieces).next(new Traversal(pieces.getFirst().id(), true)).isEmpty());
            }
        }
    }

    @Test
    void quarterTurnRequiresOddDiagonalCount() {
        for (int k = 0; k <= 5; k++) {
            final int count = k;
            if (k % 2 == 0) {
                assertThrows(IllegalArgumentException.class, () -> quarter(count));
                continue;
            }
            List<Piece> fixture = quarter(k);
            for (int q = 0; q < 4; q++) {
                for (boolean mirrored : new boolean[]{false, true}) {
                    List<Piece> pieces = symmetry(fixture, q, mirrored);
                    assertEquals(symmetry(new Point(11 + k, 0, -10 - k), q, mirrored),
                            endpoint(pieces.getLast(), End.A));
                    assertFixtureRoute(pieces, 2 * L + k * Math.sqrt(2));
                }
            }
        }
    }

    private static void assertFixtureRoute(List<Piece> pieces, double expected) {
        RailGraph graph = new RailGraph(pieces);
        var route = RailRouter.route(graph, new Cursor(new Traversal(pieces.getFirst().id(), true), 0),
                Set.of(new Cursor(new Traversal(pieces.getLast().id(), false), L)), (from, to) -> true).orElseThrow();
        assertEquals(expected, route.length(), 1e-11);
        assertEquals(pieces.size(), route.steps().size());
        assertEquals(new Traversal(pieces.getLast().id(), false), route.steps().getLast());
    }

    @Test
    void diagonalHalvesAlternateWithoutGaps() {
        List<Piece> pieces = new ArrayList<>();
        addDiagonals(pieces, 6);
        for (int q = 0; q < 4; q++) for (boolean mirror : new boolean[]{false, true}) {
            List<Piece> changed = symmetry(pieces, q, mirror);
            RailGraph graph = new RailGraph(changed);
            for (int i = 0; i < 5; i++) {
                boolean forward = i % 2 == 0;
                assertEquals(endpoint(changed.get(i), forward ? End.B : End.A),
                        endpoint(changed.get(i + 1), forward ? End.B : End.A));
                assertEquals(List.of(new Traversal(changed.get(i + 1).id(), !forward)),
                        graph.next(new Traversal(changed.get(i).id(), forward)));
            }
        }
    }

    @Test
    void oddAnchorIsRejected() {
        for (int x : new int[]{-3, -1, 1, 3}) {
            assertThrows(IllegalArgumentException.class, () -> piece(1, x, 0, 0, Shape.STRAIGHT, 0, false));
            assertThrows(IllegalArgumentException.class, () -> piece(1, 0, 0, x, Shape.STRAIGHT, 0, false));
        }
        assertDoesNotThrow(() -> piece(1, -4, -3, -8, Shape.CURVE, -5, true));
        assertEquals(3, piece(1, 0, 0, 0, Shape.CURVE, -5, false).quarterTurns());
    }

    @Test
    void reversedEquivalentStraightIsSameTrack() {
        Piece original = piece(1, 0, 0, 0, Shape.STRAIGHT, 0, false);
        Piece reversed = piece(2, 2, 0, -2, Shape.STRAIGHT, 2, false);
        assertTrue(sameTrack(original, reversed));
        assertTrue(sameTrack(original, piece(3, 2, 0, 0, Shape.STRAIGHT, 0, true)));
        assertFalse(sameTrack(original, piece(3, 0, 1, 0, Shape.STRAIGHT, 0, false)));
        assertFalse(sameTrack(original, curve(3)));
        assertThrows(IllegalArgumentException.class, () -> new RailGraph(List.of(original, reversed)));
        assertThrows(IllegalArgumentException.class, () -> new RailGraph(List.of(original, original)));
    }

    @Test
    void distanceBoundsAndNonfiniteValuesAreRejected() {
        for (Shape shape : Shape.values()) {
            Piece piece = piece(1, -2, 3, -6, shape, 2, true);
            for (double invalid : new double[]{-1e-9, length(piece) + 1e-9, Double.NaN,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
                assertThrows(IllegalArgumentException.class, () -> sample(piece, invalid));
            }
            assertDoesNotThrow(() -> sample(piece, 0));
            assertDoesNotThrow(() -> sample(piece, length(piece)));
        }
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new Cursor(new Traversal(new UUID(0, 1), true), invalid));
        }
    }

    @Test
    void cellsCoverActualStripAtItsHeight() {
        Piece straight = piece(1, 0, 5, 0, Shape.STRAIGHT, 0, false);
        Set<Point> cells = cells(straight);
        assertTrue(cells.contains(new Point(0, 5, -1)));
        assertTrue(cells.contains(new Point(1, 5, -1)));
        assertFalse(cells.contains(new Point(2, 5, -1)));
        assertFalse(cells.contains(new Point(0, 4, -1)));
        assertTrue(cells(curve(2)).contains(new Point(3, 0, -7)));
        assertFalse(cells(curve(2)).contains(new Point(0, 0, -7)));
        assertThrows(UnsupportedOperationException.class, () -> cells.add(new Point(100, 5, 100)));
    }
}
