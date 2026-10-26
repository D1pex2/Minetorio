package dev.minetorio.client.belt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.content.belt.LaneStacks;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Draws one lane item lying flat on a belt surface, like the item icons on a Factorio belt.
 *
 * <p>Pose, applied in this order to the BER's block-local pose: translate to the lane point just
 * above the 2 px plate; yaw by -facing.toYRot() and pitch +90° about X, so that the icon (an XY quad
 * that the FIXED display context turns to face -Z) lies in the XZ plane, front face up, with the top
 * of the icon pointing along the travel direction; scale 0.4.
 * Block items (3D models) get the same treatment and appear as a small cube on the belt, raised by
 * half their height so they sit on the surface instead of sinking into it.
 */
final class LaneItemRenderer {
    static final float SCALE = 0.4f;
    /** Top of the belt plate. */
    static final double SURFACE = 2.0 / 16;
    /** At most this many stacks with data components are kept parsed. */
    private static final int MAX_COMPONENT_STACKS = 64;

    private final ItemRenderer items;
    /** Plain item ids; bounded by the item registry. */
    private final Map<String, ItemStack> plain = new HashMap<>();
    /** Keys carrying data components ("id#{...}"); LRU so arbitrary NBT cannot grow it. */
    private final Map<String, ItemStack> withComponents = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
            return size() > MAX_COMPONENT_STACKS;
        }
    };

    LaneItemRenderer(ItemRenderer items) {
        this.items = items;
    }

    private ItemStack stack(String key, Level level) {
        Map<String, ItemStack> cache = key.indexOf('#') < 0 ? plain : withComponents;
        ItemStack s = cache.get(key);
        if (s == null) {
            s = LaneStacks.stack(key, level.registryAccess());
            cache.put(key, s);
        }
        return s;
    }

    /** Draws {@code key} at block-local (x, z) on the belt surface, travelling toward {@code facing}. */
    void draw(String key, double x, double z, Direction facing, Level level, PoseStack pose,
              MultiBufferSource buffers, int light, int seed) {
        if (level == null) return;
        ItemStack stack = stack(key, level);
        if (stack.isEmpty()) return;
        BakedModel model = items.getModel(stack, level, null, seed);
        // FIXED-context block models are half a block, so the cube is 0.5 * SCALE tall.
        double lift = model.isGui3d() ? 0.25 * SCALE : 0.02;
        pose.pushPose();
        pose.translate(x, SURFACE + lift, z);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(SCALE, SCALE, SCALE);
        items.render(stack, ItemDisplayContext.FIXED, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
    }
}
