package dev.minetorio.client.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.content.machine.MiningDrillBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Draws a mining drill's parts that the controller's block model cannot (R19): an optional body
 * (the 3×3 electric drill's "electric_mining_drill_body"), the drill head, which spins while the
 * drill is {@link MiningDrillBlockEntity#isWorking working}, an optional carriage, which rides up and
 * down {@value #CARRIAGE_TRAVEL_PX} px once per head turn (the electric drill's mechanism between its
 * rails), and an optional static top drawn one block up (the electric drill's upper rails), which
 * lets the machine stand taller than a model's −16..32 range. All are JSON models authored around one
 * block (−16..32) in the north orientation, placed on the footprint's center column and turned to the
 * drill's facing; the head spins about that block's vertical center axis.
 */
public class DrillRenderer implements BlockEntityRenderer<MiningDrillBlockEntity> {
    public static final ModelResourceLocation BURNER_HEAD = MachineRender.model("block/burner_mining_drill_head");
    public static final ModelResourceLocation ELECTRIC_BODY = MachineRender.model("block/electric_mining_drill_body");
    public static final ModelResourceLocation ELECTRIC_HEAD = MachineRender.model("block/electric_mining_drill_head");
    public static final ModelResourceLocation ELECTRIC_CARRIAGE = MachineRender.model("block/electric_mining_drill_carriage");
    public static final ModelResourceLocation ELECTRIC_TOWER = MachineRender.model("block/electric_mining_drill_tower");
    /** How far the carriage rises over its rest position, in model pixels (art/check_zfight.py samples 0..this). */
    static final float CARRIAGE_TRAVEL_PX = 4;

    /** A drill's models: optional body, the head, optional top and optional carriage. */
    public record Parts(@Nullable ModelResourceLocation body, ModelResourceLocation head, @Nullable ModelResourceLocation top,
                        @Nullable ModelResourceLocation carriage) {}

    public static final Parts BURNER = new Parts(null, BURNER_HEAD, null, null);
    public static final Parts ELECTRIC = new Parts(ELECTRIC_BODY, ELECTRIC_HEAD, ELECTRIC_TOWER, ELECTRIC_CARRIAGE);

    private final ModelBlockRenderer modelRenderer;
    private final Parts parts;

    public DrillRenderer(BlockEntityRendererProvider.Context ctx, Parts parts) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
        this.parts = parts;
    }

    /** Carriage height over its rest position in blocks: 0 at head angle 0, the full travel half a turn later. */
    static float carriageLift(float headAngle) {
        return CARRIAGE_TRAVEL_PX / 16f * (1 - (float) Math.cos(Math.toRadians(headAngle))) / 2;
    }

    @Override
    public void render(MiningDrillBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        assemble(parts, be.facing(), be.shape(), be.renderHeadAngle(partialTick), pose,
                MachineRender.sink(modelRenderer, buffers, light, overlay));
    }

    /** Lays out the drill's parts from the controller block's corner, the head turned by {@code angle} degrees. */
    public static void assemble(Parts parts, Direction facing, MultiblockShape shape, float angle, PoseStack pose, MachineRender.PartSink sink) {
        pose.pushPose();
        MachineRender.toCenter(pose, facing, shape);
        if (parts.body() != null) draw(parts.body(), 0, pose, sink);
        if (parts.top() != null) draw(parts.top(), 1, pose, sink);
        if (parts.carriage() != null) draw(parts.carriage(), carriageLift(angle), pose, sink);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        draw(parts.head(), 0, pose, sink);
        pose.popPose();
    }

    private static void draw(ModelResourceLocation model, float dy, PoseStack pose, MachineRender.PartSink sink) {
        pose.pushPose();
        pose.translate(-0.5, dy, -0.5);
        sink.part(model, pose);
        pose.popPose();
    }

    /** The whole footprint (the body and head extend past the controller block), plus a block above for the top. */
    @Override
    public AABB getRenderBoundingBox(MiningDrillBlockEntity be) {
        AABB box = MachineRender.footprintBox(be.getBlockPos(), be.facing(), be.shape());
        return parts.top() == null ? box : box.expandTowards(0, 1, 0);
    }
}
