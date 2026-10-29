package dev.minetorio.client.character;

import dev.minetorio.Minetorio;
import dev.minetorio.content.character.CharacterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class CharacterClient {
    private static boolean openingCatalog;
    private CharacterClient() {}

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (!openingCatalog && (event.getNewScreen() instanceof InventoryScreen
                || event.getNewScreen() instanceof CreativeModeInventoryScreen) && mc.player != null
                && mc.player.inventoryMenu instanceof CharacterMenu menu) {
            // The creative screen constructor installs its picker before ScreenEvent.Opening.
            mc.player.containerMenu = menu;
            event.setNewScreen(new CharacterScreen(menu, mc.player.getInventory()));
        }
    }

    public static void openCreativeCatalog() {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || !mc.player.isCreative()) return;
        mc.player.closeContainer();
        openingCatalog=true;
        try {
            mc.setScreen(new CreativeModeInventoryScreen(mc.player,mc.player.connection.enabledFeatures(),mc.options.operatorItemsTab().get()));
        } finally { openingCatalog=false; }
    }
}
