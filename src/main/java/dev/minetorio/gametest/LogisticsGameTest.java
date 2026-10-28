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
    @GameTest(template = "empty16", batch = "logistics_non_ticking", timeoutTicks = 800)
    public static void requesterInANonTickingChunkIsNotServed(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, PORT, 2);
        LogisticChestBlockEntity provider = chest(h, new BlockPos(8, 1, 7), LogisticMode.PASSIVE_PROVIDER, plates(10));
        LogisticChestBlockEntity inside = chest(h, new BlockPos(12, 1, 12), LogisticMode.REQUESTER);
        inside.requests().set(0, new LogisticRequest(PLATE, 3, LogisticStats.UNLIMITED));
        BlockPos chestAbs = h.absolutePos(new BlockPos(8, 1, 7));
        BlockPos away = null;
        var network = LogisticsManager.logisticArea(port);
        h.assertTrue(network.contains(chestAbs.getX() + 0.5, chestAbs.getY() + 0.5, chestAbs.getZ() + 0.5), "provider outside the logistic area");
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int cx = (chestAbs.getX() >> 4) + d[0], cz = (chestAbs.getZ() >> 4) + d[1];
            BlockPos mid = new BlockPos((cx << 4) + 8, chestAbs.getY(), (cz << 4) + 8);
            if (h.getLevel().hasChunk(cx, cz) && !h.getLevel().isPositionEntityTicking(mid) && network.contains(mid.getX() + 0.5, mid.getY() + 0.5, mid.getZ() + 0.5)) {
                away = mid;
                break;
            }
        }
        h.assertTrue(away != null, "no loaded, non-ticking chunk in the network next to the test area");
        BlockPos awayPos = away;
        h.getLevel().setBlock(awayPos, LogisticsRegistry.CHESTS.get(LogisticMode.REQUESTER).get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        LogisticChestBlockEntity outside = (LogisticChestBlockEntity) h.getLevel().getBlockEntity(awayPos);
        outside.requests().set(0, new LogisticRequest(PLATE, 5, LogisticStats.UNLIMITED));
        String outsideId = LogisticsManager.chestId(awayPos);
        boolean[] promised = {false};
        h.onEachTick(() -> {
            if (LogisticsManager.get(h.getLevel()).ledger().incoming(outsideId, PLATE) > 0) promised[0] = true;
        });
        h.runAfterDelay(200, () -> h.succeedWhen(() -> {
            h.assertTrue(count(inside, PLATE) == 3, "inside requester has " + count(inside, PLATE));
            h.assertFalse(promised[0], "a robot was sent to the non-ticking chunk");
            h.assertTrue(count(outside, PLATE) == 0, "outside requester has " + count(outside, PLATE));
            h.assertTrue(count(provider, PLATE) == 7, "provider has " + count(provider, PLATE));
            robotsHome(h, port, 2);
            outside.requests().clear();
            h.getLevel().removeBlock(awayPos, false);
            cleanUp(h, port);
        }));
    }
}
