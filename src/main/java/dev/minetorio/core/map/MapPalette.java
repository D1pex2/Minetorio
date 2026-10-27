package dev.minetorio.core.map;

/**
 * The map's color of one block column, one byte each, {@value #COLUMNS} per chunk (index
 * {@code x + z * 16}, local coordinates).
 *
 * <p>Values below {@link #MARKER_BASE} are Minecraft's packed map colors ({@code MapColor} id × 4 +
 * brightness), so any block, including blocks added later (ore fields), shows in its own map color.
 * 0 is "no color" (an explored column with nothing to show). Map color ids 62 and 63 are unused by
 * Minecraft 1.21.1; their packed values ({@code 0xF8..0xFF}) are our structure markers, drawn flat
 * (no height shading).
 */
public final class MapPalette {
    public static final int COLUMNS = 256;
    /** First marker value (unsigned). */
    public static final int MARKER_BASE = 0xF8;
    /** Any multiblock machine (controller or part). */
    public static final byte MARKER_MACHINE = (byte) 0xF8;
    /** An electric pole. */
    public static final byte MARKER_POLE = (byte) 0xF9;

    private MapPalette() {}

    public static boolean isMarker(byte value) { return (value & 0xFF) >= MARKER_BASE; }
}
