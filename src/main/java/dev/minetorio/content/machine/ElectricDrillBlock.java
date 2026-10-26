package dev.minetorio.content.machine;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Electric mining drill controller (3×3×2, mines the 5×5 around its center). Its block model is
 * empty (particle texture only): the body and the drill head are drawn by a block entity renderer
 * at the footprint's center column (R19).
 */
public class ElectricDrillBlock extends MiningDrillBlock {
    public static final MapCodec<ElectricDrillBlock> CODEC = simpleCodec(ElectricDrillBlock::new);

    public ElectricDrillBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public MultiblockShape shape() { return MultiblockShape.ELECTRIC_MINING_DRILL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricDrillBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends MiningDrillBlockEntity> blockEntityType() {
        return ModBlockEntities.ELECTRIC_MINING_DRILL.get();
    }
}
