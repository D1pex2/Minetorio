package dev.minetorio.content.rail;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import static dev.minetorio.core.rail.RailGeometry.*;

/** Направление задается взглядом по восьми секторам; приседание укладывает кривую. */
public final class RailItem extends Item {
    public RailItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.FAIL;
        BlockPos at=context.getClickedPos();
        if (!(context.getLevel().getBlockState(at).getBlock() instanceof RailCellBlock)) at=at.relative(context.getClickedFace());
        int eighth=Math.floorMod(Math.round((player.getYRot()+180)/45),8);
        Shape shape=player.isShiftKeyDown()?Shape.CURVE:(eighth%2==0?Shape.STRAIGHT:Shape.DIAGONAL);
        Piece piece=new Piece(UUID.randomUUID(),new Point(Math.floorDiv(at.getX(),2)*2,at.getY(),Math.floorDiv(at.getZ(),2)*2),shape,eighth/2,player.isShiftKeyDown() && eighth%2==1);
        return RailPlacement.place(player,piece)?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
}
