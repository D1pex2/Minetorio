package dev.minetorio.content.map;

import dev.minetorio.core.map.ChunkKeys;
import dev.minetorio.core.map.WatchCoverage;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Overworld chunks under continuous observation by a working radar: the union of the
 * {@value dev.minetorio.core.map.RadarLogic#NEAR_RADIUS}-chunk near areas (7×7) of the radars that
 * get power right now (Factorio's nearby scan). Remote view may only look at these (or at what a
 * player sees himself).
 *
 * <p><b>Upkeep.</b> Incremental ({@link WatchCoverage}): each radar checks its own entry every
 * tick (an O(1) lookup, see {@link RadarBlockEntity}) and registers or removes it when it starts
 * or stops working; a radar removed or whose chunk unloads removes itself. Nothing is saved: after
 * a restart each radar registers again on its first powered tick.
 *
 * <p><b>Clients</b> all get the set (the map is shared): the whole set on login, then once per
 * tick the net diff, in payloads of at most {@value MapPayloads.Watched#MAX_KEYS} chunks each.
 * Server thread only.
 */
public final class WatchedChunks {
    /** Told of every net change of the set, once per tick, on the server thread. */
    @FunctionalInterface
    public interface Listener {
        void changed(MinecraftServer server, Set<Long> added, Set<Long> removed);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    @Nullable
    private static MinecraftServer owner;
    private static WatchCoverage coverage = new WatchCoverage();

    private WatchedChunks() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> flush(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) sendAll(p);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            if (owner == e.getServer()) {
                owner = null;
                coverage = new WatchCoverage();
            }
        });
    }

    /** The coverage of the server's Overworld (a new server starts empty). */
    static WatchCoverage coverage(MinecraftServer server) {
        if (owner != server) {
            owner = server;
            coverage = new WatchCoverage();
        }
        return coverage;
    }

    public static void addListener(Listener listener) { LISTENERS.add(listener); }

    public static void removeListener(Listener listener) { LISTENERS.remove(listener); }

    // --- queries -----------------------------------------------------------------------------------

    /** Whether a working radar watches the chunk (only Overworld chunks ever are). */
    public static boolean isWatched(Level level, int chunkX, int chunkZ) {
        return level instanceof ServerLevel l && l.dimension() == Level.OVERWORLD
                && coverage(l.getServer()).isWatched(ChunkKeys.of(chunkX, chunkZ));
    }

    /** The radars watching the chunk, as controller block positions ({@code BlockPos.asLong}); empty if none. */
    public static Set<Long> watchedBy(Level level, int chunkX, int chunkZ) {
        if (!(level instanceof ServerLevel l) || l.dimension() != Level.OVERWORLD) return Set.of();
        return Set.copyOf(coverage(l.getServer()).watchedBy(ChunkKeys.of(chunkX, chunkZ)));
    }

    /** Number of watched chunks in the server's Overworld. */
    public static int count(MinecraftServer server) { return coverage(server).size(); }

    // --- radars ------------------------------------------------------------------------------------

    /** A radar (by controller position) watches the area around {@code centerChunk}; no-op if it already does. */
    static void watch(ServerLevel level, long radar, long centerChunk, int radius) {
        if (level.dimension() == Level.OVERWORLD) coverage(level.getServer()).put(radar, centerChunk, radius);
    }

    /** The radar watches nothing (any more). */
    static void unwatch(ServerLevel level, long radar) {
        if (level.dimension() == Level.OVERWORLD && owner == level.getServer()) coverage.remove(radar); // a stopped server's set is gone already
    }

    /** The radar's registered center chunk, or {@link WatchCoverage#NONE}. */
    static long centerOf(ServerLevel level, long radar) {
        return level.dimension() != Level.OVERWORLD ? WatchCoverage.NONE : coverage(level.getServer()).centerOf(radar);
    }

    // --- sync --------------------------------------------------------------------------------------

    private static void flush(MinecraftServer server) {
        WatchCoverage c = coverage(server);
        if (!c.hasChanges()) return;
        WatchCoverage.Diff d = c.drain();
        for (Listener l : LISTENERS) l.changed(server, d.added(), d.removed());
        if (server.getPlayerList().getPlayerCount() == 0) return;
        for (MapPayloads.Watched p : MapPayloads.Watched.diff(d.added(), d.removed())) PacketDistributor.sendToAllPlayers(p);
    }

    private static void sendAll(ServerPlayer player) {
        for (MapPayloads.Watched p : MapPayloads.Watched.full(coverage(player.server).chunks())) {
            PacketDistributor.sendToPlayer(player, p);
        }
    }

    /** Forgets the whole set, as after a server restart (GameTests only; radars register again as they tick). */
    public static void wipeForTests(MinecraftServer server) {
        if (!(server instanceof net.minecraft.gametest.framework.GameTestServer)) throw new IllegalStateException("GameTest server only");
        coverage(server);
        coverage = new WatchCoverage();
    }
}
