package dev.minetorio.gametest;

import dev.minetorio.content.belt.BeltBlock;
import dev.minetorio.content.drag.DragPlacement;
import dev.minetorio.content.drag.DragTerrain;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.robot.ConstructionManager;
import dev.minetorio.content.robot.GhostBlockEntity;
import dev.minetorio.content.robot.GhostReservations;
import dev.minetorio.content.robot.Ghosts;
import dev.minetorio.content.robot.HandGhosts;
import dev.minetorio.core.drag.DragDir;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ghosts placed by hand ({@link HandGhosts}, the ghost key + right-click) and ghost drag lines
 * ({@link DragPlacement} in ghost mode), driven through the server handlers as the client's
 * packets would: a survival fake player holding a single item, which is never used.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class HandGhostGameTest {
    /** A survival fake player holding one {@code item}, standing at {@code rel} looking {@code look}. */
    static ServerPlayer player(GameTestHelper h, Item item, BlockPos rel, Direction look) {
        ServerPlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 1));
        Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(rel));
        p.moveTo(at.x, at.y, at.z, look.toYRot(), 0);
        HandGhosts.resetBudget(p);
        return p;
    }

    /** The ghost click on face {@code face} of the block at {@code rel}. */
    static boolean click(GameTestHelper h, ServerPlayer p, BlockPos rel, Direction face) {
        BlockPos abs = h.absolutePos(rel);
        Vec3 at = Vec3.atCenterOf(abs).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        return HandGhosts.place(p, new BlockHitResult(at, face, abs, false));
    }

    static void floor(GameTestHelper h, int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    static GhostBlockEntity ghostAt(GameTestHelper h, BlockPos rel) {
        BlockState s = h.getBlockState(rel);
        h.assertTrue(s.is(ModBlocks.GHOST.get()), "no ghost at " + rel + ": " + s);
        return (GhostBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
    }

    /**
     * Removes the test's ghosts and succeeds: a ghost left standing would stay a build job for the
     * robot tests' roboports, whose areas reach into this test's area.
     */
    static void done(GameTestHelper h) {
        for (BlockPos rel : BlockPos.betweenClosed(0, 0, 0, 15, 8, 15)) {
            if (h.getBlockState(rel).is(ModBlocks.GHOST.get())) h.setBlock(rel, Blocks.AIR);
        }
        h.succeed();
    }

    private static void assertKept(GameTestHelper h, ServerPlayer p, Item item) {
        ItemStack hand = p.getMainHandItem();
        h.assertTrue(hand.is(item) && hand.getCount() == 1, "item used: " + hand);
    }

    @GameTest(template = "empty16")
    public static void beltGhostFacesTheLook(GameTestHelper h) {
        floor(h, 0, 10, 0, 10);
        ServerPlayer p = player(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(2, 1, 5), Direction.EAST);
        h.assertTrue(click(h, p, new BlockPos(5, 0, 5), Direction.UP), "ghost refused");
        GhostBlockEntity g = ghostAt(h, new BlockPos(5, 1, 5));
        h.assertTrue(g.target().is(ModBlocks.TRANSPORT_BELT.get()), "target " + g.target());
        h.assertTrue(g.target().getValue(BeltBlock.FACING) == Direction.EAST && g.facing() == Direction.EAST, "facing " + g.target());
        h.assertTrue(ConstructionManager.get(h.getLevel()).queue().get(ConstructionManager.cell(h.absolutePos(new BlockPos(5, 1, 5)))).isPresent(),
                "no build job");
        assertKept(h, p, ModItems.TRANSPORT_BELT.get());
        // a second ghost click on the ghost re-places it with the new look
        p.setYRot(Direction.NORTH.toYRot());
        h.assertTrue(click(h, p, new BlockPos(5, 1, 5), Direction.UP), "re-placing refused");
        h.assertTrue(ghostAt(h, new BlockPos(5, 1, 5)).target().getValue(BeltBlock.FACING) == Direction.NORTH, "ghost not turned");
        done(h);
    }

    /**
     * A one-block step: the lower belt ghost becomes an ascending ramp toward the upper belt ghost,
     * as the real belts would; placing the upper one second re-plans the lower one.
     */
    @GameTest(template = "empty16")
    public static void beltGhostsFormARamp(GameTestHelper h) {
        floor(h, 0, 10, 0, 10);
        for (int x = 6; x <= 9; x++) h.setBlock(new BlockPos(x, 1, 5), Blocks.STONE); // the step
        ServerPlayer p = player(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(3, 1, 5), Direction.EAST);
        h.assertTrue(click(h, p, new BlockPos(5, 0, 5), Direction.UP), "lower ghost refused");
        h.assertTrue(ghostAt(h, new BlockPos(5, 1, 5)).target().getValue(BeltBlock.SLOPE) == BeltBlock.Slope.FLAT, "flat before");
        h.assertTrue(click(h, p, new BlockPos(6, 1, 5), Direction.UP), "upper ghost refused");
        BlockState lower = ghostAt(h, new BlockPos(5, 1, 5)).target();
        h.assertTrue(lower.getValue(BeltBlock.SLOPE) == BeltBlock.Slope.ASCENDING, "lower ghost not a ramp: " + lower);
        // re-placed with the upper one already planned
        h.assertTrue(click(h, p, new BlockPos(5, 0, 5), Direction.UP), "lower ghost again refused");
        h.assertTrue(ghostAt(h, new BlockPos(5, 1, 5)).target().getValue(BeltBlock.SLOPE) == BeltBlock.Slope.ASCENDING, "not planned as a ramp");
        // built for real, it is a ramp too
        BlockPos abs = h.absolutePos(new BlockPos(6, 2, 5));
        h.assertTrue(Ghosts.build(h.getLevel(), abs, null, new ItemStack(ModItems.TRANSPORT_BELT.get())), "upper not built");
        h.assertTrue(Ghosts.build(h.getLevel(), h.absolutePos(new BlockPos(5, 1, 5)), null, new ItemStack(ModItems.TRANSPORT_BELT.get())),
                "lower not built");
        h.assertTrue(h.getBlockState(new BlockPos(5, 1, 5)).getValue(BeltBlock.SLOPE) == BeltBlock.Slope.ASCENDING, "built belt not a ramp");
        done(h);
    }

    /**
     * An assembler (3×3×2) ghost is centred on the aimed cell like the real machine: the ghost at
     * the controller, the footprint reserved against other ghosts and real blocks; building it
     * gives the whole machine.
     */
    @GameTest(template = "empty16")
    public static void assemblerGhostIsCentredAndReservesItsFootprint(GameTestHelper h) {
        floor(h, 0, 12, 0, 12);
        ServerPlayer p = player(h, ModItems.ASSEMBLING_MACHINE_1.get(), new BlockPos(7, 1, 3), Direction.SOUTH);
        h.assertTrue(click(h, p, new BlockPos(7, 0, 7), Direction.UP), "ghost refused");
        // facing south, width runs west: the controller (left-front corner) is one east and one north of the aimed cell
        BlockPos origin = new BlockPos(8, 1, 6);
        GhostBlockEntity g = ghostAt(h, origin);
        h.assertTrue(g.target().is(ModBlocks.ASSEMBLING_MACHINE_1.get()), "target " + g.target());
        h.assertTrue(g.target().getValue(MultiblockControllerBlock.FACING) == Direction.SOUTH, "facing");
        h.assertTrue(h.getBlockState(new BlockPos(7, 1, 7)).isAir(), "only the controller cell holds the ghost");
        h.assertTrue(h.absolutePos(origin).equals(GhostReservations.owner(h.getLevel(), h.absolutePos(new BlockPos(6, 2, 8)))),
                "far corner not reserved");
        assertKept(h, p, ModItems.ASSEMBLING_MACHINE_1.get());

        // another ghost into the footprint is refused
        ServerPlayer q = player(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(7, 1, 3), Direction.SOUTH);
        h.assertFalse(click(h, q, new BlockPos(6, 0, 8), Direction.UP), "belt ghost inside the footprint");
        // and a real block too (the item is kept)
        ItemStack belts = new ItemStack(ModItems.TRANSPORT_BELT.get(), 2);
        q.setItemInHand(InteractionHand.MAIN_HAND, belts);
        BlockPos floorAbs = h.absolutePos(new BlockPos(6, 0, 8));
        q.gameMode.useItemOn(q, h.getLevel(), belts, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floorAbs).add(0, 0.5, 0), Direction.UP, floorAbs, false));
        h.assertTrue(h.getBlockState(new BlockPos(6, 1, 8)).isAir(), "real belt placed into the reserved footprint");
        h.assertTrue(q.getMainHandItem().getCount() == 2, "belt used on a refused placement");

        // built by hand with the matching item: the whole machine
        ItemStack machine = new ItemStack(ModItems.ASSEMBLING_MACHINE_1.get());
        q.setItemInHand(InteractionHand.MAIN_HAND, machine);
        BlockPos ghostAbs = h.absolutePos(origin);
        q.gameMode.useItemOn(q, h.getLevel(), machine, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ghostAbs), Direction.UP, ghostAbs, false));
        h.assertTrue(h.getBlockState(origin).is(ModBlocks.ASSEMBLING_MACHINE_1.get()), "machine not built: " + h.getBlockState(origin));
        h.assertTrue(h.getBlockState(new BlockPos(6, 2, 8)).getBlock() instanceof MultiblockPartBlock, "footprint not filled");
        h.assertTrue(machine.isEmpty(), "machine item not used");
        h.assertTrue(GhostReservations.owner(h.getLevel(), h.absolutePos(new BlockPos(6, 2, 8))) == null, "reservation left behind");
        done(h);
    }

    /** A real machine placed right onto its own ghost (same footprint) completes it instead of being refused. */
    @GameTest(template = "empty16")
    public static void realMachineOntoItsGhostIsAllowed(GameTestHelper h) {
        floor(h, 0, 12, 0, 12);
        ServerPlayer p = player(h, ModItems.ASSEMBLING_MACHINE_1.get(), new BlockPos(7, 1, 3), Direction.SOUTH);
        h.assertTrue(click(h, p, new BlockPos(7, 0, 7), Direction.UP), "ghost refused");
        ItemStack machine = new ItemStack(ModItems.ASSEMBLING_MACHINE_1.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, machine);
        BlockPos floorAbs = h.absolutePos(new BlockPos(7, 0, 7));
        p.gameMode.useItemOn(p, h.getLevel(), machine, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floorAbs).add(0, 0.5, 0), Direction.UP, floorAbs, false));
        h.assertTrue(h.getBlockState(new BlockPos(8, 1, 6)).is(ModBlocks.ASSEMBLING_MACHINE_1.get()), "machine not placed onto its ghost");
        h.assertTrue(machine.isEmpty(), "machine item not used");
        done(h);
    }

    /** Aiming at the shore, looking away from the water: the pump ghost turns to the water like the real pump, and builds there. */
    @GameTest(template = "empty16")
    public static void pumpGhostTurnsToTheShore(GameTestHelper h) {
        for (int x = 0; x < 12; x++) for (int z = 6; z < 11; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        for (int z = 7; z <= 9; z++) h.setBlock(new BlockPos(5, 0, z), Blocks.WATER);
        ServerPlayer p = player(h, ModItems.OFFSHORE_PUMP.get(), new BlockPos(2, 1, 8), Direction.NORTH);
        h.assertTrue(click(h, p, new BlockPos(4, 0, 8), Direction.UP), "pump ghost refused");
        GhostBlockEntity g = ghostAt(h, new BlockPos(3, 1, 8));
        h.assertTrue(g.target().getValue(MultiblockControllerBlock.FACING) == Direction.EAST && g.facing() == Direction.EAST,
                "pump ghost not facing the water: " + g.target() + " / " + g.facing());
        h.assertTrue(Ghosts.build(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 8)), null, new ItemStack(ModItems.OFFSHORE_PUMP.get())),
                "pump not built");
        h.assertTrue(h.getBlockState(new BlockPos(3, 1, 8)).is(ModBlocks.OFFSHORE_PUMP.get()), "no pump controller");
        h.assertTrue(h.getBlockState(new BlockPos(3, 1, 8)).getValue(MultiblockControllerBlock.FACING) == Direction.EAST, "built pump facing");
        h.assertTrue(h.getBlockState(new BlockPos(4, 1, 8)).getBlock() instanceof MultiblockPartBlock, "front cell not a part");
        h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(5, 0, 8))).isSource(), "water replaced");
        done(h);
    }

    @GameTest(template = "empty16")
    public static void invalidGhostsAreRefused(GameTestHelper h) {
        floor(h, 0, 12, 0, 12);
        // out of ghost reach (the normal reach does not apply to ghosts: see ghostReach*)
        ServerPlayer p = player(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(0, 1, -90), Direction.EAST);
        h.assertFalse(click(h, p, new BlockPos(12, 0, 12), Direction.UP), "ghost out of reach");
        // a pump with no water near
        p = player(h, ModItems.OFFSHORE_PUMP.get(), new BlockPos(3, 1, 3), Direction.EAST);
        h.assertFalse(click(h, p, new BlockPos(5, 0, 3), Direction.UP), "pump ghost without water");
        // an assembler whose footprint is blocked by a stone
        h.setBlock(new BlockPos(5, 2, 6), Blocks.STONE);
        p = player(h, ModItems.ASSEMBLING_MACHINE_1.get(), new BlockPos(5, 1, 2), Direction.SOUTH);
        h.assertFalse(click(h, p, new BlockPos(5, 0, 6), Direction.UP), "assembler ghost into a blocked footprint");
        // a belt into water
        h.setBlock(new BlockPos(8, 1, 3), Blocks.WATER);
        p = player(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(6, 1, 3), Direction.EAST);
        h.assertFalse(click(h, p, new BlockPos(8, 0, 3), Direction.UP), "belt ghost into water");
        // not a Minetorio block
        p = player(h, Items.STONE, new BlockPos(6, 1, 3), Direction.EAST);
        h.assertFalse(click(h, p, new BlockPos(7, 0, 5), Direction.UP), "vanilla block ghost");
        // adventure mode may not build
        p = player(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(6, 1, 3), Direction.EAST);
        p.setGameMode(GameType.ADVENTURE);
        h.assertFalse(click(h, p, new BlockPos(7, 0, 5), Direction.UP), "adventure ghost");
        p.setGameMode(GameType.SURVIVAL);
        // a hit point far from the clicked block (a forged packet)
        BlockPos abs = h.absolutePos(new BlockPos(7, 0, 5));
        h.assertFalse(HandGhosts.place(p, new BlockHitResult(Vec3.atCenterOf(abs).add(0, 3, 0), Direction.UP, abs, false)), "forged hit");
        // a pole whose upper blocks are taken
        h.setBlock(new BlockPos(9, 3, 5), Blocks.STONE);
        p = player(h, ModItems.SMALL_ELECTRIC_POLE.get(), new BlockPos(7, 1, 5), Direction.EAST);
        h.assertFalse(click(h, p, new BlockPos(9, 0, 5), Direction.UP), "pole ghost under a block");
        // the click budget: a flood is cut off
        p = player(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(6, 1, 9), Direction.EAST);
        int placed = 0;
        for (int i = 0; i < 40; i++) if (click(h, p, new BlockPos(7, 0, 9), Direction.UP)) placed++;
        h.assertTrue(placed > 0 && placed < 40, "budget let " + placed + " of 40 through");
        h.assertTrue(h.getBlockState(new BlockPos(8, 1, 3)).is(Blocks.WATER), "water replaced");
        done(h);
    }

    private static void steps(ServerPlayer p, int from, List<DragDir> steps, boolean finish) {
        byte[] bytes = new byte[steps.size()];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) steps.get(i).id();
        DragPlacement.steps(p, from, bytes, finish);
    }

    /** The ghost click at the top of the block under {@code start}, then a ghost drag from that cell running {@code dir}. */
    static void ghostDrag(GameTestHelper h, ServerPlayer p, BlockPos start, Direction dir, List<DragDir> steps) {
        h.assertTrue(click(h, p, start.below(), Direction.UP), "first ghost refused");
        DragPlacement.start(p, h.absolutePos(start), DragTerrain.fromMc(dir), true);
        steps(p, 0, steps, true);
    }

    /** A ghost drag: 5 cells east, then a turn north for 2: ghost belts with a curve at the corner, nothing used. */
    @GameTest(template = "empty16")
    public static void ghostDragLaysALineWithACurve(GameTestHelper h) {
        floor(h, 0, 10, 0, 10);
        ServerPlayer p = player(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(3, 1, 7), Direction.EAST);
        List<DragDir> path = new java.util.ArrayList<>(Collections.nCopies(4, DragDir.EAST));
        path.addAll(Collections.nCopies(2, DragDir.NORTH));
        ghostDrag(h, p, new BlockPos(1, 1, 5), Direction.EAST, path);
        for (int x = 1; x <= 4; x++) {
            GhostBlockEntity g = ghostAt(h, new BlockPos(x, 1, 5));
            h.assertTrue(g.target().getValue(BeltBlock.FACING) == Direction.EAST, "ghost at x " + x + " faces " + g.target());
        }
        BlockState corner = ghostAt(h, new BlockPos(5, 1, 5)).target();
        h.assertTrue(corner.getValue(BeltBlock.FACING) == Direction.NORTH, "corner faces " + corner);
        h.assertTrue(corner.getValue(BeltBlock.SHAPE) != BeltBlock.Shape.STRAIGHT, "corner not a curve: " + corner);
        for (int z = 3; z <= 4; z++) {
            h.assertTrue(ghostAt(h, new BlockPos(5, 1, z)).target().getValue(BeltBlock.FACING) == Direction.NORTH, "north leg at z " + z);
        }
        h.assertTrue(h.getBlockState(new BlockPos(5, 1, 2)).isAir(), "line too long");
        h.assertBlockNotPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(3, 1, 5));
        assertKept(h, p, ModItems.TRANSPORT_BELT.get());
        h.assertFalse(DragPlacement.isDragging(p), "drag still open");
        done(h);
    }

    /** A ghost pole drag spaces the pole ghosts at wire reach (x 7 and 14 after the clicked x 0). */
    @GameTest(template = "empty16")
    public static void ghostPoleDragSpacesAtWireReach(GameTestHelper h) {
        floor(h, 0, 15, 2, 2);
        ServerPlayer p = player(h, ModItems.SMALL_ELECTRIC_POLE.get(), new BlockPos(4, 1, 5), Direction.EAST);
        h.assertTrue(click(h, p, new BlockPos(0, 0, 2), Direction.UP), "first pole ghost refused");
        DragPlacement.start(p, h.absolutePos(new BlockPos(0, 1, 2)), DragDir.EAST, true);
        steps(p, 0, Collections.nCopies(8, DragDir.EAST), false);
        Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11, 1, 5)));
        p.moveTo(at.x, at.y, at.z, p.getYRot(), 0);
        steps(p, 8, Collections.nCopies(7, DragDir.EAST), true);
        for (int x = 0; x <= 15; x++) {
            boolean expected = x == 0 || x == 7 || x == 14;
            BlockState s = h.getBlockState(new BlockPos(x, 1, 2));
            h.assertTrue(s.is(ModBlocks.GHOST.get()) == expected, (expected ? "no pole ghost at x " : "unexpected ghost at x ") + x);
        }
        h.assertTrue(ghostAt(h, new BlockPos(7, 1, 2)).target().is(ModBlocks.SMALL_ELECTRIC_POLE.get()), "not a pole ghost");
        h.assertTrue(h.absolutePos(new BlockPos(7, 1, 2)).equals(GhostReservations.owner(h.getLevel(), h.absolutePos(new BlockPos(7, 3, 2)))),
                "pole top not reserved");
        h.assertTrue(h.getBlockState(new BlockPos(7, 2, 2)).isAir() && !h.getBlockState(new BlockPos(7, 1, 2)).is(ModBlocks.SMALL_ELECTRIC_POLE.get()),
                "real pole placed");
        assertKept(h, p, ModItems.SMALL_ELECTRIC_POLE.get());
        done(h);
    }
}
