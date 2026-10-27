package dev.minetorio.content.power;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Offshore pump (1×2×1), placed Factorio-style from the shore. The pump's two cells stand on land
 * (the controller is the back cell, the part the front cell at the water's edge); its facing points
 * to the water and it draws from the water source just past its front cell ({@link #waterPos}), or the
 * one a block lower ({@link #intakeWater}: the usual shore, where the land's top is level with the
 * water's surface). The water is never replaced. Aiming: see {@link #resolvePlacement}. Its output
 * port opens out of the controller's back. Block model on the controller (2 long).
 */
public class OffshorePumpBlock extends SteamMachineBlock {
    public static final MapCodec<OffshorePumpBlock> CODEC = simpleCodec(OffshorePumpBlock::new);
    private static final List<FluidPorts.Spec> PORTS = List.of(new FluidPorts.Spec(0, 0, FluidPorts.Side.BACK, "water"));

    public OffshorePumpBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public MultiblockShape shape() { return MultiblockShape.OFFSHORE_PUMP; }

    @Override
    public List<FluidPorts.Spec> fluidPorts() { return PORTS; }

    /** The cell level with the pump just past its front end; the intake water is here or one lower. */
    public static BlockPos waterPos(BlockPos controller, Direction facing) {
        return controller.relative(facing, MultiblockShape.OFFSHORE_PUMP.depth());
    }

    /** Whether {@code pos} holds a water source block (or a waterlogged block). */
    public static boolean isWaterSource(BlockGetter level, BlockPos pos) {
        FluidState f = level.getFluidState(pos);
        return f.is(FluidTags.WATER) && f.isSource();
    }

    /** The water source a pump with its controller at {@code controller} draws from, or null: {@link #waterPos} or the cell below it. */
    @Nullable
    public static BlockPos intakeWater(BlockGetter level, BlockPos controller, Direction facing) {
        BlockPos level0 = waterPos(controller, facing);
        if (isWaterSource(level, level0)) return level0;
        BlockPos lower = level0.below();
        return isWaterSource(level, lower) ? lower : null;
    }

    /** Whether a pump placed at {@code controller} facing {@code facing} has water and ground at its front cell. */
    private static boolean shoreOk(BlockGetter level, BlockPos controller, Direction facing) {
        BlockPos front = controller.relative(facing);
        return intakeWater(level, controller, facing) != null && level.getBlockState(front.below()).isFaceSturdy(level, front.below(), Direction.UP);
    }

    /**
     * Factorio-style aiming. The player targets the shore (the cell above a land block next to
     * water, or the side of such a block) or the water itself (also its floor, seen through the water);
     * the pump turns its intake toward a water source next to the shore cell and its front cell
     * stands on the shore, its back cell inland. Candidates are tried in the player's look
     * direction, then right, left, behind; the first that fits wins, else the first with water
     * (shown red by the preview), else the plain placement (no water: refused).
     */
    @Override
    public Placement resolvePlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos target = ctx.getClickedPos();
        Direction look = ctx.getHorizontalDirection();
        List<Direction> order = List.of(look, look.getClockWise(), look.getCounterClockWise(), look.getOpposite());
        List<Placement> candidates = new ArrayList<>();
        if (level.getFluidState(target).is(FluidTags.WATER)) {
            BlockPos water = target;
            for (int i = 0; i < 16 && isWaterSource(level, water.above()); i++) water = water.above();
            if (isWaterSource(level, water)) {
                for (Direction f : order) {
                    BlockPos shore = water.relative(f.getOpposite());
                    candidates.add(new Placement(shore.relative(f.getOpposite()), f));
                    candidates.add(new Placement(shore.above().relative(f.getOpposite()), f));
                }
            }
        } else {
            for (Direction f : order) {
                Placement p = new Placement(target.relative(f.getOpposite()), f);
                if (intakeWater(level, p.origin(), f) != null) candidates.add(p);
            }
        }
        for (Placement p : candidates) if (valid(ctx, p)) return p;
        return candidates.isEmpty() ? super.resolvePlacement(ctx) : candidates.getFirst();
    }

    private boolean valid(BlockPlaceContext ctx, Placement p) {
        return footprintFree(ctx, p.origin(), p.facing()) && shoreOk(ctx.getLevel(), p.origin(), p.facing());
    }

    /** Anchor is the front cell (the shore cell), unlike other machines. */
    @Override
    public BlockPos placementOrigin(BlockPos target, Direction facing) {
        return target.relative(facing, -(shape().depth() - 1));
    }

    @Override
    public BlockPos placementTarget(BlockPos origin, Direction facing) {
        return origin.relative(facing, shape().depth() - 1);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null) return null;
        return shoreOk(ctx.getLevel(), ctx.getClickedPos(), state.getValue(MultiblockControllerBlock.FACING)) ? state : null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OffshorePumpBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends SteamMachineBlockEntity> blockEntityType() {
        return ModBlockEntities.OFFSHORE_PUMP.get();
    }
}
