package dev.minetorio.content.multiblock;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Item of a multiblock machine. Placement targets the cell a normal block item would be placed at
 * and centers the footprint on it (R26): the controller goes to
 * {@link MultiblockControllerBlock#placementOrigin}, the footprint's left-front-bottom corner, on the
 * targeted cell's layer (or wherever {@link MultiblockControllerBlock#resolvePlacement} says: the
 * offshore pump picks its own facing and cell). {@link BlockItem#place} then runs as usual on the moved context, so the
 * whole footprint (origin included) is checked by the block's placement state and the item is
 * consumed once. The place sound plays at the targeted cell, not at the controller: vanilla's sound
 * at the controller is muted ({@link #getPlaceSound}) and replayed by {@link #place}.
 */
public class MultiblockBlockItem extends BlockItem {
    public MultiblockBlockItem(MultiblockControllerBlock block, Properties properties) {
        super(block, properties);
    }

    /** Set while {@link #place} runs on this thread, so vanilla's place sound (at the controller) is muted. */
    private static final ThreadLocal<Boolean> PLACING = ThreadLocal.withInitial(() -> false);

    public MultiblockControllerBlock controller() {
        return (MultiblockControllerBlock) getBlock();
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        BlockPos target = ctx.getClickedPos();
        BlockPos origin = controller().resolvePlacement(ctx).origin();
        InteractionResult result;
        PLACING.set(true);
        try {
            result = super.place(ctx);
        } finally {
            PLACING.set(false);
        }
        if (result.consumesAction()) {
            Level level = ctx.getLevel();
            Player player = ctx.getPlayer();
            BlockState state = level.getBlockState(origin);
            SoundType sound = state.getSoundType(level, origin, player);
            level.playSound(player, target, sound.getPlaceSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        }
        return result;
    }

    @Override
    protected SoundEvent getPlaceSound(BlockState state, Level level, BlockPos pos, Player player) {
        return PLACING.get() ? SoundEvents.EMPTY : super.getPlaceSound(state, level, pos, player);
    }

    @Nullable
    @Override
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext ctx) {
        MultiblockControllerBlock.Placement placement = controller().resolvePlacement(ctx);
        boolean same = placement.origin().equals(ctx.getClickedPos()) && placement.facing() == ctx.getHorizontalDirection();
        return same ? ctx : new AtOrigin(ctx, placement.origin(), placement.facing());
    }

    /**
     * Cell to target with {@code item} looking {@code facing} so that a multiblock controller lands at
     * {@code origin}; {@code origin} itself for any other item. For programmatic placement (demo,
     * tests) that names machines by their controller position.
     */
    public static BlockPos targetFor(Item item, BlockPos origin, Direction facing) {
        return item instanceof MultiblockBlockItem m ? m.controller().placementTarget(origin, facing) : origin;
    }

    /**
     * The player's placement moved to {@code origin}; facing and player come from the original
     * ({@code original} is still null while the super constructor asks the hit block).
     */
    private static final class AtOrigin extends BlockPlaceContext {
        private final BlockPlaceContext original;
        private final Direction facing;

        AtOrigin(BlockPlaceContext original, BlockPos origin, Direction facing) {
            super(original.getLevel(), original.getPlayer(), original.getHand(), original.getItemInHand(),
                    new BlockHitResult(Vec3.atBottomCenterOf(origin), Direction.UP, origin, false));
            this.original = original;
            this.facing = facing;
            this.replaceClicked = true; // getClickedPos() is the origin itself, whatever is there
        }

        @Override
        public boolean canPlace() {
            return getLevel().getBlockState(getClickedPos()).canBeReplaced(this);
        }

        @Override
        public Direction getHorizontalDirection() { return facing == null ? super.getHorizontalDirection() : facing; }

        @Override
        public float getRotation() { return original == null ? super.getRotation() : original.getRotation(); }

        @Override
        public Direction getNearestLookingDirection() { return original == null ? super.getNearestLookingDirection() : original.getNearestLookingDirection(); }

        @Override
        public Direction getNearestLookingVerticalDirection() { return original == null ? super.getNearestLookingVerticalDirection() : original.getNearestLookingVerticalDirection(); }

        @Override
        public Direction[] getNearestLookingDirections() { return original == null ? super.getNearestLookingDirections() : original.getNearestLookingDirections(); }
    }
}
