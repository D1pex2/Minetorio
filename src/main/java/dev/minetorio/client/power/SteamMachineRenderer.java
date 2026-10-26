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
 *   <li>Steam engine (3×5, too long for one model): "steam_engine_body" covers 2.5 blocks and is
 *       drawn twice, front half and back half; "steam_engine_center" (cylinder, slide bars, crank
 *       bearing) once; "steam_engine_flywheel" turns about the width axis (pivot 1 block up, at the
 *       center) while the engine produces, and "steam_engine_piston" slides along the length in
 *       step with it.</li>
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
        /** Body model z range relative to the center is −1.5..+1.0 blocks; these shifts make it cover −2.5..0 and 0..+2.5. */
        private static final double FRONT_HALF = -1.0;
        private static final double BACK_HALF = 1.5;
        private static final double FLYWHEEL_PIVOT_Y = 1.0;
        /** Piston stroke half-length in blocks. */
        private static final double PISTON_THROW = 3 / 16.0;

        public Engine(BlockEntityRendererProvider.Context ctx) { super(ctx); }

        @Override
        protected void renderParts(SteamMachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                   int light, int overlay) {
            draw(ENGINE_BODY, FRONT_HALF, pose, buffers, light, overlay);
            draw(ENGINE_BODY, BACK_HALF, pose, buffers, light, overlay);
            draw(ENGINE_CENTER, 0, pose, buffers, light, overlay);
            float angle = be.renderAngle(partialTick);
            draw(ENGINE_PISTON, PISTON_THROW * Math.sin(Math.toRadians(angle)), pose, buffers, light, overlay);
            pose.pushPose();
            pose.translate(0, FLYWHEEL_PIVOT_Y, 0);
            pose.mulPose(Axis.XP.rotationDegrees(angle));
            pose.translate(0, -FLYWHEEL_PIVOT_Y, 0);
            draw(ENGINE_FLYWHEEL, 0, pose, buffers, light, overlay);
            pose.popPose();
        }
    }
}
