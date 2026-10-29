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
 * Draws a mining drill's parts that the controller's block model cannot (R19), after the Factorio 1.1
 * sprites and their animations ({@code art/gen_models.py}):
 * <ul>
 *   <li>burner drill (body on the controller's blockstate): the drill head, turning while the drill
 *       is {@link MiningDrillBlockEntity#isWorking working}; the piston beside the box, rising
 *       {@value #PISTON_STROKE_PX} px once per head turn; the fire behind the fire door, at full
 *       brightness while it mines;</li>
 *   <li>electric drill: the body, the head, the carriage between the rails riding up and down
 *       {@value #CARRIAGE_TRAVEL_PX} px once per head turn, the rails' upper half drawn one block up
 *       (taller than one model may be) and the green status lamp, lit while it mines.</li>
 * </ul>
 * All parts are JSON models authored around one block (−16..32) in the north orientation in their rest
 * pose (head angle 0: what a ghost shows), placed on the footprint's center column and turned to the
 * drill's facing; the head turns about that block's vertical center axis.
 */
public class DrillRenderer implements BlockEntityRenderer<MiningDrillBlockEntity> {
    public static final ModelResourceLocation BURNER_HEAD = MachineRender.model("block/burner_mining_drill_head");
    public static final ModelResourceLocation BURNER_PISTON = MachineRender.model("block/burner_mining_drill_piston");
    public static final ModelResourceLocation BURNER_FIRE = MachineRender.model("block/burner_mining_drill_fire");
    public static final ModelResourceLocation ELECTRIC_BODY = MachineRender.model("block/electric_mining_drill_body");
    public static final ModelResourceLocation ELECTRIC_HEAD = MachineRender.model("block/electric_mining_drill_head");
    public static final ModelResourceLocation ELECTRIC_CARRIAGE = MachineRender.model("block/electric_mining_drill_carriage");
    public static final ModelResourceLocation ELECTRIC_TOWER = MachineRender.model("block/electric_mining_drill_tower");
    public static final ModelResourceLocation ELECTRIC_LAMP = MachineRender.model("block/electric_mining_drill_lamp");
    /** Every model this renderer draws (for model registration). */
    public static final ModelResourceLocation[] MODELS = {BURNER_HEAD, BURNER_PISTON, BURNER_FIRE, ELECTRIC_BODY, ELECTRIC_HEAD,
        ELECTRIC_CARRIAGE, ELECTRIC_TOWER, ELECTRIC_LAMP};
    /** How far the carriage rises over its rest position, in model pixels (art/check_zfight.py samples 0..this). */
    static final float CARRIAGE_TRAVEL_PX = 4;
    /** How far the burner drill's piston rises over its rest position, in model pixels. */
    static final float PISTON_STROKE_PX = 3;

    /**
     * A drill's models: optional body, the head, optional top, carriage (riding up and down), piston
     * (pumping), and glowing parts drawn only while working (fire, lamp).
     */
    public record Parts(@Nullable ModelResourceLocation body, ModelResourceLocation head, @Nullable ModelResourceLocation top,
                        @Nullable ModelResourceLocation carriage, @Nullable ModelResourceLocation piston,
                        @Nullable ModelResourceLocation workingGlow) {}

    public static final Parts BURNER = new Parts(null, BURNER_HEAD, null, null, BURNER_PISTON, BURNER_FIRE);
    public static final Parts ELECTRIC = new Parts(ELECTRIC_BODY, ELECTRIC_HEAD, ELECTRIC_TOWER, ELECTRIC_CARRIAGE, null, ELECTRIC_LAMP);

    private final ModelBlockRenderer modelRenderer;
    private final Parts parts;

    public DrillRenderer(BlockEntityRendererProvider.Context ctx, Parts parts) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
        this.parts = parts;
    }

    /** Lift over the rest position in blocks for a stroke of {@code px}: 0 at head angle 0, the full stroke half a turn later. */
    static float lift(float headAngle, float px) {
        return px / 16f * (1 - (float) Math.cos(Math.toRadians(headAngle))) / 2;
    }

    /** Carriage height over its rest position in blocks: 0 at head angle 0, the full travel half a turn later. */
    static float carriageLift(float headAngle) {
        return lift(headAngle, CARRIAGE_TRAVEL_PX);
    }

    @Override
    public void render(MiningDrillBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        assemble(parts, be.facing(), be.shape(), be.renderHeadAngle(partialTick), be.isWorking(), pose,
                MachineRender.sink(modelRenderer, buffers, be, light, overlay));
    }

    /** Lays out the drill's parts at rest-or-turned {@code angle}, idle (nothing lit); what a ghost shows at angle 0. */
    public static void assemble(Parts parts, Direction facing, MultiblockShape shape, float angle, PoseStack pose, MachineRender.PartSink sink) {
        assemble(parts, facing, shape, angle, false, pose, sink);
    }

    /** Lays out the drill's parts from the controller block's corner, the head turned by {@code angle} degrees. */
    public static void assemble(Parts parts, Direction facing, MultiblockShape shape, float angle, boolean working, PoseStack pose,
                                MachineRender.PartSink sink) {
        pose.pushPose();
        MachineRender.toCenter(pose, facing, shape);
        if (parts.body() != null) draw(parts.body(), 0, pose, sink, false);
        if (parts.top() != null) draw(parts.top(), 1, pose, sink, false);
        if (parts.carriage() != null) draw(parts.carriage(), carriageLift(angle), pose, sink, false);
        if (parts.piston() != null) draw(parts.piston(), lift(angle, PISTON_STROKE_PX), pose, sink, false);
        if (working && parts.workingGlow() != null) draw(parts.workingGlow(), 0, pose, sink, true);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        draw(parts.head(), 0, pose, sink, false);
        pose.popPose();
    }

    private static void draw(ModelResourceLocation model, float dy, PoseStack pose, MachineRender.PartSink sink, boolean glow) {
        pose.pushPose();
        pose.translate(-0.5, dy, -0.5);
        sink.part(model, pose, false, glow);
        pose.popPose();
    }

    /** The whole footprint (the body and head extend past the controller block), plus a block above for the top. */
    @Override
    public AABB getRenderBoundingBox(MiningDrillBlockEntity be) {
        AABB box = MachineRender.footprintBox(be.getBlockPos(), be.facing(), be.shape());
        return parts.top() == null ? box : box.expandTowards(0, 1, 0);
    }
}
