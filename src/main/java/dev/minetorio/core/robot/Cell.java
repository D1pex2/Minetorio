package dev.minetorio.core.robot;

/** A block position, without Minecraft types (core stays pure). */
public record Cell(int x, int y, int z) {
    /** Squared distance between the two cells' centers. */
    public long distSq(Cell o) {
        long dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
