package dev.minetorio.content.belt;

import dev.minetorio.content.drag.DragPlacement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Transport belt item. Placed like any block item, except that a belt laid by drag placement
 * ({@link DragPlacement}) may go into the cell the dragging player stands in (the plate is 2 px
 * high and the player walks along the line); any other entity there still blocks it. A normal
 * click keeps the vanilla rule.
 */
public class BeltItem extends BlockItem {
    public BeltItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        if (super.canPlace(context, state)) return true;
        Player player = context.getPlayer();
        if (player == null || !DragPlacement.isDragPlacing(player)) return false;
        if (mustSurvive() && !state.canSurvive(context.getLevel(), context.getClickedPos())) return false;
        VoxelShape shape = state.getCollisionShape(context.getLevel(), context.getClickedPos(), CollisionContext.of(player));
        return context.getLevel().isUnobstructed(player, shape.move(context.getClickedPos().getX(),
                context.getClickedPos().getY(), context.getClickedPos().getZ()));
    }
}
