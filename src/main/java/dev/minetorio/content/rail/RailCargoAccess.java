package dev.minetorio.content.rail;

import dev.minetorio.content.belt.LaneStacks;
import dev.minetorio.core.belt.LaneItem;
import dev.minetorio.core.data.RailData;
import dev.minetorio.core.rail.RailSlots;
import dev.minetorio.core.rail.RailwayState;
import dev.minetorio.core.rail.RailDockGeometry;
import dev.minetorio.core.rail.RailGeometry;
import dev.minetorio.core.rail.TrainMotion;
import dev.minetorio.core.inserter.PickupSource;
import dev.minetorio.core.inserter.DropTarget;
import java.util.*;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

/** Адаптеры обращаются к текущему владельцу по UUID, не сохраняют копии груза. */
public final class RailCargoAccess {
    private RailCargoAccess() {}
    private record Bucket(int x,int y,int z) {
        static Bucket at(double x,double y,double z){return new Bucket((int)Math.floor(x/8),(int)Math.floor(y/8),(int)Math.floor(z/8));}
    }
    private record Entry(RailwayState owner,RailwayState.Car car,UUID train,long consist,RailGeometry.Pose pose) {}
    private static final Map<ServerLevel,Index> INDICES=new WeakHashMap<>();
    private static final class Index {
        RailwayState owner;
        long graph;
        Map<UUID,RailwayState.Train> trains=Map.of();
        Map<UUID,RailwayState.Car> cars=Map.of();
        Map<UUID,RailGeometry.Pose> last=Map.of();
        final Map<Bucket,List<Entry>> buckets=new HashMap<>();
        void refresh(RailwayState state) {
            if(owner==state&&graph==state.graph().revision()&&trains.equals(state.trains())&&cars.equals(state.cars())&&last.equals(state.lastPoses()))return;
            owner=state;graph=state.graph().revision();trains=Map.copyOf(state.trains());cars=Map.copyOf(state.cars());last=Map.copyOf(state.lastPoses());buckets.clear();
            for(var train:trains.values())for(var pose:TrainMotion.carPoses(state,train.id()).entrySet()) {
                var car=cars.get(pose.getKey());if(car==null||car.kind()==RailData.StockKind.FLUID)continue;
                var entry=new Entry(state,car,train.id(),train.consistRevision(),pose.getValue());var p=pose.getValue().position();
                Bucket low=Bucket.at(p.x()-4,p.y(),p.z()-4),high=Bucket.at(p.x()+4,p.y()+2.65,p.z()+4);
                for(int x=low.x();x<=high.x();x++)for(int y=low.y();y<=high.y();y++)for(int z=low.z();z<=high.z();z++)
                    buckets.computeIfAbsent(new Bucket(x,y,z),key->new ArrayList<>()).add(entry);
            }
        }
    }
    private static boolean eligible(RailwayState.Train train) {
        return train!=null&&Math.abs(train.speed())<=1e-6
                &&(train.mode()==RailwayState.Mode.MANUAL||train.mode()==RailwayState.Mode.AUTOMATIC&&train.status()==RailwayState.Status.WAIT_STATION);
    }
    private static OptionalDouble contact(RailGeometry.Pose pose,BlockPos cell,Direction from) {
        return RailDockGeometry.itemContact(pose,new RailGeometry.Vec(cell.getX()+.5,cell.getY()+.5,cell.getZ()+.5),
                new RailGeometry.Vec(from.getStepX(),from.getStepY(),from.getStepZ()));
    }
    @Nullable private static Entry find(ServerLevel level,BlockPos cell,Direction from) {
        if(!level.isLoaded(cell)||from==null)return null;
        var state=RailwaySavedData.get(level).state();var index=INDICES.computeIfAbsent(level,key->new Index());index.refresh(state);
        Entry best=null;double nearest=Double.POSITIVE_INFINITY;
        for(var entry:index.buckets.getOrDefault(Bucket.at(cell.getX()+.5,cell.getY()+.5,cell.getZ()+.5),List.of())) {
            if(!eligible(state.trains().get(entry.train())))continue;
            OptionalDouble hit=contact(entry.pose(),cell,from);if(hit.isEmpty())continue;
            double distance=hit.getAsDouble();
            if(best==null||distance<nearest||distance==nearest&&(entry.train().compareTo(best.train())<0
                    ||entry.train().equals(best.train())&&entry.car().id().compareTo(best.car().id())<0)) {best=entry;nearest=distance;}
        }
        return best;
    }
    @Nullable private static RailSlots current(ServerLevel level,Entry captured,BlockPos cell,Direction from) {
        if(!level.isLoaded(cell))return null;
        var state=RailwaySavedData.get(level).state();if(state!=captured.owner()||state.cars().get(captured.car().id())!=captured.car())return null;
        var train=state.trains().get(captured.train());
        if(!eligible(train)||train.consistRevision()!=captured.consist()||!train.cars().contains(captured.car().id()))return null;
        var pose=TrainMotion.carPoses(state,train.id()).get(captured.car().id());
        if(!Objects.equals(pose,captured.pose())||contact(pose,cell,from).isEmpty())return null;
        return slots(level,captured.car().id());
    }
    @Nullable public static PickupSource source(Level level,BlockPos cell,Direction from) {
        if(!(level instanceof ServerLevel server))return null;
        Entry captured=find(server,cell,from);if(captured==null)return null;
        BlockPos at=cell.immutable();
        return new PickupSource() {
            @Override public Optional<String> peek(Predicate<String> wanted) {
                RailSlots slots=current(server,captured,at,from);if(slots==null)return Optional.empty();
                for(var slot:slots.snapshot())if(slot.count()>0&&wanted.test(slot.key()))
                    return current(server,captured,at,from)==slots?Optional.of(slot.key()):Optional.empty();
                return Optional.empty();
            }
            @Override public boolean take(String key) {
                RailSlots slots=current(server,captured,at,from);if(slots==null)return false;
                for(int i=0;i<slots.snapshot().size();i++)if(slots.get(i).key().equals(key)&&slots.extract(i,1,false)==1)return true;
                return false;
            }
        };
    }
    @Nullable public static DropTarget target(Level level,BlockPos cell,Direction from) {
        if(!(level instanceof ServerLevel server))return null;
        Entry captured=find(server,cell,from);if(captured==null)return null;
        BlockPos at=cell.immutable();
        return new DropTarget() {
            @Override public boolean wants(String key) {
                RailSlots slots=current(server,captured,at,from);if(slots==null||RailItemKeys.limit(key,server.registryAccess())<=0)return false;
                if(captured.car().kind()==RailData.StockKind.LOCOMOTIVE&&RailData.fuel(LaneItem.baseId(key)).isEmpty())return false;
                for(int i=0;i<slots.bar();i++)if(slots.get(i).filter().map(filter->filter.equals(LaneItem.baseId(key))).orElse(true))return true;
                return false;
            }
            @Override public boolean accept(String key) {
                RailSlots slots=current(server,captured,at,from);if(slots==null)return false;
                for(int i=0;i<slots.bar();i++)if(slots.insert(i,key,1,false)==1)return true;
                return false;
            }
        };
    }

