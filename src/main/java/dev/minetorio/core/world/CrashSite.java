package dev.minetorio.core.world;

import java.util.ArrayList;
import java.util.List;

/**
 * The crashed ship near the spawn (Factorio's freeplay start): a broken hull lying on the ground with
 * a cargo chest holding the starter kit, and debris scattered around. Offsets are relative to the
 * wreck's anchor; {@code dy} counts up from the ground of each piece's own column (0 = the block
 * just above the ground).
 */
public final class CrashSite {
    public enum Kind { HULL, DEBRIS, CHEST }

    public record Piece(Kind kind, int dx, int dy, int dz) {}

    /** Where the wreck's anchor lies relative to the spawn: a few steps to the south-east. */
    public static final int ANCHOR_DX = 5;
    public static final int ANCHOR_DZ = 4;

    private static final List<Piece> LAYOUT = build();

    private CrashSite() {}

    public static List<Piece> layout() { return LAYOUT; }

    private static List<Piece> build() {
        List<Piece> out = new ArrayList<>();
        // the hull: 6 long (x), 3 wide (z), broken open towards the spawn (-x), its back end raised
        String[] layers = {
                // dy = 0, rows z = 0..2, columns x = 0..5
                ".HHHHH|HHHHHH|.HHHH.",
                // dy = 1
                "..HHH.|.HHHHH|..HHH.",
                // dy = 2
                "......|...HH.|......",
                // dy = 3: the fin
                "......|....H.|......",
        };
        for (int dy = 0; dy < layers.length; dy++) {
            String[] rows = layers[dy].split("\\|");
            for (int dz = 0; dz < rows.length; dz++) {
                for (int dx = 0; dx < rows[dz].length(); dx++) {
                    if (rows[dz].charAt(dx) == 'H') out.add(new Piece(Kind.HULL, dx, dy, dz));
                }
            }
        }
        // the cargo chest in the broken front, facing the spawn
        out.add(new Piece(Kind.CHEST, 0, 0, 2));
        // debris thrown along the crash path (from the north-west)
        int[][] debris = {{-3, -3}, {-6, -1}, {-2, 5}, {7, -2}, {3, -4}, {8, 4}, {-8, -6}, {1, 6}};
        for (int[] d : debris) out.add(new Piece(Kind.DEBRIS, d[0], 0, d[1]));
        return List.copyOf(out);
    }
}
