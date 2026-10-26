package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.dropped;
import static dev.minetorio.gametest.MachineGameTest.named;
import static dev.minetorio.gametest.MachineGameTest.namedInInventory;
import static dev.minetorio.gametest.MachineGameTest.player;

import dev.minetorio.content.handcraft.HandcraftAttachment;
import dev.minetorio.content.handcraft.HandcraftPayloads;
import dev.minetorio.content.handcraft.HandcraftTicker.EnqueueResult;
import dev.minetorio.content.handcraft.HandcraftTicker;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.research.LabBlockEntity;
import dev.minetorio.content.research.ResearchSavedData;
import dev.minetorio.core.handcraft.HandcraftQueue;
import dev.minetorio.core.research.LabLogic;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Hand crafting. Players are FakePlayers (as in the other tests): a mock player from
 * {@code makeMockServerPlayerInLevel} has no negotiated mod channels, so any mod payload sent to it
 * throws. FakePlayers are not ticked by the server, so the tests post {@link PlayerTickEvent.Post}
 * themselves — the same listener the real players' ticks reach.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class HandcraftGameTest {
    private static final BlockPos AT = new BlockPos(3, 1, 3);
    private static final BlockPos AT_2 = new BlockPos(8, 1, 8);

    @AfterBatch(batch = "handcraft_research")
    public static void after0(ServerLevel level) { clear(level); }

    @AfterBatch(batch = "handcraft_locks")
    public static void after1(ServerLevel level) { clear(level); }

    private static void clear(ServerLevel level) {
        ResearchSavedData.get(level.getServer()).restore(Set.of(), null, Map.of());
    }

    private static void tick(ServerPlayer p, int n) {
        for (int i = 0; i < n; i++) NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p));
    }

    /** Plain (component-free) items of this kind in the player's main inventory. */
    private static int plain(Player p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item) && s.getComponentsPatch().isEmpty()) n += s.getCount();
        return n;
    }

    private static void give(Player p, Item item, int n) {
        p.getInventory().add(new ItemStack(item, n));
    }

    private static List<HandcraftQueue.Entry> entries(Player p) {
        return HandcraftAttachment.of(p).queue().entries();
    }

    /** A payload context whose only answer is the sending player (what the handlers use). */
    private static IPayloadContext context(ServerPlayer p) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(), new Class<?>[]{IPayloadContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("player")) return p;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /** 2 iron plates → one gear 10 ticks (0.5 s) after the enqueue. */
    @GameTest(template = "empty16")
    public static void handcraftGear(GameTestHelper h) {
        FakePlayer p = player(h, AT);
        give(p, ModItems.IRON_PLATE.get(), 2);
        HandcraftPayloads.Enqueue.handle(new HandcraftPayloads.Enqueue("iron_gear_wheel", 1), context(p));
        h.assertTrue(entries(p).size() == 1, "not queued: " + entries(p));
        h.assertTrue(plain(p, ModItems.IRON_PLATE.get()) == 0, "plates not taken at enqueue");
        tick(p, 9);
        h.assertTrue(plain(p, ModItems.IRON_GEAR_WHEEL.get()) == 0, "gear early");
        h.assertTrue(Math.abs(entries(p).get(0).progress() - 0.9) < 1e-9, "progress " + entries(p));
        tick(p, 1);
        h.assertTrue(plain(p, ModItems.IRON_GEAR_WHEEL.get()) == 1, "no gear after 10 ticks");
        h.assertTrue(entries(p).isEmpty(), "queue not empty: " + entries(p));
        h.succeed();
    }

    /** From plates alone, an inserter is crafted with its cables, circuit and gear as sub-steps. */
    @GameTest(template = "empty16")
    public static void handcraftResolvesIntermediates(GameTestHelper h) {
        FakePlayer p = player(h, AT);
        give(p, ModItems.IRON_PLATE.get(), 4);
        give(p, ModItems.COPPER_PLATE.get(), 2);
        h.assertTrue(HandcraftTicker.enqueue(p, "inserter", 1) == EnqueueResult.OK, "inserter refused");
        h.assertTrue(plain(p, ModItems.IRON_PLATE.get()) == 0 && plain(p, ModItems.COPPER_PLATE.get()) == 0, "plates left");
        tick(p, 49);
        h.assertTrue(plain(p, ModItems.INSERTER.get()) == 0, "inserter early");
        tick(p, 1);
        h.assertTrue(plain(p, ModItems.INSERTER.get()) == 1, "no inserter after 50 ticks");
        h.assertTrue(plain(p, ModItems.COPPER_CABLE.get()) == 1, "spare cable " + plain(p, ModItems.COPPER_CABLE.get()));
        h.assertTrue(plain(p, ModItems.ELECTRONIC_CIRCUIT.get()) == 0 && plain(p, ModItems.IRON_GEAR_WHEEL.get()) == 0,
                "intermediates leaked");
        h.assertTrue(entries(p).isEmpty(), "queue not empty");
        h.succeed();
    }

    /** Each player has a queue of their own; one's crafting never touches the other's inventory. */
    @GameTest(template = "empty16")
    public static void twoPlayersSeparateQueues(GameTestHelper h) {
        FakePlayer a = player(h, AT);
        FakePlayer b = player(h, AT_2);
        give(a, ModItems.IRON_PLATE.get(), 2);
        give(b, ModItems.IRON_PLATE.get(), 3);
        h.assertTrue(HandcraftTicker.enqueue(a, "iron_gear_wheel", 1) == EnqueueResult.OK, "A refused");
        h.assertTrue(entries(b).isEmpty(), "B got A's entry");
        h.assertTrue(plain(b, ModItems.IRON_PLATE.get()) == 3, "B's plates touched");
        tick(a, 5);
        h.assertTrue(HandcraftTicker.enqueue(b, "pipe", 2) == EnqueueResult.OK, "B refused");
        h.assertTrue(plain(b, ModItems.IRON_PLATE.get()) == 1 && plain(a, ModItems.IRON_PLATE.get()) == 0, "wrong plates taken");
        tick(a, 5);
        h.assertTrue(plain(a, ModItems.IRON_GEAR_WHEEL.get()) == 1 && plain(b, ModItems.IRON_GEAR_WHEEL.get()) == 0, "gear went wrong");
        h.assertTrue(entries(b).size() == 1 && entries(b).get(0).progress() == 0, "B's queue advanced by A's ticks");
        tick(b, 20);
        h.assertTrue(plain(b, ModItems.PIPE.get()) == 2 && plain(a, ModItems.PIPE.get()) == 0, "pipes went wrong");
        h.assertTrue(entries(a).isEmpty() && entries(b).isEmpty(), "queues not empty");
        h.succeed();
    }

    /** Research is the world's: once player A has researched automation, player B may queue assembling_machine_1. */
    @GameTest(template = "empty16", batch = "handcraft_research")
    public static void researchSharedBetweenPlayers(GameTestHelper h) {
        ResearchSavedData data = ResearchSavedData.get(h.getLevel().getServer());
        data.restore(Set.of(), null, Map.of());
        FakePlayer a = player(h, AT);
        FakePlayer b = player(h, AT_2);
        give(b, ModItems.IRON_PLATE.get(), 22);
        give(b, ModItems.COPPER_PLATE.get(), 5);
        h.assertTrue(HandcraftTicker.enqueue(b, "assembling_machine_1", 1) == EnqueueResult.LOCKED, "locked recipe queued");
        h.assertTrue(plain(b, ModItems.IRON_PLATE.get()) == 22, "plates taken by a refused enqueue");
        h.assertTrue(ResearchSavedData.select(a, "automation") == ResearchSavedData.SelectResult.OK, "A cannot research");
        // A's lab researches automation unit by unit through the world's research (10 units × 10 s)
        LabLogic lab = new LabLogic(1.0, LabBlockEntity.POWER);
        h.assertTrue(lab.insertPack("minetorio:automation_science_pack", 10, false) == 10, "packs refused");
        for (int i = 0; i < 5000 && !data.state().isResearched("automation"); i++) data.tickLab(lab, 1.0);
        h.assertTrue(data.state().isResearched("automation"), "automation not researched, units " + data.state().unitsDone());
        h.assertTrue(HandcraftTicker.enqueue(b, "assembling_machine_1", 1) == EnqueueResult.OK, "B refused after A's research");
        h.assertTrue(plain(b, ModItems.IRON_PLATE.get()) == 0 && plain(b, ModItems.COPPER_PLATE.get()) == 0, "plates left");
        tick(b, 400);
        h.assertTrue(plain(b, ModItems.ASSEMBLING_MACHINE_1.get()) == 1, "no assembler");
        h.succeed();
    }

    /** The queue is part of the player's saved data: save → load keeps the entries and the progress. */
    @GameTest(template = "empty16")
    public static void queueSurvivesRelog(GameTestHelper h) {
        FakePlayer p = player(h, AT);
        give(p, ModItems.IRON_PLATE.get(), 5);
        h.assertTrue(HandcraftTicker.enqueue(p, "iron_gear_wheel", 2) == EnqueueResult.OK, "gears refused");
        h.assertTrue(HandcraftTicker.enqueue(p, "pipe", 1) == EnqueueResult.OK, "pipe refused");
        tick(p, 13);
        List<HandcraftQueue.Entry> before = entries(p);
        h.assertTrue(before.size() == 2 && Math.abs(before.get(0).progress() - 0.3) < 1e-9, "before " + before);

        CompoundTag saved = p.saveWithoutId(new CompoundTag());
        FakePlayer back = player(h, AT_2);
        back.load(saved);
        h.assertTrue(entries(back).equals(before), "after load " + entries(back) + " vs " + before);
        h.assertTrue(plain(back, ModItems.IRON_GEAR_WHEEL.get()) == 1, "first gear lost");
        tick(back, 7);
        h.assertTrue(plain(back, ModItems.IRON_GEAR_WHEEL.get()) == 2, "second gear not finished after load");
        tick(back, 10);
        h.assertTrue(plain(back, ModItems.PIPE.get()) == 1 && entries(back).isEmpty(), "pipe not finished after load");
        h.succeed();
    }

    /** A saved entry whose recipe no longer exists is not loaded; the items it held are given back on the next tick. */
    @GameTest(template = "empty16")
    public static void unknownSavedRecipeIsRefunded(GameTestHelper h) {
        FakePlayer p = player(h, AT);
        give(p, ModItems.IRON_PLATE.get(), 3);
        h.assertTrue(HandcraftTicker.enqueue(p, "iron_gear_wheel", 1) == EnqueueResult.OK, "gear refused");
        h.assertTrue(HandcraftTicker.enqueue(p, "pipe", 1) == EnqueueResult.OK, "pipe refused");
        CompoundTag saved = p.saveWithoutId(new CompoundTag());
        CompoundTag queue = saved.getCompound("neoforge:attachments").getCompound("minetorio:handcraft");
        h.assertTrue(!queue.isEmpty(), "queue not saved: " + saved.getCompound("neoforge:attachments"));
        CompoundTag first = queue.getList("entries", Tag.TAG_COMPOUND).getCompound(0);
        first.putString("recipe", "removed_recipe");
        first.getList("steps", Tag.TAG_STRING).set(0, StringTag.valueOf("removed_recipe"));
        FakePlayer back = player(h, AT_2);
        back.load(saved);
        h.assertTrue(entries(back).size() == 1 && entries(back).get(0).recipeId().equals("pipe"), "entries " + entries(back));
        tick(back, 1);
        h.assertTrue(plain(back, ModItems.IRON_PLATE.get()) == 2, "gear's plates not refunded: " + plain(back, ModItems.IRON_PLATE.get()));
        tick(back, 9);
        h.assertTrue(plain(back, ModItems.PIPE.get()) == 1, "pipe not crafted");
        h.succeed();
    }

    /** Only plain stacks count as ingredients: named plates are never consumed. */
    @GameTest(template = "empty16")
    public static void namedItemsAreNotConsumed(GameTestHelper h) {
        FakePlayer p = player(h, AT);
        p.getInventory().add(named(ModItems.IRON_PLATE.get(), 4));
        h.assertTrue(HandcraftTicker.enqueue(p, "iron_gear_wheel", 1) == EnqueueResult.MISSING_ITEMS, "named plates used");
        give(p, ModItems.IRON_PLATE.get(), 2);
        h.assertTrue(HandcraftTicker.enqueue(p, "iron_gear_wheel", 2) == EnqueueResult.MISSING_ITEMS, "named plates counted");
        h.assertTrue(HandcraftTicker.enqueue(p, "iron_gear_wheel", 1) == EnqueueResult.OK, "plain plates refused");
        h.assertTrue(namedInInventory(p) == 4 && plain(p, ModItems.IRON_PLATE.get()) == 0, "wrong plates taken");
        tick(p, 10);
        h.assertTrue(plain(p, ModItems.IRON_GEAR_WHEEL.get()) == 1 && namedInInventory(p) == 4, "named plates touched");
        h.succeed();
    }

    /** Cancelling with a full inventory drops what does not fit at the player's feet: nothing is lost. */
    @GameTest(template = "empty16")
    public static void cancelWithFullInventoryDropsOverflow(GameTestHelper h) {
        FakePlayer p = player(h, AT);
        give(p, ModItems.IRON_PLATE.get(), 2);
        give(p, ModItems.COPPER_PLATE.get(), 1);
        h.assertTrue(HandcraftTicker.enqueue(p, "automation_science_pack", 1) == EnqueueResult.OK, "pack refused");
        tick(p, 3);
        for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        HandcraftPayloads.Cancel.handle(new HandcraftPayloads.Cancel(0, "automation_science_pack", 2), context(p));
        HandcraftPayloads.Cancel.handle(new HandcraftPayloads.Cancel(0, "iron_gear_wheel", 1), context(p));
        h.assertTrue(entries(p).size() == 1, "an entry other than the one the client saw was cancelled");
        HandcraftPayloads.Cancel.handle(new HandcraftPayloads.Cancel(0, "automation_science_pack", 1), context(p));
        h.assertTrue(entries(p).isEmpty(), "entry not cancelled");
        h.assertTrue(dropped(h, ModItems.IRON_PLATE.get()) == 2, "iron dropped " + dropped(h, ModItems.IRON_PLATE.get()));
        h.assertTrue(dropped(h, ModItems.COPPER_PLATE.get()) == 1, "copper dropped " + dropped(h, ModItems.COPPER_PLATE.get()));
        h.succeed();
    }

    /** Enqueue payloads are validated: locked, unknown and smelting recipes and bad counts change nothing. */
    @GameTest(template = "empty16", batch = "handcraft_locks")
    public static void lockedRecipeRejectedByPayload(GameTestHelper h) {
        ResearchSavedData.get(h.getLevel().getServer()).restore(Set.of(), null, Map.of());
        FakePlayer p = player(h, AT);
        give(p, ModItems.IRON_PLATE.get(), 30);
        give(p, ModItems.COPPER_PLATE.get(), 10);
        List<HandcraftPayloads.Enqueue> bad = new ArrayList<>(List.of(
                new HandcraftPayloads.Enqueue("assembling_machine_1", 1),
                new HandcraftPayloads.Enqueue("fast_inserter", 1),
                new HandcraftPayloads.Enqueue("no_such_recipe", 1),
                new HandcraftPayloads.Enqueue("iron_plate", 1),
                new HandcraftPayloads.Enqueue("pipe", 0),
                new HandcraftPayloads.Enqueue("pipe", -3),
                new HandcraftPayloads.Enqueue("pipe", 1001)));
        for (HandcraftPayloads.Enqueue e : bad) HandcraftPayloads.Enqueue.handle(e, context(p));
        h.assertTrue(entries(p).isEmpty(), "queued " + entries(p));
        h.assertTrue(plain(p, ModItems.IRON_PLATE.get()) == 30 && plain(p, ModItems.COPPER_PLATE.get()) == 10, "items taken");
        h.assertTrue(HandcraftTicker.enqueue(p, "assembling_machine_1", 1) == EnqueueResult.LOCKED, "not LOCKED");
        h.assertTrue(HandcraftTicker.enqueue(p, "no_such_recipe", 1) == EnqueueResult.UNKNOWN_RECIPE, "not UNKNOWN_RECIPE");
        h.assertTrue(HandcraftTicker.enqueue(p, "iron_plate", 1) == EnqueueResult.NOT_CRAFTING, "not NOT_CRAFTING");
        h.assertTrue(HandcraftTicker.enqueue(p, "pipe", 1001) == EnqueueResult.BAD_COUNT, "not BAD_COUNT");
        h.succeed();
    }

    /** Spectators cannot queue; a player may send at most 10 queue requests per second. */
    @GameTest(template = "empty16")
    public static void spectatorAndRateLimitRejected(GameTestHelper h) {
        FakePlayer p = player(h, AT);
        give(p, ModItems.IRON_PLATE.get(), 64);
        p.setGameMode(GameType.SPECTATOR);
        h.assertTrue(HandcraftTicker.enqueue(p, "pipe", 1) == EnqueueResult.SPECTATOR, "spectator queued");
        h.assertTrue(entries(p).isEmpty() && plain(p, ModItems.IRON_PLATE.get()) == 64, "spectator changed something");
        p.setGameMode(GameType.SURVIVAL);
        for (int i = 0; i < HandcraftTicker.MAX_REQUESTS_PER_SECOND; i++) {
            h.assertTrue(HandcraftTicker.enqueue(p, "pipe", 1) == EnqueueResult.OK, "request " + i + " refused");
        }
        h.assertTrue(HandcraftTicker.enqueue(p, "pipe", 1) == EnqueueResult.TOO_SOON, "rate limit not applied");
        h.assertTrue(entries(p).size() == HandcraftTicker.MAX_REQUESTS_PER_SECOND, "entries " + entries(p).size());
        FakePlayer other = player(h, AT_2);
        give(other, ModItems.IRON_PLATE.get(), 1);
        h.assertTrue(HandcraftTicker.enqueue(other, "pipe", 1) == EnqueueResult.OK, "other player limited");
        h.runAfterDelay(HandcraftTicker.RATE_WINDOW_TICKS, () -> {
            h.assertTrue(HandcraftTicker.enqueue(p, "pipe", 1) == EnqueueResult.OK, "still limited after the window");
            h.succeed();
        });
    }

    /** On death (a real LivingDeathEvent) every entry is cancelled and its items go back to the inventory (to drop with it). */
    @GameTest(template = "empty16")
    public static void deathCancelsQueue(GameTestHelper h) {
        FakePlayer p = player(h, AT);
        give(p, ModItems.IRON_PLATE.get(), 4);
        give(p, ModItems.COPPER_PLATE.get(), 2);
        h.assertTrue(HandcraftTicker.enqueue(p, "inserter", 1) == EnqueueResult.OK, "inserter refused");
        h.assertTrue(HandcraftTicker.enqueue(p, "pipe", 1) == EnqueueResult.MISSING_ITEMS, "pipe from nothing");
        tick(p, 25);
        die(p);
        h.assertTrue(entries(p).isEmpty(), "queue survived death");
        h.assertTrue(plain(p, ModItems.IRON_PLATE.get()) == 4 && plain(p, ModItems.COPPER_PLATE.get()) == 2,
                "refund " + plain(p, ModItems.IRON_PLATE.get()) + " iron, " + plain(p, ModItems.COPPER_PLATE.get()) + " copper");
        h.assertTrue(plain(p, ModItems.COPPER_CABLE.get()) == 0 && plain(p, ModItems.ELECTRONIC_CIRCUIT.get()) == 0, "intermediates kept");
        h.succeed();
    }

    /** A real LivingDeathEvent reaches the handcraft listener (FakePlayer.die does nothing, so the test posts it). */
    private static void die(ServerPlayer p) {
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(p, p.damageSources().genericKill()));
    }

    /** Dying with a full inventory: what does not fit drops at the death position, nothing is lost. */
    @GameTest(template = "empty16")
    public static void deathWithFullInventoryDropsOverflow(GameTestHelper h) {
        FakePlayer p = player(h, AT);
        give(p, ModItems.IRON_PLATE.get(), 4);
        give(p, ModItems.COPPER_PLATE.get(), 2);
        h.assertTrue(HandcraftTicker.enqueue(p, "inserter", 1) == EnqueueResult.OK, "inserter refused");
        tick(p, 25);
        for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        die(p);
        h.assertTrue(entries(p).isEmpty(), "queue survived death");
        AABB near = p.getBoundingBox().inflate(1.5);
        int iron = 0;
        int copper = 0;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, near)) {
            if (e.getItem().is(ModItems.IRON_PLATE.get())) iron += e.getItem().getCount();
            if (e.getItem().is(ModItems.COPPER_PLATE.get())) copper += e.getItem().getCount();
        }
        h.assertTrue(iron == 4 && copper == 2, "dropped at the death position: " + iron + " iron, " + copper + " copper");
        h.assertTrue(dropped(h, ModItems.COPPER_CABLE.get()) == 0 && dropped(h, ModItems.ELECTRONIC_CIRCUIT.get()) == 0, "intermediates dropped");
        h.succeed();
    }

    @AfterBatch(batch = "handcraft_dead")
    public static void after2(ServerLevel level) {
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, level.getServer());
    }

    /**
     * With keepInventory, a request that arrives after death (in flight, or a modified client on the
     * death screen) is refused: the queue is not copied to the respawned player, so taking the
     * ingredients would lose them. A dead player's queue does not tick and cancels nothing.
     */
    @GameTest(template = "empty16", batch = "handcraft_dead")
    public static void deadPlayerCannotEnqueue(GameTestHelper h) {
        h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, h.getLevel().getServer());
        FakePlayer p = player(h, AT);
        give(p, ModItems.IRON_PLATE.get(), 4);
        h.assertTrue(HandcraftTicker.enqueue(p, "pipe", 1) == EnqueueResult.OK, "pipe refused");
        die(p);
        p.setHealth(0);
        h.assertTrue(plain(p, ModItems.IRON_PLATE.get()) == 4, "death refund " + plain(p, ModItems.IRON_PLATE.get()));
        HandcraftPayloads.Enqueue.handle(new HandcraftPayloads.Enqueue("iron_gear_wheel", 1), context(p));
        h.assertTrue(HandcraftTicker.enqueue(p, "pipe", 2) == EnqueueResult.DEAD, "dead player queued");
        h.assertTrue(entries(p).isEmpty(), "queued after death: " + entries(p));
        h.assertTrue(plain(p, ModItems.IRON_PLATE.get()) == 4, "inventory changed after death: " + plain(p, ModItems.IRON_PLATE.get()));
        h.succeed();
    }

    /** The sync payload round-trips and its codec refuses oversized queues. */
    @GameTest(template = "empty16")
    public static void queueSyncCodec(GameTestHelper h) {
        HandcraftPayloads.QueueSync sync = new HandcraftPayloads.QueueSync(
                List.of(new HandcraftQueue.Entry("inserter", 3, 0.25), new HandcraftQueue.Entry("pipe", 1, 0)), 10);
        ByteBuf buf = Unpooled.buffer();
        HandcraftPayloads.QueueSync.STREAM_CODEC.encode(buf, sync);
        h.assertTrue(HandcraftPayloads.QueueSync.STREAM_CODEC.decode(buf).equals(sync), "sync changed in transit");
        List<HandcraftQueue.Entry> many = new ArrayList<>();
        for (int i = 0; i <= HandcraftTicker.MAX_ENTRIES; i++) many.add(new HandcraftQueue.Entry("pipe", 1, 0));
        boolean encoded;
        try {
            HandcraftPayloads.QueueSync.STREAM_CODEC.encode(Unpooled.buffer(), new HandcraftPayloads.QueueSync(many, 10));
            encoded = true;
        } catch (RuntimeException e) {
            encoded = false;
        }
        h.assertTrue(!encoded, "oversized queue encoded");
        h.succeed();
    }

    /** The queue is capped at {@link HandcraftTicker#MAX_ENTRIES} entries. */
    @GameTest(template = "empty16", timeoutTicks = 200)
    public static void queueIsCapped(GameTestHelper h) {
        FakePlayer p = player(h, AT);
        give(p, ModItems.IRON_PLATE.get(), 64);
        give(p, ModItems.IRON_PLATE.get(), 64);
        int[] sent = {0};
        h.onEachTick(() -> {
            if (sent[0] < HandcraftTicker.MAX_ENTRIES && HandcraftTicker.enqueue(p, "pipe", 1) == EnqueueResult.OK) sent[0]++;
        });
        h.succeedWhen(() -> {
            h.assertTrue(sent[0] == HandcraftTicker.MAX_ENTRIES, "sent " + sent[0]);
            h.assertTrue(HandcraftTicker.enqueue(p, "pipe", 1) == EnqueueResult.QUEUE_FULL, "queue not capped");
        });
    }
}
