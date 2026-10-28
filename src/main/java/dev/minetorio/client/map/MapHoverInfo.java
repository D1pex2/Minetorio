package dev.minetorio.client.map;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Extra tooltip lines for the block column under the cursor on the full-screen map (after the
 * coordinates), and optionally a drawing over the map for it. Hook for other features, e.g. ore
 * fields showing their remaining ore and highlighting their patch ({@link OreFieldHover}): register
 * a provider once on the client ({@link #register}). Providers run every frame while the cursor is
 * over an explored column, on the client thread, so they must answer from client-side data (the
 * column's map color is {@code ClientMapData.colorAt}), asking the server only in a throttled way.
 */
@FunctionalInterface
public interface MapHoverInfo {
    /** Adds lines about the column at (blockX, blockZ); {@code mapColor} is its {@code MapPalette} value (unsigned). */
    void appendLines(int blockX, int blockZ, int mapColor, List<Component> lines);

    /**
     * Draws over the map for the hovered column (after the map, before the tooltip): {@code view}
     * maps world to screen; the screen is clipped to the map area.
     */
    default void drawOverlay(GuiGraphics g, MapView view, int blockX, int blockZ, int mapColor) {}

    /**
     * The full map's view: world (cx, cz) at the center of the GUI rectangle (x0, y0)–(x1, y1),
     * {@code scale} GUI pixels per block, north up.
     */
    record MapView(double cx, double cz, float scale, int x0, int y0, int x1, int y1) {
        /** GUI x of the west edge of block column x (world x). */
        public double screenX(double worldX) { return (x0 + x1) / 2.0 + (worldX - cx) * scale; }

        public double screenY(double worldZ) { return (y0 + y1) / 2.0 + (worldZ - cz) * scale; }
    }

    List<MapHoverInfo> PROVIDERS = new CopyOnWriteArrayList<>();

    static void register(MapHoverInfo provider) { PROVIDERS.add(provider); }
}
