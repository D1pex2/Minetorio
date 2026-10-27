package dev.minetorio.client.map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.minetorio.content.map.ClientMapData;
import dev.minetorio.content.map.MapPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/** Player markers on a map view: the local player as an arrow, the others as dots with their names. */
final class MapOverlay {
    static final int SELF = 0xFFFFE14D;
    static final int OTHER = 0xFF56C8FF;

    private MapOverlay() {}

    /**
     * Draws the players onto a view showing (cx, cz) at the center of (x0, y0)–(x1, y1) with
     * {@code scale} GUI px per block; markers outside the rectangle are skipped.
     */
    static void drawPlayers(GuiGraphics g, double cx, double cz, float scale, int x0, int y0, int x1, int y1, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (self == null) return;
        double mx = (x0 + x1) / 2.0, my = (y0 + y1) / 2.0;
        String own = self.getGameProfile().getName();
        for (MapPayloads.Marker m : ClientMapData.players()) {
            if (m.name().equals(own)) continue;
            float sx = (float) (mx + (m.x() + 0.5 - cx) * scale), sy = (float) (my + (m.z() + 0.5 - cz) * scale);
            if (sx < x0 || sx > x1 || sy < y0 || sy > y1) continue;
            arrow(g, sx, sy, m.yaw(), 4, OTHER);
            g.drawString(mc.font, m.name(), (int) sx + 5, (int) sy - 4, 0xFFFFFFFF, true);
        }
        double px = self.getX(partialTick), pz = self.getZ(partialTick);
        float sx = (float) (mx + (px - cx) * scale), sy = (float) (my + (pz - cz) * scale);
        if (sx >= x0 && sx <= x1 && sy >= y0 && sy <= y1) arrow(g, sx, sy, self.getViewYRot(partialTick), 5, SELF);
    }

    /** An arrow head pointing along a Minecraft yaw (north up on the map), with a dark outline. */
    static void arrow(GuiGraphics g, float x, float y, float yaw, float size, int color) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(yaw + 180));
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        quad(vc, pose.last().pose(), size + 1.2f, 0xFF000000);
        quad(vc, pose.last().pose(), size, color);
        pose.popPose();
        g.flush();
    }

    /** Tip, right, notch, left — emitted in both windings so culling never hides it. */
    private static void quad(VertexConsumer vc, Matrix4f m, float s, int color) {
        float[][] p = {{0, -s}, {s * 0.75f, s * 0.75f}, {0, s * 0.3f}, {-s * 0.75f, s * 0.75f}};
        for (int i = 0; i < 4; i++) vc.addVertex(m, p[i][0], p[i][1], 0).setColor(color);
        for (int i = 3; i >= 0; i--) vc.addVertex(m, p[i][0], p[i][1], 0).setColor(color);
    }
}
