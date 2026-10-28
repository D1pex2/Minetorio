package dev.minetorio.client.camera;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minetorio.Minetorio;
import dev.minetorio.client.ModKeys;
import dev.minetorio.core.camera.LayerCut;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Layer cut of the top-down / remote view (multi-floor factories): PgDn / PgUp move a height cut,
 * Home clears it. Every block above the cut ({@link LayerCut#blockHidden}) is left out of the chunk
 * meshes, block entities and entities above it are not drawn, and the cursor ray passes through
 * them ({@link BuildCamera#pick}). The cut only exists while the view is on; leaving the view
 * clears it and rebuilds the meshes, so the world looks exactly as before.
 *
 * <p>How the blocks disappear: {@code RenderChunkRegionMixin} makes the snapshot that chunk
 * sections are compiled from report air (and full sky light) above {@link #renderCut()}, so the
 * compiler draws the faces exposed by the cut, skips hidden fluids and block entities, and the
 * section visibility graph sees the cut-away floors as open (lower floors are not occlusion-culled).
 * When the cut changes, only the section layers between the old and new cut are rebuilt
 * ({@link LayerCut#dirtySections}). {@code BlockEntityRenderDispatcherMixin} and
 * {@code EntityRenderDispatcherMixin} skip block entities and entities above the cut.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class LayerCutClient {
    public static final KeyMapping LAYER_DOWN = new KeyMapping("key.minetorio.layer_down",
            BuildCamera.IN_BUILD_CAMERA, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN, ModKeys.CATEGORY);
    public static final KeyMapping LAYER_UP = new KeyMapping("key.minetorio.layer_up",
            BuildCamera.IN_BUILD_CAMERA, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP, ModKeys.CATEGORY);
    public static final KeyMapping LAYER_CLEAR = new KeyMapping("key.minetorio.layer_clear",
            BuildCamera.IN_BUILD_CAMERA, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_HOME, ModKeys.CATEGORY);

    /** The cut the player chose (only while the view is on). */
    private static int cut = LayerCut.NONE;
    /** The cut the world is drawn with; read by the chunk compile threads, hence volatile. */
    private static volatile int renderCut = LayerCut.NONE;

    private LayerCutClient() {}

    /** The cut the cursor ray uses: the chosen one while the view is on, else {@link LayerCut#NONE}. */
    public static int cut() {
        return BuildCamera.isActive() ? cut : LayerCut.NONE;
    }

    /** The cut the world is drawn with (mixins; any thread). */
    public static int renderCut() {
        return renderCut;
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(LAYER_DOWN);
        event.register(LAYER_UP);
        event.register(LAYER_CLEAR);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (!BuildCamera.isActive() || level == null) {
            cut = LayerCut.NONE;
            drain(LAYER_DOWN);
            drain(LAYER_UP);
            drain(LAYER_CLEAR);
        } else {
            int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight();
            int pivot = Mth.floor(BuildCamera.pivotY() - 1e-3); // the block the pivot stands on
            while (LAYER_DOWN.consumeClick()) cut = LayerCut.lower(cut, pivot, minY, maxY - 1);
            while (LAYER_UP.consumeClick()) cut = LayerCut.raise(cut, maxY - 1);
            while (LAYER_CLEAR.consumeClick()) cut = LayerCut.NONE;
        }
        apply(mc, cut());
    }

    private static void drain(KeyMapping key) {
        while (key.consumeClick()) {
            // not in the view
        }
    }

    /** Switches the drawn cut to {@code next}, rebuilding the section layers it changes. */
    private static void apply(Minecraft mc, int next) {
        int old = renderCut;
        if (old == next) return;
        renderCut = next;
        Level level = mc.level;
        LevelRenderer renderer = mc.levelRenderer;
        if (level == null) return;
        int[] range = LayerCut.dirtySections(old, next, level.getMinBuildHeight(), level.getMaxBuildHeight());
        if (range == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        int cx = SectionPos.blockToSectionCoord(cam.x), cz = SectionPos.blockToSectionCoord(cam.z);
        int r = mc.options.getEffectiveRenderDistance() + 1;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = range[0]; y <= range[1]; y++) renderer.setSectionDirty(x, y, z);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        cut = LayerCut.NONE;
        renderCut = LayerCut.NONE;
    }

    /** "Layer: Y=…" in the top left corner while a cut is set. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        int c = cut();
        if (!LayerCut.isSet(c) || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        GuiGraphics g = event.getGuiGraphics();
        Component label = Component.translatable("minetorio.layer_cut.label", c);
        Component help = Component.translatable("minetorio.layer_cut.help",
                LAYER_UP.getTranslatedKeyMessage(), LAYER_DOWN.getTranslatedKeyMessage(), LAYER_CLEAR.getTranslatedKeyMessage());
        int w = Math.max(mc.font.width(label), mc.font.width(help));
        g.fill(2, 2, 8 + w, 27, 0x90000000);
        g.drawString(mc.font, label, 5, 5, 0xFFFFD27F, false);
        g.drawString(mc.font, help, 5, 16, 0xFFB0B0B0, false);
    }
}
