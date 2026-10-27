package dev.minetorio.client.power;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.power.SteamMachineBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Bodies and moving parts of the boiler and the steam engine (R19), as separate JSON models so
 * Task 18 swaps models only. Models are authored in the north orientation around the block on the
 * footprint's center (−16..32).
 * <ul>
 *   <li>Boiler (3×2): "boiler_body", plus "boiler_fire" drawn at full brightness while it burns.</li>
 *   <li>Steam engine (3×5, too long for one model): "steam_engine_body" (half of the bed with a
 *       pipe port at its end and crankshaft pedestals) covers 2.5 blocks and is drawn twice, the
 *       front half and the same half turned 180° about the center; "steam_engine_center" (the long
 *       cylinder, valve chest and crosshead guides) once, half a block forward;
 *       "steam_engine_flywheel" (spoked flywheel and crankshaft) turns about the width axis through
 *       its pivot near the back end while the engine produces, and "steam_engine_piston" (rod and
 *       crosshead) slides along the length in step with it.</li>
 * </ul>
 */
public abstract class SteamMachineRenderer implements BlockEntityRenderer<SteamMachineBlockEntity> {
    public static final ModelResourceLocation BOILER_BODY = MachineRender.model("block/boiler_body");
    public static final ModelResourceLocation BOILER_FIRE = MachineRender.model("block/boiler_fire");
    public static final ModelResourceLocation ENGINE_BODY = MachineRender.model("block/steam_engine_body");
    public static final ModelResourceLocation ENGINE_FLYWHEEL = MachineRender.model("block/steam_engine_flywheel");
    public static final ModelResourceLocation ENGINE_CENTER = MachineRender.model("block/steam_engine_center");
    public static final ModelResourceLocation ENGINE_PISTON = MachineRender.model("block/steam_engine_piston");

    protected final ModelBlockRenderer modelRenderer;

    protected SteamMachineRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(SteamMachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        MachineRender.toCenter(pose, be.facing(), be.shape());
        renderParts(be, partialTick, pose, buffers, light, overlay);
        pose.popPose();
    }

    /** Draws the parts with the pose at the footprint center (model north = facing). */
    protected abstract void renderParts(SteamMachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                        int light, int overlay);

    /** Draws {@code model} with its block-space origin shifted by {@code dz} blocks along the model's z. */
    protected void draw(ModelResourceLocation model, double dz, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(-0.5, 0, -0.5 + dz);
        MachineRender.renderModel(modelRenderer, model, pose, buffers, light, overlay);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(SteamMachineBlockEntity be) {
        return MachineRender.footprintBox(be.getBlockPos(), be.facing(), be.shape());
    }

    public static final class Boiler extends SteamMachineRenderer {
        public Boiler(BlockEntityRendererProvider.Context ctx) { super(ctx); }

        @Override
        protected void renderParts(SteamMachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                   int light, int overlay) {
            draw(BOILER_BODY, 0, pose, buffers, light, overlay);
            if (be.isWorking()) draw(BOILER_FIRE, 0, pose, buffers, LightTexture.FULL_BRIGHT, overlay);
        }
    }

    public static final class Engine extends SteamMachineRenderer {
        /** Body model z range relative to the center is −1.5..+1.0 blocks; this shift makes it cover −2.5..0 (turned 180°: 0..+2.5). */
        private static final double FRONT_HALF = -1.0;
        /** Cylinder and piston model shift: they cover −2.0..+1.0 blocks from the center. */
        private static final double CENTER = -0.5;
        /** Flywheel model shift; its pivot is 17 px up at the model's center, i.e. 1.5 blocks behind the footprint center. */
        private static final double FLYWHEEL = 1.5;
        private static final double FLYWHEEL_PIVOT_Y = 17 / 16.0;
        /** Piston stroke half-length in blocks. */
        private static final double PISTON_THROW = 3 / 16.0;

        public Engine(BlockEntityRendererProvider.Context ctx) { super(ctx); }

        @Override
        protected void renderParts(SteamMachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                   int light, int overlay) {
            draw(ENGINE_BODY, FRONT_HALF, pose, buffers, light, overlay);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180));
            draw(ENGINE_BODY, FRONT_HALF, pose, buffers, light, overlay);
            pose.popPose();
            draw(ENGINE_CENTER, CENTER, pose, buffers, light, overlay);
            float angle = be.renderAngle(partialTick);
            draw(ENGINE_PISTON, CENTER + PISTON_THROW * Math.sin(Math.toRadians(angle)), pose, buffers, light, overlay);
            pose.pushPose();
            pose.translate(0, FLYWHEEL_PIVOT_Y, FLYWHEEL);
            pose.mulPose(Axis.XP.rotationDegrees(angle));
            pose.translate(0, -FLYWHEEL_PIVOT_Y, -FLYWHEEL);
            draw(ENGINE_FLYWHEEL, FLYWHEEL, pose, buffers, light, overlay);
            pose.popPose();
        }
    }
}
