package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.place;

import dev.minetorio.content.power.AccumulatorBlockEntity;
import dev.minetorio.content.power.MultiblockPoleBlockEntity;
import dev.minetorio.content.power.PowerPoleEntity;
import dev.minetorio.content.power.SolarPanelBlockEntity;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.power.Accumulator;
import dev.minetorio.core.power.PoleType;
import dev.minetorio.core.power.SolarLogic;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Solar panels, accumulators and the medium/big pole and substation. Tests that set the time of
 * day each run in their own batch, so no other test sees the clock jump.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class EnergyGameTest {
    private static final long NOON = 6000, MIDNIGHT = 18000;

    private static void clearSkies(GameTestHelper h, long dayTime) {
        ServerLevel level = h.getLevel();
        level.setDayTime(dayTime);
        level.setWeatherParameters(12000, 0, false, false);
    }

    /**
     * The test runner encases each test area in barriers (a ceiling included), which would cover the
     * panels: removes the barriers over the given columns (relative x0..x1, z0..x1), up to the world surface.
     */
    private static void openSky(GameTestHelper h, int x0, int z0, int x1, int z1) {
        ServerLevel level = h.getLevel();
        BlockPos base = h.absolutePos(BlockPos.ZERO);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                BlockPos col = h.absolutePos(new BlockPos(x, 0, z));
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, col.getX(), col.getZ());
                for (int y = base.getY() + 1; y < top; y++) {
                    BlockPos p = new BlockPos(col.getX(), y, col.getZ());
                    if (level.getBlockState(p).is(Blocks.BARRIER)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static long expectedSolar(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        return SolarLogic.output(level.getTimeOfDay(1f), level.getRainLevel(1f), level.getThunderLevel(1f), true);
    }

    private static <T> T be(GameTestHelper h, BlockPos rel, Class<T> type) {
        return type.cast(h.getLevel().getBlockEntity(h.absolutePos(rel)));
    }

    // --- solar ------------------------------------------------------------------------------------

    /** Noon: the panel gives the daylight curve's output (60 kW in clear weather); midnight: nothing. */
    @GameTest(template = "empty16", timeoutTicks = 100, batch = "energy_daylight")
    public static void solarPanelDayVersusNight(GameTestHelper h) {
        clearSkies(h, NOON);
        openSky(h, 0, 0, 15, 15);
        BlockPos panelPos = new BlockPos(2, 1, 4); // facing north: x 2..4, z 2..4
        place(h, ModItems.SOLAR_PANEL.get(), panelPos, Direction.NORTH);
        place(h, ModItems.SMALL_ELECTRIC_POLE.get(), new BlockPos(6, 1, 3), Direction.NORTH);
        TestPower.Load load = TestPower.load(h, new BlockPos(7, 1, 4), 1000);
        SolarPanelBlockEntity panel = be(h, panelPos, SolarPanelBlockEntity.class);
        h.runAtTickTime(30, () -> {
            h.assertTrue(panel.isExposed(), "panel does not see the sky");
            h.assertTrue(panel.isConnected(), "panel not in the pole's network");
            long want = expectedSolar(h);
            h.assertTrue(want > SolarLogic.PEAK / 2, "noon daylight " + want);
            h.assertTrue(panel.output() == want, "noon output " + panel.output() + " != " + want);
            h.assertTrue(load.satisfaction() == 1.0, "load not powered by the sun: " + load.satisfaction());
            h.getLevel().setDayTime(MIDNIGHT);
        });
        h.runAtTickTime(35, () -> {
            h.assertTrue(panel.output() == 0, "midnight output " + panel.output());
            h.assertTrue(load.satisfaction() == 0, "load powered at night: " + load.satisfaction());
            h.getLevel().setDayTime(NOON);
            h.succeed();
        });
    }

    /** A block above one of its nine cells shuts the panel off; an uncovered twin keeps working. */
    @GameTest(template = "empty16", timeoutTicks = 100, batch = "energy_covered")
    public static void coveredSolarPanelGivesNothing(GameTestHelper h) {
        clearSkies(h, NOON);
        openSky(h, 0, 0, 15, 15);
        BlockPos open = new BlockPos(1, 1, 4), covered = new BlockPos(9, 1, 4);
        place(h, ModItems.SOLAR_PANEL.get(), open, Direction.NORTH);
        place(h, ModItems.SOLAR_PANEL.get(), covered, Direction.NORTH);
        h.setBlock(new BlockPos(11, 5, 2), Blocks.GLASS); // over the far corner, a few blocks up
        SolarPanelBlockEntity a = be(h, open, SolarPanelBlockEntity.class), b = be(h, covered, SolarPanelBlockEntity.class);
        h.runAtTickTime(SolarLogic.EXPOSURE_RECHECK_TICKS + 5, () -> {
            h.assertTrue(a.isExposed() && a.output() > 0, "open panel " + a.output());
            h.assertTrue(!b.isExposed() && b.output() == 0, "covered panel " + b.output());
            h.setBlock(new BlockPos(11, 5, 2), Blocks.AIR);
        });
        h.runAtTickTime(2 * SolarLogic.EXPOSURE_RECHECK_TICKS + 10, () -> {
            h.assertTrue(b.isExposed() && b.output() > 0, "uncovered panel still off: " + b.output());
            h.succeed();
        });
    }

    // --- accumulator ------------------------------------------------------------------------------

    /**
     * By day the panel feeds a 20 kW load and its surplus (40 kW) charges the accumulator; at night
     * the accumulator alone keeps the load fully powered.
     */
    @GameTest(template = "empty16", timeoutTicks = 200, batch = "energy_night")
    public static void accumulatorChargesFromSurplusAndCarriesTheNight(GameTestHelper h) {
        clearSkies(h, NOON);
        openSky(h, 0, 0, 15, 15);
        BlockPos panelPos = new BlockPos(2, 1, 4), accPos = new BlockPos(7, 1, 4);
        place(h, ModItems.SOLAR_PANEL.get(), panelPos, Direction.NORTH);
        place(h, ModItems.ACCUMULATOR.get(), accPos, Direction.NORTH); // x 7..8, z 3..4
        place(h, ModItems.SMALL_ELECTRIC_POLE.get(), new BlockPos(6, 1, 2), Direction.NORTH);
        TestPower.Load load = TestPower.load(h, new BlockPos(6, 1, 4), 1000);
        AccumulatorBlockEntity acc = be(h, accPos, AccumulatorBlockEntity.class);
        SolarPanelBlockEntity panel = be(h, panelPos, SolarPanelBlockEntity.class);
        long[] dayEnergy = new long[1];
        h.runAtTickTime(60, () -> {
            h.assertTrue(load.satisfaction() == 1.0, "day load " + load.satisfaction());
            long surplus = panel.output() - 1000;
            h.assertTrue(acc.accumulator().flow() == Accumulator.Flow.CHARGING, "not charging: " + acc.accumulator().flow());
            h.assertTrue(acc.accumulator().lastFlow() == surplus, "charged " + acc.accumulator().lastFlow() + " != surplus " + surplus);
            dayEnergy[0] = acc.accumulator().energy();
            h.assertTrue(dayEnergy[0] > 40 * surplus, "stored only " + dayEnergy[0]);
            h.getLevel().setDayTime(MIDNIGHT);
        });
        h.runAtTickTime(80, () -> {
            h.assertTrue(panel.output() == 0, "sun at midnight: " + panel.output());
            h.assertTrue(load.satisfaction() == 1.0, "night load " + load.satisfaction());
            h.assertTrue(acc.accumulator().flow() == Accumulator.Flow.DISCHARGING, "not discharging: " + acc.accumulator().flow());
            h.assertTrue(acc.accumulator().energy() < dayEnergy[0], "energy did not drop");
            h.getLevel().setDayTime(NOON);
            h.succeed();
        });
    }

    // --- poles ------------------------------------------------------------------------------------

    /**
     * Medium poles wire at 9 (not 10) and supply 7×7; big poles wire at 30 (not 31, measured between
     * their 2×2 centers) and supply 4×4 around their footprint.
     */
    @GameTest(template = "empty32", timeoutTicks = 60)
    public static void poleTypesConnectAtTheirReachAndSupplyTheirArea(GameTestHelper h) {
        BlockPos m1 = new BlockPos(1, 1, 3), m2 = new BlockPos(10, 1, 3), m3 = new BlockPos(20, 1, 3);
        for (BlockPos p : List.of(m1, m2, m3)) place(h, ModItems.MEDIUM_ELECTRIC_POLE.get(), p, Direction.NORTH);
        h.setBlock(new BlockPos(1, 1, 5), Blocks.IRON_BLOCK);
        TestPower.unlimitedProducer(h, new BlockPos(1, 1, 5));
        TestPower.Load inMedium = TestPower.load(h, new BlockPos(13, 1, 6), 1000); // m2's 7x7 corner
        TestPower.Load outMedium = TestPower.load(h, new BlockPos(14, 1, 3), 1000); // one past it
        TestPower.Load far = TestPower.load(h, new BlockPos(20, 1, 6), 1000); // m3: not wired

        // big poles facing north: footprints x 0..1 / 30..31, z 15..16 (centers 30 apart); b3 and b4 are
        // centers (1, 28) and (31, 20): 31.05 apart
        BlockPos b1 = new BlockPos(0, 1, 16), b2 = new BlockPos(30, 1, 16), b3 = new BlockPos(0, 1, 28), b4 = new BlockPos(30, 1, 20);
        for (BlockPos p : List.of(b1, b2, b3, b4)) place(h, ModItems.BIG_ELECTRIC_POLE.get(), p, Direction.NORTH);
        h.setBlock(new BlockPos(2, 1, 17), Blocks.IRON_BLOCK);
        TestPower.unlimitedProducer(h, new BlockPos(2, 1, 17));
        TestPower.Load inBig = TestPower.load(h, new BlockPos(29, 1, 14), 1000); // b2's 4x4: x 29..32, z 14..17
        TestPower.Load outBig = TestPower.load(h, new BlockPos(28, 1, 15), 1000);

        h.runAtTickTime(20, () -> {
            PowerPoleEntity p1 = be(h, m1, MultiblockPoleBlockEntity.class), p2 = be(h, m2, MultiblockPoleBlockEntity.class);
            h.assertTrue(p1.poleType() == PoleType.MEDIUM, "not a medium pole");
            h.assertTrue(p1.connections().equals(List.of(h.absolutePos(m2))), "m1 wires " + p1.connections());
            h.assertTrue(p2.connections().equals(List.of(h.absolutePos(m1))), "m2 wires " + p2.connections() + " (m3 is 10 away)");
            h.assertTrue(inMedium.satisfaction() == 1.0, "medium 7x7 corner unpowered: " + inMedium.satisfaction());
            h.assertTrue(outMedium.satisfaction() == 0, "outside medium area powered");
            h.assertTrue(far.satisfaction() == 0, "unwired medium pole powered");

            PowerPoleEntity q1 = be(h, b1, MultiblockPoleBlockEntity.class), q3 = be(h, b3, MultiblockPoleBlockEntity.class);
            h.assertTrue(q1.connections().contains(h.absolutePos(b2)), "big poles 30 apart not wired: " + q1.connections());
            h.assertTrue(!q3.connections().contains(h.absolutePos(b4)), "big poles 31 apart wired");
            h.assertTrue(inBig.satisfaction() == 1.0, "big pole 4x4 unpowered: " + inBig.satisfaction());
            h.assertTrue(outBig.satisfaction() == 0, "outside big pole area powered");
            h.succeed();
        });
    }

    /** A substation supplies exactly the 18×18 square around its 2×2 footprint. */
    @GameTest(template = "empty32", timeoutTicks = 60)
    public static void substationSupplies18x18(GameTestHelper h) {
        BlockPos sub = new BlockPos(12, 1, 13); // facing north: x 12..13, z 12..13 -> supply x 4..21, z 4..21
        place(h, ModItems.SUBSTATION.get(), sub, Direction.NORTH);
        h.setBlock(new BlockPos(15, 1, 15), Blocks.IRON_BLOCK);
        TestPower.unlimitedProducer(h, new BlockPos(15, 1, 15));
        List<TestPower.Load> in = List.of(TestPower.load(h, new BlockPos(4, 1, 4), 1000), TestPower.load(h, new BlockPos(21, 1, 21), 1000),
                TestPower.load(h, new BlockPos(4, 1, 21), 1000), TestPower.load(h, new BlockPos(21, 1, 4), 1000));
        List<TestPower.Load> out = List.of(TestPower.load(h, new BlockPos(3, 1, 12), 1000), TestPower.load(h, new BlockPos(22, 1, 12), 1000),
                TestPower.load(h, new BlockPos(12, 1, 3), 1000), TestPower.load(h, new BlockPos(12, 1, 22), 1000));
        h.runAtTickTime(20, () -> {
            for (TestPower.Load l : in) h.assertTrue(l.satisfaction() == 1.0, "corner of the 18x18 unpowered");
            for (TestPower.Load l : out) h.assertTrue(l.satisfaction() == 0, "outside the 18x18 powered");
            h.assertTrue(be(h, sub, MultiblockPoleBlockEntity.class).poleType() == PoleType.SUBSTATION, "not a substation");
            h.succeed();
        });
    }
}
