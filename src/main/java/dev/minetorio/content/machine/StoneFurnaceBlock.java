package dev.minetorio.content.machine;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Stone furnace controller (2×2×2). No smelting logic yet. */
public class StoneFurnaceBlock extends MultiblockControllerBlock {
    public static final MapCodec<StoneFurnaceBlock> CODEC = simpleCodec(StoneFurnaceBlock::new);

    public StoneFurnaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public MultiblockShape shape() {
        return MultiblockShape.STONE_FURNACE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StoneFurnaceBlockEntity(pos, state);
    }
}
