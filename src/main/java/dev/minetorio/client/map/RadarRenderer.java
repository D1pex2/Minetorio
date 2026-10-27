package dev.minetorio.client.map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.map.RadarBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Draws a radar: the base model at the footprint's center and the dish model one block up, turning
 * about the vertical center axis while the radar is {@link RadarBlockEntity#isWorking powered}. Both
 * are authored around the center block (−16..32) in the north orientation (art/gen_radar.py).
 */
public class RadarRenderer implements BlockEntityRenderer<RadarBlockEntity> {
    public static final ModelResourceLocation BASE = MachineRender.model("block/radar_base");
    public static final ModelResourceLocation DISH = MachineRender.model("block/radar_dish");

    private final ModelBlockRenderer modelRenderer;

    public RadarRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(RadarBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        MachineRender.toCenter(pose, be.facing(), be.shape());
        pose.pushPose();
        pose.translate(-0.5, 0, -0.5);
        MachineRender.renderModel(modelRenderer, BASE, pose, buffers, light, overlay);
        pose.popPose();
        pose.mulPose(Axis.YP.rotationDegrees(be.renderDishAngle(partialTick)));
        pose.translate(-0.5, 1, -0.5);
        MachineRender.renderModel(modelRenderer, DISH, pose, buffers, light, overlay);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(RadarBlockEntity be) {
        return MachineRender.footprintBox(be.getBlockPos(), be.facing(), be.shape());
    }
}
