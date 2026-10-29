package dev.minetorio;

import dev.minetorio.content.belt.BeltTicker;
import dev.minetorio.content.demo.DemoCommand;
import dev.minetorio.content.demo.DemoSavedData;
import dev.minetorio.content.orefield.OreFieldPayloads;
import dev.minetorio.content.orefield.OrePatchLedger;
import dev.minetorio.content.orefield.OreFields;
import dev.minetorio.content.orefield.StartingFields;
import dev.minetorio.content.player.PlayerBasics;
import dev.minetorio.content.power.PowerGridManager;
import dev.minetorio.content.handcraft.HandcraftTicker;
import dev.minetorio.content.drag.DragPlacement;
import dev.minetorio.content.map.RemoteView;
import dev.minetorio.content.map.WatchedChunks;
import dev.minetorio.content.map.WorldMap;
import dev.minetorio.content.registry.ModAttachments;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.registry.ModCapabilities;
import dev.minetorio.content.registry.ModCreativeTab;
import dev.minetorio.content.registry.ModDataComponents;
import dev.minetorio.content.registry.ModEntities;
import dev.minetorio.content.registry.ModFeatures;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.registry.BaseMaterials;
import dev.minetorio.content.registry.ModMenus;
import dev.minetorio.content.registry.ModPayloads;
import dev.minetorio.content.research.ResearchSavedData;
import dev.minetorio.content.robot.ConstructionManager;
import dev.minetorio.content.robot.HandGhosts;
import dev.minetorio.content.robot.RobotCommand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Minetorio.MODID)
public class Minetorio {
    public static final String MODID = "minetorio";

    public Minetorio(IEventBus modBus) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        dev.minetorio.content.combat.CombatRegistry.register(modBus);
        BaseMaterials.register(modBus);
        dev.minetorio.content.registry.ModWorld.register(modBus);
        dev.minetorio.content.world.BasicChests.register(modBus);
        dev.minetorio.content.machineui.MachineUi.register(modBus);
        dev.minetorio.content.stats.StatsPayloads.register(modBus);
        dev.minetorio.content.registry.OilMaterials.register(modBus);
        dev.minetorio.content.oil.OilRegistry.register(modBus);
        dev.minetorio.content.oilmap.OilMapPayloads.register(modBus);
        dev.minetorio.content.logistics.LogisticsRegistry.register(modBus);
        ModDataComponents.register(modBus);
        ModBlockEntities.register(modBus);
        ModEntities.register(modBus);
        ModCreativeTab.register(modBus);
        ModCapabilities.register(modBus);
        ModMenus.register(modBus);
        ModPayloads.register(modBus);
        ModAttachments.register(modBus);
        ModFeatures.register(modBus);
        BeltTicker.register();
        PowerGridManager.register();
        dev.minetorio.content.fluid.FluidNetworkManager.register();
        OreFields.register();
        StartingFields.register();
        OrePatchLedger.register();
        OreFieldPayloads.register();
        ResearchSavedData.register();
        HandcraftTicker.register();
        DemoSavedData.register();
        WorldMap.register();
        WatchedChunks.register();
        RemoteView.register();
        DragPlacement.register();
        DemoCommand.register();
        dev.minetorio.content.demo.NauvisDemoBuild.register();
        ConstructionManager.register();
        HandGhosts.register();
        dev.minetorio.content.blueprint.BlueprintPaste.register();
        dev.minetorio.content.robot.DeconstructionMarking.register();
        dev.minetorio.content.robot.ConstructionMarks.register();
        dev.minetorio.content.robot.RobotWork.register();
        dev.minetorio.content.robot.Planners.register();
        RobotCommand.register();
        PlayerBasics.register(modBus);
    }
}
