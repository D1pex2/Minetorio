package dev.minetorio.client.character;

import dev.minetorio.Minetorio;
import dev.minetorio.content.character.CharacterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class CharacterClient {
    private CharacterClient() {}

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getNewScreen() instanceof InventoryScreen && mc.player != null
                && mc.player.inventoryMenu instanceof CharacterMenu menu) {
            event.setNewScreen(new CharacterScreen(menu, mc.player.getInventory()));
        }
    }
}
