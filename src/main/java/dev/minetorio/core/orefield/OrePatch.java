package dev.minetorio.core.orefield;

/** A circular ore field in the XZ plane; each column inside holds one field block at its own surface height. */
public record OrePatch(OreType type, int centerX, int centerZ, int radius) {
    public boolean contains(int x, int z) {
        return distSq(x, z) <= (long) radius * radius;
    }

    public long distSq(int x, int z) {
        long dx = (long) x - centerX;
        long dz = (long) z - centerZ;
        return dx * dx + dz * dz;
    }

    /** Whether the patch overlaps the box [minX..maxX] × [minZ..maxZ] (inclusive). */
    public boolean touches(int minX, int minZ, int maxX, int maxZ) {
        int nx = Math.max(minX, Math.min(centerX, maxX));
        int nz = Math.max(minZ, Math.min(centerZ, maxZ));
        return contains(nx, nz);
    }
}
