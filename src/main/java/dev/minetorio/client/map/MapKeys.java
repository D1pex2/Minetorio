package dev.minetorio.client.map;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minetorio.Minetorio;
import dev.minetorio.client.ModKeys;
import dev.minetorio.content.map.ClientMapData;
import dev.minetorio.content.map.ClientWatchedChunks;
import dev.minetorio.content.orefield.ClientOreInfo;
import dev.minetorio.client.camera.BuildCamera;
import dev.minetorio.client.camera.RemoteViewClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Map key bindings (rebindable in Controls): M opens the full-screen map, N shows/hides the
 * minimap, = and - zoom the minimap. Also forgets the map when leaving a world.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class MapKeys {
    public static final KeyMapping MAP = new KeyMapping("key.minetorio.map",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, ModKeys.CATEGORY);
    public static final KeyMapping MINIMAP = new KeyMapping("key.minetorio.minimap",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, ModKeys.CATEGORY);
    public static final KeyMapping ZOOM_IN = new KeyMapping("key.minetorio.minimap_zoom_in",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, ModKeys.CATEGORY);
    public static final KeyMapping ZOOM_OUT = new KeyMapping("key.minetorio.minimap_zoom_out",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, ModKeys.CATEGORY);

    private MapKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(MAP);
        event.register(MINIMAP);
        event.register(ZOOM_IN);
        event.register(ZOOM_OUT);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (MAP.consumeClick()) {
            if (mc.screen != null || mc.player == null) continue;
            if (BuildCamera.isRemote()) RemoteViewClient.leaveToMap(); // back to the map over the remote place
            else mc.setScreen(new MapScreen());
        }
        while (MINIMAP.consumeClick()) MinimapHud.toggle();
        while (ZOOM_IN.consumeClick()) MinimapHud.zoom(1);
        while (ZOOM_OUT.consumeClick()) MinimapHud.zoom(-1);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        dev.minetorio.content.oilmap.OilMapPayloads.clearClient();
        ClientMapData.reset();
        ClientWatchedChunks.reset();
        ClientOreInfo.reset();
    }
}
