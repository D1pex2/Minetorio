package dev.minetorio.content.robot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.minetorio.content.registry.ModDataComponents;
import dev.minetorio.core.robot.DeconstructionFilter;
import dev.minetorio.core.robot.UpgradeRules;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The deconstruction and upgrade planners (Factorio's selection tools). They are free: a key gives
 * one ({@link Planners#give}), and they only select ({@code client.selection.AreaSelection}); the
 * server applies a selection with {@link Planners#select}. Settings live on the item: the
 * deconstruction filter ({@link ModDataComponents#DECONSTRUCTION_FILTER}) and the upgrade rules
 * ({@link ModDataComponents#UPGRADE_RULES}), edited in their screens.
 */
public final class PlannerItems {
    private PlannerItems() {}

    public static final Codec<UpgradeRules.Rule> RULE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("from").forGetter(UpgradeRules.Rule::from),
            Codec.STRING.fieldOf("to").forGetter(UpgradeRules.Rule::to)).apply(i, UpgradeRules.Rule::new));
    public static final Codec<UpgradeRules> RULES_CODEC = RULE_CODEC.listOf().xmap(UpgradeRules::sanitize, UpgradeRules::rules);
    public static final StreamCodec<ByteBuf, UpgradeRules.Rule> RULE_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), UpgradeRules.Rule::from, ByteBufCodecs.stringUtf8(256), UpgradeRules.Rule::to,
            UpgradeRules.Rule::new);
    public static final StreamCodec<ByteBuf, UpgradeRules> RULES_STREAM_CODEC =
            RULE_STREAM_CODEC.apply(ByteBufCodecs.list(UpgradeRules.MAX_RULES)).map(UpgradeRules::sanitize, UpgradeRules::rules);

    public static DeconstructionFilter filter(ItemStack stack) {
        Integer id = stack.get(ModDataComponents.DECONSTRUCTION_FILTER.get());
        return DeconstructionFilter.byId(id == null ? 0 : id);
    }

    public static UpgradeRules rules(ItemStack stack) {
        UpgradeRules r = stack.get(ModDataComponents.UPGRADE_RULES.get());
        return r == null ? UpgradeRules.EMPTY : r;
    }

    public static boolean isPlanner(ItemStack stack) {
        return stack.getItem() instanceof DeconstructionPlanner || stack.getItem() instanceof UpgradePlanner;
    }

    static Component itemName(String id) {
        if (id.isEmpty()) return Component.literal("?");
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)).getDescription();
    }

    public static class DeconstructionPlanner extends Item {
        public DeconstructionPlanner(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable("minetorio.planner.deconstruction.filter",
                    Component.translatable("minetorio.planner.filter." + filter(stack).name().toLowerCase(java.util.Locale.ROOT)))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("minetorio.planner.tooltip.select").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("minetorio.planner.tooltip.cancel").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("minetorio.planner.tooltip.settings").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static class UpgradePlanner extends Item {
        public UpgradePlanner(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
            UpgradeRules r = rules(stack);
            if (!r.hasValid()) lines.add(Component.translatable("minetorio.planner.upgrade.no_rules").withStyle(ChatFormatting.GRAY));
            for (UpgradeRules.Rule rule : r.rules()) {
                if (!rule.valid()) continue;
                lines.add(Component.translatable("minetorio.planner.upgrade.rule", itemName(rule.from()), itemName(rule.to()))
                        .withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("minetorio.planner.tooltip.select").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("minetorio.planner.tooltip.cancel").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("minetorio.planner.tooltip.settings").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
