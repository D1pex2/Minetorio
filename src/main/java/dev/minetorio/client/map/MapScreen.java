package dev.minetorio.client.map;

import dev.minetorio.client.camera.RemoteViewClient;
import dev.minetorio.content.map.ClientMapData;
import dev.minetorio.content.map.ClientWatchedChunks;
import java.util.ArrayList;
import java.util.List;
import dev.minetorio.core.camera.RemoteArea;
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
 *
 * <p>Remote view: at the closest zoom, a left click (without dragging) or one more wheel notch in
 * over a chunk a radar watches (or that the player sees himself) asks for the remote view there
 * ({@link RemoteViewClient}); Esc in the remote view comes back here, centred on that place.
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
    private double pressX = Double.NaN, pressY = Double.NaN;
    private boolean dragged;

    public MapScreen() {
        super(Component.translatable("minetorio.map.title"));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            cx = mc.player.getX();
            cz = mc.player.getZ();
        }
    }

    /** The map centred on block column {@code (x, z)} (coming back from the remote view). */
    public MapScreen(double x, double z) {
        this();
        cx = x;
        cz = z;
    }

    /** Whether the map is zoomed in all the way (where the remote view starts). */
    private boolean atMaxZoom() {
        return scale >= MAX_SCALE - 1e-3f;
    }

    private double worldX(double mouseX) {
        return cx + (mouseX - width / 2.0) / scale;
    }

    private double worldZ(double mouseY) {
        return cz + (mouseY - height / 2.0) / scale;
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
            if (RemoteViewClient.eligible(bx >> 4, bz >> 4)) {
                lines.add(Component.translatable(atMaxZoom() ? "minetorio.map.remote_click" : "minetorio.map.remote_zoom"));
            }
            for (MapHoverInfo p : MapHoverInfo.PROVIDERS) p.appendLines(bx, bz, color, lines);
            MapHoverInfo.MapView view = new MapHoverInfo.MapView(cx, cz, scale, 0, 0, width, height);
            g.enableScissor(0, 0, width, height);
            for (MapHoverInfo p : MapHoverInfo.PROVIDERS) p.drawOverlay(g, view, bx, bz, color);
            g.disableScissor();
        }
        MapOverlay.drawPlayers(g, cx, cz, scale, 0, 0, width, height, partialTick);
        g.drawCenteredString(font, Component.translatable("minetorio.map.help", MapKeys.MAP.getTranslatedKeyMessage()),
                width / 2, 6, 0xFFB0B0B0);
        Component status = RemoteViewClient.message();
        if (status != null) g.drawCenteredString(font, status, width / 2, 18, 0xFFFF8080);
        int y = height - 6 - lines.size() * 10;
        for (Component line : lines) {
            g.drawString(font, line, 6, y, 0xFFFFFFFF, true);
            y += 10;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!Double.isNaN(pressX) && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 3) dragged = true;
        cx -= dragX / scale;
        cz -= dragY / scale;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            pressX = mouseX;
            pressY = mouseY;
            dragged = false;
        }
        return super.mouseClicked(mouseX, mouseY, button) || button == 0;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && !Double.isNaN(pressX) && !dragged && available() && atMaxZoom()) {
            RemoteViewClient.requestEnter(worldX(mouseX), worldZ(mouseY));
        }
        if (button == 0) pressX = pressY = Double.NaN;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return false;
        if (scrollY > 0 && atMaxZoom() && available()) { // zooming in past the closest zoom: into the remote view
            double wx = worldX(mouseX), wz = worldZ(mouseY);
            if (RemoteViewClient.eligible(RemoteArea.chunkOf(wx), RemoteArea.chunkOf(wz))) {
                RemoteViewClient.requestEnter(wx, wz);
                return true;
            }
        }
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
