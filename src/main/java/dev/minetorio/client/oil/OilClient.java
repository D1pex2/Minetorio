package dev.minetorio.client.oil;

import dev.minetorio.content.oil.OilRegistry;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class OilClient {
    private OilClient() {}
    public static void registerScreens(RegisterMenuScreensEvent event) { event.register(OilRegistry.PROCESSING_MENU.get(), OilScreen::new); event.register(OilRegistry.STEEL_CHEST_MENU.get(), SteelChestScreen::new); }
}
