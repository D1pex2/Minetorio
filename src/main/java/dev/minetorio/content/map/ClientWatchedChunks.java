package dev.minetorio.content.map;

import dev.minetorio.core.map.ChunkKeys;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The client's copy of the chunks watched by working radars ({@link WatchedChunks}, filled by
 * {@link MapPayloads.Watched}). Plain data; the full map draws radar coverage from it. Client
 * thread only; reset on logout.
 */
public final class ClientWatchedChunks {
    private static final Set<Long> CHUNKS = new HashSet<>();

    private ClientWatchedChunks() {}

    static void accept(boolean reset, long[] added, long[] removed) {
        if (reset) CHUNKS.clear();
        for (long k : added) CHUNKS.add(k);
        for (long k : removed) CHUNKS.remove(k);
    }

    public static boolean isWatched(int chunkX, int chunkZ) { return CHUNKS.contains(ChunkKeys.of(chunkX, chunkZ)); }

    public static boolean isWatched(long chunk) { return CHUNKS.contains(chunk); }

    /** Read-only view of the watched chunks. */
    public static Set<Long> chunks() { return Collections.unmodifiableSet(CHUNKS); }

    public static int count() { return CHUNKS.size(); }

    /** Forgets everything (on leaving a world). */
    public static void reset() { CHUNKS.clear(); }
}
