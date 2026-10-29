package dev.minetorio.client.mixin;

import dev.minetorio.content.world.MinetorioPreset;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The create-world screen starts on the Minetorio world type instead of vanilla "Default" (vanilla
 * hard-codes Default; the preset tags only decide what the list offers). Also applies when re-creating
 * a world of the Default type.
 */
@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void minetorio$defaultToMinetorio(Path saves, WorldCreationContext settings, Optional<ResourceKey<WorldPreset>> preset,
                                              OptionalLong seed, CallbackInfo ci) {
        if (preset.isPresent() && !preset.get().equals(WorldPresets.NORMAL)) return;
        WorldCreationUiState self = (WorldCreationUiState) (Object) this;
        settings.worldgenLoadContext().registryOrThrow(Registries.WORLD_PRESET).getHolder(MinetorioPreset.KEY)
                .ifPresent(h -> self.setWorldType(new WorldCreationUiState.WorldTypeEntry(h)));
    }
}
