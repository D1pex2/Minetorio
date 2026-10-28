package dev.minetorio.content.nbt;

import net.minecraft.nbt.CompoundTag;

/**
 * A block entity whose player-set configuration (not its contents or progress) can be copied to
 * another block of the same kind, e.g. by a blueprint or copy/paste: filters, a mode, a recipe.
 * {@link #loadSettings} accepts what {@link #saveSettings} wrote and ignores what it does not know.
 */
public interface CopyableSettings {
    CompoundTag saveSettings();

    /** Applies settings (server side: marks the block entity changed and syncs it to clients). */
    void loadSettings(CompoundTag tag);
}
