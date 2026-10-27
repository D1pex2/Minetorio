package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.place;
import static dev.minetorio.gametest.InserterGameTest.container;
import static dev.minetorio.gametest.InserterGameTest.count;
import static dev.minetorio.gametest.InserterGameTest.inserter;

import dev.minetorio.content.inserter.InserterBlockEntity;
import dev.minetorio.content.multiblock.MultiblockBlockItem;
import dev.minetorio.content.power.BoilerBlockEntity;
import dev.minetorio.content.power.ElectricPoleBlock;
import dev.minetorio.content.power.ElectricPoleBlockEntity;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.power.OffshorePumpBlockEntity;
import dev.minetorio.content.power.PipeBlock;
import dev.minetorio.content.power.PipeBlockEntity;
import dev.minetorio.content.power.SteamEngineBlockEntity;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.Units;
import dev.minetorio.core.fluid.SteamPlant;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class PowerGameTest {
    private static final String COAL = "minecraft:coal";
    private static final long KW_900 = Units.kw(900);

    /*
     * Steam plant layout (y = 1):
     *   water source (1,8), walled in; offshore pump facing WEST at (3,8) [cells x 2..3], output toward +x;
     *   pipe (4,8); boiler facing NORTH at (5,8) [x 5..7, z 7..8], left water port (5,8)→west,
     *   steam port (6,7)→north; steam engine facing NORTH at (5,6) [x 5..7, z 2..6], back port (6,6)→south
     *   (touches the boiler's steam port directly); pole (9,4) covers x 7..11, z 2..6 (the engine and the loads).
     */
    private static final BlockPos WATER = new BlockPos(1, 1, 8);
    private static final BlockPos PUMP = new BlockPos(3, 1, 8);
    private static final BlockPos PIPE = new BlockPos(4, 1, 8);
    private static final BlockPos BOILER = new BlockPos(5, 1, 8);
    private static final BlockPos ENGINE = new BlockPos(5, 1, 6);
    private static final BlockPos POLE = new BlockPos(9, 1, 4);

    // --- helpers ---------------------------------------------------------------------------------

    /** A water source block that cannot flow anywhere (stone around it except toward +x, where the pump goes). */
    static void walledWater(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel.below(), Blocks.STONE);
        h.setBlock(rel.west(), Blocks.STONE);
        h.setBlock(rel.north(), Blocks.STONE);
        h.setBlock(rel.south(), Blocks.STONE);
        h.setBlock(rel, Blocks.WATER);
    }

    /** Builds the steam plant (see layout); returns the boiler. */
    static BoilerBlockEntity steamPlant(GameTestHelper h, int coal) {
        walledWater(h, WATER);
        place(h, ModItems.PIPE.get(), PIPE, Direction.NORTH); // before the machines: connects through shape updates
        place(h, ModItems.OFFSHORE_PUMP.get(), PUMP, Direction.WEST);
        place(h, ModItems.BOILER.get(), BOILER, Direction.NORTH);
        place(h, ModItems.STEAM_ENGINE.get(), ENGINE, Direction.NORTH);
        place(h, ModItems.SMALL_ELECTRIC_POLE.get(), POLE, Direction.NORTH);
        BoilerBlockEntity boiler = (BoilerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(BOILER));
        if (coal > 0) h.assertTrue(boiler.addFuel(COAL, coal) == coal, "fuel refused");
        return boiler;
    }

    /** Like {@link BeltGameTest#place}, reporting success instead of asserting it. */
    static boolean tryPlace(GameTestHelper h, BlockItem item, BlockPos rel, Direction facing) {
        BlockPos abs = MultiblockBlockItem.targetFor(item, h.absolutePos(rel), facing);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        BlockPlaceContext ctx = new BlockPlaceContext(h.getLevel(), null, InteractionHand.MAIN_HAND, new ItemStack(item), hit) {
            @Override
            public Direction getHorizontalDirection() { return facing; }
        };
        return item.place(ctx).consumesAction();
    }

    static boolean pipeSide(GameTestHelper h, BlockPos rel, Direction side) {
        return h.getLevel().getBlockState(h.absolutePos(rel)).getValue(PipeBlock.SIDES.get(side));
    }

    // --- steam power -----------------------------------------------------------------------------

    /** Water → pump → pipe → boiler (coal) → engine → pole: a 900 kW load is fully supplied after 100 ticks. */
    @GameTest(template = "empty16", timeoutTicks = 120)
    public static void steamSetupPowersConsumer(GameTestHelper h) {
        steamPlant(h, 5);
        TestPower.Load load = TestPower.load(h, new BlockPos(10, 1, 4), KW_900);
        h.runAtTickTime(100, () -> {
            h.assertTrue(pipeSide(h, PIPE, Direction.WEST) && pipeSide(h, PIPE, Direction.EAST), "pipe not connected to pump and boiler");
            h.assertTrue(load.satisfaction() >= 0.9999, "satisfaction " + load.satisfaction()); // R15: asymptotic flow
            SteamEngineBlockEntity engine = (SteamEngineBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(ENGINE));
            h.assertTrue(engine.isWorking(), "engine not working");
            BoilerBlockEntity boiler = (BoilerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(BOILER));
            h.assertTrue(boiler.isWorking(), "boiler not burning");
            h.succeed();
        });
    }

    /** 1800 kW of loads on one 900 kW engine: satisfaction 0.5. */
    @GameTest(template = "empty16", timeoutTicks = 120)
    public static void oneEngineTwoLoads(GameTestHelper h) {
        steamPlant(h, 5);
        TestPower.Load a = TestPower.load(h, new BlockPos(10, 1, 4), KW_900);
        TestPower.Load b = TestPower.load(h, new BlockPos(10, 1, 5), KW_900);
        h.runAtTickTime(100, () -> {
            h.assertTrue(Math.abs(a.satisfaction() - 0.5) <= 0.02, "satisfaction " + a.satisfaction());
            h.assertTrue(a.satisfaction() == b.satisfaction(), "loads differ: " + a.satisfaction() + " vs " + b.satisfaction());
            h.succeed();
        });
    }

    /** Boiler without fuel: no steam, satisfaction 0, an electric inserter in the pole's area stands still. */
    @GameTest(template = "empty16", timeoutTicks = 120)
    public static void noFuelNoPower(GameTestHelper h) {
        BoilerBlockEntity boiler = steamPlant(h, 0);
        TestPower.Load load = TestPower.load(h, new BlockPos(10, 1, 4), KW_900);
        BlockPos src = new BlockPos(9, 1, 6);
        container(h, Blocks.CHEST, src, new ItemStack(ModItems.IRON_PLATE.get(), 3));
        h.setBlock(new BlockPos(11, 1, 6), Blocks.CHEST);
        place(h, ModItems.INSERTER.get(), new BlockPos(10, 1, 6), Direction.EAST);
        InserterBlockEntity ins = inserter(h, new BlockPos(10, 1, 6));
        h.runAtTickTime(100, () -> {
            h.assertTrue(boiler.water().amount() > 0, "no water reached the boiler");
            h.assertTrue(boiler.steam().amount() == 0, "steam without fuel: " + boiler.steam().amount());
            h.assertTrue(load.satisfaction() == 0, "satisfaction " + load.satisfaction());
            h.assertTrue(ins.satisfaction() == 0, "inserter satisfaction " + ins.satisfaction());
            h.assertTrue(ins.logic().held() == null && ins.logic().armProgress() == 0, "inserter moved");
            h.assertTrue(count(h, src, ModItems.IRON_PLATE.get()) == 3, "source changed");
            h.succeed();
        });
    }

    // --- poles -----------------------------------------------------------------------------------

    /** Review Focus 4: a consumer 4 blocks from a powered pole (supply radius 2) gets 0; one 2 blocks away gets 1. */
    @GameTest(template = "empty16", timeoutTicks = 40)
    public static void consumerOutsidePoleZero(GameTestHelper h) {
        TestPower.powerAt(h, new BlockPos(4, 1, 8));
        TestPower.Load near = TestPower.load(h, new BlockPos(6, 1, 8), KW_900);
        TestPower.Load far = TestPower.load(h, new BlockPos(8, 1, 8), KW_900);
        h.runAtTickTime(20, () -> {
            h.assertTrue(near.satisfaction() == 1.0, "near " + near.satisfaction());
            h.assertTrue(far.satisfaction() == 0, "far " + far.satisfaction());
            h.succeed();
        });
    }

    /**
     * Poles A–B–C in a row 5 apart (wire reach 7.5; A–C is 10): wires A–B and B–C. The producer is
     * at A, the load at C. Breaking B splits the grid: the load gets 0 within 2 ticks and A loses
     * its wire.
     */
    @GameTest(template = "empty16", timeoutTicks = 60)
    public static void breakingPoleSplitsGrid(GameTestHelper h) {
        BlockPos a = new BlockPos(2, 1, 8), b = new BlockPos(7, 1, 8), c = new BlockPos(12, 1, 8);
        TestPower.powerAt(h, a);
        place(h, ModItems.SMALL_ELECTRIC_POLE.get(), b, Direction.NORTH);
        place(h, ModItems.SMALL_ELECTRIC_POLE.get(), c, Direction.NORTH);
        TestPower.Load load = TestPower.load(h, new BlockPos(13, 1, 8), KW_900);
        ElectricPoleBlockEntity poleA = (ElectricPoleBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(a));
        ElectricPoleBlockEntity poleB = (ElectricPoleBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(b));
        h.runAtTickTime(20, () -> {
            h.assertTrue(load.satisfaction() == 1.0, "not powered through the chain: " + load.satisfaction());
            h.assertTrue(poleA.connections().equals(List.of(h.absolutePos(b))), "A wires " + poleA.connections());
            h.assertTrue(poleB.connections().size() == 2, "B wires " + poleB.connections());
            h.getLevel().destroyBlock(h.absolutePos(b), false);
        });
        h.runAtTickTime(22, () -> {
            h.assertTrue(load.satisfaction() == 0, "still powered after the split: " + load.satisfaction());
            h.assertTrue(poleA.connections().isEmpty(), "A wires " + poleA.connections());
            h.succeed();
        });
    }

    /** Sticky-satisfaction fix: breaking the only pole stops a powered electric inserter within 2 ticks. */
    @GameTest(template = "empty16", timeoutTicks = 60)
    public static void breakingOnlyPoleStopsConsumer(GameTestHelper h) {
        BlockPos pole = new BlockPos(6, 1, 6);
        h.setBlock(new BlockPos(6, 1, 8), Blocks.IRON_BLOCK); // producer cell, covered by the pole only
        TestPower.unlimitedProducer(h, new BlockPos(6, 1, 8));
        place(h, ModItems.SMALL_ELECTRIC_POLE.get(), pole, Direction.NORTH);
        place(h, ModItems.INSERTER.get(), new BlockPos(5, 1, 5), Direction.EAST);
        InserterBlockEntity ins = inserter(h, new BlockPos(5, 1, 5));
        h.runAtTickTime(20, () -> {
            h.assertTrue(ins.satisfaction() == 1.0, "not powered: " + ins.satisfaction());
            h.getLevel().destroyBlock(h.absolutePos(pole), false);
        });
        h.runAtTickTime(22, () -> {
            h.assertTrue(ins.satisfaction() == 0, "still powered: " + ins.satisfaction());
            h.succeed();
        });
    }

    // --- pump, pipes, boiler ---------------------------------------------------------------------

    /** The pump needs a water source at its back; it stops when the water is removed. */
    @GameTest(template = "empty16", timeoutTicks = 60)
    public static void pumpNeedsWater(GameTestHelper h) {
        h.assertFalse(tryPlace(h, (BlockItem) ModItems.OFFSHORE_PUMP.get(), new BlockPos(10, 1, 4), Direction.WEST), "placed without water");
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(10, 1, 4))).isAir(), "pump block left behind");
        walledWater(h, WATER);
        h.assertTrue(tryPlace(h, (BlockItem) ModItems.OFFSHORE_PUMP.get(), PUMP, Direction.WEST), "not placed at the water");
        OffshorePumpBlockEntity pump = (OffshorePumpBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(PUMP));
        h.runAtTickTime(5, () -> {
            h.assertTrue(pump.water().amount() == SteamPlant.PUMP_CAPACITY, "pumped " + pump.water().amount());
            h.setBlock(WATER, Blocks.STONE);
            pump.water().extract(pump.water().amount());
        });
        h.runAtTickTime(30, () -> {
            h.assertTrue(pump.water().amount() == 0, "pumped without water: " + pump.water().amount());
            h.assertFalse(pump.isWorking(), "still working");
            h.succeed();
        });
    }

    /**
     * Pipes connect to fluid ports only (also ports on part cells, placed after the pipe), expose an
     * IFluidHandler, and a port's filter keeps water out of the boiler's steam box.
     */
    @GameTest(template = "empty16", timeoutTicks = 40)
    public static void pipesConnectOnlyToPorts(GameTestHelper h) {
        BlockPos rightWater = new BlockPos(8, 1, 8); // next to the right water port (part cell (7,8))
        BlockPos steam = new BlockPos(6, 1, 6);      // next to the steam port (part cell (6,7))
        BlockPos plain = new BlockPos(6, 1, 9);      // next to the near row's middle cell: no port
        place(h, ModItems.PIPE.get(), rightWater, Direction.NORTH);
        place(h, ModItems.PIPE.get(), steam, Direction.NORTH);
        place(h, ModItems.PIPE.get(), plain, Direction.NORTH);
        place(h, ModItems.BOILER.get(), BOILER, Direction.NORTH);
        h.assertTrue(pipeSide(h, rightWater, Direction.WEST), "no connection to the right water port");
        h.assertTrue(pipeSide(h, steam, Direction.SOUTH), "no connection to the steam port");
        h.assertFalse(pipeSide(h, plain, Direction.NORTH), "connected to a cell without a port");

        IFluidHandler pipe = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(steam), Direction.UP);
        h.assertTrue(pipe != null, "pipe has no fluid handler");
        h.assertTrue(pipe.fill(new FluidStack(Fluids.WATER, 80), IFluidHandler.FluidAction.EXECUTE) == 80, "pipe refused water");
        IFluidHandler port = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(new BlockPos(7, 1, 8)), Direction.EAST);
        h.assertTrue(port != null, "water port has no fluid handler");
        h.assertTrue(port.fill(new FluidStack(Fluids.WATER, 50), IFluidHandler.FluidAction.EXECUTE) == 50, "port refused water");
        BoilerBlockEntity boiler = (BoilerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(BOILER));
        h.runAtTickTime(20, () -> {
            h.assertTrue(boiler.water().amount() > 0, "water port box empty");
            h.assertTrue(boiler.steam().amount() == 0 && boiler.steam().fluid() == null, "water entered the steam box");
            PipeBlockEntity p = (PipeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(steam));
            h.assertTrue(p.box().amount() == 80, "water left the pipe through the steam port: " + p.box().amount());
            h.succeed();
        });
    }

    /** Review Focus 1: water, steam and fuel survive save/load. */
    @GameTest(template = "empty16")
    public static void boilerNbtRoundTrip(GameTestHelper h) {
        place(h, ModItems.BOILER.get(), BOILER, Direction.NORTH);
        BoilerBlockEntity be = (BoilerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(BOILER));
        be.water().insert("water", 123.5);
        be.steam().insert("steam", 42.25);
        be.addFuel(COAL, 7);
        be.burner().draw(1000); // starts one coal: 6 left + a partial one
        var registries = h.getLevel().registryAccess();
        BoilerBlockEntity copy = new BoilerBlockEntity(be.getBlockPos(), be.getBlockState());
        copy.loadWithComponents(be.saveWithoutMetadata(registries), registries);
        h.assertTrue(copy.water().amount() == 123.5 && "water".equals(copy.water().fluid()), "water " + copy.water().amount());
        h.assertTrue(copy.steam().amount() == 42.25 && "steam".equals(copy.steam().fluid()), "steam " + copy.steam().amount());
        h.assertTrue(copy.burner().fuel().equals(Map.of(COAL, 6)), "fuel " + copy.burner().fuel());
        h.assertTrue(copy.burner().remainingJ() == be.burner().remainingJ() && copy.burner().remainingJ() > 0,
                "remaining " + copy.burner().remainingJ());
        h.succeed();
    }

    /**
     * Inserters fill the boiler's fuel slot up to the automated limit 5 and no further: long after
     * that (several more 33-tick cycles) it still holds exactly 5 and the inserter waits with coal
     * in its hand. All 11 coal are accounted for: chest + hand + inserter slot + boiler + the one
     * the inserter is burning (4 MJ lasts ~850 moving ticks at 94.2 kW, so only one has started).
     */
    @GameTest(template = "empty16", timeoutTicks = 380)
    public static void boilerTakesFuelFromInserter(GameTestHelper h) {
        place(h, ModItems.BOILER.get(), BOILER, Direction.NORTH);
        BlockPos src = new BlockPos(5, 1, 10);
        container(h, Blocks.CHEST, src, new ItemStack(Items.COAL, 10));
        place(h, ModItems.BURNER_INSERTER.get(), new BlockPos(5, 1, 9), Direction.NORTH); // drops into the boiler at (5,8)
        InserterBlockEntity ins = inserter(h, new BlockPos(5, 1, 9));
        ins.addFuel(COAL, 1);
        BoilerBlockEntity boiler = (BoilerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(BOILER));
        h.runAtTickTime(360, () -> {
            int inBoiler = boiler.burner().fuel().getOrDefault(COAL, 0);
            h.assertTrue(inBoiler == 5, "boiler fuel " + inBoiler);
            h.assertTrue(COAL.equals(ins.logic().held()), "inserter should wait holding coal, holds " + ins.logic().held());
            h.assertTrue(ins.burner().remainingJ() > 0, "inserter burns nothing");
            int inChest = count(h, src, Items.COAL);
            int slot = ins.burner().fuel().getOrDefault(COAL, 0);
            int total = inChest + 1 + slot + inBoiler + 1;
            h.assertTrue(total == 11, "coal total " + total + " (chest " + inChest + ", slot " + slot + ")");
            h.succeed();
        });
    }

    /** One 900 kW load covered by two poles of the same network, one engine: supplied once, satisfaction 1. */
    @GameTest(template = "empty16", timeoutTicks = 120)
    public static void loadUnderTwoPolesCountedOnce(GameTestHelper h) {
        steamPlant(h, 5);
        place(h, ModItems.SMALL_ELECTRIC_POLE.get(), new BlockPos(11, 1, 6), Direction.NORTH); // wired to (9,4); both cover (10,5)
        TestPower.Load load = TestPower.load(h, new BlockPos(10, 1, 5), KW_900);
        ElectricPoleBlockEntity second = (ElectricPoleBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(11, 1, 6)));
        h.runAtTickTime(100, () -> {
            h.assertTrue(second.connections().equals(List.of(h.absolutePos(POLE))), "second pole wires " + second.connections());
            h.assertTrue(load.satisfaction() >= 0.9999, "satisfaction " + load.satisfaction());
            h.succeed();
        });
    }

    // --- three-block small electric pole ---------------------------------------------------------

    /** Placing the item builds the lower block (with the block entity), the middle and the top. */
    @GameTest(template = "empty16")
    public static void polePlacesThreeParts(GameTestHelper h) {
        BlockPos pole = new BlockPos(4, 1, 4);
        place(h, ModItems.SMALL_ELECTRIC_POLE.get(), pole, Direction.NORTH);
        for (int part = 0; part < ElectricPoleBlock.HEIGHT; part++) {
            h.assertBlockProperty(pole.above(part), ElectricPoleBlock.PART, part);
        }
        h.assertTrue(h.getBlockEntity(pole) instanceof ElectricPoleBlockEntity, "no block entity on the lower block");
        h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(pole.above(2))) == null, "block entity on the top");
        h.succeed();
    }

    /** The pole needs the two cells above it free. */
    @GameTest(template = "empty16")
    public static void poleNeedsRoomAbove(GameTestHelper h) {
        BlockPos pole = new BlockPos(4, 1, 4);
        h.setBlock(pole.above(2), Blocks.STONE);
        h.assertFalse(tryPlace(h, (BlockItem) ModItems.SMALL_ELECTRIC_POLE.get(), pole, Direction.NORTH), "placed under stone");
        h.assertBlockNotPresent(ModBlocks.SMALL_ELECTRIC_POLE.get(), pole);
        h.succeed();
    }

    /** An entity standing where the upper parts go blocks placing the pole. */
    @GameTest(template = "empty16")
    public static void poleNeedsNoEntityAbove(GameTestHelper h) {
        BlockPos pole = new BlockPos(4, 1, 4);
        h.spawn(EntityType.ARMOR_STAND, pole.above(2));
        h.assertFalse(tryPlace(h, (BlockItem) ModItems.SMALL_ELECTRIC_POLE.get(), pole, Direction.NORTH), "placed into an entity");
        h.assertBlockNotPresent(ModBlocks.SMALL_ELECTRIC_POLE.get(), pole);
        h.succeed();
    }

    /** A placed pole stands full height; a lone lower block (a pole from an older world) does not. */
    @GameTest(template = "empty16")
    public static void poleFullHeightOnlyWithItsUpperParts(GameTestHelper h) {
        BlockPos placed = new BlockPos(3, 1, 3), lone = new BlockPos(8, 1, 3);
        place(h, ModItems.SMALL_ELECTRIC_POLE.get(), placed, Direction.NORTH);
        h.setBlock(lone, ModBlocks.SMALL_ELECTRIC_POLE.get());
        h.assertTrue(ElectricPoleBlock.isFullHeight(h.getLevel(), h.absolutePos(placed)), "placed pole not full height");
        h.assertFalse(ElectricPoleBlock.isFullHeight(h.getLevel(), h.absolutePos(lone)), "lone lower block full height");
        h.succeed();
    }

    /** Breaking any part breaks the whole pole, which drops one item. */
    @GameTest(template = "empty16")
    public static void breakingAnyPoleBlockBreaksAllAndDropsOne(GameTestHelper h) {
        BlockPos[] poles = {new BlockPos(2, 1, 2), new BlockPos(7, 1, 2), new BlockPos(12, 1, 2)};
        for (int part = 0; part < ElectricPoleBlock.HEIGHT; part++) {
            BlockPos pole = poles[part];
            place(h, ModItems.SMALL_ELECTRIC_POLE.get(), pole, Direction.NORTH);
            h.getLevel().destroyBlock(h.absolutePos(pole.above(part)), true);
            for (int i = 0; i < ElectricPoleBlock.HEIGHT; i++) h.assertBlockPresent(Blocks.AIR, pole.above(i));
            h.assertItemEntityCountIs(ModItems.SMALL_ELECTRIC_POLE.get(), pole.above(1), 2.5, 1);
        }
        h.succeed();
    }
}
