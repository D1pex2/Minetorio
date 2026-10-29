package dev.minetorio.client.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.minetorio.Minetorio;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Helpers for block entity renderers of multiblock machines (R19): models authored around one block
 * (−16..32) in the north orientation, drawn on the footprint's center and turned to the facing.
 */
public final class MachineRender {
    private MachineRender() {}

    /** A fixed set of probes outside the machine's own filled volume. */
    public static List<BlockPos> lightSamplePositions(BlockPos origin, Direction facing, MultiblockShape shape) {
        BlockPos far = origin.relative(facing, shape.depth() - 1).relative(facing.getClockWise(), shape.width() - 1);
        int minX = Math.min(origin.getX(), far.getX()), maxX = Math.max(origin.getX(), far.getX());
        int minZ = Math.min(origin.getZ(), far.getZ()), maxZ = Math.max(origin.getZ(), far.getZ());
        int centerX = minX + (maxX - minX) / 2, centerZ = minZ + (maxZ - minZ) / 2;
        int sideY = origin.getY() + (shape.height() - 1) / 2;
        return List.of(new BlockPos(centerX, origin.getY() + shape.height(), centerZ),
                new BlockPos(minX - 1, sideY, centerZ), new BlockPos(maxX + 1, sideY, centerZ),
                new BlockPos(centerX, sideY, minZ - 1), new BlockPos(centerX, sideY, maxZ + 1));
    }

    /** Combines real surrounding block and sky light, without introducing an emissive minimum. */
    public static int exteriorLight(int fallback, int... samples) {
        int block = LightTexture.block(fallback), sky = LightTexture.sky(fallback);
        for (int sample : samples) {
            block = Math.max(block, LightTexture.block(sample));
            sky = Math.max(sky, LightTexture.sky(sample));
        }
        return LightTexture.pack(block, sky);
    }

    public static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, path));
    }

    /** Footprint center relative to the controller block's corner: {x, z}. */
    public static double[] center(Direction facing, MultiblockShape shape) {
        Direction right = facing.getClockWise();
        double w = (shape.width() - 1) / 2.0;
        double d = (shape.depth() - 1) / 2.0;
        return new double[] {
            0.5 + right.getStepX() * w + facing.getStepX() * d,
            0.5 + right.getStepZ() * w + facing.getStepZ() * d
        };
    }

    /**
     * Moves {@code pose} to the footprint's center and turns it so that the north orientation of a
     * model matches {@code facing} (like blockstate "y" rotations); the origin is then the center of
     * the footprint at the bottom, with model north = forward.
     */
    public static void toCenter(PoseStack pose, Direction facing, MultiblockShape shape) {
        double[] c = center(facing, shape);
        pose.translate(c[0], 0, c[1]);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
    }

    /** Draws a standalone block model at the pose (model 0..16 = one block from the pose origin). */
    public static void renderModel(ModelBlockRenderer renderer, ModelResourceLocation location, PoseStack pose,
                                   MultiBufferSource buffers, int light, int overlay) {
        renderModel(renderer, location, pose, buffers.getBuffer(Sheets.solidBlockSheet()), light, overlay);
    }

    /** Like {@link #renderModel(ModelBlockRenderer, ModelResourceLocation, PoseStack, MultiBufferSource, int, int)}, blended (glass). */
    public static void renderTranslucentModel(ModelBlockRenderer renderer, ModelResourceLocation location, PoseStack pose,
                                              MultiBufferSource buffers, int light, int overlay) {
        renderModel(renderer, location, pose, buffers.getBuffer(Sheets.translucentCullBlockSheet()), light, overlay);
    }

    private static void renderModel(ModelBlockRenderer renderer, ModelResourceLocation location, PoseStack pose,
                                    VertexConsumer buffer, int light, int overlay) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(location);
        renderer.renderModel(pose.last(), buffer, null, model, 1f, 1f, 1f, light, overlay, ModelData.EMPTY, null);
    }

    /**
     * Receives the parts of a machine's assembly: a standalone block model drawn at {@code pose}
     * (model 0..16 = one block from the pose origin). Renderers lay their machine out once through a
     * sink, so the block entity renderer (a {@link #sink lit sink}) and the ghost renderer (a
     * translucent tinted one) draw the very same parts at the same places.
     */
    @FunctionalInterface
    public interface PartSink {
        /** {@code translucent}: blended (glass); {@code glow}: drawn at full brightness. */
        void part(ModelResourceLocation model, PoseStack pose, boolean translucent, boolean glow);

        /** An opaque, normally lit part. */
        default void part(ModelResourceLocation model, PoseStack pose) {
            part(model, pose, false, false);
        }
    }

    /** A sink that draws parts like the block entity renderers do: lit by {@code light}, solid or blended. */
    public static PartSink sink(ModelBlockRenderer renderer, MultiBufferSource buffers, int light, int overlay) {
        return (model, pose, translucent, glow) -> {
            int l = glow ? LightTexture.FULL_BRIGHT : light;
            if (translucent) renderTranslucentModel(renderer, model, pose, buffers, l, overlay);
            else renderModel(renderer, model, pose, buffers, l, overlay);
        };
    }

    /**
     * Lit parts sample their exposed surroundings rather than only the controller buried inside
     * the multiblock. Five real-light probes keep sky/day-night shading and nearby block lights;
     * missing chunks are skipped, and only explicitly emissive parts still use full brightness.
     */
    public static PartSink sink(ModelBlockRenderer renderer, MultiBufferSource buffers,
                                MultiblockControllerBlockEntity machine, int light, int overlay) {
        var level = machine.getLevel();
        if (level != null && machine.getBlockState().getBlock() instanceof MultiblockControllerBlock block) {
            var positions = lightSamplePositions(machine.getBlockPos(), machine.getBlockState().getValue(MultiblockControllerBlock.FACING), block.shape());
            int[] samples = new int[positions.size()];
            for (int i = 0; i < positions.size(); i++) {
                BlockPos sample = positions.get(i);
                samples[i] = level.hasChunkAt(sample) ? LevelRenderer.getLightColor(level, sample) : light;
            }
            light = exteriorLight(light, samples);
        }
        return sink(renderer, buffers, light, overlay);
    }

    /** The whole footprint of a machine (models extend past the controller block). */
    public static AABB footprintBox(BlockPos origin, Direction facing, MultiblockShape s) {
        BlockPos far = origin.relative(facing, s.depth() - 1).relative(facing.getClockWise(), s.width() - 1).above(s.height() - 1);
        return new AABB(origin).minmax(new AABB(far));
    }
}
