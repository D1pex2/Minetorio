package dev.minetorio.client.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.Minetorio;
import dev.minetorio.content.multiblock.MultiblockBlockItem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Placement preview (R26): while the player holds a multiblock machine and looks at a block, the
 * footprint the machine would occupy is drawn as a translucent box with an outline, green when it
 * can be placed there and red when it is obstructed. Uses the item's own placement rule and the
 * block's placement check, so it shows exactly what a right-click would do.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class MultiblockPlacementPreview {
    private static final double INFLATE = 0.005;

    private MultiblockPlacementPreview() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof MultiblockBlockItem item) {
                render(event, mc, item, new BlockPlaceContext(new UseOnContext(player, hand, hit)));
                return;
            }
        }
    }

    private static void render(RenderLevelStageEvent event, Minecraft mc, MultiblockBlockItem item, BlockPlaceContext ctx) {
        if (!ctx.canPlace()) return; // a right-click would not place anything here
        BlockPlaceContext placed = item.updatePlacementContext(ctx);
        if (placed == null) return;
        boolean ok = item.controller().getStateForPlacement(placed) != null;
        List<BlockPos> cells = item.controller().shape().footprint(placed.getClickedPos(), placed.getHorizontalDirection());
        AABB box = new AABB(cells.getFirst());
        for (BlockPos p : cells) box = box.minmax(new AABB(p));
        box = box.inflate(INFLATE);

        float r = ok ? 0.2f : 0.95f, g = ok ? 0.9f : 0.2f, b = 0.2f;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        LevelRenderer.addChainedFilledBoxVertices(pose, buffers.getBuffer(RenderType.debugFilledBox()),
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, 0.18f);
        buffers.endBatch(RenderType.debugFilledBox());
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, r, g, b, 0.9f);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }
}
