package dev.minetorio.content.player;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Death as in Factorio: what a player drops on death (the whole inventory, armour and offhand, unless
 * keepInventory) goes into a {@link CharacterCorpseEntity} at the death position instead of scattering
 * as item entities.
 */
public final class CorpseOnDeath {
    private CorpseOnDeath() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDropsEvent e) -> {
            if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p)) return;
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemEntity drop : e.getDrops()) {
                if (!drop.getItem().isEmpty()) stacks.add(drop.getItem().copy());
            }
            e.getDrops().clear();
            spawnCorpses(p, stacks);
        });
    }

    /** Puts the stacks into as many corpses as needed at the player's position; returns them. */
    public static List<CharacterCorpseEntity> spawnCorpses(ServerPlayer p, List<ItemStack> stacks) {
        List<CharacterCorpseEntity> made = new ArrayList<>();
        for (int from = 0; from < stacks.size(); from += CharacterCorpseEntity.MAX_STACKS) {
            List<ItemStack> part = stacks.subList(from, Math.min(stacks.size(), from + CharacterCorpseEntity.MAX_STACKS));
            CharacterCorpseEntity corpse = CharacterCorpseEntity.of(p, part);
            p.serverLevel().addFreshEntity(corpse);
            made.add(corpse);
        }
        return made;
    }
}
