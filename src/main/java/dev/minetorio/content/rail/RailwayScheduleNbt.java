package dev.minetorio.content.rail;

import java.util.*;
import net.minecraft.nbt.*;
import dev.minetorio.core.rail.RailwayState;
import dev.minetorio.core.rail.TrainDispatcher;
import dev.minetorio.core.rail.TrainDispatcher.*;
import dev.minetorio.core.rail.TrainSchedule;
import static dev.minetorio.core.rail.TrainSchedule.*;
import static dev.minetorio.core.rail.RailGeometry.*;

/** Typed codec диспетчера. Никаких сохраненных circuit/passenger samples. */
final class RailwayScheduleNbt {
    private RailwayScheduleNbt() {}
    static void writeTrain(CompoundTag tag, TrainDispatcher.Runtime runtime) {
        ListTag records=new ListTag();
        for(Stop stop:runtime.schedule().stops()) {
            CompoundTag record=new CompoundTag();stop.station().ifPresent(name->record.putString("station",name));
            stop.railTarget().ifPresent(target->{record.put("railTarget",cursor(target.point()));record.putBoolean("eitherDirection",target.eitherDirection());});
            record.putBoolean("temporary",stop.temporary());ListTag waits=new ListTag();
            for(Wait wait:stop.waits()) {
                CompoundTag item=new CompoundTag();item.putString("type",wait.type().name());item.putString("join",wait.join().name());item.putLong("ticks",wait.ticks());
                wait.condition().ifPresent(condition->{
                    CompoundTag value=new CompoundTag();condition.first().ifPresent(key->value.put("first",signal(key)));condition.second().ifPresent(key->value.put("second",signal(key)));
                    value.putString("comparator",condition.comparator().name());value.putInt("constant",condition.constant());item.put("condition",value);
                });waits.add(item);
            }
            record.put("waits",waits);records.add(record);
        }
        tag.put("schedule",records);tag.putInt("current",runtime.schedule().current());
        CompoundTag saved=new CompoundTag();saved.putLong("tripId",runtime.tripId());saved.putBoolean("waitComplete",runtime.waitComplete());
        runtime.stoppedStation().ifPresent(id->saved.putUUID("stoppedStation",id));
        runtime.arrivalTick().ifPresent(tick->saved.putLong("arrivalTick",tick));runtime.activityTick().ifPresent(tick->saved.putLong("activityTick",tick));
        runtime.hold().ifPresent(hold->{CompoundTag value=new CompoundTag();value.putUUID("train",hold.trainId());value.putLong("tripId",hold.tripId());value.putInt("scheduleIndex",hold.scheduleIndex());value.putInt("occurrence",hold.routeOccurrence());value.putUUID("station",hold.stationId());saved.put("hold",value);});
        runtime.assignment().ifPresent(a->{
            CompoundTag assignment=new CompoundTag();assignment.putInt("sign",a.sign());assignment.putLong("routeRevision",a.routeRevision());assignment.putLong("graphRevision",a.graphRevision());assignment.putLong("networkRevision",a.networkRevision());assignment.putInt("routeIndex",a.routeIndex());
            ListTag route=new ListTag();a.route().forEach(step->route.add(traversal(step)));assignment.put("route",route);
            ListTag visits=new ListTag();for(Visit visit:a.visits()) {
                CompoundTag v=new CompoundTag();visit.station().ifPresent(id->v.putUUID("station",id));v.put("point",cursor(visit.point()));v.putInt("scheduleIndex",visit.scheduleIndex());v.putLong("tripId",visit.tripId());v.putInt("occurrence",visit.occurrence());v.putDouble("remaining",visit.remaining());visits.add(v);
            }
            assignment.put("visits",visits);saved.put("assignment",assignment);
        });
        tag.put("runtime",saved);
    }
    static void writeStations(CompoundTag root,RailwayState state) {
        root.putLong("railClock",state.railClock());root.putLong("stationRevision",state.stationRevision());ListTag stops=new ListTag();
        for(var station:state.stations().values()) {
            CompoundTag item=new CompoundTag();item.putUUID("id",station.id());item.putString("name",station.name());item.put("stop",cursor(station.stop()));item.putBoolean("enabled",station.enabled());station.trainLimit().ifPresent(limit->item.putInt("limit",limit));stops.add(item);
        }
        root.put("stops",stops);
    }
    static void read(CompoundTag root,RailwayState state) {
        List<RailwayState.Station> stations=new ArrayList<>();
        for(Tag raw:list(root,"stops")) {
            CompoundTag s=(CompoundTag)raw;stations.add(new RailwayState.Station(uuid(s,"id"),string(s,"name"),readCursor(compound(s,"stop")),bool(s,"enabled"),s.contains("limit")?OptionalInt.of(integer(s,"limit")):OptionalInt.empty()));
        }
        Map<UUID,TrainDispatcher.Runtime> runtimes=new LinkedHashMap<>();
        for(Tag raw:list(root,"trains")) {
            CompoundTag t=(CompoundTag)raw;UUID id=uuid(t,"id");
            List<Stop> stops=new ArrayList<>();for(Tag item:list(t,"schedule")) {
                CompoundTag s=(CompoundTag)item;List<Wait> waits=new ArrayList<>();
                for(Tag waitRaw:list(s,"waits")) {
                    CompoundTag w=(CompoundTag)waitRaw;Optional<Condition> condition=Optional.empty();
                    if(w.contains("condition")) {
                        CompoundTag c=compound(w,"condition");condition=Optional.of(new Condition(c.contains("first")?Optional.of(readSignal(compound(c,"first"))):Optional.empty(),
                                TrainSchedule.Comparator.valueOf(string(c,"comparator")),c.contains("second")?Optional.of(readSignal(compound(c,"second"))):Optional.empty(),integer(c,"constant")));
                    }
                    waits.add(new Wait(Type.valueOf(string(w,"type")),Join.valueOf(string(w,"join")),nonnegative(w,"ticks"),condition));
                }
                stops.add(new Stop(s.contains("station")?Optional.of(string(s,"station")):Optional.empty(),s.contains("railTarget")?Optional.of(new RailTarget(readCursor(compound(s,"railTarget")),bool(s,"eitherDirection"))):Optional.empty(),bool(s,"temporary"),waits));
            }
            TrainSchedule schedule=new TrainSchedule(stops,integer(t,"current"));CompoundTag saved=compound(t,"runtime");Optional<Assignment> assignment=Optional.empty();
            if(saved.contains("assignment")) {
                CompoundTag a=compound(saved,"assignment");List<Traversal> route=new ArrayList<>();for(Tag step:list(a,"route")) route.add(readTraversal((CompoundTag)step));List<Visit> visits=new ArrayList<>();
                for(Tag visitRaw:list(a,"visits")) {CompoundTag v=(CompoundTag)visitRaw;visits.add(new Visit(optionalUuid(v,"station"),readCursor(compound(v,"point")),integer(v,"scheduleIndex"),nonnegative(v,"tripId"),integer(v,"occurrence"),number(v,"remaining")));}
                assignment=Optional.of(new Assignment(integer(a,"sign"),nonnegative(a,"routeRevision"),nonnegative(a,"graphRevision"),nonnegative(a,"networkRevision"),route,integer(a,"routeIndex"),visits));
            }
            Optional<Claim> hold=Optional.empty();if(saved.contains("hold")) {CompoundTag c=compound(saved,"hold");hold=Optional.of(new Claim(uuid(c,"train"),nonnegative(c,"tripId"),integer(c,"scheduleIndex"),integer(c,"occurrence"),uuid(c,"station")));}
            var runtime=new TrainDispatcher.Runtime(schedule,nonnegative(saved,"tripId"),assignment,optionalUuid(saved,"stoppedStation"),optionalTick(saved,"arrivalTick"),optionalTick(saved,"activityTick"),bool(saved,"waitComplete"),hold);
            if(runtimes.put(id,runtime)!=null) throw invalid("Повторный runtime");
        }
        state.dispatcher().restore(nonnegative(root,"railClock"),nonnegative(root,"stationRevision"),stations,runtimes);
    }
    private static CompoundTag traversal(Traversal step) {CompoundTag t=new CompoundTag();t.putUUID("piece",step.piece());t.putBoolean("forward",step.forward());return t;}
    private static CompoundTag cursor(Cursor cursor) {CompoundTag t=traversal(cursor.traversal());t.putDouble("distance",cursor.distance());return t;}
    private static CompoundTag signal(SignalKey signal) {CompoundTag t=new CompoundTag();t.putString("kind",signal.kind().name());t.putString("name",signal.name());return t;}
    private static Traversal readTraversal(CompoundTag t) {return new Traversal(uuid(t,"piece"),bool(t,"forward"));}
    private static Cursor readCursor(CompoundTag t) {return new Cursor(readTraversal(t),number(t,"distance"));}
    private static SignalKey readSignal(CompoundTag t) {return new SignalKey(SignalKind.valueOf(string(t,"kind")),string(t,"name"));}
    private static Optional<UUID> optionalUuid(CompoundTag t,String key) {return t.contains(key)?Optional.of(uuid(t,key)):Optional.empty();}
    private static OptionalLong optionalTick(CompoundTag t,String key) {return t.contains(key)?OptionalLong.of(nonnegative(t,key)):OptionalLong.empty();}
    private static void require(CompoundTag t,String key,int type) {if(!t.contains(key,type)) throw invalid("Неверный тип поля "+key);}
    private static CompoundTag compound(CompoundTag t,String key) {require(t,key,Tag.TAG_COMPOUND);return t.getCompound(key);}
    private static ListTag list(CompoundTag t,String key) {require(t,key,Tag.TAG_LIST);ListTag list=(ListTag)t.get(key);if(!list.isEmpty() && list.getElementType()!=Tag.TAG_COMPOUND) throw invalid("Неверный тип списка "+key);return list;}
    private static String string(CompoundTag t,String key) {require(t,key,Tag.TAG_STRING);return t.getString(key);}
    private static int integer(CompoundTag t,String key) {require(t,key,Tag.TAG_INT);return t.getInt(key);}
    private static long nonnegative(CompoundTag t,String key) {require(t,key,Tag.TAG_LONG);long value=t.getLong(key);if(value<0) throw invalid("Отрицательное поле "+key);return value;}
    private static double number(CompoundTag t,String key) {require(t,key,Tag.TAG_DOUBLE);double value=t.getDouble(key);if(!Double.isFinite(value)) throw invalid("Неверное число "+key);return value;}
    private static boolean bool(CompoundTag t,String key) {require(t,key,Tag.TAG_BYTE);byte value=t.getByte(key);if(value!=0 && value!=1) throw invalid("Неверный boolean "+key);return value==1;}
    private static UUID uuid(CompoundTag t,String key) {if(!t.hasUUID(key)) throw invalid("Неверный UUID "+key);return t.getUUID(key);}
    private static IllegalArgumentException invalid(String message) {return new IllegalArgumentException("Железная дорога, расписание: "+message);}
}
