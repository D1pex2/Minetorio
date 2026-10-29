package dev.minetorio.content.combat;

import dev.minetorio.core.combat.CombatRules;
import net.minecraft.world.item.Item;

/** Стрельба обрабатывается только CombatManager после клиентского намерения. */
public final class GunItem extends Item {
    public final CombatRules.Gun gun;
    public GunItem(Properties properties, CombatRules.Gun gun) { super(properties); this.gun = gun; }
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
            java.util.List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(net.minecraft.network.chat.Component.translatable("minetorio.combat.loaded", stack.getOrDefault(CombatRegistry.ROUNDS.get(), 0)));
        if (stack.getCount() > 1) lines.add(net.minecraft.network.chat.Component.translatable("minetorio.combat.single"));
    }
}
