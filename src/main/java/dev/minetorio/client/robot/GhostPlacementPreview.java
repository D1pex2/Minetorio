package dev.minetorio.client.robot;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.Minetorio;
import dev.minetorio.client.ModKeys;
import dev.minetorio.client.drag.DragClient;
import dev.minetorio.client.machine.MultiblockPlacementPreview;
import dev.minetorio.content.multiblock.MultiblockBlockItem;
import dev.minetorio.content.robot.GhostItem;
import dev.minetorio.content.robot.HandGhosts;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * While the ghost key is held with a Minetorio block item in the main hand (or a ghost item is held), shows where a
 * right-click would put the ghost ({@link HandGhosts#plan}, the same plan the server makes): the
 * cells it covers as a blue box, or the aimed cell (a multiblock: the footprint it would take) in
 * red when no ghost can go there. Hidden during a drag, which draws its own line.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class GhostPlacementPreview {
    private static final double INFLATE = 0.005;

    private GhostPlacementPreview() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || DragClient.isDragging()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) return;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof GhostItem) && !ModKeys.ghostHeld()) return;
        ItemStack stack = HandGhosts.effective(held);
        if (!HandGhosts.canGhost(stack) || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        HandGhosts.Plan plan = HandGhosts.plan(mc.level, player, stack, hit);
        AABB box;
        boolean ok = plan != null && player.getAbilities().mayBuild;
        if (plan != null) {
            box = bounds(plan.cells());
            if (plan.cells().size() == 1) {
                VoxelShape shape = plan.state().getShape(mc.level, plan.pos(), CollisionContext.of(player));
                if (!shape.isEmpty()) box = shape.bounds().move(plan.pos());
            }
        } else {
            BlockPlaceContext ctx = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            if (!ctx.canPlace()) return;
            BlockPlaceContext at = ((BlockItem) stack.getItem()).updatePlacementContext(ctx);
            BlockPos cell = at == null ? ctx.getClickedPos() : at.getClickedPos();
            box = new AABB(cell);
            if (at != null && stack.getItem() instanceof MultiblockBlockItem mb) {
                box = bounds(mb.controller().shape().footprint(at.getClickedPos(), at.getHorizontalDirection()));
            }
        }

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        MultiblockPlacementPreview.drawBox(pose, buffers, box.inflate(INFLATE), ok, true);
        pose.popPose();
    }

    private static AABB bounds(List<BlockPos> cells) {
        AABB box = new AABB(cells.getFirst());
        for (BlockPos p : cells) box = box.minmax(new AABB(p));
        return box;
    }
}
