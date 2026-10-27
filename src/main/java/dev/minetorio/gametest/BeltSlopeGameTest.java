package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.belt;
import static dev.minetorio.gametest.BeltGameTest.count;
import static dev.minetorio.gametest.BeltGameTest.place;

import dev.minetorio.content.belt.BeltBlock;
import dev.minetorio.content.belt.LaneSide;
import dev.minetorio.content.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** R25: belts become ramps like vanilla rails when the next belt is one block up or down. */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class BeltSlopeGameTest {
    private static final String PLATE = "minetorio:iron_plate";

    static BeltBlock.Slope slope(GameTestHelper h, BlockPos rel) {
        return h.getBlockState(rel).getValue(BeltBlock.SLOPE);
    }

    static void assertSlope(GameTestHelper h, BlockPos rel, BeltBlock.Slope expected) {
        BeltBlock.Slope got = slope(h, rel);
        h.assertTrue(got == expected, "belt at " + rel + " is " + got + ", expected " + expected);
    }

    /** Belts along +x from x0, one block higher each step (dy = +1) or lower (dy = -1). */
    static void stair(GameTestHelper h, Item belt, int x0, int y0, int n, int dy, int z) {
        for (int i = 0; i < n; i++) place(h, belt, new BlockPos(x0 + i, y0 + dy * i, z), Direction.EAST);
    }

    @GameTest(template = "empty16")
    public static void ascendingChainCarriesItemsUp(GameTestHelper h) {
        stair(h, ModItems.TRANSPORT_BELT.get(), 1, 1, 3, 1, 3); // (1,1) (2,2) (3,3)
        assertSlope(h, new BlockPos(1, 1, 3), BeltBlock.Slope.ASCENDING);
        assertSlope(h, new BlockPos(2, 2, 3), BeltBlock.Slope.ASCENDING);
        assertSlope(h, new BlockPos(3, 3, 3), BeltBlock.Slope.FLAT);
        BlockPos chest = new BlockPos(4, 3, 3);
        h.setBlock(chest, Blocks.CHEST);
        belt(h, new BlockPos(1, 1, 3)).lane(LaneSide.LEFT).insertAt(PLATE, 0);
        belt(h, new BlockPos(1, 1, 3)).lane(LaneSide.RIGHT).insertAt(PLATE, 0);
        h.succeedWhen(() -> h.assertTrue(count(h, chest, ModItems.IRON_PLATE.get()) == 2, "not up the ramp yet"));
    }

    /** Placed bottom-up, so the ramps form through the diagonal refresh, not at placement. */
    @GameTest(template = "empty16")
    public static void descendingChainCarriesItemsDown(GameTestHelper h) {
        Item yellow = ModItems.TRANSPORT_BELT.get();
        place(h, yellow, new BlockPos(3, 1, 3), Direction.EAST);
        place(h, yellow, new BlockPos(2, 2, 3), Direction.EAST);
        place(h, yellow, new BlockPos(1, 3, 3), Direction.EAST);
        assertSlope(h, new BlockPos(1, 3, 3), BeltBlock.Slope.FLAT);
        assertSlope(h, new BlockPos(2, 2, 3), BeltBlock.Slope.DESCENDING);
        assertSlope(h, new BlockPos(3, 1, 3), BeltBlock.Slope.DESCENDING);
        BlockPos chest = new BlockPos(4, 1, 3);
        h.setBlock(chest, Blocks.CHEST);
        belt(h, new BlockPos(1, 3, 3)).lane(LaneSide.LEFT).insertAt(PLATE, 0);
        belt(h, new BlockPos(1, 3, 3)).lane(LaneSide.RIGHT).insertAt(PLATE, 0);
        h.succeedWhen(() -> h.assertTrue(count(h, chest, ModItems.IRON_PLATE.get()) == 2, "not down the ramp yet"));
    }

    /**
     * A compressed lane over 4 ramps and 6 flat belts delivers 7.5 items/s like 10 flat belts
     * (BeltGameTest.chainedThroughputIsFull): the ramps tick downstream-first and pass the overflow.
     */
    @GameTest(template = "empty16", timeoutTicks = 140)
    public static void rampThroughputIsFull(GameTestHelper h) {
        Item yellow = ModItems.TRANSPORT_BELT.get();
        stair(h, yellow, 1, 1, 5, 1, 2);                 // (1,1)..(4,4) ramps, (5,5) flat
        for (int x = 6; x <= 10; x++) place(h, yellow, new BlockPos(x, 5, 2), Direction.EAST);
        for (int x = 1; x <= 4; x++) assertSlope(h, new BlockPos(x, x, 2), BeltBlock.Slope.ASCENDING);
        BlockPos chest = new BlockPos(11, 5, 2);
        h.setBlock(chest, Blocks.CHEST);
        for (int x = 1; x <= 10; x++) {
            BlockPos b = new BlockPos(x, Math.min(x, 5), 2);
            for (double p = 0; p < 1; p += 0.25) h.assertTrue(belt(h, b).lane(LaneSide.RIGHT).insertAt(PLATE, p), "fill");
        }
        h.runAtTickTime(101, () -> {
            int n = count(h, chest, ModItems.IRON_PLATE.get());
            h.assertTrue(n >= 36 && n <= 38, "delivered " + n + " by tick 101, expected 36..38");
            h.succeed();
        });
    }

    @GameTest(template = "empty16")
    public static void rampFlattensWhenUpperBeltRemoved(GameTestHelper h) {
        stair(h, ModItems.TRANSPORT_BELT.get(), 2, 1, 2, 1, 3);
        assertSlope(h, new BlockPos(2, 1, 3), BeltBlock.Slope.ASCENDING);
        h.getLevel().destroyBlock(h.absolutePos(new BlockPos(3, 2, 3)), false);
        assertSlope(h, new BlockPos(2, 1, 3), BeltBlock.Slope.FLAT);
        // and the descending case
        place(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(6, 2, 3), Direction.EAST);
        place(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(7, 1, 3), Direction.EAST);
        assertSlope(h, new BlockPos(7, 1, 3), BeltBlock.Slope.DESCENDING);
        h.getLevel().destroyBlock(h.absolutePos(new BlockPos(6, 2, 3)), false);
        assertSlope(h, new BlockPos(7, 1, 3), BeltBlock.Slope.FLAT);
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void curvesNeverBecomeRamps(GameTestHelper h) {
        Item yellow = ModItems.TRANSPORT_BELT.get();
        // A curve (fed from the west, facing north) with a belt one up in front stays a flat curve.
        place(h, yellow, new BlockPos(4, 1, 5), Direction.EAST);
        place(h, yellow, new BlockPos(5, 1, 5), Direction.NORTH);
        place(h, yellow, new BlockPos(5, 2, 4), Direction.NORTH);
        BlockState curve = h.getBlockState(new BlockPos(5, 1, 5));
        h.assertTrue(curve.getValue(BeltBlock.SHAPE) == BeltBlock.Shape.CURVE_LEFT, "not a curve: " + curve);
        assertSlope(h, new BlockPos(5, 1, 5), BeltBlock.Slope.FLAT);
        // The same with the belt above placed first.
        place(h, yellow, new BlockPos(9, 2, 4), Direction.NORTH);
        place(h, yellow, new BlockPos(9, 1, 5), Direction.NORTH);
        assertSlope(h, new BlockPos(9, 1, 5), BeltBlock.Slope.ASCENDING);
        place(h, yellow, new BlockPos(10, 1, 5), Direction.WEST); // feeds from the right, nothing behind
        h.assertTrue(h.getBlockState(new BlockPos(9, 1, 5)).getValue(BeltBlock.SHAPE) == BeltBlock.Shape.CURVE_RIGHT, "not a curve");
        assertSlope(h, new BlockPos(9, 1, 5), BeltBlock.Slope.FLAT);
        // No belt anywhere ends up as a curved ramp.
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 8; y++) {
                for (int z = 0; z < 16; z++) {
                    BlockState s = h.getBlockState(new BlockPos(x, y, z));
                    if (s.getBlock() instanceof BeltBlock && s.getValue(BeltBlock.SHAPE) != BeltBlock.Shape.STRAIGHT) {
                        h.assertTrue(s.getValue(BeltBlock.SLOPE) == BeltBlock.Slope.FLAT, "curved ramp at " + x + "," + y + "," + z);
                    }
                }
            }
        }
        h.succeed();
    }

    /** Two belt lines stacked directly on top of each other stay flat (each belt feeds the one in front of it). */
    @GameTest(template = "empty16")
    public static void stackedLinesStayFlat(GameTestHelper h) {
        Item yellow = ModItems.TRANSPORT_BELT.get();
        for (int x = 1; x <= 5; x++) place(h, yellow, new BlockPos(x, 1, 3), Direction.EAST);
        for (int x = 1; x <= 5; x++) place(h, yellow, new BlockPos(x, 2, 3), Direction.EAST);
        for (int x = 1; x <= 5; x++) {
            assertSlope(h, new BlockPos(x, 1, 3), BeltBlock.Slope.FLAT);
            assertSlope(h, new BlockPos(x, 2, 3), BeltBlock.Slope.FLAT);
        }
        h.succeed();
    }

    /** An entity (armor stand: no AI) rides up a ramp onto the upper belt line. */
    @GameTest(template = "empty16", timeoutTicks = 120)
    public static void entityRidesUpRamp(GameTestHelper h) {
        Item yellow = ModItems.TRANSPORT_BELT.get();
        stair(h, yellow, 1, 1, 3, 1, 3);                 // (1,1) (2,2) ramps, (3,3) flat
        for (int x = 4; x <= 9; x++) place(h, yellow, new BlockPos(x, 3, 3), Direction.EAST);
        ArmorStand stand = h.spawn(EntityType.ARMOR_STAND, new Vec3(1.25, 1.8, 3.5));
        h.succeedWhen(() -> {
            Vec3 at = h.relativeVec(stand.position());
            h.assertTrue(at.x > 5 && at.y >= 3, "armor stand at " + at + ", motion " + stand.getDeltaMovement());
        });
    }
}
