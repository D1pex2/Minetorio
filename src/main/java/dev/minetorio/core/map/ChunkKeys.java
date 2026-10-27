package dev.minetorio.core.map;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunk and region coordinates packed into a {@code long} (x in the low 32 bits, z in the high 32
 * bits, like Minecraft's {@code ChunkPos.toLong}). A region is {@value MapRegion#SIZE}×{@value MapRegion#SIZE}
 * chunks; its key packs the region coordinates the same way.
 */
public final class ChunkKeys {
    private ChunkKeys() {}

    public static long of(int x, int z) { return (x & 0xFFFFFFFFL) | ((long) z << 32); }

    public static int x(long key) { return (int) key; }

    public static int z(long key) { return (int) (key >> 32); }

    /** Key of the region holding the chunk. */
    public static long region(long chunk) {
        return of(Math.floorDiv(x(chunk), MapRegion.SIZE), Math.floorDiv(z(chunk), MapRegion.SIZE));
    }

    /** Index of the chunk inside its region (0..{@value MapRegion#CHUNKS} - 1). */
    public static int local(long chunk) {
        return Math.floorMod(x(chunk), MapRegion.SIZE) + Math.floorMod(z(chunk), MapRegion.SIZE) * MapRegion.SIZE;
    }

    /** The chunk at {@code local} in {@code region}. */
    public static long fromLocal(long region, int local) {
        return of(x(region) * MapRegion.SIZE + local % MapRegion.SIZE, z(region) * MapRegion.SIZE + local / MapRegion.SIZE);
    }

    /** Chebyshev distance between two chunks. */
    public static int distance(long a, long b) {
        return Math.max(Math.abs(x(a) - x(b)), Math.abs(z(a) - z(b)));
    }

    /** Every chunk of the (2r+1)² square around (cx, cz), row by row. */
    public static List<Long> around(int cx, int cz, int r) {
        List<Long> out = new ArrayList<>((2 * r + 1) * (2 * r + 1));
        for (int z = cz - r; z <= cz + r; z++) {
            for (int x = cx - r; x <= cx + r; x++) out.add(of(x, z));
        }
        return out;
    }
}
