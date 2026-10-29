package dev.minetorio.client.blueprint;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.Minetorio;
import dev.minetorio.client.ModKeys;
import dev.minetorio.client.camera.BuildCamera;
import dev.minetorio.client.camera.RemoteViewClient;
import dev.minetorio.client.machine.MultiblockPlacementPreview;
import dev.minetorio.client.robot.GhostRenderer;
import dev.minetorio.content.blueprint.BlueprintBookData;
import dev.minetorio.content.blueprint.BlueprintBookItem;
import dev.minetorio.content.blueprint.BlueprintData;
import dev.minetorio.content.blueprint.BlueprintItem;
import dev.minetorio.content.blueprint.BlueprintPaste;
import dev.minetorio.content.blueprint.BlueprintPaste.PartPlan;
import dev.minetorio.content.blueprint.BlueprintPayloads;
import dev.minetorio.content.blueprint.BlueprintRecord;
import dev.minetorio.content.blueprint.BlueprintStacks;
import dev.minetorio.content.research.RecipeLocks;
import dev.minetorio.core.blueprint.BlueprintTransform;
import dev.minetorio.core.blueprint.BlueprintDrag;
import dev.minetorio.core.blueprint.Blueprints;
import dev.minetorio.core.robot.GhostReach;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of the blueprint ({@link BlueprintItem}).
 *
 * <p><b>Getting one:</b> Ctrl+B ({@link #GIVE}) puts a new empty blueprint in the hand (free, as
 * Factorio's shortcut); Ctrl+Shift+B (the same binding with Shift also held) an empty blueprint
 * book. B ({@link #LIBRARY}) opens the blueprint library ({@link BlueprintLibraryScreen}).
 *
 * <p><b>Books</b> ({@link BlueprintBookItem}): held, a book pastes its active blueprint exactly like
 * a blueprint; Ctrl + wheel picks the active entry (shown at the bottom of the screen). Right-click
 * a book in the inventory to open it ({@link BlueprintBookScreen}), a blueprint to name it, set its
 * icons or copy its string ({@link BlueprintMetaScreen}).
 *
 * <p><b>Copying</b> (empty blueprint). First person: right-click the first corner, right-click the
 * second; the box shows translucent. Hold Left Shift ({@link #ADJUST}) and turn the wheel to move
 * the face you look at out or in (that face is highlighted). Right-click again (or Enter) copies;
 * left-click starts over. Top-down and remote view: press the right button and drag a rectangle
 * under the cursor; releasing copies it, the height found by the server (lowest to highest
 * Minetorio block in the columns).
 *
 * <p><b>Pasting</b> (filled blueprint): the whole blueprint shows as ghosts centred under the
 * cursor (first person: on top of the block looked at; top-down: under the cursor, out to ghost
 * reach), red where a part cannot go, yellow boxes on blocks a Shift paste would mark for
 * deconstruction. R turns it, F mirrors it left-right, G front-back. Right-click pastes the ghosts
 * (one packet; the server re-plans everything); holding it repeats whole blueprints along the
 * cursor's path, spaced by the transformed footprint. Shift+right-click pastes through vanilla obstacles.
 * While a blueprint is held, R, F and G act on it only (hand crafting and swap-hands wait).
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class BlueprintClient {
    /** Keys that act only while a blueprint is in the main hand (no screen); they conflict only with each other. */
    public static final IKeyConflictContext HOLDING_BLUEPRINT = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return holding() != null;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    public static final KeyMapping GIVE = new KeyMapping("key.minetorio.blueprint", KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, ModKeys.CATEGORY);
    /** The blueprint library (B alone; Ctrl+B is {@link #GIVE}). */
    public static final KeyMapping LIBRARY = new KeyMapping("key.minetorio.blueprint_library", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, ModKeys.CATEGORY);
    public static final KeyMapping ROTATE = new KeyMapping("key.minetorio.blueprint_rotate", HOLDING_BLUEPRINT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, ModKeys.CATEGORY);
    public static final KeyMapping FLIP_X = new KeyMapping("key.minetorio.blueprint_flip_x", HOLDING_BLUEPRINT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, ModKeys.CATEGORY);
    public static final KeyMapping FLIP_Z = new KeyMapping("key.minetorio.blueprint_flip_z", HOLDING_BLUEPRINT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, ModKeys.CATEGORY);
    public static final KeyMapping ADJUST = new KeyMapping("key.minetorio.blueprint_adjust", HOLDING_BLUEPRINT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, ModKeys.CATEGORY);
    public static final KeyMapping CONFIRM = new KeyMapping("key.minetorio.blueprint_confirm", HOLDING_BLUEPRINT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_ENTER, ModKeys.CATEGORY);

    private static final float[] BLUE = {GhostRenderer.R, GhostRenderer.G, GhostRenderer.B, GhostRenderer.A};
    private static final float[] RED = {1.0f, 0.35f, 0.3f, 0.5f};
    private static final float[] YELLOW = {1.0f, 0.85f, 0.2f};
    /** Ticks a paste preview is reused while nothing changes. */
    private static final int PLAN_TTL = 10;

    private static BlueprintTransform transform = BlueprintTransform.IDENTITY;
    /** First person: the clicked corners; {@link #boxMin}/{@link #boxMax} the (adjusted) box once both are set. */
    @Nullable
    private static BlockPos corner;
    @Nullable
    private static BlockPos boxMin, boxMax;
    /** Top-down: where the rectangle drag started, or null. */
    @Nullable
    private static BlockPos dragStart;
    /** A held right button acts once: armed again when released. */
    private static boolean armed = true;
    private static final int PASTE_REPEAT_TICKS = 4;
    private static final int MAX_PENDING_PASTES = 64;
    private static final ArrayDeque<BlockPos> pendingPastes = new ArrayDeque<>();
    @Nullable private static BlueprintDrag pasteDrag;
    @Nullable private static BlueprintData dragData;
    @Nullable private static BlockPos pasteAnchor;
    private static BlueprintTransform dragTransform = BlueprintTransform.IDENTITY;
    private static long lastPasteTick;
    private static boolean dragForce;

    // paste preview cache
    @Nullable
    private static List<PartPlan> plans;
    @Nullable
    private static Object planKey;
    private static long planTick;

    private BlueprintClient() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(GIVE);
        event.register(LIBRARY);
        event.register(ROTATE);
        event.register(FLIP_X);
        event.register(FLIP_Z);
        event.register(ADJUST);
        event.register(CONFIRM);
    }

    /** The blueprint or book in the main hand (no screen open), or null. */
    @Nullable
    static ItemStack holding() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return null;
        ItemStack s = mc.player.getMainHandItem();
        return BlueprintStacks.isBlueprintLike(s) ? s : null;
    }

    /** Whether {@code held} selects an area to copy: an empty blueprint (not a book). */
    private static boolean selecting(@Nullable ItemStack held) {
        return held != null && BlueprintItem.isEmptyBlueprint(held);
    }

    public static BlueprintTransform transform() {
        return transform;
    }

    // --- keys --------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack held = holding();
        BlueprintData data = held == null ? null : BlueprintStacks.activeData(held);
        if (data != null) {
            while (ROTATE.consumeClick()) transform = transform.rotated();
            while (FLIP_X.consumeClick()) transform = transform.flippedX();
            while (FLIP_Z.consumeClick()) transform = transform.flippedZ();
            // the same keys' other bindings (hand crafting on R, swap hands on F) wait while a blueprint is held
            drainOthers(mc, ROTATE);
            drainOthers(mc, FLIP_X);
            drainOthers(mc, FLIP_Z);
        } else {
            drain(ROTATE);
            drain(FLIP_X);
            drain(FLIP_Z);
        }
        if (!selecting(held)) {
            corner = null;
            boxMin = boxMax = null;
            dragStart = null;
        }
        while (CONFIRM.consumeClick()) {
            if (selecting(held) && boxMin != null) confirmBox();
        }
        if (!mc.options.keyUse.isDown()) armed = true;
        tickPasteDrag(mc, data);
        if (dragStart != null && selecting(held) && !mc.options.keyUse.isDown()) {
            BlockPos end = cursorCell(mc, false);
            PacketDistributor.sendToServer(new BlueprintPayloads.Capture(dragStart, end == null ? dragStart : end, true));
            dragStart = null;
        }
    }

    @SubscribeEvent
    public static void onTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (GIVE.consumeClick()) {
            if (mc.player != null && mc.screen == null) PacketDistributor.sendToServer(new BlueprintPayloads.Give(Screen.hasShiftDown()));
        }
        while (LIBRARY.consumeClick()) {
            if (mc.player != null && mc.screen == null && !Screen.hasControlDown()) mc.setScreen(new BlueprintLibraryScreen());
        }
    }

    private static void drain(KeyMapping key) {
        while (key.consumeClick()) {
            // dropped
        }
    }

    private static void drainOthers(Minecraft mc, KeyMapping key) {
        for (KeyMapping other : mc.options.keyMappings) {
            if (other != key && other.same(key)) drain(other);
        }
    }

    // --- clicks ------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || event.isPickBlock()) return;
        ItemStack held = player.getMainHandItem();
        if (!BlueprintStacks.isBlueprintLike(held)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        BlueprintData data = BlueprintStacks.activeData(held);
        if (event.isAttack()) {
            clearPasteDrag();
            corner = null;
            boxMin = boxMax = null;
            dragStart = null;
            return;
        }
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || !armed) return;
        armed = false;
        if (data != null) {
            BlockPos anchor = cursorCell(mc, true);
            if (anchor != null) {
                clearPasteDrag();
                int[] size = Blueprints.size(BlueprintPaste.transformed(data, transform));
                pasteDrag = new BlueprintDrag(anchor.getX(), anchor.getZ(), size[0], size[2]);
                dragData = data;
                dragTransform = transform;
                pasteAnchor = anchor;
                dragForce = mc.options.keyShift.isDown();
                lastPasteTick = mc.level.getGameTime();
                PacketDistributor.sendToServer(new BlueprintPayloads.Paste(anchor, (byte) transform.bits(), mc.options.keyShift.isDown()));
                planKey = null; // re-plan against the new ghosts
            }
            return;
        }
        if (!selecting(held)) return; // a book whose active entry is empty
        if (BuildCamera.isActive()) {
            dragStart = cursorCell(mc, false);
            return;
        }
        BlockHitResult hit = mc.hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b : null;
        if (boxMin != null) {
            confirmBox();
        } else if (hit == null) {
            return;
        } else if (corner == null) {
            corner = hit.getBlockPos().immutable();
        } else {
            setBox(corner, hit.getBlockPos());
            corner = null;
        }
    }

    /** Samples every tick so a quick cursor move does not leave holes; sends at the server's repeat budget. */
    private static void tickPasteDrag(Minecraft mc, @Nullable BlueprintData data) {
        if (pasteDrag == null) return;
        if (data != dragData || mc.level == null || !transform.equals(dragTransform)) {
            clearPasteDrag();
            return;
        }
        boolean held = mc.options.keyUse.isDown();
        if (held) {
            BlockPos target = cursorCell(mc, true);
            if (target != null) pasteAnchor = target;
            dragForce = mc.options.keyShift.isDown();
        }
        if (pasteAnchor != null && pendingPastes.size() < MAX_PENDING_PASTES) {
            for (BlueprintDrag.Cell cell : pasteDrag.extend(pasteAnchor.getX(), pasteAnchor.getZ(), MAX_PENDING_PASTES - pendingPastes.size())) {
                pendingPastes.add(new BlockPos(cell.x(), pasteAnchor.getY(), cell.z()));
            }
        }
        long now = mc.level.getGameTime();
        if (!pendingPastes.isEmpty() && (now < lastPasteTick || now - lastPasteTick >= PASTE_REPEAT_TICKS)) {
            PacketDistributor.sendToServer(new BlueprintPayloads.Paste(pendingPastes.removeFirst(), (byte) dragTransform.bits(), dragForce));
            lastPasteTick = now;
            planKey = null;
        }
        if (!held && pendingPastes.isEmpty()) clearPasteDrag();
    }

    private static void clearPasteDrag() {
        pasteDrag = null;
        dragData = null;
        pasteAnchor = null;
        pendingPastes.clear();
    }

    private static void setBox(BlockPos a, BlockPos b) {
        boxMin = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        boxMax = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    private static void confirmBox() {
        if (boxMin == null || boxMax == null) return;
        PacketDistributor.sendToServer(new BlueprintPayloads.Capture(boxMin, boxMax, false));
        boxMin = boxMax = null;
        corner = null;
    }

    /** Ctrl + wheel with a book in the main hand: the active entry (wheel down: the next one). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBookScroll(InputEvent.MouseScrollingEvent event) {
        ItemStack held = holding();
        if (held == null || !BlueprintStacks.isBook(held) || !Screen.hasControlDown() || event.getScrollDeltaY() == 0) return;
        if (!BlueprintStacks.book(held).isEmpty()) {
            PacketDistributor.sendToServer(new BlueprintPayloads.BookScroll(event.getScrollDeltaY() > 0 ? -1 : 1));
            planKey = null;
        }
        event.setCanceled(true);
    }

    /** Shift + wheel (first person, a box selected): moves the face looked at out (wheel up) or in. */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (BuildCamera.isActive() || boxMin == null || boxMax == null || !ADJUST.isDown() || holding() == null) return;
        Direction face = lookedFace();
        if (face == null) return;
        int step = event.getScrollDeltaY() > 0 ? 1 : -1;
        int[] min = {boxMin.getX(), boxMin.getY(), boxMin.getZ()};
        int[] max = {boxMax.getX(), boxMax.getY(), boxMax.getZ()};
        int axis = face.getAxis().ordinal();
        int limit = axis == 1 ? Blueprints.MAX_HEIGHT : Blueprints.MAX_SPAN;
        if (face.getAxisDirection() == Direction.AxisDirection.POSITIVE) max[axis] = Math.max(min[axis], max[axis] + step);
        else min[axis] = Math.min(max[axis], min[axis] - step);
        if (max[axis] - min[axis] < limit) {
            boxMin = new BlockPos(min[0], min[1], min[2]);
            boxMax = new BlockPos(max[0], max[1], max[2]);
        }
        event.setCanceled(true);
    }

    /** The face of the selection box the player looks at (from inside: the one he looks out through). */
    @Nullable
    private static Direction lookedFace() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || boxMin == null || boxMax == null) return null;
        AABB box = new AABB(Vec3.atLowerCornerOf(boxMin), Vec3.atLowerCornerOf(boxMax).add(1, 1, 1));
        Vec3 eye = mc.player.getEyePosition();
        Vec3 far = eye.add(mc.player.getLookAngle().scale(128));
        if (box.contains(eye)) {
            BlockHitResult out = AABB.clip(List.of(box), far, eye, BlockPos.ZERO);
            return out == null ? null : out.getDirection();
        }
        BlockHitResult in = AABB.clip(List.of(box), eye, far, BlockPos.ZERO);
        return in == null ? null : in.getDirection();
    }

    /**
     * The cell under the cursor: top-down, the block under it however far (to ghost reach; remote:
     * in the view's area); first person, the block looked at. {@code onTop}: the cell above a solid
     * block (where a paste's bottom layer goes); else the block itself.
     */
    @Nullable
    static BlockPos cursorCell(Minecraft mc, boolean onTop) {
        if (mc.level == null || mc.player == null) return null;
        HitResult h = BuildCamera.isActive() && !BuildCamera.isRemote() ? BuildCamera.cursorHit() : mc.hitResult;
        if (!(h instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos p = hit.getBlockPos();
        if (!reachable(p)) return null;
        if (!onTop) return p.immutable();
        return mc.level.getBlockState(p).canBeReplaced() ? p.immutable() : p.above();
    }

    /** Client view of the server's ghost reach. */
    static boolean reachable(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (BuildCamera.isRemote()) return RemoteViewClient.inArea(pos);
        return GhostReach.inReach(pos.getX() + 0.5 - mc.player.getX(), pos.getZ() + 0.5 - mc.player.getZ(), 0);
    }

    // --- preview -------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        ItemStack held = holding();
        if (held == null || mc.level == null || mc.player == null || mc.options.hideGui) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        BlueprintData data = BlueprintStacks.activeData(held);
        if (data != null) drawPaste(mc, data, pose, buffers);
        else if (selecting(held)) drawSelection(mc, pose, buffers);
        pose.popPose();
    }

    private static void drawSelection(Minecraft mc, PoseStack pose, MultiBufferSource.BufferSource buffers) {
        BlockPos a = null, b = null;
        if (dragStart != null) {
            a = dragStart;
            b = Objects.requireNonNullElse(cursorCell(mc, false), dragStart);
        } else if (boxMin != null) {
            a = boxMin;
            b = boxMax;
        } else if (corner != null) {
            a = corner;
            b = mc.hitResult instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK ? h.getBlockPos() : corner;
        }
        if (a == null || b == null) return;
        AABB box = new AABB(Vec3.atLowerCornerOf(a), Vec3.atLowerCornerOf(b)).minmax(new AABB(a)).minmax(new AABB(b)).inflate(0.01);
        MultiblockPlacementPreview.drawBox(pose, buffers, box, true, true);
        Direction face = boxMin != null && dragStart == null ? lookedFace() : null;
        if (face != null) {
            AABB f = faceSlab(box, face);
            LevelRenderer.addChainedFilledBoxVertices(pose, buffers.getBuffer(RenderType.debugFilledBox()),
                    f.minX, f.minY, f.minZ, f.maxX, f.maxY, f.maxZ, YELLOW[0], YELLOW[1], YELLOW[2], ADJUST.isDown() ? 0.45f : 0.2f);
            buffers.endBatch(RenderType.debugFilledBox());
        }
    }

    /** A thin slab on {@code face} of {@code box}. */
    private static AABB faceSlab(AABB box, Direction face) {
        double t = 0.02;
        return switch (face) {
            case DOWN -> new AABB(box.minX, box.minY - t, box.minZ, box.maxX, box.minY + t, box.maxZ);
            case UP -> new AABB(box.minX, box.maxY - t, box.minZ, box.maxX, box.maxY + t, box.maxZ);
            case NORTH -> new AABB(box.minX, box.minY, box.minZ - t, box.maxX, box.maxY, box.minZ + t);
            case SOUTH -> new AABB(box.minX, box.minY, box.maxZ - t, box.maxX, box.maxY, box.maxZ + t);
            case WEST -> new AABB(box.minX - t, box.minY, box.minZ, box.minX + t, box.maxY, box.maxZ);
            case EAST -> new AABB(box.maxX - t, box.minY, box.minZ, box.maxX + t, box.maxY, box.maxZ);
        };
    }

    private static void drawPaste(Minecraft mc, BlueprintData data, PoseStack pose, MultiBufferSource.BufferSource buffers) {
        BlockPos anchor = cursorCell(mc, true);
        if (anchor == null) return;
        boolean force = mc.options.keyShift.isDown(); // the remote view zeroes the body's sneak input
        List<PartPlan> list = plansFor(mc, data, anchor, force);
        AABB bounds = null;
        for (PartPlan p : list) {
            AABB cellsBox = new AABB(p.cells().getFirst());
            for (BlockPos c : p.cells()) cellsBox = cellsBox.minmax(new AABB(c));
            bounds = bounds == null ? cellsBox : bounds.minmax(cellsBox);
            if (p.status() == BlueprintPaste.Status.SAME) continue;
            BlockState state = p.state() != null ? p.state() : shown(p);
            float[] c = p.placeable() ? BLUE : RED;
            pose.pushPose();
            pose.translate(p.origin().getX(), p.origin().getY(), p.origin().getZ());
            GhostRenderer.draw(state, p.facing(), pose, buffers, c[0], c[1], c[2], c[3]);
            pose.popPose();
            for (BlockPos o : p.obstacles()) {
                LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), new AABB(o).inflate(0.02),
                        YELLOW[0], YELLOW[1], YELLOW[2], 0.9f);
            }
        }
        buffers.endBatch();
        if (bounds != null) {
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), bounds.inflate(0.03),
                    GhostRenderer.R, GhostRenderer.G, GhostRenderer.B, 0.6f);
            buffers.endBatch(RenderType.lines());
        }
    }

    /** The state a red part is drawn with: its block turned to its facing. */
    private static BlockState shown(PartPlan p) {
        BlockState s = p.block().defaultBlockState();
        return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(BlockStateProperties.HORIZONTAL_FACING, p.facing()) : s;
    }

    private static List<PartPlan> plansFor(Minecraft mc, BlueprintData data, BlockPos anchor, boolean force) {
        Level level = mc.level;
        List<Object> key = List.of(System.identityHashCode(data), anchor, transform.bits(), force);
        long now = level.getGameTime();
        if (plans != null && key.equals(planKey) && now - planTick < PLAN_TTL && now >= planTick) return plans;
        boolean mayBuild = mc.player.getAbilities().mayBuild;
        plans = BlueprintPaste.plan(level, mc.player, data, anchor, transform, force, new BlueprintPaste.Context() {
            @Override
            public boolean reachable(BlockPos pos) {
                return mayBuild && BlueprintClient.reachable(pos);
            }

            @Override
            public boolean recipeUnlocked(String recipeId) {
                return RecipeLocks.isUnlockedOnClient(recipeId);
            }
        });
        planKey = key;
        planTick = now;
        return plans;
    }

    // --- HUD -----------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack held = holding();
        if (held == null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        if (BlueprintStacks.isBook(held)) drawBookLine(mc, g, held);
        Component help;
        if (BlueprintStacks.isBook(held) && BlueprintStacks.activeData(held) == null) {
            help = Component.translatable("minetorio.blueprint_book.help.empty");
        } else if (BlueprintStacks.activeData(held) != null) {
            help = Component.translatable("minetorio.blueprint.help.paste", ROTATE.getTranslatedKeyMessage(),
                    FLIP_X.getTranslatedKeyMessage(), FLIP_Z.getTranslatedKeyMessage());
        } else if (BuildCamera.isActive()) {
            help = Component.translatable("minetorio.blueprint.help.drag");
        } else if (boxMin != null) {
            help = Component.translatable("minetorio.blueprint.help.box", ADJUST.getTranslatedKeyMessage(), CONFIRM.getTranslatedKeyMessage());
        } else {
            help = Component.translatable(corner == null ? "minetorio.blueprint.help.corner1" : "minetorio.blueprint.help.corner2");
        }
        int y = g.guiHeight() - 72;
        g.drawCenteredString(mc.font, help, g.guiWidth() / 2, y, 0xFFB0D8FF);
    }

    /** The book in hand: its name, the active entry's number and name, and the wheel hint. */
    private static void drawBookLine(Minecraft mc, GuiGraphics g, ItemStack held) {
        BlueprintBookData d = BlueprintStacks.book(held);
        Component line;
        if (d.isEmpty()) {
            line = Component.translatable("minetorio.blueprint_book.hud.empty", held.getHoverName());
        } else {
            BlueprintRecord a = d.activeEntry();
            line = Component.translatable("minetorio.blueprint_book.hud", held.getHoverName(), d.active() + 1, d.size(),
                    a == null ? Component.empty() : BlueprintStacks.name(a));
        }
        g.drawCenteredString(mc.font, line, g.guiWidth() / 2, g.guiHeight() - 84, 0xFFFFE080);
    }

    // --- opening blueprints and books from the inventory -------------------------------------------

    /**
     * Right-click (with nothing on the cursor) on a blueprint or book in the player's own inventory
     * opens it: a book its window, a blueprint its name/icons/string editor.
     */
    @SubscribeEvent
    public static void onInventoryClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        if (!(event.getScreen() instanceof InventoryScreen) && !(event.getScreen() instanceof CreativeModeInventoryScreen)) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !screen.getMenu().getCarried().isEmpty()) return;
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !(slot.container instanceof Inventory) || slot.getContainerSlot() >= Inventory.INVENTORY_SIZE) return;
        ItemStack s = slot.getItem();
        if (!BlueprintStacks.isBlueprintLike(s)) return;
        int index = slot.getContainerSlot();
        mc.setScreen(BlueprintStacks.isBook(s) ? new BlueprintBookScreen(screen, index) : BlueprintMetaScreen.forSlot(screen, index, List.of()));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void registerDecorations(RegisterItemDecorationsEvent event) {
        event.register(dev.minetorio.content.registry.ModItems.BLUEPRINT.get(), (g, font, stack, x, y) -> BlueprintGui.drawIcons(g, stack, x, y));
        event.register(dev.minetorio.content.registry.ModItems.BLUEPRINT_BOOK.get(), (g, font, stack, x, y) -> BlueprintGui.drawIcons(g, stack, x, y));
    }
}
