package dev.minetorio.core.power;

/** Position of a pole: the lowest, smallest-x, smallest-z block of its footprint. */
public record PolePos(int x, int y, int z) implements Comparable<PolePos> {
    /** Order of Minecraft's {@code BlockPos.compareTo}: y, then z, then x. */
    @Override
    public int compareTo(PolePos o) {
        if (y != o.y) return Integer.compare(y, o.y);
        if (z != o.z) return Integer.compare(z, o.z);
        return Integer.compare(x, o.x);
    }
}
