package dev.minetorio.client.inserter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.minetorio.content.belt.LaneStacks;
import dev.minetorio.content.inserter.InserterBlockEntity;
import dev.minetorio.core.inserter.InserterType;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Draws an inserter's arm, Factorio style: a dark shoulder joint on the base's pivot, a short tier
 * coloured riser standing almost straight up to a dark elbow joint, and from it the long thin tier
 * coloured arm reaching out and slightly down to the hand, a small dark block with two claw fingers
 * hanging from it, all in the vertical plane of the arm's heading. Long-handed arms reach twice as far.
 * The heading turns 180° about the base through the inserter's right side as {@code armProgress}
 * goes 0 (hand over the pickup block) → 1 (over the drop block). The held item hangs under the claw.
 */
public class InserterRenderer implements BlockEntityRenderer<InserterBlockEntity> {
    private static final ResourceLocation ARM_TEXTURE = ResourceLocation.fromNamespaceAndPath("minetorio", "textures/block/steel.png");
    private static final float SHOULDER_Y = 5.5f / 16;
    /** Elbow height over the shoulder: a base plus a share per block of reach (long arms stand a little taller). */
    private static final float ELBOW_RISE = 0.40f;
    private static final float ELBOW_RISE_PER_REACH = 0.06f;
    /** Horizontal elbow distance from the center as a fraction of the hand's (the riser is nearly upright). */
    private static final float ELBOW_AT = 0.1f;
    private static final float HAND_RISE = 0.2f;
    /** Horizontal hand distance from the inserter's center, per block of reach. */
    private static final float HAND_REACH = 0.85f;
    /** Half thicknesses (blocks) of the shoulder joint, riser, elbow joint, arm and hand. */
    private static final float SHOULDER_HALF = 0.065f;
    private static final float UPPER_HALF = 0.035f;
    private static final float ELBOW_HALF = 0.045f;
    private static final float FORE_HALF = 0.024f;
    private static final float HAND_HALF = 0.05f;
    private static final int MAX_CACHED_STACKS = 64;
    private static final int JOINT_COLOR = 0xFF3A3D41;
    /** Claw fingers: sideways offsets from the hand center, length and color. */
    private static final float[] FINGER_OFFSETS = {-0.045f, 0.045f};
    private static final float FINGER_LENGTH = 0.1f;
    private static final int FINGER_COLOR = 0xFFB4B8BC;

