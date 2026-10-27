package dev.minetorio.client.research;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.research.LabBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Draws a lab (R19): the body model (base, pedestal, cap), the fluid column ("dome") inside the glass
 * tube and the tube itself, translucent. While the lab is {@link LabBlockEntity#isWorking working} the
 * column swaps to its glowing model (animated pulsing core, full brightness) and turns slowly about
 * the vertical center axis; idle it is dim. Models are authored around the footprint's center block
 * (−16..32) in the north orientation.
 */
public class LabRenderer implements BlockEntityRenderer<LabBlockEntity> {
    public static final ModelResourceLocation BODY = MachineRender.model("block/lab_body");
    public static final ModelResourceLocation DOME = MachineRender.model("block/lab_dome");
    public static final ModelResourceLocation DOME_IDLE = MachineRender.model("block/lab_dome_idle");
    public static final ModelResourceLocation SHELL = MachineRender.model("block/lab_shell");

    private final ModelBlockRenderer modelRenderer;

    public LabRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(LabBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        MachineRender.toCenter(pose, be.facing(), be.shape());
        pose.pushPose();
        pose.translate(-0.5, 0, -0.5);
        MachineRender.renderModel(modelRenderer, BODY, pose, buffers, light, overlay);
        MachineRender.renderTranslucentModel(modelRenderer, SHELL, pose, buffers, light, overlay);
        pose.popPose();
        pose.mulPose(Axis.YP.rotationDegrees(be.renderDomeAngle(partialTick)));
        pose.translate(-0.5, 0, -0.5);
        boolean working = be.isWorking();
        MachineRender.renderModel(modelRenderer, working ? DOME : DOME_IDLE, pose, buffers, working ? LightTexture.FULL_BRIGHT : light, overlay);
        pose.popPose();
    }

    /** The whole footprint (the models extend past the controller block). */
    @Override
    public AABB getRenderBoundingBox(LabBlockEntity be) {
        return MachineRender.footprintBox(be.getBlockPos(), be.facing(), be.shape());
    }
}
