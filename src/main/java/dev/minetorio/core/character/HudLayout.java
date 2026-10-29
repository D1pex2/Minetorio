package dev.minetorio.core.character;

import dev.minetorio.core.player.CharacterRules;

/** Shared positions for shortcut rows, actual character vitals and hand-crafting queue. */
public record HudLayout(float scale, float left, float hotbarTop, float vitalsTop, float queueTop, float nameTop) {
    public static HudLayout of(int width, int height, int visibleRows) {
        int rows = Math.clamp(visibleRows, 1, 4);
        float widthScale = Math.max(1, width - 4) / 180f;
        // The research panel occupies the upper-left 47 pixels whenever it can be shown.
        int topReserve = width >= 196 && height > 50 ? 48 : 0;
        float heightScale = Math.max(1, height - 2 - topReserve) / (rows * 20f + 54f);
        float scale = Math.min(1f, Math.min(widthScale, heightScale));
        float left = (width - 180f * scale) / 2f;
        float hotbarTop = height - 2f - rows * 20f * scale;
        float vitalsTop = hotbarTop - 22f * scale;
        float queueTop = vitalsTop - 20f * scale;
        float nameTop = queueTop - 12f * scale;
        return new HudLayout(scale, left, hotbarTop, vitalsTop, queueTop, nameTop);
    }

    /** Convert Minecraft health to Factorio points without altering actual health. */
    public static int factorioHealth(float health) {
        return Math.round(Math.max(0f, health) * (float) CharacterRules.FACTORIO_MAX_HEALTH / 20f);
    }
}
