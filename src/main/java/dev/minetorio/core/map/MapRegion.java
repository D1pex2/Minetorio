package dev.minetorio.core.map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

/**
 * The map colors of a {@value #SIZE}×{@value #SIZE}-chunk region: one {@value MapPalette#COLUMNS}-byte
 * array per chunk that has been scanned (null otherwise). Stored on disk deflated
 * ({@link #encode}/{@link #decode}); a region only takes memory for its scanned chunks.
 */
public final class MapRegion {
    public static final int SIZE = 32;
    public static final int CHUNKS = SIZE * SIZE;
    private static final int MAGIC = 0x4D54524D; // "MTRM"
    private static final int VERSION = 1;
    /** Largest inflated encoding: header plus every chunk. */
    public static final int MAX_ENCODED_RAW = 12 + CHUNKS * (2 + MapPalette.COLUMNS);

    private final byte[][] chunks = new byte[CHUNKS][];
    private int count;
    private boolean dirty;

    /** Colors of the chunk at {@code local}, or null if never scanned. Do not modify. */
    public byte[] get(int local) { return chunks[local]; }

    /** Stores a copy of {@code colors}; returns whether anything changed (then the region is dirty). */
    public boolean put(int local, byte[] colors) {
        if (colors.length != MapPalette.COLUMNS) throw new IllegalArgumentException("need " + MapPalette.COLUMNS + " colors");
        byte[] old = chunks[local];
        if (old != null && Arrays.equals(old, colors)) return false;
        if (old == null) count++;
        chunks[local] = colors.clone();
        dirty = true;
        return true;
    }

    /** Number of scanned chunks. */
    public int count() { return count; }

    /** Changed since the last {@link #markClean}. */
    public boolean isDirty() { return dirty; }

    public void markClean() { dirty = false; }

    /** Deflated form: magic, version, count, then (local index, colors) per scanned chunk. */
    public byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(12 + count * (2 + MapPalette.COLUMNS));
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(count);
            for (int i = 0; i < CHUNKS; i++) {
                if (chunks[i] == null) continue;
                out.writeShort(i);
                out.write(chunks[i]);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return MapCompression.deflate(bytes.toByteArray());
    }

    /** Reads {@link #encode}'s output; the result is clean. Throws on malformed or oversized data. */
    public static MapRegion decode(byte[] data) throws IOException {
        byte[] raw = MapCompression.inflate(data, MAX_ENCODED_RAW);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
            if (in.readInt() != MAGIC) throw new IOException("not a map region");
            if (in.readInt() != VERSION) throw new IOException("unknown map region version");
            int n = in.readInt();
            if (n < 0 || n > CHUNKS) throw new IOException("bad chunk count " + n);
            MapRegion r = new MapRegion();
            for (int k = 0; k < n; k++) {
                int i = in.readUnsignedShort();
                if (i >= CHUNKS) throw new IOException("bad chunk index " + i);
                byte[] colors = new byte[MapPalette.COLUMNS];
                in.readFully(colors);
                r.put(i, colors);
            }
            r.markClean();
            return r;
        }
    }
}
