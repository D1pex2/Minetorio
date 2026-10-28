package dev.minetorio.content.inserter;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.inserter.InserterType;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Inserter (all four types). {@link #FACING} is the drop direction (the player's horizontal look
 * direction at placement); items are picked up {@code reach} blocks behind and dropped
 * {@code reach} blocks ahead. The base is a block model; the arm is drawn by a block entity
 * renderer. A burner inserter is fuelled by right-clicking it with fuel; right-clicking a filter
 * inserter opens its filter GUI ({@link InserterFilterMenu}).
 */
public class InserterBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape BASE = Block.box(2, 0, 2, 14, 5, 14);

    private final InserterType type;
    private final MapCodec<InserterBlock> codec;

    public InserterBlock(InserterType type, Properties properties) {
        super(properties);
        this.type = type;
        this.codec = simpleCodec(p -> new InserterBlock(type, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public InserterType type() { return type; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return BASE; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InserterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
        return createTickerHelper(beType, ModBlockEntities.INSERTER.get(),
                level.isClientSide() ? InserterBlockEntity::clientTick : InserterBlockEntity::serverTick);
    }

    /** Burner inserter: right-click with fuel puts the whole held stack (up to 64) into its fuel slot (not fuel with data components). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!type.burner || stack.isEmpty()
                || !MachineItems.isPlain(stack)
                || !Phase1Data.fuelValues().containsKey(MachineItems.id(stack))) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof InserterBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        int accepted = be.addFuel(MachineItems.id(stack), stack.getCount());
        if (accepted == 0) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        stack.consume(accepted, player);
        return ItemInteractionResult.CONSUME;
    }

    /** Filter inserter: opens the filter GUI. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (type.filterSlots == 0) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof InserterBlockEntity) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        sp.openMenu(new SimpleMenuProvider((id, inventory, p) -> new InserterFilterMenu(id, inventory, pos),
                Component.translatable(getDescriptionId())), pos);
        return InteractionResult.CONSUME;
    }

    /** Drops the item in the hand and the fuel; the inserter item itself comes from the loot table. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof InserterBlockEntity be) be.dropContents();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
