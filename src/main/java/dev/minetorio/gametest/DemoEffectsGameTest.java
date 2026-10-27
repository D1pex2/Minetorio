package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.place;
import static dev.minetorio.gametest.InserterGameTest.count;
import static dev.minetorio.gametest.InserterGameTest.handler;

import com.mojang.authlib.GameProfile;
import dev.minetorio.content.belt.LaneSide;
import dev.minetorio.content.demo.DemoSavedData;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.research.ResearchSavedData;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The demo's endless chests and research autopilot act only on what the demo placed and stop when
 * a player takes over. The demo data and research are per world, so each test has its own batch and
 * everything is cleared afterwards.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class DemoEffectsGameTest {
    private static final BlockPos SOURCE = new BlockPos(2, 1, 2);
    private static final BlockPos SINK = new BlockPos(4, 1, 2);
    private static final BlockPos TRIM = new BlockPos(6, 1, 2);
    private static final BlockPos BELT = new BlockPos(8, 1, 2);

    @AfterBatch(batch = "demo_chests")
    public static void afterChests(ServerLevel level) { reset(level); }

    @AfterBatch(batch = "demo_autopilot")
    public static void afterAutopilot(ServerLevel level) { reset(level); }

    private static void reset(ServerLevel level) {
        DemoSavedData.get(level).clear();
        ResearchSavedData.get(level.getServer()).restore(Set.of(), null, Map.of());
    }

    private static void tagged(GameTestHelper h, BlockPos rel, String name, ItemStack... contents) {
        h.setBlock(rel, Blocks.CHEST);
        DemoSavedData.tag((ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel)), name); // before filling: tagging resets the contents
        var handler = handler(h, rel);
        for (ItemStack s : contents) ItemHandlerHelper.insertItemStacked(handler, s.copy(), false);
    }

    private static int items(GameTestHelper h, BlockPos rel) {
        var handler = handler(h, rel);
        int n = 0;
        for (int i = 0; i < handler.getSlots(); i++) n += handler.getStackInSlot(i).getCount();
        return n;
    }

    /**
     * Demo chests are refilled, emptied and trimmed, a feed fills its belt. Once a chest is replaced
     * by a plain one (or the belt by stone) the entry is dropped: the new chest is left alone.
     */
    @GameTest(template = "empty16", batch = "demo_chests", timeoutTicks = 100)
    public static void demoChestsOnlyTheirOwn(GameTestHelper h) {
        DemoSavedData data = DemoSavedData.get(h.getLevel());
        tagged(h, SOURCE, DemoSavedData.SOURCE_NAME);
        data.addSource(h.absolutePos(SOURCE), "minetorio:iron_plate", 10);
        tagged(h, SINK, DemoSavedData.SINK_NAME, new ItemStack(Items.STONE, 5));
        data.addSink(h.absolutePos(SINK), 0);
        tagged(h, TRIM, DemoSavedData.SINK_NAME, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 64));
        data.addSink(h.absolutePos(TRIM), 64);
        place(h, ModItems.TRANSPORT_BELT.get(), BELT, Direction.EAST);
        data.addFeed(h.absolutePos(BELT), "minetorio:iron_plate", null);

        h.runAtTickTime(25, () -> {
            h.assertTrue(count(h, SOURCE, ModItems.IRON_PLATE.get()) == 10, "source has " + count(h, SOURCE, ModItems.IRON_PLATE.get()));
            h.assertTrue(items(h, SINK) == 0, "sink not emptied");
            h.assertTrue(items(h, TRIM) == 64, "trim chest has " + items(h, TRIM));
            h.assertTrue(!BeltGameTest.belt(h, BELT).lane(LaneSide.LEFT).isEmpty(), "belt not fed");
            // a player replaces the chests with plain ones and the belt with stone
            h.setBlock(SOURCE, Blocks.AIR);
            h.setBlock(SINK, Blocks.AIR);
            InserterGameTest.container(h, Blocks.CHEST, SOURCE);
            InserterGameTest.container(h, Blocks.CHEST, SINK, new ItemStack(Items.STONE, 5));
            h.setBlock(BELT, Blocks.STONE);
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(items(h, SOURCE) == 0, "plain chest refilled");
            h.assertTrue(count(h, SINK, Items.STONE) == 5, "plain chest emptied");
            h.assertTrue(data.sources().stream().noneMatch(s -> s.pos().equals(h.absolutePos(SOURCE))), "source entry kept");
            h.assertTrue(data.sinks().stream().noneMatch(s -> s.pos().equals(h.absolutePos(SINK))), "sink entry kept");
            h.assertTrue(data.sinks().stream().anyMatch(s -> s.pos().equals(h.absolutePos(TRIM))), "untouched sink entry dropped");
            h.assertTrue(data.feeds().stream().noneMatch(f -> f.pos().equals(h.absolutePos(BELT))), "feed entry kept");
            h.succeed();
        });
    }

    /** The autopilot picks automation; a player's research choice turns it off for good. */
    @GameTest(template = "empty16", batch = "demo_autopilot", timeoutTicks = 100)
    public static void playerResearchStopsAutopilot(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ResearchSavedData research = ResearchSavedData.get(level.getServer());
        research.restore(Set.of(), null, Map.of());
        DemoSavedData data = DemoSavedData.get(level);
        data.setResearch(true);
        h.runAtTickTime(25, () -> {
            h.assertTrue(research.state().current().map(t -> t.id().equals("automation")).orElse(false),
                    "autopilot did not select automation: " + research.state().current());
            FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "demo-test"));
            h.assertTrue(ResearchSavedData.select(player, "logistics") == ResearchSavedData.SelectResult.OK, "selection refused");
            h.assertFalse(data.research(), "autopilot still on");
            research.restore(Set.of(), null, Map.of());
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(research.state().current().isEmpty(), "autopilot selected " + research.state().current());
            h.succeed();
        });
    }
}
