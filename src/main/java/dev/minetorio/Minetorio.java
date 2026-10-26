package dev.minetorio;

import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.registry.ModCreativeTab;
import dev.minetorio.content.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Minetorio.MODID)
public class Minetorio {
    public static final String MODID = "minetorio";

    public Minetorio(IEventBus modBus) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModCreativeTab.register(modBus);
    }
}
