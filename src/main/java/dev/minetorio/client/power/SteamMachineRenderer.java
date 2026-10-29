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
 * Bodies and moving parts of the boiler and the steam engine (R19), as separate JSON models, after the
 * Factorio 1.1 sprites and the steam engine's 32-frame animation ({@code art/gen_models.py}). Models are
 * authored in the north orientation around the block on the footprint's center (−16..32), the moving ones
 * in their rest pose (engine angle 0: what a ghost shows).
 * <ul>
 *   <li>Boiler (3×2): "boiler_body", plus "boiler_fire" (flickering fire strip) drawn at full brightness
 *       while it burns; smoke and steam puffs come from {@link dev.minetorio.content.power.BoilerBlock}.</li>
 *   <li>Steam engine (3×5, too long for one model), Factorio's three upright tanks in a row:
 *       "steam_engine_body" (middle tank, gear mounting plate, crosshead guide) at the center,
 *       "steam_engine_front" and "steam_engine_rear" (end tanks and the blue fluid connections) 1.5 blocks
 *       before and behind it. While the engine produces: the gear cluster on the east side turns about the
 *       width axis (the small gears the other way), the big gear's crank pin drives the connecting rod
 *       ("steam_engine_rod") and the crosshead ("steam_engine_crosshead") up and down its guide, the
 *       flywheel at the front end turns, and the three valve stems ("steam_engine_valve") work up and
 *       down a third of a turn apart.</li>
 * </ul>
 */
public abstract class SteamMachineRenderer implements BlockEntityRenderer<SteamMachineBlockEntity> {
    public static final ModelResourceLocation BOILER_BODY = MachineRender.model("block/boiler_body");
    public static final ModelResourceLocation BOILER_FIRE = MachineRender.model("block/boiler_fire");
    public static final ModelResourceLocation ENGINE_BODY = MachineRender.model("block/steam_engine_body");
    public static final ModelResourceLocation ENGINE_FRONT = MachineRender.model("block/steam_engine_front");
    public static final ModelResourceLocation ENGINE_REAR = MachineRender.model("block/steam_engine_rear");
    public static final ModelResourceLocation ENGINE_GEAR = MachineRender.model("block/steam_engine_gear");
    public static final ModelResourceLocation ENGINE_GEAR_SMALL = MachineRender.model("block/steam_engine_gear_small");
    public static final ModelResourceLocation ENGINE_FLYWHEEL = MachineRender.model("block/steam_engine_flywheel");
    public static final ModelResourceLocation ENGINE_ROD = MachineRender.model("block/steam_engine_rod");
    public static final ModelResourceLocation ENGINE_CROSSHEAD = MachineRender.model("block/steam_engine_crosshead");
    public static final ModelResourceLocation ENGINE_VALVE = MachineRender.model("block/steam_engine_valve");
    /** Every model this renderer draws (for model registration). */
    public static final ModelResourceLocation[] MODELS = {BOILER_BODY, BOILER_FIRE, ENGINE_BODY, ENGINE_FRONT, ENGINE_REAR,
        ENGINE_GEAR, ENGINE_GEAR_SMALL, ENGINE_FLYWHEEL, ENGINE_ROD, ENGINE_CROSSHEAD, ENGINE_VALVE};

    private static final float PX = 1 / 16f;

    protected final ModelBlockRenderer modelRenderer;

    protected SteamMachineRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(SteamMachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        renderParts(be, partialTick, pose, MachineRender.sink(modelRenderer, buffers, be, light, overlay));
    }

    /** Draws the machine's parts from the controller block's corner. */
    protected abstract void renderParts(SteamMachineBlockEntity be, float partialTick, PoseStack pose, MachineRender.PartSink sink);

