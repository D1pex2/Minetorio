package dev.minetorio.content.drag;

import dev.minetorio.content.belt.BeltBlock;
import dev.minetorio.content.power.ElectricPoleBlock;
import dev.minetorio.content.power.PowerGridManager;
import dev.minetorio.core.drag.DragCell;
import dev.minetorio.core.drag.DragDir;
import dev.minetorio.core.drag.DragLine;
import dev.minetorio.core.drag.PoleSpacer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Server side of Factorio-style drag placement of belts and small electric poles.
 *
 * <p>The first cell is placed by the player's normal right-click. The client then sends
 * {@link DragPayloads.Start} (that cell and the line direction) and {@link DragPayloads.Steps}:
 * the line's next cells as one direction each ({@link DragLine}: straight on, or a turn that makes
 * the previous cell a curve). The server rebuilds the line, takes each new cell's level from the
 * ground ({@link DragTerrain}) and places every cell whose facing is final (all but the head; the
 * head too when the drag ends), one item each from the main hand, refilled from the inventory.
 * Placement goes through {@code ServerPlayerGameMode.useItemOn} as a right-click on the cell with
 * the player turned to the cell's facing, so every placement rule and hook runs (block shapes,
 * ramps, block entities, the power grid, protection events). Belts may also go into the cell the
 * player stands in ({@code BeltItem}).
 *
 * <p>Cells holding something are skipped; a belt of another tier facing the line's way is
 * fast-replaced by the belt's own rule. A pole drag places a pole only where the next cell would
 * be out of wire reach of the last pole ({@link PoleSpacer}). The server checks reach (with
 * {@value #REACH_TOLERANCE} blocks of slack), the line geometry (contiguous, never reversing,
 * at most {@value #MAX_CELLS} cells), the step index, and a step rate budget; anything invalid
 * ends the drag. Running out of items ends it too.
 */
public final class DragPlacement {
    /** Most steps one packet may carry. */
    public static final int MAX_STEPS_PER_PACKET = 64;
    /** Longest drag line, in cells. */
    public static final int MAX_CELLS = 4096;
    /** Extra blocks of reach the server allows over the player's block interaction range. */
    public static final double REACH_TOLERANCE = 2.0;
    /** Step budget: a burst of this many steps, refilled by {@value #REFILL_PER_TICK} per tick. */
    static final int BURST = 96;
    static final int REFILL_PER_TICK = 2;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** The player whose drag cell is being placed right now (server thread), see {@link #isDragPlacing}. */
    @Nullable
    private static Player placing;

    private DragPlacement() {}

    public enum Kind { BELT, POLE }

    /** What a drag with {@code stack} lays, or null if it cannot be dragged. */
    @Nullable
    public static Kind kindOf(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return null;
        if (item.getBlock() instanceof BeltBlock) return Kind.BELT;
        if (item.getBlock() instanceof ElectricPoleBlock) return Kind.POLE;
        return null;
    }

    /** Whether {@code player}'s drag is placing a cell at this moment (lets a belt go under the player). */
    public static boolean isDragPlacing(Player player) {
        return placing == player;
    }

    /** Whether {@code player} has a drag in progress (for tests). */
    public static boolean isDragging(Player player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> SESSIONS.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> SESSIONS.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> SESSIONS.clear());
    }

    private static final class Session {
        final Kind kind;
        final Item item;
        final Block block;
        final ResourceKey<Level> dimension;
        final DragLine line;
        final int[] ys = new int[MAX_CELLS];
        final PoleSpacer spacer;
        /** Next cell to place; cell 0 is the click's own. */
        int next = 1;
        double tokens = BURST;
        long lastTick;

        Session(Kind kind, BlockItem item, ResourceKey<Level> dimension, BlockPos start, DragDir dir, long tick) {
            this.kind = kind;
            this.item = item;
            this.block = item.getBlock();
            this.dimension = dimension;
            this.line = new DragLine(start.getX(), start.getZ(), dir, MAX_CELLS);
            this.ys[0] = start.getY();
            this.spacer = new PoleSpacer(PowerGridManager.WIRE_REACH, start.getX(), start.getY(), start.getZ());
            this.lastTick = tick;
        }

        BlockPos pos(int i) {
            DragCell c = line.cell(i);
            return new BlockPos(c.x(), ys[i], c.z());
        }
    }

    /**
     * Starts a drag from the cell at {@code start} (placed by the click before) in {@code dir},
     * with the item in the main hand. Replaces any drag the player had going.
     */
    public static void start(ServerPlayer player, BlockPos start, DragDir dir) {
        SESSIONS.remove(player.getUUID());
        ItemStack stack = player.getMainHandItem();
        Kind kind = kindOf(stack);
        if (kind == null || dir == null || !player.level().isLoaded(start)
                || !player.canInteractWithBlock(start, REACH_TOLERANCE)) {
            return;
        }
        SESSIONS.put(player.getUUID(), new Session(kind, (BlockItem) stack.getItem(), player.level().dimension(), start, dir,
                player.server.getTickCount()));
    }

    /**
     * The client's next {@code steps} (direction ids), the first of which is step number
     * {@code from} of the line; {@code finish} ends the drag after placing the rest.
     */
    public static void steps(ServerPlayer player, int from, byte[] steps, boolean finish) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        ServerLevel level = player.serverLevel();
        if (s.dimension != level.dimension() || from != s.line.steps() || steps.length > MAX_STEPS_PER_PACKET
                || !budget(s, player, steps.length)) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        boolean ok = true;
        for (byte b : steps) {
            DragDir d = DragDir.byId(b);
            int prevY = s.ys[s.line.size() - 1];
            if (!s.line.step(d)) {
                ok = false;
                break;
            }
            DragCell c = s.line.head();
            s.ys[s.line.size() - 1] = DragTerrain.cellY(level, c.x(), c.z(), prevY);
        }
        boolean going = ok && place(player, level, s, finish);
        if (!going || finish) SESSIONS.remove(player.getUUID());
    }

    private static boolean budget(Session s, ServerPlayer player, int count) {
        long now = player.server.getTickCount();
        s.tokens = Math.min(BURST, s.tokens + Math.max(0, now - s.lastTick) * (double) REFILL_PER_TICK);
        s.lastTick = now;
        if (count > s.tokens) return false;
        s.tokens -= count;
        return true;
    }

    /** Places the cells whose facing is final; false when the drag must stop (out of items, item switched). */
    private static boolean place(ServerPlayer player, ServerLevel level, Session s, boolean finish) {
        if (s.next == 1 && s.line.size() > 1 && !settleStart(player, level, s)) return false;
        int end = finish ? s.line.size() : s.line.size() - 1;
        for (; s.next < end; s.next++) {
            int i = s.next;
            if (s.kind == Kind.POLE) {
                if (i + 1 >= s.line.size()) continue; // the head gets no pole when the drag ends (Factorio)
                BlockPos n = s.pos(i + 1);
                if (!s.spacer.poleBefore(n.getX(), n.getY(), n.getZ())) continue;
            }
            BlockPos pos = s.pos(i);
            Result r = placeCell(player, level, s, pos, DragTerrain.toMc(s.line.cell(i).facing()));
            if (r == Result.STOP) return false;
            if (r == Result.PLACED && s.kind == Kind.POLE) s.spacer.placed(pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }

    /**
     * Once the start cell's facing is final: the click normally placed it facing the line's first
     * direction, and a turn right away re-points that belt; if the click placed nothing there (it
     * used the block it hit instead) and the cell is still free, the drag places it now. False
     * when out of items.
     */
    private static boolean settleStart(ServerPlayer player, ServerLevel level, Session s) {
        BlockPos pos = s.pos(0);
        Direction facing = DragTerrain.toMc(s.line.cell(0).facing());
        BlockState state = level.getBlockState(pos);
        if (DragTerrain.isFree(level, pos)) return placeCell(player, level, s, pos, facing) != Result.STOP;
        if (s.kind != Kind.BELT || !state.is(s.block) || state.getValue(BeltBlock.FACING) == facing || !mayBuild(player, level, pos)) {
            return true;
        }
        level.setBlock(pos, BeltBlock.withShape(level, pos, state.setValue(BeltBlock.FACING, facing)), Block.UPDATE_ALL);
        return true;
    }

    private enum Result { PLACED, SKIPPED, STOP }

    private static boolean mayBuild(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && player.mayBuild() && level.mayInteract(player, pos)
                && player.canInteractWithBlock(pos, REACH_TOLERANCE);
    }

    private static Result placeCell(ServerPlayer player, ServerLevel level, Session s, BlockPos pos, Direction facing) {
        if (!mayBuild(player, level, pos)) return Result.SKIPPED;
        BlockState state = level.getBlockState(pos);
        boolean fastReplace = s.kind == Kind.BELT && state.getBlock() instanceof BeltBlock other && other != s.block
                && state.getValue(BeltBlock.FACING) == facing && !player.isSecondaryUseActive();
        if (!fastReplace && !DragTerrain.isFree(level, pos)) return Result.SKIPPED;
        ItemStack stack = handStack(player, s.item);
        if (stack.isEmpty()) return Result.STOP;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        float yRot = player.getYRot();
        BlockState before = state;
        player.setYRot(facing.toYRot());
        placing = player;
        try {
            player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
        } finally {
            placing = null;
            player.setYRot(yRot);
        }
        return level.getBlockState(pos) != before ? Result.PLACED : Result.SKIPPED;
    }

    /**
     * The main-hand stack to place from: refilled from the rest of the inventory when it ran out;
     * empty when there are no more of {@code item} or the player holds something else now.
     */
    private static ItemStack handStack(ServerPlayer player, Item item) {
        ItemStack hand = player.getMainHandItem();
        if (!hand.isEmpty()) return hand.is(item) ? hand : ItemStack.EMPTY;
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.items.size(); slot++) {
            ItemStack other = inv.items.get(slot);
            if (slot != inv.selected && other.is(item)) {
                inv.setItem(inv.selected, other);
                inv.setItem(slot, ItemStack.EMPTY);
                return other;
            }
        }
        return ItemStack.EMPTY;
    }
}
