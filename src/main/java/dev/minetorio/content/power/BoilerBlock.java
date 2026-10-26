package dev.minetorio.content.power;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.data.Phase1Data;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Boiler controller (3 wide × 2 deep × 2 high). The fire side faces the placer; water enters and
 * leaves through the left and right ends of the near row (both ports share the one water box, so
 * water passes through to the next boiler), steam leaves through the middle of the far side (the
 * facing). Fuel: right-click with a fuel item, or inserters (no GUI slot in phase 1). Body and fire
 * glow are drawn by a block entity renderer (R19).
 */
public class BoilerBlock extends SteamMachineBlock {
    public static final MapCodec<BoilerBlock> CODEC = simpleCodec(BoilerBlock::new);
    /** Port indices: 0, 1 water (left, right), 2 steam. */
    private static final List<FluidPorts.Spec> PORTS = List.of(
            new FluidPorts.Spec(0, 0, FluidPorts.Side.LEFT, "water"),
            new FluidPorts.Spec(2, 0, FluidPorts.Side.RIGHT, "water"),
            new FluidPorts.Spec(1, 1, FluidPorts.Side.FRONT, "steam"));

    public BoilerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public MultiblockShape shape() { return MultiblockShape.BOILER; }

    @Override
    public List<FluidPorts.Spec> fluidPorts() { return PORTS; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoilerBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends SteamMachineBlockEntity> blockEntityType() {
        return ModBlockEntities.BOILER.get();
    }

    /** Right-click with fuel puts the whole held stack (up to 64) into the fuel slot. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !Phase1Data.fuelValues().containsKey(stack.getItemHolder().getRegisteredName())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof BoilerBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        int accepted = be.addFuel(stack.getItemHolder().getRegisteredName(), stack.getCount());
        if (accepted == 0) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        stack.consume(accepted, player);
        return ItemInteractionResult.CONSUME;
    }
}
