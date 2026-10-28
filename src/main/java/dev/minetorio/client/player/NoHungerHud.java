package dev.minetorio.client.player;

import dev.minetorio.Minetorio;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** No hunger in Minetorio, so no food bar on the HUD. */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class NoHungerHud {
    private NoHungerHud() {}

    @SubscribeEvent
    public static void hideFood(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)) event.setCanceled(true);
    }
}