    /** Draws {@code model} with its block-space origin shifted by {@code dz} blocks along the model's z. */
    protected static void draw(ModelResourceLocation model, double dz, PoseStack pose, MachineRender.PartSink sink, boolean glow) {
        pose.pushPose();
        pose.translate(-0.5, 0, -0.5 + dz);
        sink.part(model, pose, false, glow);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(SteamMachineBlockEntity be) {
        return MachineRender.footprintBox(be.getBlockPos(), be.facing(), be.shape());
    }

    public static final class Boiler extends SteamMachineRenderer {
        public Boiler(BlockEntityRendererProvider.Context ctx) { super(ctx); }

        @Override
        protected void renderParts(SteamMachineBlockEntity be, float partialTick, PoseStack pose, MachineRender.PartSink sink) {
            assemble(be.facing(), be.shape(), be.isWorking(), pose, sink);
        }

        /** Lays out the boiler from the controller block's corner, with its fire while {@code burning}. */
        public static void assemble(Direction facing, MultiblockShape shape, boolean burning, PoseStack pose, MachineRender.PartSink sink) {
            pose.pushPose();
            MachineRender.toCenter(pose, facing, shape);
            draw(BOILER_BODY, 0, pose, sink, false);
            if (burning) draw(BOILER_FIRE, 0, pose, sink, true);
            pose.popPose();
        }
    }

    public static final class Engine extends SteamMachineRenderer {
        /** The end models are authored around the center block and drawn this many blocks before and behind it. */
        private static final double END_SHIFT = 1.5;
        /** The gear models are authored around this point (model pixels, y and z), their pivot. */
        private static final float GEAR_CENTER = 8;
        private static final Gear[] GEARS = {
            new Gear(ENGINE_GEAR, 15, 10, 1, 0),
            new Gear(ENGINE_GEAR_SMALL, 12, -6, -18 / 11f, 11.25f),
            new Gear(ENGINE_GEAR_SMALL, 21, 25, -18 / 11f, 11.25f),
            new Gear(ENGINE_FLYWHEEL, 14, -23, 1, 0),
        };
        // crank train (center-frame px, art/gen_models.py ENG_CRANK_R, ENG_ROD_L): the pin sits CRANK_R off the
        // big gear's axis (at its top at angle 0), the rod reaches ROD_L up from it to the crosshead
        static final float CRANK_Y = 15, CRANK_Z = 10, CRANK_R = 5, ROD_L = 10;
        /** Valve stem stroke (px) and the tanks' offsets along the engine (blocks): front, middle, rear. */
        static final float VALVE_STROKE_PX = 1.5f;
        private static final double[] TANKS = {-END_SHIFT, 0, END_SHIFT};

        /**
         * A gear drawn with its pivot at (y, z) center-frame pixels (art/gen_models.py ENG_GEARS, ENG_FLYWHEEL),
         * turning {@code ratio} times per engine turn (negative: the other way), offset by {@code phase} degrees.
         */
        private record Gear(ModelResourceLocation model, float y, float z, float ratio, float phase) {}

        public Engine(BlockEntityRendererProvider.Context ctx) { super(ctx); }

        @Override
        protected void renderParts(SteamMachineBlockEntity be, float partialTick, PoseStack pose, MachineRender.PartSink sink) {
            assemble(be.facing(), be.shape(), be.renderAngle(partialTick), pose, sink);
        }

        /** Crank pin position (center-frame px {y, z}) at engine angle {@code deg} (the big gear turned about +x). */
        static float[] crankPin(float deg) {
            double a = Math.toRadians(deg);
            return new float[] {CRANK_Y + CRANK_R * (float) Math.cos(a), CRANK_Z + CRANK_R * (float) Math.sin(a)};
        }

        /** Connecting rod's turn about +x (degrees) so that it reaches from the pin back over the guide (z = CRANK_Z). */
        static float rodAngle(float deg) {
            return (float) Math.toDegrees(Math.asin((CRANK_Z - crankPin(deg)[1]) / ROD_L));
        }

        /** Crosshead height over its rest height (px): 0 at angle 0, lowest (-2 crank radii) half a turn later. */
        static float crossheadDrop(float deg) {
            float[] pin = crankPin(deg);
            double dz = CRANK_Z - pin[1];
            return (float) (pin[0] + Math.sqrt(ROD_L * ROD_L - dz * dz)) - (CRANK_Y + CRANK_R + ROD_L);
        }

        /** Lift (px) of tank {@code k}'s valve stem (0 front, 1 middle, 2 rear), a third of a turn apart. */
        static float valveLift(float deg, int k) {
            return VALVE_STROKE_PX * (0.5f - 0.5f * (float) Math.cos(Math.toRadians(deg + 120 * (k - 1))));
        }

        /** Lays out the engine from the controller block's corner, the moving parts posed for engine angle {@code angle}. */
        public static void assemble(Direction facing, MultiblockShape shape, float angle, PoseStack pose, MachineRender.PartSink sink) {
            pose.pushPose();
            MachineRender.toCenter(pose, facing, shape);
            draw(ENGINE_BODY, 0, pose, sink, false);
            draw(ENGINE_FRONT, -END_SHIFT, pose, sink, false);
            draw(ENGINE_REAR, END_SHIFT, pose, sink, false);
            for (Gear gear : GEARS) {
                pose.pushPose();
                pose.translate(0, gear.y() * PX, (gear.z() - GEAR_CENTER) * PX);
                pose.mulPose(Axis.XP.rotationDegrees(angle * gear.ratio() + gear.phase()));
                pose.translate(-0.5, -GEAR_CENTER * PX, -GEAR_CENTER * PX);
                sink.part(gear.model(), pose);
                pose.popPose();
            }
            // connecting rod from the crank pin, the crosshead it drives up and down
            float[] pin = crankPin(angle);
            pose.pushPose();
            pose.translate(-0.5, 0, -0.5);
            pose.pushPose();
            pose.translate(0, pin[0] * PX, pin[1] * PX);
            pose.mulPose(Axis.XP.rotationDegrees(rodAngle(angle)));
            pose.translate(0, -(CRANK_Y + CRANK_R) * PX, -CRANK_Z * PX);
            sink.part(ENGINE_ROD, pose);
            pose.popPose();
            pose.pushPose();
            pose.translate(0, crossheadDrop(angle) * PX, 0);
            sink.part(ENGINE_CROSSHEAD, pose);
            pose.popPose();
            for (int k = 0; k < TANKS.length; k++) {
                pose.pushPose();
                pose.translate(0, valveLift(angle, k) * PX, TANKS[k]);
                sink.part(ENGINE_VALVE, pose);
                pose.popPose();
            }
            pose.popPose();
            pose.popPose();
        }
    }
}
