package dev.minetorio.gametest;

import dev.minetorio.content.belt.BeltBlockEntity;
import dev.minetorio.content.belt.LaneSide;
import dev.minetorio.content.demo.DemoBuilder;
import dev.minetorio.content.demo.DemoSavedData;
import dev.minetorio.content.deposit.Deposits;
import dev.minetorio.content.inserter.InserterBlockEntity;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.machine.ElectricDrillBlockEntity;
import dev.minetorio.content.power.SteamEngineBlockEntity;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.research.LabBlockEntity;
import dev.minetorio.content.research.ResearchSavedData;
import dev.minetorio.core.deposit.Deposit;
import dev.minetorio.core.deposit.PlacedDeposits;
import dev.minetorio.core.research.ResearchState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The demo showcase. 96×96 does not fit a test template, so the test runs {@code /minetorio demo}
 * for a spot {@value #OFFSET} blocks east of its own (empty) template, on the template's floor
 * level, force-loads the showcase's chunks while it runs and checks the showcase there. It changes
 * the world's research and adds a placed deposit and demo data, so it runs in its own batch and
 * puts all of that back afterwards.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class DemoGameTest {
    private static final int OFFSET = 4000;
    /** The showcase being tested (set by the test, cleaned up after the batch). */
    @Nullable
    private static BlockPos origin;

    @AfterBatch(batch = "demo")
    public static void cleanUp(ServerLevel level) {
        DemoSavedData.get(level).clear();
        ResearchSavedData.get(level.getServer()).restore(Set.of(), null, Map.of());
        if (origin == null) return;
        DemoBuilder.Layout layout = DemoBuilder.layout(origin);
        Deposits.get(level).depositAt(layout.depositCenter().getX(), layout.depositCenter().getZ())
                .filter(d -> d.id() <= PlacedDeposits.FIRST_ID)
                .ifPresent(d -> Deposits.removePlacedDeposit(level, d));
        for (ChunkPos c : chunks(layout.area())) level.setChunkForced(c.x, c.z, false);
        origin = null;
    }

    private static List<ChunkPos> chunks(BoundingBox area) {
        List<ChunkPos> out = new ArrayList<>();
        for (int cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++) {
            for (int cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++) out.add(new ChunkPos(cx, cz));
        }
        return out;
    }

    private static int count(ServerLevel level, BlockPos pos, Item item) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        int n = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).is(item)) n += handler.getStackInSlot(i).getCount();
        }
        return n;
    }

    private static List<BlockEntity> blockEntities(ServerLevel level, BoundingBox area) {
        List<BlockEntity> out = new ArrayList<>();
        for (ChunkPos c : chunks(area)) {
            for (BlockEntity be : level.getChunk(c.x, c.z).getBlockEntities().values()) {
                if (area.isInside(be.getBlockPos())) out.add(be);
            }
        }
        return out;
    }

    /** Satisfaction of an electric consumer; NaN for anything else. */
    private static double satisfaction(BlockEntity be) {
        if (be instanceof InserterBlockEntity i) return i.type().burner ? Double.NaN : i.satisfaction();
        if (be instanceof AssemblerBlockEntity a) return a.satisfaction();
        if (be instanceof ElectricDrillBlockEntity d) return d.satisfaction();
        if (be instanceof LabBlockEntity l) return l.satisfaction();
        return Double.NaN;
    }

    /**
     * After 600 ticks: plates in the smelting output, gears made, every electric consumer of the
     * showcase fully powered by the steam plant (both engines running), research progressing, all
     * three belt tiers carrying items.
     */
    @GameTest(template = "empty16", batch = "demo", timeoutTicks = 700)
    public static void demoBuildsAndRuns(GameTestHelper h) throws Exception {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        ResearchSavedData research = ResearchSavedData.get(server);
        research.restore(Set.of(), null, Map.of());
        BlockPos feet = h.absolutePos(new BlockPos(0, 1, 0)).east(OFFSET);
        BlockPos o = DemoBuilder.originAround(feet);
        origin = o;
        DemoBuilder.Layout layout = DemoBuilder.layout(o);
        for (ChunkPos c : chunks(layout.area())) level.setChunkForced(c.x, c.z, true);

        int result = server.getCommands().getDispatcher().execute(
                "minetorio demo " + feet.getX() + " " + feet.getY() + " " + feet.getZ(), server.createCommandSourceStack().withSuppressedOutput());
        h.assertTrue(result == 1, "command returned " + result);
        Deposit deposit = Deposits.get(level).depositAt(layout.depositCenter().getX(), layout.depositCenter().getZ()).orElse(null);
        h.assertTrue(deposit != null && deposit.id() <= PlacedDeposits.FIRST_ID && deposit.initialAmount() == DemoBuilder.DEPOSIT_AMOUNT,
                "no demo deposit: " + deposit);
        ResearchState state = research.state();
        h.assertTrue(state.current().map(t -> t.id().equals("automation")).orElse(false), "research " + state.current());

        h.runAtTickTime(600, () -> {
            int plates = count(level, layout.smeltingOutput(), ModItems.IRON_PLATE.get());
            h.assertTrue(plates > 0, "no plates in the smelting output");
            AssemblerBlockEntity gearAsm = (AssemblerBlockEntity) level.getBlockEntity(layout.gearAssembler());
            AssemblerBlockEntity redAsm = (AssemblerBlockEntity) level.getBlockEntity(layout.redAssembler());
            String gear = "minetorio:iron_gear_wheel";
            int gears = count(level, layout.gearChest(), ModItems.IRON_GEAR_WHEEL.get())
                    + gearAsm.machine().outputs().getOrDefault(gear, 0) + redAsm.machine().inputs().getOrDefault(gear, 0);
            h.assertTrue(gears > 0, "no gears made; gear assembler " + gearAsm.machine().inputs() + " / " + gearAsm.machine().outputs());

            int consumers = 0;
            for (BlockEntity be : blockEntities(level, layout.area())) {
                double s = satisfaction(be);
                if (Double.isNaN(s)) continue;
                consumers++;
                h.assertTrue(s >= 0.99, be.getType() + " at " + be.getBlockPos().subtract(o) + " has satisfaction " + s);
            }
            h.assertTrue(consumers == 18, "electric consumers: " + consumers);
            for (BlockPos e : layout.engines()) {
                SteamEngineBlockEntity engine = (SteamEngineBlockEntity) level.getBlockEntity(e);
                h.assertTrue(engine.isWorking(), "engine at " + e.subtract(o) + " not working");
            }

            h.assertTrue(state.unitsDone() > 0 || state.isResearched("automation"), "no research progress");
            for (BlockPos lab : layout.labs()) h.assertTrue(((LabBlockEntity) level.getBlockEntity(lab)).isWorking(), "lab idle at " + lab.subtract(o));

            for (int i = 0; i < 3; i++) {
                BeltBlockEntity belt = (BeltBlockEntity) level.getBlockEntity(o.offset(14, 1, 28 + 2 * i));
                h.assertTrue(!belt.lane(LaneSide.LEFT).isEmpty() && !belt.lane(LaneSide.RIGHT).isEmpty(), "belt tier " + i + " empty");
            }
            h.succeed();
        });
    }
}
