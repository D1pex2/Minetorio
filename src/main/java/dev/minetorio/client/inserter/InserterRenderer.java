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
 * Draws an inserter's arm: a shoulder on top of the base, an upper arm rising to the elbow and a
 * forearm down to the hand, all in the vertical plane of the arm's heading; the hand is a tier
 * colored block with two gripper fingers hanging from it. The heading turns 180° about the base through the
 * inserter's right side as {@code armProgress} goes 0 (hand over the pickup block) → 1 (over the
 * drop block). The held item hangs under the hand.
 */
public class InserterRenderer implements BlockEntityRenderer<InserterBlockEntity> {
    private static final ResourceLocation ARM_TEXTURE = ResourceLocation.fromNamespaceAndPath("minetorio", "textures/block/steel.png");
    private static final float SHOULDER_Y = 5f / 16;
    private static final float ELBOW_RISE = 0.45f;
    private static final float HAND_RISE = 0.12f;
    /** Horizontal hand distance from the inserter's center, per block of reach. */
    private static final float HAND_REACH = 0.85f;
    private static final int MAX_CACHED_STACKS = 64;

    private final ItemRenderer items;
    private final Map<String, ItemStack> stacks = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
            return size() > MAX_CACHED_STACKS;
        }
    };

    public InserterRenderer(BlockEntityRendererProvider.Context ctx) {
        items = ctx.getItemRenderer();
    }

    /** Tier color of the hand (matches the base plate). */
    static int tierColor(InserterType type) {
        return switch (type) {
            case BURNER -> 0xFF8A5A2B;
            case BASIC -> 0xFFE8B820;
            case LONG -> 0xFFC03030;
            case FAST -> 0xFF3070D0;
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
        Vector3f elbow = new Vector3f(0.5f + h[0] * radius * 0.45f, SHOULDER_Y + ELBOW_RISE, 0.5f + h[1] * radius * 0.45f);
        Vector3f hand = new Vector3f(0.5f + h[0] * radius, SHOULDER_Y + HAND_RISE, 0.5f + h[1] * radius);
        Vector3f side = new Vector3f(-h[1], 0, h[0]);

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ARM_TEXTURE));
        PoseStack.Pose p = pose.last();
        int metal = 0xFFE8E8E8;
        box(vc, p, shoulder, elbow, side, 0.05f, metal, light);
        box(vc, p, elbow, hand, side, 0.04f, metal, light);
        box(vc, p, new Vector3f(hand).sub(0, 0.06f, 0), new Vector3f(hand).add(0, 0.02f, 0), side, 0.07f, tierColor(type), light);
        for (float s : new float[] {-0.05f, 0.05f}) { // gripper fingers
            Vector3f top = new Vector3f(hand).add(new Vector3f(side).mul(s)).sub(0, 0.06f, 0);
            box(vc, p, top, new Vector3f(top).sub(0, 0.09f, 0), side, 0.015f, 0xFF505458, light);
        }

        String held = be.logic().held();
        Level level = be.getLevel();
        if (held != null && level != null) {
            ItemStack stack = stacks.computeIfAbsent(held, k -> LaneStacks.stack(k, level.registryAccess()));
            if (!stack.isEmpty()) {
                pose.pushPose();
                pose.translate(hand.x, hand.y - 0.22f, hand.z);
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
