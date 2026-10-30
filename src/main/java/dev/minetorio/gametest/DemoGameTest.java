package dev.minetorio.gametest;

import dev.minetorio.content.registry.BaseMaterials;
import dev.minetorio.content.belt.BeltBlockEntity;
import dev.minetorio.content.belt.LaneSide;
import dev.minetorio.content.demo.DemoBuilder;
import dev.minetorio.content.demo.DemoSavedData;
import dev.minetorio.content.inserter.InserterBlockEntity;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.machine.ElectricDrillBlockEntity;
import dev.minetorio.content.map.RadarBlockEntity;
import dev.minetorio.content.orefield.OreFieldPayloads;
import dev.minetorio.content.orefield.OreFields;
import dev.minetorio.content.orefield.OrePatchLedger;
import dev.minetorio.core.orefield.OrePatch;
import dev.minetorio.core.orefield.PatchKind;
import dev.minetorio.core.orefield.PatchLedger;
import dev.minetorio.core.orefield.PatchShape;
import dev.minetorio.content.power.SteamEngineBlockEntity;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.research.LabBlockEntity;
import dev.minetorio.content.research.ResearchSavedData;
import dev.minetorio.core.orefield.OreType;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The demo showcase. 96×96 does not fit a test template, so the test runs {@code /minetorio demo}
 * for a spot {@value #OFFSET} blocks east of its own (empty) template, on the template's floor
 * level, force-loads the showcase's chunks while it runs and checks the showcase there. It changes
 * the world's research and adds demo ore fields and demo data, so it runs in its own batch and
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
        dev.minetorio.content.demo.RailDemoBuilder.clear(level, layout.area());
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

    private static void spawn(ServerLevel level, EntityType<?> type, BlockPos at) {
        Entity e = type.create(level);
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        level.addFreshEntity(e);
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
        if (be instanceof RadarBlockEntity r) return r.satisfaction();
        return Double.NaN;
    }

    /**
     * After 600 ticks: plates in the smelting output, gears made, every electric consumer of the
     * showcase fully powered by the steam plant (both engines running), research progressing, all
     * three belt tiers carrying items, the radar powered and working.
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
        // A tree crosses the west boundary: its exterior crown must not be left floating,
        // while an unrelated exterior block remains untouched.
        BlockPos tree = o.offset(0, 1, 4);
        BlockPos outsideLeaf = tree.offset(-1, 2, 0);
        BlockPos exteriorBuilding = tree.offset(-2, 2, 0);
        level.setBlock(tree, dev.minetorio.content.registry.ModWorld.TREE_TRUNK.get().defaultBlockState(), 3);
        level.setBlock(tree.above(), dev.minetorio.content.registry.ModWorld.TREE_TRUNK.get().defaultBlockState(), 3);
        level.setBlock(tree.above(2), dev.minetorio.content.registry.ModWorld.TREE_LEAVES_GREEN.get().defaultBlockState(), 3);
        level.setBlock(outsideLeaf, dev.minetorio.content.registry.ModWorld.TREE_LEAVES_GREEN.get().defaultBlockState(), 3);
        level.setBlock(exteriorBuilding, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        // animals standing where machines go must not stop the build
        spawn(level, EntityType.COW, layout.gearAssembler());
        spawn(level, EntityType.PIG, layout.engines().get(0));

        int result = server.getCommands().getDispatcher().execute(
                "minetorio demo " + feet.getX() + " " + feet.getY() + " " + feet.getZ(), server.createCommandSourceStack().withSuppressedOutput());
        h.assertTrue(result == 1, "command returned " + result);
        var railway = dev.minetorio.content.rail.RailwaySavedData.get(level).state();
        var demoTrains = railway.trains().values().stream().filter(t ->
                dev.minetorio.core.rail.TrainMotion.carPoses(railway, t.id()).values().stream().anyMatch(p ->
                        layout.area().isInside(BlockPos.containing(p.position().x(), p.position().y(), p.position().z())))).toList();
        h.assertTrue(demoTrains.size() == 1, "Демо должно содержать настоящий canonical поезд, найдено " + demoTrains.size());
        var demoTrain = demoTrains.getFirst();
        h.assertTrue(demoTrain.cars().size() == 4 && demoTrain.mode() == dev.minetorio.core.rail.RailwayState.Mode.AUTOMATIC,
                "Демо: два встречных локомотива, грузовой вагон и цистерна в AUTO");
        h.assertTrue(railway.dispatcher().runtime(demoTrain.id()).schedule().stops().size() == 2,
                "Демо: две настоящие остановки в расписании");

        h.assertTrue(level.getBlockState(outsideLeaf).isAir(), "boundary tree left an exterior crown");
        h.assertTrue(level.getBlockState(exteriorBuilding).is(Blocks.IRON_BLOCK), "boundary tree cleanup removed an exterior building");
        h.assertTrue(level.getEntitiesOfClass(Animal.class, AABB.of(layout.area())).isEmpty(), "animals left in the area");
        h.assertTrue(OreFields.typeOf(level.getBlockState(layout.ironField())) == OreType.IRON, "no iron field: " + level.getBlockState(layout.ironField()));
        h.assertTrue(OreFields.remaining(level, layout.ironField()) == DemoBuilder.FIELD_AMOUNT, "iron field amount " + OreFields.remaining(level, layout.ironField()));
        h.assertTrue(OreFields.typeOf(level.getBlockState(layout.coalField())) == OreType.COAL, "no coal field: " + level.getBlockState(layout.coalField()));
        // each demo field reports 10 000 × its cells as its patch total, from any of its blocks
        List<OrePatch> demoPatches = List.of(
                new OrePatch(OreType.IRON, layout.ironField().getX(), layout.ironField().getZ(), DemoBuilder.IRON_RADIUS),
                new OrePatch(OreType.COAL, layout.coalField().getX(), layout.coalField().getZ(), DemoBuilder.COAL_RADIUS));
        for (OrePatch disk : demoPatches) {
            int fieldY = layout.ironField().getY();
            int cells = PatchShape.of(disk).cells(), blocks = 0;
            for (PatchShape.Run r : PatchShape.of(disk).runs()) {
                for (int x = r.x0(); x <= r.x1(); x++) {
                    if (OreFields.typeOf(level.getBlockState(new BlockPos(x, fieldY, r.z()))) == disk.type()) blocks++;
                }
            }
            h.assertTrue(blocks == cells, disk + ": " + blocks + " field blocks of " + cells + " cells");
            long key = PatchLedger.key(PatchKind.DEMO, disk);
            for (int[] at : List.of(new int[] {disk.centerX(), disk.centerZ()}, new int[] {disk.centerX() - disk.radius(), disk.centerZ()})) {
                OreFieldPayloads.Info info = OreFieldPayloads.lookup(level, at[0], at[1]);
                h.assertTrue(info.patch() == key && info.radius() == disk.radius() && info.centerX() == disk.centerX()
                        && info.centerZ() == disk.centerZ(), "patch at " + at[0] + "," + at[1] + ": " + info);
                h.assertTrue(info.total() == DemoBuilder.FIELD_AMOUNT * cells, disk + " total " + info.total() + " vs " + DemoBuilder.FIELD_AMOUNT * cells);
            }
        }
        h.assertTrue(level.getBlockEntity(layout.radar()) instanceof RadarBlockEntity, "no radar at " + layout.radar().subtract(o)
                + ": " + level.getBlockState(layout.radar()));
        ResearchState state = research.state();
        h.assertTrue(state.current().map(t -> t.id().equals("automation")).orElse(false), "research " + state.current());

        h.runAtTickTime(600, () -> {
            var railLayout = dev.minetorio.content.demo.RailDemoBuilder.layout(o);
            h.assertTrue(count(level, railLayout.output(), ModItems.IRON_PLATE.get()) > 0,
                    "Челнок должен выгрузить пластины через настоящий манипулятор: " + railway.trains().get(demoTrain.id()).status());
            var railTank = (dev.minetorio.content.fluid.StorageTankBlockEntity) level.getBlockEntity(railLayout.tank());
            h.assertTrue(railTank.fluidNode().amount() > 0, "Челнок должен выгрузить воду через настоящую помпу, tank=" + railTank.fluidNode().amount());
            h.assertTrue(count(level, railLayout.weapons(), dev.minetorio.content.combat.CombatRegistry.PISTOL.get()) == 1,
                    "Оружие первого боя доступно в сундуке");
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
            h.assertTrue(consumers == 27, "electric consumers, including the oil showcase inserters: " + consumers);
            for (BlockPos e : layout.engines()) {
                SteamEngineBlockEntity engine = (SteamEngineBlockEntity) level.getBlockEntity(e);
                h.assertTrue(engine.isWorking(), "engine at " + e.subtract(o) + " not working");
            }

            RadarBlockEntity radar = (RadarBlockEntity) level.getBlockEntity(layout.radar());
            h.assertTrue(radar.satisfaction() >= 0.99 && radar.isWorking(), "radar not powered: " + radar.satisfaction());

            h.assertTrue(state.unitsDone() > 0 || state.isResearched("automation"), "no research progress");
            for (BlockPos lab : layout.labs()) h.assertTrue(((LabBlockEntity) level.getBlockEntity(lab)).isWorking(), "lab idle at " + lab.subtract(o));

            for (int i = 0; i < 3; i++) {
                BeltBlockEntity belt = (BeltBlockEntity) level.getBlockEntity(o.offset(14, 1, 28 + 2 * i));
                h.assertTrue(!belt.lane(LaneSide.LEFT).isEmpty() && !belt.lane(LaneSide.RIGHT).isEmpty(), "belt tier " + i + " empty");
            }
            h.assertTrue(plates <= DemoBuilder.OUTPUT_KEEP, "output chest not trimmed: " + plates);

            h.assertTrue(count(level, layout.coalField().offset(1, 1, -1), BaseMaterials.COAL.get()) > 0, "coal drill mined nothing");

            // Повтор команды не оставляет исчезнувшие проекции с живыми canonical владельцами.
            var previousCars = List.copyOf(demoTrain.cars());
            var previousRails = railway.rails().stream().filter(r -> layout.area().isInside(
                    dev.minetorio.content.rail.RailPlacement.block(r.origin()))).map(r -> r.id()).toList();
            var other = railway.createCar(java.util.UUID.randomUUID(), dev.minetorio.core.data.RailData.StockKind.CARGO, false);
            DemoBuilder.build(level, o);
            h.assertTrue(previousCars.stream().noneMatch(railway.cars()::containsKey)
                            && previousRails.stream().noneMatch(id -> railway.graph().piece(id).isPresent()),
                    "Повторная постройка должна удалить старые canonical машины и рельсы");
            h.assertTrue(railway.cars().containsKey(other.id()), "Перестройка демо не удаляет посторонний owner");
            railway.removeCar(other.id(), true);
            h.assertTrue(railway.cars().values().stream().filter(c -> !previousCars.contains(c.id())).count() >= 4,
                    "После перестройки остаются новые четыре вагона");

            // demo off: no entries, no autopilot, the fields turned into dirt
            try {
                server.getCommands().getDispatcher().execute("minetorio demo off", server.createCommandSourceStack().withSuppressedOutput());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            DemoSavedData data = DemoSavedData.getIfPresent(server);
            h.assertTrue(data == null || data.isEmpty(), "demo data left after off");
            for (BlockPos f : List.of(layout.ironField(), layout.coalField(), layout.ironField().offset(-7, 0, -4))) {
                h.assertTrue(level.getBlockState(f).is(Blocks.DIRT), "field left after off at " + f.subtract(o));
            }
            for (OrePatch disk : demoPatches) {
                h.assertTrue(OrePatchLedger.get(level).total(PatchLedger.key(PatchKind.DEMO, disk)) == -1, "demo patch left in the ledger: " + disk);
            }
            h.succeed();
        });
    }
}
