package dev.minetorio.content.map;

import com.mojang.logging.LogUtils;
import dev.minetorio.core.map.ChunkKeys;
import dev.minetorio.core.map.MapRegion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * The map colors on disk: one deflated file per {@link MapRegion} ({@code r.<x>.<z>.bin}) in the
 * world's {@code data/minetorio_map} folder. At most {@value #MAX_LOADED} regions are kept in memory
 * (least recently used ones are written if dirty and dropped); dirty regions are written when the
 * world saves ({@link #saveDirty}). Server thread only.
 */
final class MapRegionStore {
    private static final Logger LOG = LogUtils.getLogger();
    /** Regions kept in memory (each at most 256 KiB, usually far less). */
    static final int MAX_LOADED = 64;

    private final Path dir;
    private final LinkedHashMap<Long, MapRegion> loaded = new LinkedHashMap<>(16, 0.75f, true);

    MapRegionStore(Path dir) {
        this.dir = dir;
    }

    /** Colors of a chunk, or null if never scanned. Do not modify. */
    @Nullable
    byte[] colors(long chunk) {
        MapRegion r = region(ChunkKeys.region(chunk), false);
        return r == null ? null : r.get(ChunkKeys.local(chunk));
    }

    /** Stores a chunk's colors; returns whether they changed. */
    boolean put(long chunk, byte[] colors) {
        return region(ChunkKeys.region(chunk), true).put(ChunkKeys.local(chunk), colors);
    }

    @Nullable
    private MapRegion region(long key, boolean create) {
        MapRegion r = loaded.get(key);
        if (r != null) return r;
        r = read(key);
        if (r == null && !create) return null;
        if (r == null) r = new MapRegion();
        loaded.put(key, r);
        evict();
        return r;
    }

    @Nullable
    private MapRegion read(long key) {
        Path file = file(key);
        if (!Files.isRegularFile(file)) return null;
        try {
            return MapRegion.decode(Files.readAllBytes(file));
        } catch (IOException e) {
            LOG.warn("Unreadable map region {}, starting it over", file, e);
            return null;
        }
    }

    private void write(long key, MapRegion r) {
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(file(key).getFileName() + ".tmp");
            Files.write(tmp, r.encode());
            Files.move(tmp, file(key), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            r.markClean();
        } catch (IOException e) {
            LOG.error("Could not save map region {}", file(key), e);
        }
    }

    private Path file(long key) {
        return dir.resolve("r." + ChunkKeys.x(key) + "." + ChunkKeys.z(key) + ".bin");
    }

    /** Writes every changed region. */
    void saveDirty() {
        for (Map.Entry<Long, MapRegion> e : loaded.entrySet()) {
            if (e.getValue().isDirty()) write(e.getKey(), e.getValue());
        }
    }

    private void evict() {
        for (Iterator<Map.Entry<Long, MapRegion>> it = loaded.entrySet().iterator(); loaded.size() > MAX_LOADED && it.hasNext(); ) {
            Map.Entry<Long, MapRegion> e = it.next();
            if (e.getValue().isDirty()) write(e.getKey(), e.getValue());
            it.remove();
        }
    }

    int loadedRegions() { return loaded.size(); }

    /** Forgets every region in memory and deletes the files (GameTests). */
    void wipe() {
        loaded.clear();
        if (!Files.isDirectory(dir)) return;
        try (var files = Files.list(dir)) {
            for (Path p : (Iterable<Path>) files::iterator) Files.deleteIfExists(p);
        } catch (IOException e) {
            LOG.warn("Could not wipe map folder {}", dir, e);
        }
    }
}
