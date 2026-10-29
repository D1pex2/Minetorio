package dev.minetorio.client.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlocks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws an assembling machine (R19), after the Factorio 1.1 sprites: the tier's static body model and
 * the machinery that moves while the machine is {@link AssemblerBlockEntity#isWorking working}. Every
 * moving part is its own model authored where it rests (so phase 0 is the rest pose a ghost shows) and
 * is moved here through one work loop (phase 0..1, see {@link AssemblerBlockEntity#renderAnimPhase}):
 * <ul>
 *   <li>both tiers: two meshing brass cogs over the back gearbox; the status lamp lit while working;</li>
 *   <li>tier 1 (crank train): a push rod driven by the first cog's crank, a train of three upright gears
 *       and a carriage running along the work table's rails;</li>
 *   <li>tier 2 (robot arm): an arm swinging over the work table on its turret, its press head plunging at
 *       each end of the swing, and a fan turning in its ring.</li>
 * </ul>
 * Models are authored around the footprint's center block (−16..32) in the north orientation; the
 * pivots below are in those model pixels ({@code art/gen_models.py}: ASM_COG_X, ASM_GEAR_X, ASM_ARM_PIVOT,
 * ASM_FAN). {@code art/check_zfight.py} and {@code ModelZFightTest} check the same layout.
 */
public class AssemblerRenderer implements BlockEntityRenderer<AssemblerBlockEntity> {
    public static final ModelResourceLocation BODY_1 = MachineRender.model("block/assembling_machine_1_body");
    public static final ModelResourceLocation BODY_2 = MachineRender.model("block/assembling_machine_2_body");
    public static final ModelResourceLocation COG = MachineRender.model("block/assembling_machine_cog");
    public static final ModelResourceLocation COG_B = MachineRender.model("block/assembling_machine_cog_b");
    public static final ModelResourceLocation GEAR = MachineRender.model("block/assembling_machine_gear");
    public static final ModelResourceLocation GEAR_B = MachineRender.model("block/assembling_machine_gear_b");
    public static final ModelResourceLocation ROD = MachineRender.model("block/assembling_machine_rod");
    public static final ModelResourceLocation SLIDER = MachineRender.model("block/assembling_machine_slider");
    public static final ModelResourceLocation ARM = MachineRender.model("block/assembling_machine_arm");
    public static final ModelResourceLocation HEAD = MachineRender.model("block/assembling_machine_head");
    public static final ModelResourceLocation FAN = MachineRender.model("block/assembling_machine_fan");
    public static final ModelResourceLocation LAMP = MachineRender.model("block/assembling_machine_lamp");
    /** Every model this renderer draws (for model registration). */
    public static final ModelResourceLocation[] MODELS = {BODY_1, BODY_2, COG, COG_B, GEAR, GEAR_B, ROD, SLIDER, ARM, HEAD, FAN, LAMP};

    private static final float PX = 1 / 16f;
    // pivots in model px
    private static final float COG_Y = 25, COG_A_X = 0, COG_B_X = 10.5f;
    private static final float GEAR_Y = 24.5f, GEAR_A_X = 9, GEAR_B_X = 16.5f, GEAR_A2_DX = 15;
    private static final float TURRET_X = 10, TURRET_Z = 10, FAN_X = 20, FAN_Z = 20;
    // strokes per loop
    static final float ROD_STROKE_PX = 1.5f, SLIDER_STROKE_PX = 7, ARM_SWING_DEG = 22.5f, HEAD_DROP_PX = 2;
    static final float GEAR_TURNS = 2, FAN_TURNS = 3;

    private final ModelBlockRenderer modelRenderer;

    public AssemblerRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(AssemblerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        assemble(be.getBlockState(), be.facing(), be.shape(), be.renderAnimPhase(partialTick), be.isWorking(), pose,
                MachineRender.sink(modelRenderer, buffers, be, light, overlay));
    }

    /**
     * Lays out the machine of {@code state}'s tier from the controller block's corner: the static body,
     * then the moving parts at {@code phase} of the work loop (0 = rest, what a ghost shows); the lamp
     * glows only while {@code working}.
     */
    public static void assemble(BlockState state, Direction facing, MultiblockShape shape, float phase, boolean working,
                                PoseStack pose, MachineRender.PartSink sink) {
        boolean tier2 = state.is(ModBlocks.ASSEMBLING_MACHINE_2.get());
        pose.pushPose();
        MachineRender.toCenter(pose, facing, shape);
        pose.translate(-0.5, 0, -0.5);
        // --- static parts
        sink.part(tier2 ? BODY_2 : BODY_1, pose);
        // --- moving parts
        float turn = phase * 360f;
        float wave = Mth.sin(phase * Mth.TWO_PI);
        turnZ(sink, COG, pose, COG_A_X, COG_Y, 0, turn);
        turnZ(sink, COG_B, pose, COG_B_X, COG_Y, 0, -turn);
        if (!tier2) {
            float g = turn * GEAR_TURNS;
            turnZ(sink, GEAR, pose, GEAR_A_X, GEAR_Y, 0, g);
            turnZ(sink, GEAR_B, pose, GEAR_B_X, GEAR_Y, 0, -g);
            turnZ(sink, GEAR, pose, GEAR_A_X, GEAR_Y, GEAR_A2_DX, g);
            // the rod follows the first cog's crank; the carriage runs out and back once per loop
            moved(sink, ROD, pose, 0, 0, ROD_STROKE_PX * wave);
            moved(sink, SLIDER, pose, -SLIDER_STROKE_PX * wave, 0, 0);
        } else {
            float yaw = ARM_SWING_DEG * wave;
            turnY(sink, ARM, pose, TURRET_X, TURRET_Z, yaw, 0);
            turnY(sink, HEAD, pose, TURRET_X, TURRET_Z, yaw, -headDrop(phase));
            turnY(sink, FAN, pose, FAN_X, FAN_Z, turn * FAN_TURNS, 0);
        }
        if (lampLit(tier2, phase, working)) sink.part(LAMP, pose, false, true);
        pose.popPose();
    }

    /** Press head drop (px): down at both ends of the swing (phase 1/4 and 3/4), up while the arm crosses. */
    static float headDrop(float phase) {
        return HEAD_DROP_PX * (0.5f - 0.5f * Mth.cos(phase * 2 * Mth.TWO_PI));
    }

    /** Tier 1 blinks twice per loop; tier 2's lens pulses (animated texture) the whole time it works. */
    static boolean lampLit(boolean tier2, float phase, boolean working) {
        if (!working) return false;
        if (tier2) return true;
        float p = phase * 2;
        return p - Mth.floor(p) < 0.5f;
    }

    private static void turnZ(MachineRender.PartSink sink, ModelResourceLocation model, PoseStack pose, float px, float py,
                              float dx, float degrees) {
        pose.pushPose();
        pose.translate((px + dx) * PX, py * PX, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(degrees));
        pose.translate(-px * PX, -py * PX, 0);
        sink.part(model, pose);
        pose.popPose();
    }

    private static void turnY(MachineRender.PartSink sink, ModelResourceLocation model, PoseStack pose, float px, float pz,
                              float degrees, float dyPx) {
        pose.pushPose();
        pose.translate(px * PX, dyPx * PX, pz * PX);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.translate(-px * PX, 0, -pz * PX);
        sink.part(model, pose);
        pose.popPose();
    }

    private static void moved(MachineRender.PartSink sink, ModelResourceLocation model, PoseStack pose, float dxPx, float dyPx,
                              float dzPx) {
        pose.pushPose();
        pose.translate(dxPx * PX, dyPx * PX, dzPx * PX);
        sink.part(model, pose);
        pose.popPose();
    }

    /** The whole footprint (the models extend past the controller block). */
    @Override
    public AABB getRenderBoundingBox(AssemblerBlockEntity be) {
        return MachineRender.footprintBox(be.getBlockPos(), be.facing(), be.shape());
    }
}
