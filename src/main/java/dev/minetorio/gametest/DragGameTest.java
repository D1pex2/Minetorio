package dev.minetorio.gametest;

import dev.minetorio.content.belt.BeltBlock;
import dev.minetorio.content.drag.DragPlacement;
import dev.minetorio.content.drag.DragTerrain;
import dev.minetorio.content.power.ElectricPoleBlock;
import dev.minetorio.content.power.ElectricPoleBlockEntity;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.drag.DragDir;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Drag placement on the server ({@link DragPlacement}): a survival fake player clicks the first
 * cell like a normal placement, then the drag's steps arrive as the client would send them.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class DragGameTest {
    private static final DragDir E = DragDir.EAST, N = DragDir.NORTH;

    /** A survival fake player with an empty inventory holding {@code count} of {@code item}, standing at {@code rel}. */
    private static ServerPlayer player(GameTestHelper h, Item item, int count, BlockPos rel) {
        ServerPlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, count));
        moveTo(h, p, rel);
        return p;
    }

    private static void moveTo(GameTestHelper h, ServerPlayer p, BlockPos rel) {
        Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(rel));
        p.moveTo(at.x, at.y, at.z, p.getYRot(), 0);
    }

    /** Stone floor at y 0 from x0 to x1 along row z. */
    private static void floor(GameTestHelper h, int x0, int x1, int z) {
        for (int x = x0; x <= x1; x++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    /** The click that starts the drag: right-click the top of the block under {@code start}, looking {@code facing}. */
    private static void click(GameTestHelper h, ServerPlayer p, BlockPos start, Direction facing) {
        p.setYRot(facing.toYRot());
        BlockPos floor = h.absolutePos(start.below());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        DragPlacement.start(p, h.absolutePos(start), DragTerrain.fromMc(facing));
    }

    private static void steps(ServerPlayer p, int from, List<DragDir> steps, boolean finish) {
        byte[] bytes = new byte[steps.size()];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) steps.get(i).id();
        DragPlacement.steps(p, from, bytes, finish);
    }

    private static List<DragDir> repeat(DragDir d, int n) {
        return java.util.Collections.nCopies(n, d);
    }

    private static int held(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static void assertBelt(GameTestHelper h, BlockPos rel, Block block, Direction facing) {
        BlockState s = h.getBlockState(rel);
        h.assertTrue(s.is(block), "expected " + block + " at " + rel + ", got " + s);
        h.assertTrue(s.getValue(BeltBlock.FACING) == facing, "belt at " + rel + " faces " + s.getValue(BeltBlock.FACING) + ", expected " + facing);
    }

    private static void assertSlope(GameTestHelper h, BlockPos rel, BeltBlock.Slope slope) {
        BeltBlock.Slope got = h.getBlockState(rel).getValue(BeltBlock.SLOPE);
        h.assertTrue(got == slope, "belt at " + rel + " is " + got + ", expected " + slope);
    }

    /** Click at x 1, then 6 steps east and release: 7 belts facing east, one item each. */
    @GameTest(template = "empty16")
    public static void straightRunPlacesOneBeltPerItem(GameTestHelper h) {
        floor(h, 0, 10, 5);
        ServerPlayer p = player(h, ModItems.TRANSPORT_BELT.get(), 10, new BlockPos(4, 1, 7));
        click(h, p, new BlockPos(1, 1, 5), Direction.EAST);
        steps(p, 0, repeat(E, 6), true);
        for (int x = 1; x <= 7; x++) assertBelt(h, new BlockPos(x, 1, 5), ModBlocks.TRANSPORT_BELT.get(), Direction.EAST);
        h.assertBlockNotPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(8, 1, 5));
        h.assertTrue(held(p, ModItems.TRANSPORT_BELT.get()) == 3, "expected 3 belts left, got " + held(p, ModItems.TRANSPORT_BELT.get()));
        h.assertFalse(DragPlacement.isDragging(p), "drag still open after release");
        h.succeed();
    }

    /** Until release the head stays a ghost: its facing may still turn. */
    @GameTest(template = "empty16")
    public static void headWaitsForTheNextStep(GameTestHelper h) {
        floor(h, 0, 10, 5);
        ServerPlayer p = player(h, ModItems.TRANSPORT_BELT.get(), 10, new BlockPos(4, 1, 7));
        click(h, p, new BlockPos(1, 1, 5), Direction.EAST);
        steps(p, 0, repeat(E, 3), false);
        h.assertBlockPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(3, 1, 5));
        h.assertBlockNotPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(4, 1, 5));
        steps(p, 3, repeat(E, 1), true);
        assertBelt(h, new BlockPos(4, 1, 5), ModBlocks.TRANSPORT_BELT.get(), Direction.EAST);
        assertBelt(h, new BlockPos(5, 1, 5), ModBlocks.TRANSPORT_BELT.get(), Direction.EAST);
        h.succeed();
    }

    /** East 3, then north 3: the corner belt faces north and is a curve; a turn on the first step re-points the clicked belt. */
    @GameTest(template = "empty16")
    public static void cornerMakesACurve(GameTestHelper h) {
        for (int z = 2; z <= 9; z++) floor(h, 1, 6, z);
        ServerPlayer p = player(h, ModItems.TRANSPORT_BELT.get(), 20, new BlockPos(4, 1, 10));
        click(h, p, new BlockPos(2, 1, 8), Direction.EAST);
        steps(p, 0, List.of(E, E, E, N, N, N), true);
        for (int x = 2; x <= 4; x++) assertBelt(h, new BlockPos(x, 1, 8), ModBlocks.TRANSPORT_BELT.get(), Direction.EAST);
        BlockPos corner = new BlockPos(5, 1, 8);
        assertBelt(h, corner, ModBlocks.TRANSPORT_BELT.get(), Direction.NORTH);
        h.assertTrue(h.getBlockState(corner).getValue(BeltBlock.SHAPE) == BeltBlock.Shape.CURVE_LEFT,
                "corner is " + h.getBlockState(corner).getValue(BeltBlock.SHAPE));
        for (int z = 5; z <= 7; z++) assertBelt(h, new BlockPos(5, 1, z), ModBlocks.TRANSPORT_BELT.get(), Direction.NORTH);
        h.assertTrue(held(p, ModItems.TRANSPORT_BELT.get()) == 13, "expected 13 left, got " + held(p, ModItems.TRANSPORT_BELT.get()));

        // turning right after the click: the clicked belt turns north too (it becomes the corner)
        moveTo(h, p, new BlockPos(3, 1, 6));
        click(h, p, new BlockPos(2, 1, 4), Direction.EAST);
        steps(p, 0, List.of(N), true);
        assertBelt(h, new BlockPos(2, 1, 4), ModBlocks.TRANSPORT_BELT.get(), Direction.NORTH);
        assertBelt(h, new BlockPos(2, 1, 3), ModBlocks.TRANSPORT_BELT.get(), Direction.NORTH);
        h.succeed();
    }

    /**
     * A chest and a crossing belt in the line are skipped and kept, without using an item; a
     * yellow belt facing the line's way is fast-replaced by the red belts being dragged.
     */
    @GameTest(template = "empty16")
    public static void occupiedCellsAreSkipped(GameTestHelper h) {
        floor(h, 0, 10, 5);
        h.setBlock(new BlockPos(3, 1, 5), Blocks.CHEST);
        BeltGameTest.place(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(5, 1, 5), Direction.SOUTH);
        BeltGameTest.place(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(6, 1, 5), Direction.EAST);
        ServerPlayer p = player(h, ModItems.FAST_TRANSPORT_BELT.get(), 10, new BlockPos(4, 1, 7));
        click(h, p, new BlockPos(1, 1, 5), Direction.EAST);
        steps(p, 0, repeat(E, 6), true);
        h.assertBlockPresent(Blocks.CHEST, new BlockPos(3, 1, 5));
        assertBelt(h, new BlockPos(5, 1, 5), ModBlocks.TRANSPORT_BELT.get(), Direction.SOUTH);
        assertBelt(h, new BlockPos(6, 1, 5), ModBlocks.FAST_TRANSPORT_BELT.get(), Direction.EAST);
        for (int x : new int[] {1, 2, 4, 7}) assertBelt(h, new BlockPos(x, 1, 5), ModBlocks.FAST_TRANSPORT_BELT.get(), Direction.EAST);
        // 10 - 1 (click) - 3 placed - 1 fast-replaced = 5 red; the replaced yellow belt comes back
        h.assertTrue(held(p, ModItems.FAST_TRANSPORT_BELT.get()) == 5, "red left " + held(p, ModItems.FAST_TRANSPORT_BELT.get()));
        h.assertTrue(held(p, ModItems.TRANSPORT_BELT.get()) == 1, "yellow refunded " + held(p, ModItems.TRANSPORT_BELT.get()));
        h.succeed();
    }

    /** 3 belts in hand and 2 more in the inventory: after the click 4 are left, so 4 of 6 cells get one and the drag stops. */
    @GameTest(template = "empty16")
    public static void runningOutStopsTheDrag(GameTestHelper h) {
        floor(h, 0, 10, 5);
        ServerPlayer p = player(h, ModItems.TRANSPORT_BELT.get(), 3, new BlockPos(4, 1, 7));
        p.getInventory().setItem(9, new ItemStack(ModItems.TRANSPORT_BELT.get(), 2));
        click(h, p, new BlockPos(1, 1, 5), Direction.EAST);
        steps(p, 0, repeat(E, 7), false);
        for (int x = 1; x <= 5; x++) assertBelt(h, new BlockPos(x, 1, 5), ModBlocks.TRANSPORT_BELT.get(), Direction.EAST);
        h.assertBlockNotPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(6, 1, 5));
        h.assertTrue(held(p, ModItems.TRANSPORT_BELT.get()) == 0, "items left " + held(p, ModItems.TRANSPORT_BELT.get()));
        h.assertFalse(DragPlacement.isDragging(p), "drag goes on without items");
        steps(p, 7, repeat(E, 1), true); // the client's release after the server stopped: ignored
        h.assertBlockNotPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(6, 1, 5));
        h.succeed();
    }

    /**
     * Over a one-block-high plateau (x 5..7): the line climbs onto it and comes back down, and the
     * belts become ramps exactly as when placed by hand.
     */
    @GameTest(template = "empty16")
    public static void terrainStepMakesRamps(GameTestHelper h) {
        floor(h, 0, 12, 5);
        for (int x = 5; x <= 7; x++) h.setBlock(new BlockPos(x, 1, 5), Blocks.STONE);
        ServerPlayer p = player(h, ModItems.TRANSPORT_BELT.get(), 20, new BlockPos(6, 1, 7));
        click(h, p, new BlockPos(2, 1, 5), Direction.EAST);
        steps(p, 0, repeat(E, 8), true);
        for (int x = 2; x <= 4; x++) assertBelt(h, new BlockPos(x, 1, 5), ModBlocks.TRANSPORT_BELT.get(), Direction.EAST);
        for (int x = 5; x <= 7; x++) assertBelt(h, new BlockPos(x, 2, 5), ModBlocks.TRANSPORT_BELT.get(), Direction.EAST);
        for (int x = 8; x <= 10; x++) assertBelt(h, new BlockPos(x, 1, 5), ModBlocks.TRANSPORT_BELT.get(), Direction.EAST);
        assertSlope(h, new BlockPos(3, 1, 5), BeltBlock.Slope.FLAT);
        assertSlope(h, new BlockPos(4, 1, 5), BeltBlock.Slope.ASCENDING);
        assertSlope(h, new BlockPos(5, 2, 5), BeltBlock.Slope.FLAT);
        assertSlope(h, new BlockPos(7, 2, 5), BeltBlock.Slope.FLAT);
        assertSlope(h, new BlockPos(8, 1, 5), BeltBlock.Slope.DESCENDING);
        assertSlope(h, new BlockPos(9, 1, 5), BeltBlock.Slope.FLAT);
        h.assertTrue(held(p, ModItems.TRANSPORT_BELT.get()) == 11, "expected 11 left, got " + held(p, ModItems.TRANSPORT_BELT.get()));
        h.succeed();
    }

    /**
     * A pole drag over 15 cells (x 0..15) places poles at the wire reach: x 7 and 14 besides the
     * clicked one at x 0, and they wire up. The player walks along between the two packets.
     */
    @GameTest(template = "empty16", timeoutTicks = 60)
    public static void poleDragSpacesAtWireReach(GameTestHelper h) {
        floor(h, 0, 15, 2);
        ServerPlayer p = player(h, ModItems.SMALL_ELECTRIC_POLE.get(), 5, new BlockPos(4, 1, 5));
        click(h, p, new BlockPos(0, 1, 2), Direction.EAST);
        steps(p, 0, repeat(E, 8), false);
        moveTo(h, p, new BlockPos(11, 1, 5));
        steps(p, 8, repeat(E, 7), true);
        Block pole = ModBlocks.SMALL_ELECTRIC_POLE.get();
        for (int x = 1; x <= 15; x++) {
            boolean expected = x == 7 || x == 14;
            BlockState s = h.getBlockState(new BlockPos(x, 1, 2));
            h.assertTrue(s.is(pole) == expected, (expected ? "no pole at x " : "unexpected pole at x ") + x);
        }
        h.assertTrue(h.getBlockState(new BlockPos(7, 3, 2)).is(pole)
                && h.getBlockState(new BlockPos(7, 3, 2)).getValue(ElectricPoleBlock.PART) == 2, "pole at x 7 not full height");
        h.assertTrue(held(p, ModItems.SMALL_ELECTRIC_POLE.get()) == 2, "poles left " + held(p, ModItems.SMALL_ELECTRIC_POLE.get()));
        BlockPos a = h.absolutePos(new BlockPos(0, 1, 2)), b = h.absolutePos(new BlockPos(7, 1, 2)), c = h.absolutePos(new BlockPos(14, 1, 2));
        h.succeedWhen(() -> {
            ElectricPoleBlockEntity mid = (ElectricPoleBlockEntity) h.getLevel().getBlockEntity(b);
            h.assertTrue(mid.connections().equals(List.of(a, c)), "middle pole wires " + mid.connections());
        });
    }

    /** When the click placed nothing (it used the block it hit), the drag places the start cell itself. */
    @GameTest(template = "empty16")
    public static void startCellIsPlacedIfTheClickDidNot(GameTestHelper h) {
        floor(h, 0, 10, 5);
        ServerPlayer p = player(h, ModItems.TRANSPORT_BELT.get(), 10, new BlockPos(4, 1, 7));
        DragPlacement.start(p, h.absolutePos(new BlockPos(1, 1, 5)), DragDir.EAST);
        steps(p, 0, repeat(E, 2), true);
        for (int x = 1; x <= 3; x++) assertBelt(h, new BlockPos(x, 1, 5), ModBlocks.TRANSPORT_BELT.get(), Direction.EAST);
        h.assertTrue(held(p, ModItems.TRANSPORT_BELT.get()) == 7, "expected 7 left, got " + held(p, ModItems.TRANSPORT_BELT.get()));
        h.succeed();
    }

    /** A step backwards or a step index that does not follow on ends the drag; nothing more is placed. */
    @GameTest(template = "empty16")
    public static void invalidStepsEndTheDrag(GameTestHelper h) {
        floor(h, 0, 10, 5);
        ServerPlayer p = player(h, ModItems.TRANSPORT_BELT.get(), 10, new BlockPos(4, 1, 7));
        click(h, p, new BlockPos(1, 1, 5), Direction.EAST);
        steps(p, 0, List.of(E, E, DragDir.WEST), true);
        h.assertFalse(DragPlacement.isDragging(p), "reversal accepted");
        h.assertBlockNotPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(3, 1, 5));

        floor(h, 0, 10, 3);
        click(h, p, new BlockPos(1, 1, 3), Direction.EAST);
        steps(p, 5, repeat(E, 2), true);
        h.assertFalse(DragPlacement.isDragging(p), "gap in the steps accepted");
        h.assertBlockNotPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(2, 1, 3));
        h.succeed();
    }
}
