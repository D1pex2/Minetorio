package dev.minetorio.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minetorio.Minetorio;
import dev.minetorio.client.handcraft.HandcraftScreen;
import dev.minetorio.client.research.ResearchScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Client key bindings: Y opens the research screen, R the hand-crafting screen. */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class ModKeys {
    public static final String CATEGORY = "key.categories.minetorio";
    public static final KeyMapping RESEARCH = new KeyMapping("key.minetorio.research",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);
    public static final KeyMapping HANDCRAFT = new KeyMapping("key.minetorio.handcraft",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    private ModKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(RESEARCH);
        event.register(HANDCRAFT);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (RESEARCH.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new ResearchScreen());
        }
        while (HANDCRAFT.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new HandcraftScreen());
        }
    }
}
