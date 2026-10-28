package dev.minetorio.client.map;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;
import dev.minetorio.content.multiblock.MultiblockShape;
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
        assemble(be.facing(), be.shape(), be.renderDishAngle(partialTick), pose, MachineRender.sink(modelRenderer, buffers, light, overlay));
    }

    /** Lays out the radar from the controller block's corner, the dish turned by {@code dishAngle}. */
    public static void assemble(Direction facing, MultiblockShape shape, float dishAngle, PoseStack pose, MachineRender.PartSink sink) {
        pose.pushPose();
        MachineRender.toCenter(pose, facing, shape);
        pose.pushPose();
        pose.translate(-0.5, 0, -0.5);
        sink.part(BASE, pose);
        pose.popPose();
        pose.mulPose(Axis.YP.rotationDegrees(dishAngle));
        pose.translate(-0.5, 1, -0.5);
        sink.part(DISH, pose);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(RadarBlockEntity be) {
        return MachineRender.footprintBox(be.getBlockPos(), be.facing(), be.shape());
    }
}
