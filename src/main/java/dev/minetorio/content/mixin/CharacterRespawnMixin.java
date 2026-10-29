package dev.minetorio.content.mixin;

import dev.minetorio.content.character.CharacterShortcuts;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shortcut settings describe player preferences and survive death as well as dimension transfer. */
@Mixin(ServerPlayer.class)
public abstract class CharacterRespawnMixin {
    @Inject(method = "restoreFrom", at = @At("RETURN"))
    private void minetorio$copyShortcuts(ServerPlayer oldPlayer, boolean keepEverything, CallbackInfo ci) {
        CharacterShortcuts old = (CharacterShortcuts) oldPlayer;
        CharacterShortcuts current = (CharacterShortcuts) this;
        for (int i = 0; i < CharacterShortcuts.ROWS * CharacterShortcuts.COLUMNS; i++) current.shortcut(i, old.shortcut(i));
        current.selectedRow(old.selectedRow());
        current.visibleRows(old.visibleRows());
    }
}
