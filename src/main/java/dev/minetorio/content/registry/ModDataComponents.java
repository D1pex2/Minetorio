package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import dev.minetorio.content.blueprint.BlueprintData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data components. */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Minetorio.MODID);

    /** The block item a ghost item ({@code GhostItem}) places ghosts of. Saved and synced. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> GHOST_TARGET =
            COMPONENTS.registerComponentType("ghost_target",
                    b -> b.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));

    /** A filled blueprint's contents ({@code BlueprintData}); an empty blueprint has none. Saved and synced. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlueprintData>> BLUEPRINT =
            COMPONENTS.registerComponentType("blueprint",
                    b -> b.persistent(BlueprintData.CODEC).networkSynchronized(BlueprintData.STREAM_CODEC).cacheEncoding());

    private ModDataComponents() {}

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
