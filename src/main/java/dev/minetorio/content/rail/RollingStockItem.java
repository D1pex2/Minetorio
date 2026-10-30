package dev.minetorio.content.rail;
import dev.minetorio.core.data.RailData.StockKind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
public final class RollingStockItem extends Item {
    private final StockKind kind;
    public RollingStockItem(Properties properties,StockKind kind) { super(properties);this.kind=kind; }
    @Override public InteractionResult useOn(UseOnContext context) {
        if(context.getLevel().isClientSide())return InteractionResult.SUCCESS;
        if(!(context.getPlayer() instanceof ServerPlayer player))return InteractionResult.FAIL;
        var cursor=RailPlacement.nearest(player.serverLevel(),context.getClickLocation(),2);
        return cursor.isPresent()&&RailwayManager.placeStock(player,kind,cursor.get(),player.isShiftKeyDown()).isPresent()?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
}
