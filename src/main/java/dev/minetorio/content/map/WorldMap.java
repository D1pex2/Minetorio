package dev.minetorio.content.map;

import dev.minetorio.core.Units;
import dev.minetorio.core.map.ChunkKeys;
import dev.minetorio.core.map.MapChunkBatch;
import dev.minetorio.core.map.ScanThrottle;
import dev.minetorio.core.map.SendQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The world's map (Overworld only), shared by every player like the research.
 *
 * <p><b>Data.</b> The set of explored chunks is this {@link SavedData} ({@value #NAME}); the
 * chunks' colors ({@link MapScanner}) live in deflated region files ({@link MapRegionStore}),
 * written off the server thread when the world saves.
 *
 * <p><b>Exploring.</b> A chunk becomes explored when it is first scanned. Scans are requested
 * ({@link #requestScan}) by players (every chunk within {@value #PLAYER_RADIUS} of each Overworld
 * player who is not a spectator), radars (their near area and their frontier) and block changes by
 * players; chunks outside the world border are never requested. Requests are queued; per tick at
 * most {@value #SCANS_PER_TICK} scans run (finished loads first) and at most
 * {@value #QUEUE_LOOKS_PER_TICK} queue entries are looked at. An unexplored chunk that is not loaded
 * is loaded with a ticket (at most {@value #MAX_PENDING_LOADS} at a time) and scanned once it is; a
 * load that times out is queued again, so no request is lost. An explored chunk is re-scanned only
 * if loaded, and at most once per {@link #RESCAN_TICKS} ticks ({@link ScanThrottle}). A region file
 * that cannot be read makes its chunks unexplored again.
 *
 * <p><b>Clients</b> get only explored chunks: everything on login (region by region, nearest
 * first, so each region file is read once), then each chunk whose colors changed, in deflated
 * batches of {@value MapChunkBatch#MAX_CHUNKS} chunks, at most one batch per player per tick and
 * {@value #SEND_BYTES_PER_TICK} bytes per tick on average ({@link SendQueue}). Player positions go
 * out every {@value #PLAYERS_EVERY} ticks.
 */
public final class WorldMap extends SavedData {
    public static final String NAME = "minetorio_map";
    /** Chunks within this distance of a player get explored. */
    public static final int PLAYER_RADIUS = 3;
    /** Least ticks between two scans of the same explored chunk. */
    public static final int RESCAN_TICKS = Units.ticks(5);
    static final int SCANS_PER_TICK = 8;
    static final int MAX_PENDING_LOADS = 8;
    /** A chunk load that takes longer is given up (it is requested again later). */
    static final int LOAD_TIMEOUT_TICKS = Units.ticks(20);
    static final int PLAYER_REVEAL_EVERY = 20;
    /** Explored chunks around players are re-scanned this often (throttled, only changes are sent). */
    static final int PLAYER_REFRESH_EVERY = Units.ticks(10);
    static final int PLAYERS_EVERY = 10;
    /** Queue entries looked at per tick at most (unloaded explored ones are skipped cheaply, but not without limit). */
    static final int QUEUE_LOOKS_PER_TICK = 64;
    /** Average map bytes sent to one client per tick at most (~240 kB/s). */
    public static final int SEND_BYTES_PER_TICK = 12_000;
    private static final String KEY_EXPLORED = "explored";
    private static final TicketType<ChunkPos> TICKET = TicketType.create("minetorio_map", Comparator.comparingLong(ChunkPos::toLong));

    private final LongOpenHashSet explored = new LongOpenHashSet();

    // runtime state, not saved
    @Nullable
    private MinecraftServer server;
    @Nullable
    private MapRegionStore store;
    private final ScanThrottle throttle = new ScanThrottle(RESCAN_TICKS);
    private final LinkedHashSet<Long> queue = new LinkedHashSet<>();
    private final Map<Long, Long> pendingLoads = new HashMap<>();
    private final Map<UUID, SendQueue> sendQueues = new HashMap<>();

    private WorldMap() {}

    private static SavedData.Factory<WorldMap> factory() {
        return new SavedData.Factory<>(WorldMap::new, WorldMap::load);
    }

    public static WorldMap get(MinecraftServer server) {
        WorldMap map = server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
        if (map.server != server) map.attach(server);
        return map;
    }

    /** Binds a loaded map to its server and its region folder. */
    public void attach(MinecraftServer server) {
        this.server = server;
        this.store = new MapRegionStore(regionDir(server), this::forgetRegion);
    }

    public static Path regionDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(NAME);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> get(e.getServer()).tick());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) get(p.server).onLogin(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) get(p.server).sendQueues.remove(p.getUUID());
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Save e) -> {
            if (!(e.getLevel() instanceof ServerLevel l) || l.dimension() != Level.OVERWORLD) return;
            WorldMap map = get(l.getServer());
            map.flushRegions();
            if (!l.getServer().isRunning() && map.store != null) map.store.awaitWrites(); // stopping: finish the files
        });
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e) -> blockChanged(e.getLevel(), e.getPos().getX(), e.getPos().getZ()));
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e) -> blockChanged(e.getLevel(), e.getPos().getX(), e.getPos().getZ()));
    }

    private static void blockChanged(LevelAccessor level, int x, int z) {
        if (level instanceof ServerLevel l && l.dimension() == Level.OVERWORLD) get(l.getServer()).requestScan(ChunkKeys.of(x >> 4, z >> 4));
    }

    // --- queries -----------------------------------------------------------------------------------

    public boolean isExplored(long chunk) { return explored.contains(chunk); }

    public int exploredCount() { return explored.size(); }

    /** A copy of the explored set. */
    public List<Long> exploredChunks() { return new ArrayList<>(explored); }

    /** Colors of an explored chunk, or null. */
    @Nullable
    public byte[] colors(long chunk) {
        return store == null || !explored.contains(chunk) ? null : store.colors(chunk);
    }

    /** Whether the chunk lies inside the Overworld's world border (nothing outside is explored). */
    public boolean inBorder(long chunk) {
        return server == null || server.overworld().getWorldBorder().isWithinBounds(new ChunkPos(ChunkKeys.x(chunk), ChunkKeys.z(chunk)));
    }

    /** Chunks queued or loading for a scan. */
    public int pendingScans() { return queue.size() + pendingLoads.size(); }

    // --- scanning ----------------------------------------------------------------------------------

    /** Asks for a scan of the chunk (see class doc for queueing, loading and throttling). */
    public void requestScan(long chunk) {
        if (queue.contains(chunk) || pendingLoads.containsKey(chunk) || !inBorder(chunk)) return;
        if (explored.contains(chunk) && !throttle.request(chunk, now())) return; // comes back through due()
        queue.add(chunk);
    }

    /** Requests every chunk within {@code radius} of (cx, cz). */
    public void requestArea(int cx, int cz, int radius) {
        for (long k : ChunkKeys.around(cx, cz, radius)) requestScan(k);
    }

    private long now() { return server == null ? 0 : server.getTickCount(); }

    private void tick() {
        if (server == null) return;
        ServerLevel level = server.overworld();
        long now = now();
        for (long k : throttle.due(now)) queue.add(k);
        int scans = processLoads(level, now);
        processQueue(level, now, SCANS_PER_TICK - scans);
        if (now % PLAYER_REVEAL_EVERY == 0) revealAroundPlayers(level, now % PLAYER_REFRESH_EVERY == 0);
        if (now % PLAYERS_EVERY == 0) sendPlayers();
        flushSends();
        if (now % 1200 == 0) throttle.prune(now);
    }

    /** Scans up to {@code budget} loaded queued chunks and starts loads, looking at {@value #QUEUE_LOOKS_PER_TICK} entries at most. */
    private void processQueue(ServerLevel level, long now, int budget) {
        int scans = 0, looks = 0;
        for (Iterator<Long> it = queue.iterator(); it.hasNext() && scans < budget && looks++ < QUEUE_LOOKS_PER_TICK; ) {
            long k = it.next();
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkKeys.x(k), ChunkKeys.z(k));
            if (chunk != null) {
                it.remove();
                scan(level, k, chunk, now);
                scans++;
            } else if (explored.contains(k)) {
                it.remove(); // stays as last seen until it is loaded again
            } else if (pendingLoads.size() < MAX_PENDING_LOADS) {
                it.remove();
                ChunkPos pos = new ChunkPos(ChunkKeys.x(k), ChunkKeys.z(k));
                level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
                pendingLoads.put(k, now);
            }
        }
    }

    /**
     * Scans chunks whose load finished; a load that timed out is given up and, if the chunk is still
     * unexplored, queued again (so no request is lost). Returns the number of scans done.
     */
    private int processLoads(ServerLevel level, long now) {
        int scans = 0;
        for (Iterator<Map.Entry<Long, Long>> it = pendingLoads.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Long> e = it.next();
            long k = e.getKey();
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkKeys.x(k), ChunkKeys.z(k));
            if (chunk == null && now - e.getValue() < LOAD_TIMEOUT_TICKS) continue;
            it.remove();
            ChunkPos pos = new ChunkPos(ChunkKeys.x(k), ChunkKeys.z(k));
            level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
            if (chunk != null) {
                scan(level, k, chunk, now);
                scans++;
            } else if (!explored.contains(k)) {
                queue.add(k);
            }
        }
        return scans;
    }

    private void scan(ServerLevel level, long k, LevelChunk chunk, long now) {
        if (store == null) return;
        byte[] colors = MapScanner.scan(level, chunk);
        throttle.scanned(k, now);
        boolean changed = store.put(k, colors); // first: a corrupt region file forgets its chunks here
        boolean added = explored.add(k);
        if (added) setDirty();
        if (changed || added) {
            for (SendQueue q : sendQueues.values()) q.add(k);
        }
    }

    // --- players -----------------------------------------------------------------------------------

    /** Explores the chunks around one player; with {@code refresh}, also re-scans explored ones (throttled). */
    public void revealAround(ServerPlayer player, boolean refresh) {
        if (player.isSpectator() || player.level().dimension() != Level.OVERWORLD) return;
        ChunkPos c = player.chunkPosition();
        for (long k : ChunkKeys.around(c.x, c.z, PLAYER_RADIUS)) {
            if (refresh || !explored.contains(k)) requestScan(k);
        }
    }

    private void revealAroundPlayers(ServerLevel level, boolean refresh) {
        for (ServerPlayer p : level.players()) revealAround(p, refresh);
    }

    private void onLogin(ServerPlayer player) {
        SendQueue q = new SendQueue();
        ChunkPos c = player.chunkPosition();
        q.addAllByRegion(explored, c.x, c.z);
        sendQueues.put(player.getUUID(), q);
    }

    /** At most one batch per player per tick, within {@value #SEND_BYTES_PER_TICK} bytes per tick on average. */
    private void flushSends() {
        if (server == null || store == null || sendQueues.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            SendQueue q = sendQueues.get(p.getUUID());
            if (q == null) continue;
            q.refill(SEND_BYTES_PER_TICK, MapChunkBatch.MAX_BYTES);
            if (q.size() == 0 || !q.canSend()) continue;
            MapPayloads.Chunks payload = nextBatch(q);
            if (payload == null) continue;
            q.spend(payload.data().length);
            PacketDistributor.sendToPlayer(p, payload);
        }
    }

    /** The next batch of a send queue (at most {@value MapChunkBatch#MAX_CHUNKS} chunks), or null if none of them has colors. */
    @Nullable
    public MapPayloads.Chunks nextBatch(SendQueue q) {
        if (store == null) return null;
        List<MapChunkBatch.Entry> entries = new ArrayList<>();
        for (long k : q.poll(MapChunkBatch.MAX_CHUNKS)) {
            byte[] c = colors(k);
            if (c != null) entries.add(new MapChunkBatch.Entry(k, c));
        }
        return entries.isEmpty() ? null : MapPayloads.Chunks.of(entries);
    }

    private void sendPlayers() {
        if (server == null || server.getPlayerList().getPlayerCount() == 0) return;
        List<MapPayloads.Marker> markers = new ArrayList<>();
        for (ServerPlayer p : server.overworld().players()) {
            if (markers.size() >= MapPayloads.Players.MAX_PLAYERS) break;
            if (p.isSpectator()) continue;
            String name = p.getGameProfile().getName();
            if (name.length() > MapPayloads.Players.MAX_NAME) name = name.substring(0, MapPayloads.Players.MAX_NAME);
            markers.add(new MapPayloads.Marker(name, p.getBlockX(), p.getBlockZ(), p.getYRot()));
        }
        PacketDistributor.sendToAllPlayers(new MapPayloads.Players(markers));
    }

    // --- persistence -------------------------------------------------------------------------------

    /** Starts writing the changed region files (on world save). */
    public void flushRegions() {
        if (store != null) store.saveDirty();
    }

    /** Writes the changed region files and waits until they are on disk. */
    public void flushRegionsAndWait() {
        if (store == null) return;
        store.saveDirty();
        store.awaitWrites();
    }

    /** A region file was unreadable: its chunks count as unexplored again. */
    private void forgetRegion(long region) {
        if (explored.removeIf((java.util.function.LongPredicate) k -> ChunkKeys.region(k) == region)) setDirty();
    }

    private void requireGameTest() {
        if (!(server instanceof GameTestServer)) throw new IllegalStateException("GameTest server only");
    }

    /** Drops every queued and loading scan, as if they were lost (GameTests only). */
    public void dropRequestsForTests() {
        requireGameTest();
        for (long k : pendingLoads.keySet()) {
            ChunkPos pos = new ChunkPos(ChunkKeys.x(k), ChunkKeys.z(k));
            server.overworld().getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
        }
        pendingLoads.clear();
        queue.clear();
    }

    /** Forgets the whole map, on disk too, and every pending scan (GameTests only). */
    public void wipeForTests() {
        dropRequestsForTests();
        throttle.clear();
        explored.clear();
        if (store != null) store.wipe();
        setDirty();
    }

    public static WorldMap load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldMap map = new WorldMap();
        for (long k : tag.getLongArray(KEY_EXPLORED)) map.explored.add(k);
        return map;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray(KEY_EXPLORED, explored.toLongArray());
        return tag;
    }
}
