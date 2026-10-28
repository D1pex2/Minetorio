package dev.minetorio.client.map;

import dev.minetorio.content.map.ClientWatchedChunks;
import dev.minetorio.core.map.ChunkKeys;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Radar coverage on a map view (as Factorio shows it): the chunks a working radar watches right
 * now ({@link ClientWatchedChunks}) get a faint cool tint, and the edge of the covered area a thin
 * outline, so the live nearby-scan area stands apart from land that was only explored once.
 */
final class RadarCoverageOverlay {
    static final int TINT = 0x2A5FD0FF;
    static final int EDGE = 0xA05FD0FF;
    /** Below this many GUI px per chunk the outline is left out (it would cover the chunk). */
    private static final float MIN_EDGE_CHUNK_PX = 4f;

    private RadarCoverageOverlay() {}

    /**
     * Draws the coverage onto a view showing (cx, cz) at the center of (x0, y0)–(x1, y1) with
     * {@code scale} GUI px per block. The caller clips if needed.
     */
    static void draw(GuiGraphics g, double cx, double cz, float scale, int x0, int y0, int x1, int y1) {
        Set<Long> watched = ClientWatchedChunks.chunks();
        if (watched.isEmpty()) return;
        double mx = (x0 + x1) / 2.0, my = (y0 + y1) / 2.0;
        int chx0 = Math.floorDiv((int) Math.floor(cx - (mx - x0) / scale), 16), chx1 = Math.floorDiv((int) Math.floor(cx + (x1 - mx) / scale), 16);
        int chz0 = Math.floorDiv((int) Math.floor(cz - (my - y0) / scale), 16), chz1 = Math.floorDiv((int) Math.floor(cz + (y1 - my) / scale), 16);
        boolean edges = 16 * scale >= MIN_EDGE_CHUNK_PX;
        long visible = (long) (chx1 - chx0 + 1) * (chz1 - chz0 + 1);
        if (watched.size() < visible) {
            for (long k : watched) {
                int x = ChunkKeys.x(k), z = ChunkKeys.z(k);
                if (x >= chx0 && x <= chx1 && z >= chz0 && z <= chz1) chunk(g, x, z, mx, my, cx, cz, scale, edges);
            }
        } else {
            for (int z = chz0; z <= chz1; z++) {
                for (int x = chx0; x <= chx1; x++) {
                    if (ClientWatchedChunks.isWatched(x, z)) chunk(g, x, z, mx, my, cx, cz, scale, edges);
                }
            }
        }
    }

    /** Screen edge of a chunk boundary; adjacent chunks share edges, so tints never overlap. */
    private static int edge(double mid, int chunk, double center, float scale) {
        return (int) Math.floor(mid + (chunk * 16 - center) * scale);
    }

    private static void chunk(GuiGraphics g, int x, int z, double mx, double my, double cx, double cz, float scale, boolean edges) {
        int sx0 = edge(mx, x, cx, scale), sx1 = edge(mx, x + 1, cx, scale);
        int sy0 = edge(my, z, cz, scale), sy1 = edge(my, z + 1, cz, scale);
        if (sx1 <= sx0 || sy1 <= sy0) {
            sx1 = Math.max(sx1, sx0 + 1);
            sy1 = Math.max(sy1, sy0 + 1);
        }
        g.fill(sx0, sy0, sx1, sy1, TINT);
        if (!edges) return;
        if (!ClientWatchedChunks.isWatched(x, z - 1)) g.fill(sx0, sy0, sx1, sy0 + 1, EDGE);
        if (!ClientWatchedChunks.isWatched(x, z + 1)) g.fill(sx0, sy1 - 1, sx1, sy1, EDGE);
        if (!ClientWatchedChunks.isWatched(x - 1, z)) g.fill(sx0, sy0, sx0 + 1, sy1, EDGE);
        if (!ClientWatchedChunks.isWatched(x + 1, z)) g.fill(sx1 - 1, sy0, sx1, sy1, EDGE);
    }
}
