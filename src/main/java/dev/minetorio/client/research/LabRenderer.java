package dev.minetorio.client.research;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.research.LabBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Draws a lab (R19), after Factorio 1.1's lab and its animation: a still geodesic glass dome (see-through
 * glass, an opaque brass lattice, in a translucent buffer) over the body (the low round base, the doorway
 * and the core column inside). While the lab is {@link LabBlockEntity#isWorking researching} the mechanism
 * inside - a ring carrying four science pods round the core - turns about the vertical center axis, the
 * core glows and the glass swaps to its lit model (pulsing blue, full brightness); idle the glass is dark
 * and nothing is lit. Models are authored around the footprint's center block (−16..32) in the north
 * orientation, the ring in its rest pose (angle 0, what a ghost shows).
 */
public class LabRenderer implements BlockEntityRenderer<LabBlockEntity> {
    public static final ModelResourceLocation BODY = MachineRender.model("block/lab_body");
    public static final ModelResourceLocation ROTOR = MachineRender.model("block/lab_rotor");
    public static final ModelResourceLocation CORE_GLOW = MachineRender.model("block/lab_core_glow");
    public static final ModelResourceLocation DOME = MachineRender.model("block/lab_dome");
    public static final ModelResourceLocation DOME_IDLE = MachineRender.model("block/lab_dome_idle");
    /** Every model this renderer draws (for model registration). */
    public static final ModelResourceLocation[] MODELS = {BODY, ROTOR, CORE_GLOW, DOME, DOME_IDLE};

    private final ModelBlockRenderer modelRenderer;

    public LabRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(LabBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        assemble(be.facing(), be.shape(), be.renderDomeAngle(partialTick), be.isWorking(), pose,
                MachineRender.sink(modelRenderer, buffers, be, light, overlay));
    }

    /**
     * Lays out the lab from the controller block's corner: the inner ring turned by {@code rotorAngle}, the
     * core and the glass lit while {@code working}.
     */
    public static void assemble(Direction facing, MultiblockShape shape, float rotorAngle, boolean working, PoseStack pose,
                                MachineRender.PartSink sink) {
        pose.pushPose();
        MachineRender.toCenter(pose, facing, shape);
        pose.pushPose();
        pose.translate(-0.5, 0, -0.5);
        sink.part(BODY, pose);
        if (working) sink.part(CORE_GLOW, pose, false, true);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(rotorAngle));
        pose.translate(-0.5, 0, -0.5);
        sink.part(ROTOR, pose);
        pose.popPose();
        pose.translate(-0.5, 0, -0.5);
        sink.part(working ? DOME : DOME_IDLE, pose, true, working);
        pose.popPose();
    }

    /** The whole footprint (the models extend past the controller block). */
    @Override
    public AABB getRenderBoundingBox(LabBlockEntity be) {
        return MachineRender.footprintBox(be.getBlockPos(), be.facing(), be.shape());
    }
}
