package dev.minetorio.content.multiblock;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Item of a multiblock machine. Placement targets the cell a normal block item would be placed at
 * and centers the footprint on it (R26): the controller goes to
 * {@link MultiblockControllerBlock#placementOrigin}, the footprint's left-front-bottom corner, on the
 * targeted cell's layer. {@link BlockItem#place} then runs as usual on the moved context, so the
 * whole footprint (origin included) is checked by the block's placement state, the item is consumed
 * once and the place sound plays at the controller.
 */
public class MultiblockBlockItem extends BlockItem {
    public MultiblockBlockItem(MultiblockControllerBlock block, Properties properties) {
        super(block, properties);
    }

    public MultiblockControllerBlock controller() {
        return (MultiblockControllerBlock) getBlock();
    }

    @Nullable
    @Override
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext ctx) {
        BlockPos target = ctx.getClickedPos();
        BlockPos origin = controller().placementOrigin(target, ctx.getHorizontalDirection());
        return origin.equals(target) ? ctx : new AtOrigin(ctx, origin);
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

        AtOrigin(BlockPlaceContext original, BlockPos origin) {
            super(original.getLevel(), original.getPlayer(), original.getHand(), original.getItemInHand(),
                    new BlockHitResult(Vec3.atBottomCenterOf(origin), Direction.UP, origin, false));
            this.original = original;
            this.replaceClicked = true; // getClickedPos() is the origin itself, whatever is there
        }

        @Override
        public boolean canPlace() {
            return getLevel().getBlockState(getClickedPos()).canBeReplaced(this);
        }

        @Override
        public Direction getHorizontalDirection() { return original == null ? super.getHorizontalDirection() : original.getHorizontalDirection(); }

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