    @Nullable
    private static RailSlots slots(ServerLevel level,UUID carId) {
        RailwayState.Car car=RailwaySavedData.get(level).state().cars().get(carId);
        return car==null?null:car.cargo().or(()->car.fuel()).orElse(null);
    }

    /** Прямой инвентарь для меню: физическая близость манипулятора здесь не требуется. */
    @Nullable
    public static IItemHandler itemHandler(ServerLevel level,UUID carId) {
        return slots(level,carId)==null?null:new IItemHandler() {
            @Override public int getSlots() {
                RailSlots current=slots(level,carId);
                return current==null?0:current.snapshot().size();
            }
            @Nullable private RailSlots current(int slot) {
                RailSlots current=slots(level,carId);
                if(current==null)return null;
                current.get(slot);
                return current;
            }
            @Override public ItemStack getStackInSlot(int slot) {
                RailSlots current=current(slot);
                if(current==null)return ItemStack.EMPTY;
                var stored=current.get(slot);
                if(stored.count()==0)return ItemStack.EMPTY;
                return LaneStacks.stack(stored.key(),level.registryAccess()).copyWithCount(stored.count());
            }
            @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) {
                RailSlots current=current(slot);
                if(current==null||stack.isEmpty())return stack;
                String key=LaneStacks.toLane(stack,level.registryAccess()).orElse(null);
                int accepted=key==null?0:current.insert(slot,key,stack.getCount(),simulate);
                return accepted==0?stack:stack.copyWithCount(stack.getCount()-accepted);
            }
            @Override public ItemStack extractItem(int slot,int amount,boolean simulate) {
                RailSlots current=current(slot);
                if(current==null||amount<=0)return ItemStack.EMPTY;
                var stored=current.get(slot);
                if(stored.count()==0)return ItemStack.EMPTY;
                int removed=current.extract(slot,amount,simulate);
                return LaneStacks.stack(stored.key(),level.registryAccess()).copyWithCount(removed);
            }
            @Override public int getSlotLimit(int slot) {
                RailSlots current=current(slot);
                if(current==null)return 0;
                var stored=current.get(slot);
                // Пустой слот не ограничивает предмет универсальным числом: предел зависит от components.
                return stored.count()==0?Integer.MAX_VALUE:RailItemKeys.limit(stored.key(),level.registryAccess());
            }
            @Override public boolean isItemValid(int slot,ItemStack stack) {
                RailSlots current=current(slot);
                if(current==null||slot>=current.bar())return false;
                String key=LaneStacks.toLane(stack,level.registryAccess()).orElse(null);
                if(key==null||RailItemKeys.limit(key,level.registryAccess())<=0)return false;
                var car=RailwaySavedData.get(level).state().cars().get(carId);
                return (car.kind()!=RailData.StockKind.LOCOMOTIVE||RailData.fuel(LaneItem.baseId(key)).isPresent())
                        &&current.get(slot).filter().map(filter->filter.equals(LaneItem.baseId(key))).orElse(true);
            }
        };
    }
}
