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
 * Bodies and moving parts of the boiler and the steam engine (R19), as separate JSON models so
 * Task 18 swaps models only. Models are authored in the north orientation around the block on the
 * footprint's center (−16..32).
 * <ul>
 *   <li>Boiler (3×2): "boiler_body", plus "boiler_fire" drawn at full brightness while it burns.</li>
 *   <li>Steam engine (3×5, too long for one model), Factorio's three upright tanks in a row:
 *       "steam_engine_body" (middle tank, gear mounting plate) at the center, "steam_engine_front" and
 *       "steam_engine_rear" (end tanks and the blue fluid connections) 1.5 blocks before and behind it,
 *       and a cluster of gears on the east side ("steam_engine_gear", "steam_engine_gear_small") that
 *       turn about the width axis while the engine produces, the small ones the other way.</li>
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

    protected final ModelBlockRenderer modelRenderer;

    protected SteamMachineRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(SteamMachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        renderParts(be, partialTick, pose, MachineRender.sink(modelRenderer, buffers, light, overlay));
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
        };

        /**
         * A gear drawn with its pivot at (y, z) center-frame pixels (art/gen_models.py ENG_GEARS), turning
         * {@code ratio} times per engine turn (negative: the other way), offset by {@code phase} degrees.
         */
        private record Gear(ModelResourceLocation model, float y, float z, float ratio, float phase) {}

        public Engine(BlockEntityRendererProvider.Context ctx) { super(ctx); }

        @Override
        protected void renderParts(SteamMachineBlockEntity be, float partialTick, PoseStack pose, MachineRender.PartSink sink) {
            assemble(be.facing(), be.shape(), be.renderAngle(partialTick), pose, sink);
        }

        /** Lays out the engine from the controller block's corner, the gears turned for engine angle {@code angle}. */
        public static void assemble(Direction facing, MultiblockShape shape, float angle, PoseStack pose, MachineRender.PartSink sink) {
            pose.pushPose();
            MachineRender.toCenter(pose, facing, shape);
            draw(ENGINE_BODY, 0, pose, sink, false);
            draw(ENGINE_FRONT, -END_SHIFT, pose, sink, false);
            draw(ENGINE_REAR, END_SHIFT, pose, sink, false);
            for (Gear gear : GEARS) {
                pose.pushPose();
                pose.translate(0, gear.y() / 16f, (gear.z() - GEAR_CENTER) / 16f);
                pose.mulPose(Axis.XP.rotationDegrees(angle * gear.ratio() + gear.phase()));
                pose.translate(-0.5, -GEAR_CENTER / 16f, -GEAR_CENTER / 16f);
                sink.part(gear.model(), pose);
                pose.popPose();
            }
            pose.popPose();
        }
    }
}
