package dev.minetorio.content.robot;

import dev.minetorio.content.multiblock.MultiblockBlockItem;
import dev.minetorio.content.registry.ModBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Creating and building ghosts. A ghost is placed at the cell a player's block would occupy (for a
 * multiblock: the controller position, the footprint's left-front-bottom corner) together with the
 * player's look direction; building it runs the item's normal placement with that look direction,
 * so belts, inserters and multiblocks come out exactly as if a player placed them there.
 */
public final class Ghosts {
    private Ghosts() {}

    /**
     * Places a ghost of {@code item} at {@code pos} as a player looking {@code facing} would place it.
     * False if the cell is not replaceable or the item is not a block item.
     */
    public static boolean place(ServerLevel level, BlockPos pos, Item item, Direction facing) {
        if (!(item instanceof BlockItem) || !level.getBlockState(pos).canBeReplaced()) return false;
        if (level.getBlockState(pos).is(ModBlocks.GHOST.get())) level.removeBlock(pos, false);
        BlockState preview = previewState(level, pos, item, facing);
        return put(level, pos, preview, facing, 0);
    }

    private static boolean put(ServerLevel level, BlockPos pos, BlockState target, Direction facing, long seq) {
        if (!level.setBlock(pos, ModBlocks.GHOST.get().defaultBlockState(), Block.UPDATE_ALL)) return false;
        if (!(level.getBlockEntity(pos) instanceof GhostBlockEntity be)) return false;
        be.setup(target, facing, seq);
        level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
        return true;
    }

    /**
     * The state {@code item} would get at {@code pos} placed looking {@code facing} (neighbours
     * included, as now); the block's default state turned to {@code facing} if placement says no.
     */
    public static BlockState previewState(Level level, BlockPos pos, Item item, Direction facing) {
        Block block = ((BlockItem) item).getBlock();
        BlockState s = block.getStateForPlacement(context(level, pos, null, new ItemStack(item), facing));
        if (s != null) return s;
        BlockState d = block.defaultBlockState();
        return d.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? d.setValue(BlockStateProperties.HORIZONTAL_FACING, facing) : d;
    }

    /**
     * Whether the ghost at {@code pos} can be built right now (a multiblock's footprint may be blocked).
     * The ghost itself is replaceable, so it does not block its own placement.
     */
    public static boolean canBuild(ServerLevel level, BlockPos pos) {
        GhostBlockEntity be = GhostBlock.at(level, pos);
        if (be == null || !be.isValid() || !(be.item() instanceof BlockItem bi)) return false;
        return bi.getBlock().getStateForPlacement(context(level, pos, null, new ItemStack(bi), be.facing())) != null;
    }

    /**
     * Builds the ghost at {@code pos} with {@code stack} (one item of the ghost's item; not consumed
     * here) through the item's normal placement, by {@code player} or a robot (null). The ghost is
     * restored if placement fails. True once the real block is placed.
     */
    public static boolean build(ServerLevel level, BlockPos pos, @Nullable Player player, ItemStack stack) {
        GhostBlockEntity be = GhostBlock.at(level, pos);
        if (be == null || !be.isValid() || !stack.is(be.item()) || !(stack.getItem() instanceof BlockItem item)) return false;
        BlockState target = be.target();
        Direction facing = be.facing();
        long seq = be.seq();
        level.removeBlock(pos, false);
        BlockPos click = MultiblockBlockItem.targetFor(item, pos, facing);
        boolean placed = item.place(context(level, click, player, stack.copyWithCount(1), facing)).consumesAction();
        if (!placed && level.getBlockState(pos).canBeReplaced()) put(level, pos, target, facing, seq);
        return placed;
    }

    /** A placement at {@code pos} (replacing what is there) looking {@code facing}. */
    private static BlockPlaceContext context(Level level, BlockPos pos, @Nullable Player player, ItemStack stack, Direction facing) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, hit) {
            @Override
            public Direction getHorizontalDirection() { return facing; }

            @Override
            public Direction getNearestLookingDirection() { return facing; }

            @Override
            public float getRotation() { return facing.toYRot(); }
        };
    }
}
