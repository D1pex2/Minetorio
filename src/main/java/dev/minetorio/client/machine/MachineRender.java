package dev.minetorio.client.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.Minetorio;
import dev.minetorio.content.multiblock.MultiblockShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Helpers for block entity renderers of multiblock machines (R19): models authored around one block
 * (−16..32) in the north orientation, drawn on the footprint's center and turned to the facing.
 */
public final class MachineRender {
    private MachineRender() {}

    public static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, path));
    }

    /** Footprint center relative to the controller block's corner: {x, z}. */
    public static double[] center(Direction facing, MultiblockShape shape) {
        Direction right = facing.getClockWise();
        double w = (shape.width() - 1) / 2.0;
        double d = (shape.depth() - 1) / 2.0;
        return new double[] {
            0.5 + right.getStepX() * w + facing.getStepX() * d,
            0.5 + right.getStepZ() * w + facing.getStepZ() * d
        };
    }

    /**
     * Moves {@code pose} to the footprint's center and turns it so that the north orientation of a
     * model matches {@code facing} (like blockstate "y" rotations); the origin is then the center of
     * the footprint at the bottom, with model north = forward.
     */
    public static void toCenter(PoseStack pose, Direction facing, MultiblockShape shape) {
        double[] c = center(facing, shape);
        pose.translate(c[0], 0, c[1]);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
    }

    /** Draws a standalone block model at the pose (model 0..16 = one block from the pose origin). */
    public static void renderModel(ModelBlockRenderer renderer, ModelResourceLocation location, PoseStack pose,
                                   MultiBufferSource buffers, int light, int overlay) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(location);
        renderer.renderModel(pose.last(), buffers.getBuffer(Sheets.solidBlockSheet()), null, model, 1f, 1f, 1f,
                light, overlay, ModelData.EMPTY, null);
    }

    /** The whole footprint of a machine (models extend past the controller block). */
    public static AABB footprintBox(BlockPos origin, Direction facing, MultiblockShape s) {
        BlockPos far = origin.relative(facing, s.depth() - 1).relative(facing.getClockWise(), s.width() - 1).above(s.height() - 1);
        return new AABB(origin).minmax(new AABB(far));
    }
}
