package dev.minetorio.content.robot;

import dev.minetorio.content.registry.ModDataComponents;
import dev.minetorio.content.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Factorio's ghost cursor as an item: carries the block item it stands for
 * ({@link ModDataComponents#GHOST_TARGET}, a Minetorio placeable block item) and places ghosts of
 * it like the ghost key + right-click ({@link HandGhosts}; a held right-click drags a ghost line).
 * Never used up, one per stack. It only lives in player inventories: inserters and belts never take
 * it ({@code LaneStacks.toLane}), it is no ingredient, and dropped on the ground it vanishes
 * ({@link HandGhosts}). Got from the hand-crafting screen (middle-click a researched recipe) or by
 * pick-block on a placed block whose item the player has none of. Drawn as the target's icon,
 * tinted ghost blue (client: {@code GhostItemRenderer}).
 */
public class GhostItem extends Item {
    public GhostItem(Properties properties) {
        super(properties);
    }

    /** A ghost item of {@code target} (not checked). */
    public static ItemStack of(Item target) {
        ItemStack s = new ItemStack(ModItems.GHOST_ITEM.get());
        s.set(ModDataComponents.GHOST_TARGET.get(), BuiltInRegistries.ITEM.getKey(target));
        return s;
    }

    /** One of the item {@code ghost} stands for; EMPTY if {@code ghost} is no ghost item or its target is not ghostable. */
    public static ItemStack target(ItemStack ghost) {
        if (!(ghost.getItem() instanceof GhostItem)) return ItemStack.EMPTY;
        ResourceLocation id = ghost.get(ModDataComponents.GHOST_TARGET.get());
        if (id == null) return ItemStack.EMPTY;
        ItemStack t = new ItemStack(BuiltInRegistries.ITEM.get(id));
        return HandGhosts.canGhost(t) ? t : ItemStack.EMPTY;
    }

    @Override
    public Component getName(ItemStack stack) {
        ItemStack t = target(stack);
        return t.isEmpty() ? super.getName(stack) : Component.translatable("item.minetorio.ghost_item.named", t.getHoverName());
    }

    /**
     * The client sends ghost clicks itself ({@code DragClient}); this covers a use that reaches the
     * server the vanilla way (another mod, a fake player): the same checked placement.
     */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(ctx.getPlayer() instanceof ServerPlayer player) || ctx.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.FAIL;
        }
        BlockHitResult hit = new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(), ctx.getClickedPos(), ctx.isInside());
        return HandGhosts.place(player, hit) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
}
