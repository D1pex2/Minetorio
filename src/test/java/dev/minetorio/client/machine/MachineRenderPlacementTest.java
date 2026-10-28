package dev.minetorio.client.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.content.multiblock.MultiblockShape;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

/**
 * The pose machine renderers and the ghost renderer share ({@link MachineRender#toCenter}): from the
 * controller cell it lands on the centre of the footprint the machine (or its ghost) occupies, and
 * model north points along the facing, for every facing and machine size.
 */
class MachineRenderPlacementTest {
    private static final double EPS = 1e-5;
    private static final List<MultiblockShape> SHAPES = List.of(MultiblockShape.STONE_FURNACE, MultiblockShape.ELECTRIC_MINING_DRILL,
            MultiblockShape.ASSEMBLING_MACHINE, MultiblockShape.BOILER, MultiblockShape.STEAM_ENGINE, MultiblockShape.SPLITTER,
            new MultiblockShape(4, 4, 4));

    private static Vector4f apply(PoseStack pose, float x, float y, float z, float w) {
        return pose.last().pose().transform(new Vector4f(x, y, z, w));
    }

    @Test
    void centerIsFootprintCentroidAndNorthIsFacing() {
        for (MultiblockShape shape : SHAPES) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                double cx = 0, cz = 0;
                List<BlockPos> cells = shape.footprint(BlockPos.ZERO, facing);
                for (BlockPos p : cells) {
                    cx += p.getX() + 0.5;
                    cz += p.getZ() + 0.5;
                }
                cx /= cells.size();
                cz /= cells.size();

                PoseStack pose = new PoseStack();
                MachineRender.toCenter(pose, facing, shape);
                Vector4f origin = apply(pose, 0, 0, 0, 1);
                String what = shape + " " + facing;
                assertEquals(cx, origin.x, EPS, what);
                assertEquals(0, origin.y, EPS, what);
                assertEquals(cz, origin.z, EPS, what);

                Vector4f north = apply(pose, 0, 0, -1, 0);
                assertEquals(facing.getStepX(), north.x, EPS, what);
                assertEquals(facing.getStepZ(), north.z, EPS, what);
            }
        }
    }

    /** ModelPoint (smoke and steam origins) maps model pixels like the renderers' pose does. */
    @Test
    void modelPointMatchesTheRenderPose() {
        float[][] points = {{8, 0, 8}, {-10, 31, -1}, {13, 31, 14}, {24, 4, -20}};
        for (MultiblockShape shape : SHAPES) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                PoseStack pose = new PoseStack();
                MachineRender.toCenter(pose, facing, shape);
                pose.translate(-0.5, 0, -0.5);
                for (float[] p : points) {
                    Vector4f r = apply(pose, p[0] / 16, p[1] / 16, p[2] / 16, 1);
                    var w = dev.minetorio.content.multiblock.ModelPoint.world(BlockPos.ZERO, facing, shape, p[0], p[1], p[2]);
                    String what = shape + " " + facing + " " + p[0] + "," + p[1] + "," + p[2];
                    assertEquals(r.x, w.x, EPS, what);
                    assertEquals(r.y, w.y, EPS, what);
                    assertEquals(r.z, w.z, EPS, what);
                }
            }
        }
    }
}
