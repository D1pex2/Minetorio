package dev.minetorio.client.belt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.content.belt.LaneStacks;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Draws one lane item lying on a belt surface. */
final class LaneItemRenderer {
    static final float SCALE = 0.4f;
    /** Top of the belt plate. */
    static final double SURFACE = 2.0 / 16;

    private final ItemRenderer items;
    private final Map<String, ItemStack> stacks = new HashMap<>();

    LaneItemRenderer(ItemRenderer items) {
        this.items = items;
    }

    /** Draws {@code id} at block-local (x, z) on the belt surface, facing {@code facing}. */
    void draw(String id, double x, double z, Direction facing, Level level, PoseStack pose,
              MultiBufferSource buffers, int light, int seed) {
        ItemStack stack = stacks.computeIfAbsent(id, LaneStacks::stack);
        if (stack.isEmpty()) return;
        BakedModel model = items.getModel(stack, level, null, seed);
        pose.pushPose();
        if (model.isGui3d()) {
            // Block items: a small upright cube standing on the belt.
            pose.translate(x, SURFACE + 0.5 * 0.5 * SCALE, z);
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            pose.scale(SCALE, SCALE, SCALE);
        } else {
            // Flat items: lying on the belt like Factorio icons, top of the icon pointing forward.
            pose.translate(x, SURFACE + 0.01, z);
            pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(SCALE, SCALE, SCALE);
        }
        items.render(stack, ItemDisplayContext.FIXED, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
    }
}
