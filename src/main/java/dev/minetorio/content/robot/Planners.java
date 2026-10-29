package dev.minetorio.content.robot;

import dev.minetorio.content.belt.UndergroundBeltBlock;
import dev.minetorio.content.map.RemoteView;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.content.registry.ModDataComponents;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.robot.DeconstructionFilter;
import dev.minetorio.core.robot.RateBudget;
import dev.minetorio.core.robot.UpgradeRules;
import dev.minetorio.core.selection.SelectionBox;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server side of the planners: applying a selected area, changing their settings, the shortcut
 * that hands out a free planner.
 *
 * <p><b>Deconstruction planner</b>: marks every block in the box the filter accepts (Minetorio
 * blocks: a machine by its controller, whichever piece is inside; trees and rocks with the toggle);
 * ghosts in the box are removed at once (Factorio cancels ghosts the same way). With Shift the
 * same blocks are unmarked instead.
 *
 * <p><b>Upgrade planner</b>: marks every Minetorio block a rule maps to another item; a paired
 * underground belt is marked once for its pair. With Shift every upgrade order in the box is
 * cancelled.
 *
 * <p>Checks: the box within {@link SelectionBox#withinLimits}, only chunks the player can see (his
 * view distance; in the remote view the view's area), spawn protection per block; a small rate
 * budget per player.
 */
public final class Planners {
    public enum Kind { DECONSTRUCTION, UPGRADE }

    private static final Map<UUID, RateBudget> BUDGETS = new HashMap<>();

    private Planners() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> BUDGETS.remove(e.getEntity().getUUID()));
    }

    /** What a selection did. */
    public record Result(int changed, int ghostsRemoved) {}

    /**
     * Applies {@code box} with the planner in {@code player}'s main hand; {@code cancel} (Shift)
     * unmarks. Null when refused (no planner, box too large, rate limit).
     */
    public static Result select(ServerPlayer player, SelectionBox box, boolean cancel) {
        ItemStack held = player.getMainHandItem();
        if (!PlannerItems.isPlanner(held) || !box.withinLimits() || player.isSpectator()) return null;
        if (!BUDGETS.computeIfAbsent(player.getUUID(), u -> new RateBudget(6, 0.25)).take(player.server.getTickCount(), 1)) return null;
        ServerLevel level = player.serverLevel();
        Result r = held.getItem() instanceof PlannerItems.DeconstructionPlanner
                ? deconstruct(level, box, cancel, PlannerItems.filter(held), p -> allowed(player, p))
                : upgrade(level, box, cancel, PlannerItems.rules(held), p -> allowed(player, p));
        String key = held.getItem() instanceof PlannerItems.DeconstructionPlanner
                ? (cancel ? "minetorio.planner.unmarked" : "minetorio.planner.marked")
                : (cancel ? "minetorio.planner.upgrade_cancelled" : "minetorio.planner.upgrade_marked");
        player.displayClientMessage(Component.translatable(key, r.changed()), true);
        return r;
    }

    /** Whether {@code player} may mark at {@code pos}: loaded, in sight (or the remote view's area), not protected. */
    static boolean allowed(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !player.mayBuild() || !level.mayInteract(player, pos)) return false;
        if (RemoteView.isRemote(player)) return RemoteView.inArea(player, pos);
        ChunkPos own = player.chunkPosition();
        int d = RemoteView.ownViewDistance(player);
        return Math.abs((pos.getX() >> 4) - own.x) <= d && Math.abs((pos.getZ() >> 4) - own.z) <= d;
    }

    /** Deconstruction planner over {@code box} (no player checks beyond {@code allowed}; used by tests directly). */
    public static Result deconstruct(ServerLevel level, SelectionBox box, boolean cancel, DeconstructionFilter filter,
                                     java.util.function.Predicate<BlockPos> allowed) {
        ConstructionMarks marks = ConstructionMarks.get(level);
        int changed = 0;
        List<BlockPos> ghosts = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos p : cells(level, box)) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof GhostBlock) {
                if (!cancel && filter.machines() && allowed.test(p)) ghosts.add(p.immutable());
                continue;
            }
            BlockPos at = p;
            if (s.getBlock() instanceof MultiblockPartBlock) {
                BlockPos c = MultiblockPartBlock.controllerPos(level, p);
                if (c == null) continue;
                at = c;
                s = level.getBlockState(c);
            }
            if (!seen.add(at.immutable())) continue;
            if (!filter.accepts(RobotWork.isMinetorioBuildable(s), RobotWork.isNature(s)) || !allowed.test(at)) continue;
            boolean did = cancel ? marks.unmark(level, at) : marks.mark(level, at);
            if (did) changed++;
        }
        for (BlockPos g : ghosts) level.removeBlock(g, false);
        return new Result(changed, ghosts.size());
    }

    /** Upgrade planner over {@code box}. */
    public static Result upgrade(ServerLevel level, SelectionBox box, boolean cancel, UpgradeRules rules,
                                 java.util.function.Predicate<BlockPos> allowed) {
        ConstructionMarks marks = ConstructionMarks.get(level);
        int changed = 0;
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos p : cells(level, box)) {
            BlockState s = level.getBlockState(p);
            BlockPos at = p;
            if (s.getBlock() instanceof MultiblockPartBlock) {
                BlockPos c = MultiblockPartBlock.controllerPos(level, p);
                if (c == null) continue;
                at = c;
                s = level.getBlockState(c);
            }
            if (!seen.add(at.immutable()) || !RobotWork.isMinetorioBuildable(s) || !allowed.test(at)) continue;
            if (cancel) {
                if (marks.unmarkUpgrade(level, at)) changed++;
                continue;
            }
            var target = rules.target(RobotWork.itemId(s));
            if (target.isEmpty()) continue;
            int count = RobotWork.piecesFor(level, at);
            if (count == 2) { // a pair is swapped together: one order on the first piece seen
                BlockPos partner = UndergroundBeltBlock.pairedPartner(level, at);
                if (partner != null && (seen.contains(partner) || marks.upgradeOrder(level, partner) != null)) continue;
                if (partner != null) seen.add(partner.immutable());
            }
            if (marks.markUpgrade(level, at, target.get(), count)) changed++;
        }
        return new Result(changed, 0);
    }

    /** Non-air cells of {@code box} in loaded chunks, chunk by chunk. */
    private static List<BlockPos> cells(ServerLevel level, SelectionBox box) {
        List<BlockPos> out = new ArrayList<>();
        int minY = Math.max(box.minY(), level.getMinBuildHeight()), maxY = Math.min(box.maxY(), level.getMaxBuildHeight() - 1);
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                int x0 = Math.max(box.minX(), cx << 4), x1 = Math.min(box.maxX(), (cx << 4) + 15);
                int z0 = Math.max(box.minZ(), cz << 4), z1 = Math.min(box.maxZ(), (cz << 4) + 15);
                BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
                for (int y = minY; y <= maxY; y++) {
                    for (int x = x0; x <= x1; x++) {
                        for (int z = z0; z <= z1; z++) {
                            if (!chunk.getBlockState(m.set(x, y, z)).isAir()) out.add(m.immutable());
                        }
                    }
                }
            }
        }
        return out;
    }

    // --- settings -------------------------------------------------------------------------------

    public static void setFilter(ServerPlayer player, int filter) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof PlannerItems.DeconstructionPlanner) {
            held.set(ModDataComponents.DECONSTRUCTION_FILTER.get(), DeconstructionFilter.byId(filter).ordinal());
        }
    }

    public static void setRules(ServerPlayer player, List<UpgradeRules.Rule> rules) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof PlannerItems.UpgradePlanner) {
            held.set(ModDataComponents.UPGRADE_RULES.get(), UpgradeRules.sanitize(rules));
        }
    }

    // --- shortcut -------------------------------------------------------------------------------

    /**
     * The shortcut: puts a planner of {@code kind} into the player's hand. One he already carries is
     * moved there; otherwise a new, free one is made. The hand's previous item goes to a free slot
     * (the planner takes a free hotbar slot and gets selected when there is no room).
     */
    public static void give(ServerPlayer player, Kind kind) {
        if (player.isSpectator()) return;
        Item item = kind == Kind.DECONSTRUCTION ? ModItems.DECONSTRUCTION_PLANNER.get() : ModItems.UPGRADE_PLANNER.get();
        Inventory inv = player.getInventory();
        if (inv.getSelected().is(item)) return;
        int have = -1;
        for (int i = 0; i < dev.minetorio.content.character.CharacterInventory.MAIN_SIZE; i++) {
            int index = dev.minetorio.content.character.CharacterInventory.inventoryIndex(i);
            if (inv.getItem(index).is(item)) {
                have = index;
                break;
            }
        }
        ItemStack planner = have >= 0 ? inv.getItem(have) : new ItemStack(item);
        if (have >= 0) inv.setItem(have, ItemStack.EMPTY);
        ItemStack hand = inv.items.get(inv.selected);
        if (hand.isEmpty()) {
            inv.items.set(inv.selected, planner);
        } else if (have >= 0) { // swap places
            inv.setItem(have, hand);
            inv.items.set(inv.selected, planner);
        } else {
            int free = -1;
            for (int i = 0; i < Inventory.getSelectionSize(); i++) {
                if (inv.items.get(i).isEmpty()) {
                    free = i;
                    break;
                }
            }
            if (free >= 0) {
                inv.items.set(free, planner);
                inv.selected = free;
                player.connection.send(new ClientboundSetCarriedItemPacket(free));
            } else if (inv.getFreeSlot() >= 0) {
                int slot = inv.getFreeSlot();
                inv.setItem(slot, hand);
                inv.items.set(inv.selected, planner);
            } else {
                player.displayClientMessage(Component.translatable("minetorio.planner.inventory_full"), true);
                return;
            }
        }
        ((dev.minetorio.content.character.CharacterShortcuts) player).emptyHand(false);
        inv.setChanged();
        player.containerMenu.broadcastChanges();
    }
}
