package dev.minetorio.content.machine;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.ModelPoint;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.data.Phase1Data;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
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
 * Burner mining drill controller (2×2×2, mines the 2×2 under itself). Body: block model on the
 * controller; drill head: block entity renderer. Right-click with fuel puts it into the fuel slot.
 */
public class BurnerDrillBlock extends MiningDrillBlock {
    public static final MapCodec<BurnerDrillBlock> CODEC = simpleCodec(BurnerDrillBlock::new);

    public BurnerDrillBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public MultiblockShape shape() { return MultiblockShape.BURNER_MINING_DRILL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BurnerDrillBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends MiningDrillBlockEntity> blockEntityType() {
        return ModBlockEntities.BURNER_MINING_DRILL.get();
    }

    /** Right-click with fuel puts the whole held stack (up to 64) into the fuel slot; fuel with data components is refused. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !MachineItems.isPlain(stack)
                || !Phase1Data.fuelValues().containsKey(MachineItems.id(stack))) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof BurnerDrillBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        int accepted = be.addFuel(MachineItems.id(stack), stack.getCount());
        if (accepted == 0) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        stack.consume(accepted, player);
        return ItemInteractionResult.CONSUME;
    }

    /** Client: while it mines, smoke rises from the chimney (model center frame (13, 31, 14)). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof MiningDrillBlockEntity be) || !be.isWorking()) return;
        ModelPoint.puff(level, random, ParticleTypes.SMOKE, 2, pos, be.facing(), shape(), 13, 31, 14, 2, 0.05);
    }
}
