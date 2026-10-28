package dev.minetorio.core.robot;

/** Straight-line robot flight. Robots ignore terrain: they cruise above what they head for. */
public final class Flight {
    private Flight() {}

    public record Vec(double x, double y, double z) {}

    public static double distance(Vec a, Vec b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** One tick from {@code from} toward {@code to}, at most {@code step} blocks; exactly {@code to} once within reach. */
    public static Vec step(Vec from, Vec to, double step) {
        double d = distance(from, to);
        if (d <= step || d == 0) return to;
        double k = step / d;
        return new Vec(from.x() + (to.x() - from.x()) * k, from.y() + (to.y() - from.y()) * k, from.z() + (to.z() - from.z()) * k);
    }

    /** The point {@code height} blocks above the top center of {@code cell}. */
    public static Vec hover(Cell cell, double height) {
        return new Vec(cell.x() + 0.5, cell.y() + 1 + height, cell.z() + 0.5);
    }

    /** Ticks to cover {@code distance} at {@code speed} blocks per tick. */
    public static int ticks(double distance, double speed) {
        return (int) Math.ceil(distance / speed - 1e-9);
    }
}
