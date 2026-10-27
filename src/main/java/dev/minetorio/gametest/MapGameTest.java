package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.place;

import dev.minetorio.content.map.MapPayloads;
import dev.minetorio.content.map.RadarBlockEntity;
import dev.minetorio.content.map.WorldMap;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.map.ChunkKeys;
import dev.minetorio.core.map.MapChunkBatch;
import dev.minetorio.core.map.MapPalette;
import dev.minetorio.core.map.RadarFrontier;
import dev.minetorio.core.map.RadarLogic;
import dev.minetorio.core.map.SendQueue;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The world map and radars. The map is one per world, so each test that explores runs in its own
 * batch (batches run one after another) and starts from a wiped map.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class MapGameTest {
    /** Radar placed at (5,1,5) facing east (its footprint is 3×3 around there). */
    private static final BlockPos RADAR = new BlockPos(5, 1, 5);
    /** A pole west of the radar whose supply area reaches its footprint. */
    private static final BlockPos POLE = new BlockPos(3, 1, 6);

    @BeforeBatch(batch = "map_radar")
    public static void before0(ServerLevel level) { WorldMap.get(level.getServer()).wipeForTests(); }

    @AfterBatch(batch = "map_radar")
    public static void after0(ServerLevel level) { WorldMap.get(level.getServer()).wipeForTests(); }

    @BeforeBatch(batch = "map_unpowered")
    public static void before1(ServerLevel level) { WorldMap.get(level.getServer()).wipeForTests(); }

    @AfterBatch(batch = "map_unpowered")
    public static void after1(ServerLevel level) { WorldMap.get(level.getServer()).wipeForTests(); }

    @BeforeBatch(batch = "map_persist")
    public static void before2(ServerLevel level) { WorldMap.get(level.getServer()).wipeForTests(); }

    @AfterBatch(batch = "map_persist")
    public static void after2(ServerLevel level) { WorldMap.get(level.getServer()).wipeForTests(); }

    @BeforeBatch(batch = "map_radar_lost")
    public static void before4(ServerLevel level) { WorldMap.get(level.getServer()).wipeForTests(); }

    @AfterBatch(batch = "map_radar_lost")
    public static void after4(ServerLevel level) { WorldMap.get(level.getServer()).wipeForTests(); }

    @BeforeBatch(batch = "map_corrupt")
    public static void before5(ServerLevel level) { WorldMap.get(level.getServer()).wipeForTests(); }

    @AfterBatch(batch = "map_corrupt")
    public static void after5(ServerLevel level) { WorldMap.get(level.getServer()).wipeForTests(); }

    @BeforeBatch(batch = "map_players")
    public static void before3(ServerLevel level) { WorldMap.get(level.getServer()).wipeForTests(); }

    @AfterBatch(batch = "map_players")
    public static void after3(ServerLevel level) { WorldMap.get(level.getServer()).wipeForTests(); }

    private static RadarBlockEntity radar(GameTestHelper h) {
        BlockPos abs = h.absolutePos(RADAR);
        for (BlockPos p : BlockPos.betweenClosed(abs.offset(-3, -1, -3), abs.offset(3, 2, 3))) {
            if (h.getLevel().getBlockEntity(p) instanceof RadarBlockEntity be) return be;
        }
        throw new AssertionError("no radar placed");
    }

    private static List<Long> near(int[] c) { return ChunkKeys.around(c[0], c[1], RadarLogic.NEAR_RADIUS); }

    /**
     * Explored chunks within {@value #LOCAL} of a chunk, leaving out those within reach of a player
     * in the level. Tests count locally and skip players' areas: machines and mock players of other
     * tests stay in the shared test world and keep exploring (as they should).
     */
    private static int local(WorldMap map, ServerLevel level, int cx, int cz) {
        int n = 0;
        for (long k : ChunkKeys.around(cx, cz, LOCAL)) if (map.isExplored(k) && !nearPlayer(level, k)) n++;
        return n;
    }

    private static boolean nearPlayer(ServerLevel level, long chunk) {
        for (ServerPlayer p : level.players()) {
            ChunkPos c = p.chunkPosition();
            if (!p.isSpectator() && ChunkKeys.distance(chunk, ChunkKeys.of(c.x, c.z)) <= WorldMap.PLAYER_RADIUS) return true;
        }
        return false;
    }

    /** {@code expected} minus the chunks of {@code chunks} within reach of a player. */
    private static int minusPlayers(ServerLevel level, List<Long> chunks, int expected) {
        for (long k : chunks) if (nearPlayer(level, k)) expected--;
        return expected;
    }

    private static final int LOCAL = 6;

    /**
     * A powered radar first explores its 7×7 near area, then the first chunk beyond it (ring 4 of its
     * frontier) within 35 s of getting power.
     */
    @GameTest(template = "empty16", batch = "map_radar", timeoutTicks = 1000)
    public static void poweredRadarRevealsANewChunk(GameTestHelper h) {
        WorldMap map = WorldMap.get(h.getLevel().getServer());
        place(h, ModItems.RADAR.get(), RADAR, Direction.EAST);
        TestPower.powerAt(h, POLE);
        long start = h.getTick();
        h.startSequence()
                .thenWaitUntil(() -> {
                    RadarBlockEntity be = radar(h);
                    h.assertTrue(be.satisfaction() > 0.99, "radar not powered: " + be.satisfaction());
                    for (long k : near(be.centerChunk())) h.assertTrue(map.isExplored(k), "near chunk not explored yet");
                })
                .thenExecute(() -> {
                    int[] c = radar(h).centerChunk();
                    h.assertTrue(local(map, h.getLevel(), c[0], c[1]) == minusPlayers(h.getLevel(), near(c), 49), "explored beyond the near area early: " + local(map, h.getLevel(), c[0], c[1]));
                })
                .thenWaitUntil(() -> {
                    int[] c = radar(h).centerChunk();
                    long far = map.exploredChunks().stream().filter(k -> ChunkKeys.distance(k, ChunkKeys.of(c[0], c[1])) == 4 && !nearPlayer(h.getLevel(), k)).count();
                    h.assertTrue(far == 1, "no ring-4 chunk yet");
                })
                .thenExecute(() -> {
                    long took = h.getTick() - start;
                    h.assertTrue(took <= 700, "first far chunk after " + took + " ticks");
                    h.assertTrue(took >= RadarLogic.REVEAL_TICKS, "far chunk too early: " + took);
                    int[] c = radar(h).centerChunk();
                    h.assertTrue(local(map, h.getLevel(), c[0], c[1]) == minusPlayers(h.getLevel(), near(c), 49) + 1, "explored " + local(map, h.getLevel(), c[0], c[1]));
                    h.assertTrue(radar(h).frontier().index() > 49, "frontier index " + radar(h).frontier().index());
                })
                .thenExecute(() -> unpower(h))
                .thenSucceed();
    }

    /**
     * A radar's frontier chunk whose scan request is lost (dropped from the queue, as after a load
     * timeout or a restart) is asked for again with the next charge, not skipped: the radar keeps it
     * as its pending target until the map has explored it.
     */
    @GameTest(template = "empty16", batch = "map_radar_lost", timeoutTicks = 600)
    public static void lostRadarRequestIsAskedForAgain(GameTestHelper h) {
        WorldMap map = WorldMap.get(h.getLevel().getServer());
        place(h, ModItems.RADAR.get(), RADAR, Direction.EAST);
        TestPower.powerAt(h, POLE);
        long[] target = {RadarFrontier.NONE};
        h.startSequence()
                .thenWaitUntil(() -> {
                    for (long k : near(radar(h).centerChunk())) h.assertTrue(map.isExplored(k), "near chunk not explored yet");
                })
                .thenExecute(() -> radar(h).logic().load(1.0)) // charged: the next tick hands out a frontier chunk
                .thenWaitUntil(() -> h.assertTrue(radar(h).pendingTarget() != RadarFrontier.NONE, "no target yet"))
                .thenExecute(() -> {
                    target[0] = radar(h).pendingTarget();
                    map.dropRequestsForTests(); // the request is lost
                    h.assertFalse(map.isExplored(target[0]), "explored before the request was lost");
                    h.assertTrue(map.pendingScans() == 0, "request still pending");
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertFalse(map.isExplored(target[0]), "explored without a request");
                    h.assertTrue(radar(h).pendingTarget() == target[0], "radar moved on from its lost target");
                    radar(h).logic().load(1.0); // next charge
                })
                .thenWaitUntil(() -> h.assertTrue(map.isExplored(target[0]), "lost target not explored"))
                .thenExecute(() -> {
                    int[] c = radar(h).centerChunk();
                    h.assertTrue(local(map, h.getLevel(), c[0], c[1]) == minusPlayers(h.getLevel(), near(c), 49) + 1, "explored " + local(map, h.getLevel(), c[0], c[1]));
                    h.assertTrue(radar(h).pendingTarget() == RadarFrontier.NONE || map.isExplored(radar(h).pendingTarget()),
                            "target not cleared");
                })
                .thenExecute(() -> unpower(h))
                .thenSucceed();
    }

    /**
     * An unreadable region file is renamed to .bad and its chunks count as unexplored again (so
     * players and radars explore them anew).
     */
    @GameTest(template = "empty16", batch = "map_corrupt", timeoutTicks = 200)
    public static void corruptRegionFileIsReexplored(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WorldMap map = WorldMap.get(level.getServer());
        ChunkPos c = new ChunkPos(h.absolutePos(BlockPos.ZERO));
        long k = ChunkKeys.of(c.x, c.z);
        map.requestScan(k);
        h.succeedWhen(() -> {
            h.assertTrue(map.isExplored(k), "not explored yet");
            map.flushRegionsAndWait();
            long region = ChunkKeys.region(k);
            Path file = WorldMap.regionDir(level.getServer()).resolve("r." + ChunkKeys.x(region) + "." + ChunkKeys.z(region) + ".bin");
            try {
                h.assertTrue(Files.isRegularFile(file), "no region file " + file);
                Files.write(file, new byte[] {1, 2, 3, 4, 5, 6, 7});
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            WorldMap copy = WorldMap.load(map.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            copy.attach(level.getServer());
            h.assertTrue(copy.isExplored(k), "setup: not explored in the copy");
            h.assertTrue(copy.colors(k) == null, "colors from a corrupt file");
            h.assertFalse(copy.isExplored(k), "chunk of a corrupt region still explored");
            h.assertTrue(Files.isRegularFile(file.resolveSibling(file.getFileName() + ".bad")), "corrupt file not kept as .bad");
            h.assertFalse(Files.exists(file), "corrupt file still in place");
        });
    }

    /**
     * Removes the test's pole, so its radar stops exploring: test structures of finished batches stay
     * in the world, next to later batches' tests.
     */
    private static void unpower(GameTestHelper h) {
        h.setBlock(POLE, net.minecraft.world.level.block.Blocks.AIR);
    }

    /** Without power a radar explores nothing and charges nothing. */
    @GameTest(template = "empty16", batch = "map_unpowered", timeoutTicks = 400)
    public static void unpoweredRadarRevealsNothing(GameTestHelper h) {
        WorldMap map = WorldMap.get(h.getLevel().getServer());
        place(h, ModItems.RADAR.get(), RADAR, Direction.EAST);
        h.runAtTickTime(300, () -> {
            RadarBlockEntity be = radar(h);
            h.assertTrue(be.satisfaction() == 0, "radar powered: " + be.satisfaction());
            int[] c = be.centerChunk();
            h.assertTrue(local(map, h.getLevel(), c[0], c[1]) == 0, "explored " + local(map, h.getLevel(), c[0], c[1]));
            h.assertTrue(be.logic().progress() == 0, "charged " + be.logic().progress());
            h.assertTrue(be.frontier().index() == 0, "frontier moved");
            h.assertFalse(be.isWorking(), "working without power");
            h.succeed();
        });
    }

    /** The explored set (SavedData) and the chunk colors (region files) survive a save and reload. */
    @GameTest(template = "empty16", batch = "map_persist", timeoutTicks = 200)
    public static void exploredSetSurvivesSaveAndLoad(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WorldMap map = WorldMap.get(level.getServer());
        ChunkPos c = new ChunkPos(h.absolutePos(BlockPos.ZERO));
        long k = ChunkKeys.of(c.x, c.z), k2 = ChunkKeys.of(c.x + 1, c.z);
        map.requestScan(k);
        map.requestScan(k2);
        h.succeedWhen(() -> {
            h.assertTrue(map.isExplored(k) && map.isExplored(k2), "not explored yet");
            byte[] colors = map.colors(k);
            h.assertTrue(colors != null && colors.length == MapPalette.COLUMNS, "no colors");
            boolean any = false;
            for (byte b : colors) any |= b != 0;
            h.assertTrue(any, "chunk scanned as all empty");
            CompoundTag tag = map.save(new CompoundTag(), level.registryAccess());
            map.flushRegionsAndWait();
            WorldMap copy = WorldMap.load(tag, level.registryAccess());
            copy.attach(level.getServer()); // a fresh region store: colors come from disk
            h.assertTrue(copy.exploredCount() == map.exploredCount(), "explored " + copy.exploredCount() + " vs " + map.exploredCount());
            h.assertTrue(copy.isExplored(k) && copy.isExplored(k2), "explored chunks lost");
            h.assertTrue(Arrays.equals(copy.colors(k), map.colors(k)), "colors of " + c + " lost");
            h.assertTrue(Arrays.equals(copy.colors(k2), map.colors(k2)), "colors of the second chunk lost");
            h.assertFalse(copy.isExplored(ChunkKeys.of(c.x + 5, c.z)), "unexplored chunk loaded as explored");
        });
    }

    /**
     * Every chunk within 3 of a player gets explored (loading the ones that are not loaded), nothing
     * further; and the client batches built from them are bounded.
     */
    @GameTest(template = "empty16", batch = "map_players", timeoutTicks = 400)
    public static void playersRevealRadiusThree(GameTestHelper h) {
        WorldMap map = WorldMap.get(h.getLevel().getServer());
        ServerPlayer player = FakePlayerFactory.getMinecraft(h.getLevel());
        BlockPos at = h.absolutePos(new BlockPos(8, 1, 8));
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ChunkPos c = player.chunkPosition();
        map.revealAround(player, false);
        List<Long> square = ChunkKeys.around(c.x, c.z, WorldMap.PLAYER_RADIUS);
        h.succeedWhen(() -> {
            for (long k : square) h.assertTrue(map.isExplored(k), "chunk " + ChunkKeys.x(k) + "," + ChunkKeys.z(k) + " not explored");
            for (long k : ChunkKeys.around(c.x, c.z, WorldMap.PLAYER_RADIUS + 1)) {
                if (!square.contains(k) && !nearPlayer(h.getLevel(), k)) h.assertFalse(map.isExplored(k), "explored beyond radius 3");
            }
            SendQueue q = new SendQueue();
            q.addAllByRegion(square, c.x, c.z);
            MapPayloads.Chunks batch = map.nextBatch(q);
            h.assertTrue(batch != null && batch.data().length <= MapChunkBatch.MAX_BYTES, "batch too big");
            h.assertTrue(q.size() == 0, "49 chunks did not fit one batch");
        });
    }

    /**
     * A map payload of the most chunks allowed, with incompressible colors, stays within its byte
     * bound on the wire and decodes to the same chunks; more chunks are refused.
     */
    @GameTest(template = "empty16")
    public static void mapPayloadSizeIsBounded(GameTestHelper h) {
        Random rnd = new Random(7);
        List<MapChunkBatch.Entry> entries = new ArrayList<>();
        for (int i = 0; i < MapChunkBatch.MAX_CHUNKS; i++) {
            byte[] colors = new byte[MapPalette.COLUMNS];
            rnd.nextBytes(colors);
            entries.add(new MapChunkBatch.Entry(ChunkKeys.of(i, -i), colors));
        }
        ByteBuf buf = Unpooled.buffer();
        MapPayloads.Chunks.STREAM_CODEC.encode(buf, MapPayloads.Chunks.of(entries));
        int size = buf.readableBytes();
        h.assertTrue(size <= MapChunkBatch.MAX_BYTES + 5, "payload of " + size + " bytes");
        MapPayloads.Chunks back = MapPayloads.Chunks.STREAM_CODEC.decode(buf);
        try {
            List<MapChunkBatch.Entry> decoded = MapChunkBatch.decode(back.data());
            h.assertTrue(decoded.size() == entries.size(), "decoded " + decoded.size());
            for (int i = 0; i < decoded.size(); i++) {
                h.assertTrue(decoded.get(i).key() == entries.get(i).key()
                        && Arrays.equals(decoded.get(i).colors(), entries.get(i).colors()), "chunk " + i + " differs");
            }
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        entries.add(entries.get(0));
        boolean refused = false;
        try {
            MapPayloads.Chunks.of(entries);
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        h.assertTrue(refused, "oversized batch accepted");
        List<MapPayloads.Marker> many = new ArrayList<>();
        for (int i = 0; i < MapPayloads.Players.MAX_PLAYERS; i++) many.add(new MapPayloads.Marker("player" + i, i, -i, 90f));
        ByteBuf pb = Unpooled.buffer();
        MapPayloads.Players.STREAM_CODEC.encode(pb, new MapPayloads.Players(many));
        h.assertTrue(pb.readableBytes() < 4096, "players payload of " + pb.readableBytes() + " bytes");
        h.succeed();
    }
}
