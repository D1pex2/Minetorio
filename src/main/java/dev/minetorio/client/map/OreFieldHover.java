package dev.minetorio.client.map;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.minetorio.content.map.ClientMapData;
import dev.minetorio.content.orefield.ClientOreInfo;
import dev.minetorio.content.orefield.OreFieldPayloads;
import dev.minetorio.content.orefield.OreFields;
import dev.minetorio.core.map.HoverThrottle;
import dev.minetorio.core.map.MapPalette;
import dev.minetorio.core.orefield.OrePatch;
import dev.minetorio.core.orefield.OreType;
import dev.minetorio.core.orefield.PatchShape;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

/**
 * Full-map hover for ore fields, like Factorio's resource tooltip: the whole patch under the cursor
 * is tinted and outlined and "Iron ore field: 190k left" gives its remaining ore, followed by the
 * hovered block's own amount. Amounts are server-side, so the column is asked about
 * ({@link OreFieldPayloads.Query}) at most every {@value #MIN_INTERVAL_MS} ms, and again every
 * {@value #REFRESH_MS} ms while it stays hovered; only columns whose map color can be an ore field
 * (or a machine standing on one) are asked about. While the cursor moves over a patch already known,
 * it stays highlighted until the answers for the new columns arrive.
 *
 * <p>The highlight covers only explored map chunks; the patch's shape (runs and outline edges) is
 * computed once per patch and cached.
 */
public final class OreFieldHover implements MapHoverInfo {
    static final long MIN_INTERVAL_MS = 100;
    static final long REFRESH_MS = 1000;
    private static final int TINT = 0x50FFFFFF;
    private static final int OUTLINE = 0xFFFFFFFF;
    private static final int SHAPES_CACHED = 16;

    private final HoverThrottle throttle = new HoverThrottle(MIN_INTERVAL_MS, REFRESH_MS);
    private final Map<Long, PatchShape> shapes = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, PatchShape> eldest) { return size() > SHAPES_CACHED; }
    };

    public static void register() { MapHoverInfo.register(new OreFieldHover()); }

    @Override
    public void appendLines(int blockX, int blockZ, int mapColor, List<Component> lines) {
        if (!mayBeField(mapColor)) return;
        if (throttle.shouldAsk(blockX, blockZ, Util.getMillis())) PacketDistributor.sendToServer(new OreFieldPayloads.Query(blockX, blockZ));
        OreFieldPayloads.Info patch = ClientOreInfo.patchAt(blockX, blockZ);
        if (patch != null && patch.oreType() != null) lines.add(OreFields.patchMessage(patch.oreType(), patch.total()));
        OreFieldPayloads.Info info = ClientOreInfo.at(blockX, blockZ);
        OreType type = info == null ? null : info.oreType();
        if (type != null) lines.add(OreFields.amountMessage(type, info.remaining()));
    }

    @Override
    public void drawOverlay(GuiGraphics g, MapView view, int blockX, int blockZ, int mapColor) {
        if (!mayBeField(mapColor)) return;
        OreFieldPayloads.Info info = ClientOreInfo.patchAt(blockX, blockZ);
        OrePatch patch = info == null ? null : info.patchShape();
        if (patch == null) return;
        PatchShape shape = shapes.computeIfAbsent(info.patch(), k -> PatchShape.of(patch));
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        // tint: each run split at chunk borders, explored chunks only
        for (PatchShape.Run r : shape.runs()) {
            double y0 = view.screenY(r.z()), y1 = view.screenY(r.z() + 1);
            if (y1 < view.y0() || y0 > view.y1()) continue;
            for (int x = r.x0(); x <= r.x1(); ) {
                int end = Math.min(r.x1(), (x & ~15) + 15);
                if (ClientMapData.isExplored(x >> 4, r.z() >> 4)) {
                    quad(vc, m, view.screenX(x), y0, view.screenX(end + 1), y1, TINT);
                }
                x = end + 1;
            }
        }
        // outline: one GUI pixel wide, centered on the disk's border
        double h = 0.5;
        for (PatchShape.Edge e : shape.edges()) {
            if (!ClientMapData.isExplored(e.x() >> 4, e.z() >> 4)) continue;
            double sx0 = view.screenX(e.x()), sx1 = view.screenX(e.x() + 1);
            double sy0 = view.screenY(e.z()), sy1 = view.screenY(e.z() + 1);
            switch (e.side()) {
                case PatchShape.NORTH -> quad(vc, m, sx0 - h, sy0 - h, sx1 + h, sy0 + h, OUTLINE);
                case PatchShape.SOUTH -> quad(vc, m, sx0 - h, sy1 - h, sx1 + h, sy1 + h, OUTLINE);
                case PatchShape.WEST -> quad(vc, m, sx0 - h, sy0 - h, sx0 + h, sy1 + h, OUTLINE);
                default -> quad(vc, m, sx1 - h, sy0 - h, sx1 + h, sy1 + h, OUTLINE);
            }
        }
        g.flush();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, double x0, double y0, double x1, double y1, int argb) {
        vc.addVertex(m, (float) x0, (float) y0, 0).setColor(argb);
        vc.addVertex(m, (float) x0, (float) y1, 0).setColor(argb);
        vc.addVertex(m, (float) x1, (float) y1, 0).setColor(argb);
        vc.addVertex(m, (float) x1, (float) y0, 0).setColor(argb);
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
