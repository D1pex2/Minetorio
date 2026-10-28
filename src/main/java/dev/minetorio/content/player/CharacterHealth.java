package dev.minetorio.content.player;

import dev.minetorio.core.player.CharacterRules;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Factorio health: no hunger (the food bar is locked full, see FoodDataMixin) and the character heals by
 * itself {@link CharacterRules#REGEN_DELAY_TICKS} ticks after it last took damage, at Factorio's rate
 * scaled to Minecraft health. Respects the {@code naturalRegeneration} game rule.
 */
public final class CharacterHealth {
    /** Game time of each player's last damage (not saved: after a restart the player is out of combat). */
    private static final Map<UUID, Long> LAST_DAMAGE = new ConcurrentHashMap<>();

    private CharacterHealth() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p) tick(p);
        });
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p && e.getNewDamage() > 0) markDamaged(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> fillFood(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> fillFood(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> LAST_DAMAGE.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> LAST_DAMAGE.clear());
    }

    public static void markDamaged(ServerPlayer p) {
        LAST_DAMAGE.put(p.getUUID(), p.serverLevel().getGameTime());
    }

    /** The food bar of worlds made before the rule (or changed by commands) goes back to full. */
    static void fillFood(Player p) {
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(5f);
        p.getFoodData().setExhaustion(0f);
    }

    public static void tick(ServerPlayer p) {
        if (p.getFoodData().getFoodLevel() < 20) fillFood(p);
        if (!p.isAlive() || !p.serverLevel().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) return;
        long now = p.serverLevel().getGameTime();
        long since = now - LAST_DAMAGE.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2);
        double heal = CharacterRules.regenThisTick(since, p.getHealth(), p.getMaxHealth());
        if (heal > 0) p.heal((float) heal);
    }
}
