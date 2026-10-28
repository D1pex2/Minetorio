package dev.minetorio.client.map;

import dev.minetorio.content.map.ClientMapData;
import dev.minetorio.content.map.ClientWatchedChunks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * The full-screen map (M, as in Factorio): drag with the mouse to move, scroll to zoom about the
 * cursor (down to half a screen pixel per block), the block coordinates under the cursor (plus any {@link MapHoverInfo} lines) at the
 * bottom. Chunks a working radar watches right now are tinted and outlined
 * ({@link RadarCoverageOverlay}). Opens centered on the player. Overworld only: elsewhere it says the map is unavailable.
 * Does not pause the game.
 */
public class MapScreen extends Screen {
    /**
     * Most zoomed out: half a screen pixel per block, so at most about 40 region textures are
     * visible on a 1080p screen (they are cached in {@link MapTextures}).
     */
    private static final float MIN_SCREEN_PX_PER_BLOCK = 0.5f;
    private static final float MAX_SCALE = 8f;
    private static final float ZOOM_STEP = 1.25f;
    /** Zoom kept between openings. */
    private static float lastScale = 2f;

    private double cx;
    private double cz;
    private float scale = lastScale;

    public MapScreen() {
        super(Component.translatable("minetorio.map.title"));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            cx = mc.player.getX();
            cz = mc.player.getZ();
        }
    }

    static boolean available() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == Level.OVERWORLD;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!available()) {
            g.fill(0, 0, width, height, MapTextures.UNEXPLORED);
            g.drawCenteredString(font, Component.translatable("minetorio.map.unavailable"), width / 2, height / 2 - 4, 0xFFFFFFFF);
            return;
        }
        MapTextures.draw(g, cx, cz, scale, 0, 0, width, height);
        RadarCoverageOverlay.draw(g, cx, cz, scale, 0, 0, width, height);
        int bx = Mth.floor(cx + (mouseX - width / 2.0) / scale), bz = Mth.floor(cz + (mouseY - height / 2.0) / scale);
        int color = ClientMapData.colorAt(bx, bz);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("minetorio.map.coords", bx, bz));
        if (color < 0) {
            lines.add(Component.translatable("minetorio.map.unexplored"));
        } else {
            if (ClientWatchedChunks.isWatched(bx >> 4, bz >> 4)) lines.add(Component.translatable("minetorio.map.watched"));
            for (MapHoverInfo p : MapHoverInfo.PROVIDERS) p.appendLines(bx, bz, color, lines);
            MapHoverInfo.MapView view = new MapHoverInfo.MapView(cx, cz, scale, 0, 0, width, height);
            g.enableScissor(0, 0, width, height);
            for (MapHoverInfo p : MapHoverInfo.PROVIDERS) p.drawOverlay(g, view, bx, bz, color);
            g.disableScissor();
        }
        MapOverlay.drawPlayers(g, cx, cz, scale, 0, 0, width, height, partialTick);
        g.drawCenteredString(font, Component.translatable("minetorio.map.help", MapKeys.MAP.getTranslatedKeyMessage()),
                width / 2, 6, 0xFFB0B0B0);
        int y = height - 6 - lines.size() * 10;
        for (Component line : lines) {
            g.drawString(font, line, 6, y, 0xFFFFFFFF, true);
            y += 10;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        cx -= dragX / scale;
        cz -= dragY / scale;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return false;
        double wx = cx + (mouseX - width / 2.0) / scale, wz = cz + (mouseY - height / 2.0) / scale;
        float min = MIN_SCREEN_PX_PER_BLOCK / (float) Minecraft.getInstance().getWindow().getGuiScale();
        scale = Mth.clamp(scale * (float) Math.pow(ZOOM_STEP, scrollY), min, MAX_SCALE);
        lastScale = scale;
        // keep the point under the cursor in place
        cx = wx - (mouseX - width / 2.0) / scale;
        cz = wz - (mouseY - height / 2.0) / scale;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MapKeys.MAP.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
