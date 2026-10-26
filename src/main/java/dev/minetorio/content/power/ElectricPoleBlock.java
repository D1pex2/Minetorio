package dev.minetorio.content.power;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Small electric pole: a thin post that connects by wire to every pole within
 * {@value PowerGridManager#WIRE_REACH} blocks and supplies the 5×5×5 cube around itself (see
 * {@link PowerGridManager}). Wires are drawn by the client from the connections in the block
 * entity's update tag.
 */
public class ElectricPoleBlock extends BaseEntityBlock {
    public static final MapCodec<ElectricPoleBlock> CODEC = simpleCodec(ElectricPoleBlock::new);
    private static final VoxelShape SHAPE = box(6, 0, 6, 10, 24, 10);

    public ElectricPoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricPoleBlockEntity(pos, state);
    }
}
