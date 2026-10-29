package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.place;

import dev.minetorio.content.fluid.FluidAccess;
import dev.minetorio.content.fluid.FluidNetworkManager;
import dev.minetorio.content.fluid.FluidNodeEntity;
import dev.minetorio.content.fluid.PipeBlock;
import dev.minetorio.content.fluid.PipeBlockEntity;
import dev.minetorio.content.fluid.PipeSide;
import dev.minetorio.content.fluid.PumpBlockEntity;
import dev.minetorio.content.fluid.StorageTankBlockEntity;
import dev.minetorio.content.fluid.UndergroundPipeBlock;
import dev.minetorio.content.power.BoilerBlockEntity;
import dev.minetorio.content.power.SteamEngineBlockEntity;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.Units;
import dev.minetorio.core.fluid.FluidData;
import dev.minetorio.core.fluid.FluidNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class FluidGameTest {
    // --- helpers ---------------------------------------------------------------------------------------

    private static void pipe(GameTestHelper h, int x, int y, int z) {
        place(h, ModItems.PIPE.get(), new BlockPos(x, y, z), Direction.NORTH);
    }

    /** Places an item looking {@code look} (for underground pipes, which use the full look direction); reports success. */
    static boolean placeLooking(GameTestHelper h, BlockItem item, BlockPos rel, Direction look) {
        BlockPos abs = h.absolutePos(rel);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        BlockPlaceContext ctx = new BlockPlaceContext(h.getLevel(), null, InteractionHand.MAIN_HAND, new ItemStack(item), hit) {
            @Override
            public Direction getHorizontalDirection() { return look.getAxis().isHorizontal() ? look : Direction.NORTH; }

            @Override
            public Direction getNearestLookingDirection() { return look; }
        };
        return item.place(ctx).consumesAction();
    }

    static FluidNetwork.Segment segment(GameTestHelper h, BlockPos rel) {
        return FluidAccess.segmentAt(h.getLevel(), h.absolutePos(rel));
    }

    private static FluidNetworkManager manager(GameTestHelper h) {
        return FluidNetworkManager.get(h.getLevel());
    }

    // --- steam over a long 3D pipe -------------------------------------------------------------------

    /**
     * Water from the offshore pump goes up 5 blocks, 5 across and down again (16 pipes, one segment)
     * to a boiler, which feeds the engine: a 900 kW load is fully supplied.
     */
    @GameTest(template = "empty16", timeoutTicks = 260)
    public static void longVerticalPipeFeedsBoiler(GameTestHelper h) {
        PowerGameTest.walledWater(h, new BlockPos(1, 1, 8));
        for (int y = 1; y <= 6; y++) pipe(h, 4, y, 8);
        for (int x = 5; x <= 8; x++) pipe(h, x, 6, 8);
        for (int y = 6; y >= 1; y--) pipe(h, 9, y, 8);
        place(h, ModItems.OFFSHORE_PUMP.get(), new BlockPos(3, 1, 8), Direction.WEST);
        place(h, ModItems.BOILER.get(), new BlockPos(10, 1, 8), Direction.NORTH);    // x 10..12, z 7..8; water port (10,8) west
        place(h, ModItems.STEAM_ENGINE.get(), new BlockPos(10, 1, 6), Direction.NORTH); // x 10..12, z 2..6
        place(h, ModItems.SMALL_ELECTRIC_POLE.get(), new BlockPos(13, 1, 4), Direction.NORTH);
        BoilerBlockEntity boiler = (BoilerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10, 1, 8)));
        h.assertTrue(boiler.addFuel("minetorio:coal", 5) == 5, "fuel refused");
        TestPower.Load load = TestPower.load(h, new BlockPos(14, 1, 4), Units.kw(900));
        h.runAtTickTime(240, () -> {
            FluidNetwork.Segment s = segment(h, new BlockPos(4, 6, 8));
            h.assertTrue(s != null && s == segment(h, new BlockPos(9, 1, 8)), "the 3D run is not one segment");
            h.assertTrue(s.length() == 16, "segment length " + s.length());
            h.assertTrue("water".equals(s.fluid()), "segment holds " + s.fluid());
            h.assertTrue(boiler.water().amount() > 0, "no water reached the boiler");
            SteamEngineBlockEntity engine = (SteamEngineBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10, 1, 6)));
            h.assertTrue(engine.isWorking(), "engine not working");
            h.assertTrue(load.satisfaction() >= 0.9999, "satisfaction " + load.satisfaction());
            h.succeed();
        });
    }

    // --- side toggles and mixing ---------------------------------------------------------------------

    /**
     * Two lines side by side, water and steam: with the water line's sides toward the other closed,
     * the steam line can be built next to it and both keep their fluid; reopening a side, or a pipe
     * that would join both lines, is refused.
     */
    @GameTest(template = "empty16", timeoutTicks = 40)
    public static void sideToggleIsolatesTwoFluids(GameTestHelper h) {
        for (int x = 2; x <= 6; x++) pipe(h, x, 1, 4);
        h.assertTrue(segment(h, new BlockPos(2, 1, 4)).insert("water", 400) == 400, "water refused");
        for (int x = 2; x <= 6; x++) {
            h.assertTrue(PipeBlock.toggle(h.getLevel(), h.absolutePos(new BlockPos(x, 1, 4)), Direction.SOUTH, null), "toggle failed");
            h.assertTrue(PipeBlock.side(h.getBlockState(new BlockPos(x, 1, 4)), Direction.SOUTH) == PipeSide.CAP, "no cap");
        }
        for (int x = 2; x <= 6; x++) pipe(h, x, 1, 5);
        FluidNetwork.Segment water = segment(h, new BlockPos(2, 1, 4)), steam = segment(h, new BlockPos(2, 1, 5));
        h.assertTrue(water != steam, "the lines joined through closed sides");
        h.assertTrue(PipeBlock.side(h.getBlockState(new BlockPos(3, 1, 5)), Direction.NORTH) == PipeSide.NONE, "arm drawn toward a cap");
        h.assertTrue(steam.insert("steam", 300) == 300, "steam refused");

        h.assertFalse(PipeBlock.toggle(h.getLevel(), h.absolutePos(new BlockPos(4, 1, 4)), Direction.SOUTH, null), "reopened into steam");
        h.assertTrue(PipeBlock.side(h.getBlockState(new BlockPos(4, 1, 4)), Direction.SOUTH) == PipeSide.CAP, "cap gone");
        pipe(h, 1, 1, 4); // joins the water line only
        h.assertFalse(PowerGameTest.tryPlace(h, (BlockItem) ModItems.PIPE.get(), new BlockPos(1, 1, 5), Direction.NORTH),
                "placed a pipe joining water and steam");
        h.assertTrue(h.getBlockState(new BlockPos(1, 1, 5)).isAir(), "pipe left behind");
        h.runAtTickTime(20, () -> {
            FluidNetwork.Segment w = segment(h, new BlockPos(6, 1, 4)), s = segment(h, new BlockPos(6, 1, 5));
            h.assertTrue("water".equals(w.fluid()) && Math.abs(w.amount() - 400) < 1e-6, "water line " + w.fluid() + " " + w.amount());
            h.assertTrue("steam".equals(s.fluid()) && Math.abs(s.amount() - 300) < 1e-6, "steam line " + s.fluid() + " " + s.amount());
            h.assertTrue(w.length() == 6 && s.length() == 5, "lengths " + w.length() + ", " + s.length());
            // closing a side in the middle of a line splits it
            PipeBlock.toggle(h.getLevel(), h.absolutePos(new BlockPos(4, 1, 5)), Direction.EAST, null);
            h.assertTrue(segment(h, new BlockPos(2, 1, 5)) != segment(h, new BlockPos(6, 1, 5)), "closing a side did not split");
            h.succeed();
        });
    }

    /** Shift + right-click aim: the face or arm hit picks the side. */
    @GameTest(template = "empty16")
    public static void clickedSideFollowsTheHit(GameTestHelper h) {
        BlockPos p = new BlockPos(0, 0, 0);
        h.assertTrue(PipeBlock.clickedSide(p, new Vec3(0.95, 0.5, 0.55)) == Direction.EAST, "east arm");
        h.assertTrue(PipeBlock.clickedSide(p, new Vec3(0.5, 0.75, 0.45)) == Direction.UP, "core top");
        h.assertTrue(PipeBlock.clickedSide(p, new Vec3(0.45, 0.71, 0.1)) == Direction.NORTH, "top of the north arm");
        h.succeed();
    }

    /** Breaking a pipe splits the segment; each part keeps its share. */
    @GameTest(template = "empty16", timeoutTicks = 20)
    public static void breakingAPipeSplitsTheSegment(GameTestHelper h) {
        for (int x = 2; x <= 6; x++) pipe(h, x, 1, 4);
        segment(h, new BlockPos(2, 1, 4)).insert("water", 500);
        h.getLevel().destroyBlock(h.absolutePos(new BlockPos(4, 1, 4)), false);
        h.runAtTickTime(2, () -> {
            FluidNetwork.Segment a = segment(h, new BlockPos(2, 1, 4)), b = segment(h, new BlockPos(6, 1, 4));
            h.assertTrue(a != b, "still one segment");
            h.assertTrue(Math.abs(a.amount() - 200) < 1e-6 && Math.abs(b.amount() - 200) < 1e-6, "amounts " + a.amount() + ", " + b.amount());
            h.succeed();
        });
    }

    // --- underground pipes ----------------------------------------------------------------------------

    /** A vertical underground pair 4 blocks apart joins the pipe above with the pipe below; a horizontal one too. */
    @GameTest(template = "empty16", timeoutTicks = 20)
    public static void undergroundVerticalPairConnects(GameTestHelper h) {
        BlockItem ug = (BlockItem) ModItems.PIPE_TO_GROUND.get();
        h.assertTrue(placeLooking(h, ug, new BlockPos(5, 6, 5), Direction.DOWN), "entrance not placed");
        h.assertTrue(placeLooking(h, ug, new BlockPos(5, 2, 5), Direction.DOWN), "exit not placed");
        h.assertTrue(h.getBlockState(new BlockPos(5, 6, 5)).getValue(UndergroundPipeBlock.FACING) == Direction.UP, "entrance facing");
        h.assertTrue(h.getBlockState(new BlockPos(5, 2, 5)).getValue(UndergroundPipeBlock.FACING) == Direction.DOWN, "exit facing");
        pipe(h, 5, 7, 5);
        pipe(h, 5, 1, 5);
        h.assertTrue(PipeBlock.side(h.getBlockState(new BlockPos(5, 7, 5)), Direction.DOWN) == PipeSide.PIPE, "top pipe not connected");
        // horizontal pair along x, 8 apart
        h.assertTrue(placeLooking(h, ug, new BlockPos(2, 1, 10), Direction.EAST), "h entrance");
        h.assertTrue(placeLooking(h, ug, new BlockPos(10, 1, 10), Direction.EAST), "h exit");
        pipe(h, 1, 1, 10);
        pipe(h, 11, 1, 10);
        h.runAtTickTime(2, () -> {
            FluidNetwork.Segment top = segment(h, new BlockPos(5, 7, 5));
            h.assertTrue(top != null && top == segment(h, new BlockPos(5, 1, 5)), "vertical pair not connected");
            h.assertTrue(top.length() == 4, "length " + top.length());
            h.assertTrue(top.insert("water", 300) == 300, "insert");
            FluidNetwork.Segment west = segment(h, new BlockPos(1, 1, 10));
            h.assertTrue(west != null && west == segment(h, new BlockPos(11, 1, 10)), "horizontal pair not connected");
            h.assertTrue(UndergroundPipeBlock.partner(h.getLevel(), h.absolutePos(new BlockPos(2, 1, 10)), Direction.WEST)
                    .equals(h.absolutePos(new BlockPos(10, 1, 10))), "partner");
            // breaking the exit cuts the tunnel
            h.getLevel().destroyBlock(h.absolutePos(new BlockPos(5, 2, 5)), false);
        });
        h.runAtTickTime(4, () -> {
            h.assertTrue(segment(h, new BlockPos(5, 7, 5)) != segment(h, new BlockPos(5, 1, 5)), "still connected without the exit");
            h.succeed();
        });
    }

    // --- tank and pump --------------------------------------------------------------------------------

    /**
     * Water from an offshore pump fills a storage tank through a pipe (the tank holds its share of the
     * segment, by capacity); a powered pump then drains it into a pipe line on the other side.
     */
    @GameTest(template = "empty16", timeoutTicks = 200)
    public static void tankFillsAndDrains(GameTestHelper h) {
        PowerGameTest.walledWater(h, new BlockPos(1, 1, 8));
        pipe(h, 4, 1, 8);
        pipe(h, 4, 1, 9);
        pipe(h, 4, 1, 10);
        place(h, ModItems.OFFSHORE_PUMP.get(), new BlockPos(3, 1, 8), Direction.WEST);
        // tank facing north at (5,1,10): x 5..7, z 8..10; left connection of the controller cell faces west (4,1,10)
        place(h, ModItems.STORAGE_TANK.get(), new BlockPos(5, 1, 10), Direction.NORTH);
        StorageTankBlockEntity tank = (StorageTankBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5, 1, 10)));
        h.runAtTickTime(60, () -> {
            FluidNetwork.Segment s = segment(h, new BlockPos(6, 2, 9)); // any tank cell
            h.assertTrue(s != null && s == segment(h, new BlockPos(4, 1, 8)), "tank not in the pipe's segment");
            h.assertTrue("water".equals(s.fluid()) && s.amount() > 2000, "tank holds " + s.amount());
            double share = tank.fluidNode().amount();
            h.assertTrue(Math.abs(share - s.amount() * FluidData.STORAGE_TANK_CAPACITY / s.capacity()) < 1e-6, "share " + share);
            // drain: a pump at the front-right connection (7,1,8) north -> (7,1,7), pushing north into pipes
            place(h, ModItems.PUMP.get(), new BlockPos(7, 1, 7), Direction.NORTH); // back cell (7,1,7), front (7,1,6)
            pipe(h, 7, 1, 5);
            pipe(h, 7, 1, 4);
            TestPower.powerAt(h, new BlockPos(9, 1, 6));
        });
        h.runAtTickTime(120, () -> {
            FluidNetwork.Segment out = segment(h, new BlockPos(7, 1, 4));
            h.assertTrue(out != null && out != segment(h, new BlockPos(6, 1, 9)), "the pump does not split segments");
            h.assertTrue("water".equals(out.fluid()) && out.amount() > 100, "nothing pumped out: " + out.amount());
            h.succeed();
        });
    }

    /** A pump moves fluid from one segment to the other only while powered; the two stay separate. */
    @GameTest(template = "empty16", timeoutTicks = 80)
    public static void pumpMovesFluidBetweenSegments(GameTestHelper h) {
        for (int x = 2; x <= 5; x++) pipe(h, x, 1, 5);
        place(h, ModItems.PUMP.get(), new BlockPos(6, 1, 5), Direction.EAST); // back (6,1,5), front (7,1,5)
        for (int x = 8; x <= 11; x++) pipe(h, x, 1, 5);
        segment(h, new BlockPos(2, 1, 5)).insert("water", 400);
        PumpBlockEntity pump = (PumpBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(6, 1, 5)));
        h.runAtTickTime(10, () -> {
            FluidNetwork.Segment b = segment(h, new BlockPos(11, 1, 5));
            h.assertTrue(b != segment(h, new BlockPos(2, 1, 5)), "pump joined the segments");
            h.assertTrue(b.amount() == 0, "pumped without power: " + b.amount());
            TestPower.powerAt(h, new BlockPos(6, 1, 7));
        });
        h.runAtTickTime(40, () -> {
            h.assertTrue(pump.satisfaction() > 0.99, "pump not powered: " + pump.satisfaction());
            FluidNetwork.Segment a = segment(h, new BlockPos(2, 1, 5)), b = segment(h, new BlockPos(11, 1, 5));
            h.assertTrue(b.amount() > 350, "segment B " + b.amount());
            h.assertTrue(a.amount() < 1e-6, "segment A " + a.amount());
            h.assertTrue(Math.abs(a.amount() + b.amount() + pump.box().amount() - 400) < 1e-6, "not conserved");
            h.succeed();
        });
    }

    // --- persistence ----------------------------------------------------------------------------------

    /**
     * Save/load: every node saves its share; after the nodes leave the network (as on chunk unload)
     * and come back from their saved data, the segment holds the same fluid and amount.
     */
    @GameTest(template = "empty16", timeoutTicks = 20)
    public static void saveLoadKeepsContents(GameTestHelper h) {
        for (int x = 2; x <= 4; x++) pipe(h, x, 1, 12);
        place(h, ModItems.STORAGE_TANK.get(), new BlockPos(5, 1, 12), Direction.NORTH); // left connection -> (4,1,12)
        h.runAtTickTime(1, () -> {
            FluidNetwork.Segment s = segment(h, new BlockPos(2, 1, 12));
            h.assertTrue(s.length() == 4, "tank not linked: length " + s.length());
            h.assertTrue(s.insert("crude_oil", 12_345.5) == 12_345.5, "insert");
            List<BlockPos> nodes = List.of(new BlockPos(2, 1, 12), new BlockPos(3, 1, 12), new BlockPos(4, 1, 12), new BlockPos(5, 1, 12));
            var registries = h.getLevel().registryAccess();
            List<CompoundTag> tags = new ArrayList<>();
            double saved = 0;
            for (BlockPos rel : nodes) {
                BlockEntity be = h.getLevel().getBlockEntity(h.absolutePos(rel));
                CompoundTag tag = be.saveWithoutMetadata(registries);
                tags.add(tag);
                saved += tag.getDouble("amount");
                h.assertTrue("crude_oil".equals(tag.getString("fluid")), "saved fluid " + tag.getString("fluid"));
            }
            h.assertTrue(Math.abs(saved - 12_345.5) < 1e-6, "saved total " + saved);
            FluidNetworkManager m = manager(h);
            for (BlockPos rel : nodes) m.network().removeNode(h.absolutePos(rel).asLong()); // "unloaded"
            h.assertTrue(!m.network().has(h.absolutePos(new BlockPos(2, 1, 12)).asLong()), "still registered");
            for (int i = 0; i < nodes.size(); i++) {
                BlockEntity be = h.getLevel().getBlockEntity(h.absolutePos(nodes.get(i)));
                be.loadWithComponents(tags.get(i), registries);
                h.assertTrue(((FluidNodeEntity) be).fluidNode().amount() == tags.get(i).getDouble("amount"), "loaded amount");
                m.queueNode(be.getBlockPos());
            }
            FluidNetwork.Segment back = segment(h, new BlockPos(3, 1, 12));
            h.assertTrue(back != null && back.length() == 4, "not re-linked");
            h.assertTrue("crude_oil".equals(back.fluid()) && Math.abs(back.amount() - 12_345.5) < 1e-6, "back " + back.fluid() + " " + back.amount());
            PipeBlockEntity pipe = (PipeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2, 1, 12)));
            CompoundTag settings = pipe.saveSettings();
            h.assertTrue(settings.getByte(PipeBlockEntity.KEY_CLOSED) == 0, "closed mask " + settings);
            h.succeed();
        });
    }

    /** Blueprint settings: the closed-sides mask copies to another pipe. */
    @GameTest(template = "empty16", timeoutTicks = 20)
    public static void closedSidesCopyAsSettings(GameTestHelper h) {
        pipe(h, 2, 1, 2);
        pipe(h, 8, 1, 2);
        PipeBlock.toggle(h.getLevel(), h.absolutePos(new BlockPos(2, 1, 2)), Direction.UP, null);
        PipeBlock.toggle(h.getLevel(), h.absolutePos(new BlockPos(2, 1, 2)), Direction.WEST, null);
        CompoundTag settings = ((PipeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2, 1, 2)))).saveSettings();
        ((PipeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(8, 1, 2)))).loadSettings(settings);
        h.assertTrue(PipeBlock.side(h.getBlockState(new BlockPos(8, 1, 2)), Direction.UP) == PipeSide.CAP, "up");
        h.assertTrue(PipeBlock.side(h.getBlockState(new BlockPos(8, 1, 2)), Direction.WEST) == PipeSide.CAP, "west");
        h.assertTrue(PipeBlock.side(h.getBlockState(new BlockPos(8, 1, 2)), Direction.EAST) == PipeSide.NONE, "east");
        h.succeed();
    }
}
