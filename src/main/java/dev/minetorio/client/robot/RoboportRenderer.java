package dev.minetorio.client.robot;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.robot.RoboportBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Draws a roboport. The 4×4×4 machine is bigger than one block model may be (−16..32), so
 * art/gen_robots.py cuts it into eight pieces — x half {@code i}, z half {@code j}, lower/upper
 * {@code k} (the cut is at y 28 px; the upper pieces start at 40 px and reach the 64 px top) — each
 * authored around its own origin: piece px = machine px − {@link #offset}, with the machine authored
 * around the footprint's center at the bottom, north orientation.
 */
public class RoboportRenderer implements BlockEntityRenderer<RoboportBlockEntity> {
    public static final List<ModelResourceLocation> PIECES = new ArrayList<>();
    private static final List<double[]> OFFSETS = new ArrayList<>();

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

    private final ModelBlockRenderer modelRenderer;

    public RoboportRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(RoboportBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        assemble(be.facing(), be.shape(), pose, MachineRender.sink(modelRenderer, buffers, light, overlay));
    }

    /** Lays out the roboport's pieces from the controller block's corner. */
    public static void assemble(Direction facing, MultiblockShape shape, PoseStack pose, MachineRender.PartSink sink) {
        pose.pushPose();
        MachineRender.toCenter(pose, facing, shape);
        for (int p = 0; p < PIECES.size(); p++) {
            double[] o = OFFSETS.get(p);
            pose.pushPose();
            pose.translate(o[0] / 16, o[1] / 16, o[2] / 16);
            sink.part(PIECES.get(p), pose);
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(RoboportBlockEntity be) {
        return be.renderBox();
    }
}
