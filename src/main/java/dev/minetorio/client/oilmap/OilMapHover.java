package dev.minetorio.client.oilmap;

import dev.minetorio.client.map.MapHoverInfo;
import dev.minetorio.content.oilmap.OilMapPayloads;
import dev.minetorio.core.map.HoverThrottle;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Field and per-well yields, also available for radar-explored chunks no longer loaded. */
public final class OilMapHover implements MapHoverInfo {
    private final HoverThrottle throttle = new HoverThrottle(200, 1000);
    public static void register() { MapHoverInfo.register(new OilMapHover()); }
    @Override public void appendLines(int x, int z, int color, List<Component> lines) {
        if (throttle.shouldAsk(x, z, Util.getMillis())) PacketDistributor.sendToServer(new OilMapPayloads.Query(x, z));
        OilMapPayloads.Info info = OilMapPayloads.cached(x, z);
        if (info == null || info.count() <= 0) return;
        lines.add(Component.translatable("minetorio.oil.field_yield", percent(info.total()), info.count()));
        lines.add(Component.translatable("minetorio.oil.well_yield", percent(info.yield())));
    }
    private static String percent(double ratio) { return String.format(Locale.ROOT, "%.1f%%", ratio * 100); }
}
