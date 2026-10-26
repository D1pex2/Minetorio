package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.dropped;
import static dev.minetorio.gametest.BeltGameTest.place;
import static dev.minetorio.gametest.InserterGameTest.handler;
import static dev.minetorio.gametest.MachineGameTest.named;
import static dev.minetorio.gametest.MachineGameTest.player;

import dev.minetorio.content.inserter.InserterEndpoints;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.machine.AssemblerBlockEntity.SelectResult;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.research.LabBlockEntity;
import dev.minetorio.content.research.RecipeLocks;
import dev.minetorio.content.research.ResearchSavedData;
import dev.minetorio.content.research.ResearchSyncPayload;
import dev.minetorio.content.research.SelectResearchPayload;
import dev.minetorio.core.inserter.DropTarget;
import dev.minetorio.core.research.ResearchState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Labs and the world's research. The research state is one per world (shared by every test), so
 * each test that reads or changes it runs in its own batch (batches run one after another) and
 * starts from a cleared state.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class ResearchGameTest {
    private static final String R = "minetorio:automation_science_pack";
    private static final String G = "minetorio:logistic_science_pack";
    /** Lab at (2,1,2) facing east: footprint x 2..4, z 2..4. */
    private static final BlockPos LAB = new BlockPos(2, 1, 2);
    /** Second lab at (2,1,8) facing east: footprint x 2..4, z 8..10. */
    private static final BlockPos LAB_2 = new BlockPos(2, 1, 8);
    /** Pole whose supply area (z 4..8) covers a cell of both labs. */
    private static final BlockPos POLE = new BlockPos(3, 1, 6);

    static ResearchSavedData clearedResearch(GameTestHelper h) {
        ResearchSavedData data = ResearchSavedData.get(h.getLevel().getServer());
        data.restore(Set.of(), null, Map.of());
        return data;
    }

    static LabBlockEntity lab(GameTestHelper h, BlockPos rel) {
        place(h, ModItems.LAB.get(), rel, Direction.EAST);
        return (LabBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
    }

    /** Records the test tick at which {@code techId} became researched in [0]. */
    static long[] watchResearched(GameTestHelper h, ResearchState state, String techId) {
        long[] at = {-1};
        h.onEachTick(() -> { if (at[0] < 0 && state.isResearched(techId)) at[0] = h.getTick(); });
        return at;
    }

    /** One powered lab with 10 automation packs researches automation (10 units × 10 s) in ~2000 ticks. */
    @GameTest(template = "empty16", batch = "research_single_lab", timeoutTicks = 2300)
    public static void labResearchesAutomation(GameTestHelper h) {
        ResearchSavedData data = clearedResearch(h);
        MinecraftServer server = h.getLevel().getServer();
        h.assertTrue(data.select("automation"), "automation not selectable");
        LabBlockEntity lab = lab(h, LAB);
        TestPower.powerAt(h, POLE);
        h.assertTrue(lab.lab().insertPack(R, 10, false) == 10, "packs refused");
        long[] done = watchResearched(h, data.state(), "automation");
        h.runAtTickTime(1100, () -> {
            h.assertTrue(data.state().unitsDone() == 5, "units after 1100 ticks: " + data.state().unitsDone());
            h.assertTrue(lab.isWorking() && lab.lab().powerDemand() == LabBlockEntity.POWER, "lab idle mid-research");
        });
        h.succeedWhen(() -> {
            h.assertTrue(done[0] >= 0, "automation not researched");
            h.assertTrue(done[0] >= 1995 && done[0] <= 2030, "researched at tick " + done[0]);
            h.assertTrue(RecipeLocks.isUnlocked(server, "assembling_machine_1"), "assembling_machine_1 still locked");
            h.assertTrue(RecipeLocks.isUnlocked(server, "long_handed_inserter"), "long_handed_inserter still locked");
            h.assertTrue(data.state().current().isEmpty(), "current " + data.state().current());
            h.assertTrue(lab.lab().packs().isEmpty(), "packs left " + lab.lab().packs());
            h.assertTrue(data.isDirty(), "completion not marked dirty");
        });
    }

    /** Two labs share the work: automation is done in ~1000 ticks (half the single-lab time). */
    @GameTest(template = "empty16", batch = "research_two_labs", timeoutTicks = 1300)
    public static void labsShareProgress(GameTestHelper h) {
        ResearchSavedData data = clearedResearch(h);
        h.assertTrue(data.select("automation"), "automation not selectable");
        LabBlockEntity a = lab(h, LAB);
        LabBlockEntity b = lab(h, LAB_2);
        TestPower.powerAt(h, POLE);
        a.lab().insertPack(R, 5, false);
        b.lab().insertPack(R, 5, false);
        long[] done = watchResearched(h, data.state(), "automation");
        h.succeedWhen(() -> {
            h.assertTrue(done[0] >= 0, "automation not researched");
            h.assertTrue(done[0] >= 995 && done[0] <= 1030, "researched at tick " + done[0]);
            h.assertTrue(a.lab().packs().isEmpty() && b.lab().packs().isEmpty(), "packs left " + a.lab().packs() + " " + b.lab().packs());
        });
    }

    /** Before automation is researched the assembler refuses assembling_machine_1; afterwards it accepts it. */
    @GameTest(template = "empty16", batch = "research_locks")
    public static void assemblerRejectsLockedRecipe(GameTestHelper h) {
        ResearchSavedData data = clearedResearch(h);
        MinecraftServer server = h.getLevel().getServer();
        place(h, ModItems.ASSEMBLING_MACHINE_1.get(), LAB, Direction.EAST);
        AssemblerBlockEntity be = (AssemblerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(LAB));
        BlockPos pos = h.absolutePos(LAB);
        FakePlayer p = player(h, new BlockPos(3, 1, 7));
        p.containerMenu = be.createMenu(1, p.getInventory(), p);
        h.assertTrue(!RecipeLocks.isUnlocked(server, "assembling_machine_1"), "assembling_machine_1 unlocked at start");
        h.assertTrue(RecipeLocks.isUnlocked(server, "iron_plate"), "smelting locked");
        h.assertTrue(RecipeLocks.isUnlocked(server, "iron_gear_wheel"), "start recipe locked");
        h.assertTrue(AssemblerBlockEntity.select(p, pos, "assembling_machine_1") == SelectResult.LOCKED, "locked recipe accepted");
        h.assertTrue(AssemblerBlockEntity.select(p, pos, "fast_inserter") == SelectResult.LOCKED, "locked recipe accepted");
        h.assertTrue(be.machine().recipe() == null, "recipe set to " + be.machine().recipe());
        data.restore(Set.of("automation"), null, Map.of());
        h.assertTrue(AssemblerBlockEntity.select(p, pos, "assembling_machine_1") == SelectResult.OK, "unlocked recipe refused");
        h.assertTrue(be.machine().recipe() != null && be.machine().recipe().id().equals("assembling_machine_1"), "recipe " + be.machine().recipe());
        h.assertTrue(AssemblerBlockEntity.select(p, pos, "fast_inserter") == SelectResult.LOCKED, "fast_inserter accepted");
        h.succeed();
    }

    /** The research state survives save → load (researched, current, partial progress per technology). */
    @GameTest(template = "empty16", batch = "research_persist")
    public static void researchPersists(GameTestHelper h) {
        ResearchSavedData data = clearedResearch(h);
        var registries = h.getLevel().registryAccess();
        data.restore(Set.of("automation", "logistics"), "fast_inserter", Map.of("fast_inserter", 7, "logistic_science_pack", 3));
        h.assertTrue(data.isDirty(), "change not marked dirty");
        CompoundTag tag = data.save(new CompoundTag(), registries);
        ResearchState back = ResearchSavedData.load(tag, registries).state();
        h.assertTrue(back.researched().equals(Set.of("automation", "logistics")), "researched " + back.researched());
        h.assertTrue(back.current().map(t -> t.id().equals("fast_inserter")).orElse(false), "current " + back.current());
        h.assertTrue(back.unitsDone() == 7, "units done " + back.unitsDone());
        h.assertTrue(back.progressById().equals(Map.of("fast_inserter", 7, "logistic_science_pack", 3)), "progress " + back.progressById());
        h.assertTrue(back.isRecipeUnlocked("assembling_machine_1") && back.isRecipeUnlocked("splitter"), "unlocks lost");

        // a saved current technology that is already researched or unknown is dropped
        tag.putString("current", "automation");
        h.assertTrue(ResearchSavedData.load(tag, registries).state().current().isEmpty(), "researched tech loaded as current");
        tag.putString("current", "no_such_tech");
        h.assertTrue(ResearchSavedData.load(tag, registries).state().current().isEmpty(), "unknown tech loaded as current");
        // the world's instance is the one in the Overworld's data storage
        h.assertTrue(ResearchSavedData.get(h.getLevel().getServer()) == data, "not one state per world");
        data.restore(Set.of(), null, Map.of());
        h.succeed();
    }

    /**
     * SelectResearchPayload is validated: unknown, locked and already researched technologies and
     * spectators are refused; the current one may be chosen again. The codecs cap id length and
     * collection sizes.
     */
    @GameTest(template = "empty16", batch = "research_select")
    public static void selectResearchPayloadRejectsLockedOrUnknown(GameTestHelper h) {
        ResearchSavedData data = clearedResearch(h);
        FakePlayer p = player(h, new BlockPos(3, 1, 7));
        h.assertTrue(ResearchSavedData.select(p, "no_such_tech") == ResearchSavedData.SelectResult.UNKNOWN, "unknown accepted");
        h.assertTrue(ResearchSavedData.select(p, "automation_2") == ResearchSavedData.SelectResult.UNAVAILABLE, "locked accepted");
        h.assertTrue(data.state().current().isEmpty(), "current " + data.state().current());
        p.setGameMode(GameType.SPECTATOR);
        h.assertTrue(ResearchSavedData.select(p, "automation") == ResearchSavedData.SelectResult.SPECTATOR, "spectator accepted");
        p.setGameMode(GameType.SURVIVAL);
        h.assertTrue(ResearchSavedData.select(p, "automation") == ResearchSavedData.SelectResult.OK, "available refused");
        h.assertTrue(ResearchSavedData.select(p, "automation") == ResearchSavedData.SelectResult.OK, "current refused");
        h.assertTrue(data.state().current().map(t -> t.id().equals("automation")).orElse(false), "current " + data.state().current());
        data.restore(Set.of("automation"), null, Map.of());
        h.assertTrue(ResearchSavedData.select(p, "automation") == ResearchSavedData.SelectResult.UNAVAILABLE, "researched accepted");
        h.assertTrue(ResearchSavedData.select(p, "fast_inserter") == ResearchSavedData.SelectResult.OK, "unlocked tech refused");

        // codecs
        h.assertTrue(!roundTrips(SelectResearchPayload.STREAM_CODEC, new SelectResearchPayload("x".repeat(ResearchSyncPayload.MAX_ID_LENGTH + 1))),
                "over-long tech id accepted");
        ResearchSyncPayload sync = new ResearchSyncPayload(Set.of("automation"), "fast_inserter", Map.of("fast_inserter", 4));
        h.assertTrue(decode(ResearchSyncPayload.STREAM_CODEC, sync).equals(sync), "sync payload changed in transit");
        ResearchSyncPayload none = new ResearchSyncPayload(Set.of(), null, Map.of());
        h.assertTrue(decode(ResearchSyncPayload.STREAM_CODEC, none).equals(none), "null current changed in transit");
        Set<String> many = new HashSet<>();
        for (int i = 0; i <= ResearchSyncPayload.MAX_TECHS; i++) many.add("t" + i);
        h.assertTrue(!roundTrips(ResearchSyncPayload.STREAM_CODEC, new ResearchSyncPayload(many, null, Map.of())), "oversized set accepted");
        data.restore(Set.of(), null, Map.of());
        h.succeed();
    }

    private static <T> T decode(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        codec.encode(buf, value);
        return codec.decode(buf);
    }

    private static <T> boolean roundTrips(StreamCodec<ByteBuf, T> codec, T value) {
        try {
            decode(codec, value);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Packs: inserters and the item capability insert up to 2 of each (no iron plates, no packs with
     * data components, no extraction); right-click inserts by hand up to 64 and takes them from the
     * stack; breaking the lab drops the stored packs once.
     */
    @GameTest(template = "empty16")
    public static void labPackHandling(GameTestHelper h) {
        LabBlockEntity lab = lab(h, LAB);
        BlockPos part = new BlockPos(4, 2, 4);
        DropTarget target = InserterEndpoints.target(h.getLevel(), h.absolutePos(part), Direction.UP);
        h.assertTrue(target != null && !target.wants("minetorio:iron_plate"), "lab wants plates");
        h.assertTrue(target.accept(R) && target.accept(R) && !target.accept(R), "inserter cap is not 2");
        IItemHandler handler = handler(h, part);
        h.assertTrue(handler.insertItem(1, new ItemStack(ModItems.LOGISTIC_SCIENCE_PACK.get(), 5), false).getCount() == 3, "capability cap is not 2");
        h.assertTrue(handler.insertItem(0, new ItemStack(ModItems.IRON_PLATE.get(), 1), false).getCount() == 1, "plate accepted");
        h.assertTrue(handler.insertItem(1, named(ModItems.LOGISTIC_SCIENCE_PACK.get(), 1), false).getCount() == 1, "named pack accepted");
        h.assertTrue(handler.extractItem(0, 64, false).isEmpty(), "packs extracted");
        h.assertTrue(lab.lab().packs().equals(Map.of(R, 2, G, 2)), "packs " + lab.lab().packs());

        FakePlayer p = player(h, new BlockPos(3, 1, 7));
        p.setGameMode(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AUTOMATION_SCIENCE_PACK.get(), 64));
        BlockPos abs = h.absolutePos(part);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        h.assertTrue(lab.lab().packs().get(R) == 64, "manual cap: " + lab.lab().packs());
        h.assertTrue(p.getMainHandItem().getCount() == 2, "hand left " + p.getMainHandItem());

        h.getLevel().destroyBlock(abs, true);
        h.assertTrue(dropped(h, ModItems.AUTOMATION_SCIENCE_PACK.get()) == 64, "R dropped " + dropped(h, ModItems.AUTOMATION_SCIENCE_PACK.get()));
        h.assertTrue(dropped(h, ModItems.LOGISTIC_SCIENCE_PACK.get()) == 2, "G dropped " + dropped(h, ModItems.LOGISTIC_SCIENCE_PACK.get()));
        h.assertTrue(dropped(h, ModItems.LAB.get()) == 1, "lab item not dropped once: " + dropped(h, ModItems.LAB.get()));
        h.succeed();
    }

    /** Stored packs and the unit in progress survive save/load. */
    @GameTest(template = "empty16")
    public static void labNbtRoundTrip(GameTestHelper h) {
        LabBlockEntity be = lab(h, LAB);
        be.lab().load(Map.of(R, 3, G, 1), "automation", 0.25);
        var registries = h.getLevel().registryAccess();
        LabBlockEntity copy = new LabBlockEntity(be.getBlockPos(), be.getBlockState());
        copy.loadWithComponents(be.saveWithoutMetadata(registries), registries);
        h.assertTrue(copy.lab().packs().equals(Map.of(R, 3, G, 1)), "packs " + copy.lab().packs());
        h.assertTrue("automation".equals(copy.lab().unitTech()) && copy.lab().progress() == 0.25, "unit lost");
        h.succeed();
    }
}
