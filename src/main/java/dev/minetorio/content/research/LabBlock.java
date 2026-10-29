package dev.minetorio.content.research;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Laboratory controller with manual pack insertion and a live slot window. */
public class LabBlock extends MultiblockControllerBlock {
    public static final MapCodec<LabBlock> CODEC = simpleCodec(LabBlock::new);

    public LabBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public MultiblockShape shape() { return MultiblockShape.LAB; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LabBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.LAB.get(), level.isClientSide()
                ? (l, p, s, be) -> be.tickClient()
                : (l, p, s, be) -> be.tickServer(l));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!LabBlockEntity.isPack(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof LabBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        int n = be.insertManually(stack);
        if (n > 0) stack.consume(n, player);
        dev.minetorio.content.machineui.MachineUi.open(player, be);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof LabBlockEntity be)) return InteractionResult.PASS;
        dev.minetorio.content.machineui.MachineUi.open(player, be);
        return InteractionResult.CONSUME;
    }
}
