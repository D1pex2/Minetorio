package dev.minetorio.content.oil;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.ModelPoint;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Stone furnace controller (2×2×2). Right-click (any piece) opens its GUI. {@link #LIT} is set while
 * it smelts: the model shows the lit front and the block gives light.
 */
public class SteelFurnaceBlock extends MultiblockControllerBlock {
    public static final MapCodec<SteelFurnaceBlock> CODEC = simpleCodec(SteelFurnaceBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Light level while lit. */
    public static final int LIGHT = 13;

    public SteelFurnaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteelFurnaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, OilRegistry.STEEL_FURNACE_BE.get(), SteelFurnaceBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof SteelFurnaceBlockEntity be)) return InteractionResult.PASS;
        player.openMenu(be, pos);
        return InteractionResult.CONSUME;
    }

    /** Client: while lit, smoke rises from the vent on top (model center frame (8, 24, 8)). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        ModelPoint.puff(level, random, ParticleTypes.SMOKE, 2, pos, state.getValue(FACING), shape(), 8, 24.5, 8, 6, 0.04);
    }
}

