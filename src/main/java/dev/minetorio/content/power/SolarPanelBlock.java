package dev.minetorio.content.power;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Solar panel controller (3×3×1, Factorio's 3×3). Static: drawn by {@code SolarPanelRenderer}
 * (block model particle only). Right-click shows its output in the action bar.
 */
public class SolarPanelBlock extends MultiblockControllerBlock {
    public static final MapCodec<SolarPanelBlock> CODEC = simpleCodec(SolarPanelBlock::new);
    public static final MultiblockShape SHAPE = new MultiblockShape(3, 3, 1);
    /** Low frame with the tilted panels on it (art/gen_energy.py: the panels top out at about 17 px). */
    private static final VoxelShape BODY = Block.box(0, 0, 0, 16, 14, 16);

    public SolarPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public MultiblockShape shape() { return SHAPE; }

    @Override
    public VoxelShape partShape(BlockState controllerState, BlockPos partOffset) { return BODY; }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                  net.minecraft.world.phys.shapes.CollisionContext ctx) {
        return BODY;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarPanelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.SOLAR_PANEL.get(), (l, p, s, be) -> be.tickServer(l));
    }

    /** A block changing next to the controller may have covered it: recheck the sky now. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (level.getBlockEntity(pos) instanceof SolarPanelBlockEntity be) be.markSkyDirty();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof SolarPanelBlockEntity be)) return InteractionResult.PASS;
        player.displayClientMessage(be.status(), true);
        return InteractionResult.CONSUME;
    }
}
