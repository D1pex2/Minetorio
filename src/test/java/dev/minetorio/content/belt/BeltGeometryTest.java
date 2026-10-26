package dev.minetorio.content.belt;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.belt.BeltShape;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class BeltGeometryTest {
    private static void assertPoint(double x, double z, double[] p) {
        assertEquals(x, p[0], 1e-9, "x");
        assertEquals(z, p[1], 1e-9, "z");
    }

    @Test void farLaneIsAwayFromTheInserter() {
        // Belt going north: west is its left side.
        assertEquals(LaneSide.RIGHT, BeltGeometry.farLane(Direction.NORTH, Direction.WEST));
        assertEquals(LaneSide.LEFT, BeltGeometry.farLane(Direction.NORTH, Direction.EAST));
        assertEquals(LaneSide.LEFT, BeltGeometry.farLane(Direction.EAST, Direction.SOUTH));
        assertEquals(LaneSide.RIGHT, BeltGeometry.farLane(Direction.NORTH, Direction.SOUTH));
    }

    @Test void nearLaneForSideLoading() {
        // Item moving east arrives at a north-going belt from its west (left) side.
        assertEquals(LaneSide.LEFT, BeltGeometry.nearLane(Direction.NORTH, Direction.EAST));
        assertEquals(LaneSide.RIGHT, BeltGeometry.nearLane(Direction.NORTH, Direction.WEST));
        assertNull(BeltGeometry.nearLane(Direction.NORTH, Direction.NORTH));
        assertNull(BeltGeometry.nearLane(Direction.NORTH, Direction.SOUTH));
    }

    @Test void straightLanesRunBackToFrontAtQuarterOffsets() {
        // North: forward is -z, right is +x; the left lane is at x = 0.25.
        assertPoint(0.25, 1.0, BeltGeometry.lanePoint(Direction.NORTH, BeltShape.STRAIGHT, LaneSide.LEFT, 0));
        assertPoint(0.25, 0.0, BeltGeometry.lanePoint(Direction.NORTH, BeltShape.STRAIGHT, LaneSide.LEFT, 1));
        assertPoint(0.75, 0.5, BeltGeometry.lanePoint(Direction.NORTH, BeltShape.STRAIGHT, LaneSide.RIGHT, 0.5));
        // East: forward +x, right +z.
        assertPoint(0.0, 0.25, BeltGeometry.lanePoint(Direction.EAST, BeltShape.STRAIGHT, LaneSide.LEFT, 0));
    }

    @Test void curvesEnterFromTheSideAndLeaveAtTheFront() {
        // CURVE_LEFT facing north: enters from the west edge, leaves through the north edge.
        assertPoint(0.0, 0.25, BeltGeometry.lanePoint(Direction.NORTH, BeltShape.CURVE_LEFT, LaneSide.LEFT, 0));
        assertPoint(0.0, 0.75, BeltGeometry.lanePoint(Direction.NORTH, BeltShape.CURVE_LEFT, LaneSide.RIGHT, 0));
        assertPoint(0.25, 0.0, BeltGeometry.lanePoint(Direction.NORTH, BeltShape.CURVE_LEFT, LaneSide.LEFT, 1));
        assertPoint(0.75, 0.0, BeltGeometry.lanePoint(Direction.NORTH, BeltShape.CURVE_LEFT, LaneSide.RIGHT, 1));
        // CURVE_RIGHT facing north: enters from the east edge.
        assertPoint(1.0, 0.25, BeltGeometry.lanePoint(Direction.NORTH, BeltShape.CURVE_RIGHT, LaneSide.RIGHT, 0));
        assertPoint(1.0, 0.75, BeltGeometry.lanePoint(Direction.NORTH, BeltShape.CURVE_RIGHT, LaneSide.LEFT, 0));
        assertPoint(0.75, 0.0, BeltGeometry.lanePoint(Direction.NORTH, BeltShape.CURVE_RIGHT, LaneSide.RIGHT, 1));
    }

    @Test void boxesRotateWithTheFacing() {
        // Hood over the front half of a north-facing piece.
        AABB north = BeltGeometry.box(Direction.NORTH, 0, 2, 0, 16, 8, 8).bounds();
        AABB east = BeltGeometry.box(Direction.EAST, 0, 2, 0, 16, 8, 8).bounds();
        AABB south = BeltGeometry.box(Direction.SOUTH, 0, 2, 0, 16, 8, 8).bounds();
        assertEquals(new AABB(0, 0.125, 0, 1, 0.5, 0.5), north);
        assertEquals(new AABB(0.5, 0.125, 0, 1, 0.5, 1), east);
        assertEquals(new AABB(0, 0.125, 0.5, 1, 0.5, 1), south);
    }
}
