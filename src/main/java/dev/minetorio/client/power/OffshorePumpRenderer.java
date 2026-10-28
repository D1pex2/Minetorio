package dev.minetorio.client.power;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.power.SteamMachineBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * The offshore pump's moving parts, after Factorio 1.1's pump and its animation (32 frames at 1/4 speed):
 * the body, with its still glass tubes, is the controller's blockstate model; this draws the two little
 * cogs on the housing's east side, meshing and turning about the length axis while the pump
 * {@link SteamMachineBlockEntity#isWorking works}, and the water running through the tubes (an animated
 * strip over the still tubes) while it pumps. Models are authored in the center frame (footprint center at
 * x = z = 8; art/gen_models.py OP_GEARS, OP_TO_CENTER), the cogs about (y 8, z 8) like the steam engine's
 * gears, the second one half a tooth turned so the two mesh.
 */
public class OffshorePumpRenderer implements BlockEntityRenderer<SteamMachineBlockEntity> {
    public static final ModelResourceLocation GEAR = MachineRender.model("block/offshore_pump_gear");
    public static final ModelResourceLocation GEAR_B = MachineRender.model("block/offshore_pump_gear_b");
    public static final ModelResourceLocation FLOW = MachineRender.model("block/offshore_pump_flow");
    /** Every model this renderer draws (for model registration). */
    public static final ModelResourceLocation[] MODELS = {GEAR, GEAR_B, FLOW};
    private static final float PX = 1 / 16f, GEAR_CENTER = 8;
    /** Cog pivots, center-frame px {y, z} (controller frame + 8 px in z), and their turning direction. */
    static final float[][] GEARS = {{6, 4, 1}, {6, 11, -1}};

    private final ModelBlockRenderer modelRenderer;

    public OffshorePumpRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(SteamMachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        assemble(be.facing(), be.shape(), be.renderAngle(partialTick), be.isWorking(), pose,
                MachineRender.sink(modelRenderer, buffers, light, overlay));
    }

    /** Lays out the pump's moving parts from the controller block's corner: the cogs at {@code angle}, the flow while {@code working}. */
    public static void assemble(Direction facing, MultiblockShape shape, float angle, boolean working, PoseStack pose,
                                MachineRender.PartSink sink) {
        pose.pushPose();
        MachineRender.toCenter(pose, facing, shape);
        for (int i = 0; i < GEARS.length; i++) {
            float[] g = GEARS[i];
            pose.pushPose();
            pose.translate(0, g[0] * PX, (g[1] - GEAR_CENTER) * PX);
            pose.mulPose(Axis.XP.rotationDegrees(angle * g[2]));
            pose.translate(-0.5, -GEAR_CENTER * PX, -GEAR_CENTER * PX);
            sink.part(i == 0 ? GEAR : GEAR_B, pose);
            pose.popPose();
        }
        if (working) {
            pose.translate(-0.5, 0, -0.5);
            sink.part(FLOW, pose);
        }
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(SteamMachineBlockEntity be) {
        return MachineRender.footprintBox(be.getBlockPos(), be.facing(), be.shape()).inflate(0, 1, 0);
    }
}
