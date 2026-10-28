package dev.minetorio.client.camera;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minetorio.Minetorio;
import dev.minetorio.client.ModKeys;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Free orbit of the top-down / remote view: holding the middle mouse button and dragging turns
 * the camera around its pivot (the point at the screen's centre) by any yaw and tilts it between
 * almost straight down and almost a side view ({@link BuildCamera#orbitBy}). A middle click without
 * a drag is still pick-block (delivered on release); a double click, or the reset key (End), goes
 * back to the default angle. Q/E keep turning in quarter steps (a free yaw snaps to the next one).
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class CameraOrbit {
    public static final KeyMapping RESET = new KeyMapping("key.minetorio.build_camera_reset",
            BuildCamera.IN_BUILD_CAMERA, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_END, ModKeys.CATEGORY);

    /** Screen pixels the cursor must move before a middle press counts as a drag (not a click). */
    private static final double DRAG_THRESHOLD = 4;
    private static final long DOUBLE_CLICK_MS = 300;

    private static boolean pressed, dragging;
    private static double pressX, pressY, lastX, lastY;
    private static long lastClickMs;

    private CameraOrbit() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(RESET);
    }

    /** The middle button in the view is ours: a drag orbits, a click is passed on as pick-block on release. */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (!BuildCamera.isActive() || mc.screen != null) return;
            pressed = true;
            dragging = false;
            pressX = lastX = mc.mouseHandler.xpos();
            pressY = lastY = mc.mouseHandler.ypos();
            event.setCanceled(true);
        } else if (event.getAction() == GLFW.GLFW_RELEASE && pressed) {
            pressed = false;
            event.setCanceled(true);
            if (dragging) {
                dragging = false;
                return;
            }
            if (!BuildCamera.isActive() || mc.screen != null) return;
            long now = Util.getMillis();
            if (now - lastClickMs <= DOUBLE_CLICK_MS) {
                lastClickMs = 0;
                BuildCamera.resetAngle();
            } else {
                lastClickMs = now;
                KeyMapping.click(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)); // pick-block
            }
        }
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        if (!pressed) return;
        Minecraft mc = Minecraft.getInstance();
        if (!BuildCamera.isActive() || mc.screen != null) {
            pressed = dragging = false;
            return;
        }
        double x = mc.mouseHandler.xpos(), y = mc.mouseHandler.ypos();
        if (!dragging && Math.hypot(x - pressX, y - pressY) >= DRAG_THRESHOLD) {
            dragging = true;
            lastClickMs = 0;
        }
        if (dragging) BuildCamera.orbitBy(x - lastX, y - lastY);
        lastX = x;
        lastY = y;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (RESET.consumeClick()) {
            if (BuildCamera.isActive()) BuildCamera.resetAngle();
        }
    }
}
