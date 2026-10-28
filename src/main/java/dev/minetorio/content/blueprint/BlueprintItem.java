package dev.minetorio.content.blueprint;

import dev.minetorio.content.registry.ModDataComponents;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.blueprint.Blueprints;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
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
 * Factorio's blueprint. Empty (no {@link ModDataComponents#BLUEPRINT} component) it selects an
 * area to copy ({@link BlueprintCapture}); filled it pastes that area as ghosts
 * ({@link BlueprintPaste}). Free, like in Factorio: the blueprint key hands out an empty one
 * ({@link BlueprintPayloads.Give}); dropped empty, it vanishes. Every click is handled by the
 * client ({@code BlueprintClient}) through the blueprint payloads; vanilla use does nothing.
 */
public class BlueprintItem extends Item {
    public BlueprintItem(Properties properties) {
        super(properties);
    }

    public static ItemStack empty() {
        return new ItemStack(ModItems.BLUEPRINT.get());
    }

    /** The contents of a filled blueprint, or null (not a blueprint, or empty). */
    @Nullable
    public static BlueprintData data(ItemStack stack) {
        if (!(stack.getItem() instanceof BlueprintItem)) return null;
        BlueprintData d = stack.get(ModDataComponents.BLUEPRINT.get());
        return d == null || d.isEmpty() ? null : d;
    }

    public static boolean isEmptyBlueprint(ItemStack stack) {
        return stack.getItem() instanceof BlueprintItem && data(stack) == null;
    }

    @Override
    public Component getName(ItemStack stack) {
        BlueprintMeta m = BlueprintStacks.meta(stack);
        if (m.hasLabel()) return Component.literal(m.label());
        return data(stack) == null ? Component.translatable("item.minetorio.blueprint.empty") : super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return data(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        BlueprintData d = data(stack);
        if (BlueprintStacks.meta(stack).hasLabel()) {
            lines.add(Component.translatable(d == null ? "item.minetorio.blueprint.empty" : "item.minetorio.blueprint").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (d == null) {
            lines.add(Component.translatable("minetorio.blueprint.tooltip.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        int[] size = Blueprints.size(d.parts());
        lines.add(Component.translatable("minetorio.blueprint.tooltip.size", d.entries().size(), size[0], size[2], size[1])
                .withStyle(ChatFormatting.GRAY));
        int shown = 0;
        for (Map.Entry<String, Integer> e : d.counts().entrySet()) {
            if (shown++ == 8) {
                lines.add(Component.literal("...").withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            Item item = BlueprintData.block(e.getKey()).asItem();
            lines.add(Component.literal(e.getValue() + " × ").append(item.getDescription()).withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable("minetorio.blueprint.tooltip.paste").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("minetorio.blueprint.tooltip.edit").withStyle(ChatFormatting.DARK_GRAY));
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
