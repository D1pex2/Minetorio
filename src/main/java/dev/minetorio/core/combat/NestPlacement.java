package dev.minetorio.core.combat;

/** Детерминированное размещение первого среза: одно поселение в сетке 8 x 8 чанков. */
public final class NestPlacement {
    public static final int SAFE_RADIUS = 320;
    private NestPlacement() {}
    public static boolean candidate(long seed, int chunkX, int chunkZ) {
        int gx = Math.floorDiv(chunkX, 8), gz = Math.floorDiv(chunkZ, 8);
        long hash = seed ^ gx * 0x9E3779B97F4A7C15L ^ gz * 0xC2B2AE3D27D4EB4FL;
        hash ^= hash >>> 30; hash *= 0xBF58476D1CE4E5B9L; hash ^= hash >>> 27;
        return Math.floorMod(chunkX, 8) == (int)(hash & 7) && Math.floorMod(chunkZ, 8) == (int)((hash >>> 3) & 7);
    }
    public static boolean outsideStart(int x, int z, int spawnX, int spawnZ) {
        return Math.hypot((double)x - spawnX, (double)z - spawnZ) >= SAFE_RADIUS;
    }
}
