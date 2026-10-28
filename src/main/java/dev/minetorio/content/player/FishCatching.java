package dev.minetorio.content.player;

import dev.minetorio.Minetorio;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.player.CharacterRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Catching fish as in Factorio: right-click (or hit) a {@code minetorio:fish} and it is gone, with
 * {@link CharacterRules#FISH_PER_CATCH} raw fish in the inventory (what does not fit drops at the player).
 * Matches the entity by id so it works with whichever fish entity class registers {@code minetorio:fish}.
 */
public final class FishCatching {
    public static final ResourceLocation FISH_ID = ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "fish");

    private FishCatching() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract e) -> {
            if (!isFish(e.getTarget())) return;
            if (!e.getLevel().isClientSide) catchFish(e.getEntity(), e.getTarget());
            e.setCancellationResult(InteractionResult.sidedSuccess(e.getLevel().isClientSide));
            e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((AttackEntityEvent e) -> {
            if (!isFish(e.getTarget())) return;
            if (!e.getEntity().level().isClientSide) catchFish(e.getEntity(), e.getTarget());
            e.setCanceled(true);
        });
    }

    public static boolean isFish(Entity e) {
        return FISH_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
    }

    /** Removes the fish and gives the player its raw fish; false if the fish was already caught. */
    public static boolean catchFish(Player player, Entity fish) {
        if (!fish.isAlive()) return false;
        fish.discard();
        ItemStack catchStack = new ItemStack(ModItems.RAW_FISH.get(), CharacterRules.FISH_PER_CATCH);
        if (!player.getInventory().add(catchStack) && !catchStack.isEmpty()) player.drop(catchStack, false);
        player.level().playSound(null, fish.getX(), fish.getY(), fish.getZ(), SoundEvents.FISH_SWIM, SoundSource.PLAYERS, 0.6f, 1.2f);
        return true;
    }
}
