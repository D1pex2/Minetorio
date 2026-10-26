package dev.minetorio.client.belt;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.content.belt.BeltBlockEntity;
import dev.minetorio.content.belt.BeltGeometry;
import dev.minetorio.content.belt.LaneSide;
import dev.minetorio.core.belt.BeltLane;
import dev.minetorio.core.belt.BeltShape;
import dev.minetorio.core.belt.LaneItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/** Draws the items on a belt's two lanes (±0.25 from the center line), following curves. */
public class BeltRenderer implements BlockEntityRenderer<BeltBlockEntity> {
    private final LaneItemRenderer items;

    public BeltRenderer(BlockEntityRendererProvider.Context ctx) {
        items = new LaneItemRenderer(ctx.getItemRenderer());
    }

    @Override
    public void render(BeltBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.facing();
        BeltShape shape = be.shape();
        double speed = be.tier().speed();
        int seed = (int) be.getBlockPos().asLong();
        for (LaneSide side : LaneSide.values()) {
            BeltLane lane = be.lane(side);
            for (LaneItem it : lane.items()) {
                double p = be.tracker().renderPos(it, speed, partialTick);
                double[] xz = BeltGeometry.lanePoint(facing, shape, side, Math.min(1, p / lane.length()));
                items.draw(it.item(), xz[0], xz[1], facing, be.getLevel(), pose, buffers, light, seed);
            }
        }
    }
}
