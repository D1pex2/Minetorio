package dev.minetorio.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minetorio.Minetorio;
import dev.minetorio.client.research.ResearchScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Client key bindings: Y opens the research screen. */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class ModKeys {
    public static final String CATEGORY = "key.categories.minetorio";
    public static final KeyMapping RESEARCH = new KeyMapping("key.minetorio.research",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);

    private ModKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(RESEARCH);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (RESEARCH.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new ResearchScreen());
        }
    }
}
