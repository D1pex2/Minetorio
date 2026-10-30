package dev.minetorio.content.rail;

import dev.minetorio.content.belt.LaneStacks;
import dev.minetorio.content.research.RecipeLocks;
import dev.minetorio.core.data.RailData;
import dev.minetorio.core.data.RailData.StockKind;
import dev.minetorio.core.rail.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.RailwayState.*;

/** Одна симуляция измерения. Все сущности и клетки восстанавливаются из SavedData. */
public final class RailwayManager {
    private static final Map<ServerLevel,Views> VIEWS=new WeakHashMap<>();
    private static final TicketType<UUID> PASSENGER=TicketType.create("minetorio_rail_passenger",UUID::compareTo);
    private static final class Views {
        final Map<UUID,RollingStockEntity> cars=new HashMap<>();
        final Set<BlockPos> cells=new HashSet<>();
        final Map<UUID,ChunkPos> passengerTickets=new HashMap<>();
        boolean reconciling;
    }
    private RailwayManager() {}
    private static Views views(ServerLevel level) { return VIEWS.computeIfAbsent(level,k->new Views()); }
    public static boolean reconciling(ServerLevel level) { return views(level).reconciling; }
    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event)->{for(ServerLevel level:event.getServer().getAllLevels())tick(level);});
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event)->{if(event.getLevel() instanceof ServerLevel level)release(level);});
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event)->VIEWS.clear());
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event)->{
            if(!(event.getLevel() instanceof ServerLevel level)||!(event.getPlayer() instanceof ServerPlayer player))return;
            if(event.getState().getBlock() instanceof RailCellBlock) {
                Point point=new Point(event.getPos().getX(),event.getPos().getY(),event.getPos().getZ());
                var ids=RailwaySavedData.get(level).state().rails().stream().filter(p->cells(p).contains(point)).map(Piece::id).toList();
                event.setCanceled(true);
                if(ids.stream().anyMatch(id->RailPlacement.occupied(RailwaySavedData.get(level).state(),id)))return;
                for(UUID id:ids)RailPlacement.remove(player,id);
            } else if(event.getState().getBlock() instanceof TrainStopBlock || event.getState().getBlock() instanceof RailSignalBlock) {
                if(!RailPlacement.allowed(player,event.getPos())) {event.setCanceled(true);return;}
                // Сигнал снимает owner через onRemove и возвращает item после успешного playerDestroy.
                if(event.getState().getBlock() instanceof RailSignalBlock)return;
                Item item=event.getState().is(RailRegistry.STOP_BLOCK.get())?RailRegistry.TRAIN_STOP.get():event.getState().is(RailRegistry.CHAIN_BLOCK.get())?RailRegistry.RAIL_CHAIN_SIGNAL.get():RailRegistry.RAIL_SIGNAL.get();
                boolean removed=RailPlacement.destroyAttachment(level,event.getPos());
                if(removed&&!player.isCreative())player.getInventory().placeItemBackInInventory(new ItemStack(item));
            }
        });
    }
    private static void release(ServerLevel level) {
        Views view=VIEWS.remove(level);if(view==null)return;
        view.passengerTickets.forEach((id,chunk)->level.getChunkSource().removeRegionTicket(PASSENGER,chunk,3,id,true));
    }
    public static void tick(ServerLevel level) {
        RailwayState state=RailwaySavedData.get(level).state();
        for(int substep=0;substep<3;substep++) {
            passengerInputs(level,state);
            double brakingBonus=RailData.brakingBonus(dev.minetorio.content.research.ResearchSavedData.get(level.getServer()).state().researched());
            List<TrainMotion.Proposal> proposals=new ArrayList<>(state.dispatcher().beginTick(RailCircuitInputs.NONE,brakingBonus));
            for(Train train:state.trains().values().stream().sorted(Comparator.comparing(Train::id)).toList())
                if(train.mode()==Mode.MANUAL)TrainMotion.propose(state,train.id(),control(level,state,train),brakingBonus).ifPresent(proposals::add);
            RailWorldCollision.resolve(level,state,proposals);
            state.dispatcher().finishTick(RailCircuitInputs.NONE);
        }
        reconcileViews(level);updateTickets(level);
    }
    private static TrainMotion.Control control(ServerLevel level,RailwayState state,Train train) {
        for(UUID car:train.cars()) {
            RollingStockEntity view=views(level).cars.get(car);
            if(state.cars().get(car).kind()!=StockKind.LOCOMOTIVE||view==null||view.isRemoved())continue;
            for(Entity entity:view.getPassengers()) if(entity instanceof ServerPlayer player && player.serverLevel()==level && player.isAlive()&&!player.isSpectator()) {
                double throttle=Float.isFinite(player.zza)?Math.max(-1,Math.min(1,player.zza)):0;
                if(state.cars().get(car).reversed())throttle=-throttle;
                var turn=player.xxa>0.1?TrainMotion.Turn.LEFT:player.xxa<-.1?TrainMotion.Turn.RIGHT:TrainMotion.Turn.STRAIGHT;
                return new TrainMotion.Control(throttle,false,turn,TrainMotion.DriverContext.SEATED);
            }
        }
        return new TrainMotion.Control(0,true,TrainMotion.Turn.STRAIGHT,TrainMotion.DriverContext.ABSENT);
    }
    private static void passengerInputs(ServerLevel level,RailwayState state) {
        for(Train train:state.trains().values()) {
            Set<UUID> owners=new HashSet<>();
            for(UUID id:train.cars()) {
                RollingStockEntity view=views(level).cars.get(id);
                if(view!=null&&!view.isRemoved())for(Entity rider:view.getPassengers())if(rider instanceof ServerPlayer player&&player.serverLevel()==level&&player.isAlive())owners.add(player.getUUID());
            }
            state.dispatcher().passengers(train.id(),new TrainWait.PassengerState(!owners.isEmpty(),owners.isEmpty()?OptionalInt.of(0):dev.minetorio.content.logistics.PersonalRobotActivity.count(level.getServer(),owners)));
        }
    }
    private static void updateTickets(ServerLevel level) {
        Views view=views(level);Map<UUID,ChunkPos> next=new HashMap<>();
        for(var entry:view.cars.entrySet()) if(!entry.getValue().isRemoved()&&entry.getValue().getPassengers().stream().anyMatch(e->e instanceof ServerPlayer p&&p.serverLevel()==level&&p.isAlive()))
            next.put(entry.getKey(),entry.getValue().chunkPosition());
        next.forEach((id,pos)->{if(!pos.equals(view.passengerTickets.get(id)))level.getChunkSource().addRegionTicket(PASSENGER,pos,3,id,true);});
        view.passengerTickets.forEach((id,pos)->{if(!pos.equals(next.get(id)))level.getChunkSource().removeRegionTicket(PASSENGER,pos,3,id,true);});
        view.passengerTickets.clear();view.passengerTickets.putAll(next);
    }
    public static int passengerTicketCount(ServerLevel level) { return views(level).passengerTickets.size(); }
    public static void reconcileCell(ServerLevel level,BlockPos pos) {
        if(views(level).reconciling)return;
        Point cell=new Point(pos.getX(),pos.getY(),pos.getZ());
        List<Piece> pieces=RailwaySavedData.get(level).state().rails().stream().filter(p->cells(p).contains(cell)).toList();
        Views view=views(level);view.reconciling=true;
        try {
            if(pieces.isEmpty()) {if(level.getBlockState(pos).getBlock() instanceof RailCellBlock)level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());view.cells.remove(pos);}
            else if(level.getBlockEntity(pos) instanceof RailCellBlockEntity entity) {entity.project(pieces);view.cells.add(pos.immutable());}
        } finally {view.reconciling=false;}
    }
    public static void reconcileViews(ServerLevel level) {
        Views view=views(level);RailwayState state=RailwaySavedData.get(level).state();view.reconciling=true;
        try {
            Map<BlockPos,List<Piece>> wanted=new HashMap<>();
            for(Piece piece:state.rails())for(Point cell:cells(piece)) {
                BlockPos pos=RailPlacement.block(cell);
                if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null)wanted.computeIfAbsent(pos,k->new ArrayList<>()).add(piece);
            }
            for(BlockPos old:List.copyOf(view.cells))if(!wanted.containsKey(old)&&level.getChunkSource().getChunkNow(old.getX()>>4,old.getZ()>>4)!=null) {
                if(level.getBlockState(old).getBlock() instanceof RailCellBlock)level.setBlockAndUpdate(old,Blocks.AIR.defaultBlockState());view.cells.remove(old);
            }
            wanted.forEach((pos,pieces)->{
                if(!(level.getBlockState(pos).getBlock() instanceof RailCellBlock)&&level.getBlockState(pos).canBeReplaced())level.setBlockAndUpdate(pos,RailRegistry.CELL.get().defaultBlockState());
                if(level.getBlockEntity(pos) instanceof RailCellBlockEntity cell){cell.project(pieces);view.cells.add(pos);}
            });
            for(var stop:RailwaySavedData.get(level).worldStops().values())projectBlock(level,stop.position(),RailRegistry.STOP_BLOCK.get().defaultBlockState());
            var data=RailwaySavedData.get(level);data.rebindSignals();
            Set<UUID> connectedSignals=new HashSet<>();
            for(var signal:state.signalDefinitions()) {
                connectedSignals.add(signal.id());
                Block block=signal.kind()==RailSignals.Kind.CHAIN?RailRegistry.CHAIN_BLOCK.get():RailRegistry.SIGNAL_BLOCK.get();
                int aspect=Math.min(3,state.signals().aspect(signal.id(),state.reservations()).ordinal());
                projectBlock(level,RailPlacement.signalPosition(state,signal),block.defaultBlockState().setValue(RailSignalBlock.ASPECT,aspect));
            }
            for(var signal:data.worldSignals().values())if(!connectedSignals.contains(signal.id())) {
                Block block=signal.kind()==RailSignals.Kind.CHAIN?RailRegistry.CHAIN_BLOCK.get():RailRegistry.SIGNAL_BLOCK.get();
                // Собственная cadence: белый/off по10Minecraft ticks, без logical permission.
                int aspect=4+(int)((level.getGameTime()/10)%2);projectBlock(level,signal.position(),block.defaultBlockState().setValue(RailSignalBlock.ASPECT,aspect));
            }
            for(Entity entity:level.getAllEntities())if(entity instanceof RollingStockEntity stock)validateView(level,stock);
            Map<UUID,Pose> poses=new LinkedHashMap<>();for(Train train:state.trains().values())poses.putAll(TrainMotion.carPoses(state,train.id()));
            for(var entry:poses.entrySet()) {
                Pose pose=entry.getValue();BlockPos pos=BlockPos.containing(pose.position().x(),pose.position().y(),pose.position().z());
                RollingStockEntity entity=view.cars.get(entry.getKey());
                if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null) {
                    if(entity!=null&&!entity.isRemoved()&&entity.getPassengers().isEmpty())entity.discard();continue;
                }
                if(entity==null||entity.isRemoved()) {
                    entity=new RollingStockEntity(RailRegistry.STOCK.get(),level);entity.bind(entry.getKey());entity.project(state.cars().get(entry.getKey()),pose);
                    if(!level.addFreshEntity(entity))continue;view.cars.put(entry.getKey(),entity);
                }
                entity.project(state.cars().get(entry.getKey()),pose);
            }
            view.cars.entrySet().removeIf(e->e.getValue().isRemoved());
        } finally {view.reconciling=false;}
    }
    private static void projectBlock(ServerLevel level,BlockPos pos,BlockState state) {
        if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)return;
        BlockState old=level.getBlockState(pos);if(old.is(state.getBlock())||old.canBeReplaced())level.setBlockAndUpdate(pos,state);
    }
    public static void validateView(ServerLevel level,RollingStockEntity entity) {
        UUID id=entity.carId();Views view=views(level);
        if(id==null||!RailwaySavedData.get(level).state().cars().containsKey(id)) {entity.discard();return;}
        RollingStockEntity previous=view.cars.get(id);
        if(previous!=null&&previous!=entity&&!previous.isRemoved()){entity.discard();return;}
        view.cars.put(id,entity);
    }
    public static Optional<UUID> placeStock(ServerPlayer player,StockKind kind,Cursor cursor,boolean reversed) {
        var level=player.serverLevel();RailwayState state=RailwaySavedData.get(level).state();Item item=RailRegistry.stockItem(kind);
        ItemStack held=player.getMainHandItem().is(item)?player.getMainHandItem():player.getOffhandItem().is(item)?player.getOffhandItem():ItemStack.EMPTY;
        if(held.isEmpty()||!RecipeLocks.isUnlocked(player.server,RailRegistry.stockRecipe(kind)))return Optional.empty();
        double health=held.getOrDefault(RailRegistry.STOCK_HEALTH.get(),1.0);
        if(!Double.isFinite(health)||health<=0||health>1)return Optional.empty();
        Piece piece=state.graph().piece(cursor.traversal().piece()).orElse(null);
        if(piece==null||cursor.distance()>length(piece))return Optional.empty();
        List<Traversal> tape=new ArrayList<>(List.of(cursor.traversal()));double q=cursor.distance(),total=length(piece);
        while(q<3.5) {
            var first=tape.getFirst();var previous=state.graph().next(new Traversal(first.piece(),!first.forward()));
            if(previous.isEmpty())return Optional.empty();var chosen=previous.getFirst();var prepend=new Traversal(chosen.piece(),!chosen.forward());
            double added=length(state.graph().piece(prepend.piece()).orElseThrow());tape.addFirst(prepend);q+=added;total+=added;
        }
        while(total-q<3.5) {
            var next=state.graph().next(tape.getLast());if(next.isEmpty())return Optional.empty();Traversal chosen=next.getFirst();tape.add(chosen);total+=length(state.graph().piece(chosen.piece()).orElseThrow());
        }
        Pose pose=sample(piece,cursor.traversal().forward()?cursor.distance():length(piece)-cursor.distance());BlockPos pos=BlockPos.containing(pose.position().x(),pose.position().y(),pose.position().z());
        if(!RailPlacement.allowed(player,pos)||!RailPlacement.reachable(player,pos))return Optional.empty();
        UUID carId=UUID.randomUUID();
        int index=0;double at=q;while(index<tape.size()-1&&at>=length(state.graph().piece(tape.get(index).piece()).orElseThrow()))at-=length(state.graph().piece(tape.get(index++).piece()).orElseThrow());
        var candidate=new Train(UUID.randomUUID(),List.of(carId),Optional.of(new Cursor(tape.get(index),at)),index,tape,0,Mode.MANUAL,Status.MANUAL_CONTROL_STOP,0,0,0);
        var candidateBody=RailSweep.stationary(state,candidate);
        for(Train other:state.trains().values())if(TrainMotion.carPoses(state,other.id()).size()==other.cars().size()
                &&RailSweep.placementOverlap(candidateBody,RailSweep.stationary(state,other)))return Optional.empty();
        if(!placementClear(level,state,candidate))return Optional.empty();
        var car=state.createCar(carId,kind,reversed);car.setHp(health*RailData.stock(kind).health());
        var train=state.createTrain(candidate);
        if(!player.isCreative())held.shrink(1);
        for(UUID other:List.copyOf(state.cars().keySet()))if(!other.equals(carId))for(var end:TrainCoupling.End.values())for(var opposite:TrainCoupling.End.values())TrainCoupling.couple(state,other,opposite,carId,end);
        reconcileViews(level);return Optional.of(carId);
    }
    private static boolean placementClear(ServerLevel level,RailwayState state,Train candidate) {
        var body=RailSweep.stationary(state,candidate);var bounds=RailSweep.bounds(body);
        for(int x=(int)Math.floor(bounds.x0());x<=Math.floor(bounds.x1());x++)for(int z=(int)Math.floor(bounds.z0());z<=Math.floor(bounds.z1());z++) {
            var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);if(chunk==null)return false;
            for(int y=(int)Math.floor(bounds.y0());y<=Math.floor(bounds.y1());y++) {
                BlockPos pos=new BlockPos(x,y,z);var block=chunk.getBlockState(pos);if(block.getBlock() instanceof RailCellBlock)continue;
                for(var local:block.getCollisionShape(level,pos).toAabbs()) {
                    var shape=local.move(pos);
                    if(RailSweep.box(body,new RailSweep.Box(shape.minX,shape.minY,shape.minZ,shape.maxX,shape.maxY,shape.maxZ)).isPresent())return false;
                }
            }
        }
        return true;
    }
    public static boolean removeStock(ServerPlayer player,UUID id) {
        var level=player.serverLevel();var state=RailwaySavedData.get(level).state();var car=state.cars().get(id);if(car==null)return false;
        var pose=state.lastPoses().get(id);if(pose==null)return false;
        BlockPos at=BlockPos.containing(pose.position().x(),pose.position().y(),pose.position().z());
        if(!RailPlacement.allowed(player,at)||!RailPlacement.reachable(player,at))return false;
        if(state.removeCar(id,false).isEmpty())return false;
        returnSlots(player,car.cargo());returnSlots(player,car.fuel());
        ItemStack result=new ItemStack(RailRegistry.stockItem(car.kind()));double fraction=car.hp()/RailData.stock(car.kind()).health();
        if(fraction<1&&fraction>0)result.set(RailRegistry.STOCK_HEALTH.get(),fraction);
        player.getInventory().placeItemBackInInventory(result);reconcileViews(level);updateTickets(level);return true;
    }
    private static void returnSlots(ServerPlayer player,Optional<RailSlots> slots) {
        slots.ifPresent(s->{for(var slot:s.snapshot())if(slot.count()>0) {ItemStack stack=LaneStacks.stack(slot.key(),player.registryAccess());stack.setCount(slot.count());player.getInventory().placeItemBackInInventory(stack);}});
    }
    public static boolean damageStock(ServerLevel level,UUID id,double damage) {
        var state=RailwaySavedData.get(level).state();var car=state.cars().get(id);if(car==null||!Double.isFinite(damage)||damage<=0)return false;
        car.setHp(Math.max(0,car.hp()-damage));if(car.hp()==0)state.removeCar(id,true);return true;
    }
    public static boolean interact(ServerPlayer player,RollingStockEntity view,InteractionHand hand) {
        var level=player.serverLevel();var state=RailwaySavedData.get(level).state();var car=state.cars().get(view.carId());
        if(view.level()!=level||car==null||view.isRemoved()||views(level).cars.get(car.id())!=view||!player.canInteractWithEntity(view,2)||player.isSpectator())return false;
        if(player.isShiftKeyDown()&&player.getItemInHand(hand).isEmpty())return removeStock(player,car.id());
        ItemStack held=player.getItemInHand(hand);
        if(car.fuel().isPresent()&&!held.isEmpty()) {
            var key=LaneStacks.toLane(held,player.registryAccess());if(key.isPresent())for(int i=0;i<car.fuel().get().snapshot().size();i++) {
                int accepted=car.fuel().get().insertManual(i,key.get(),held.getCount(),false);if(accepted>0){held.shrink(accepted);return true;}
            }
        }
        if(!held.isEmpty())return false;
        boolean boarded=player.startRiding(view);if(boarded){passengerInputs(level,state);updateTickets(level);}return boarded;
    }
}
