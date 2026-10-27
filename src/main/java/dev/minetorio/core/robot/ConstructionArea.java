package dev.minetorio.core.robot;

/**
 * A roboport's construction area: the cube of cells whose centers lie less than {@code radius}
 * blocks from the center on each axis (a 4×4 roboport's center is a block corner, so radius 27
 * covers 54×54 cells).
 */
public record ConstructionArea(double centerX, double centerY, double centerZ, int radius) {
    public boolean contains(Cell c) {
        return Math.abs(c.x() + 0.5 - centerX) < radius
                && Math.abs(c.y() + 0.5 - centerY) < radius
                && Math.abs(c.z() + 0.5 - centerZ) < radius;
    }

    /** Cells across (for an integer center, as with an even footprint). */
    public int width() { return 2 * radius; }

    public int minX() { return (int) Math.floor(centerX - radius); }
    public int maxX() { return (int) Math.ceil(centerX + radius) - 1; }
    public int minY() { return (int) Math.floor(centerY - radius); }
    public int maxY() { return (int) Math.ceil(centerY + radius) - 1; }
    public int minZ() { return (int) Math.floor(centerZ - radius); }
    public int maxZ() { return (int) Math.ceil(centerZ + radius) - 1; }
}
