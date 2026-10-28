package dev.minetorio.core.blueprint;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The blueprint string (Minetorio's own, not Factorio's): a version character, then the record's
 * bytes (binary NBT, written by the content layer) deflated and Base64-encoded. Version
 * {@value #VERSION} is the only one so far. Whitespace inside a pasted string is ignored.
 *
 * <p>The same deflated bytes are what a client uploads when it hands the server a blueprint (from
 * the library or a string): {@link #deflate} / {@link #inflate} with their size limits, so a short
 * string can never inflate into something huge.
 */
public final class BlueprintStrings {
    /** The format version: the string's first character. */
    public static final char VERSION = '0';
    /** Largest deflated payload accepted, in bytes. */
    public static final int MAX_COMPRESSED_BYTES = 256 * 1024;
    /** Largest inflated payload accepted, in bytes. */
    public static final int MAX_RAW_BYTES = 2 * BlueprintBooks.MAX_RECORD_BYTES;
    /** Longest string accepted (Base64 of the largest payload, plus the version and some whitespace). */
    public static final int MAX_STRING_LENGTH = (MAX_COMPRESSED_BYTES + 2) / 3 * 4 + 4096;

    private BlueprintStrings() {}

    /** Why a string or payload was refused. */
    public static final class BadBlueprintString extends Exception {
        public BadBlueprintString(String message) {
            super(message);
        }
    }

    /** The string of the record bytes {@code raw}. */
    public static String encode(byte[] raw) {
        return VERSION + Base64.getEncoder().encodeToString(deflate(raw));
    }

    /** The record bytes of {@code s}; refused when it is not a version-{@value #VERSION} string or is too large. */
    public static byte[] decode(String s) throws BadBlueprintString {
        return inflate(unwrap(s));
    }

    /** The deflated payload inside {@code s} (version checked, Base64 decoded, not inflated). */
    public static byte[] unwrap(String s) throws BadBlueprintString {
        if (s == null) throw new BadBlueprintString("empty");
        if (s.length() > MAX_STRING_LENGTH) throw new BadBlueprintString("too long");
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) b.append(c);
        }
        if (b.isEmpty()) throw new BadBlueprintString("empty");
        if (b.charAt(0) != VERSION) throw new BadBlueprintString("unknown version");
        byte[] packed;
        try {
            packed = Base64.getDecoder().decode(b.substring(1));
        } catch (IllegalArgumentException e) {
            throw new BadBlueprintString("not base64");
        }
        if (packed.length == 0) throw new BadBlueprintString("empty");
        if (packed.length > MAX_COMPRESSED_BYTES) throw new BadBlueprintString("too large");
        return packed;
    }

    public static byte[] deflate(byte[] raw) {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
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

    /** {@code packed} inflated; refused when malformed, truncated, followed by junk or larger than {@link #MAX_RAW_BYTES}. */
    public static byte[] inflate(byte[] packed) throws BadBlueprintString {
        if (packed == null || packed.length == 0) throw new BadBlueprintString("empty");
        if (packed.length > MAX_COMPRESSED_BYTES) throw new BadBlueprintString("too large");
        Inflater inf = new Inflater();
        try {
            inf.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(MAX_RAW_BYTES, packed.length * 4));
            byte[] buf = new byte[8192];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0) {
                    if (inf.needsInput() || inf.needsDictionary()) throw new BadBlueprintString("truncated");
                    continue;
                }
                if (out.size() + n > MAX_RAW_BYTES) throw new BadBlueprintString("too large");
                out.write(buf, 0, n);
            }
            if (inf.getRemaining() > 0) throw new BadBlueprintString("trailing data");
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new BadBlueprintString("corrupt");
        } finally {
            inf.end();
        }
    }
}
