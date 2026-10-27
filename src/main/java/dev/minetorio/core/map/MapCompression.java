package dev.minetorio.core.map;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Deflate helpers with a hard cap on the inflated size (data from disk or the network is untrusted). */
public final class MapCompression {
    private MapCompression() {}

    public static byte[] deflate(byte[] raw) {
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        try {
            d.setInput(raw);
            d.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
            byte[] buf = new byte[8192];
            while (!d.finished()) out.write(buf, 0, d.deflate(buf));
            return out.toByteArray();
        } finally {
            d.end();
        }
    }

    /** Inflates {@code data}; throws if it is malformed or inflates to more than {@code maxSize} bytes. */
    public static byte[] inflate(byte[] data, int maxSize) throws IOException {
        Inflater inf = new Inflater();
        try {
            inf.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) throw new IOException("truncated map data");
                if (out.size() + n > maxSize) throw new IOException("map data larger than " + maxSize + " bytes");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new IOException("malformed map data", e);
        } finally {
            inf.end();
        }
    }
}
