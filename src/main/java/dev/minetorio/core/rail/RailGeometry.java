package dev.minetorio.core.rail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class RailGeometry {
    // S10: boskid, геометрия Factorio 1.1.x, 15.10.2023 17:45.
    // https://forums.factorio.com/viewtopic.php?t=109189&start=0
    private static final double CURVE_START = 0.14405836993571697;
    private static final double CURVE_ARC = 7.3113238341979292;
    private static final double CURVE_END = 0.38669902096136721;
    private static final double CURVE_RADIUS = 9.3090666300655158;
    private static final double CURVE_LENGTH = 7.842081225095013;
    private static final double HALF_WIDTH = 0.6;
    private static final double MAX_CHORD_ERROR = 1e-5;
    private static final double ARC_X = 1 + CURVE_RADIUS * (1 - Math.cos(CURVE_ARC / CURVE_RADIUS));
    private static final double ARC_Z = -CURVE_START - CURVE_RADIUS * Math.sin(CURVE_ARC / CURVE_RADIUS);

    private RailGeometry() {}

    public record Point(int x, int y, int z) {}
    public record Vec(double x, double y, double z) {}
    public record Pose(Vec position, Vec tangent) {}
    public enum Shape { STRAIGHT, DIAGONAL, CURVE }
    public record Piece(UUID id, Point origin, Shape shape, int quarterTurns, boolean mirrored) {
        public Piece {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(shape, "shape");
            if ((origin.x() & 1) != 0 || (origin.z() & 1) != 0) {
                throw new IllegalArgumentException("Начало рельса должно лежать на четной сетке X/Z");
            }
            quarterTurns = Math.floorMod(quarterTurns, 4);
        }
    }
    public enum End { A, B }
    public record Port(UUID piece, End end) {
        public Port {
            Objects.requireNonNull(piece, "piece");
            Objects.requireNonNull(end, "end");
        }
    }

    public record Traversal(UUID piece, boolean forward) {
        public Traversal {
            Objects.requireNonNull(piece, "piece");
        }
    }

    /** Расстояние от входа: от A для forward, от B для обратного движения. */
    public record Cursor(Traversal traversal, double distance) {
        public Cursor {
            Objects.requireNonNull(traversal, "traversal");
            if (!Double.isFinite(distance) || distance < 0) {
                throw new IllegalArgumentException("Расстояние должно быть конечным и неотрицательным");
            }
        }
    }

    public static List<Port> ports(Piece piece) {
        return List.of(new Port(piece.id(), End.A), new Port(piece.id(), End.B));
    }

    public static Point endpoint(Piece piece, End end) {
        Objects.requireNonNull(end, "end");
        Point local = switch (piece.shape()) {
            case STRAIGHT -> end == End.A ? new Point(1, 0, 0) : new Point(1, 0, -2);
            case DIAGONAL -> end == End.A ? new Point(0, 0, 1) : new Point(1, 0, 0);
            case CURVE -> end == End.A ? new Point(1, 0, 0) : new Point(4, 0, -7);
        };
        Point transformed = orient(local, piece.quarterTurns(), piece.mirrored());
        return new Point(piece.origin().x() + transformed.x(), piece.origin().y(),
                piece.origin().z() + transformed.z());
    }

    public static RailDir outward(Piece piece, End end) {
        Objects.requireNonNull(end, "end");
        RailDir tangent = piece.shape() == Shape.DIAGONAL || (piece.shape() == Shape.CURVE && end == End.B)
                ? RailDir.NE : RailDir.N;
        if (end == End.A) tangent = tangent.opposite();
        if (piece.mirrored()) tangent = tangent.mirrorX();
        return tangent.rotateQuarter(piece.quarterTurns());
    }

    public static double length(Piece piece) {
        return switch (piece.shape()) {
            case STRAIGHT -> 2;
            case DIAGONAL -> Math.sqrt(2);
            case CURVE -> CURVE_LENGTH;
        };
    }

    /** Геометрическое расстояние всегда отсчитывается от A. */
    public static Pose sample(Piece piece, double distance) {
        double total = length(piece);
        if (!Double.isFinite(distance) || distance < 0 || distance > total) {
            throw new IllegalArgumentException("Расстояние находится за пределами рельса");
        }
        double x;
        double z;
        double tx;
        double tz;
        if (piece.shape() == Shape.STRAIGHT) {
            x = 1;
            z = -distance;
            tx = 0;
            tz = -1;
        } else if (piece.shape() == Shape.DIAGONAL) {
            x = distance / total;
            z = 1 - x;
            tx = Math.sqrt(0.5);
            tz = -tx;
        } else if (distance <= CURVE_START) {
            x = 1;
            z = -distance;
            tx = 0;
            tz = -1;
        } else if (distance <= CURVE_START + CURVE_ARC) {
            double theta = (distance - CURVE_START) / CURVE_RADIUS;
            x = 1 + CURVE_RADIUS * (1 - Math.cos(theta));
            z = -CURVE_START - CURVE_RADIUS * Math.sin(theta);
            tx = Math.sin(theta);
            tz = -Math.cos(theta);
        } else {
            // В оригинале position function диагонального конца имеет дефект около 0.02.
            // Здесь последний участок прямой и приходит точно в B; длина остается S10.
            double u = (distance - CURVE_START - CURVE_ARC) / CURVE_END;
            x = ARC_X + (4 - ARC_X) * u;
            z = ARC_Z + (-7 - ARC_Z) * u;
            double norm = Math.hypot(4 - ARC_X, -7 - ARC_Z);
            tx = (4 - ARC_X) / norm;
            tz = (-7 - ARC_Z) / norm;
        }
        Vec local = orient(new Vec(x, 0, z), piece.quarterTurns(), piece.mirrored());
        Vec position = new Vec(local.x() + piece.origin().x(), piece.origin().y(), local.z() + piece.origin().z());
        // Граничные координаты целые: не передаем в граф погрешность формул S10.
        if (distance == 0 || distance == total) {
            Point exact = endpoint(piece, distance == 0 ? End.A : End.B);
            position = new Vec(exact.x(), exact.y(), exact.z());
        }
        return new Pose(position, orient(new Vec(tx, 0, tz), piece.quarterTurns(), piece.mirrored()));
    }

    public static Set<Point> cells(Piece piece) {
        List<Vec> line = centerline(piece);
        Set<Point> result = new LinkedHashSet<>();
        for (int i = 1; i < line.size(); i++) {
            Vec from = line.get(i - 1);
            Vec to = line.get(i);
            int minX = (int) Math.floor(Math.min(from.x(), to.x()) - HALF_WIDTH);
            int maxX = (int) Math.floor(Math.max(from.x(), to.x()) + HALF_WIDTH);
            int minZ = (int) Math.floor(Math.min(from.z(), to.z()) - HALF_WIDTH);
            int maxZ = (int) Math.floor(Math.max(from.z(), to.z()) + HALF_WIDTH);
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                if (segmentSquareDistanceSquared(from, to, x, z) <= HALF_WIDTH * HALF_WIDTH) {
                    result.add(new Point(x, piece.origin().y(), z));
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public static Piece transform(Piece piece, int turns, boolean mirrored) {
        int q = Math.floorMod(turns, 4);
        Point origin = orient(piece.origin(), q, mirrored);
        int composed = q + (mirrored ? -piece.quarterTurns() : piece.quarterTurns());
        return new Piece(piece.id(), origin, piece.shape(), composed, piece.mirrored() != mirrored);
    }

    public static boolean sameTrack(Piece first, Piece second) {
        if (first.shape() != second.shape()) return false;
        return samePort(first, End.A, second, End.A) && samePort(first, End.B, second, End.B)
                || samePort(first, End.A, second, End.B) && samePort(first, End.B, second, End.A);
    }

    private static boolean samePort(Piece first, End firstEnd, Piece second, End secondEnd) {
        return endpoint(first, firstEnd).equals(endpoint(second, secondEnd))
                && outward(first, firstEnd) == outward(second, secondEnd);
    }

    private static Point orient(Point point, int turns, boolean mirror) {
        int x = mirror ? -point.x() : point.x();
        int z = point.z();
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            int nextX = -z;
            z = x;
            x = nextX;
        }
        return new Point(x, point.y(), z);
    }

    private static Vec orient(Vec vec, int turns, boolean mirror) {
        double x = mirror ? -vec.x() : vec.x();
        double z = vec.z();
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            double nextX = -z;
            z = x;
            x = nextX;
        }
        return new Vec(x, vec.y(), z);
    }

    static List<Vec> centerline(Piece piece) {
        if (piece.shape() != Shape.CURVE) {
            return List.of(sample(piece, 0).position(), sample(piece, length(piece)).position());
        }
        // Sagitta = r*(1-cos(angle/2)); отклонение каждой хорды не более 1e-5.
        double maxAngle = 2 * Math.acos(1 - MAX_CHORD_ERROR / CURVE_RADIUS);
        int sections = (int) Math.ceil(CURVE_ARC / CURVE_RADIUS / maxAngle);
        List<Vec> result = new ArrayList<>(sections + 4);
        result.add(sample(piece, 0).position());
        result.add(sample(piece, CURVE_START).position());
        for (int i = 1; i <= sections; i++) {
            result.add(sample(piece, CURVE_START + CURVE_ARC * i / sections).position());
        }
        result.add(sample(piece, CURVE_LENGTH).position());
        return List.copyOf(result);
    }

    /** Полоса - объединение капсул радиуса 0.6, включая круглые торцы. */
    static boolean stripsOverlap(List<Vec> first, List<Vec> second) {
        for (int i = 1; i < first.size(); i++) for (int j = 1; j < second.size(); j++) {
            if (segmentDistanceSquared(first.get(i - 1), first.get(i), second.get(j - 1), second.get(j))
                    <= 4 * HALF_WIDTH * HALF_WIDTH) return true;
        }
        return false;
    }

    private static double segmentSquareDistanceSquared(Vec from, Vec to, int x, int z) {
        if (inside(from, x, z) || inside(to, x, z)) return 0;
        Vec a = new Vec(x, 0, z);
        Vec b = new Vec(x + 1, 0, z);
        Vec c = new Vec(x + 1, 0, z + 1);
        Vec d = new Vec(x, 0, z + 1);
        return Math.min(Math.min(segmentDistanceSquared(from, to, a, b), segmentDistanceSquared(from, to, b, c)),
                Math.min(segmentDistanceSquared(from, to, c, d), segmentDistanceSquared(from, to, d, a)));
    }

    private static boolean inside(Vec point, int x, int z) {
        return point.x() >= x && point.x() <= x + 1 && point.z() >= z && point.z() <= z + 1;
    }

    private static double segmentDistanceSquared(Vec a, Vec b, Vec c, Vec d) {
        double abC = cross(a, b, c);
        double abD = cross(a, b, d);
        double cdA = cross(c, d, a);
        double cdB = cross(c, d, b);
        if (abC * abD <= 0 && cdA * cdB <= 0
                && Math.max(Math.min(a.x(), b.x()), Math.min(c.x(), d.x()))
                <= Math.min(Math.max(a.x(), b.x()), Math.max(c.x(), d.x()))
                && Math.max(Math.min(a.z(), b.z()), Math.min(c.z(), d.z()))
                <= Math.min(Math.max(a.z(), b.z()), Math.max(c.z(), d.z()))) return 0;
        return Math.min(Math.min(pointSegmentDistanceSquared(a, c, d), pointSegmentDistanceSquared(b, c, d)),
                Math.min(pointSegmentDistanceSquared(c, a, b), pointSegmentDistanceSquared(d, a, b)));
    }

    private static double cross(Vec a, Vec b, Vec c) {
        return (b.x() - a.x()) * (c.z() - a.z()) - (b.z() - a.z()) * (c.x() - a.x());
    }

    private static double pointSegmentDistanceSquared(Vec point, Vec from, Vec to) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        double squared = dx * dx + dz * dz;
        double t = squared == 0 ? 0 : Math.max(0, Math.min(1,
                ((point.x() - from.x()) * dx + (point.z() - from.z()) * dz) / squared));
        double x = point.x() - from.x() - t * dx;
        double z = point.z() - from.z() - t * dz;
        return x * x + z * z;
    }
}
