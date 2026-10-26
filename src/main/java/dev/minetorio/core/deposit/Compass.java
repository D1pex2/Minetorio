package dev.minetorio.core.deposit;

/** Eight-way compass bearings in Minecraft's XZ plane (north = -Z, east = +X). */
public enum Compass {
    N, NE, E, SE, S, SW, W, NW;

    /** Bearing from a point to another {@code (dx, dz)} away; N for a zero offset. */
    public static Compass of(long dx, long dz) {
        if (dx == 0 && dz == 0) return N;
        double deg = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        int i = (int) Math.round(deg / 45.0);
        return values()[Math.floorMod(i, 8)];
    }
}
