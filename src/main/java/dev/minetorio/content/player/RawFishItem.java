package dev.minetorio.content.player;

import dev.minetorio.core.player.CharacterRules;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Factorio's raw fish: right-click to eat it at once; heals {@link CharacterRules#fishHeal} (80 of 250 HP,
 * scaled), then a short cooldown. Only when hurt, so none is wasted.
 */
public class RawFishItem extends Item {
    public RawFishItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isHurt()) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) eat(player, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Heals the player, uses up one fish (unless creative) and starts the cooldown. */
    public static void eat(Player player, ItemStack stack) {
        player.heal((float) CharacterRules.fishHeal(player.getMaxHealth()));
        stack.consume(1, player);
        player.getCooldowns().addCooldown(stack.getItem(), CharacterRules.FISH_COOLDOWN_TICKS);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT,
                SoundSource.PLAYERS, 0.5f, 1.0f);
    }
}
