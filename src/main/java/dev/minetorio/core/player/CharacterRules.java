package dev.minetorio.core.player;

import dev.minetorio.core.Units;
import java.util.Set;

/**
 * Factorio's character rules, scaled to Minecraft: health (Factorio 250 HP → Minecraft 20), regeneration
 * after a quiet spell, raw fish, the character corpse and the day length. Pure numbers; the content layer
 * (dev.minetorio.content.player) applies them.
 */
public final class CharacterRules {
    /** Factorio character max health. */
    public static final double FACTORIO_MAX_HEALTH = 250;
    /** Factorio runs at 60 ticks per second. */
    public static final int FACTORIO_TPS = 60;

    /** Factorio character: {@code ticks_to_stay_in_combat} = 600 (10 s) before healing starts. */
    public static final int REGEN_DELAY_TICKS = Units.ticks(600.0 / FACTORIO_TPS);
    /** Factorio character: {@code healing_per_tick} = 0.15 HP per Factorio tick. */
    public static final double FACTORIO_HEALING_PER_TICK = 0.15;

    /** Factorio raw fish heals 80 HP. */
    public static final double FISH_HEAL_FACTORIO = 80;
    /** Raw fish per caught fish (Factorio: mining a fish yields 5 raw fish). */
    public static final int FISH_PER_CATCH = 5;
    /** Least ticks between two fish eaten (Factorio capsule cooldown 30 Factorio ticks = 0.5 s). */
    public static final int FISH_COOLDOWN_TICKS = Units.ticks(0.5);

    /** Factorio day: 25 000 Factorio ticks ≈ 416.7 s. */
    public static final int FACTORIO_DAY_SECONDS = 25_000 / FACTORIO_TPS;
    /** A Minecraft day in clock ticks. */
    public static final int MC_TICKS_PER_DAY = 24_000;

    /** Default corpse lifetime (Factorio keeps them long; we pick two hours of world time). */
    public static final int CORPSE_MINUTES = 120;

    private CharacterRules() {}

    /** Health regenerated per Minecraft tick for this max health (Factorio's rate, proportionally). */
    public static double regenPerTick(double maxHealth) {
        double perSecondFraction = FACTORIO_HEALING_PER_TICK * FACTORIO_TPS / FACTORIO_MAX_HEALTH;
        return maxHealth * perSecondFraction / Units.TICKS_PER_SECOND;
    }

    /** Health to restore this tick, given ticks since last damage (0 while in combat or at full health). */
    public static double regenThisTick(long ticksSinceDamage, double health, double maxHealth) {
        if (health <= 0 || health >= maxHealth || ticksSinceDamage < REGEN_DELAY_TICKS) return 0;
        return Math.min(regenPerTick(maxHealth), maxHealth - health);
    }

    /** Health one raw fish restores for this max health (80/250 of it: 6.4 on 20). */
    public static double fishHeal(double maxHealth) {
        return maxHealth * FISH_HEAL_FACTORIO / FACTORIO_MAX_HEALTH;
    }

    /**
     * Clock ticks per game tick so that a full day lasts {@code daySeconds} real seconds;
     * {@code daySeconds <= 0} means vanilla (negative result: no speed set).
     */
    public static float dayTimePerTick(int daySeconds) {
        if (daySeconds <= 0) return -1f;
        return (float) MC_TICKS_PER_DAY / (daySeconds * (float) Units.TICKS_PER_SECOND);
    }

    /** Corpse lifetime in game ticks ({@code minutes <= 0}: never expires → {@link Long#MAX_VALUE}). */
    public static long corpseLifetimeTicks(int minutes) {
        return minutes <= 0 ? Long.MAX_VALUE : (long) minutes * 60 * Units.TICKS_PER_SECOND;
    }

    /** Rows of a 9-wide chest view holding {@code stacks} stacks (1..6). */
    public static int corpseRows(int stacks) {
        return Math.max(1, Math.min(6, (stacks + 8) / 9));
    }

    /** Spawn reasons (Minecraft MobSpawnType names) that the world itself causes; these are refused for non-mod mobs. */
    public static final Set<String> WORLD_SPAWN_REASONS = Set.of(
            "NATURAL", "CHUNK_GENERATION", "SPAWNER", "STRUCTURE", "JOCKEY", "EVENT", "REINFORCEMENT",
            "PATROL", "TRIAL_SPAWNER", "MOB_SUMMONED");

    /**
     * Whether a mob may spawn: the mod's own entities always may; others (vanilla, other mods) only by a
     * player's or command's hand (spawn egg, /summon, bucket, dispenser, breeding, conversion...).
     */
    public static boolean allowsSpawn(String entityNamespace, String spawnReason) {
        return "minetorio".equals(entityNamespace) || !WORLD_SPAWN_REASONS.contains(spawnReason);
    }
}
