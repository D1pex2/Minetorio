package dev.minetorio.gametest;

import dev.minetorio.content.robot.Ghosts;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public final class GhostSupportGameTest {
    @GameTest(template = "empty16")
    public static void everyBottomCellNeedsRealSupportAndFluidDoesNotCount(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(5, 1, 5));
        var target = ModBlocks.ASSEMBLING_MACHINE_1.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        for (BlockPos cell : BlockPos.betweenClosed(pos.offset(0, -1, 0), pos.offset(2, -1, 2))) level.setBlock(cell, Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(Ghosts.supported(level, pos, target, false) && Ghosts.supported(level, pos, target, true), "real floor does not support the whole machine");
        BlockPos corner = pos.offset(2, -1, 2);
        level.removeBlock(corner, false);
        h.assertFalse(Ghosts.supported(level, pos, target, true), "preview ignored missing support away from the controller");
        level.setBlock(corner, Blocks.WATER.defaultBlockState(), 3);
        h.assertFalse(Ghosts.supported(level, pos, target, false) || Ghosts.supported(level, pos, target, true), "water counted as support");
        level.setBlock(corner, Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(Ghosts.supported(level, pos, target, true), "restored support stayed invalid");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void reservedGhostFootprintIsPlannedSupportButCannotBeBuiltOn(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos lower = h.absolutePos(new BlockPos(4, 1, 4));
        for (BlockPos cell : BlockPos.betweenClosed(lower.offset(0, -1, 0), lower.offset(2, -1, 2))) level.setBlock(cell, Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(Ghosts.place(level, lower, ModItems.ASSEMBLING_MACHINE_1.get(), Direction.EAST), "lower machine ghost could not be placed");
        var target = ModBlocks.ASSEMBLING_MACHINE_1.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        BlockPos upper = lower.above(2);
        h.assertTrue(Ghosts.supported(level, upper, target, true), "reserved upper cells of the lower ghost were treated as air");
        h.assertFalse(Ghosts.supported(level, upper, target, false), "unbuilt machine counted as real support");
        level.removeBlock(lower, false);
        h.assertFalse(Ghosts.supported(level, upper, target, true), "deleted ghost still supports the upper machine");
        h.succeed();
    }
}
