package dev.minetorio.content.map;

import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.machine.MachineMenu;
import dev.minetorio.content.machine.StoneFurnaceBlockEntity;
import dev.minetorio.content.mixin.ChunkMapAccess;
import dev.minetorio.content.mixin.DistanceManagerAccess;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.core.camera.RemoteArea;
import dev.minetorio.core.robot.RateBudget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Remote view (Factorio's): from the full map a player looks at a place a working radar watches
 * ({@link WatchedChunks}) or that lies in his own view, from the top-down camera, while his body
 * stays home. One remote focus per player, Overworld only.
 *
 * <p><b>Chunks.</b> The server loads the area ({@link RemoteArea#RADIUS} chunks around the focus
 * chunk) with one region ticket of its own that is not a simulation ticket: the chunks reach the
 * level at which vanilla sends chunks, but nothing in them ticks because of it. While the view is
 * open, vanilla's chunk tracking view for the player is centred on the focus ({@code ChunkMapMixin});
 * vanilla then streams the remote chunks, their block updates and (by distance to the focus,
 * {@code TrackedEntityMixin}) their entities, and drops the home chunks from the client, exactly as
 * if the player had moved there; leaving re-centres it on the body and vanilla sends the home back.
 * The body, its own chunk ticket and all gameplay (reach, damage) stay where the player stands.
 *
 * <p><b>Acting.</b> In the view the player may place ghosts (single and drag lines), pick ghost
 * items, remove ghosts, and open a machine's GUI to look at it and change its recipe. The server
 * replaces the reach check of those actions by {@link #canReach}: inside the view's area, on
 * eligible chunks. Real block placing and breaking, item use and entity interaction are refused
 * while the view is open, and the machine GUI moves no items ({@link #blocksTransfer}).
 *
 * <p><b>Lifetime.</b> Focus updates are rate-limited and must stay over eligible chunks. The view
 * closes when its focus chunk stops being eligible (the radar stops), and on exit, logout, death and
 * dimension change; its ticket is released each time.
 */
public final class RemoteView {
    public static final int TRACKING_VIEW_DISTANCE = RemoteArea.TRACKING_VIEW_DISTANCE;
    static final TicketType<ChunkPos> TICKET = TicketType.create("minetorio_remote", Comparator.comparingLong(ChunkPos::toLong));
    /** Ticks between checks that every open view is still over eligible ground. */
    private static final int CHECK_INTERVAL = 5;
    /** Ghost removals: like hand ghost clicks. */
    private static final int ACTION_BURST = 16;
    private static final double ACTION_REFILL_PER_TICK = 0.5;

    /** Results of the server handlers (for tests). */
    public enum Result { OK, NOT_ALLOWED, NOT_OVERWORLD, NOT_WATCHED, TOO_SOON, NOT_REMOTE, OUT_OF_AREA, NO_MACHINE, NO_GHOST }

    /** Why a view was refused or closed (sent to the client). */
    public enum Reason { NONE, NOT_WATCHED, LOST_COVERAGE, EXITED, NOT_ALLOWED }

    /** Client hook for {@link RemotePayloads.State} (set by the client's remote view). */
    @FunctionalInterface
    public interface ClientHandler {
        void accept(boolean active, double x, double z, Reason reason);
    }

    @Nullable
    public static volatile ClientHandler clientHandler;
    /** Whether this client is in remote view (set by the client; blocks item moves in machine GUIs there too). */
    public static volatile boolean clientActive;

    private static final class Session {
        final ResourceKey<Level> dimension;
        ChunkPos center;
        double x, z;
        int menuId = -1;
        @Nullable
        BlockPos menuPos;

        Session(ResourceKey<Level> dimension, ChunkPos center, double x, double z) {
            this.dimension = dimension;
            this.center = center;
            this.x = x;
            this.z = z;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, RateBudget> MOVE_BUDGETS = new HashMap<>();
    private static final Map<UUID, RateBudget> ACTION_BUDGETS = new HashMap<>();

    private RemoteView() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) end(p, Reason.NONE, false, false);
            MOVE_BUDGETS.remove(e.getEntity().getUUID());
            ACTION_BUDGETS.remove(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) end(p, Reason.NOT_ALLOWED, true, true);
        });
        NeoForge.EVENT_BUS.addListener((EntityTravelToDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) end(p, Reason.NOT_ALLOWED, false, true);
        });
        // a respawned or moved player is a new start; nothing may survive from an old body
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) end(p, Reason.NOT_ALLOWED, false, true);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) end(p, Reason.NOT_ALLOWED, false, true);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            SESSIONS.clear();
            MOVE_BUDGETS.clear();
            ACTION_BUDGETS.clear();
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (!SESSIONS.isEmpty() && e.getServer().getTickCount() % CHECK_INTERVAL == 0) checkAll(e.getServer());
        });
        // no hands-on play from afar: the view only plans
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> refuse(e.getEntity(), e));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e) -> refuse(e.getEntity(), e));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem e) -> refuse(e.getEntity(), e));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract e) -> refuse(e.getEntity(), e));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteractSpecific e) -> refuse(e.getEntity(), e));
        NeoForge.EVENT_BUS.addListener((AttackEntityEvent e) -> refuse(e.getEntity(), e));
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e) -> refuse(e.getPlayer(), e));
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e) -> {
            if (e.getEntity() instanceof Player p) refuse(p, e);
        });
    }

    private static void refuse(Player player, ICancellableEvent event) {
        if (player instanceof ServerPlayer && isRemote(player)) event.setCanceled(true);
    }

    // --- queries -----------------------------------------------------------------------------------

    /** Whether {@code player} (server side) is in remote view. */
    public static boolean isRemote(Player player) {
        return player instanceof ServerPlayer && SESSIONS.containsKey(player.getUUID());
    }

    /** The remote focus chunk vanilla chunk tracking centres on for {@code player} in {@code level}, or null. */
    @Nullable
    public static ChunkPos trackingCenter(ServerPlayer player, ServerLevel level) {
        Session s = SESSIONS.get(player.getUUID());
        return s != null && s.dimension == level.dimension() ? s.center : null;
    }

    /** Where entities are tracked from for {@code player}: the remote focus, or null (his body). */
    @Nullable
    public static Vec3 trackingPosition(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s != null && s.dimension == player.level().dimension() ? new Vec3(s.x, player.getY(), s.z) : null;
    }

    /** The remote focus chunk of {@code player}, or null when he is not in remote view. */
    @Nullable
    public static ChunkPos focus(Player player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.center;
    }

    /** The player's view distance: the chunks around him he sees himself. */
    public static int ownViewDistance(ServerPlayer player) {
        // as ChunkMap: the server's view distance is kept in 2..32
        return Mth.clamp(player.requestedViewDistance(), 2, Mth.clamp(player.server.getPlayerList().getViewDistance(), 2, 32));
    }

    /** Whether the remote view may be over chunk {@code (cx, cz)}: watched by a working radar, or in the player's own view. */
    public static boolean eligible(ServerPlayer player, int cx, int cz) {
        if (player.level().dimension() != Level.OVERWORLD) return false;
        if (WatchedChunks.isWatched(player.level(), cx, cz)) return true;
        ChunkPos own = player.chunkPosition();
        return RemoteArea.ownView(own.x, own.z, ownViewDistance(player), cx, cz);
    }

    /** Whether {@code pos} lies in the area of {@code player}'s remote view (false when he has none). */
    public static boolean inArea(ServerPlayer player, BlockPos pos) {
        Session s = SESSIONS.get(player.getUUID());
        return s != null && s.dimension == player.level().dimension()
                && RemoteArea.inArea(s.center.x, s.center.z, pos.getX() >> 4, pos.getZ() >> 4, (cx, cz) -> eligible(player, cx, cz));
    }

    /**
     * The reach check of the actions the remote view allows: in remote view, {@code pos} must be in
     * its area; otherwise vanilla's block reach (plus {@code tolerance}).
     */
    public static boolean canReach(ServerPlayer player, BlockPos pos, double tolerance) {
        return isRemote(player) ? inArea(player, pos) : player.canInteractWithBlock(pos, tolerance);
    }

    /** Whether a machine GUI opened from the remote view is still valid for {@code player}. */
    public static boolean menuValid(Player player, AbstractContainerMenu menu) {
        if (!(player instanceof ServerPlayer sp)) return false;
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null || s.menuPos == null || s.menuId != menu.containerId || !inArea(sp, s.menuPos)) return false;
        BlockEntity be = sp.level().getBlockEntity(s.menuPos);
        return be instanceof MenuProvider && !be.isRemoved();
    }

    /** Whether clicks in a machine GUI must move no items: it is open from the remote view (either side). */
    public static boolean blocksTransfer(Player player) {
        return player.level().isClientSide() ? clientActive : isRemote(player);
    }

    // --- handlers ----------------------------------------------------------------------------------

    /** Opens (or re-centres) {@code player}'s remote view on block column {@code (x, z)}. */
    public static Result enter(ServerPlayer player, double x, double z) {
        if (player.isSpectator() || !player.isAlive()) return refused(player, x, z, Result.NOT_ALLOWED, Reason.NOT_ALLOWED);
        if (player.level().dimension() != Level.OVERWORLD) return refused(player, x, z, Result.NOT_OVERWORLD, Reason.NOT_ALLOWED);
        if (!Double.isFinite(x) || !Double.isFinite(z)) return Result.NOT_ALLOWED;
        if (!moveBudget(player)) return Result.TOO_SOON;
        int cx = RemoteArea.chunkOf(x), cz = RemoteArea.chunkOf(z);
        if (!eligible(player, cx, cz)) return refused(player, x, z, Result.NOT_WATCHED, Reason.NOT_WATCHED);
        Session s = SESSIONS.get(player.getUUID());
        if (s != null) {
            recenter(player, s, x, z);
        } else {
            s = new Session(player.level().dimension(), new ChunkPos(cx, cz), x, z);
            SESSIONS.put(player.getUUID(), s);
            player.serverLevel().getChunkSource().addRegionTicket(TICKET, s.center, RemoteArea.TICKET_DISTANCE, s.center, false);
            retrack(player, player.serverLevel());
        }
        send(player, true, x, z, Reason.NONE);
        return Result.OK;
    }

    /** The remote camera moved to {@code (x, z)}; refused (and the client put back) when that is not eligible. */
    public static Result move(ServerPlayer player, double x, double z) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return Result.NOT_REMOTE;
        if (!Double.isFinite(x) || !Double.isFinite(z)) return Result.NOT_ALLOWED;
        if (!moveBudget(player)) {
            send(player, true, s.x, s.z, Reason.NONE);
            return Result.TOO_SOON;
        }
        if (!eligible(player, RemoteArea.chunkOf(x), RemoteArea.chunkOf(z))) {
            send(player, true, s.x, s.z, Reason.NOT_WATCHED);
            return Result.NOT_WATCHED;
        }
        recenter(player, s, x, z);
        return Result.OK;
    }

    /** The player leaves his remote view. */
    public static void exit(ServerPlayer player) {
        end(player, Reason.EXITED, true, false);
    }

    /** Opens the GUI of the machine at {@code pos} (an assembler or a furnace) from the remote view. */
    public static Result openMenu(ServerPlayer player, BlockPos pos) {
        if (!isRemote(player)) return Result.NOT_REMOTE;
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !inArea(player, pos)) return Result.OUT_OF_AREA;
        BlockPos at = menuTarget(player, pos);
        if (at == null) return Result.NO_MACHINE;
        player.openMenu((MenuProvider) level.getBlockEntity(at), at);
        return attachMenu(player, at) ? Result.OK : Result.NO_MACHINE;
    }

    /**
     * The machine whose GUI a remote click on {@code pos} opens (the controller of a multiblock
     * part; an assembler or a furnace), in the view's area; null if none.
     */
    @Nullable
    public static BlockPos menuTarget(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !inArea(player, pos)) return null;
        BlockPos at = level.getBlockState(pos).getBlock() instanceof MultiblockPartBlock ? MultiblockPartBlock.controllerPos(level, pos) : pos;
        if (at == null || !level.isLoaded(at) || !inArea(player, at)) return null;
        BlockEntity be = level.getBlockEntity(at);
        return be instanceof AssemblerBlockEntity || be instanceof StoneFurnaceBlockEntity ? at.immutable() : null;
    }

    /**
     * Marks the machine GUI the player now has open (of the machine at {@code at}) as opened from
     * his remote view, so it stays valid from afar (and moves no items). False if no machine GUI is open.
     */
    public static boolean attachMenu(ServerPlayer player, BlockPos at) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || !(player.containerMenu instanceof MachineMenu)) return false;
        s.menuId = player.containerMenu.containerId;
        s.menuPos = at.immutable();
        return true;
    }

    /** Removes the ghost at {@code pos} (a left-click in the remote view). */
    public static Result removeGhost(ServerPlayer player, BlockPos pos) {
        if (!isRemote(player)) return Result.NOT_REMOTE;
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !inArea(player, pos)) return Result.OUT_OF_AREA;
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return Result.NOT_ALLOWED;
        if (!level.getBlockState(pos).is(ModBlocks.GHOST.get())) return Result.NO_GHOST;
        if (!ACTION_BUDGETS.computeIfAbsent(player.getUUID(), u -> new RateBudget(ACTION_BURST, ACTION_REFILL_PER_TICK))
                .take(player.server.getTickCount(), 1)) {
            return Result.TOO_SOON;
        }
        level.removeBlock(pos, false);
        return Result.OK;
    }

    // --- upkeep ------------------------------------------------------------------------------------

    /** Closes every view whose focus chunk is no longer eligible (the radar stopped, the player moved off). */
    public static void checkAll(MinecraftServer server) {
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                Session s = SESSIONS.remove(id);
                ServerLevel level = server.getLevel(s.dimension);
                if (level != null) level.getChunkSource().removeRegionTicket(TICKET, s.center, RemoteArea.TICKET_DISTANCE, s.center, false);
                continue;
            }
            check(p);
        }
    }

    /** Closes {@code player}'s view if its focus chunk is no longer eligible; true when it stays open. */
    public static boolean check(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return false;
        if (s.dimension == player.level().dimension() && player.isAlive() && eligible(player, s.center.x, s.center.z)) return true;
        end(player, Reason.LOST_COVERAGE, true, true);
        return false;
    }

    private static void recenter(ServerPlayer player, Session s, double x, double z) {
        s.x = x;
        s.z = z;
        ChunkPos c = new ChunkPos(RemoteArea.chunkOf(x), RemoteArea.chunkOf(z));
        if (c.equals(s.center)) return;
        ServerLevel level = player.serverLevel();
        // the new ticket first: chunks in both areas stay loaded
        level.getChunkSource().addRegionTicket(TICKET, c, RemoteArea.TICKET_DISTANCE, c, false);
        level.getChunkSource().removeRegionTicket(TICKET, s.center, RemoteArea.TICKET_DISTANCE, s.center, false);
        s.center = c;
        retrack(player, level);
    }

    /**
     * Ends {@code player}'s remote view (if any): releases the ticket, closes a GUI it opened, and
     * when {@code retrack}, re-centres chunk tracking on his body; {@code notify} tells his client.
     */
    static void end(ServerPlayer player, Reason reason, boolean retrack, boolean notify) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s == null) return;
        ServerLevel level = player.server.getLevel(s.dimension);
        if (level != null) level.getChunkSource().removeRegionTicket(TICKET, s.center, RemoteArea.TICKET_DISTANCE, s.center, false);
        if (s.menuId >= 0 && player.containerMenu.containerId == s.menuId && player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        if (retrack && level == player.level()) retrack(player, level);
        if (notify) send(player, false, s.x, s.z, reason);
    }

    /** Recomputes the player's chunk tracking view (vanilla sends and drops chunks) and his entity tracking. */
    private static void retrack(ServerPlayer player, ServerLevel level) {
        if (player.level() != level) return;
        ((ChunkMapAccess) level.getChunkSource().chunkMap).minetorio$updateChunkTracking(player);
        // entity pairing for real players (a fake player is not in the chunk map's player list)
        if (level.players().contains(player)) level.getChunkSource().chunkMap.move(player);
    }

    private static boolean moveBudget(ServerPlayer player) {
        return MOVE_BUDGETS.computeIfAbsent(player.getUUID(), u -> new RateBudget(RemoteArea.MOVE_BURST, RemoteArea.MOVE_REFILL_PER_TICK))
                .take(player.server.getTickCount(), 1);
    }

    private static Result refused(ServerPlayer player, double x, double z, Result result, Reason reason) {
        if (!SESSIONS.containsKey(player.getUUID())) send(player, false, x, z, reason);
        return result;
    }

    private static void send(ServerPlayer player, boolean active, double x, double z, Reason reason) {
        PacketDistributor.sendToPlayer(player, new RemotePayloads.State(active, x, z, (byte) reason.ordinal()));
    }

    // --- tests -------------------------------------------------------------------------------------

    /** Forgets {@code player}'s rate budgets (tests share fake players). */
    public static void resetBudgets(Player player) {
        MOVE_BUDGETS.remove(player.getUUID());
        ACTION_BUDGETS.remove(player.getUUID());
    }

    /** Ends the view without telling a client (tests with fake players). */
    public static void endForTests(ServerPlayer player) {
        end(player, Reason.EXITED, true, false);
    }

    /** Whether the remote view's ticket is on chunk {@code pos} of {@code level} (the strongest ticket there is ours). */
    public static boolean hasTicket(ServerLevel level, ChunkPos pos) {
        String s = ((DistanceManagerAccess) level.getChunkSource().chunkMap.getDistanceManager()).minetorio$ticketDebugString(pos.toLong());
        return s.contains(TICKET.toString());
    }
}
