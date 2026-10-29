package dev.minetorio.content.world;

import dev.minetorio.Minetorio;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/**
 * The Minetorio world type ({@code data/minetorio/worldgen/world_preset/minetorio.json}): the Overworld
 * only, generated with the {@code minetorio:nauvis} dimension type and noise settings. It is in the
 * {@code #minecraft:normal} preset tag and is the default: the create-world screen starts on it instead
 * of "Default" (client mixin {@code WorldCreationUiStateMixin}) and a dedicated server's
 * {@code level-type} defaults to {@value #ID} (mixin {@code DedicatedServerPropertiesMixin}).
 * The vanilla types stay selectable.
 */
public final class MinetorioPreset {
    public static final String ID = "minetorio:minetorio";
    public static final ResourceKey<WorldPreset> KEY =
            ResourceKey.create(Registries.WORLD_PRESET, ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "minetorio"));

    private MinetorioPreset() {}
}
