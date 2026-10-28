package dev.minetorio.client.alt;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Alt mode on/off (client). Toggled like Factorio's Alt key ({@code ModKeys.ALT_MODE}); it stays
 * as set for the rest of the session.
 */
public final class AltMode {
    private static boolean on;

    private AltMode() {}

    public static boolean isOn() { return on; }

    /** Flips alt mode and says so in the action bar. */
    public static void toggle(Player player) {
        on = !on;
        player.displayClientMessage(Component.translatable(on ? "minetorio.alt_mode.on" : "minetorio.alt_mode.off"), true);
    }
}
