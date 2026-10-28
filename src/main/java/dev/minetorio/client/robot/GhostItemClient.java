package dev.minetorio.client.robot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.minetorio.Minetorio;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.robot.GhostItem;
import dev.minetorio.content.robot.GhostPayloads;
import dev.minetorio.content.robot.HandGhosts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of {@link GhostItem}: its icon (the target item's model, translucent and tinted
 * ghost blue like {@link GhostRenderer}) and the pipette (pick-block in survival on a placed
 * Minetorio block whose item the player has none of asks the server for a ghost item instead of
 * doing nothing; with the item at hand vanilla pick-block runs as usual).
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class GhostItemClient {
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private GhostItemClient() {}

    @SubscribeEvent
    public static void registerExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private Renderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new Renderer();
                return renderer;
            }
        }, ModItems.GHOST_ITEM.get());
    }

    @SubscribeEvent
    public static void onPick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isPickBlock()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.getAbilities().instabuild
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return;
        ItemStack clone = state.getCloneItemStack(hit, mc.level, pos, player);
        if (!HandGhosts.canGhost(clone)) return;
        Item item = clone.getItem();
        if (player.getInventory().hasAnyMatching(s -> s.is(item))) return; // vanilla pick-block selects it
        event.setCanceled(true);
        event.setSwingHand(false);
        PacketDistributor.sendToServer(new GhostPayloads.Pipette(pos));
    }

    /** Draws a ghost item as its target's item model, translucent ghost blue. */
    private static final class Renderer extends BlockEntityWithoutLevelRenderer {
        Renderer() {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            ItemStack target = GhostItem.target(stack);
            if (target.isEmpty()) return;
            ItemRenderer items = Minecraft.getInstance().getItemRenderer();
            BakedModel model = items.getModel(target, null, null, 0);
            pose.pushPose();
            // ItemRenderer moved us to the corner for a custom renderer; apply the target's own display transform instead
            pose.translate(0.5, 0.5, 0.5);
            boolean left = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            model = model.applyTransform(ctx, pose, left);
            pose.translate(-0.5, -0.5, -0.5);
            if (model.isCustomRenderer()) {
                IClientItemExtensions.of(target).getCustomRenderer().renderByItem(target, ctx, pose, buffers, light, overlay);
            } else {
                VertexConsumer vc = buffers.getBuffer(Sheets.translucentCullBlockSheet());
                RandomSource r = RandomSource.create(42);
                for (Direction d : SIDES) {
                    r.setSeed(42);
                    for (var q : model.getQuads(null, d, r)) {
                        vc.putBulkData(pose.last(), q, GhostRenderer.R, GhostRenderer.G, GhostRenderer.B, GhostRenderer.A, light, overlay);
                    }
                }
            }
            pose.popPose();
        }
    }
}
