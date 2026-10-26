package dev.minetorio.client.belt;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.content.belt.BeltGeometry;
import dev.minetorio.core.belt.BeltShape;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

/** The exact pose sequence of LaneItemRenderer + ItemRenderer (context NONE) applied to an item icon. */
class LanePoseTest {
    private static final float EPS = 1e-5f;

    /** translate(lane point) → basis → scale → ItemRenderer's translate(-0.5). */
    private static PoseStack flatPose(double x, double z, double dx, double dz) {
        PoseStack pose = new PoseStack();
        pose.translate(x, LaneItemRenderer.SURFACE + LaneItemRenderer.FLAT_LIFT, z);
        pose.mulPose(LanePose.flatItem(dx, dz));
        pose.scale(LaneItemRenderer.SCALE, LaneItemRenderer.SCALE, LaneItemRenderer.SCALE);
        pose.translate(-0.5f, -0.5f, -0.5f);
        return pose;
    }

    private static Vector3f point(PoseStack pose, float x, float y, float z) {
        Vector4f v = pose.last().pose().transform(new Vector4f(x, y, z, 1));
        return new Vector3f(v.x, v.y, v.z);
    }

    private static void assertVec(float x, float y, float z, Vector3f v) {
        assertEquals(x, v.x, EPS, "x of " + v);
        assertEquals(y, v.y, EPS, "y of " + v);
        assertEquals(z, v.z, EPS, "z of " + v);
    }

    @Test void iconLiesFaceUpWithTopAlongTravel() {
        for (Direction f : Direction.Plane.HORIZONTAL) {
            PoseStack pose = flatPose(0.25, 0.5, f.getStepX(), f.getStepZ());
            // Front face normal of a generated icon is +Z in model space; it must point up.
            Vector3f n = pose.last().normal().transform(new Vector3f(0, 0, 1));
            assertVec(0, 1, 0, n);
            // The front face (z = 8.5/16) is horizontal: all four corners at the same height.
            float zf = 8.5f / 16;
            float y0 = point(pose, 0, 0, zf).y;
            for (float[] c : new float[][] {{1, 0}, {0, 1}, {1, 1}}) assertEquals(y0, point(pose, c[0], c[1], zf).y, EPS);
            assertTrue(y0 > LaneItemRenderer.SURFACE, "above the plate");
            // Picture top (model +Y) points along travel.
            Vector3f top = point(pose, 0.5f, 1, 0.5f).sub(point(pose, 0.5f, 0, 0.5f));
            assertVec(f.getStepX() * LaneItemRenderer.SCALE, 0, f.getStepZ() * LaneItemRenderer.SCALE, top);
            // Centred on the lane point.
            Vector3f c = point(pose, 0.5f, 0.5f, 0.5f);
            assertEquals(0.25, c.x, EPS);
            assertEquals(0.5, c.z, EPS);
        }
    }

    @Test void iconIsNotMirrored() {
        // Looking down (from +Y) with the picture top pointing away, model +X must point to the right.
        PoseStack pose = flatPose(0.5, 0.5, 0, -1); // travel north; right is east (+x)
        Vector3f right = point(pose, 1, 0.5f, 0.5f).sub(point(pose, 0, 0.5f, 0.5f));
        assertVec(LaneItemRenderer.SCALE, 0, 0, right);
    }

    @Test void blockStandsUprightOnThePlate() {
        PoseStack pose = new PoseStack();
        pose.translate(0.5, LaneItemRenderer.SURFACE + LaneItemRenderer.BLOCK_SCALE / 2, 0.5);
        pose.mulPose(LanePose.uprightBlock(1, 0));
        pose.scale(LaneItemRenderer.BLOCK_SCALE, LaneItemRenderer.BLOCK_SCALE, LaneItemRenderer.BLOCK_SCALE);
        pose.translate(-0.5f, -0.5f, -0.5f);
        assertEquals(LaneItemRenderer.SURFACE, point(pose, 0, 0, 0).y, EPS, "bottom rests on the plate");
        assertVec(0, 1, 0, pose.last().normal().transform(new Vector3f(0, 1, 0)));
    }

    @Test void curveTangentTurnsFromEntryToFacing() {
        // CURVE_LEFT facing north is fed from the west: travel east at t=0, north at t=1.
        double[] start = BeltGeometry.laneTangent(Direction.NORTH, BeltShape.CURVE_LEFT, 0);
        double[] end = BeltGeometry.laneTangent(Direction.NORTH, BeltShape.CURVE_LEFT, 1);
        assertEquals(1, start[0], 1e-9);
        assertEquals(0, start[1], 1e-9);
        assertEquals(0, end[0], 1e-9);
        assertEquals(-1, end[1], 1e-9);
        // CURVE_RIGHT facing north is fed from the east: travel west at t=0.
        double[] r = BeltGeometry.laneTangent(Direction.NORTH, BeltShape.CURVE_RIGHT, 0);
        assertEquals(-1, r[0], 1e-9);
        // Tangent matches the path: finite difference of lanePoint at mid-curve.
        double[] a = BeltGeometry.lanePoint(Direction.NORTH, BeltShape.CURVE_LEFT, dev.minetorio.content.belt.LaneSide.LEFT, 0.49);
        double[] b = BeltGeometry.lanePoint(Direction.NORTH, BeltShape.CURVE_LEFT, dev.minetorio.content.belt.LaneSide.LEFT, 0.51);
        double[] t = BeltGeometry.laneTangent(Direction.NORTH, BeltShape.CURVE_LEFT, 0.5);
        double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
        assertEquals(t[0], (b[0] - a[0]) / len, 1e-3);
        assertEquals(t[1], (b[1] - a[1]) / len, 1e-3);
    }
}
