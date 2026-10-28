package dev.minetorio.core.logistics;

import dev.minetorio.core.robot.Cell;

/**
 * A roboport's logistic area: the cube of cells whose centers lie less than {@code radius} blocks
 * from the center on each axis (a 4×4 roboport's center is a block corner, so radius 25 covers
 * 50×50 cells, Factorio's logistic area).
 */
public record LogisticArea(double centerX, double centerY, double centerZ, int radius) {
    public boolean contains(Cell c) {
        return contains(c.x() + 0.5, c.y() + 0.5, c.z() + 0.5);
    }

    /** Whether a point (an entity's position) is inside. */
    public boolean contains(double x, double y, double z) {
        return Math.abs(x - centerX) < radius && Math.abs(y - centerY) < radius && Math.abs(z - centerZ) < radius;
    }

    /**
     * Whether the two areas touch or overlap (Factorio connects roboports whose logistic areas
     * touch): on every axis the gap between them is at most zero.
     */
    public boolean touches(LogisticArea o) {
        double reach = radius + o.radius + 1e-9;
        return Math.abs(centerX - o.centerX) <= reach && Math.abs(centerY - o.centerY) <= reach
                && Math.abs(centerZ - o.centerZ) <= reach;
    }

    public int minX() { return (int) Math.floor(centerX - radius); }
    public int maxX() { return (int) Math.ceil(centerX + radius) - 1; }
    public int minZ() { return (int) Math.floor(centerZ - radius); }
    public int maxZ() { return (int) Math.ceil(centerZ + radius) - 1; }
}
