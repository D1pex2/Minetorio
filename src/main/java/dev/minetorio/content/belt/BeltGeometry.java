package dev.minetorio.content.belt;

import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.core.belt.BeltShape;
import dev.minetorio.core.belt.BeltSlope;
import dev.minetorio.core.belt.LaneSink;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Belt-to-belt geometry: hand-off targets, lane sides, lane paths, rotated shapes. */
public final class BeltGeometry {
    /** Lane centers are this far left/right of the belt's center line. */
    public static final double LANE_OFFSET = 0.25;

    private BeltGeometry() {}

    /** {@link #sinkTo(Level, BlockPos, BeltSlope, Direction, LaneSide)} from a flat node. */
    @Nullable
    public static LaneSink sinkTo(Level level, BlockPos from, Direction travel, LaneSide side) {
        return sinkTo(level, from, BeltSlope.FLAT, travel, side);
    }

    /**
     * Where an item leaving {@code from} (whose exit edge has slope {@code slope}) in direction
     * {@code travel} on lane {@code side} goes. The target cell is the one in front, one block
     * higher after an ascending ramp:
     * <ul>
     *   <li>a belt node there that accepts it (straight/curve continuation: lanes one to one;
     *       a belt seen from its side: its near lane at mid-length — side-loading; underground
     *       entrance; splitter input). A descending ramp there is not entered: its entry edge is
     *       one block higher;</li>
     *   <li>otherwise, unless leaving an ascending ramp, a descending ramp one block lower facing
     *       {@code travel} (its entry edge is level with this exit);</li>
     *   <li>otherwise, on the server, a block with an {@code IItemHandler} (e.g. a chest);</li>
     *   <li>otherwise null: the item waits at the end of its lane.</li>
     * </ul>
     * The returned sink receives the overflow past the sender's lane end.
     */
    @Nullable
    public static LaneSink sinkTo(Level level, BlockPos from, BeltSlope slope, Direction travel, LaneSide side) {
        BlockPos q = from.relative(travel).above(slope.exitLevel());
        if (!level.isLoaded(q)) return null;
        BeltNode node = BeltTicker.of(level).at(q);
        if (node != null) {
            if (node instanceof BeltBlockEntity b && b.slope() == BeltSlope.DESCENDING) return null;
            return node.incoming(q, travel, side);
        }
        if (slope != BeltSlope.ASCENDING && level.isLoaded(q.below())
                && BeltTicker.of(level).at(q.below()) instanceof BeltBlockEntity b
                && b.slope() == BeltSlope.DESCENDING && b.facing() == travel) {
            return b.incoming(q.below(), travel, side);
        }
        if (level.isClientSide()) return null; // the server decides what containers accept
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, q, travel.getOpposite());
        if (handler == null) return null;
        return (item, overflow) -> {
            ItemStack stack = LaneStacks.stack(item, level.registryAccess());
            return !stack.isEmpty() && ItemHandlerHelper.insertItem(handler, stack, false).isEmpty();
        };
    }

    /** A sink that resolves {@link #sinkTo} only when an item actually reaches the lane end. */
    public static LaneSink lazySinkTo(Level level, BlockPos from, Direction travel, LaneSide side) {
        return lazySinkTo(level, from, BeltSlope.FLAT, travel, side);
    }

    /** A sink that resolves {@link #sinkTo} (from a ramp) only when an item actually reaches the lane end. */
    public static LaneSink lazySinkTo(Level level, BlockPos from, BeltSlope slope, Direction travel, LaneSide side) {
        return (item, overflow) -> {
            LaneSink sink = sinkTo(level, from, slope, travel, side);
            return sink != null && sink.offer(item, overflow);
        };
    }

    /**
     * The lane of a belt facing {@code beltFacing} farther from {@code fromSide} (the side of the
     * belt the inserter stands on). From behind or in front there is no far side: RIGHT.
     */
    public static LaneSide farLane(Direction beltFacing, Direction fromSide) {
        if (fromSide == beltFacing.getCounterClockWise()) return LaneSide.RIGHT;
        if (fromSide == beltFacing.getClockWise()) return LaneSide.LEFT;
        return LaneSide.RIGHT;
    }

    /**
     * The lane of a belt facing {@code beltFacing} nearer to an item arriving sideways while
     * travelling in {@code travel}, or null if {@code travel} is along the belt.
     */
    @Nullable
    public static LaneSide nearLane(Direction beltFacing, Direction travel) {
        if (travel == beltFacing.getClockWise()) return LaneSide.LEFT;   // arrives from the left side
        if (travel == beltFacing.getCounterClockWise()) return LaneSide.RIGHT;
        return null;
    }

    /**
     * Whether the block at {@code pos} pushes items out in direction {@code travel} at its own
     * level (into the cell in front of it). An ascending ramp pushes into the cell one block higher.
     */
    public static boolean outputsInto(BlockGetter level, BlockPos pos, Direction travel) {
        BlockState state = controllerState(level, pos);
        if (state.getBlock() instanceof BeltBlock) {
            return state.getValue(BeltBlock.FACING) == travel && state.getValue(BeltBlock.SLOPE) != BeltBlock.Slope.ASCENDING;
        }
        if (state.getBlock() instanceof UndergroundBeltBlock) {
            return state.getValue(UndergroundBeltBlock.TYPE) == UndergroundBeltBlock.Type.OUTPUT
                    && state.getValue(UndergroundBeltBlock.FACING) == travel;
        }
        if (state.getBlock() instanceof SplitterBlock) return state.getValue(SplitterBlock.FACING) == travel;
        return false;
    }

    /** Whether the block at {@code pos} is an ascending belt ramp facing {@code travel} (it feeds the cell in front, one higher). */
    public static boolean rampsInto(BlockGetter level, BlockPos pos, Direction travel) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof BeltBlock && state.getValue(BeltBlock.FACING) == travel
                && state.getValue(BeltBlock.SLOPE) == BeltBlock.Slope.ASCENDING;
    }

    /** Whether the block at {@code pos} is a belt, underground belt or splitter (or a part of one). */
    public static boolean isBeltLike(BlockGetter level, BlockPos pos) {
        BlockState state = controllerState(level, pos);
        return state.getBlock() instanceof BeltBlock || state.getBlock() instanceof UndergroundBeltBlock
                || state.getBlock() instanceof SplitterBlock;
    }

    private static BlockState controllerState(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof MultiblockPartBlock) {
            BlockPos c = MultiblockPartBlock.controllerPos(level, pos);
            return c == null ? state : level.getBlockState(c);
        }
        return state;
    }

    /**
     * Point on a lane in block-local coordinates {x, z} (0..1), for a belt facing {@code facing}.
     * {@code t} runs 0..1 from the lane's entrance to its exit. Straight lanes run from the back
     * edge to the front edge; curves follow a quarter circle around the inner corner.
     */
    public static double[] lanePoint(Direction facing, BeltShape shape, LaneSide side, double t) {
        double lat = side == LaneSide.LEFT ? -LANE_OFFSET : LANE_OFFSET;
        double a; // along the facing, from the block center
        double b; // to the right of the facing, from the block center
        double theta = t * Math.PI / 2;
        switch (shape) {
            case CURVE_LEFT -> {
                double r = 0.5 + lat; // pivot at the front-left corner; LEFT lane is inner
                a = 0.5 - r * Math.cos(theta);
                b = -0.5 + r * Math.sin(theta);
            }
            case CURVE_RIGHT -> {
                double r = 0.5 - lat; // pivot at the front-right corner; RIGHT lane is inner
                a = 0.5 - r * Math.cos(theta);
                b = 0.5 - r * Math.sin(theta);
            }
            default -> {
                a = t - 0.5;
                b = lat;
            }
        }
        return local(facing, a, b);
    }

    /**
     * Unit travel direction {dx, dz} (world XZ) of a lane at {@code t}, matching {@link #lanePoint}:
     * the facing on straight belts, the arc's tangent on curves (entry direction at t=0, facing at t=1).
     */
    public static double[] laneTangent(Direction facing, BeltShape shape, double t) {
        double theta = t * Math.PI / 2;
        return switch (shape) {
            case CURVE_LEFT -> world(facing, Math.sin(theta), Math.cos(theta));
            case CURVE_RIGHT -> world(facing, Math.sin(theta), -Math.cos(theta));
            default -> world(facing, 1, 0);
        };
    }

    /** World XZ vector of {@code along} forward and {@code right} to the right of {@code facing}. */
    public static double[] world(Direction facing, double along, double right) {
        Direction r = facing.getClockWise();
        return new double[] {
            facing.getStepX() * along + r.getStepX() * right,
            facing.getStepZ() * along + r.getStepZ() * right
        };
    }

    /** Block-local {x, z} of the point {@code along} blocks forward and {@code right} blocks right of the center. */
    public static double[] local(Direction facing, double along, double right) {
        Direction r = facing.getClockWise();
        return new double[] {
            0.5 + facing.getStepX() * along + r.getStepX() * right,
            0.5 + facing.getStepZ() * along + r.getStepZ() * right
        };
    }

    /** A box given in pixels for a NORTH-facing block, rotated to {@code facing}. */
    public static VoxelShape box(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        double[] a = rotate(facing, x0, z0);
        double[] b = rotate(facing, x1, z1);
        return Shapes.box(Math.min(a[0], b[0]) / 16, y0 / 16, Math.min(a[1], b[1]) / 16,
                Math.max(a[0], b[0]) / 16, y1 / 16, Math.max(a[1], b[1]) / 16);
    }

    private static double[] rotate(Direction facing, double x, double z) {
        return switch (facing) {
            case EAST -> new double[] {16 - z, x};
            case SOUTH -> new double[] {16 - x, 16 - z};
            case WEST -> new double[] {z, 16 - x};
            default -> new double[] {x, z};
        };
    }
}
