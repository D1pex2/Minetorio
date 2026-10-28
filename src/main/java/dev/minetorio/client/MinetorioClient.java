package dev.minetorio.client;

import dev.minetorio.Minetorio;
import dev.minetorio.client.camera.RemoteViewClient;
import dev.minetorio.client.belt.BeltRenderer;
import dev.minetorio.client.belt.SplitterRenderer;
import dev.minetorio.client.belt.UndergroundBeltRenderer;
import dev.minetorio.client.inserter.InserterRenderer;
import dev.minetorio.client.machine.AssemblerRenderer;
import dev.minetorio.client.machine.AssemblerScreen;
import dev.minetorio.client.machine.DrillRenderer;
import dev.minetorio.client.machine.StoneFurnaceScreen;
import dev.minetorio.client.map.OreFieldHover;
import dev.minetorio.client.map.RadarRenderer;
import dev.minetorio.client.power.SteamMachineRenderer;
import dev.minetorio.client.research.LabRenderer;
import dev.minetorio.client.robot.ConstructionAreaOverlay;
import dev.minetorio.client.robot.ConstructionRobotRenderer;
import dev.minetorio.client.robot.GhostRenderer;
import dev.minetorio.client.robot.RoboportRenderer;
import dev.minetorio.client.robot.RoboportScreen;
import dev.minetorio.client.power.WireRenderer;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.content.registry.ModEntities;
import dev.minetorio.content.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only registrations (mod bus). */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class MinetorioClient {
    private MinetorioClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TRANSPORT_BELT.get(), BeltRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.UNDERGROUND_BELT.get(), UndergroundBeltRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SPLITTER.get(), SplitterRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.INSERTER.get(), InserterRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BURNER_MINING_DRILL.get(),
                ctx -> new DrillRenderer(ctx, DrillRenderer.BURNER));
        event.registerBlockEntityRenderer(ModBlockEntities.ELECTRIC_MINING_DRILL.get(),
                ctx -> new DrillRenderer(ctx, DrillRenderer.ELECTRIC));
        event.registerBlockEntityRenderer(ModBlockEntities.BOILER.get(), SteamMachineRenderer.Boiler::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STEAM_ENGINE.get(), SteamMachineRenderer.Engine::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STORAGE_TANK.get(), dev.minetorio.client.power.StorageTankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SMALL_ELECTRIC_POLE.get(), WireRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ASSEMBLING_MACHINE.get(), AssemblerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LAB.get(), LabRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.RADAR.get(), RadarRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ROBOPORT.get(), RoboportRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GHOST.get(), GhostRenderer::new);
        event.registerEntityRenderer(ModEntities.CONSTRUCTION_ROBOT.get(), ConstructionRobotRenderer::new);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        OreFieldHover.register();
        ConstructionAreaOverlay.register();
        RemoteViewClient.register();
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.STONE_FURNACE.get(), StoneFurnaceScreen::new);
        event.register(ModMenus.ASSEMBLING_MACHINE.get(), AssemblerScreen::new);
        event.register(ModMenus.ROBOPORT.get(), RoboportScreen::new);
        event.register(ModMenus.FILTER_INSERTER.get(), dev.minetorio.client.inserter.InserterFilterScreen::new);
    }

    /** Models drawn by block entity renderers rather than through a blockstate. */
    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(DrillRenderer.BURNER_HEAD);
        event.register(DrillRenderer.ELECTRIC_BODY);
        event.register(DrillRenderer.ELECTRIC_HEAD);
        event.register(DrillRenderer.ELECTRIC_CARRIAGE);
        event.register(DrillRenderer.ELECTRIC_TOWER);
        event.register(SteamMachineRenderer.BOILER_BODY);
        event.register(SteamMachineRenderer.BOILER_FIRE);
        event.register(SteamMachineRenderer.ENGINE_BODY);
        event.register(SteamMachineRenderer.ENGINE_FRONT);
        event.register(SteamMachineRenderer.ENGINE_REAR);
        event.register(SteamMachineRenderer.ENGINE_GEAR);
        event.register(SteamMachineRenderer.ENGINE_GEAR_SMALL);
        event.register(dev.minetorio.client.power.StorageTankRenderer.BODY);
        for (var m : AssemblerRenderer.MODELS) event.register(m);
        event.register(LabRenderer.BODY);
        event.register(LabRenderer.DOME);
        event.register(LabRenderer.DOME_IDLE);
        event.register(RadarRenderer.BASE);
        event.register(RadarRenderer.DISH);
        RoboportRenderer.PIECES.forEach(event::register);
        RoboportRenderer.ANIMATED.forEach(event::register);
        event.register(ConstructionRobotRenderer.MODEL);
        event.register(ConstructionRobotRenderer.ROTOR);
    }
}
