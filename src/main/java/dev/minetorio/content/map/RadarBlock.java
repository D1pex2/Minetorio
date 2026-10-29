package dev.minetorio.content.map;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Radar controller (3×3×3). No GUI: right-click (any piece) shows its status in the action bar.
 * Its block model is empty (particle only): the base and the turning dish are drawn by a block
 * entity renderer.
 */
public class RadarBlock extends MultiblockControllerBlock {
    public static final MapCodec<RadarBlock> CODEC = simpleCodec(RadarBlock::new);
    public static final MultiblockShape SHAPE = new MultiblockShape(3, 3, 3);

    public RadarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public MultiblockShape shape() { return SHAPE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.RADAR.get(), level.isClientSide()
                ? (l, p, s, be) -> be.tickClient()
                : (l, p, s, be) -> be.tickServer(l));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof RadarBlockEntity be)) return InteractionResult.PASS;
        dev.minetorio.content.machineui.MachineUi.open(player, be);
        return InteractionResult.CONSUME;
    }
}
