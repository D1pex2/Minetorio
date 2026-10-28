package dev.minetorio.client.robot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.minetorio.client.inserter.InserterRenderer;
import dev.minetorio.client.machine.AssemblerRenderer;
import dev.minetorio.client.machine.DrillRenderer;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.client.map.RadarRenderer;
import dev.minetorio.client.power.SteamMachineRenderer;
import dev.minetorio.client.research.LabRenderer;
import dev.minetorio.content.inserter.InserterBlock;
import dev.minetorio.content.machine.AssemblerBlock;
import dev.minetorio.content.machine.BurnerDrillBlock;
import dev.minetorio.content.machine.ElectricDrillBlock;
import dev.minetorio.content.map.RadarBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.power.BoilerBlock;
import dev.minetorio.content.power.ElectricPoleBlock;
import dev.minetorio.content.power.SteamEngineBlock;
import dev.minetorio.content.research.LabBlock;
import dev.minetorio.content.robot.GhostBlockEntity;
import dev.minetorio.content.robot.RoboportBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a ghost exactly as its target looks once built, translucent and tinted blue like Factorio's
 * entity ghosts: the target's block model (in the target state's rotation, as the chunk renderer
 * would draw it), and for machines whose parts a block entity renderer draws, those parts laid out
 * by that renderer's own {@code assemble} method (resting animation: gears, heads and dishes at angle
 * 0, no fire or glow) from the controller cell, which is where a multiblock ghost sits. A pole shows
 * all its three blocks and an inserter its resting arm. The whole ghost is inflated a hair about its
 * bottom center so faces lying on a neighbour's never z-fight.
 */
public class GhostRenderer implements BlockEntityRenderer<GhostBlockEntity> {
    public static final float R = 0.55f, G = 0.78f, B = 1.0f, A = 0.45f;
    /** Relative growth about the bottom center and lift (blocks) against z-fighting. */
    private static final float INFLATE = 0.002f;
    private static final float LIFT = 0.001f;
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    public GhostRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(GhostBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.isValid()) return;
        BlockState target = be.target();
        Block block = target.getBlock();
        VertexConsumer vc = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        pose.pushPose();
        double[] c = block instanceof MultiblockControllerBlock mb ? MachineRender.center(facing(be), mb.shape()) : new double[] {0.5, 0.5};
        pose.translate(c[0], LIFT, c[1]);
        pose.scale(1 + INFLATE, 1 + INFLATE, 1 + INFLATE);
        pose.translate(-c[0], 0, -c[1]);

        if (block instanceof ElectricPoleBlock) {
            for (int i = 0; i < ElectricPoleBlock.HEIGHT; i++) {
                pose.pushPose();
                pose.translate(0, i, 0);
                BlockState part = target.setValue(ElectricPoleBlock.PART, i);
                quads(blockModel(part), part, pose, vc);
                pose.popPose();
            }
        } else {
            quads(blockModel(target), target, pose, vc);
        }
        if (block instanceof MultiblockControllerBlock mb) {
            assemble(target, facing(be), mb.shape(), pose, (model, p, translucent, glow) ->
                    quads(Minecraft.getInstance().getModelManager().getModel(model), null, p, vc));
        } else if (block instanceof InserterBlock inserter && target.hasProperty(InserterBlock.FACING)) {
            InserterRenderer.drawArm(buffers.getBuffer(RenderType.entityTranslucent(InserterRenderer.ARM_TEXTURE)), pose.last(),
                    inserter.type(), target.getValue(InserterBlock.FACING), 0, LightTexture.FULL_BRIGHT, GhostRenderer::tint);
        }
        pose.popPose();
    }

    /**
     * The parts a machine's block entity renderer draws (its block model is empty or only the body),
     * at rest, from the controller cell. Nothing for machines drawn by their block model alone.
     */
    private static void assemble(BlockState target, Direction facing, MultiblockShape shape, PoseStack pose, MachineRender.PartSink sink) {
        Block block = target.getBlock();
        if (block instanceof ElectricDrillBlock) DrillRenderer.assemble(DrillRenderer.ELECTRIC, facing, shape, 0, pose, sink);
        else if (block instanceof BurnerDrillBlock) DrillRenderer.assemble(DrillRenderer.BURNER, facing, shape, 0, pose, sink);
        else if (block instanceof AssemblerBlock) AssemblerRenderer.assemble(target, facing, shape, 0, false, pose, sink);
        else if (block instanceof LabBlock) LabRenderer.assemble(facing, shape, 0, false, pose, sink);
        else if (block instanceof RadarBlock) RadarRenderer.assemble(facing, shape, 0, pose, sink);
        else if (block instanceof RoboportBlock) RoboportRenderer.assemble(facing, shape, pose, sink);
        else if (block instanceof BoilerBlock) SteamMachineRenderer.Boiler.assemble(facing, shape, false, pose, sink);
        else if (block instanceof SteamEngineBlock) SteamMachineRenderer.Engine.assemble(facing, shape, 0, pose, sink);
        else if (block instanceof dev.minetorio.content.fluid.StorageTankBlock) dev.minetorio.client.power.StorageTankRenderer.assemble(facing, pose, sink);
    }

    /** The facing the built machine will have: its state's, else the look direction the ghost was placed with. */
    private static Direction facing(GhostBlockEntity be) {
        BlockState target = be.target();
        return target.hasProperty(MultiblockControllerBlock.FACING) ? target.getValue(MultiblockControllerBlock.FACING) : be.facing();
    }

    private static BakedModel blockModel(BlockState state) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
    }

    /** An ARGB color tinted ghost blue at the ghost's alpha. */
    private static int tint(int argb) {
        int r = (int) (((argb >> 16) & 0xFF) * R);
        int g = (int) (((argb >> 8) & 0xFF) * G);
        int b = (int) ((argb & 0xFF) * B);
        return ((int) (A * 255) << 24) | (r << 16) | (g << 8) | b;
    }

    private static void quads(BakedModel model, BlockState state, PoseStack pose, VertexConsumer vc) {
        RandomSource r = RandomSource.create(42);
        for (Direction d : SIDES) {
            r.setSeed(42);
            for (var q : model.getQuads(state, d, r, ModelData.EMPTY, null)) {
                vc.putBulkData(pose.last(), q, R, G, B, A, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(GhostBlockEntity be) {
        if (be.target().getBlock() instanceof MultiblockControllerBlock mb) {
            // a block above for parts drawn over the footprint (the electric drill's top, the radar's dish)
            return MachineRender.footprintBox(be.getBlockPos(), facing(be), mb.shape()).expandTowards(0, 1, 0);
        }
        return new AABB(be.getBlockPos()).inflate(2).expandTowards(0, ElectricPoleBlock.HEIGHT - 1, 0);
    }
}
