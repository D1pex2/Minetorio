package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.place;

import dev.minetorio.content.map.MapPayloads;
import dev.minetorio.content.map.RadarBlockEntity;
import dev.minetorio.content.map.WatchedChunks;
import dev.minetorio.content.map.WorldMap;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.map.ChunkKeys;
import dev.minetorio.core.map.RadarLogic;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Chunks watched by working radars ({@link WatchedChunks}). The set is one per server and other
 * tests' radars stand nearby, so every check is about the test's own radars ({@code watchedBy}).
 * Each test removes its poles at the end, so its radars stop working (and exploring).
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class WatchedChunksGameTest {
    private static final BlockPos RADAR = new BlockPos(5, 1, 5);
    private static final BlockPos POLE = new BlockPos(3, 1, 6);
    /** A second radar east of the first, powered by its own pole (which does not reach the first). */
    private static final BlockPos RADAR2 = new BlockPos(10, 1, 5);
    private static final BlockPos POLE2 = new BlockPos(14, 1, 6);

    @AfterBatch(batch = "map_watch_restart")
    public static void after(ServerLevel level) { WorldMap.get(level.getServer()).wipeForTests(); }

    private static RadarBlockEntity radar(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        for (BlockPos p : BlockPos.betweenClosed(abs.offset(-2, -1, -2), abs.offset(2, 2, 2))) {
            if (h.getLevel().getBlockEntity(p) instanceof RadarBlockEntity be) return be;
        }
        StringBuilder found = new StringBuilder();
        for (BlockPos p : BlockPos.betweenClosed(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(15, 7, 15)))) {
            if (h.getLevel().getBlockEntity(p) instanceof RadarBlockEntity) found.append(h.relativePos(p)).append(' ');
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("no radar placed at " + rel + "; radars at " + found);
    }

    private static List<Long> near(RadarBlockEntity be) {
        int[] c = be.centerChunk();
        return ChunkKeys.around(c[0], c[1], RadarLogic.NEAR_RADIUS);
    }

    private static boolean watches(GameTestHelper h, long radar, long chunk) {
        return WatchedChunks.watchedBy(h.getLevel(), ChunkKeys.x(chunk), ChunkKeys.z(chunk)).contains(radar);
    }

    /** Chunks of the level that list the radar as a watcher, within 6 chunks of it. */
    private static int watchedByRadar(GameTestHelper h, RadarBlockEntity be) {
        int[] c = be.centerChunk();
        int n = 0;
        for (long k : ChunkKeys.around(c[0], c[1], RadarLogic.NEAR_RADIUS + 3)) if (watches(h, be.getBlockPos().asLong(), k)) n++;
        return n;
    }

    private static void assertWatchesNearArea(GameTestHelper h, RadarBlockEntity be) {
        long id = be.getBlockPos().asLong();
        for (long k : near(be)) {
            h.assertTrue(watches(h, id, k), "chunk " + ChunkKeys.x(k) + "," + ChunkKeys.z(k) + " not watched by the radar");
            h.assertTrue(WatchedChunks.isWatched(h.getLevel(), ChunkKeys.x(k), ChunkKeys.z(k)), "isWatched false");
        }
        h.assertTrue(watchedByRadar(h, be) == 49, "radar watches " + watchedByRadar(h, be) + " chunks, not 49");
    }

    /** A powered radar watches exactly its 7×7 near area; once its power is gone it watches nothing. */
    @GameTest(template = "empty16", timeoutTicks = 200)
    public static void poweredRadarWatchesItsNearAreaUntilUnpowered(GameTestHelper h) {
        place(h, ModItems.RADAR.get(), RADAR, Direction.EAST);
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    h.assertTrue(watchedByRadar(h, radar(h, RADAR)) == 0, "unpowered radar watches chunks");
                    TestPower.powerAt(h, POLE);
                })
                .thenWaitUntil(() -> h.assertTrue(radar(h, RADAR).isWorking(), "not working"))
                .thenWaitUntil(() -> assertWatchesNearArea(h, radar(h, RADAR)))
                .thenExecute(() -> h.setBlock(POLE, Blocks.AIR))
                .thenWaitUntil(() -> h.assertFalse(radar(h, RADAR).isWorking(), "still working"))
                .thenWaitUntil(() -> h.assertTrue(watchedByRadar(h, radar(h, RADAR)) == 0, "unpowered radar still watches"))
                .thenSucceed();
    }

    /**
     * Two powered radars whose areas overlap both watch the overlap; removing one keeps every chunk
     * the other still covers watched (by the other only).
     */
    @GameTest(template = "empty16", timeoutTicks = 200)
    public static void removingOneOfTwoOverlappingRadarsKeepsTheOverlap(GameTestHelper h) {
        place(h, ModItems.RADAR.get(), RADAR, Direction.EAST);
        place(h, ModItems.RADAR.get(), RADAR2, Direction.EAST);
        TestPower.powerAt(h, POLE);
        TestPower.powerAt(h, POLE2);
        long[] ids = new long[2];
        h.startSequence()
                .thenWaitUntil(() -> {
                    assertWatchesNearArea(h, radar(h, RADAR));
                    assertWatchesNearArea(h, radar(h, RADAR2));
                })
                .thenExecute(() -> {
                    RadarBlockEntity a = radar(h, RADAR), b = radar(h, RADAR2);
                    ids[0] = a.getBlockPos().asLong();
                    ids[1] = b.getBlockPos().asLong();
                    Set<Long> overlap = new HashSet<>(near(a));
                    overlap.retainAll(near(b));
                    h.assertFalse(overlap.isEmpty(), "setup: the radars' areas do not overlap");
                    for (long k : overlap) h.assertTrue(watches(h, ids[0], k) && watches(h, ids[1], k), "overlap not watched by both");
                    h.getLevel().destroyBlock(b.getBlockPos(), false);
                })
                .thenExecute(() -> {
                    RadarBlockEntity a = radar(h, RADAR);
                    for (long k : near(a)) {
                        h.assertTrue(watches(h, ids[0], k), "chunk of the remaining radar lost");
                        h.assertFalse(watches(h, ids[1], k), "removed radar still listed");
                    }
                    h.assertTrue(watchedByRadar(h, a) == 49, "remaining radar watches " + watchedByRadar(h, a));
                })
                .thenExecute(() -> {
                    h.setBlock(POLE, Blocks.AIR);
                    h.setBlock(POLE2, Blocks.AIR);
                })
                .thenSucceed();
    }

    /**
     * Nothing of the set is saved: after it is lost (as on a server restart, when it starts empty)
     * each powered radar registers again on its next tick.
     */
    @GameTest(template = "empty16", batch = "map_watch_restart", timeoutTicks = 200)
    public static void watchedSetIsRebuiltFromRadarsAfterARestart(GameTestHelper h) {
        place(h, ModItems.RADAR.get(), RADAR, Direction.EAST);
        TestPower.powerAt(h, POLE);
        h.startSequence()
                .thenWaitUntil(() -> assertWatchesNearArea(h, radar(h, RADAR)))
                .thenExecute(() -> {
                    WatchedChunks.wipeForTests(h.getLevel().getServer());
                    h.assertTrue(watchedByRadar(h, radar(h, RADAR)) == 0, "setup: set not wiped");
                    h.assertTrue(WatchedChunks.count(h.getLevel().getServer()) == 0, "setup: set not empty");
                })
                .thenIdle(2)
                .thenExecute(() -> assertWatchesNearArea(h, radar(h, RADAR)))
                .thenExecute(() -> h.setBlock(POLE, Blocks.AIR))
                .thenSucceed();
    }

    /** Watched payloads stay within their bound, a full set starts with a reset, and they decode to the same chunks. */
    @GameTest(template = "empty16")
    public static void watchedPayloadsAreBounded(GameTestHelper h) {
        List<Long> chunks = new ArrayList<>();
        for (int i = 0; i < MapPayloads.Watched.MAX_KEYS * 2 + 7; i++) chunks.add(ChunkKeys.of(i, -i));
        List<MapPayloads.Watched> full = MapPayloads.Watched.full(chunks);
        h.assertTrue(full.size() == 3, "pieces " + full.size());
        h.assertTrue(full.get(0).reset() && !full.get(1).reset() && !full.get(2).reset(), "reset not only on the first piece");
        Set<Long> back = new HashSet<>();
        for (MapPayloads.Watched w : full) {
            ByteBuf buf = Unpooled.buffer();
            MapPayloads.Watched.STREAM_CODEC.encode(buf, w);
            h.assertTrue(buf.readableBytes() <= 1 + 2 * 3 + MapPayloads.Watched.MAX_KEYS * 8, "piece of " + buf.readableBytes() + " bytes");
            MapPayloads.Watched d = MapPayloads.Watched.STREAM_CODEC.decode(buf);
            for (long k : d.added()) back.add(k);
            h.assertTrue(d.removed().length == 0, "removed in a full set");
        }
        h.assertTrue(back.equals(new HashSet<>(chunks)), "full set changed on the wire");
        h.assertTrue(MapPayloads.Watched.full(List.of()).size() == 1, "an empty set still resets");
        h.assertTrue(MapPayloads.Watched.diff(List.of(), List.of()).isEmpty(), "empty diff sent");
        List<MapPayloads.Watched> diff = MapPayloads.Watched.diff(chunks.subList(0, 10), chunks.subList(10, 20));
        h.assertTrue(diff.size() == 2 && !diff.get(0).reset(), "diff pieces");
        boolean refused = false;
        try {
            new MapPayloads.Watched(false, new long[MapPayloads.Watched.MAX_KEYS], new long[1]);
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        h.assertTrue(refused, "oversized payload accepted");
        h.succeed();
    }
}
