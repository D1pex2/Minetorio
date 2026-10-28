package dev.minetorio.client.robot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.robot.GhostBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a ghost as its target block, translucent and tinted blue like Factorio's entity ghosts. A
 * multiblock whose block model is empty (machines drawn by their own renderers) is drawn from its
 * item model, scaled up over the footprint and turned to the ghost's facing.
 */
public class GhostRenderer implements BlockEntityRenderer<GhostBlockEntity> {
    static final float R = 0.55f, G = 0.78f, B = 1.0f, A = 0.45f;
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    public GhostRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(GhostBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.isValid()) return;
        BlockState target = be.target();
        VertexConsumer vc = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(target);
        if (hasQuads(model, target) || !(target.getBlock() instanceof MultiblockControllerBlock mb)) {
            quads(model, target, pose, vc);
            return;
        }
        MultiblockShape shape = mb.shape();
        BakedModel item = Minecraft.getInstance().getItemRenderer().getModel(new ItemStack(be.item()), be.getLevel(), null, 0);
        pose.pushPose();
        MachineRender.toCenter(pose, be.facing(), shape);
        float s = Math.max(shape.width(), shape.depth());
        pose.scale(s, s, s);
        pose.translate(-0.5, 0, -0.5);
        quads(item, null, pose, vc);
        pose.popPose();
    }

    private static boolean hasQuads(BakedModel model, BlockState state) {
        RandomSource r = RandomSource.create(42);
        for (Direction d : SIDES) {
            if (!model.getQuads(state, d, r, ModelData.EMPTY, null).isEmpty()) return true;
        }
        return false;
    }

    private static void quads(BakedModel model, BlockState state, PoseStack pose, VertexConsumer vc) {
        RandomSource r = RandomSource.create(42);
        for (Direction d : SIDES) {
            for (var q : model.getQuads(state, d, r, ModelData.EMPTY, null)) {
                vc.putBulkData(pose.last(), q, R, G, B, A, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(GhostBlockEntity be) {
        if (be.target().getBlock() instanceof MultiblockControllerBlock mb) {
            return MachineRender.footprintBox(be.getBlockPos(), be.facing(), mb.shape());
        }
        return new AABB(be.getBlockPos()).inflate(1);
    }
}
