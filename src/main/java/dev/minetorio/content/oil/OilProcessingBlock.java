package dev.minetorio.content.oil;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.fluid.FluidPort;
import dev.minetorio.content.fluid.FluidPortBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.multiblock.ModelPoint;
import dev.minetorio.core.oil.OilRecipe.MachineKind;
import java.util.List;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

public final class OilProcessingBlock extends MultiblockControllerBlock implements FluidPortBlock {
    private final MachineKind kind;
    private final MultiblockShape shape;
    private final MapCodec<OilProcessingBlock> codec;
    public OilProcessingBlock(MachineKind kind, Properties properties) {
        super(properties); this.kind = kind;
        shape = kind == MachineKind.REFINERY ? new MultiblockShape(5, 5, 3) : new MultiblockShape(3, 3, 2);
        codec = simpleCodec(p -> new OilProcessingBlock(kind, p));
    }
    public MachineKind kind() { return kind; }
    @Override public MultiblockShape shape() { return shape; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }
    @Override public List<FluidPort> fluidPorts() {
        return kind == MachineKind.REFINERY ? List.of(
            FluidPort.input(1, 0, FluidPort.Side.BACK, null), FluidPort.input(3, 0, FluidPort.Side.BACK, null),
            FluidPort.output(0, 4, FluidPort.Side.FRONT, null), FluidPort.output(2, 4, FluidPort.Side.FRONT, null), FluidPort.output(4, 4, FluidPort.Side.FRONT, null))
            : List.of(FluidPort.input(0, 0, FluidPort.Side.BACK, null), FluidPort.input(2, 0, FluidPort.Side.BACK, null),
            FluidPort.output(0, 2, FluidPort.Side.FRONT, null), FluidPort.output(2, 2, FluidPort.Side.FRONT, null));
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new OilProcessingBlockEntity(pos, state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, OilRegistry.PROCESSING_BE.get(), OilProcessingBlockEntity::serverTick);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof OilProcessingBlockEntity be)) return InteractionResult.PASS;
        player.openMenu(be, pos); return InteractionResult.CONSUME;
    }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof OilProcessingBlockEntity be) || !be.isWorking()) return;
        if (random.nextInt(3)!=0) return;
        if(kind==MachineKind.REFINERY) {
            ModelPoint.puff(level,random,ParticleTypes.SMOKE,1,pos,state.getValue(FACING),shape,8,55,8,3,0.04);
            if(random.nextInt(3)==0) ModelPoint.puff(level,random,ParticleTypes.FLAME,1,pos,state.getValue(FACING),shape,8,55,8,1,0.01);
        } else ModelPoint.puff(level,random,ParticleTypes.SMOKE,1,pos,state.getValue(FACING),shape,8,34,8,2,0.025);
    }
}
