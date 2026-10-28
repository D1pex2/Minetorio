package dev.minetorio.content.robot;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Roboport controller (4×4×4, Factorio's 4×4 tiles; tall like Factorio's roboport). Its block model
 * is empty (particle only): the machine is drawn by {@code RoboportRenderer}. Right-click (any piece)
 * opens the robot slots.
 */
public class RoboportBlock extends MultiblockControllerBlock {
    public static final MapCodec<RoboportBlock> CODEC = simpleCodec(RoboportBlock::new);
    public static final MultiblockShape SHAPE = new MultiblockShape(4, 4, 4);

    /*
     * Top layer (art/gen_robots.py: roof at 50 px, iris hatch top at 55.6 px, pylons up to 64 px): the
     * corner cells hold the pylons (full block), the middle four the hatch, the edge cells only the roof.
     */
    private static final VoxelShape TOP_HATCH = Block.box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape TOP_ROOF = Block.box(0, 0, 0, 16, 2, 16);

    public RoboportBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public MultiblockShape shape() { return SHAPE; }

    @Override
    public VoxelShape partShape(BlockState controllerState, BlockPos partOffset) {
        if (partOffset.getY() < SHAPE.height() - 1) return Shapes.block();
        Direction facing = controllerState.getValue(FACING);
        Direction right = facing.getClockWise();
        int w = partOffset.getX() * right.getStepX() + partOffset.getZ() * right.getStepZ();
        int d = partOffset.getX() * facing.getStepX() + partOffset.getZ() * facing.getStepZ();
        boolean edgeW = w == 0 || w == SHAPE.width() - 1;
        boolean edgeD = d == 0 || d == SHAPE.depth() - 1;
        if (edgeW && edgeD) return Shapes.block();
        return edgeW || edgeD ? TOP_ROOF : TOP_HATCH;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RoboportBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.ROBOPORT.get(), (l, p, s, be) -> be.tickServer());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof RoboportBlockEntity be)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) sp.openMenu(be, pos);
        return InteractionResult.CONSUME;
    }
}
