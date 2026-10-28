package dev.minetorio.client.camera;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.Minetorio;
import dev.minetorio.client.ModKeys;
import dev.minetorio.core.camera.TopDownCamera;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The top-down build camera (Factorio's view): V toggles it. The camera hangs above the player
 * looking down at {@link TopDownCamera#PITCH}, Q/E turn it in 90 degree steps, the mouse wheel
 * changes its height. The mouse cursor is free; the ray from the camera through it replaces the
 * crosshair ray, so vanilla clicks (break, place, use, attack) act on the block or entity under
 * the cursor with the usual packets, and the server's reach rules stay the same.
 *
 * <p>While active, {@code Minecraft.hitResult} holds the cursor target filtered to the player's
 * reach (a {@code MISS} beyond it, like vanilla's own picking), so everything that reads it —
 * the block outline, placement previews, area overlays, drag placement — follows the cursor
 * unchanged. {@link #cursorHit()} gives the unfiltered target for features that work beyond reach
 * (ghosts). The player turns to look at the cursor target (its body is drawn) and WASD walk
 * relative to the screen.
 *
 * <p>Hooks into vanilla: {@code CameraMixin} places the camera, {@code GameRendererMixin} replaces
 * the pick and turns off view bobbing, {@code MouseHandlerMixin} keeps the cursor free,
 * {@code MinecraftMixin} lets a held left button keep breaking without a grabbed mouse.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class BuildCamera {
    /** Keys that only act while the build camera is on and no screen is open; they never conflict. */
    public static final IKeyConflictContext IN_BUILD_CAMERA = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return active && Minecraft.getInstance().screen == null;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    public static final KeyMapping TOGGLE = new KeyMapping("key.minetorio.build_camera",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, ModKeys.CATEGORY);
    public static final KeyMapping ROTATE_LEFT = new KeyMapping("key.minetorio.build_camera_rotate_left",
            IN_BUILD_CAMERA, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, ModKeys.CATEGORY);
    public static final KeyMapping ROTATE_RIGHT = new KeyMapping("key.minetorio.build_camera_rotate_right",
            IN_BUILD_CAMERA, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, ModKeys.CATEGORY);

    /** Blocks the cursor ray reaches past the camera. */
    private static final double RAY_LENGTH = 160;
    /** Orbit center above the player's feet. */
    private static final double CENTER_Y = 1.0;
    /** Height the camera rises from when switched on (animates up to the chosen height). */
    private static final double START_HEIGHT = 3.0;
    private static final double YAW_RATE = 14, HEIGHT_RATE = 10;

    private static boolean active;
    private static boolean exitRequested;
    private static double targetYaw, yaw;
    private static double targetHeight = TopDownCamera.DEFAULT_HEIGHT, height;
    private static long lastFrameNanos;
    /** Field of view of the last rendered frame (degrees, vertical). */
    private static double fov = 70;
    @Nullable
    private static HitResult cursorHit;
    private static boolean cursorInReach;

    private BuildCamera() {}

    // --- public client API ----------------------------------------------------------------------

    /** Whether the top-down build camera is on. */
    public static boolean isActive() {
        return active;
    }

    /**
     * The target under the cursor, filtered to the player's reach (what {@code mc.hitResult} holds
     * while the camera is on); the vanilla {@code mc.hitResult} otherwise.
     */
    @Nullable
    public static HitResult hitResult() {
        return Minecraft.getInstance().hitResult;
    }

    /**
     * The block or entity under the cursor however far it is (a {@code MISS} at the end of the
     * ray over the sky); null when the camera is off. Use for ghosts and other out-of-reach work.
     */
    @Nullable
    public static HitResult cursorHit() {
        return active ? cursorHit : null;
    }

    /** Whether {@link #cursorHit()} is within the player's reach (real placing/breaking works). */
    public static boolean cursorInReach() {
        return active && cursorInReach;
    }

    /** Current (animated) camera yaw in degrees. */
    public static double yaw() {
        return yaw;
    }

    /** Camera yaw the camera turns to (a multiple of 90); WASD move relative to it. */
    public static double targetYaw() {
        return targetYaw;
    }

    // --- switching ------------------------------------------------------------------------------

    public static void toggle() {
        if (active) exit();
        else enter();
    }

    public static void enter() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (active || player == null || mc.getCameraEntity() != player) return;
        active = true;
        exitRequested = false;
        targetYaw = TopDownCamera.snapYaw(player.getYRot());
        yaw = player.getYRot();
        height = START_HEIGHT;
        lastFrameNanos = Util.getNanos();
        cursorHit = null;
        mc.mouseHandler.releaseMouse();
    }

    public static void exit() {
        if (!active) return;
        active = false;
        exitRequested = false;
        cursorHit = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.player != null) mc.mouseHandler.grabMouse();
    }

    // --- input ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(ROTATE_LEFT);
        event.register(ROTATE_RIGHT);
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active) return;
        // spectating another entity ends the view; a respawn or dimension change keeps it
        if (exitRequested || (mc.getCameraEntity() != null && !(mc.getCameraEntity() instanceof LocalPlayer))) {
            exit();
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        // Q/E default to vanilla's drop and inventory keys: in this view they turn the camera
        // instead, so swallow the clicks of whatever else shares their key before vanilla sees them.
        rotate(mc, ROTATE_LEFT, -90);
        rotate(mc, ROTATE_RIGHT, 90);
        if (mc.screen == null) aimPlayer(player);
    }

    private static void rotate(Minecraft mc, KeyMapping key, double by) {
        boolean clicked = false;
        while (key.consumeClick()) {
            targetYaw = TopDownCamera.wrap(targetYaw + by);
            clicked = true;
        }
        if (!clicked) return;
        for (KeyMapping other : mc.options.keyMappings) {
            if (other == ROTATE_LEFT || other == ROTATE_RIGHT || !other.same(key)) continue;
            while (other.consumeClick()) {
                // drained
            }
        }
    }

    /** Turns the player to look at the cursor target, so its body faces it and item-use rays agree. */
    private static void aimPlayer(LocalPlayer player) {
        HitResult hit = cursorHit;
        if (hit == null) return;
        Vec3 eye = player.getEyePosition();
        Vec3 at = hit.getLocation();
        if (eye.distanceToSqr(at) < 0.25) return;
        double[] a = TopDownCamera.aim(new double[] {eye.x, eye.y, eye.z}, new double[] {at.x, at.y, at.z});
        // keep the yaw continuous with the current one (the client interpolates yRotO -> yRot)
        float y = player.getYRot() + Mth.wrapDegrees((float) a[0] - player.getYRot());
        player.setYRot(y);
        player.setXRot(Mth.clamp((float) a[1], -90f, 90f));
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (TOGGLE.consumeClick()) {
            if (mc.screen == null && mc.player != null) toggle();
        }
    }

    /** WASD walk relative to the screen: the input is turned from the camera's yaw to the player's. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!active || event.getEntity() != Minecraft.getInstance().player) return;
        Input in = event.getInput();
        double[] r = TopDownCamera.remapInput(in.forwardImpulse, in.leftImpulse, targetYaw, event.getEntity().getYRot());
        in.forwardImpulse = (float) r[0];
        in.leftImpulse = (float) r[1];
    }

    /** The mouse wheel sets the camera height instead of scrolling the hotbar. */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!active || Minecraft.getInstance().screen != null) return;
        targetHeight = TopDownCamera.zoom(targetHeight, event.getScrollDeltaY());
        event.setCanceled(true);
    }

    /** Esc leaves the view instead of opening the pause menu (losing focus still pauses). */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (active && event.getNewScreen() instanceof PauseScreen && mc.screen == null && mc.isWindowActive()) {
            event.setCanceled(true);
            exitRequested = true; // switching the mouse back from inside setScreen would re-enter it
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        exitRequested = false;
        cursorHit = null;
    }

    // --- rendering ------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        long now = Util.getNanos();
        double dt = Math.min(0.25, (now - lastFrameNanos) / 1e9);
        lastFrameNanos = now;
        if (!active) return;
        yaw = TopDownCamera.approachAngle(yaw, targetYaw, YAW_RATE, dt, 0.01);
        if (Math.abs(TopDownCamera.wrap(yaw - targetYaw)) < 0.01) yaw = targetYaw;
        height = TopDownCamera.approach(height, targetHeight, HEIGHT_RATE, dt, 0.01);
    }

    /** Records the field of view the world is drawn with, for the cursor ray. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) fov = event.getFOV();
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (active) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (active && VanillaGuiLayers.CROSSHAIR.equals(event.getName())) event.setCanceled(true);
    }

    /** Outlines the block under the cursor in red when it is out of reach (vanilla outlines the reachable one). */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!active || cursorInReach || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!(cursorHit instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        BlockPos pos = hit.getBlockPos();
        var shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        AABB box = (shape.isEmpty() ? new AABB(BlockPos.ZERO) : shape.bounds()).move(pos).inflate(0.002);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, 0.95f, 0.2f, 0.2f, 0.9f);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    // --- called from the mixins -----------------------------------------------------------------

    /** Whether the camera for {@code entity} is the build camera (else vanilla's setup stands). */
    public static boolean overrides(Entity entity) {
        return active && entity == Minecraft.getInstance().player;
    }

    /** The camera position for the player at {@code partialTick}. */
    public static Vec3 cameraPosition(Entity entity, float partialTick) {
        double cx = Mth.lerp(partialTick, entity.xo, entity.getX());
        double cy = Mth.lerp(partialTick, entity.yo, entity.getY()) + CENTER_Y;
        double cz = Mth.lerp(partialTick, entity.zo, entity.getZ());
        double[] p = TopDownCamera.orbit(cx, cy, cz, yaw, TopDownCamera.PITCH, height);
        return new Vec3(p[0], p[1], p[2]);
    }

    public static float cameraYaw() {
        return (float) yaw;
    }

    public static float cameraPitch() {
        return (float) TopDownCamera.PITCH;
    }

    /**
     * Replaces vanilla's crosshair pick: casts the ray from the camera through the cursor, keeps
     * the full result for {@link #cursorHit()} and puts the reach-filtered one into
     * {@code mc.hitResult} / {@code mc.crosshairPickEntity}.
     */
    public static void pick(Minecraft mc, float partialTick) {
        LocalPlayer player = mc.player;
        if (!active || player == null || mc.level == null || mc.getCameraEntity() != player) return;
        Window window = mc.getWindow();
        if (window.getScreenWidth() <= 0 || window.getScreenHeight() <= 0 || window.getHeight() <= 0) return;
        double[] ndc = TopDownCamera.ndc(mc.mouseHandler.xpos(), mc.mouseHandler.ypos(), window.getScreenWidth(), window.getScreenHeight());
        double aspect = (double) window.getWidth() / window.getHeight();
        double[] d = TopDownCamera.cursorRay(yaw, TopDownCamera.PITCH, fov, aspect, ndc[0], ndc[1]);
        Vec3 dir = new Vec3(d[0], d[1], d[2]);
        Vec3 from = cameraPosition(player, partialTick);
        Vec3 to = from.add(dir.scale(RAY_LENGTH));
        BlockHitResult block = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double blockDist = block.getType() == HitResult.Type.MISS ? RAY_LENGTH : block.getLocation().distanceTo(from);
        Vec3 end = from.add(dir.scale(blockDist));
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, from, end, new AABB(from, end).inflate(1.0),
                e -> !e.isSpectator() && e.isPickable(), blockDist * blockDist);
        HitResult hit = entity != null ? entity : block;
        cursorHit = hit;

        Vec3 eye = player.getEyePosition(partialTick);
        double range = entity != null ? player.entityInteractionRange() : player.blockInteractionRange();
        Vec3 at = hit.getLocation();
        cursorInReach = hit.getType() != HitResult.Type.MISS && at.closerThan(eye, range);
        HitResult filtered = cursorInReach ? hit
                : BlockHitResult.miss(at, Direction.getNearest(at.x - eye.x, at.y - eye.y, at.z - eye.z), BlockPos.containing(at));
        mc.hitResult = filtered;
        mc.crosshairPickEntity = filtered instanceof EntityHitResult e ? e.getEntity() : null;
    }
}
