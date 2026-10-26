package dev.minetorio.content.machine;

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
 * Assembling machine controller (3×3×2), tiers 1 and 2 differ in crafting speed and power. Its
 * block model is empty (particle texture only): body and spinning gear are drawn by a block entity
 * renderer at the footprint's center (R19). Right-click (any piece) opens the GUI.
 */
public class AssemblerBlock extends MultiblockControllerBlock {
    private final MapCodec<AssemblerBlock> codec;
    private final double speed;
    private final long power;

    /**
     * @param speed crafting speed (recipe time multiplier 1/speed)
     * @param power joules per tick while crafting
     */
    public AssemblerBlock(double speed, long power, Properties properties) {
        super(properties);
        this.speed = speed;
        this.power = power;
        this.codec = simpleCodec(p -> new AssemblerBlock(speed, power, p));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }

    public double speed() { return speed; }

    public long power() { return power; }

    @Override
    public MultiblockShape shape() { return MultiblockShape.ASSEMBLING_MACHINE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssemblerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.ASSEMBLING_MACHINE.get(), level.isClientSide()
                ? (l, p, s, be) -> be.tickClient()
                : (l, p, s, be) -> be.tickServer(l));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof AssemblerBlockEntity be)) return InteractionResult.PASS;
        player.openMenu(be, pos);
        return InteractionResult.CONSUME;
    }
}
