package dev.minetorio.core.area;

import java.util.function.IntPredicate;

/** Geometry of the ground overlays (pole supply, mining area, radar scan), pure and unit tested. */
public final class AreaMath {
    private AreaMath() {}

    /** Supply area of a pole at column (x, z): the square of {@code radius} around it. */
    public static GroundRect poleSupply(int x, int z, int radius) {
        return new GroundRect(x - radius, z - radius, x + radius, z + radius);
    }

    /**
     * Mining area of a drill: the {@code areaSize} square around the center of its footprint. The
     * footprint starts at the origin column (ox, oz) and extends {@code depth} along the facing
     * (fx, fz) and {@code width} to its clockwise side (−fz, fx). For an even/odd mismatch the
     * square grows by the floor of half the difference on each side (as the drill mines).
     */
    public static GroundRect drillArea(int ox, int oz, int fx, int fz, int width, int depth, int areaSize) {
        int cwx = -fz, cwz = fx;
        int farX = ox + fx * (depth - 1) + cwx * (width - 1);
        int farZ = oz + fz * (depth - 1) + cwz * (width - 1);
        int grow = (areaSize - Math.max(width, depth)) / 2;
        return new GroundRect(Math.min(ox, farX) - grow, Math.min(oz, farZ) - grow,
                Math.max(ox, farX) + grow, Math.max(oz, farZ) + grow);
    }

    /** Block columns of the chunks within {@code radius} chunks of chunk (cx, cz). */
    public static GroundRect chunkSquare(int cx, int cz, int radius) {
        return new GroundRect((cx - radius) << 4, (cz - radius) << 4, ((cx + radius) << 4) + 15, ((cz + radius) << 4) + 15);
    }

    /**
     * Points of a circle of {@code radius} around (cx, cz) as {x0, z0, x1, z1, ...}, closed (the
     * last point repeats the first), {@code segments} segments.
     */
    public static double[] circle(double cx, double cz, double radius, int segments) {
        if (segments < 3) throw new IllegalArgumentException("segments " + segments);
        double[] out = new double[(segments + 1) * 2];
        for (int i = 0; i <= segments; i++) {
            double a = 2 * Math.PI * (i % segments) / segments;
            out[i * 2] = cx + Math.cos(a) * radius;
            out[i * 2 + 1] = cz + Math.sin(a) * radius;
        }
        return out;
    }

    /**
     * Ground level of a column near a reference height: scans down from {@code fromY} to
     * {@code toY} (inclusive) for the first block that counts as ground and returns that y, or
     * {@code fallback} if there is none (unloaded or a deep hole).
     */
    public static int groundY(int fromY, int toY, IntPredicate isGround, int fallback) {
        for (int y = fromY; y >= toY; y--) if (isGround.test(y)) return y;
        return fallback;
    }
}
