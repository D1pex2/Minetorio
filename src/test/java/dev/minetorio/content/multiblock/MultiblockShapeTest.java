package dev.minetorio.content.multiblock;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** R26: the footprint is centered on the targeted cell (left-front cell of the central pair for even sizes). */
class MultiblockShapeTest {
    private static final BlockPos TARGET = new BlockPos(10, 64, 10);

    /** Footprint placed with {@code target} as its anchor. */
    private static List<BlockPos> centered(MultiblockShape shape, Direction facing) {
        return shape.footprint(shape.originForAnchor(TARGET, facing), facing);
    }

    private static int[] bounds(List<BlockPos> cells) {
        int[] b = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (BlockPos p : cells) {
            b[0] = Math.min(b[0], p.getX()); b[1] = Math.max(b[1], p.getX());
            b[2] = Math.min(b[2], p.getY()); b[3] = Math.max(b[3], p.getY());
            b[4] = Math.min(b[4], p.getZ()); b[5] = Math.max(b[5], p.getZ());
        }
        return b;
    }

    @Test
    void oddShapesAreCenteredOnTheTargetInEveryFacing() {
        for (MultiblockShape shape : List.of(MultiblockShape.ASSEMBLING_MACHINE, MultiblockShape.ELECTRIC_MINING_DRILL, MultiblockShape.STEAM_ENGINE)) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                int[] b = bounds(centered(shape, facing));
                assertEquals(TARGET.getX() * 2, b[0] + b[1], shape + " " + facing + " x not centered");
                assertEquals(TARGET.getZ() * 2, b[4] + b[5], shape + " " + facing + " z not centered");
                assertEquals(TARGET.getY(), b[2], shape + " " + facing + " bottom layer is not the target's");
            }
        }
    }

    @Test
    void steamEngineLongSideRunsAlongFacing() {
        // Facing EAST: depth 5 along x, width 3 along z.
        int[] b = bounds(centered(MultiblockShape.STEAM_ENGINE, Direction.EAST));
        assertArrayEquals(new int[] {8, 12, 64, 65, 9, 11}, b);
        assertEquals(new BlockPos(8, 64, 9), MultiblockShape.STEAM_ENGINE.originForAnchor(TARGET, Direction.EAST));
    }

    @Test
    void evenSizesPutTheTargetOnTheLeftFrontCellOfTheCentralPair() {
        // 2x2 furnace facing NORTH: left = west (min x), front = nearest the placer = south (max z).
        int[] f = bounds(centered(MultiblockShape.STONE_FURNACE, Direction.NORTH));
        assertArrayEquals(new int[] {10, 11, 64, 65, 9, 10}, f);
        // 3x2 boiler facing SOUTH: right = west; width centered on x, depth 2 starts at the target.
        int[] b = bounds(centered(MultiblockShape.BOILER, Direction.SOUTH));
        assertArrayEquals(new int[] {9, 11, 64, 65, 10, 11}, b);
        // 1x2 offshore pump: the target stays the near cell.
        assertEquals(TARGET, MultiblockShape.OFFSHORE_PUMP.originForAnchor(TARGET, Direction.WEST));
    }

    @Test
    void anchorIsTheInverseOfOrigin() {
        for (MultiblockShape shape : List.of(MultiblockShape.ASSEMBLING_MACHINE, MultiblockShape.STEAM_ENGINE, MultiblockShape.BOILER, MultiblockShape.STONE_FURNACE)) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                assertEquals(TARGET, shape.anchorOf(shape.originForAnchor(TARGET, facing), facing), shape + " " + facing);
                assertTrue(centered(shape, facing).contains(TARGET), shape + " " + facing + " target outside footprint");
            }
        }
    }
}
