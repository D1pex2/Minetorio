package dev.minetorio.content.robot;

import dev.minetorio.Minetorio;
import dev.minetorio.content.map.RemoteView;
import dev.minetorio.content.research.RecipeLocks;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.recipe.RecipeCategory;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.core.robot.RateBudget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Ghosts placed by hand (Factorio's shift-click; here the ghost key, see the client's
 * {@code ModKeys.GHOST}, held while right-clicking): the click is planned exactly like a real
 * placement of the held Minetorio block item ({@link #plan}: the cell, the multiblock's centred or
 * aimed origin and facing, the block's placement state, belts' shape and slope), but a ghost goes
 * there instead and no item is used. The client sends the click ({@link GhostPayloads.Place}); the
 * server re-plans it and checks ghost reach ({@link RemoteView#canReachGhost}: far beyond the
 * normal reach, Factorio's ghosts anywhere on screen), build rights, the cells and a rate budget. Drag lines of
 * ghosts go through {@code DragPlacement} with the same {@link #planAt}. A {@link GhostItem} in the
 * main hand does the same without the key ({@link #effective}).
 *
 * <p>Ghost items come from here too: {@link #fromRecipe} (the hand-crafting screen, researched
 * recipes only) and {@link #pipette} (pick-block on a placed block whose item the player lacks).
 * A ghost item that would land on the ground as an item entity is deleted instead.
 *
 * <p>Also guards ghost reservations against real placement: a player placing a block into a cell
 * another ghost covers (a multiblock's footprint, a pole's upper blocks) is refused, unless that
 * placement replaced the ghost itself (the matching machine placed right onto it).
 */
public final class HandGhosts {
    /** Extra blocks of reach the server allows over the player's block interaction range. */
    public static final double REACH_TOLERANCE = 1.0;
    /** Ghost clicks: a burst of this many, refilled by {@value #REFILL_PER_TICK} per tick (vanilla repeats a held click every 4 ticks). */
    static final int BURST = 16;
    static final double REFILL_PER_TICK = 0.5;

    private static final Map<UUID, RateBudget> BUDGETS = new HashMap<>();

    private HandGhosts() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> BUDGETS.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> BUDGETS.clear());
        NeoForge.EVENT_BUS.addListener(HandGhosts::onPlace);
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (e.getEntity() instanceof ItemEntity item && item.getItem().getItem() instanceof GhostItem) e.setCanceled(true);
        });
    }

    /** What a hand ghost would be: its cell, the state it previews and the facing it is built with. */
    public record Plan(BlockPos pos, BlockState state, Direction facing) {
        public List<BlockPos> cells() { return Ghosts.footprint(state, pos); }
    }

    /** Whether {@code stack} can be placed as a ghost: a Minetorio block item. */
    public static boolean canGhost(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && !(bi.getBlock() instanceof GhostBlock)
                && Minetorio.MODID.equals(BuiltInRegistries.ITEM.getKey(bi).getNamespace());
    }

    /** What a click with {@code held} places ghosts of: a ghost item's target, else {@code held} itself. */
    public static ItemStack effective(ItemStack held) {
        return held.getItem() instanceof GhostItem ? GhostItem.target(held) : held;
    }

    /** The ghost a right-click {@code hit} with {@code stack} would place (either side), or null where the real block could not go. */
    @Nullable
    public static Plan plan(Level level, @Nullable Player player, ItemStack stack, BlockHitResult hit) {
        if (!canGhost(stack)) return null;
        return plan(new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, hit));
    }

    /** The ghost of {@code stack} placed into cell {@code pos} looking {@code facing} (drag lines), or null. */
    @Nullable
    public static Plan planAt(Level level, @Nullable Player player, ItemStack stack, BlockPos pos, Direction facing) {
        if (!canGhost(stack)) return null;
        return plan(Ghosts.context(level, pos, player, stack, facing));
    }

    /**
     * The real placement's rules without the item: the clicked cell must take a block, the item
     * moves it (multiblocks: centred origin, the pump's shore and facing), the block's placement
     * state must exist (a multiblock's whole footprint free) and survive there. Then the ghost's
     * own rules: no fluid in its cell (a ghost would delete it) and no overlap with another ghost.
     * Entities do not block a ghost (robots wait for them); the build itself still checks them.
     */
    @Nullable
    private static Plan plan(BlockPlaceContext ctx) {
        BlockItem item = (BlockItem) ctx.getItemInHand().getItem();
        if (!ctx.canPlace()) return null;
        BlockPlaceContext at = item.updatePlacementContext(ctx);
        if (at == null) return null;
        Level level = at.getLevel();
        BlockPos pos = at.getClickedPos();
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.getFluidState(pos).isEmpty()) return null;
        if (!level.getBlockState(pos).canBeReplaced(at)) return null;
        BlockState state = item.getBlock().getStateForPlacement(at);
        if (state == null || !state.canSurvive(level, pos)) return null;
        state = Ghosts.planned(level, pos, state);
        if (!Ghosts.fits(level, pos, state)) return null;
        return new Plan(pos.immutable(), state, at.getHorizontalDirection());
    }

    // --- server --------------------------------------------------------------------------------

    /**
     * A ghost click from {@code player}'s client: the main hand's item (or its ghost item's target),
     * the block {@code hit}. True when a ghost was placed.
     */
    public static boolean place(ServerPlayer player, BlockHitResult hit) {
        ServerLevel level = player.serverLevel();
        ItemStack stack = effective(player.getMainHandItem());
        BlockPos clicked = hit.getBlockPos();
        if (!canGhost(stack) || !player.mayBuild() || !level.isLoaded(clicked) || !hitInside(hit)
                || !RemoteView.canReachGhost(player, clicked)) {
            return false;
        }
        if (!budget(player)) return false;
        Plan p = plan(level, player, stack, hit);
        return p != null && commit(player, level, p);
    }

    /** Places the planned ghost if {@code player} may build in all of its cells (spawn protection, claims). */
    public static boolean commit(ServerPlayer player, ServerLevel level, Plan p) {
        return commit(player, level, p, null);
    }

    /** {@link #commit(ServerPlayer, ServerLevel, Plan)} with settings for the built block (a blueprint's). */
    public static boolean commit(ServerPlayer player, ServerLevel level, Plan p, @Nullable net.minecraft.nbt.CompoundTag settings) {
        if (!mayCommit(player, level, p.cells())) return false;
        return Ghosts.placePlanned(level, p.pos(), p.state(), p.facing(), settings);
    }

    /** Whether {@code player} may put ghosts in all of {@code cells}: loaded, build rights, and in the remote view's area when remote. */
    public static boolean mayCommit(ServerPlayer player, ServerLevel level, List<BlockPos> cells) {
        for (BlockPos c : cells) {
            if (!level.isLoaded(c) || !level.mayInteract(player, c)) return false;
            if (RemoteView.isRemote(player) && !RemoteView.inArea(player, c)) return false;
        }
        return true;
    }

    /** The hit point lies in (or on) the clicked block, as vanilla checks for a use packet. */
    private static boolean hitInside(BlockHitResult hit) {
        Vec3 d = hit.getLocation().subtract(Vec3.atCenterOf(hit.getBlockPos()));
        double limit = 1.0000001;
        return Math.abs(d.x) < limit && Math.abs(d.y) < limit && Math.abs(d.z) < limit;
    }

    private static boolean budget(ServerPlayer player) {
        return BUDGETS.computeIfAbsent(player.getUUID(), u -> new RateBudget(BURST, REFILL_PER_TICK))
                .take(player.server.getTickCount(), 1);
    }

    /** Forgets {@code player}'s click budget (tests share one fake player). */
    public static void resetBudget(Player player) {
        BUDGETS.remove(player.getUUID());
    }

    // --- ghost items ----------------------------------------------------------------------------

    /** Why a ghost item request was refused (for tests). */
    public enum GiveResult { OK, NOT_ALLOWED, UNKNOWN, LOCKED, NOT_PLACEABLE, HAS_ITEM, TOO_FAR, TOO_SOON, NO_ROOM }

    /**
     * A ghost item of the output of hand-crafting recipe {@code recipeId}: only a crafting recipe
     * unlocked by research whose output is a Minetorio block item.
     */
    public static GiveResult fromRecipe(ServerPlayer player, String recipeId) {
        if (player.isSpectator() || !player.isAlive()) return GiveResult.NOT_ALLOWED;
        Optional<Recipe> recipe = Phase1Data.recipes().get(recipeId);
        if (recipe.isEmpty() || recipe.get().category() != RecipeCategory.CRAFTING || recipe.get().outputs().isEmpty()) {
            return GiveResult.UNKNOWN;
        }
        if (!RecipeLocks.isUnlocked(player.server, recipeId)) return GiveResult.LOCKED;
        ResourceLocation out = ResourceLocation.tryParse(recipe.get().outputs().getFirst().item());
        ItemStack target = out == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(out));
        if (!canGhost(target)) return GiveResult.NOT_PLACEABLE;
        if (!budget(player)) return GiveResult.TOO_SOON;
        return give(player, target);
    }

    /**
     * Pick-block on the block at {@code pos} by a survival player who has none of its item: a
     * ghost item of it (a multiblock part gives its machine, a ghost its item).
     */
    public static GiveResult pipette(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (player.isSpectator() || !player.isAlive() || player.getAbilities().instabuild) return GiveResult.NOT_ALLOWED;
        if (!level.isLoaded(pos) || !RemoteView.canReach(player, pos, REACH_TOLERANCE)) return GiveResult.TOO_FAR;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return GiveResult.UNKNOWN;
        ItemStack target = state.getCloneItemStack(new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false), level, pos, player);
        if (!canGhost(target)) return GiveResult.NOT_PLACEABLE;
        Item item = target.getItem();
        if (player.getInventory().hasAnyMatching(s -> s.is(item))) return GiveResult.HAS_ITEM;
        if (!budget(player)) return GiveResult.TOO_SOON;
        return give(player, new ItemStack(item));
    }

    /**
     * Puts a ghost item of {@code target} in the player's hand the way vanilla pick-block does
     * (an existing one is selected; else a suitable hotbar slot, its item moved to a free slot);
     * refused when that would overwrite an item.
     */
    private static GiveResult give(ServerPlayer player, ItemStack target) {
        ItemStack ghost = GhostItem.of(target.getItem());
        Inventory inv = player.getInventory();
        if (inv.findSlotMatchingItem(ghost) < 0 && !inv.getItem(inv.getSuitableHotbarSlot()).isEmpty() && inv.getFreeSlot() < 0) {
            return GiveResult.NO_ROOM;
        }
        inv.setPickedItem(ghost);
        inv.setChanged();
        player.connection.send(new ClientboundSetCarriedItemPacket(inv.selected));
        player.inventoryMenu.broadcastChanges();
        return GiveResult.OK;
    }

    // --- reservations --------------------------------------------------------------------------

    /** Refuses a real placement into cells a ghost (still standing) reserves. */
    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        List<BlockSnapshot> snaps = event instanceof BlockEvent.EntityMultiPlaceEvent multi
                ? multi.getReplacedBlockSnapshots() : List.of(event.getBlockSnapshot());
        for (BlockSnapshot snap : snaps) {
            if (level.getBlockState(snap.getPos()).is(ModBlocks.GHOST.get())) continue; // a ghost item's use: checked by its plan
            for (BlockPos c : placedCells(level, snap.getPos())) {
                BlockPos owner = GhostReservations.owner(level, c);
                if (owner != null && level.getBlockState(owner).is(ModBlocks.GHOST.get())) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    /** Cells the block now at {@code pos} occupies: a multiblock controller's footprint (its parts come after the event), else {@code pos}. */
    private static List<BlockPos> placedCells(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof MultiblockControllerBlock mb && s.hasProperty(MultiblockControllerBlock.FACING)) {
            return new ArrayList<>(mb.footprint(pos, s));
        }
        return List.of(pos);
    }
}
