package dev.minetorio.client.selection;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.minetorio.Minetorio;
import dev.minetorio.client.area.OverlayQuads;
import dev.minetorio.client.camera.BuildCamera;
import dev.minetorio.core.selection.SelectionBox;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Area selection with a selection tool in hand (deconstruction / upgrade planner, the blueprint):
 * generic, every tool registers a {@link Tool} and gets a finished {@link SelectionBox}.
 *
 * <p><b>First person</b>: right-click a block for the first corner, right-click another for the
 * second (targets up to {@value #REACH} blocks away); the box then stays and the mouse wheel moves
 * the face you look at outward (up) or inward (down); right-click again applies it (Shift held:
 * the tool's alternative, e.g. unmark). Left-click cancels. Right-click into the air with no
 * selection opens the tool's settings.
 *
 * <p><b>Top-down / remote view</b> ({@link BuildCamera}): hold the left button and drag a rectangle
 * under the cursor; letting go applies it ({@link SelectionBox#column}: the rectangle from a little
 * below the ground to well above it). Shift held on release: the alternative. Right-click opens
 * the settings.
 *
 * <p>While a tool is held its clicks never reach vanilla (no block breaking with a planner).
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class AreaSelection {
    /** How far first-person corner clicks reach. */
    public static final double REACH = 64;

    /** A selection tool. */
    public interface Tool {
        /** Whether {@code held} (the main hand) is this tool. */
        boolean matches(ItemStack held);

        /** Applies a finished selection; {@code alternative}: Shift was held (unmark / cancel). */
        void apply(SelectionBox box, boolean alternative);

        /** Right-click without a selection: open the tool's settings (may do nothing). */
        void openSettings();

        /** Outline color (ARGB) of the box, normal and with Shift. */
        int color(boolean alternative);
    }

    private static final List<Tool> TOOLS = new CopyOnWriteArrayList<>();

    @Nullable private static Tool tool;
    @Nullable private static BlockPos corner;
    @Nullable private static SelectionBox box;
    @Nullable private static BlockPos dragStart;
    @Nullable private static BlockPos hover;
    /** The use key was handled and is still held: ignore vanilla's repeats until it is let go. */
    private static boolean useLatched;

    private AreaSelection() {}

    public static void register(Tool t) {
        TOOLS.add(t);
    }

    @Nullable
    private static Tool held() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return null;
        ItemStack s = p.getMainHandItem();
        for (Tool t : TOOLS) if (t.matches(s)) return t;
        return null;
    }

    /** The current (finished, adjustable) first-person box, or null. */
    @Nullable
    public static SelectionBox pending() { return box; }

    public static void reset() {
        corner = null;
        box = null;
        dragStart = null;
    }

    private static boolean alternative() {
        return Minecraft.getInstance().options.keyShift.isDown();
    }

    // --- picking --------------------------------------------------------------------------------

    /** The block the selection points at: under the cursor in the build camera, else along the look up to {@link #REACH}. */
    @Nullable
    private static BlockPos target() {
        Minecraft mc = Minecraft.getInstance();
        if (BuildCamera.isActive()) {
            HitResult h = BuildCamera.cursorHit();
            return h instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b.getBlockPos() : null;
        }
        if (mc.player == null) return null;
        HitResult h = mc.player.pick(REACH, 1f, false);
        return h instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b.getBlockPos() : null;
    }

    // --- input ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Tool now = held();
        if (now != tool) {
            reset();
            tool = now;
        }
        if (!mc.options.keyUse.isDown()) useLatched = false;
        if (tool == null) return;
        BlockPos t = target();
        if (t != null) hover = t;
        if (dragStart != null && !BuildCamera.isActive()) dragStart = null;
    }

    /** First person: the use and attack keys with a tool in hand. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isPickBlock()) return;
        Tool t = held();
        if (t == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (BuildCamera.isActive()) return; // mouse buttons handle the build camera (onMouseButton)
        if (event.isAttack()) {
            reset();
            return;
        }
        if (!event.isUseItem() || useLatched) return;
        useLatched = true;
        BlockPos at = target();
        if (box != null) {
            t.apply(box, alternative());
            reset();
        } else if (at == null) {
            if (corner == null) t.openSettings();
        } else if (corner == null) {
            corner = at.immutable();
        } else {
            box = SelectionBox.of(corner.getX(), corner.getY(), corner.getZ(), at.getX(), at.getY(), at.getZ());
            corner = null;
        }
    }

    /** First person: the wheel moves the looked-at face of the pending box. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (box == null || mc.screen != null || BuildCamera.isActive() || held() == null || mc.player == null) return;
        int face = lookedFace(mc.player);
        if (face < 0) return;
        event.setCanceled(true);
        SelectionBox next = box.push(face, event.getScrollDeltaY() > 0 ? 1 : -1);
        if (next.withinLimits()) box = next;
    }

    private static int lookedFace(LocalPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        return box.faceHit(eye.x, eye.y, eye.z, look.x, look.y, look.z);
    }

    /** Build camera: left drag selects, right-click opens the settings. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!BuildCamera.isActive() || mc.screen != null) return;
        Tool t = held();
        if (t == null) return;
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            event.setCanceled(true);
            if (event.getAction() == GLFW.GLFW_PRESS) {
                BlockPos at = target();
                dragStart = at == null ? null : at.immutable();
                hover = dragStart;
            } else if (event.getAction() == GLFW.GLFW_RELEASE && dragStart != null) {
                BlockPos end = target() != null ? target() : hover;
                if (end != null) {
                    SelectionBox b = SelectionBox.column(dragStart.getX(), dragStart.getY(), dragStart.getZ(), end.getX(), end.getY(), end.getZ());
                    if (b.withinLimits()) t.apply(b, alternative());
                    else tooLarge();
                }
                dragStart = null;
            }
        } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            event.setCanceled(true);
            if (event.getAction() == GLFW.GLFW_PRESS) t.openSettings();
        }
    }

    private static void tooLarge() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(Component.translatable("minetorio.selection.too_large", SelectionBox.MAX_SIDE), true);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        tool = null;
    }

    // --- drawing --------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Tool t = held();
        Minecraft mc = Minecraft.getInstance();
        if (t == null || mc.player == null) return;
        boolean alt = alternative();
        int color = t.color(alt);
        SelectionBox show = box;
        if (show == null && corner != null && hover != null) {
            show = SelectionBox.of(corner.getX(), corner.getY(), corner.getZ(), hover.getX(), hover.getY(), hover.getZ());
        }
        if (show == null && dragStart != null && hover != null) {
            show = SelectionBox.column(dragStart.getX(), dragStart.getY(), dragStart.getZ(), hover.getX(), hover.getY(), hover.getZ());
        }
        BlockPos h = target();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        if (show != null) {
            if (!show.withinLimits()) {
                r = 1f;
                g = 0.2f;
                b = 0.2f;
            }
            LevelRenderer.renderLineBox(pose, lines, show.minX() - 0.01, show.minY() - 0.01, show.minZ() - 0.01,
                    show.maxX() + 1.01, show.maxY() + 1.01, show.maxZ() + 1.01, r, g, b, 1f);
        } else if (h != null) {
            LevelRenderer.renderLineBox(pose, lines, h.getX() - 0.005, h.getY() - 0.005, h.getZ() - 0.005,
                    h.getX() + 1.005, h.getY() + 1.005, h.getZ() + 1.005, r, g, b, 0.8f);
        }
        if (corner != null) {
            LevelRenderer.renderLineBox(pose, lines, corner.getX() - 0.02, corner.getY() - 0.02, corner.getZ() - 0.02,
                    corner.getX() + 1.02, corner.getY() + 1.02, corner.getZ() + 1.02, 1f, 1f, 1f, 1f);
        }
        buffers.endBatch(RenderType.lines());
        if (box != null && !BuildCamera.isActive()) drawFace(pose, lookedFace(mc.player), (color & 0x00FFFFFF) | 0x40000000);
        pose.popPose();
    }

    /** Tints the face the wheel would move. */
    private static void drawFace(PoseStack pose, int face, int argb) {
        if (face < 0) return;
        MultiBufferSource.BufferSource buffers = OverlayQuads.buffers();
        VertexConsumer vc = buffers.getBuffer(OverlayQuads.GROUND);
        Matrix4f m = pose.last().pose();
        float x0 = box.minX() - 0.02f, y0 = box.minY() - 0.02f, z0 = box.minZ() - 0.02f;
        float x1 = box.maxX() + 1.02f, y1 = box.maxY() + 1.02f, z1 = box.maxZ() + 1.02f;
        switch (face) {
            case 0 -> OverlayQuads.quad(vc, m, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            case 1 -> OverlayQuads.quad(vc, m, argb, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
            case 2 -> OverlayQuads.quad(vc, m, argb, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
            case 3 -> OverlayQuads.quad(vc, m, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
            case 4 -> OverlayQuads.quad(vc, m, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
            default -> OverlayQuads.quad(vc, m, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        }
        buffers.endBatch(OverlayQuads.GROUND);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null || held() == null) return;
        String key;
        if (BuildCamera.isActive()) key = dragStart != null ? "minetorio.selection.hint.dragging" : "minetorio.selection.hint.top_down";
        else if (box != null) key = "minetorio.selection.hint.box";
        else if (corner != null) key = "minetorio.selection.hint.corner";
        else key = "minetorio.selection.hint.start";
        GuiGraphics g = event.getGuiGraphics();
        Component text = Component.translatable(key);
        if (box != null) text = text.copy().append(" (" + box.sizeX() + "×" + box.sizeY() + "×" + box.sizeZ() + ")");
        int y = g.guiHeight() - 72;
        g.drawCenteredString(mc.font, text, g.guiWidth() / 2, y, 0xFFE0E0E0);
    }
}
