package dev.minetorio.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minetorio.Minetorio;
import dev.minetorio.client.alt.AltMode;
import dev.minetorio.client.camera.BuildCamera;
import dev.minetorio.client.character.CharacterScreen;
import dev.minetorio.content.character.CharacterMenu;
import dev.minetorio.client.research.ResearchScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client key bindings: Y opens the research screen, R the hand-crafting screen, Left Alt toggles
 * alt mode, Left Control held while right-clicking places ghosts.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class ModKeys {
    public static final String CATEGORY = "key.categories.minetorio";
    public static final KeyMapping RESEARCH = new KeyMapping("key.minetorio.research",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);
    public static final KeyMapping HANDCRAFT = new KeyMapping("key.minetorio.handcraft",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    /** Alt mode toggle (Factorio's Alt): machine icons and status warnings, see {@link AltMode}. */
    public static final KeyMapping ALT_MODE = new KeyMapping("key.minetorio.alt_mode",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);

    /**
     * Ghost placement (Factorio's Shift-click): held while right-clicking with a Minetorio block
     * item, the click places a ghost instead of the block ({@code HandGhosts}); held at the start of
     * a drag, the line is laid as ghosts. Only read at the moment of a right-click (and for the
     * preview while such an item is held), so sharing Left Control with vanilla sprint is harmless:
     * NeoForge delivers the key to both bindings, and vanilla has no Ctrl+right-click.
     */
    public static final KeyMapping GHOST = new KeyMapping("key.minetorio.ghost",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);

    private ModKeys() {}

    /** Whether the ghost key is physically held, screens included (a keyboard binding only). */
    public static boolean ghostKeyDown() {
        InputConstants.Key key = GHOST.getKey();
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()
                && InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key.getValue());
    }

    /** Whether the ghost key is held (in game, no screen open); always in the remote view, where every placement is a ghost. */
    public static boolean ghostHeld() {
        return Minecraft.getInstance().screen == null && (GHOST.isDown() || BuildCamera.isRemote());
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(RESEARCH);
        event.register(HANDCRAFT);
        event.register(ALT_MODE);
        event.register(GHOST);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (RESEARCH.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new ResearchScreen());
        }
        while (HANDCRAFT.consumeClick()) {
            if (mc.screen == null && mc.player != null && mc.player.inventoryMenu instanceof CharacterMenu menu) {
                if (mc.player.containerMenu != menu) mc.player.closeContainer();
                mc.player.containerMenu = menu;
                mc.setScreen(new CharacterScreen(menu, mc.player.getInventory()));
            }
        }
        while (ALT_MODE.consumeClick()) {
            if (mc.player != null) AltMode.toggle(mc.player);
        }
    }
}
