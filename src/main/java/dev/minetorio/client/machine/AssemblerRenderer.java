package dev.minetorio.client.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.registry.ModBlocks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Draws an assembling machine (R19): the tier's body model (housing with the round roof opening)
 * and the tier's big spoked gear in the opening, which spins about the vertical center axis while the
 * machine is {@link AssemblerBlockEntity#isWorking working}. Models are authored around the
 * footprint's center block (−16..32) in the north orientation.
 */
public class AssemblerRenderer implements BlockEntityRenderer<AssemblerBlockEntity> {
    public static final ModelResourceLocation BODY_1 = MachineRender.model("block/assembling_machine_1_body");
    public static final ModelResourceLocation BODY_2 = MachineRender.model("block/assembling_machine_2_body");
    public static final ModelResourceLocation GEAR_1 = MachineRender.model("block/assembling_machine_1_gear");
    public static final ModelResourceLocation GEAR_2 = MachineRender.model("block/assembling_machine_2_gear");

    private final ModelBlockRenderer modelRenderer;

    public AssemblerRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(AssemblerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        boolean tier2 = be.getBlockState().is(ModBlocks.ASSEMBLING_MACHINE_2.get());
        pose.pushPose();
        MachineRender.toCenter(pose, be.facing(), be.shape());
        pose.pushPose();
        pose.translate(-0.5, 0, -0.5);
        MachineRender.renderModel(modelRenderer, tier2 ? BODY_2 : BODY_1, pose, buffers, light, overlay);
        pose.popPose();
        pose.mulPose(Axis.YP.rotationDegrees(be.renderGearAngle(partialTick)));
        pose.translate(-0.5, 0, -0.5);
        MachineRender.renderModel(modelRenderer, tier2 ? GEAR_2 : GEAR_1, pose, buffers, light, overlay);
        pose.popPose();
    }

    /** The whole footprint (the models extend past the controller block). */
    @Override
    public AABB getRenderBoundingBox(AssemblerBlockEntity be) {
        return MachineRender.footprintBox(be.getBlockPos(), be.facing(), be.shape());
    }
}
