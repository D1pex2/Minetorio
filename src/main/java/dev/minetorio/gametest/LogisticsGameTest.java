package dev.minetorio.gametest;

import com.mojang.authlib.GameProfile;
import dev.minetorio.content.logistics.LogisticChestBlockEntity;
import dev.minetorio.content.logistics.LogisticRobotEntity;
import dev.minetorio.content.logistics.LogisticsManager;
import dev.minetorio.content.logistics.LogisticsRegistry;
import dev.minetorio.content.logistics.PersonalLogistics;
import dev.minetorio.content.logistics.PlayerItems;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.robot.Ghosts;
import dev.minetorio.content.robot.RoboportBlockEntity;
import dev.minetorio.core.logistics.LogisticMode;
import dev.minetorio.core.logistics.LogisticRequest;
import dev.minetorio.core.logistics.LogisticStats;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The logistic network: chests, logistic robots, personal logistics and the personal roboport. Every
 * test with a roboport runs in its own batch: a logistic area (50×50) reaches into neighbouring test
 * areas. Players are FakePlayers (mock players cannot receive mod payloads) registered with
 * {@link LogisticsManager#addExtraPlayer}, since FakePlayers are not in the level's player list.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class LogisticsGameTest {
    /** Roboport controller (facing SOUTH: footprint x 2..5, z 2..5). */
    private static final BlockPos PORT = new BlockPos(5, 1, 2);
    private static final String PLATE = "minetorio:iron_plate";
    private static final String GEAR = "minetorio:iron_gear_wheel";

    private static RoboportBlockEntity roboport(GameTestHelper h, BlockPos rel, int logisticRobots) {
        // Covers the full SOUTH-facing footprint for either port, including the one at x=13.
        HandGhostGameTest.floor(h, 0, 15, 0, 15);
        BlockPos abs = h.absolutePos(rel);
        h.assertTrue(Ghosts.place(h.getLevel(), abs, ModItems.ROBOPORT.get(), Direction.SOUTH), "no roboport ghost");
        h.assertTrue(Ghosts.build(h.getLevel(), abs, null, new ItemStack(ModItems.ROBOPORT.get())), "roboport not placed");
        RoboportBlockEntity be = (RoboportBlockEntity) h.getLevel().getBlockEntity(abs);
        be.setCreativePower(true);
        if (logisticRobots > 0) be.robots().setItem(0, new ItemStack(LogisticsRegistry.LOGISTIC_ROBOT.get(), logisticRobots));
        return be;
    }

    private static LogisticChestBlockEntity chest(GameTestHelper h, BlockPos rel, LogisticMode mode, ItemStack... items) {
        h.setBlock(rel, LogisticsRegistry.CHESTS.get(mode).get());
        LogisticChestBlockEntity c = (LogisticChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
        for (int i = 0; i < items.length; i++) c.container().setItem(i, items[i]);
        return c;
    }

    private static ItemStack plates(int n) { return new ItemStack(ModItems.IRON_PLATE.get(), n); }

    private static int count(LogisticChestBlockEntity c, String item) { return c.counts().getOrDefault(item, 0); }

    private static int flying(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(LogisticRobotEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40)).size();
    }

    private static void robotsHome(GameTestHelper h, RoboportBlockEntity port, int robots) {
        h.assertTrue(flying(h) == 0 && port.availableLogisticRobots() == robots && port.robotsOut() == 0,
                "robots not home: " + port.availableLogisticRobots() + " stored, " + flying(h) + " flying, " + port.robotsOut() + " out");
    }

    /**
     * Leaves nothing for later batches: the GameTest server does not clear finished test areas, and
     * an emptied logistic chest or roboport stays put but cannot feed anyone else's robots.
     */
    private static void cleanUp(GameTestHelper h, RoboportBlockEntity... ports) {
        AABB box = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40);
        for (LogisticRobotEntity r : h.getLevel().getEntitiesOfClass(LogisticRobotEntity.class, box)) r.discard();
        for (BlockPos rel : BlockPos.betweenClosed(0, 1, 0, 15, 1, 15)) {
            if (h.getLevel().getBlockEntity(h.absolutePos(rel)) instanceof LogisticChestBlockEntity c) {
                c.container().clearContent();
                c.requests().clear();
            }
        }
        for (RoboportBlockEntity p : ports) {
            p.robots().clearContent();
            p.setCreativePower(false);
        }
    }

    private static ServerPlayer player(GameTestHelper h, BlockPos rel) {
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "logistics-test"));
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.moveTo(h.absolutePos(rel).getBottomCenter());
        LogisticsManager.addExtraPlayer(p);
        return p;
    }

    // --- chests ----------------------------------------------------------------------------------

    @GameTest(template = "empty16", batch = "logistics_requester", timeoutTicks = 1200)
    public static void requesterChestGetsItemsFromAPassiveProvider(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, PORT, 3);
        LogisticChestBlockEntity provider = chest(h, new BlockPos(3, 1, 12), LogisticMode.PASSIVE_PROVIDER, plates(10));
        LogisticChestBlockEntity requester = chest(h, new BlockPos(12, 1, 12), LogisticMode.REQUESTER);
        requester.requests().set(0, new LogisticRequest(PLATE, 4, LogisticStats.UNLIMITED));
        boolean[] launched = {false};
        h.onEachTick(() -> { if (flying(h) > 0) launched[0] = true; });
        h.succeedWhen(() -> {
            h.assertTrue(count(requester, PLATE) == 4, "requester has " + count(requester, PLATE) + ", stored "
                    + port.availableLogisticRobots() + ", flying " + h.getLevel().getEntitiesOfClass(LogisticRobotEntity.class,
                    new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40)).stream().map(r -> r.phase() + "@" + r.blockPosition()).toList()
                    + ", ledger empty " + LogisticsManager.get(h.getLevel()).ledger().isEmpty());
            h.assertTrue(count(provider, PLATE) == 6, "provider has " + count(provider, PLATE));
            h.assertTrue(launched[0], "never saw a logistic robot fly");
            robotsHome(h, port, 3);
            h.assertTrue(LogisticsManager.get(h.getLevel()).ledger().incoming(LogisticsManager.chestId(requester.getBlockPos()), PLATE) == 0,
                    "promises left in the ledger");
            cleanUp(h, port);
        });
    }

    @GameTest(template = "empty16", batch = "logistics_active", timeoutTicks = 1200)
    public static void activeProviderEmptiesIntoStorage(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, PORT, 4);
        LogisticChestBlockEntity active = chest(h, new BlockPos(3, 1, 12), LogisticMode.ACTIVE_PROVIDER, plates(3),
                new ItemStack(ModItems.IRON_GEAR_WHEEL.get(), 2));
        LogisticChestBlockEntity filtered = chest(h, new BlockPos(12, 1, 8), LogisticMode.STORAGE);
        filtered.requests().set(0, new LogisticRequest(GEAR, 0, LogisticStats.UNLIMITED));
        LogisticChestBlockEntity storage = chest(h, new BlockPos(12, 1, 13), LogisticMode.STORAGE);
        // a passive provider is never emptied
        LogisticChestBlockEntity passive = chest(h, new BlockPos(8, 1, 13), LogisticMode.PASSIVE_PROVIDER, plates(2));
        h.succeedWhen(() -> {
            h.assertTrue(active.counts().isEmpty(), "active provider still has " + active.counts());
            h.assertTrue(count(filtered, GEAR) == 2 && count(filtered, PLATE) == 0, "filtered storage has " + filtered.counts());
            h.assertTrue(count(storage, PLATE) == 3, "storage has " + storage.counts());
            h.assertTrue(count(passive, PLATE) == 2, "passive provider was emptied");
            robotsHome(h, port, 4);
            cleanUp(h, port);
        });
    }

    @GameTest(template = "empty16", batch = "logistics_buffer", timeoutTicks = 1600)
    public static void bufferChestFillsAndSuppliesRequestersOnlyWhenAllowed(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, PORT, 4);
        LogisticChestBlockEntity provider = chest(h, new BlockPos(3, 1, 12), LogisticMode.PASSIVE_PROVIDER, plates(3));
        LogisticChestBlockEntity buffer = chest(h, new BlockPos(8, 1, 12), LogisticMode.BUFFER);
        buffer.requests().set(0, new LogisticRequest(PLATE, 3, LogisticStats.UNLIMITED));
        LogisticChestBlockEntity requester = chest(h, new BlockPos(12, 1, 12), LogisticMode.REQUESTER);
        int[] phase = {0};
        h.onEachTick(() -> {
            if (phase[0] == 0 && count(buffer, PLATE) == 3 && flying(h) == 0) {
                // the buffer holds everything: a requester without "request from buffers" gets nothing
                requester.requests().set(0, new LogisticRequest(PLATE, 2, LogisticStats.UNLIMITED));
                phase[0] = 1;
            } else if (phase[0] >= 1 && phase[0] < 100) {
                phase[0]++;
                h.assertTrue(count(requester, PLATE) == 0, "requester took from a buffer without asking");
            } else if (phase[0] == 100) {
                requester.setRequestFromBuffers(true);
                phase[0] = 101;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(phase[0] == 101, "phase " + phase[0]);
            h.assertTrue(count(provider, PLATE) == 0, "provider not emptied into the buffer");
            h.assertTrue(count(requester, PLATE) == 2 && count(buffer, PLATE) == 1,
                    "requester " + count(requester, PLATE) + ", buffer " + count(buffer, PLATE));
            robotsHome(h, port, 4);
            cleanUp(h, port);
        });
    }

    @GameTest(template = "empty16", batch = "logistics_two_ports", timeoutTicks = 1200)
    public static void twoRoboportsWithOverlappingAreasFormOneNetwork(GameTestHelper h) {
        RoboportBlockEntity a = roboport(h, PORT, 1);
        RoboportBlockEntity b = roboport(h, new BlockPos(13, 1, 2), 1);
        chest(h, new BlockPos(3, 1, 12), LogisticMode.PASSIVE_PROVIDER, plates(5));
        LogisticChestBlockEntity requester = chest(h, new BlockPos(12, 1, 12), LogisticMode.REQUESTER);
        requester.requests().set(0, new LogisticRequest(PLATE, 2, LogisticStats.UNLIMITED));
        boolean[] both = {false};
        h.onEachTick(() -> { if (a.robotsOut() > 0 && b.robotsOut() > 0) both[0] = true; });
        h.succeedWhen(() -> {
            // the roboports register with the level once loaded (their first tick)
            List<LogisticsManager.Network> networks = LogisticsManager.networks(h.getLevel());
            LogisticsManager.Network n = LogisticsManager.networkAt(h.getLevel(), a.getBlockPos());
            h.assertTrue(n != null && n.hasPort(a.getBlockPos()) && n.hasPort(b.getBlockPos()),
                    "not one network: " + networks.size() + " networks");
            h.assertTrue(count(requester, PLATE) == 2, "requester has " + count(requester, PLATE));
            h.assertTrue(both[0], "the two roboports never worked for the same request");
            h.assertTrue(flying(h) == 0 && a.availableLogisticRobots() + b.availableLogisticRobots() == 2, "robots not home");
            var stats = LogisticsManager.get(h.getLevel()).stats(h.getLevel(), LogisticsManager.networkAt(h.getLevel(), a.getBlockPos()), 0);
            // (roboports left by earlier test batches nearby may join the network: at least ours)
            h.assertTrue(stats.roboports() >= 2 && stats.logisticTotal() >= 2, "stats " + stats);
            h.assertTrue(stats.items().stream().anyMatch(c -> c.item().equals(PLATE) && c.count() == 3), "network items " + stats.items());
            cleanUp(h, a, b);
        });
    }

    // --- personal logistics -------------------------------------------------------------------------

    @GameTest(template = "empty16", batch = "logistics_personal", timeoutTicks = 1600)
    public static void personalRequestsAreDeliveredAndTrashTakenAway(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, PORT, 4);
        LogisticChestBlockEntity provider = chest(h, new BlockPos(3, 1, 12), LogisticMode.PASSIVE_PROVIDER, plates(10));
        LogisticChestBlockEntity storage = chest(h, new BlockPos(12, 1, 3), LogisticMode.STORAGE);
        ServerPlayer p = player(h, new BlockPos(11, 1, 11));
        p.getInventory().add(new ItemStack(ModItems.IRON_GEAR_WHEEL.get(), 5));
        PersonalLogistics pl = PersonalLogistics.of(p);
        pl.requests().clear();
        pl.requests().set(0, new LogisticRequest(PLATE, 3, LogisticStats.UNLIMITED));
        pl.requests().set(1, new LogisticRequest(GEAR, 0, 2));
        Item plate = ModItems.IRON_PLATE.get(), gear = ModItems.IRON_GEAR_WHEEL.get();
        h.succeedWhen(() -> {
            h.assertTrue(PlayerItems.count(p, plate) == 3, "player has " + PlayerItems.count(p, plate) + " plates");
            h.assertTrue(PlayerItems.count(p, gear) == 2, "player has " + PlayerItems.count(p, gear) + " gears");
            h.assertTrue(count(storage, GEAR) == 3, "storage has " + storage.counts());
            h.assertTrue(count(provider, PLATE) == 7, "provider has " + count(provider, PLATE));
            robotsHome(h, port, 4);
            LogisticsManager.removeExtraPlayer(p);
            cleanUp(h, port);
        });
    }

    @GameTest(template = "empty16", batch = "logistics_personal_away", timeoutTicks = 200)
    public static void playerOutsideTheNetworkIsNotServed(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, PORT, 2);
        chest(h, new BlockPos(3, 1, 12), LogisticMode.PASSIVE_PROVIDER, plates(10));
        ServerPlayer p = player(h, new BlockPos(8, 1, 8));
        p.moveTo(p.getX() + 60, p.getY(), p.getZ()); // well outside the 50×50 area
        PersonalLogistics.of(p).requests().clear();
        PersonalLogistics.of(p).requests().set(0, new LogisticRequest(PLATE, 3, LogisticStats.UNLIMITED));
        h.runAfterDelay(100, () -> {
            h.assertTrue(port.availableLogisticRobots() == 2 && flying(h) == 0, "a robot was sent to a player outside the network");
            LogisticsManager.removeExtraPlayer(p);
            cleanUp(h, port);
            h.succeed();
        });
    }

    // --- personal roboport ----------------------------------------------------------------------------

    @GameTest(template = "empty16", batch = "logistics_personal_roboport", timeoutTicks = 800)
    public static void personalRoboportBuildsAGhostNearThePlayer(GameTestHelper h) {
        HandGhostGameTest.floor(h, 0, 15, 0, 15);
        ServerPlayer p = player(h, new BlockPos(3, 1, 3));
        p.getInventory().add(new ItemStack(LogisticsRegistry.PERSONAL_ROBOPORT.get()));
        p.getInventory().add(new ItemStack(ModItems.CONSTRUCTION_ROBOT.get()));
        p.getInventory().add(new ItemStack(ModItems.INSERTER.get(), 2));
        BlockPos ghost = new BlockPos(11, 1, 11);
        h.assertTrue(Ghosts.place(h.getLevel(), h.absolutePos(ghost), ModItems.INSERTER.get(), Direction.EAST), "no ghost");
        boolean[] launched = {false};
        h.onEachTick(() -> {
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p)); // FakePlayers are not ticked by the server
            if (flying(h) > 0) launched[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertBlockPresent(ModBlocks.INSERTER.get(), ghost);
            h.assertTrue(launched[0], "no robot flew from the player");
            h.assertTrue(flying(h) == 0, "robot still flying");
            h.assertTrue(PlayerItems.count(p, ModItems.CONSTRUCTION_ROBOT.get()) == 1, "robot not back in the inventory");
            h.assertTrue(PlayerItems.count(p, ModItems.INSERTER.get()) == 1, "inserter not taken from the inventory");
            LogisticsManager.removeExtraPlayer(p);
        });
    }

    // --- robots and chunks that do not tick ---------------------------------------------------

    /**
     * A requester chest in the network but in a loaded chunk that does not entity-tick gets nothing:
     * a robot flying there would freeze holding its promise. The requester inside the test area is
     * served and every robot comes home with no promise left.
     */
    // The batch cleanup also runs after a failed setup or assertion.
    private static net.minecraft.world.level.ChunkPos isolatedTicket;
    private static BlockPos isolatedPort, isolatedProvider, isolatedInside, isolatedOutside;

    @net.minecraft.gametest.framework.AfterBatch(batch = "logistics_non_ticking")
    public static void cleanNonTickingFixture(net.minecraft.server.level.ServerLevel level) {
        if (isolatedPort != null) {
            for (LogisticRobotEntity robot : level.getEntitiesOfClass(LogisticRobotEntity.class,
                    new AABB(isolatedPort).inflate(40))) robot.discard();
            for (BlockPos p : new BlockPos[] {isolatedOutside, isolatedInside, isolatedProvider, isolatedPort}) {
                if (p == null || !level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
                if (level.getBlockEntity(p) instanceof LogisticChestBlockEntity chest) {
                    chest.requests().clear(); chest.container().clearContent();
                }
                if (level.getBlockEntity(p) instanceof RoboportBlockEntity port) {
                    port.robots().clearContent(); port.setCreativePower(false);
                }
                level.removeBlock(p, false);
            }
        }
        if (isolatedTicket != null) level.setChunkForced(isolatedTicket.x, isolatedTicket.z, false);
        isolatedTicket = null;
        isolatedPort = isolatedProvider = isolatedInside = isolatedOutside = null;
    }

    @GameTest(template = "empty16", batch = "logistics_non_ticking", timeoutTicks = 800)
    public static void requesterInANonTickingChunkIsNotServed(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos template = h.absolutePos(BlockPos.ZERO);
        // Keep this fixture far from every GameTest ticket; force only the port chunk. A chunk two
        // steps away is FULL-loaded by the force ticket but outside its entity-ticking radius.
        int cx = (template.getX() >> 4) + 65_536, cz = template.getZ() >> 4;
        isolatedTicket = new net.minecraft.world.level.ChunkPos(cx, cz);
        int y = 80; // Above the generated flat terrain, independent of the template floor.
        isolatedPort = new BlockPos((cx << 4) + 14, y, (cz << 4) + 8);
        isolatedProvider = isolatedPort.offset(-6, 0, 0);
        isolatedInside = isolatedPort.offset(-6, 0, 3);
        isolatedOutside = new BlockPos((cx + 2) << 4, y, (cz << 4) + 8);
        level.setChunkForced(cx, cz, true);
        level.getChunk(cx, cz);
        int[] age = {0};
        boolean[] ready = {false}, promised = {false}, ticking = {false}, unloaded = {false};
        h.onEachTick(() -> {
            if (!ready[0]) {
                h.assertTrue(++age[0] < 400, "isolated roboport chunk did not entity-tick");
                if (!level.isPositionEntityTicking(isolatedPort)) return;
                level.getChunk(cx + 2, cz);
                h.assertTrue(level.hasChunk(cx + 2, cz) && !level.isPositionEntityTicking(isolatedOutside),
                        "isolated requester chunk is not loaded and non-ticking");
                for (int dx = -4; dx <= 1; dx++) for (int dz = -4; dz <= 4; dz++)
                    level.setBlock(isolatedPort.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                h.assertTrue(Ghosts.place(level, isolatedPort, ModItems.ROBOPORT.get(), Direction.SOUTH), "no isolated roboport ghost");
                h.assertTrue(Ghosts.build(level, isolatedPort, null, new ItemStack(ModItems.ROBOPORT.get())), "isolated roboport not placed");
                RoboportBlockEntity port = (RoboportBlockEntity) level.getBlockEntity(isolatedPort);
                port.setCreativePower(true);
                port.robots().setItem(0, new ItemStack(LogisticsRegistry.LOGISTIC_ROBOT.get(), 2));
                level.setBlock(isolatedProvider, LogisticsRegistry.CHESTS.get(LogisticMode.PASSIVE_PROVIDER).get().defaultBlockState(), 3);
                level.setBlock(isolatedInside, LogisticsRegistry.CHESTS.get(LogisticMode.REQUESTER).get().defaultBlockState(), 3);
                level.setBlock(isolatedOutside, LogisticsRegistry.CHESTS.get(LogisticMode.REQUESTER).get().defaultBlockState(), 3);
                var provider = (LogisticChestBlockEntity) level.getBlockEntity(isolatedProvider);
                var inside = (LogisticChestBlockEntity) level.getBlockEntity(isolatedInside);
                var outside = (LogisticChestBlockEntity) level.getBlockEntity(isolatedOutside);
                provider.container().setItem(0, plates(10));
                inside.requests().set(0, new LogisticRequest(PLATE, 3, LogisticStats.UNLIMITED));
                outside.requests().set(0, new LogisticRequest(PLATE, 5, LogisticStats.UNLIMITED));
                var network = LogisticsManager.logisticArea(port);
                for (BlockPos chest : new BlockPos[] {isolatedProvider, isolatedInside, isolatedOutside})
                    h.assertTrue(network.contains(chest.getX()+0.5, y+0.5, chest.getZ()+0.5), "chest outside logistic area");
                ready[0] = true;
                age[0] = 0;
                return;
            }
            promised[0] |= LogisticsManager.get(level).ledger().incoming(LogisticsManager.chestId(isolatedOutside), PLATE) > 0;
            ticking[0] |= level.isPositionEntityTicking(isolatedOutside);
            unloaded[0] |= !level.hasChunk(cx + 2, cz);
            if (++age[0] < 200) return;
            var inside = (LogisticChestBlockEntity) level.getBlockEntity(isolatedInside);
            var outside = (LogisticChestBlockEntity) level.getBlockEntity(isolatedOutside);
            var provider = (LogisticChestBlockEntity) level.getBlockEntity(isolatedProvider);
            var port = (RoboportBlockEntity) level.getBlockEntity(isolatedPort);
            h.assertTrue(count(inside, PLATE) == 3, "inside requester has " + count(inside, PLATE));
            h.assertFalse(promised[0], "a robot was sent to the non-ticking chunk");
            h.assertFalse(ticking[0], "outside chunk became entity-ticking");
            h.assertFalse(unloaded[0], "outside chunk became unloaded");
            h.assertTrue(count(outside, PLATE) == 0, "outside requester has " + count(outside, PLATE));
            h.assertTrue(count(provider, PLATE) == 7, "provider has " + count(provider, PLATE));
            int flying = level.getEntitiesOfClass(LogisticRobotEntity.class, new AABB(isolatedPort).inflate(40)).size();
            h.assertTrue(flying == 0 && port.availableLogisticRobots() == 2 && port.robotsOut() == 0, "robots not home");
            h.succeed();
        });
    }

    @GameTest(template = "empty16")
    public static void logisticChestsStoreFortyEightStacks(GameTestHelper h) {
        int x = 2;
        for (LogisticMode mode : LogisticMode.values()) {
            var be = chest(h, new BlockPos(x++, 1, 3), mode);
            h.assertTrue(be.container().getContainerSize() == 48, "wrong capacity " + mode);
            be.container().setItem(47, plates(7));
            var tag = be.saveWithoutMetadata(h.getLevel().registryAccess());
            be.container().clearContent();
            be.loadWithComponents(tag, h.getLevel().registryAccess());
            h.assertTrue(be.container().getItem(47).getCount() == 7, "last slot did not survive save " + mode);
        }
        h.succeed();
    }

}
