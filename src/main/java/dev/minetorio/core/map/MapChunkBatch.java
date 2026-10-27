package dev.minetorio.core.map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Network form of up to {@value #MAX_CHUNKS} chunks' map colors: deflated (count, then key and
 * colors per chunk). Even incompressible data stays within {@value #MAX_BYTES} bytes.
 */
public final class MapChunkBatch {
    public static final int MAX_CHUNKS = 128;
    private static final int ENTRY = 8 + MapPalette.COLUMNS;
    private static final int MAX_RAW = 4 + MAX_CHUNKS * ENTRY;
    /** Upper bound of an encoded batch (deflate adds at most a few bytes per 16 KiB block). */
    public static final int MAX_BYTES = 40_000;

    public record Entry(long key, byte[] colors) {}

    private MapChunkBatch() {}

    public static byte[] encode(List<Entry> entries) {
        if (entries.size() > MAX_CHUNKS) throw new IllegalArgumentException("more than " + MAX_CHUNKS + " chunks");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(4 + entries.size() * ENTRY);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(entries.size());
            for (Entry e : entries) {
                if (e.colors().length != MapPalette.COLUMNS) throw new IllegalArgumentException("need " + MapPalette.COLUMNS + " colors");
                out.writeLong(e.key());
                out.write(e.colors());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return MapCompression.deflate(bytes.toByteArray());
    }

    public static List<Entry> decode(byte[] data) throws IOException {
        if (data.length > MAX_BYTES) throw new IOException("map batch of " + data.length + " bytes");
        byte[] raw = MapCompression.inflate(data, MAX_RAW);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
            int n = in.readInt();
            if (n < 0 || n > MAX_CHUNKS) throw new IOException("bad chunk count " + n);
            List<Entry> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                long key = in.readLong();
                byte[] colors = new byte[MapPalette.COLUMNS];
                in.readFully(colors);
                out.add(new Entry(key, colors));
            }
            return out;
        }
    }
}
