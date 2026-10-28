package dev.minetorio.core.area;

/** A rectangle of block columns, inclusive on both ends. */
public record GroundRect(int minX, int minZ, int maxX, int maxZ) {
    public GroundRect {
        if (maxX < minX || maxZ < minZ) throw new IllegalArgumentException("empty rect " + minX + "," + minZ + ".." + maxX + "," + maxZ);
    }

    public int width() { return maxX - minX + 1; }

    public int depth() { return maxZ - minZ + 1; }

    public boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }

    /** Whether column (x, z) is on the rectangle's border. */
    public boolean onEdge(int x, int z) {
        return contains(x, z) && (x == minX || x == maxX || z == minZ || z == maxZ);
    }
}
