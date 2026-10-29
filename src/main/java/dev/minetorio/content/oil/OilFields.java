package dev.minetorio.content.oil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Seed-only field layout. A region owns its wells so neighbouring chunks agree at their border. */
public final class OilFields {
    public static final int REGION_SIZE = 256;
    public static final int EXCLUSION_RADIUS = 256;
    public record Well(long fieldKey, int x, int z, double initialYield) {}
    private OilFields() {}

    public static List<Well> inChunk(long seed, int chunkX, int chunkZ) {
        int minX = chunkX * 16, minZ = chunkZ * 16;
        List<Well> result = new ArrayList<>();
        for (int rx = Math.floorDiv(minX - 24, REGION_SIZE); rx <= Math.floorDiv(minX + 39, REGION_SIZE); rx++) {
            for (int rz = Math.floorDiv(minZ - 24, REGION_SIZE); rz <= Math.floorDiv(minZ + 39, REGION_SIZE); rz++) {
                Random random = new Random(seed ^ (rx * 341873128712L) ^ (rz * 132897987541L) ^ 0x4f494cL);
                if (random.nextInt(4) != 0) continue;
                int cx = rx * REGION_SIZE + 24 + random.nextInt(REGION_SIZE - 48);
                int cz = rz * REGION_SIZE + 24 + random.nextInt(REGION_SIZE - 48);
                int wells = 4 + random.nextInt(5);
                List<Well> field = new ArrayList<>();
                long key = ((long) rx << 32) ^ (rz & 0xffffffffL);
                for (int i = 0; i < wells; i++) {
                    int x = cx + random.nextInt(41) - 20, z = cz + random.nextInt(41) - 20;
                    double distance = Math.hypot(x, z);
                    double yield = 1 + random.nextDouble() + distance / 5000.0;
                    if (field.stream().anyMatch(w -> Math.abs(w.x() - x) < 3 && Math.abs(w.z() - z) < 3)) continue;
                    field.add(new Well(key, x, z, yield));
                    if (x >= minX && x < minX + 16 && z >= minZ && z < minZ + 16)
                        result.add(new Well(key, x, z, yield));
                }
            }
        }
        return result;
    }

    /** Yield falls by one percentage point per 300 extraction cycles, never below 20% of initial. */
    public static double yield(double initial, long cycles) {
        return Math.max(Math.max(0.2, initial * 0.2), initial - Math.max(0, cycles) / 30000.0);
    }
}
