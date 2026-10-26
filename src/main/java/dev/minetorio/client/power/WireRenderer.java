package dev.minetorio.client.power;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.minetorio.content.power.ElectricPoleBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Copper wires between connected small electric poles: sagging line strips (a parabola through
 * both pole tops) from the client copy of each pole's connections. Each wire is drawn once, by the
 * pole with the smaller position; the render box covers all its wires so they are not culled while
 * that pole is off screen.
 */
public class WireRenderer implements BlockEntityRenderer<ElectricPoleBlockEntity> {
    /** Wire attachment height above the pole's block origin (top of the post). */
    private static final float ATTACH_Y = 22 / 16f;
    private static final int SEGMENTS = 16;
    /** Sag at mid-span per block of span, capped at {@link #MAX_SAG}. */
    private static final float SAG_PER_BLOCK = 0.06f;
    private static final float MAX_SAG = 0.5f;
    private static final float RED = 0.80f, GREEN = 0.47f, BLUE = 0.22f;

    public WireRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ElectricPoleBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockPos from = be.getBlockPos();
        VertexConsumer lines = null;
        for (BlockPos to : be.connections()) {
            if (from.compareTo(to) > 0) continue; // the other pole draws it
            if (lines == null) lines = buffers.getBuffer(RenderType.lines());
            float dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
            float sag = Math.min(MAX_SAG, SAG_PER_BLOCK * (float) Math.sqrt(dx * dx + dz * dz));
            float[] prev = point(0, dx, dy, dz, sag);
            for (int i = 1; i <= SEGMENTS; i++) {
                float[] next = point((float) i / SEGMENTS, dx, dy, dz, sag);
                segment(lines, pose.last(), prev, next);
                prev = next;
            }
        }
    }

    /** Point at {@code t} (0..1) along the wire, relative to this pole's block origin. */
    private static float[] point(float t, float dx, float dy, float dz, float sag) {
        return new float[] {0.5f + dx * t, ATTACH_Y + dy * t - sag * 4 * t * (1 - t), 0.5f + dz * t};
    }

    private static void segment(VertexConsumer lines, PoseStack.Pose pose, float[] a, float[] b) {
        float nx = b[0] - a[0], ny = b[1] - a[1], nz = b[2] - a[2];
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len == 0) return;
        nx /= len;
        ny /= len;
        nz /= len;
        lines.addVertex(pose, a[0], a[1], a[2]).setColor(RED, GREEN, BLUE, 1f).setNormal(pose, nx, ny, nz);
        lines.addVertex(pose, b[0], b[1], b[2]).setColor(RED, GREEN, BLUE, 1f).setNormal(pose, nx, ny, nz);
    }

    /** This pole and every pole it is wired to. */
    @Override
    public AABB getRenderBoundingBox(ElectricPoleBlockEntity be) {
        AABB box = new AABB(be.getBlockPos()).expandTowards(0, 1, 0);
        for (BlockPos p : be.connections()) box = box.minmax(new AABB(p).expandTowards(0, 1, 0));
        return box;
    }
}
