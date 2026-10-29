package dev.minetorio.client.alt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.minetorio.Minetorio;
import dev.minetorio.client.area.OverlayQuads;
import dev.minetorio.content.alt.AltSource;
import dev.minetorio.content.inserter.InserterBlockEntity;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.research.ClientResearch;
import dev.minetorio.content.research.LabBlockEntity;
import dev.minetorio.core.alt.AltInfo;
import dev.minetorio.core.alt.AltRules;
import dev.minetorio.core.alt.AltStatus;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.fluid.FluidData;
import dev.minetorio.core.fluid.FluidGauge;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Alt mode in the world: above every Minetorio machine within {@value #MAX_DISTANCE} blocks (from
 * {@link MachineIndex}) a camera-facing icon on a dark rounded backdrop, as Factorio's alt icons:
 * the machine's {@link AltInfo} icon (the lab: its research's icon) and, flashing, a red or yellow
 * status badge. Inserters get a flat arrow from pickup to drop over their base (and a badge when
 * their hand stalls for want of energy).
 *
 * <p>Cheap with hundreds of machines: no chunk walk per frame (the index), distance and frustum
 * culling, all backdrops, badges and arrows in one buffer flushed once ({@link OverlayQuads}),
 * items batched in the vanilla buffers. Depth-tested, so walls hide icons behind them.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class AltIconRenderer {
    public static final double MAX_DISTANCE = 32;
    /** Pipe gauges (small) show only this close, so a big pipe field does not drown the view. */
    public static final double GAUGE_DISTANCE = 16;
    private static final int BACKDROP = 0xF0181A1D;
    private static final int ERROR = 0xF0D93A2B;
    private static final int WARNING = 0xF0F0C02C;
    private static final int ARROW = 0xD8F2D13B;
    private static final float CORNER = 0.14f;
    private static final int CORNER_STEPS = 4;
    private static final int MAX_CACHED_STACKS = 64;

    private static final Map<String, ItemStack> STACKS = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) { return size() > MAX_CACHED_STACKS; }
    };

    private AltIconRenderer() {}

    private static ItemStack stack(String id) {
        return STACKS.computeIfAbsent(id, k -> MachineItems.stack(k, 1));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !AltMode.isOn()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Frustum frustum = event.getFrustum();
        PoseStack pose = event.getPoseStack();
        Quaternionf rotation = camera.rotation();
        MultiBufferSource.BufferSource quads = OverlayQuads.buffers();
        MultiBufferSource.BufferSource items = mc.renderBuffers().bufferSource();
        VertexConsumer solid = quads.getBuffer(OverlayQuads.SOLID);
        boolean flash = AltRules.flashOn(level.getGameTime());
        String labIcon = AltRules.labIcon(ClientResearch.state().current(), Phase1Data.recipes());
        double max2 = MAX_DISTANCE * MAX_DISTANCE;
        Ctx ctx = new Ctx(pose, solid, items, mc.getItemRenderer(), mc.font, level, rotation, flash);

        for (MachineIndex.Machine m : MachineIndex.machines()) {
            if (m.be().isRemoved()) continue;
            AltInfo info = ((AltSource) m.be()).altInfo();
            String icon = info.icon();
            if (m.be() instanceof LabBlockEntity && info.status() != AltStatus.NO_RESEARCH) icon = labIcon;
            if (icon == null && info.status() == AltStatus.NONE) continue;
            double dx = m.x() - cam.x, dy = m.y() - cam.y, dz = m.z() - cam.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > max2) continue;
            double size = AltRules.iconSize(m.size(), Math.sqrt(d2));
            double h = size / 2;
            if (!frustum.isVisible(new AABB(m.x() - h, m.y() - h, m.z() - h, m.x() + h, m.y() + h, m.z() + h))) continue;
            drawIcon(ctx, dx, dy, dz, (float) size, icon, info.status());
        }

        double gaugeMax2 = GAUGE_DISTANCE * GAUGE_DISTANCE;
        for (MachineIndex.Gauge g : MachineIndex.gauges()) {
            if (g.be().isRemoved()) continue;
            FluidGauge gauge = g.source().fluidGauge();
            if (gauge.isEmpty()) continue;
            double dx = g.x() - cam.x, dy = g.y() - cam.y, dz = g.z() - cam.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > (g.size() < 0.5 ? gaugeMax2 : max2)) continue;
            double h = g.size() / 2;
            if (!frustum.isVisible(new AABB(g.x() - h, g.y() - h, g.z() - h, g.x() + h, g.y() + h, g.z() + h))) continue;
            drawGauge(pose, solid, rotation, dx, dy, dz, (float) g.size(), gauge);
        }

        for (InserterBlockEntity ins : MachineIndex.inserters()) {
            if (ins.isRemoved()) continue;
            BlockPos p = ins.getBlockPos();
            double dx = p.getX() + 0.5 - cam.x, dy = p.getY() - cam.y, dz = p.getZ() + 0.5 - cam.z;
            if (dx * dx + dy * dy + dz * dz > max2) continue;
            if (!frustum.isVisible(new AABB(p))) continue;
            drawArrow(pose, solid, dx, dy, dz, ins.facing());
            AltStatus stall = AltRules.inserter(ins.logic().isMoving(), ins.isWorking(), ins.type().burner);
            if (stall != AltStatus.NONE) drawIcon(ctx, dx, dy + 1.0, dz, 0.45f, null, stall);
        }

        quads.endBatch(OverlayQuads.SOLID);
        items.endBatch();
    }

    private record Ctx(PoseStack pose, VertexConsumer solid, MultiBufferSource.BufferSource items, ItemRenderer itemRenderer,
                       Font font, ClientLevel level, Quaternionf rotation, boolean flash) {}

    /** A billboard at (dx, dy, dz) from the camera, {@code size} blocks across. */
    private static void drawIcon(Ctx c, double dx, double dy, double dz, float size, String icon, AltStatus status) {
        boolean hasStatus = status != AltStatus.NONE;
        if (icon == null && !(hasStatus && c.flash())) return; // a lone warning blinks as a whole
        PoseStack pose = c.pose();
        pose.pushPose();
        pose.translate(dx, dy, dz);
        pose.mulPose(c.rotation());
        pose.scale(size, size, size);
        Matrix4f m = pose.last().pose();
        roundedRect(c.solid(), m, 0, 0, 0.5f, 0, 0xFFE1B861);
        roundedRect(c.solid(), m, 0, 0, 0.46f, 0.01f, BACKDROP);
        if (icon != null && icon.startsWith("fluid:")) {
            int color = 0xFF000000 | FluidData.color(icon.substring(6));
            roundedRect(c.solid(), m, 0, 0, 0.29f, 0.2f, color);
        } else if (icon != null) {
            pose.pushPose();
            pose.translate(0, 0, 0.2f);
            pose.scale(0.8f, 0.8f, 0.25f);
            c.itemRenderer().renderStatic(stack(icon), ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    pose, c.items(), c.level(), 0);
            pose.popPose();
        }
        if (hasStatus && c.flash()) {
            float cx = icon == null ? 0 : 0.27f, cy = icon == null ? 0 : -0.27f, half = icon == null ? 0.4f : 0.23f;
            float z = 0.4f;
            int color = status.severity() == AltStatus.Severity.ERROR ? ERROR : WARNING;
            roundedRect(c.solid(), m, cx, cy, half, z, color);
            badgeSymbol(c, cx, cy, half, z + 0.02f, status);
        }
        pose.popPose();
    }

    /** The symbol on a status badge: a coal icon for no fuel, else a glyph. */
    private static void badgeSymbol(Ctx c, float cx, float cy, float half, float z, AltStatus status) {
        PoseStack pose = c.pose();
        pose.pushPose();
        pose.translate(cx, cy, z);
        if (status == AltStatus.NO_FUEL) {
            pose.scale(half * 1.6f, half * 1.6f, 0.05f);
            c.itemRenderer().renderStatic(stack("minecraft:coal"), ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, pose, c.items(), c.level(), 0);
        } else {
            String glyph = switch (status) {
                case NO_POWER, LOW_POWER -> "⚡";
                case NO_ORE -> "✖";
                case OUTPUT_FULL -> "!";
                default -> "?";
            };
            float s = half * 1.5f / 9f; // the font's line is 9 units
            pose.scale(s, -s, s);
            int color = status.severity() == AltStatus.Severity.ERROR ? 0xFFFFFFFF : 0xFF202020;
            c.font().drawInBatch(glyph, -c.font().width(glyph) / 2f, -4f, color, false, pose.last().pose(), c.items(),
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }

    /**
     * A fluid gauge: the dark backdrop, the fluid's colour dimmed over the whole square and bright up
     * to the fill level (bottom up), like a tank's sight glass.
     */
    private static void drawGauge(PoseStack pose, VertexConsumer vc, Quaternionf rotation, double dx, double dy, double dz,
                                  float size, FluidGauge gauge) {
        pose.pushPose();
        pose.translate(dx, dy, dz);
        pose.mulPose(rotation);
        pose.scale(size, size, size);
        Matrix4f m = pose.last().pose();
        roundedRect(vc, m, 0, 0, 0.5f, 0, BACKDROP);
        int rgb = FluidData.color(gauge.fluid()) & 0xFFFFFF;
        float in = 0.36f, top = -in + 2 * in * gauge.fill();
        OverlayQuads.quad(vc, m, 0x50000000 | rgb, -in, -in, 0.05f, in, -in, 0.05f, in, in, 0.05f, -in, in, 0.05f);
        OverlayQuads.quad(vc, m, 0xF0000000 | rgb, -in, -in, 0.1f, in, -in, 0.1f, in, top, 0.1f, -in, top, 0.1f);
        int flow = FluidData.fluid(gauge.fluid()) == null ? 0xB3B3B3 : FluidData.fluid(gauge.fluid()).flow();
        OverlayQuads.quad(vc, m, 0xE0000000 | flow, -in, top - 0.04f, 0.12f, in, top - 0.04f, 0.12f, in, top, 0.12f, -in, top, 0.12f);
        pose.popPose();
    }

    /** A rounded square of half-size {@code half} centered at (cx, cy) in the billboard plane at depth z. */
    private static void roundedRect(VertexConsumer vc, Matrix4f m, float cx, float cy, float half, float z, int argb) {
        float r = Math.min(CORNER, half * 0.45f);
        float x0 = cx - half, x1 = cx + half, y0 = cy - half, y1 = cy + half;
        // middle column and the two side strips (no overlap, so the translucent color stays even)
        OverlayQuads.quad(vc, m, argb, x0 + r, y0, z, x1 - r, y0, z, x1 - r, y1, z, x0 + r, y1, z);
        OverlayQuads.quad(vc, m, argb, x0, y0 + r, z, x0 + r, y0 + r, z, x0 + r, y1 - r, z, x0, y1 - r, z);
        OverlayQuads.quad(vc, m, argb, x1 - r, y0 + r, z, x1, y0 + r, z, x1, y1 - r, z, x1 - r, y1 - r, z);
        corner(vc, m, x1 - r, y1 - r, r, 0, z, argb);
        corner(vc, m, x0 + r, y1 - r, r, 1, z, argb);
        corner(vc, m, x0 + r, y0 + r, r, 2, z, argb);
        corner(vc, m, x1 - r, y0 + r, r, 3, z, argb);
    }

    /** Quarter disc around (cx, cy), quadrant q (0 = +x+y, counter-clockwise), as degenerate quads. */
    private static void corner(VertexConsumer vc, Matrix4f m, float cx, float cy, float r, int q, float z, int argb) {
        double a0 = q * Math.PI / 2;
        for (int i = 0; i < CORNER_STEPS; i++) {
            double a = a0 + Math.PI / 2 * i / CORNER_STEPS, b = a0 + Math.PI / 2 * (i + 1) / CORNER_STEPS;
            float ax = cx + (float) Math.cos(a) * r, ay = cy + (float) Math.sin(a) * r;
            float bx = cx + (float) Math.cos(b) * r, by = cy + (float) Math.sin(b) * r;
            OverlayQuads.quad(vc, m, argb, cx, cy, z, ax, ay, z, bx, by, z, bx, by, z);
        }
    }

    /** A flat arrow over the inserter base, pointing from the pickup side to the drop side. */
    private static void drawArrow(PoseStack pose, VertexConsumer vc, double dx, double dy, double dz, Direction facing) {
        pose.pushPose();
        pose.translate(dx, dy + 0.34, dz);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-facing.toYRot()));
        Matrix4f m = pose.last().pose();
        // local +z is the facing (south at yRot 0): shaft from -0.32 to 0.05, head to 0.36
        float w = 0.07f, hw = 0.2f;
        OverlayQuads.quad(vc, m, ARROW, -w, 0, -0.32f, w, 0, -0.32f, w, 0, 0.05f, -w, 0, 0.05f);
        OverlayQuads.quad(vc, m, ARROW, -hw, 0, 0.05f, hw, 0, 0.05f, 0, 0, 0.36f, 0, 0, 0.36f);
        pose.popPose();
    }
}
