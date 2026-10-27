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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import javax.annotation.Nullable;
import net.minecraft.Util;
import org.slf4j.Logger;

/**
 * The map colors on disk: one deflated file per {@link MapRegion} ({@code r.<x>.<z>.bin}) in the
 * world's {@code data/minetorio_map} folder. At most {@value #MAX_LOADED} regions are kept in memory
 * (least recently used ones are written if dirty and dropped); dirty regions are written when the
 * world saves ({@link #saveDirty}).
 *
 * <p>Regions are encoded on the server thread and the files written on Minecraft's IO pool, one
 * write after another (so an older write never lands after a newer one); until a write is done its
 * bytes are what a read of that region sees. A file that cannot be decoded is renamed to
 * {@code .bad} and reported to {@code onCorrupt} (the map then forgets its chunks, so they get
 * explored again). Server thread only, apart from the file writes.
 */
final class MapRegionStore {
    private static final Logger LOG = LogUtils.getLogger();
    /** Regions kept in memory (each at most 256 KiB, usually far less). */
    static final int MAX_LOADED = 64;

    private final Path dir;
    private final LongConsumer onCorrupt;
    private final LinkedHashMap<Long, MapRegion> loaded = new LinkedHashMap<>(16, 0.75f, true);
    /** Encoded regions whose file write has not finished yet. */
    private final Map<Long, byte[]> writing = new ConcurrentHashMap<>();
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);

    MapRegionStore(Path dir, LongConsumer onCorrupt) {
        this.dir = dir;
        this.onCorrupt = onCorrupt;
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
        byte[] data = writing.get(key);
        try {
            if (data == null) {
                if (!Files.isRegularFile(file)) return null;
                data = Files.readAllBytes(file);
            }
            return MapRegion.decode(data);
        } catch (IOException e) {
            LOG.warn("Unreadable map region {}; renamed to .bad, its chunks will be explored again", file, e);
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                LOG.warn("Could not rename {}", file, e2);
            }
            onCorrupt.accept(key);
            return null;
        }
    }

    /** Encodes now, writes on the IO pool. */
    private void write(long key, MapRegion r) {
        byte[] data = r.encode();
        r.markClean();
        writing.put(key, data);
        Path target = file(key);
        lastWrite = lastWrite.thenRunAsync(() -> {
            try {
                Files.createDirectories(dir);
                Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
                Files.write(tmp, data);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                LOG.error("Could not save map region {}", target, e);
            } finally {
                writing.remove(key, data);
            }
        }, Util.ioPool());
    }

    private Path file(long key) {
        return dir.resolve("r." + ChunkKeys.x(key) + "." + ChunkKeys.z(key) + ".bin");
    }

    /** Starts writing every changed region. */
    void saveDirty() {
        for (Map.Entry<Long, MapRegion> e : loaded.entrySet()) {
            if (e.getValue().isDirty()) write(e.getKey(), e.getValue());
        }
    }

    /** Blocks until every started write is on disk (server stop, tests). */
    void awaitWrites() {
        lastWrite.join();
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
        awaitWrites();
        loaded.clear();
        if (!Files.isDirectory(dir)) return;
        try (var files = Files.list(dir)) {
            for (Path p : (Iterable<Path>) files::iterator) Files.deleteIfExists(p);
        } catch (IOException e) {
            LOG.warn("Could not wipe map folder {}", dir, e);
        }
    }
}
