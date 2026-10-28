package dev.minetorio.content.blueprint;

import dev.minetorio.content.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Factorio's blueprint book: holds blueprints and books ({@link BlueprintBookData}), one of them
 * active. Held, it pastes its active blueprint like a blueprint; Ctrl + wheel picks the active
 * entry; right-clicked in the inventory it opens (arrange, rename, delete, take out, put in).
 * Free like the blueprint (Ctrl+Shift+B); dropped empty, it vanishes. Clicks are handled by the
 * client ({@code BlueprintClient}); vanilla use does nothing.
 */
public class BlueprintBookItem extends Item {
    public BlueprintBookItem(Properties properties) {
        super(properties);
    }

    public static ItemStack empty() {
        return new ItemStack(ModItems.BLUEPRINT_BOOK.get());
    }

    public static boolean isEmptyBook(ItemStack stack) {
        return stack.getItem() instanceof BlueprintBookItem && BlueprintStacks.book(stack).isEmpty();
    }

    @Override
    public Component getName(ItemStack stack) {
        BlueprintMeta m = BlueprintStacks.meta(stack);
        return m.hasLabel() ? Component.literal(m.label()) : super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !BlueprintStacks.book(stack).isEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        BlueprintBookData d = BlueprintStacks.book(stack);
        if (BlueprintStacks.meta(stack).hasLabel()) lines.add(Component.translatable("item.minetorio.blueprint_book").withStyle(ChatFormatting.DARK_GRAY));
        if (d.isEmpty()) {
            lines.add(Component.translatable("minetorio.blueprint_book.tooltip.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        lines.add(Component.translatable("minetorio.blueprint_book.tooltip.size", d.size()).withStyle(ChatFormatting.GRAY));
        BlueprintRecord active = d.activeEntry();
        if (active != null) {
            lines.add(Component.translatable("minetorio.blueprint_book.tooltip.active", d.active() + 1, BlueprintStacks.name(active))
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("minetorio.blueprint_book.tooltip.use").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }
}
