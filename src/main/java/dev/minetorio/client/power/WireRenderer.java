package dev.minetorio.client.power;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.minetorio.content.power.ElectricPoleBlockEntity;
import dev.minetorio.content.power.PowerPoleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Copper wires between connected electric poles of any type: sagging line strips (a parabola through
 * both attachment points, {@link PowerPoleEntity#wireAttach}) from the client copy of each pole's
 * connections. Each wire is drawn once, by the pole with the smaller position; the render box covers
 * all its wires so they are not culled while that pole is off screen. This renderer serves the small
 * pole (which is drawn by its block model); {@link MultiblockPoleRenderer} draws the other poles and
 * calls {@link #drawWires}.
 */
public class WireRenderer implements BlockEntityRenderer<ElectricPoleBlockEntity> {
    private static final int SEGMENTS = 16;
    /** Sag at mid-span per block of span, capped at {@link #MAX_SAG}. */
    private static final float SAG_PER_BLOCK = 0.06f;
    private static final float MAX_SAG = 0.5f;
    private static final float RED = 0.80f, GREEN = 0.47f, BLUE = 0.22f;

    public WireRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ElectricPoleBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        drawWires(be, be.getLevel(), pose, buffers);
    }

    /** Where wires attach to the pole whose block entity is at {@code pos}, relative to that position (small pole default). */
    static Vec3 attach(Level level, BlockPos pos) {
        if (level != null && level.getBlockEntity(pos) instanceof PowerPoleEntity p) return p.wireAttach();
        return new Vec3(0.5, ElectricPoleBlockEntity.ATTACH_Y, 0.5);
    }

    /** Draws the wires of {@code pole} to every connected pole with a larger position, from the pose at the pole's block. */
    static void drawWires(PowerPoleEntity pole, Level level, PoseStack pose, MultiBufferSource buffers) {
        if (level == null) return;
        BlockPos from = pole.getBlockPos();
        Vec3 a = pole.wireAttach();
        VertexConsumer lines = null;
        for (BlockPos to : pole.connections()) {
            if (from.compareTo(to) > 0) continue; // the other pole draws it
            if (lines == null) lines = buffers.getBuffer(RenderType.lines());
            Vec3 b = attach(level, to).add(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
            float dx = (float) (b.x - a.x), dy = (float) (b.y - a.y), dz = (float) (b.z - a.z);
            float sag = Math.min(MAX_SAG, SAG_PER_BLOCK * (float) Math.sqrt(dx * dx + dz * dz));
            float[] prev = point(0, a, dx, dy, dz, sag);
            for (int i = 1; i <= SEGMENTS; i++) {
                float[] next = point((float) i / SEGMENTS, a, dx, dy, dz, sag);
                segment(lines, pose.last(), prev, next);
                prev = next;
            }
        }
    }

    /** Point at {@code t} (0..1) along the wire, relative to the drawing pole's block origin. */
    private static float[] point(float t, Vec3 a, float dx, float dy, float dz, float sag) {
        return new float[] {(float) a.x + dx * t, (float) a.y + dy * t - sag * 4 * t * (1 - t), (float) a.z + dz * t};
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

    /** This pole and every pole it is wired to, up to their attachment points. */
    @Override
    public AABB getRenderBoundingBox(ElectricPoleBlockEntity be) {
        return wireBox(be, be.getLevel(), new AABB(be.getBlockPos()).expandTowards(0, 2, 0));
    }

    /** {@code own} grown to include every wire end of {@code pole}. */
    static AABB wireBox(PowerPoleEntity pole, Level level, AABB own) {
        AABB box = own;
        for (BlockPos p : pole.connections()) {
            Vec3 at = attach(level, p).add(p.getX(), p.getY(), p.getZ());
            box = box.minmax(new AABB(p)).minmax(new AABB(at, at).inflate(0.5));
        }
        return box;
    }
}
