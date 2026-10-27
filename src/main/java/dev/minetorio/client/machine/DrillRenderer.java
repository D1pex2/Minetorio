package dev.minetorio.client.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.content.machine.MiningDrillBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Draws a mining drill's parts that the controller's block model cannot (R19): an optional body
 * (the 3×3 electric drill's "electric_mining_drill_body"), the drill head, which spins while the
 * drill is {@link MiningDrillBlockEntity#isWorking working}, and an optional static top drawn one
 * block up (the electric drill's derrick, the burner drill's chimney), which lets the machine stand
 * taller than a model's −16..32 range. All are JSON models authored around one block (−16..32) in
 * the north orientation, placed on the footprint's center column and turned to the drill's facing;
 * the head spins about that block's vertical center axis.
 */
public class DrillRenderer implements BlockEntityRenderer<MiningDrillBlockEntity> {
    public static final ModelResourceLocation BURNER_HEAD = MachineRender.model("block/burner_mining_drill_head");
    public static final ModelResourceLocation BURNER_CHIMNEY = MachineRender.model("block/burner_mining_drill_chimney");
    public static final ModelResourceLocation ELECTRIC_BODY = MachineRender.model("block/electric_mining_drill_body");
    public static final ModelResourceLocation ELECTRIC_HEAD = MachineRender.model("block/electric_mining_drill_head");
    public static final ModelResourceLocation ELECTRIC_TOWER = MachineRender.model("block/electric_mining_drill_tower");

    private final ModelBlockRenderer modelRenderer;
    @Nullable
    private final ModelResourceLocation body;
    private final ModelResourceLocation head;
    @Nullable
    private final ModelResourceLocation top;

    public DrillRenderer(BlockEntityRendererProvider.Context ctx, @Nullable ModelResourceLocation body, ModelResourceLocation head,
                         @Nullable ModelResourceLocation top) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
        this.body = body;
        this.head = head;
        this.top = top;
    }

    @Override
    public void render(MiningDrillBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        MachineRender.toCenter(pose, be.facing(), be.shape());
        if (body != null) {
            pose.pushPose();
            pose.translate(-0.5, 0, -0.5);
            MachineRender.renderModel(modelRenderer, body, pose, buffers, light, overlay);
            pose.popPose();
        }
        if (top != null) {
            pose.pushPose();
            pose.translate(-0.5, 1, -0.5);
            MachineRender.renderModel(modelRenderer, top, pose, buffers, light, overlay);
            pose.popPose();
        }
        pose.mulPose(Axis.YP.rotationDegrees(be.renderHeadAngle(partialTick)));
        pose.translate(-0.5, 0, -0.5);
        MachineRender.renderModel(modelRenderer, head, pose, buffers, light, overlay);
        pose.popPose();
    }

    /** The whole footprint (the body and head extend past the controller block), plus a block above for the top. */
    @Override
    public AABB getRenderBoundingBox(MiningDrillBlockEntity be) {
        AABB box = MachineRender.footprintBox(be.getBlockPos(), be.facing(), be.shape());
        return top == null ? box : box.expandTowards(0, 1, 0);
    }
}
