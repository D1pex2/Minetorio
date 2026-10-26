package dev.minetorio.client.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.Minetorio;
import dev.minetorio.content.machine.MiningDrillBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a mining drill's parts that the controller's block model cannot (R19): an optional body
 * (the 3×3 electric drill's "electric_mining_drill_body") and the drill head, which spins while
 * the drill is {@link MiningDrillBlockEntity#isWorking working}. Both are JSON models authored
 * around one block (−16..32) in the north orientation, placed on the footprint's center column
 * and turned to the drill's facing; the head spins about that block's vertical center axis.
 * Placeholder geometry: Task 18 swaps the models.
 */
public class DrillRenderer implements BlockEntityRenderer<MiningDrillBlockEntity> {
    public static final ModelResourceLocation BURNER_HEAD = model("block/burner_mining_drill_head");
    public static final ModelResourceLocation ELECTRIC_BODY = model("block/electric_mining_drill_body");
    public static final ModelResourceLocation ELECTRIC_HEAD = model("block/electric_mining_drill_head");

    private final ModelBlockRenderer modelRenderer;
    @Nullable
    private final ModelResourceLocation body;
    private final ModelResourceLocation head;

    public DrillRenderer(BlockEntityRendererProvider.Context ctx, @Nullable ModelResourceLocation body, ModelResourceLocation head) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
        this.body = body;
        this.head = head;
    }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, path));
    }

    /** Footprint center relative to the controller block's corner: {x, z}. */
    static double[] center(Direction facing, MultiblockShape shape) {
        Direction right = facing.getClockWise();
        double w = (shape.width() - 1) / 2.0;
        double d = (shape.depth() - 1) / 2.0;
        return new double[] {
            0.5 + right.getStepX() * w + facing.getStepX() * d,
            0.5 + right.getStepZ() * w + facing.getStepZ() * d
        };
    }

    @Override
    public void render(MiningDrillBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.facing();
        double[] c = center(facing, be.shape());
        pose.pushPose();
        pose.translate(c[0], 0, c[1]);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot())); // north model → facing, like blockstate "y" rotations
        if (body != null) {
            pose.pushPose();
            pose.translate(-0.5, 0, -0.5);
            renderModel(body, pose, buffers, light, overlay);
            pose.popPose();
        }
        pose.mulPose(Axis.YP.rotationDegrees(be.renderHeadAngle(partialTick)));
        pose.translate(-0.5, 0, -0.5);
        renderModel(head, pose, buffers, light, overlay);
        pose.popPose();
    }

    private void renderModel(ModelResourceLocation location, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(location);
        modelRenderer.renderModel(pose.last(), buffers.getBuffer(Sheets.solidBlockSheet()), null, model, 1f, 1f, 1f,
                light, overlay, ModelData.EMPTY, null);
    }

    /** The whole footprint (the body and head extend past the controller block). */
    @Override
    public AABB getRenderBoundingBox(MiningDrillBlockEntity be) {
        MultiblockShape s = be.shape();
        Direction f = be.facing();
        BlockPos o = be.getBlockPos();
        BlockPos far = o.relative(f, s.depth() - 1).relative(f.getClockWise(), s.width() - 1).above(s.height() - 1);
        return new AABB(o).minmax(new AABB(far));
    }
}
