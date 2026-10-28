package dev.minetorio.client.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.Minetorio;
import dev.minetorio.client.ModKeys;
import dev.minetorio.content.multiblock.MultiblockBlockItem;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.content.robot.GhostItem;
import dev.minetorio.content.robot.GhostReservations;
import dev.minetorio.content.robot.HandGhosts;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Placement preview (R26): while the player holds a multiblock machine and looks at a block, the
 * footprint the machine would occupy is drawn as a translucent box with an outline, green when it
 * can be placed there and red when it is obstructed. Uses the item's own placement rule and the
 * block's placement check, so it shows what a right-click would do: the main hand wins (a
 * different block item there means no preview), and nothing is shown while aiming at a machine or
 * any block with a menu (a right-click would open it) unless sneaking. Red also when the player may
 * not build or would stand in the controller cell, or when a ghost reserves part of the footprint
 * (unless the machine goes right onto that ghost: then it completes it). With the ghost key held
 * the ghost preview ({@code GhostPlacementPreview}) shows instead.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class MultiblockPlacementPreview {
    private static final double INFLATE = 0.005;
    /** Preview colours: fits, ghost (Factorio's ghost blue), blocked. */
    public static final float[] OK_RGB = {0.2f, 0.9f, 0.2f};
    public static final float[] GHOST_RGB = {0.35f, 0.65f, 1.0f};
    public static final float[] BAD_RGB = {0.95f, 0.2f, 0.2f};

    private MultiblockPlacementPreview() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        if ((ModKeys.ghostHeld() && HandGhosts.canGhost(player.getMainHandItem()))
                || player.getMainHandItem().getItem() instanceof GhostItem) return; // a ghost click: GhostPlacementPreview
        InteractionHand hand = heldHand(player);
        if (hand == null || (opensOnUse(mc.level, hit.getBlockPos()) && !player.isSecondaryUseActive())) return;
        MultiblockBlockItem item = (MultiblockBlockItem) player.getItemInHand(hand).getItem();
        render(event, mc, player, item, new BlockPlaceContext(new UseOnContext(player, hand, hit)));
    }

    /** Hand whose multiblock item a right-click would place: the main hand first; null if none. */
    @Nullable
    private static InteractionHand heldHand(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof MultiblockBlockItem) return InteractionHand.MAIN_HAND;
        if (main.getItem() instanceof BlockItem) return null; // the main hand's block would be placed instead
        return player.getOffhandItem().getItem() instanceof MultiblockBlockItem ? InteractionHand.OFF_HAND : null;
    }

    /** Whether right-clicking the block at {@code pos} opens or operates it instead of placing. */
    private static boolean opensOnUse(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof MultiblockControllerBlock || state.getBlock() instanceof MultiblockPartBlock
                || state.getMenuProvider(level, pos) != null;
    }

    private static void render(RenderLevelStageEvent event, Minecraft mc, LocalPlayer player, MultiblockBlockItem item, BlockPlaceContext ctx) {
        if (!ctx.canPlace()) return; // a right-click would not place anything here
        BlockPlaceContext placed = item.updatePlacementContext(ctx);
        if (placed == null) return;
        BlockState state = item.controller().getStateForPlacement(placed);
        boolean ok = state != null && player.getAbilities().mayBuild
                && mc.level.isUnobstructed(state, placed.getClickedPos(), CollisionContext.of(player));
        List<BlockPos> cells = item.controller().shape().footprint(placed.getClickedPos(), placed.getHorizontalDirection());
        for (BlockPos c : cells) if (!GhostReservations.freeFor(mc.level, c, placed.getClickedPos())) ok = false;
        AABB box = new AABB(cells.getFirst());
        for (BlockPos p : cells) box = box.minmax(new AABB(p));
        box = box.inflate(INFLATE);

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        drawBox(pose, buffers, box, ok);
        pose.popPose();
    }

    /**
     * Draws {@code box} (world coordinates; {@code pose} already translated by the camera) as a
     * translucent fill with an outline, green when {@code ok}, red otherwise. Shared with the drag
     * placement ghost.
     */
    public static void drawBox(PoseStack pose, MultiBufferSource.BufferSource buffers, AABB box, boolean ok) {
        drawBox(pose, buffers, box, ok, false);
    }

    /** {@link #drawBox(PoseStack, MultiBufferSource.BufferSource, AABB, boolean)}, blue instead of green for a {@code ghost}. */
    public static void drawBox(PoseStack pose, MultiBufferSource.BufferSource buffers, AABB box, boolean ok, boolean ghost) {
        float[] c = !ok ? BAD_RGB : ghost ? GHOST_RGB : OK_RGB;
        float r = c[0], g = c[1], b = c[2];
        LevelRenderer.addChainedFilledBoxVertices(pose, buffers.getBuffer(RenderType.debugFilledBox()),
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, 0.18f);
        buffers.endBatch(RenderType.debugFilledBox());
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, r, g, b, 0.9f);
        buffers.endBatch(RenderType.lines());
    }
}
