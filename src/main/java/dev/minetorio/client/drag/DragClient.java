package dev.minetorio.client.drag;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.minetorio.Minetorio;
import dev.minetorio.client.ModKeys;
import dev.minetorio.client.camera.BuildCamera;
import dev.minetorio.client.machine.MultiblockPlacementPreview;
import dev.minetorio.content.belt.BeltBlock;
import dev.minetorio.content.belt.BeltGeometry;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.content.drag.DragPayloads;
import dev.minetorio.content.drag.DragPlacement;
import dev.minetorio.content.drag.DragTerrain;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.robot.GhostItem;
import dev.minetorio.content.robot.GhostPayloads;
import dev.minetorio.content.robot.GhostReservations;
import dev.minetorio.content.robot.HandGhosts;
import dev.minetorio.content.map.RemotePayloads;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.core.robot.GhostReach;
import dev.minetorio.core.drag.DragCell;
import dev.minetorio.core.drag.DragDir;
import dev.minetorio.core.drag.DragLine;
import dev.minetorio.core.drag.PoleSpacer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of drag placement ({@link DragPlacement}): with a transport belt (any tier) or a
 * small electric pole in the main hand, the right-click places the first cell as usual and, while
 * the button is held, a straight line runs from it in the player's facing to the column the player
 * looks at (or stands on), drawn as a translucent ghost. Looking/walking at least
 * {@value #TURN_THRESHOLD} cells off the line to a side turns it (a curve at the corner). Cells
 * about to leave reach are sent to the server and placed; releasing places the rest. A pole drag
 * shows (and places) poles only at wire-reach spacing. Vanilla's repeated placement while the
 * button is held is suppressed during a drag; a click without moving places just the one cell.
 *
 * <p>With the ghost key ({@link ModKeys#GHOST}) held, a right-click with any Minetorio block item
 * places a ghost instead ({@link HandGhosts}, sent as {@link GhostPayloads.Place}; nothing is
 * placed or used here), and a belt or pole click starts a ghost drag: the same line, drawn blue,
 * laid as ghosts without items. A {@link GhostItem} in the main hand does all this without the key.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class DragClient {
    /** Cells to a side of the line the target must be before the line turns (1 would turn on every wobble). */
    static final int TURN_THRESHOLD = 2;
    /** Most cells the line grows per tick. */
    private static final int MAX_GROWTH_PER_TICK = 8;
    /** Cells this much closer than the reach limit are already sent, so they arrive while still in reach. */
    private static final double FLUSH_MARGIN = 1.0;
    private static final double INFLATE = 0.005;

    @Nullable
    private static Drag drag;

    private DragClient() {}

    private static final class Drag {
        final DragPlacement.Kind kind;
        /** Lays ghosts: no item budget, blue preview. */
        final boolean ghost;
        final Item item;
        final BlockPos start;
        final DragDir startDir;
        final Level level;
        final DragLine line;
        final List<Integer> ys = new ArrayList<>();
        final List<DragDir> steps = new ArrayList<>();
        /** Per cell: whether a belt will take an item there (counted against the budget). */
        final List<Boolean> pieces = new ArrayList<>();
        /** Steps sent to the server; it has placed cells 1 .. sent-1. */
        int sent;
        boolean started;
        /** Items available for new cells (counted after the click took its one); -1 until counted. */
        int budget = -1;
        int used;

        Drag(DragPlacement.Kind kind, boolean ghost, Item item, BlockPos start, DragDir dir, Level level) {
            this.kind = kind;
            this.ghost = ghost;
            this.item = item;
            this.start = start;
            this.startDir = dir;
            this.level = level;
            this.line = new DragLine(start.getX(), start.getZ(), dir, DragPlacement.MAX_CELLS);
            ys.add(start.getY());
            pieces.add(false);
        }

        /** Cuts the unsent ghost back to {@code size} cells. */
        void truncate(int size) {
            line.truncate(size);
            while (ys.size() > size) {
                ys.removeLast();
                steps.removeLast();
                if (pieces.removeLast()) used--;
            }
        }

        BlockPos pos(int i) {
            DragCell c = line.cell(i);
            return new BlockPos(c.x(), ys.get(i), c.z());
        }

        List<int[]> path() {
            List<int[]> out = new ArrayList<>(line.size());
            for (int i = 0; i < line.size(); i++) out.add(new int[] {line.cell(i).x(), ys.get(i), line.cell(i).z()});
            return out;
        }
    }

    /** Whether a belt of the dragged item would go into {@code pos} facing {@code facing} (free, or fast-replace). */
    private static boolean placeable(Level level, Drag d, BlockPos pos, Direction facing) {
        if (d.ghost) return DragTerrain.isFree(level, pos) && GhostReservations.freeFor(level, pos, pos);
        if (DragTerrain.isFree(level, pos)) return true;
        BlockState state = level.getBlockState(pos);
        return d.kind == DragPlacement.Kind.BELT && state.getBlock() instanceof BeltBlock b
                && b != ((BlockItem) d.item).getBlock() && state.getValue(BeltBlock.FACING) == facing;
    }

    // --- input -------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        if (drag != null) { // holding the button during a drag: no vanilla repeat placement
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack stack = player.getMainHandItem();
        boolean ghostItem = stack.getItem() instanceof GhostItem;
        BlockHitResult far = farHit(mc);
        if (ghostItem || ((ModKeys.ghostHeld() || far != null) && HandGhosts.canGhost(stack))) {
            ItemStack target = HandGhosts.effective(stack);
            if (target.isEmpty()) {
                event.setCanceled(true); // a ghost item without a valid target does nothing
                event.setSwingHand(false);
                return;
            }
            ghostClick(event, mc, player, target);
            return;
        }
        DragPlacement.Kind kind = DragPlacement.kindOf(stack);
        if (kind == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (usesBlock(mc.level, hit.getBlockPos()) && !player.isSecondaryUseActive()) return; // the click uses that block
        BlockPlaceContext ctx = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        if (!ctx.canPlace()) return;
        // the vanilla click goes on and places the first cell; the drag continues from it
        drag = new Drag(kind, false, stack.getItem(), ctx.getClickedPos(), DragTerrain.fromMc(player.getDirection()), mc.level);
    }

    /**
     * A right-click with the ghost key held: no real placement; the server places the ghost the
     * click plans ({@link HandGhosts#plan}). A belt or pole goes on as a ghost drag from that cell.
     */
    private static void ghostClick(InputEvent.InteractionKeyMappingTriggered event, Minecraft mc, LocalPlayer player, ItemStack stack) {
        event.setCanceled(true);
        BlockHitResult hit = ghostHit(mc);
        if (hit == null) {
            event.setSwingHand(false);
            return;
        }
        PacketDistributor.sendToServer(new GhostPayloads.Place(hit));
        DragPlacement.Kind kind = DragPlacement.kindOf(stack);
        HandGhosts.Plan plan = HandGhosts.plan(mc.level, player, stack, hit);
        if (kind != null && plan != null) {
            drag = new Drag(kind, true, stack.getItem(), plan.pos(), DragTerrain.fromMc(player.getDirection()), mc.level);
        }
    }

    /**
     * Top-down build camera (not remote): the block under the cursor when it is beyond the normal
     * reach but within ghost reach (Factorio: placing out of reach makes ghosts); else null.
     */
    @Nullable
    public static BlockHitResult farHit(Minecraft mc) {
        if (!BuildCamera.isActive() || BuildCamera.isRemote() || BuildCamera.cursorInReach() || mc.player == null) return null;
        if (!(BuildCamera.cursorHit() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos p = hit.getBlockPos();
        return GhostReach.inReach(p.getX() + 0.5 - mc.player.getX(), p.getZ() + 0.5 - mc.player.getZ(), 0) ? hit : null;
    }

    /** The block a ghost click acts on: the reach-filtered target, else (top-down) the far one ({@link #farHit}). */
    @Nullable
    public static BlockHitResult ghostHit(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) return hit;
        return farHit(mc);
    }

    /**
     * Left-click on a ghost beyond reach in the top-down camera removes it through the ghost-reach
     * path (vanilla only breaks blocks in reach); the remote view does the same in its own handler.
     */
    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        BlockHitResult far = farHit(mc);
        if (far == null || mc.level == null || !mc.level.getBlockState(far.getBlockPos()).is(ModBlocks.GHOST.get())) return;
        event.setCanceled(true);
        PacketDistributor.sendToServer(new RemotePayloads.RemoveGhost(far.getBlockPos()));
    }

    /** Whether a drag is in progress (the single-click ghost preview hides meanwhile). */
    public static boolean isDragging() {
        return drag != null;
    }

    /**
     * Whether right-clicking the block at {@code pos} does something else than placing against it:
     * a belt, underground or splitter (fast-replace, or placing on top of it), a machine, a menu.
     */
    private static boolean usesBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return BeltGeometry.isBeltLike(level, pos) || state.getBlock() instanceof MultiblockControllerBlock
                || state.getBlock() instanceof MultiblockPartBlock || state.getMenuProvider(level, pos) != null;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Drag d = drag;
        if (d == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level != d.level || mc.screen != null || !mc.options.keyUse.isDown()
                || !HandGhosts.effective(player.getMainHandItem()).is(d.item)) {
            finish(d);
            return;
        }
        if (d.budget < 0) d.budget = player.getAbilities().instabuild || d.ghost ? Integer.MAX_VALUE : count(player, d.item);
        grow(mc, player, d);
        flush(player, d);
    }

    private static int count(LocalPlayer player, Item item) {
        int n = 0;
        var main = dev.minetorio.content.character.CharacterInventory.main(player.getInventory());
        for (int i=0;i<main.getContainerSize();i++) { ItemStack s=main.getItem(i); if (s.is(item)) n += s.getCount(); }
        return n;
    }

    /** The column the line heads for: the cell a click would place into, or the player's own. */
    private static int[] target(Minecraft mc, LocalPlayer player, Drag d) {
        HitResult aim = d.ghost ? ghostHit(mc) : mc.hitResult;
        if (aim instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos p = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).getClickedPos();
            return new int[] {p.getX(), p.getZ()};
        }
        if (BuildCamera.isRemote()) { // the cursor is off the remote area: the line waits (the body is far away)
            DragCell head = d.line.head();
            return new int[] {head.x(), head.z()};
        }
        BlockPos feet = player.blockPosition();
        return new int[] {feet.getX(), feet.getZ()};
    }

    private static void grow(Minecraft mc, LocalPlayer player, Drag d) {
        int[] t = target(mc, player, d);
        int drop = d.line.retraction(t[0], t[1], TURN_THRESHOLD, d.sent + 1);
        if (drop > 0) d.truncate(d.line.size() - drop); // turning behind the ghost's head: move the corner back
        PoleSpacer spacer = null;
        int poles = 0;
        if (d.kind == DragPlacement.Kind.POLE) {
            List<int[]> path = d.path();
            List<Integer> picks = PoleSpacer.picks(path, DragPlacement.dragReach(d.item));
            poles = picks.size();
            int[] last = picks.isEmpty() ? path.getFirst() : path.get(picks.getLast());
            spacer = new PoleSpacer(DragPlacement.dragReach(d.item), last[0], last[1], last[2]);
        }
        for (DragDir step : d.line.plan(t[0], t[1], TURN_THRESHOLD, MAX_GROWTH_PER_TICK)) {
            DragCell head = d.line.head();
            int prevY = d.ys.getLast();
            int nx = head.x() + step.dx(), nz = head.z() + step.dz();
            int ny = DragTerrain.cellY(d.level, nx, nz, prevY);
            if (spacer != null) {
                // the current head gets a pole once this cell is out of wire reach of the last one
                if (d.line.size() > 1 && spacer.poleBefore(nx, ny, nz)) {
                    if (poles >= d.budget) break;
                    poles++;
                    spacer.placed(head.x(), prevY, head.z());
                }
            }
            boolean piece = spacer == null && placeable(d.level, d, new BlockPos(nx, ny, nz), DragTerrain.toMc(step));
            if (piece && d.used >= d.budget) break;
            if (!d.line.step(step)) break;
            if (piece) d.used++;
            d.ys.add(ny);
            d.steps.add(step);
            d.pieces.add(piece);
        }
    }

    /** Sends the steps that let the server place every cell (but the head) about to leave reach. */
    private static void flush(LocalPlayer player, Drag d) {
        Vec3 eye = player.getEyePosition();
        double limit = Math.max(1, player.blockInteractionRange() - FLUSH_MARGIN);
        int last = -1;
        for (int j = Math.max(1, d.sent); j <= d.line.size() - 2; j++) {
            if (new AABB(d.pos(j)).distanceToSqr(eye) > limit * limit) last = j;
        }
        if (last >= 0) send(d, last + 1, false);
    }

    /** Sends steps up to (not including) step number {@code upTo}. */
    private static void send(Drag d, int upTo, boolean finish) {
        if (!d.started) {
            if (upTo == 0) return; // a plain click: nothing to drag
            PacketDistributor.sendToServer(new DragPayloads.Start(d.start, (byte) d.startDir.id(), d.ghost));
            d.started = true;
        }
        do {
            int n = Math.min(DragPlacement.MAX_STEPS_PER_PACKET, upTo - d.sent);
            byte[] bytes = new byte[n];
            for (int i = 0; i < n; i++) bytes[i] = (byte) d.steps.get(d.sent + i).id();
            boolean lastChunk = d.sent + n >= upTo;
            PacketDistributor.sendToServer(new DragPayloads.Steps(d.sent, bytes, finish && lastChunk));
            d.sent += n;
        } while (d.sent < upTo);
    }

    private static void finish(Drag d) {
        drag = null;
        send(d, d.steps.size(), true);
    }

    // --- ghost -------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        Drag d = drag;
        if (d == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != d.level || d.line.size() < 2) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int from = Math.max(1, d.sent);
        if (d.kind == DragPlacement.Kind.BELT) {
            for (int i = from; i < d.line.size(); i++) {
                BlockPos p = d.pos(i);
                Direction facing = DragTerrain.toMc(d.line.cell(i).facing());
                boolean ok = placeable(d.level, d, p, facing);
                AABB box = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 2 / 16.0, p.getZ() + 1).inflate(INFLATE);
                MultiblockPlacementPreview.drawBox(pose, buffers, box, ok, d.ghost);
                if (ok) arrow(pose, buffers, p, facing, d.ghost);
            }
        } else {
            Set<Integer> picks = new HashSet<>(PoleSpacer.picks(d.path(), DragPlacement.dragReach(d.item)));
            for (int i = from; i < d.line.size(); i++) {
                BlockPos p = d.pos(i);
                if (picks.contains(i)) {
                    Direction facing = DragTerrain.toMc(d.line.cell(i).facing());
                    AABB box;
                    if (((BlockItem) d.item).getBlock() instanceof MultiblockControllerBlock mb) {
                        box = MachineRender.footprintBox(mb.placementOrigin(p, facing), facing, mb.shape()).inflate(INFLATE);
                    } else {
                        box = new AABB(p.getX() + 6 / 16.0, p.getY(), p.getZ() + 6 / 16.0, p.getX() + 10 / 16.0, p.getY() + 3,
                                p.getZ() + 10 / 16.0).inflate(INFLATE);
                    }
                    MultiblockPlacementPreview.drawBox(pose, buffers, box, poleFits(d, p, facing), d.ghost);
                } else if (i == d.line.size() - 1) {
                    AABB box = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1 / 16.0, p.getZ() + 1).inflate(INFLATE);
                    MultiblockPlacementPreview.drawBox(pose, buffers, box, true, d.ghost);
                }
            }
        }
        pose.popPose();
    }

    /** Whether a pole of the dragged kind fits with its anchor at {@code p}, turned to {@code facing}. */
    private static boolean poleFits(Drag d, BlockPos p, Direction facing) {
        List<BlockPos> cells = new ArrayList<>();
        if (((BlockItem) d.item).getBlock() instanceof MultiblockControllerBlock mb) {
            cells.addAll(mb.shape().footprint(mb.placementOrigin(p, facing), facing));
        } else {
            for (int k = 0; k < 3; k++) cells.add(p.above(k));
        }
        for (BlockPos c : cells) {
            if (!DragTerrain.isFree(d.level, c)) return false;
            if (d.ghost && !GhostReservations.freeFor(d.level, c, p)) return false;
        }
        return true;
    }

    /** A flat arrow on top of the ghost plate pointing along {@code facing}. */
    private static void arrow(PoseStack pose, MultiBufferSource.BufferSource buffers, BlockPos p, Direction facing, boolean ghost) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose last = pose.last();
        float y = p.getY() + 2.5f / 16;
        float cx = p.getX() + 0.5f, cz = p.getZ() + 0.5f;
        float fx = facing.getStepX(), fz = facing.getStepZ(), sx = -fz, sz = fx;
        float[] tip = {cx + fx * 0.3f, cz + fz * 0.3f};
        float[] tail = {cx - fx * 0.3f, cz - fz * 0.3f};
        float[] wingL = {cx + fx * 0.05f + sx * 0.25f, cz + fz * 0.05f + sz * 0.25f};
        float[] wingR = {cx + fx * 0.05f - sx * 0.25f, cz + fz * 0.05f - sz * 0.25f};
        float[] c = ghost ? MultiblockPlacementPreview.GHOST_RGB : MultiblockPlacementPreview.OK_RGB;
        segment(lines, last, tail, tip, y, c);
        segment(lines, last, wingL, tip, y, c);
        segment(lines, last, wingR, tip, y, c);
        buffers.endBatch(RenderType.lines());
    }

    private static void segment(VertexConsumer lines, PoseStack.Pose pose, float[] a, float[] b, float y, float[] c) {
        float nx = b[0] - a[0], nz = b[1] - a[1];
        float len = (float) Math.sqrt(nx * nx + nz * nz);
        nx /= len;
        nz /= len;
        lines.addVertex(pose, a[0], y, a[1]).setColor(c[0], c[1], c[2], 0.9f).setNormal(pose, nx, 0, nz);
        lines.addVertex(pose, b[0], y, b[1]).setColor(c[0], c[1], c[2], 0.9f).setNormal(pose, nx, 0, nz);
    }
}
