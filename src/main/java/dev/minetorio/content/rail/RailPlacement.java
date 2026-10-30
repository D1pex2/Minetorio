package dev.minetorio.content.rail;

import dev.minetorio.content.research.RecipeLocks;
import dev.minetorio.core.rail.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import static dev.minetorio.core.rail.RailGeometry.*;

/** Все проверки выполнены до изменения графа и оплаты. Вызовы выполняются на серверном потоке. */
public final class RailPlacement {
    private RailPlacement() {}
    public static BlockPos block(Point p) { return new BlockPos(p.x(),p.y(),p.z()); }
    public static boolean allowed(ServerPlayer player,BlockPos pos) {
        var level=player.serverLevel();
        return player.isAlive()&&!player.isSpectator()&&player.mayBuild()&&level.getWorldBorder().isWithinBounds(pos)
                &&!level.isOutsideBuildHeight(pos)&&level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null
                &&level.mayInteract(player,pos);
    }
    public static boolean reachable(ServerPlayer player,BlockPos pos) { return player.canInteractWithBlock(pos,2); }
    private static List<ItemStack> paymentStacks(ServerPlayer player,Item item) {
        List<ItemStack> stacks=new ArrayList<>();var inventory=dev.minetorio.content.character.CharacterInventory.main(player.getInventory());
        for(int slot=0;slot<inventory.getContainerSize();slot++)if(inventory.getItem(slot).is(item))stacks.add(inventory.getItem(slot));
        for(ItemStack stack:player.getInventory().offhand)if(stack.is(item))stacks.add(stack);
        return stacks;
    }
    public static boolean has(ServerPlayer player,Item item,int count) { return player.isCreative()||paymentStacks(player,item).stream().mapToInt(ItemStack::getCount).sum()>=count; }
    public static void pay(ServerPlayer player,Item item,int count) {
        if(player.isCreative())return;
        // После всех внешних callbacks серверный поток оплачивает те же допустимые stacks.
        List<ItemStack> stacks=paymentStacks(player,item);
        if(stacks.stream().mapToInt(ItemStack::getCount).sum()<count)throw new IllegalStateException("Недостаточно предметов для оплаты");
        for(ItemStack stack:stacks){int take=Math.min(count,stack.getCount());stack.shrink(take);count-=take;if(count==0)break;}
        player.getInventory().setChanged();
    }
    public static boolean samePath(Piece a,Piece b) {
        if(a.shape()!=b.shape()||a.origin().y()!=b.origin().y())return false;
        boolean ends=endpoint(a,End.A).equals(endpoint(b,End.A))&&endpoint(a,End.B).equals(endpoint(b,End.B))
                ||endpoint(a,End.A).equals(endpoint(b,End.B))&&endpoint(a,End.B).equals(endpoint(b,End.A));
        return ends&&sample(a,length(a)/2).position().equals(sample(b,length(b)/2).position());
    }
    public static boolean place(ServerPlayer player,Piece piece) {
        var level=player.serverLevel();var data=RailwaySavedData.get(level);var state=data.state();
        if(!RecipeLocks.isUnlocked(player.server,"rail")||!reachable(player,block(piece.origin())))return false;
        if(state.rails().stream().anyMatch(p->samePath(p,piece)))return true;
        int cost=piece.shape()==Shape.CURVE?4:1;
        if(state.graph().piece(piece.id()).isPresent()||!has(player,RailRegistry.RAIL.get(),cost))return false;
        for(Point cell:cells(piece)) {
            BlockPos pos=block(cell);
            if(!allowed(player,pos)||!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))return false;
            BlockState old=level.getBlockState(pos);
            if(!(old.getBlock() instanceof RailCellBlock)&&!old.canBeReplaced())return false;
            if(EventHooks.onBlockPlace(player,BlockSnapshot.create(level.dimension(),level,pos),Direction.UP))return false;
        }
        var rails=new ArrayList<>(state.rails());rails.add(piece);
        // Проверка графа не имеет побочных эффектов и предшествует оплате.
        try { new RailGraph(rails); } catch(IllegalArgumentException invalid) { return false; }
        if(!has(player,RailRegistry.RAIL.get(),cost))return false;
        state.replaceRails(rails);pay(player,RailRegistry.RAIL.get(),cost);data.rebindStops();RailwayManager.reconcileViews(level);return true;
    }
    public static boolean occupied(RailwayState state,UUID rail) {
        // Только физический интервал тел и сцепок, без свободной истории и будущей ленты.
        return state.trains().values().stream().anyMatch(t->TrainMotion.occupiedRails(state,t).contains(rail));
    }
    public static boolean remove(ServerPlayer player,UUID id) {
        var level=player.serverLevel();var state=RailwaySavedData.get(level).state();var found=state.graph().piece(id);
        if(found.isEmpty()||occupied(state,id)||!reachable(player,block(found.get().origin())))return false;
        for(Point cell:cells(found.get()))if(!allowed(player,block(cell)))return false;
        state.replaceRails(state.rails().stream().filter(p->!p.id().equals(id)).toList());
        RailwaySavedData.get(level).rebindStops();RailwayManager.reconcileViews(level);
        if(!player.isCreative())player.getInventory().placeItemBackInInventory(new ItemStack(RailRegistry.RAIL.get(),found.get().shape()==Shape.CURVE?4:1));return true;
    }
    public static void destroyCell(ServerLevel level,BlockPos pos) {
        var data=RailwaySavedData.get(level);var state=data.state();Point cell=new Point(pos.getX(),pos.getY(),pos.getZ());
        state.replaceRails(state.rails().stream().filter(p->!cells(p).contains(cell)).toList());data.rebindStops();
    }
    public static Optional<Cursor> nearest(ServerLevel level,net.minecraft.world.phys.Vec3 hit,double radius) {
        Cursor best=null;double distance=radius*radius;
        for(Piece piece:RailwaySavedData.get(level).state().rails()) {
            if(Math.abs(piece.origin().y()-hit.y)>1.5)continue;
            int count=(int)Math.ceil(length(piece)*32);
            for(int i=0;i<=count;i++) {
                double q=length(piece)*i/count;Vec p=sample(piece,q).position();double d=Math.pow(p.x()-hit.x,2)+Math.pow(p.z()-hit.z,2);
                if(d<distance){distance=d;best=new Cursor(new Traversal(piece.id(),true),q);}
            }
        }
        return Optional.ofNullable(best);
    }
    public static BlockPos signalPosition(RailwayState state,RailSignals.Signal signal) {
        Piece piece=state.graph().piece(signal.boundary().piece()).orElseThrow();Point p=endpoint(piece,signal.boundary().end());
        RailDir dir=outward(piece,signal.boundary().end());
        double sign=signal.intoPiece()?-1:1;Vec tangent=sample(piece,signal.boundary().end()==End.A?0:length(piece)).tangent();
        if(signal.boundary().end()==End.A)sign=-sign;
        return BlockPos.containing(p.x()-tangent.z()*sign*1.5,p.y(),p.z()+tangent.x()*sign*1.5);
    }
    public static Optional<UUID> attachSignal(ServerPlayer player,Port port,boolean into,RailSignals.Kind kind) {
        var level=player.serverLevel();var state=RailwaySavedData.get(level).state();
        Item item=kind==RailSignals.Kind.CHAIN?RailRegistry.RAIL_CHAIN_SIGNAL.get():RailRegistry.RAIL_SIGNAL.get();
        if(!RecipeLocks.isUnlocked(player.server,kind==RailSignals.Kind.CHAIN?"rail_chain_signal":"rail_signal")||state.graph().piece(port.piece()).isEmpty())return Optional.empty();
        for(var signal:state.signalDefinitions())if(signal.boundary().equals(port)&&signal.intoPiece()==into&&signal.kind()==kind)return Optional.of(signal.id());
        var signal=new RailSignals.Signal(UUID.randomUUID(),port,into,kind);BlockPos at=signalPosition(state,signal);
        if(!canAttach(player,at)||!has(player,item,1))return Optional.empty();
        var signals=new ArrayList<>(state.signalDefinitions());signals.add(signal);
        try { RailSignals.rebuild(state.graph(),signals,state.signals().revision()+1); } catch(IllegalArgumentException invalid){return Optional.empty();}
        state.replaceSignals(signals);pay(player,item,1);RailwayManager.reconcileViews(level);return Optional.of(signal.id());
    }
    private static boolean canAttach(ServerPlayer player,BlockPos pos) {
        var level=player.serverLevel();return allowed(player,pos)&&reachable(player,pos)&&level.getBlockState(pos).canBeReplaced()
                &&level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)
                &&!EventHooks.onBlockPlace(player,BlockSnapshot.create(level.dimension(),level,pos),Direction.UP);
    }
    public static Optional<UUID> attachStop(ServerPlayer player,Cursor connected,String name) {
        var level=player.serverLevel();var data=RailwaySavedData.get(level);var state=data.state();var found=state.graph().piece(connected.traversal().piece());
        if(found.isEmpty()||!RecipeLocks.isUnlocked(player.server,"train_stop")||name==null||name.isBlank())return Optional.empty();
        Piece piece=found.get();boolean forward=connected.traversal().forward();
        double center=connected.distance(),target=center+.5;
        if(target>length(piece))return Optional.empty();
        Pose pose=sample(piece,forward?center:length(piece)-center);double sign=forward?1:-1;
        BlockPos pos=BlockPos.containing(pose.position().x()-pose.tangent().z()*sign*2,pose.position().y(),pose.position().z()+pose.tangent().x()*sign*2);
        for(var stop:data.worldStops().values())if(stop.position().equals(pos))return Optional.of(stop.id());
        if(!canAttach(player,pos)||!has(player,RailRegistry.TRAIN_STOP.get(),1))return Optional.empty();
        UUID id=UUID.randomUUID();data.putStop(new RailwaySavedData.WorldStop(id,pos,name,true,OptionalInt.empty(),piece.origin(),piece.shape(),piece.quarterTurns(),piece.mirrored(),forward,target));
        pay(player,RailRegistry.TRAIN_STOP.get(),1);RailwayManager.reconcileViews(level);return Optional.of(id);
    }
    public static boolean destroyAttachment(ServerLevel level,BlockPos pos) {
        var data=RailwaySavedData.get(level);var state=data.state();
        var stops=data.worldStops().values().stream().filter(s->s.position().equals(pos)).map(RailwaySavedData.WorldStop::id).toList();stops.forEach(data::removeStop);
        var signals=state.signalDefinitions().stream().filter(s->!signalPosition(state,s).equals(pos)).toList();boolean removed=!stops.isEmpty()||signals.size()!=state.signalDefinitions().size();
        state.replaceSignals(signals);return removed;
    }
}
