package dev.minetorio.client.belt;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.content.belt.BeltGeometry;
import dev.minetorio.content.belt.LaneSide;
import dev.minetorio.content.belt.SplitterBlockEntity;
import dev.minetorio.core.belt.BeltLane;
import dev.minetorio.core.belt.LaneItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Draws a splitter's items: input halves at the back, output halves at the front of both columns. */
public class SplitterRenderer implements BlockEntityRenderer<SplitterBlockEntity> {
    private final LaneItemRenderer items;

    public SplitterRenderer(BlockEntityRendererProvider.Context ctx) {
        items = new LaneItemRenderer(ctx.getItemRenderer());
    }

    @Override
    public void render(SplitterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.facing();
        double speed = be.tier().speed();
        int seed = (int) be.getBlockPos().asLong();
        for (int c = 0; c < 2; c++) {
            for (LaneSide side : LaneSide.values()) {
                double lat = c + (side == LaneSide.LEFT ? -BeltGeometry.LANE_OFFSET : BeltGeometry.LANE_OFFSET);
                drawLane(be, be.input(c, side), 0, lat, facing, speed, partialTick, pose, buffers, light, seed);
                drawLane(be, be.output(c, side), SplitterBlockEntity.HALF, lat, facing, speed, partialTick, pose, buffers, light, seed);
            }
        }
    }

    private void drawLane(SplitterBlockEntity be, BeltLane lane, double start, double lat, Direction facing, double speed,
                          float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int seed) {
        for (LaneItem it : lane.items()) {
            double p = start + be.tracker().renderPos(it, speed, partialTick);
            double[] xz = BeltGeometry.local(facing, p - 0.5, lat);
            items.draw(it.item(), xz[0], xz[1], facing, be.getLevel(), pose, buffers, light, seed);
        }
    }

    /** Both columns. */
    @Override
    public AABB getRenderBoundingBox(SplitterBlockEntity be) {
        return new AABB(be.columnPos(0)).minmax(new AABB(be.columnPos(1)));
    }
}