    private final ItemRenderer items;
    /** Reused per finger (renderers run on the render thread only). */
    private final Vector3f fingerTop = new Vector3f();
    private final Vector3f fingerTip = new Vector3f();
    private final Map<String, ItemStack> stacks = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
            return size() > MAX_CACHED_STACKS;
        }
    };

    public InserterRenderer(BlockEntityRendererProvider.Context ctx) {
        items = ctx.getItemRenderer();
    }

    /** Tier color of the arm segments. */
    static int tierColor(InserterType type) {
        return switch (type) {
            case BURNER -> 0xFF5C5F63;
            case BASIC -> 0xFFE8B820;
            case LONG -> 0xFFC83A30;
            case FAST -> 0xFF3A78D8;
        };
    }

    /** Unit heading (world XZ) of the arm: toward the pickup at 0, through the right side, toward the drop at 1. */
    static float[] heading(Direction facing, double arm) {
        double a = Math.PI * arm;
        Direction right = facing.getClockWise();
        double c = -Math.cos(a);
        double s = Math.sin(a);
        return new float[] {
            (float) (facing.getStepX() * c + right.getStepX() * s),
            (float) (facing.getStepZ() * c + right.getStepZ() * s)
        };
    }

    @Override
    public void render(InserterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        InserterType type = be.type();
        float[] h = heading(be.facing(), be.renderArm(partialTick));
        float radius = HAND_REACH * type.reach;
        Vector3f shoulder = new Vector3f(0.5f, SHOULDER_Y, 0.5f);
        Vector3f elbow = new Vector3f(0.5f + h[0] * radius * ELBOW_AT, SHOULDER_Y + ELBOW_RISE + ELBOW_RISE_PER_REACH * type.reach,
                0.5f + h[1] * radius * ELBOW_AT);
        Vector3f hand = new Vector3f(0.5f + h[0] * radius, SHOULDER_Y + HAND_RISE, 0.5f + h[1] * radius);
        Vector3f side = new Vector3f(-h[1], 0, h[0]);

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ARM_TEXTURE));
        PoseStack.Pose p = pose.last();
        int tier = tierColor(type);
        box(vc, p, shoulder, new Vector3f(shoulder).add(0, 0.1f, 0), side, SHOULDER_HALF, JOINT_COLOR, light);
        box(vc, p, new Vector3f(shoulder).add(0, 0.05f, 0), elbow, side, UPPER_HALF, tier, light);
        box(vc, p, new Vector3f(elbow).sub(0, 0.045f, 0), new Vector3f(elbow).add(0, 0.045f, 0), side, ELBOW_HALF, JOINT_COLOR, light);
        box(vc, p, elbow, new Vector3f(hand).add(0, 0.03f, 0), side, FORE_HALF, tier, light);
        box(vc, p, new Vector3f(hand).sub(0, 0.06f, 0), new Vector3f(hand).add(0, 0.03f, 0), side, HAND_HALF, JOINT_COLOR, light);
        for (float offset : FINGER_OFFSETS) {
            fingerTop.set(side).mul(offset).add(hand).sub(0, 0.06f, 0);
            fingerTip.set(fingerTop).sub(0, FINGER_LENGTH, 0);
            box(vc, p, fingerTop, fingerTip, side, 0.014f, FINGER_COLOR, light);
        }

        String held = be.logic().held();
        Level level = be.getLevel();
        if (held != null && level != null) {
            ItemStack stack = stacks.computeIfAbsent(held, k -> LaneStacks.stack(k, level.registryAccess()));
            if (!stack.isEmpty()) {
                pose.pushPose();
                pose.translate(hand.x, hand.y - 0.26f, hand.z);
                pose.scale(0.6f, 0.6f, 0.6f);
                items.renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, level,
                        (int) be.getBlockPos().asLong());
                pose.popPose();
            }
        }
    }

    /** A square-section box from {@code a} to {@code b}, {@code half} wide; {@code side} is a horizontal axis across it. */
    private static void box(VertexConsumer vc, PoseStack.Pose p, Vector3f a, Vector3f b, Vector3f side, float half, int color, int light) {
        Vector3f u = new Vector3f(b).sub(a).normalize();
        Vector3f s = new Vector3f(side).normalize();
        Vector3f v = new Vector3f(u).cross(s).normalize();
        s = new Vector3f(v).cross(u).normalize();
        Vector3f[] c = new Vector3f[8]; // index bits: 4 = end b, 2 = +s, 1 = +v
        for (int i = 0; i < 8; i++) {
            Vector3f base = new Vector3f((i & 4) != 0 ? b : a);
            base.add(new Vector3f(s).mul((i & 2) != 0 ? half : -half));
            base.add(new Vector3f(v).mul((i & 1) != 0 ? half : -half));
            c[i] = base;
        }
        quad(vc, p, c[2], c[3], c[7], c[6], s, color, light);
        quad(vc, p, c[0], c[4], c[5], c[1], new Vector3f(s).negate(), color, light);
        quad(vc, p, c[1], c[5], c[7], c[3], v, color, light);
        quad(vc, p, c[0], c[2], c[6], c[4], new Vector3f(v).negate(), color, light);
        quad(vc, p, c[4], c[6], c[7], c[5], u, color, light);
        quad(vc, p, c[0], c[1], c[3], c[2], new Vector3f(u).negate(), color, light);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, Vector3f c0, Vector3f c1, Vector3f c2, Vector3f c3,
                             Vector3f n, int color, int light) {
        vertex(vc, p, c0, 0, 0, n, color, light);
        vertex(vc, p, c1, 0, 1, n, color, light);
        vertex(vc, p, c2, 1, 1, n, color, light);
        vertex(vc, p, c3, 1, 0, n, color, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, Vector3f pos, float uu, float vv, Vector3f n, int color, int light) {
        vc.addVertex(p, pos.x, pos.y, pos.z)
                .setColor(color)
                .setUv(uu, vv)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(p, n.x, n.y, n.z);
    }

    /** The arm reaches {@code reach} blocks to either side. */
    @Override
    public AABB getRenderBoundingBox(InserterBlockEntity be) {
        int r = be.type().reach;
        return new AABB(be.getBlockPos()).inflate(r, 1, r);
    }
}
