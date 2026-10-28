package dev.minetorio.client.robot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.robot.RoboportBlockEntity;
import dev.minetorio.core.robot.RoboportAnimation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Draws a roboport. The 4×4×4 machine is bigger than one block model may be (−16..32), so
 * art/gen_robots.py cuts its static parts into eight pieces — x half {@code i}, z half {@code j},
 * lower/upper {@code k} (the cut is at y 28 px; the upper pieces start at 40 px and reach the 64 px
 * top) — each authored around its own origin: piece px = machine px − {@link #offset}, with the
 * machine authored around the footprint's center at the bottom, north orientation.
 *
 * <p>Over the pieces it draws the moving and lit parts ({@link #ANIMATED}), each a small model
 * authored around a pivot at model (8, 0, 8) and placed at a pivot in machine px (all must match
 * art/gen_robots.py and the roboport composite of ModelZFightTest): the eight iris blades of the roof
 * hatch (opening: each turns {@value #BLADE_TWIST}° about the hatch axis and slides
 * {@value #BLADE_SLIDE} px out under the collar), the antenna head turning on its mast, the charging
 * points lit while robots charge and the blinking status lamps ({@link RoboportAnimation}). At rest
 * ({@link Anim#REST}: door closed, antenna unturned, nothing lit) — what ghosts draw — only the pieces,
 * the closed blades and the antenna remain.
 */
public class RoboportRenderer implements BlockEntityRenderer<RoboportBlockEntity> {
    /** The static parts. */
    public static final List<ModelResourceLocation> PIECES = new ArrayList<>();
    private static final List<double[]> OFFSETS = new ArrayList<>();

    public static final ModelResourceLocation BLADE = MachineRender.model("block/roboport_blade");
    public static final ModelResourceLocation ANTENNA = MachineRender.model("block/roboport_antenna");
    public static final ModelResourceLocation CHARGE_GLOW = MachineRender.model("block/roboport_charge_glow");
    public static final ModelResourceLocation LAMP_GREEN = MachineRender.model("block/roboport_lamp_green");
    public static final ModelResourceLocation LAMP_RED = MachineRender.model("block/roboport_lamp_red");
    /** The moving and lit parts (registered with the pieces). */
    public static final List<ModelResourceLocation> ANIMATED = List.of(BLADE, ANTENNA, CHARGE_GLOW, LAMP_GREEN, LAMP_RED);

    // art/gen_robots.py: HATCH, BLADE_*, ANTENNA, CHARGE, LAMPS (machine px)
    static final double HATCH_X = 0, HATCH_Z = 1;
    static final double BLADE_Y = 52.2, BLADE_STEP = 0.1;
    static final double BLADE_TWIST = 60, BLADE_SLIDE = 8.3;
    static final double[] ANTENNA_PIVOT = {14, 58, 21};
    static final double[][] CHARGE = {{-27.5, 60, -27.5}, {27.5, 60, -27.5}, {-27.5, 60, 27.5}, {27.5, 60, 27.5}};
    static final double[][] LAMPS = {{-16, 17, -28.5}, {-6, 17, -28.5}, {24.5, 17, -9}, {-24.5, 17, 9},
        {-31.25, 7, -31.25}, {31.25, 7, -31.25}, {-31.25, 7, 31.25}, {31.25, 7, 31.25}};

    static {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int k = 0; k < 2; k++) {
                    PIECES.add(MachineRender.model("block/roboport_" + i + j + k));
                    OFFSETS.add(offset(i, j, k));
                }
            }
        }
    }

    /** Piece origin in machine pixels (must match art/gen_robots.py). */
    static double[] offset(int i, int j, int k) {
        return new double[] {-16 + 16 * i, 40 * k, -16 + 16 * j};
    }

    /**
     * The animation pose: door 0 closed .. 1 open (eased), antenna angle in degrees, robots charging,
     * lamps lit as a bit mask over {@link #LAMPS} (green with power, red without), and the game time
     * (with the partial tick) for the charging points' flicker.
     */
    public record Anim(float door, float antenna, int charging, int lamps, boolean powered, long time, float partialTick) {
        public static final Anim REST = new Anim(0, 0, 0, 0, false, 0, 0);

        static Anim of(RoboportBlockEntity be, float partialTick) {
            long time = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
            int lamps = 0;
            for (int i = 0; i < LAMPS.length; i++) {
                if (RoboportAnimation.lampOn(time, i, be.isPowered())) lamps |= 1 << i;
            }
            return new Anim(be.renderDoor(partialTick), be.renderAntenna(partialTick), be.chargingRobots(), lamps,
                    be.isPowered(), time, partialTick);
        }
    }

    private final ModelBlockRenderer modelRenderer;

    public RoboportRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(RoboportBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        assemble(be.facing(), be.shape(), Anim.of(be, partialTick), pose, MachineRender.sink(modelRenderer, buffers, light, overlay));
    }

    /** Lays out the roboport at rest (door closed, nothing lit) from the controller block's corner. */
    public static void assemble(Direction facing, MultiblockShape shape, PoseStack pose, MachineRender.PartSink sink) {
        assemble(facing, shape, Anim.REST, pose, sink);
    }

    /** Lays out the roboport's pieces and its moving and lit parts in {@code anim}'s pose, from the controller block's corner. */
    public static void assemble(Direction facing, MultiblockShape shape, Anim anim, PoseStack pose, MachineRender.PartSink sink) {
        pose.pushPose();
        MachineRender.toCenter(pose, facing, shape);
        for (int p = 0; p < PIECES.size(); p++) {
            double[] o = OFFSETS.get(p);
            pose.pushPose();
            pose.translate(o[0] / 16, o[1] / 16, o[2] / 16);
            sink.part(PIECES.get(p), pose);
            pose.popPose();
        }
        // iris blades: once fully open they are hidden under the collar
        if (anim.door() < 1) {
            for (int k = 0; k < 8; k++) {
                place(pose, sink, BLADE, HATCH_X, BLADE_Y + k * BLADE_STEP, HATCH_Z,
                        45 * k + anim.door() * BLADE_TWIST, anim.door() * BLADE_SLIDE, 1, false);
            }
        }
        place(pose, sink, ANTENNA, ANTENNA_PIVOT[0], ANTENNA_PIVOT[1], ANTENNA_PIVOT[2], anim.antenna(), 0, 1, false);
        for (int i = 0; i < Math.min(anim.charging(), CHARGE.length); i++) {
            float pulse = RoboportAnimation.chargePulse(anim.time(), anim.partialTick(), i);
            if (pulse > 0) place(pose, sink, CHARGE_GLOW, CHARGE[i][0], CHARGE[i][1], CHARGE[i][2], 0, 0, pulse, true);
        }
        for (int i = 0; i < LAMPS.length; i++) {
            if ((anim.lamps() & (1 << i)) != 0) {
                place(pose, sink, anim.powered() ? LAMP_GREEN : LAMP_RED, LAMPS[i][0], LAMPS[i][1], LAMPS[i][2], 0, 0, 1, true);
            }
        }
        pose.popPose();
    }

    /**
     * Draws a part authored around model (8, 0, 8) with that point at machine px (x, y, z), turned
     * {@code yaw}° about the vertical, slid {@code slide} px along its own +x and scaled about the pivot.
     */
    private static void place(PoseStack pose, MachineRender.PartSink sink, ModelResourceLocation model, double x, double y,
                              double z, double yaw, double slide, float scale, boolean glow) {
        pose.pushPose();
        pose.translate(x / 16, y / 16, z / 16);
        if (yaw != 0) pose.mulPose(Axis.YP.rotationDegrees((float) yaw));
        if (slide != 0) pose.translate(slide / 16, 0, 0);
        if (scale != 1) pose.scale(scale, scale, scale);
        pose.translate(-0.5, 0, -0.5);
        sink.part(model, pose, false, glow);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(RoboportBlockEntity be) {
        return be.renderBox();
    }
}
