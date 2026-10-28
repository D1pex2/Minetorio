package dev.minetorio.content.player;

import dev.minetorio.core.player.CharacterRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Factorio-length days: the clock advances {@link CharacterRules#dayTimePerTick} ticks per game tick so a
 * whole day-night cycle takes {@link MinetorioGameRules#DAY_SECONDS} real seconds (default ≈ 416 s, as
 * Factorio). Uses NeoForge's day-time speed, which is synced to clients (smooth sun, no jumps). The sky
 * light follows the clock, so solar power adapts on its own.
 */
public final class DayLength {
    private DayLength() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> apply(e.getServer()));
    }

    public static void apply(MinecraftServer server) {
        float rate = CharacterRules.dayTimePerTick(server.getGameRules().getInt(MinetorioGameRules.DAY_SECONDS));
        for (ServerLevel level : server.getAllLevels()) level.setDayTimePerTick(rate);
    }
}
