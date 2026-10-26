package dev.minetorio.client.belt;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.content.belt.BeltGeometry;
import dev.minetorio.content.belt.LaneSide;
import dev.minetorio.content.belt.UndergroundBeltBlock;
import dev.minetorio.content.belt.UndergroundBeltBlockEntity;
import dev.minetorio.core.belt.LaneItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/**
 * Draws the visible items of an underground belt piece: an entrance shows the first half block of
 * its lanes (the rest runs underground), an exit the second half of its lanes.
 */
public class UndergroundBeltRenderer implements BlockEntityRenderer<UndergroundBeltBlockEntity> {
    private final LaneItemRenderer items;

    public UndergroundBeltRenderer(BlockEntityRendererProvider.Context ctx) {
        items = new LaneItemRenderer(ctx.getItemRenderer());
    }

    @Override
    public void render(UndergroundBeltBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Direction facing = be.facing();
        boolean input = be.type() == UndergroundBeltBlock.Type.INPUT;
        double speed = be.tier().speed();
        int seed = (int) be.getBlockPos().asLong();
        for (LaneSide side : LaneSide.values()) {
            double lat = side == LaneSide.LEFT ? -BeltGeometry.LANE_OFFSET : BeltGeometry.LANE_OFFSET;
            for (LaneItem it : be.lane(side).items()) {
                double p = be.tracker().renderPos(it, speed, partialTick);
                if (input ? p > 0.5 : p < 0.5) continue; // under the hood
                double[] xz = BeltGeometry.local(facing, p - 0.5, lat);
                items.draw(it.item(), xz[0], xz[1], facing.getStepX(), facing.getStepZ(), be.getLevel(), pose, buffers, light, seed);
            }
        }
    }
}
