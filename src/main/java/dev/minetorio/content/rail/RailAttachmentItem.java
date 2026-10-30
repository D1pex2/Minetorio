package dev.minetorio.content.rail;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import dev.minetorio.core.rail.RailSignals;
import static dev.minetorio.core.rail.RailGeometry.*;
public final class RailAttachmentItem extends Item {
    private final int kind;
    public RailAttachmentItem(Properties properties,int kind) { super(properties);this.kind=kind; }
    @Override public InteractionResult useOn(UseOnContext context) {
        if(context.getLevel().isClientSide())return InteractionResult.SUCCESS;
        if(!(context.getPlayer() instanceof ServerPlayer player))return InteractionResult.FAIL;
        var cursor=RailPlacement.nearest(player.serverLevel(),context.getClickLocation(),3);
        if(cursor.isEmpty())return InteractionResult.FAIL;
        var point=cursor.get();var piece=RailwaySavedData.get(player.serverLevel()).state().graph().piece(point.traversal().piece()).orElseThrow();
        var look=player.getLookAngle();var tangent=sample(piece,point.distance()).tangent();boolean forward=look.x*tangent.x()+look.z*tangent.z()>=0;
        if(kind==0) return RailPlacement.attachStop(player,new Cursor(new Traversal(piece.id(),forward),length(piece)/2),"Станция").isPresent()?InteractionResult.CONSUME:InteractionResult.FAIL;
        End end=point.distance()<length(piece)/2?End.A:End.B;
        return RailPlacement.attachSignal(player,new Port(piece.id(),end),forward==(end==End.A),kind==2?RailSignals.Kind.CHAIN:RailSignals.Kind.REGULAR).isPresent()?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
}
