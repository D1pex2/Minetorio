package dev.minetorio.content.power;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.core.power.PoleType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Medium electric pole (1×1×4), big electric pole (2×2×5) and substation (2×2×3): multiblock
 * controllers of a {@link PoleType} (footprint size × size, height from the type). Wire reach and
 * supply area come from the type ({@link PowerGridManager}). The block model is empty (particle
 * only); {@code MultiblockPoleRenderer} draws the pole and its wires.
 *
 * <p>Collision follows the model: the medium pole is a thin lattice mast; the 2×2 poles have a full
 * bottom layer (the big pole's splayed legs, the substation's body) and a mast around the
 * footprint's center above it.
 */
public class MultiblockPoleBlock extends MultiblockControllerBlock implements PowerPole {
    public static final MapCodec<MultiblockPoleBlock> CODEC = simpleCodec(p -> new MultiblockPoleBlock(PoleType.MEDIUM, p));

    private final PoleType type;
    private final MultiblockShape shape;
    /** Half-width (px) of the mast's collision box around the footprint's center. */
    private final double mastHalf;

    public MultiblockPoleBlock(PoleType type, Properties properties) {
        super(properties);
        this.type = type;
        this.shape = new MultiblockShape(type.size(), type.size(), type.height());
        this.mastHalf = switch (type) {
            case MEDIUM -> 3;
            case BIG -> 6;
            default -> 5;
        };
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public PoleType poleType() { return type; }

    @Override
    public MultiblockShape shape() { return shape; }

    @Override
    public VoxelShape partShape(BlockState controllerState, BlockPos partOffset) {
        if (type.size() > 1 && partOffset.getY() == 0) return Shapes.block();
        Direction facing = controllerState.getValue(FACING);
        Direction right = facing.getClockWise();
        double w = (type.size() - 1) / 2.0;
        // footprint center relative to this part's block corner, in blocks
        double cx = 0.5 + right.getStepX() * w + facing.getStepX() * w - partOffset.getX();
        double cz = 0.5 + right.getStepZ() * w + facing.getStepZ() * w - partOffset.getZ();
        double h = mastHalf / 16;
        double x0 = Math.max(0, cx - h), x1 = Math.min(1, cx + h);
        double z0 = Math.max(0, cz - h), z1 = Math.min(1, cz + h);
        if (x1 <= x0 || z1 <= z0) return Shapes.empty();
        return Shapes.box(x0, 0, z0, x1, 1, z1);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return partShape(state, BlockPos.ZERO);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockPoleBlockEntity(pos, state);
    }
    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        dev.minetorio.content.machineui.MachineUi.open(player, level.getBlockEntity(pos));
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
    }

}
