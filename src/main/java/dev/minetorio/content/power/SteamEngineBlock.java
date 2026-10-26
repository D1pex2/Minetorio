package dev.minetorio.content.power;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Steam engine controller (3 wide × 5 long × 2 high), placed looking along its length. Steam ports
 * at the middle of both short ends, so engines chain end to end like in Factorio (each pair of
 * touching ports flows directly, see {@link FluidPorts}). Body and flywheel are drawn by a block
 * entity renderer (R19).
 */
public class SteamEngineBlock extends SteamMachineBlock {
    public static final MapCodec<SteamEngineBlock> CODEC = simpleCodec(SteamEngineBlock::new);
    private static final List<FluidPorts.Spec> PORTS = List.of(
            new FluidPorts.Spec(1, 0, FluidPorts.Side.BACK, "steam"),
            new FluidPorts.Spec(1, 4, FluidPorts.Side.FRONT, "steam"));

    public SteamEngineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public MultiblockShape shape() { return MultiblockShape.STEAM_ENGINE; }

    @Override
    public List<FluidPorts.Spec> fluidPorts() { return PORTS; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteamEngineBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends SteamMachineBlockEntity> blockEntityType() {
        return ModBlockEntities.STEAM_ENGINE.get();
    }
}
