package dev.minetorio.content.rail;

import dev.minetorio.content.fluid.*;
import dev.minetorio.core.fluid.FluidData;
import dev.minetorio.core.fluid.PortKind;
import dev.minetorio.core.rail.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import static dev.minetorio.core.rail.RailGeometry.*;

/** Производные подключения помп; груз остается только в существующих Car и PumpBlockEntity. */
public final class RailPumpDock {
    public static final long WARMUP_RAIL_TICKS=64;
    private RailPumpDock() {}
    public record Dock(UUID train,UUID car,BlockPos controller,int port,int side,int connector,int anchor) {}
    public record Connection(Dock dock,long firstPoweredRailClock,OptionalLong firstTransferRailClock) {}
    private record Candidate(Dock dock,RailwayState owner,RailwayState.Car car,PumpBlockEntity pump,
                             long epoch,long consist,FluidPorts.Port port) {}
    private static final class Connections {
        long tick=Long.MIN_VALUE;
        List<Candidate> selected=List.of();
        Map<Candidate,Long> starts=Map.of();
        Map<Candidate,Long> firstTransfers=Map.of();
    }
    // Удаляется вместе с FluidNetworkManager при выгрузке измерения; NBT не имеет этих данных.
    private static final Map<ServerLevel,Connections> CONNECTIONS=new HashMap<>();
    public static void forget(ServerLevel level){CONNECTIONS.remove(level);}
    private static boolean stopped(RailwayState.Train train) {
        return Math.abs(train.speed())<=1e-6&&(train.mode()==RailwayState.Mode.MANUAL
                ||train.mode()==RailwayState.Mode.AUTOMATIC&&train.status()==RailwayState.Status.WAIT_STATION);
    }
    private static RailDir direction(FluidPorts.Port port) {return RailDir.valueOf(port.side().name().substring(0,1));}
    private static Point cell(FluidPorts.Port port) {var p=port.cell();return new Point(p.getX(),p.getY(),p.getZ());}
    private static List<Candidate> candidates(ServerLevel level) {
        var state=RailwaySavedData.get(level).state();List<Candidate> result=new ArrayList<>();
        for(var pump:FluidNetworkManager.get(level).loadedPumps()) {
            if(!Double.isFinite(pump.satisfaction())||pump.satisfaction()<=0)continue;
            for(var port:FluidPorts.ports(level,pump.getBlockPos())) {
                if(!port.side().getAxis().isHorizontal()||FluidConnections.endAt(level,port.neighbor(),port.side().getOpposite())!=null)continue;
                for(var train:state.trains().values()) {
                    if(!stopped(train))continue;
                    for(UUID id:train.cars()) {
                        var car=state.cars().get(id);if(car==null||car.fluid().isEmpty())continue;
                        for(int connector=-1;connector<=1;connector++) {
                            var frame=RailDockGeometry.connectorFrame(state,train.id(),id,connector);if(frame.isEmpty())continue;
                            for(int side:new int[]{-1,1}) {
                                var anchor=RailDockGeometry.anchor(frame.get(),side,0,cell(port),direction(port));
                                if(anchor.isPresent())result.add(new Candidate(new Dock(train.id(),id,pump.getBlockPos(),port.index(),side,connector,anchor.getAsInt()),
                                        state,car,pump,pump.railConnectionEpoch(),train.consistRevision(),port));
                            }
                        }
                    }
                }
            }
        }
        result.sort(Comparator.comparing((Candidate c)->c.dock().controller()).thenComparingInt(c->c.dock().port())
                .thenComparing(c->c.dock().car()).thenComparingInt(c->c.dock().side()).thenComparingInt(c->c.dock().connector()));
        return result;
    }
    private static boolean add(List<Candidate> selected,Candidate candidate) {
        Dock d=candidate.dock();int count=0;
        for(Candidate other:selected) {
            Dock o=other.dock();
            if(o.controller().equals(d.controller())&&o.port()==d.port())return false;
            if(o.car().equals(d.car())) {
                if(o.side()==d.side()&&o.connector()==d.connector())return false;
                if(++count>=3)return false;
            }
        }
        selected.add(candidate);return true;
    }
    private static List<Candidate> computeSelection(ServerLevel level,List<Candidate> previous) {
        List<Candidate> candidates=candidates(level),selected=new ArrayList<>();
        // Сохраняем валидные подключения; восстановленное питание не вытесняет уже занятые места.
        for(Candidate old:previous)if(candidates.contains(old))add(selected,old);
        for(Candidate candidate:candidates)add(selected,candidate);
        return List.copyOf(selected);
    }
    /** Чистый preview: вычисляет тот же выбор, не меняя runtime claims, содержимое и таймеры. */
    public static List<Dock> selection(ServerLevel level) {
        Connections connections=CONNECTIONS.get(level);
        return computeSelection(level,connections==null?List.of():connections.selected).stream().map(Candidate::dock).toList();
    }
    /** Чистый снимок уже подтвержденных runtime подключений для диагностики и preview. */
    public static List<Connection> committedConnections(ServerLevel level) {
        Connections connections=CONNECTIONS.get(level);
        return connections==null?List.of():connections.selected.stream().map(c->new Connection(c.dock(),connections.starts.get(c),
                connections.firstTransfers.containsKey(c)?OptionalLong.of(connections.firstTransfers.get(c)):OptionalLong.empty())).toList();
    }
    public static void beginTick(ServerLevel level) {
        Connections connections=CONNECTIONS.computeIfAbsent(level,key->new Connections());
        long tick=level.getGameTime();if(connections.tick==tick)return;
        var selected=computeSelection(level,connections.selected);Map<Candidate,Long> starts=new HashMap<>();
        long clock=RailwaySavedData.get(level).state().railClock();
        for(Candidate candidate:selected)starts.put(candidate,connections.starts.getOrDefault(candidate,clock));
        Map<Candidate,Long> transfers=new HashMap<>();
        for(Candidate candidate:selected)if(connections.firstTransfers.containsKey(candidate))transfers.put(candidate,connections.firstTransfers.get(candidate));
        connections.selected=selected;connections.starts=starts;connections.firstTransfers=transfers;connections.tick=tick;
    }
    private static boolean current(ServerLevel level,Candidate candidate) {
        if(RailwaySavedData.get(level).state()!=candidate.owner()||candidate.owner().cars().get(candidate.dock().car())!=candidate.car()
                ||candidate.pump().isRemoved()||!level.isLoaded(candidate.dock().controller())
                ||candidate.pump().railConnectionEpoch()!=candidate.epoch()
                ||level.getBlockEntity(candidate.dock().controller())!=candidate.pump()
                ||!Double.isFinite(candidate.pump().satisfaction())||candidate.pump().satisfaction()<=0
                ||!FluidNetworkManager.get(level).loadedPumps().contains(candidate.pump()))return false;
        var train=candidate.owner().trains().get(candidate.dock().train());
        if(train==null||!stopped(train)||train.consistRevision()!=candidate.consist())return false;
        var frame=RailDockGeometry.connectorFrame(candidate.owner(),train.id(),candidate.car().id(),candidate.dock().connector());
        if(frame.isEmpty())return false;
        var port=FluidPorts.ports(level,candidate.dock().controller()).stream().filter(p->p.index()==candidate.dock().port()).findFirst();
        return port.isPresent()&&port.get().equals(candidate.port())
                &&FluidConnections.endAt(level,port.get().neighbor(),port.get().side().getOpposite())==null
                &&RailDockGeometry.anchor(frame.get(),candidate.dock().side(),0,cell(port.get()),direction(port.get())).equals(OptionalInt.of(candidate.dock().anchor()));
    }
    /** Проверки выполняются до extract: неизвестный id и неконечный объем не могут потеряться. */
    public static double transferAmount(String sourceFluid,double sourceAmount,String targetFluid,double targetAmount,double capacity,double rate) {
        if(sourceFluid==null||sourceFluid.isEmpty()||!FluidData.isFluid(sourceFluid)
                ||!Double.isFinite(sourceAmount)||sourceAmount<=0||!Double.isFinite(targetAmount)||targetAmount<0
                ||!Double.isFinite(capacity)||capacity<targetAmount||!Double.isFinite(rate)||rate<=0
                ||targetFluid!=null&&(!FluidData.isFluid(targetFluid)||!sourceFluid.equals(targetFluid))
                ||targetAmount>0&&targetFluid==null)return 0;
        return Math.max(0,Math.min(rate,Math.min(sourceAmount,capacity-targetAmount)));
    }
    public static double transfer(ServerLevel level,PumpBlockEntity pump) {
        beginTick(level);double transferred=0;long tick=level.getGameTime();Connections connections=CONNECTIONS.get(level);
        for(Candidate candidate:connections.selected) {
            if(candidate.pump()!=pump)continue;
            if(!current(level,candidate)) {
                // Наблюденная runtime потеря окончательна даже при восстановлении в этом же тике.
                connections.selected=connections.selected.stream().filter(c->c!=candidate).toList();connections.starts.remove(candidate);connections.firstTransfers.remove(candidate);continue;
            }
            if(candidate.owner().railClock()-connections.starts.get(candidate)<WARMUP_RAIL_TICKS)continue;
            var wagon=candidate.car().fluid().orElseThrow();var box=pump.box();double rate=pump.railBudget(tick);
            double amount;
            if(candidate.port().spec().kind()==PortKind.OUTPUT) {
                amount=transferAmount(box.fluid(),box.amount(),wagon.id(),wagon.amount(),wagon.capacity(),rate);
                if(amount<=0)continue;
                double accepted=candidate.car().insertFluid(box.fluid(),amount);if(accepted<=0)continue;
                box.extract(accepted);amount=accepted;
            } else if(candidate.port().spec().kind()==PortKind.INPUT) {
                amount=transferAmount(wagon.id(),wagon.amount(),box.fluid(),box.amount(),box.capacity(),rate);
                if(amount<=0)continue;
                double accepted=box.insert(wagon.id(),amount);if(accepted<=0)continue;
                candidate.car().extractFluid(accepted);amount=accepted;
            } else continue;
            pump.recordRailTransfer(tick,amount);pump.fluidChanged();transferred+=amount;
            connections.firstTransfers.putIfAbsent(candidate,candidate.owner().railClock());
        }
        return transferred;
    }
}
