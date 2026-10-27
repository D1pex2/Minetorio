package dev.minetorio.client.map;

import dev.minetorio.Minetorio;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * HUD layer: the minimap, {@value #SIZE}×{@value #SIZE} GUI px in the top right corner (below the
 * status effect icons when there are any), north up, centered on the player, 1 block = 1 px by
 * default ({@link #zoom} steps through {@link #SCALES}). Drawn from the shared region textures
 * ({@link MapTextures}). Hidden with N, with the HUD (F1), outside the Overworld and while the
 * full-screen map is open.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class MinimapHud {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "minimap");
    public static final int SIZE = 128;
    private static final int MARGIN = 4;
    static final float[] SCALES = {0.25f, 0.5f, 1f, 2f, 4f};
    private static int scaleIndex = 2;
    private static boolean visible = true;

    private MinimapHud() {}

    static void toggle() { visible = !visible; }

    static void zoom(int steps) { scaleIndex = Math.max(0, Math.min(SCALES.length - 1, scaleIndex + steps)); }

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ID, MinimapHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!visible || mc.options.hideGui || player == null || !MapScreen.available() || mc.screen instanceof MapScreen) return;
        float partial = delta.getGameTimeDeltaPartialTick(true);
        int x1 = g.guiWidth() - MARGIN, x0 = x1 - SIZE;
        int y0 = MARGIN + effectRows(player) * 26, y1 = y0 + SIZE;
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF000000);
        float scale = SCALES[scaleIndex];
        double cx = player.getX(partial), cz = player.getZ(partial);
        g.enableScissor(x0, y0, x1, y1);
        MapTextures.draw(g, cx, cz, scale, x0, y0, x1, y1);
        MapOverlay.drawPlayers(g, cx, cz, scale, x0, y0, x1, y1, partial);
        g.disableScissor();
        String coords = player.getBlockX() + ", " + player.getBlockZ();
        g.drawString(mc.font, coords, x1 - mc.font.width(coords), y1 + 3, 0xFFE0E0E0, true);
    }

    /** Rows of vanilla status effect icons in the top right corner (beneficial, harmful). */
    private static int effectRows(LocalPlayer player) {
        boolean good = false, bad = false;
        for (MobEffectInstance e : player.getActiveEffects()) {
            if (!e.showIcon()) continue;
            if (e.getEffect().value().isBeneficial()) good = true;
            else bad = true;
        }
        return bad ? 2 : good ? 1 : 0;
    }
}
