package dev.minetorio.gametest;

import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * R26 player-style placement: a survival fake player looking {@code facing} right-clicks the top of
 * the floor under the target cell. The machine's footprint must be centered on that cell (left-front
 * cell of the central pair for even sizes), on its layer; splitters keep the target as their left
 * column. Expected cells are written out by hand per case, not derived from the placement code.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class PlacementGameTest {
    private static final BlockPos TARGET = new BlockPos(7, 1, 7);

    private static ServerPlayer player(GameTestHelper h, Item item, int count, Direction facing) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        Vec3 corner = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(14, 1, 14)));
        player.moveTo(corner.x, corner.y, corner.z, facing.toYRot(), 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, count));
        return player;
    }

    /** Right-clicks the top face of the floor under {@code target}. */
    private static InteractionResult click(GameTestHelper h, ServerPlayer player, BlockPos target) {
        BlockPos floor = h.absolutePos(target.below());
        h.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /**
     * Asserts that the machine occupies exactly x0..x1, y0..y1, z0..z1 (relative, inclusive) within a
     * one-block margin, with its controller ({@code block}) at {@code controller}.
     */
    private static void assertBox(GameTestHelper h, Block block, BlockPos controller, int x0, int x1, int y0, int y1, int z0, int z1) {
        h.assertTrue(h.getBlockState(controller).is(block), "controller not at " + controller + ": " + h.getBlockState(controller));
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int y = y0; y <= y1 + 1; y++) {
                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    boolean inside = x >= x0 && x <= x1 && y <= y1 && z >= z0 && z <= z1;
                    boolean machine = h.getBlockState(p).is(block) || h.getBlockState(p).is(ModBlocks.MULTIBLOCK_PART.get());
                    h.assertTrue(inside == machine, (inside ? "missing machine at " : "machine outside footprint at ") + p);
                }
            }
        }
    }

    private static void assertConsumed(GameTestHelper h, ServerPlayer player, InteractionResult result, int left) {
        h.assertTrue(result.consumesAction(), "placement failed: " + result);
        int count = player.getItemInHand(InteractionHand.MAIN_HAND).getCount();
        h.assertTrue(count == left, "expected " + left + " items left, got " + count);
    }

    /** 3×3 assembler facing NORTH (right = +x, forward = −z): x 6..8, z 6..8, controller at the left-front corner. */
    @GameTest(template = "empty16")
    public static void assemblerIsCenteredOnTarget(GameTestHelper h) {
        ServerPlayer player = player(h, ModItems.ASSEMBLING_MACHINE_1.get(), 2, Direction.NORTH);
        assertConsumed(h, player, click(h, player, TARGET), 1);
        assertBox(h, ModBlocks.ASSEMBLING_MACHINE_1.get(), new BlockPos(6, 1, 8), 6, 8, 1, 2, 6, 8);
        h.succeed();
    }

    /** 2×2 furnace facing NORTH: the target is the left-front cell (min x, max z): x 7..8, z 6..7. */
    @GameTest(template = "empty16")
    public static void furnaceTargetIsLeftFrontCell(GameTestHelper h) {
        ServerPlayer player = player(h, ModItems.STONE_FURNACE.get(), 2, Direction.NORTH);
        assertConsumed(h, player, click(h, player, TARGET), 1);
        assertBox(h, ModBlocks.STONE_FURNACE.get(), TARGET, 7, 8, 1, 2, 6, 7);
        h.succeed();
    }

    /** 3×5 steam engine facing EAST: the 5 runs along the facing (x 5..9), the 3 across it (z 6..8). */
    @GameTest(template = "empty16")
    public static void steamEngineIsCenteredAlongBothAxes(GameTestHelper h) {
        ServerPlayer player = player(h, ModItems.STEAM_ENGINE.get(), 1, Direction.EAST);
        assertConsumed(h, player, click(h, player, TARGET), 0);
        assertBox(h, ModBlocks.STEAM_ENGINE.get(), new BlockPos(5, 1, 6), 5, 9, 1, 2, 6, 8);
        h.succeed();
    }

    /** Splitter facing NORTH: unchanged, the target is the left column (controller), the right column is +x. */
    @GameTest(template = "empty16")
    public static void splitterTargetStaysLeftColumn(GameTestHelper h) {
        ServerPlayer player = player(h, ModItems.SPLITTER.get(), 2, Direction.NORTH);
        assertConsumed(h, player, click(h, player, TARGET), 1);
        assertBox(h, ModBlocks.SPLITTER.get(), TARGET, 7, 8, 1, 1, 7, 7);
        h.succeed();
    }

    /**
     * An obstruction anywhere in the centered assembler footprint refuses placement without
     * consuming the item: at the computed origin (not the target), the far top corner and the
     * target's right neighbour.
     */
    @GameTest(template = "empty16")
    public static void obstructionInCenteredFootprintRefuses(GameTestHelper h) {
        for (BlockPos stone : List.of(new BlockPos(6, 1, 8), new BlockPos(8, 2, 6), new BlockPos(8, 1, 7))) {
            h.setBlock(stone, Blocks.STONE);
            ServerPlayer player = player(h, ModItems.ASSEMBLING_MACHINE_1.get(), 2, Direction.NORTH);
            InteractionResult result = click(h, player, TARGET);
            h.assertFalse(result.consumesAction(), "placed despite stone at " + stone);
            int count = player.getItemInHand(InteractionHand.MAIN_HAND).getCount();
            h.assertTrue(count == 2, "item consumed with stone at " + stone + ": " + count + " left");
            for (int x = 6; x <= 8; x++) {
                for (int y = 1; y <= 2; y++) {
                    for (int z = 6; z <= 8; z++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (!p.equals(stone)) h.assertTrue(h.getBlockState(p).isAir(), "block placed at " + p + " with stone at " + stone);
                    }
                }
            }
            h.setBlock(stone, Blocks.AIR);
        }
        h.succeed();
    }

    /** The rule is the controller's: placementTarget inverts placementOrigin for every multiblock in every facing. */
    @GameTest(template = "empty16")
    public static void placementTargetInvertsOrigin(GameTestHelper h) {
        for (Block b : List.of(ModBlocks.ASSEMBLING_MACHINE_1.get(), ModBlocks.STEAM_ENGINE.get(), ModBlocks.BOILER.get(),
                ModBlocks.SPLITTER.get(), ModBlocks.OFFSHORE_PUMP.get())) {
            MultiblockControllerBlock c = (MultiblockControllerBlock) b;
            for (Direction f : Direction.Plane.HORIZONTAL) {
                h.assertTrue(c.placementTarget(c.placementOrigin(TARGET, f), f).equals(TARGET), b + " " + f);
            }
        }
        h.succeed();
    }
}
