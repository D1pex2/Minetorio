package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import dev.minetorio.content.orefield.OreFieldFeature;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** World generation features (configured and placed in {@code data/minetorio/worldgen}). */
public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Minetorio.MODID);

    public static final Supplier<OreFieldFeature> ORE_FIELD = FEATURES.register("ore_field", OreFieldFeature::new);

    private ModFeatures() {}

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}
