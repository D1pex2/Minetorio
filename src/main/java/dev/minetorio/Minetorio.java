package dev.minetorio;

import dev.minetorio.content.belt.BeltTicker;
import dev.minetorio.content.demo.DemoCommand;
import dev.minetorio.content.demo.DemoSavedData;
import dev.minetorio.content.deposit.Deposits;
import dev.minetorio.content.power.PowerGridManager;
import dev.minetorio.content.handcraft.HandcraftTicker;
import dev.minetorio.content.registry.ModAttachments;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.registry.ModCapabilities;
import dev.minetorio.content.registry.ModCreativeTab;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.registry.ModMenus;
import dev.minetorio.content.registry.ModPayloads;
import dev.minetorio.content.research.ResearchSavedData;
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
        ModCapabilities.register(modBus);
        ModMenus.register(modBus);
        ModPayloads.register(modBus);
        ModAttachments.register(modBus);
        BeltTicker.register();
        PowerGridManager.register();
        Deposits.register();
        ResearchSavedData.register();
        HandcraftTicker.register();
        DemoSavedData.register();
        DemoCommand.register();
    }
}
