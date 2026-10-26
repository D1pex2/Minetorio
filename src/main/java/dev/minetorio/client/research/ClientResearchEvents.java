package dev.minetorio.client.research;

import dev.minetorio.Minetorio;
import dev.minetorio.content.handcraft.ClientHandcraft;
import dev.minetorio.content.research.ClientResearch;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Forgets the last world's research and hand-crafting queue when the client leaves it. */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class ClientResearchEvents {
    private ClientResearchEvents() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientResearch.reset();
        ClientHandcraft.reset();
    }
}
