package dev.minetorio.content.map;

import dev.minetorio.core.map.ChunkKeys;
import dev.minetorio.core.map.MapChunkBatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The client's copy of the explored map (filled by {@link MapPayloads.Chunks}) and the other
 * players' positions ({@link MapPayloads.Players}). Plain data, no rendering. This is the source of
 * truth for the client's map; the region textures are a bounded cache rebuilt from it on demand,
 * and drain {@link #pollChanged} to update incrementally. Client thread only; reset on logout.
 */
public final class ClientMapData {
    private static final Map<Long, byte[]> CHUNKS = new HashMap<>();
    private static final LinkedHashSet<Long> CHANGED = new LinkedHashSet<>();
    /** Regions ({@code ChunkKeys.region}) with at least one explored chunk. */
    private static final Set<Long> REGIONS = new HashSet<>();
    private static List<MapPayloads.Marker> players = List.of();
    private static int generation;

    private ClientMapData() {}

    static void accept(List<MapChunkBatch.Entry> entries) {
        for (MapChunkBatch.Entry e : entries) {
            CHUNKS.put(e.key(), e.colors());
            CHANGED.add(e.key());
            REGIONS.add(ChunkKeys.region(e.key()));
        }
    }

    static void setPlayers(List<MapPayloads.Marker> list) { players = list; }

    /** Colors of an explored chunk ({@code MapPalette}), or null if unexplored. */
    @Nullable
    public static byte[] chunk(long key) { return CHUNKS.get(key); }

    /** Map color byte of the column at a block position, or -1 if unexplored. */
    public static int colorAt(int blockX, int blockZ) {
        byte[] c = CHUNKS.get(ChunkKeys.of(blockX >> 4, blockZ >> 4));
        return c == null ? -1 : c[(blockX & 15) + (blockZ & 15) * 16] & 0xFF;
    }

    public static boolean isExplored(int chunkX, int chunkZ) { return CHUNKS.containsKey(ChunkKeys.of(chunkX, chunkZ)); }

    /** Whether the region has any explored chunk. */
    public static boolean hasRegion(long region) { return REGIONS.contains(region); }

    public static int exploredCount() { return CHUNKS.size(); }

    /** Removes and returns up to {@code max} chunks received since the last call, oldest first. */
    public static List<Long> pollChanged(int max) {
        List<Long> out = new ArrayList<>(Math.min(max, CHANGED.size()));
        Iterator<Long> it = CHANGED.iterator();
        while (it.hasNext() && out.size() < max) {
            out.add(it.next());
            it.remove();
        }
        return out;
    }

    public static List<MapPayloads.Marker> players() { return players; }

    /** Bumped by {@link #reset}: textures built from older data must be dropped. */
    public static int generation() { return generation; }

    /** Forgets everything (on leaving a world). */
    public static void reset() {
        CHUNKS.clear();
        CHANGED.clear();
        REGIONS.clear();
        players = List.of();
        generation++;
    }
}
