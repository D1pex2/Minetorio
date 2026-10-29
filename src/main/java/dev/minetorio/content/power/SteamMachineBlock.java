package dev.minetorio.content.power;

import dev.minetorio.content.fluid.FluidPortBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Controller block shared by the steam-power machines (offshore pump, boiler, steam engine): fluid
 * ports ({@link FluidPortBlock}), ticking, and a status line on right-click with an empty hand.
 */
public abstract class SteamMachineBlock extends MultiblockControllerBlock implements FluidPortBlock {
    protected SteamMachineBlock(Properties properties) {
        super(properties);
    }

    protected abstract BlockEntityType<? extends SteamMachineBlockEntity> blockEntityType();

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type != blockEntityType() ? null : level.isClientSide()
                ? (l, p, s, be) -> ((SteamMachineBlockEntity) be).tickClient()
                : (l, p, s, be) -> ((SteamMachineBlockEntity) be).tickServer(l);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof SteamMachineBlockEntity be)) return InteractionResult.PASS;
        dev.minetorio.content.machineui.MachineUi.open(player, be);
        return InteractionResult.CONSUME;
    }
}
