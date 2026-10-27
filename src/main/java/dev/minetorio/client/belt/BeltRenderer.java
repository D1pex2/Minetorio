package dev.minetorio.client.belt;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.content.belt.BeltBlockEntity;
import dev.minetorio.content.belt.BeltGeometry;
import dev.minetorio.content.belt.LaneSide;
import dev.minetorio.core.belt.BeltLane;
import dev.minetorio.core.belt.BeltShape;
import dev.minetorio.core.belt.BeltSlope;
import dev.minetorio.core.belt.LaneItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Draws the items on a belt's two lanes (±0.25 from the center line), following curves and ramps. */
public class BeltRenderer implements BlockEntityRenderer<BeltBlockEntity> {
    private final LaneItemRenderer items;

    public BeltRenderer(BlockEntityRendererProvider.Context ctx) {
        items = new LaneItemRenderer(ctx.getItemRenderer());
    }

    @Override
    public void render(BeltBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.facing();
        BeltShape shape = be.shape();
        BeltSlope slope = be.slope();
        double speed = be.tier().speed();
        int seed = (int) be.getBlockPos().asLong();
        for (LaneSide side : LaneSide.values()) {
            BeltLane lane = be.lane(side);
            for (LaneItem it : lane.items()) {
                double p = be.tracker().renderPos(it, speed, partialTick);
                double t = Math.min(1, p / lane.length());
                double[] xz = BeltGeometry.lanePoint(facing, shape, side, t);
                double[] dir = BeltGeometry.laneTangent(facing, shape, t);
                items.draw(it.item(), xz[0], slope.rise(t), xz[1], dir[0], dir[1], slope.grade(), be.getLevel(), pose, buffers,
                        light, seed);
            }
        }
    }

    /** Items on a ramp rise up to the top of the cell and a bit above (the next belt's plate is 2 px higher). */
    @Override
    public AABB getRenderBoundingBox(BeltBlockEntity be) {
        AABB box = new AABB(be.getBlockPos());
        return be.slope() == BeltSlope.FLAT ? box : box.expandTowards(0, 1, 0);
    }
}
