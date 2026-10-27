package dev.minetorio.client.map;

import dev.minetorio.content.orefield.ClientOreInfo;
import dev.minetorio.content.orefield.OreFieldPayloads;
import dev.minetorio.content.orefield.OreFields;
import dev.minetorio.core.map.HoverThrottle;
import dev.minetorio.core.map.MapPalette;
import dev.minetorio.core.orefield.OreType;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Full-map hover line for ore fields: "Iron ore field: 1 234 left" for the field block on the
 * hovered column. Amounts are server-side, so the column is asked about
 * ({@link OreFieldPayloads.Query}) at most every {@value #MIN_INTERVAL_MS} ms, and again every
 * {@value #REFRESH_MS} ms while it stays hovered; only columns whose map color can be an ore field
 * (or a machine standing on one) are asked about.
 */
public final class OreFieldHover implements MapHoverInfo {
    static final long MIN_INTERVAL_MS = 100;
    static final long REFRESH_MS = 1000;
    private final HoverThrottle throttle = new HoverThrottle(MIN_INTERVAL_MS, REFRESH_MS);

    public static void register() { MapHoverInfo.register(new OreFieldHover()); }

    @Override
    public void appendLines(int blockX, int blockZ, int mapColor, List<Component> lines) {
        if (!mayBeField(mapColor)) return;
        if (throttle.shouldAsk(blockX, blockZ, Util.getMillis())) PacketDistributor.sendToServer(new OreFieldPayloads.Query(blockX, blockZ));
        OreFieldPayloads.Info info = ClientOreInfo.at(blockX, blockZ);
        OreType type = info == null ? null : info.oreType();
        if (type != null) lines.add(OreFields.amountMessage(type, info.remaining()));
    }

    /** Whether a column of this map color may have a field block on top (or under a machine). */
    private static boolean mayBeField(int mapColor) {
        if (mapColor == (MapPalette.MARKER_MACHINE & 0xFF)) return true;
        if (mapColor >= MapPalette.MARKER_BASE) return false;
        int id = mapColor >> 2;
        for (OreType t : OreType.values()) {
            if (OreFields.block(t).defaultMapColor().id == id) return true;
        }
        return false;
    }
}
