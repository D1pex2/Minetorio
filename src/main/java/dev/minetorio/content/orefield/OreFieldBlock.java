package dev.minetorio.content.orefield;

import dev.minetorio.core.orefield.OreType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A block of an ore field: a plain block without item or block entity; its ore amount lives in the
 * chunk's {@link OreFieldData}. Breaking it as a player ("mining" it, any tool or bare hands, through
 * the vanilla break path) yields exactly one ore and leaves the block in place until it is empty,
 * when it turns into dirt ({@link OreFields#mineByHand}); in creative mode it turns into dirt at
 * once. {@link #DEPLETED} switches to a paler texture below 10 % of the initial amount.
 */
public class OreFieldBlock extends Block {
    public static final BooleanProperty DEPLETED = BooleanProperty.create("depleted");

    private final OreType type;

    public OreFieldBlock(OreType type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(DEPLETED, false));
    }

    public OreType type() { return type; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DEPLETED);
    }

    /** Never removes the block itself: on the server one ore is mined instead (the client keeps the block too). */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (level instanceof ServerLevel server) {
            if (player.isCreative()) OreFields.exhaust(server, pos);
            else OreFields.mineByHand(server, pos, player);
        }
        return false;
    }

    /** Right-click with an empty hand: the amount left, in the action bar. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        player.displayClientMessage(OreFields.amountMessage(level, pos), true);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(this)) OreFields.forget(level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
