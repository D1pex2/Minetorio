package dev.minetorio.client.map;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.chat.Component;

/**
 * Extra tooltip lines for the block column under the cursor on the full-screen map (after the
 * coordinates). Hook for other features, e.g. ore fields showing their remaining ore
 * ({@link OreFieldHover}): register a provider once on the client ({@link #register}). Providers
 * run every frame while the cursor is over an explored column, on the client thread, so they must
 * answer from client-side data (the column's map color is {@code ClientMapData.colorAt}), asking
 * the server only in a throttled way.
 */
@FunctionalInterface
public interface MapHoverInfo {
    /** Adds lines about the column at (blockX, blockZ); {@code mapColor} is its {@code MapPalette} value (unsigned). */
    void appendLines(int blockX, int blockZ, int mapColor, List<Component> lines);

    List<MapHoverInfo> PROVIDERS = new CopyOnWriteArrayList<>();

    static void register(MapHoverInfo provider) { PROVIDERS.add(provider); }
}
