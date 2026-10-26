package dev.minetorio.client.belt;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.content.belt.LaneStacks;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Draws one lane item lying flat on a belt surface, like the item icons on a Factorio belt.
 *
 * <p>The model is rendered with {@code ItemDisplayContext.NONE} (no display transform; raw model
 * space, a generated icon being the XY quad with its front toward +Z). Pose, in this order on the
 * BER's block-local pose: translate to the lane point just above the 2 px plate → multiply by the
 * {@link LanePose} basis (icon front → +Y, icon top → travel tangent) → scale 0.4 → (inside
 * ItemRenderer) translate(-0.5) to centre the model. Net effect on a vertex v of the icon:
 * {@code T(lanePoint) · B · S(0.4) · T(-0.5) · v}, so the quad's normal (0,0,1) becomes (0,1,0).
 * Block items use an upright basis and stand as a 0.2 cube resting on the plate.
 */
final class LaneItemRenderer {
    static final float SCALE = 0.4f;
    /** Block items: a 0.2 cube (the FIXED-frame size of a block at item scale 0.4). */
    static final float BLOCK_SCALE = 0.2f;
    /** Flat icons float this far above the plate (half the icon's 1/16 thickness at 0.4, plus a gap). */
    static final double FLAT_LIFT = 0.02;
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

    /**
     * Draws {@code key} at block-local (x, z) on the belt surface; {@code (dx, dz)} is the unit
     * travel direction there (the lane tangent on curves).
     */
    void draw(String key, double x, double z, double dx, double dz, Level level, PoseStack pose,
              MultiBufferSource buffers, int light, int seed) {
        if (level == null) return;
        ItemStack stack = stack(key, level);
        if (stack.isEmpty()) return;
        BakedModel model = items.getModel(stack, level, null, seed);
        // Only real block items stand as cubes; every other item (even one whose model claims 3D
        // GUI lighting) lies flat, so a flat icon can never end up standing on its edge.
        boolean block = stack.getItem() instanceof BlockItem && model.isGui3d();
        // Raw model space (context NONE) is the unit cube; ItemRenderer centres it with translate(-0.5).
        float size = block ? BLOCK_SCALE : SCALE;
        pose.pushPose();
        pose.translate(x, SURFACE + (block ? size / 2 : FLAT_LIFT), z);
        pose.mulPose(block ? LanePose.uprightBlock(dx, dz) : LanePose.flatItem(dx, dz));
        pose.scale(size, size, size);
        items.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
    }
}
