package dev.minetorio.client.robot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.minetorio.Minetorio;
import dev.minetorio.client.area.OverlayQuads;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.robot.MarkPayloads;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Marks over marked blocks (cheap flat quads, one batch per frame): a red X on the top and the
 * four sides of a block marked for deconstruction, a blue up-arrow for an upgrade order. A machine's
 * mark covers its whole footprint. Only marks within {@value #RANGE} blocks are drawn.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class MarkOverlayRenderer {
    private static final double RANGE = 96;
    private static final int RED = 0xE0F02828, BLUE = 0xE02E8CFF;

    private MarkOverlayRenderer() {}

    private static int cachedVersion = -1;
    private static List<MarkPayloads.Entry> cached = List.of();

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        if (cachedVersion != MarkPayloads.CLIENT.version()) {
            cachedVersion = MarkPayloads.CLIENT.version();
            cached = MarkPayloads.CLIENT.all();
        }
        if (cached.isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        MultiBufferSource.BufferSource buffers = OverlayQuads.buffers();
        VertexConsumer vc = buffers.getBuffer(OverlayQuads.SOLID);
        for (MarkPayloads.Entry e : cached) {
            BlockPos p = e.pos();
            if (p.distToCenterSqr(cam) > RANGE * RANGE) continue;
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            float x0 = p.getX(), y0 = p.getY(), z0 = p.getZ(), x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;
            if (s.getBlock() instanceof MultiblockControllerBlock mb && s.hasProperty(MultiblockControllerBlock.FACING)) {
                for (BlockPos f : mb.footprint(p, s)) {
                    x0 = Math.min(x0, f.getX());
                    y0 = Math.min(y0, f.getY());
                    z0 = Math.min(z0, f.getZ());
                    x1 = Math.max(x1, f.getX() + 1);
                    y1 = Math.max(y1, f.getY() + 1);
                    z1 = Math.max(z1, f.getZ() + 1);
                }
            } else {
                var shape = s.getShape(level, p);
                if (!shape.isEmpty()) y1 = y0 + (float) Math.max(0.15, shape.max(net.minecraft.core.Direction.Axis.Y));
            }
            float o = 0.02f;
            x0 -= o; y0 -= o; z0 -= o; x1 += o; y1 += o; z1 += o;
            // top, north, south, west, east: origin, u, v (u × v points out)
            face(vc, m, e, new Vector3f(x0, y1, z1), new Vector3f(x1 - x0, 0, 0), new Vector3f(0, 0, z0 - z1));
            face(vc, m, e, new Vector3f(x1, y0, z0), new Vector3f(x0 - x1, 0, 0), new Vector3f(0, y1 - y0, 0));
            face(vc, m, e, new Vector3f(x0, y0, z1), new Vector3f(x1 - x0, 0, 0), new Vector3f(0, y1 - y0, 0));
            face(vc, m, e, new Vector3f(x0, y0, z0), new Vector3f(0, 0, z1 - z0), new Vector3f(0, y1 - y0, 0));
            face(vc, m, e, new Vector3f(x1, y0, z1), new Vector3f(0, 0, z0 - z1), new Vector3f(0, y1 - y0, 0));
        }
        buffers.endBatch(OverlayQuads.SOLID);
        pose.popPose();
    }

    /** One face spanned by {@code u} (sideways) and {@code v} (up the face) from {@code o}. */
    private static void face(VertexConsumer vc, Matrix4f m, MarkPayloads.Entry e, Vector3f o, Vector3f u, Vector3f v) {
        float lu = u.length(), lv = v.length();
        float h = Math.min(lu, lv) * 0.36f;
        Vector3f c = new Vector3f(o).add(new Vector3f(u).mul(0.5f)).add(new Vector3f(v).mul(0.5f));
        Vector3f du = new Vector3f(u).normalize(), dv = new Vector3f(v).normalize();
        if (!e.upgrade()) {
            float w = h * 0.16f;
            Vector3f d1 = new Vector3f(du).add(dv).normalize(), d2 = new Vector3f(du).sub(dv).normalize();
            strip(vc, m, RED, c, d1, d2, h * 1.41f, w);
            strip(vc, m, RED, c, d2, d1, h * 1.41f, w);
        } else {
            // shaft and head of an arrow pointing along v
            Vector3f shaftC = new Vector3f(c).add(new Vector3f(dv).mul(-0.25f * h));
            strip(vc, m, BLUE, shaftC, dv, du, 0.55f * h, 0.18f * h);
            Vector3f tip = new Vector3f(c).add(new Vector3f(dv).mul(0.9f * h));
            Vector3f base = new Vector3f(c).add(new Vector3f(dv).mul(0.2f * h));
            Vector3f l = new Vector3f(base).add(new Vector3f(du).mul(-0.6f * h)), r = new Vector3f(base).add(new Vector3f(du).mul(0.6f * h));
            OverlayQuads.quad(vc, m, BLUE, l.x, l.y, l.z, r.x, r.y, r.z, tip.x, tip.y, tip.z, tip.x, tip.y, tip.z);
        }
    }

    /** A bar centred on {@code c} along {@code dir} (half length {@code half}), {@code width} wide across {@code across}. */
    private static void strip(VertexConsumer vc, Matrix4f m, int argb, Vector3f c, Vector3f dir, Vector3f across, float half, float width) {
        Vector3f a = new Vector3f(dir).mul(half), w = new Vector3f(across).mul(width / 2);
        Vector3f p0 = new Vector3f(c).sub(a).sub(w), p1 = new Vector3f(c).add(a).sub(w);
        Vector3f p2 = new Vector3f(c).add(a).add(w), p3 = new Vector3f(c).sub(a).add(w);
        OverlayQuads.quad(vc, m, argb, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, p3.x, p3.y, p3.z);
    }
}
