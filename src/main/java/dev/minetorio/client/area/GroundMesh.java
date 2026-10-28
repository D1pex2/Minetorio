package dev.minetorio.client.area;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.minetorio.core.area.AreaMath;
import dev.minetorio.core.area.GroundRect;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

/**
 * Ground overlay geometry built once (on a client tick) from a provider's areas and drawn every
 * frame: one quad per column at the top of that column's ground, so the overlay follows steps and
 * slopes. Ground is the first block, scanning down from just above the object's own level, whose
 * collision box covers the whole column (or whose top face is sturdy) and that has no block
 * entity, so machines, belts and poles standing on it do not lift the overlay. Coordinates are kept
 * relative to an origin for float precision far from 0,0.
 */
final class GroundMesh implements AreaOverlays.Sink {
    /** Lift above the ground surface (the render type adds a view offset too). */
    private static final float LIFT = 0.02f;
    /** Width of borders and rings, in blocks. */
    private static final float BAND = 0.09f;
    private static final float RING = 0.07f;
    private static final int RING_SEGMENTS = 72;
    /** How far above and below the object's level the ground is searched. */
    private static final int SCAN_UP = 1;
    private static final int SCAN_DOWN = 6;

    private final Level level;
    private final BlockPos origin;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final Map<Long, Float> heights = new HashMap<>();
    /** x, y, z per vertex, relative to the origin. */
    private float[] data = new float[192];
    private int[] colors = new int[64];
    private int vertices;

    GroundMesh(Level level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
    }

    boolean isEmpty() { return vertices == 0; }

    // --- sink --------------------------------------------------------------------------------------

    @Override
    public void fill(GroundRect rect, int argb, int baseY) {
        for (int x = rect.minX(); x <= rect.maxX(); x++) {
            for (int z = rect.minZ(); z <= rect.maxZ(); z++) {
                float y = top(x, z, baseY) + LIFT;
                flat(argb, x, z, x + 1, z + 1, y);
            }
        }
        outline(rect, strongerEdge(argb), baseY);
    }

    @Override
    public void outline(GroundRect rect, int argb, int baseY) {
        for (int x = rect.minX(); x <= rect.maxX(); x++) {
            for (int z = rect.minZ(); z <= rect.maxZ(); z++) {
                if (!rect.onEdge(x, z)) continue;
                float y = top(x, z, baseY) + LIFT * 1.5f;
                if (x == rect.minX()) flat(argb, x, z, x + BAND, z + 1, y);
                if (x == rect.maxX()) flat(argb, x + 1 - BAND, z, x + 1, z + 1, y);
                if (z == rect.minZ()) flat(argb, x, z, x + 1, z + BAND, y);
                if (z == rect.maxZ()) flat(argb, x, z + 1 - BAND, x + 1, z + 1, y);
            }
        }
    }

    @Override
    public void circle(double cx, double cz, double radius, int argb, int baseY) {
        float y = top((int) Math.floor(cx), (int) Math.floor(cz), baseY) + LIFT * 2;
        double[] p = AreaMath.circle(cx, cz, radius, RING_SEGMENTS);
        for (int i = 0; i + 3 < p.length; i += 2) {
            double ax = p[i], az = p[i + 1], bx = p[i + 2], bz = p[i + 3];
            double len = Math.hypot(bx - ax, bz - az);
            double nx = -(bz - az) / len * RING / 2, nz = (bx - ax) / len * RING / 2;
            vertex(ax - nx, y, az - nz, argb);
            vertex(ax + nx, y, az + nz, argb);
            vertex(bx + nx, y, bz + nz, argb);
            vertex(bx - nx, y, bz - nz, argb);
        }
    }

    // --- ground ------------------------------------------------------------------------------------

    /** World y of the ground's top surface in column (x, z) near {@code baseY} (baseY itself if none is found). */
    private float top(int x, int z, int baseY) {
        long key = BlockPos.asLong(x, baseY, z);
        Float cached = heights.get(key);
        if (cached != null) return cached;
        int y = AreaMath.groundY(baseY + SCAN_UP, baseY - SCAN_DOWN, yy -> isGround(x, yy, z), Integer.MIN_VALUE);
        float top;
        if (y == Integer.MIN_VALUE) {
            top = baseY;
        } else {
            BlockState s = level.getBlockState(cursor.set(x, y, z));
            VoxelShape shape = s.getCollisionShape(level, cursor);
            top = y + (float) (shape.isEmpty() ? 1 : shape.max(Direction.Axis.Y));
        }
        heights.put(key, top);
        return top;
    }

    private boolean isGround(int x, int y, int z) {
        BlockState s = level.getBlockState(cursor.set(x, y, z));
        if (s.isAir() || s.hasBlockEntity()) return false;
        if (s.isFaceSturdy(level, cursor, Direction.UP)) return true;
        VoxelShape shape = s.getCollisionShape(level, cursor);
        if (shape.isEmpty()) return false;
        return shape.min(Direction.Axis.X) < 0.01 && shape.max(Direction.Axis.X) > 0.99
                && shape.min(Direction.Axis.Z) < 0.01 && shape.max(Direction.Axis.Z) > 0.99;
    }

    // --- vertices ----------------------------------------------------------------------------------

    private static int strongerEdge(int argb) {
        int a = Math.min(255, (argb >>> 24) * 2 + 40);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private void flat(int argb, double x0, double z0, double x1, double z1, float y) {
        vertex(x0, y, z0, argb);
        vertex(x0, y, z1, argb);
        vertex(x1, y, z1, argb);
        vertex(x1, y, z0, argb);
    }

    private void vertex(double x, double y, double z, int argb) {
        if (vertices == colors.length) {
            colors = Arrays.copyOf(colors, colors.length * 2);
            data = Arrays.copyOf(data, colors.length * 3);
        }
        int i = vertices * 3;
        data[i] = (float) (x - origin.getX());
        data[i + 1] = (float) (y - origin.getY());
        data[i + 2] = (float) (z - origin.getZ());
        colors[vertices++] = argb;
    }

    /** Draws the mesh; {@code pose} is the level pose (camera rotation only). */
    void render(PoseStack pose, Vec3 camera, VertexConsumer vc) {
        pose.pushPose();
        pose.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
        Matrix4f m = pose.last().pose();
        for (int v = 0; v < vertices; v++) {
            vc.addVertex(m, data[v * 3], data[v * 3 + 1], data[v * 3 + 2]).setColor(colors[v]);
        }
        pose.popPose();
    }
}
