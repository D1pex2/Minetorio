package dev.minetorio.content.rail;

import dev.minetorio.core.data.RailData.StockKind;
import dev.minetorio.core.rail.RailSlots;
import dev.minetorio.core.rail.RailSignals;
import dev.minetorio.core.rail.TrainReservations;
import dev.minetorio.core.rail.RailwayState;
import dev.minetorio.core.rail.RailwayState.*;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import static dev.minetorio.core.rail.RailGeometry.*;

/** Строгая схема: груз никогда не обрезается и не пропускается при ошибке. */
public final class RailwayNbt {
    public static final int SCHEMA_VERSION = 6;
    private RailwayNbt() {}

    public static CompoundTag write(RailwayState state, HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", SCHEMA_VERSION);
        root.put("worldStops",new ListTag());
        root.put("worldSignals",new ListTag());
        ListTag rails = new ListTag();
        for (Piece piece : state.rails()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", piece.id());
            tag.putInt("x", piece.origin().x());
            tag.putInt("y", piece.origin().y());
            tag.putInt("z", piece.origin().z());
            tag.putString("shape", piece.shape().name());
            tag.putInt("rotation", piece.quarterTurns());
            tag.putBoolean("mirrored", piece.mirrored());
            rails.add(tag);
        }
        root.put("rails", rails);
        ListTag cars = new ListTag();
        for (Car car : state.cars().values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", car.id());
            tag.putString("kind", car.kind().name());
            tag.putBoolean("reversed", car.reversed());
            tag.putDouble("hp", car.hp());
            car.cargo().ifPresent(slots -> tag.put("cargo", writeSlots(slots)));
            car.fuel().ifPresent(slots -> {
                tag.put("fuel", writeSlots(slots));
                tag.putString("activeFuel", car.activeFuel());
                tag.putString("remainingJ", car.remainingJ().toPlainString());
            });
            car.fluid().ifPresent(fluid -> {
                CompoundTag box = new CompoundTag();
                box.putString("id", fluid.id() == null ? "" : fluid.id());
                box.putDouble("amount", fluid.amount());
                tag.put("fluid", box);
            });
            cars.add(tag);
        }
        root.put("cars", cars);
        ListTag poses=new ListTag();
        for(var entry:state.lastPoses().entrySet()) {
            CompoundTag p=new CompoundTag();p.putUUID("car",entry.getKey());Pose pose=entry.getValue();
            p.putDouble("x",pose.position().x());p.putDouble("y",pose.position().y());p.putDouble("z",pose.position().z());
            p.putDouble("tx",pose.tangent().x());p.putDouble("tz",pose.tangent().z());poses.add(p);
        }
        root.put("lastPoses",poses);
        ListTag trains = new ListTag();
        for (Train train : state.trains().values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", train.id());
            ListTag members = new ListTag();
            for (UUID id : train.cars()) { CompoundTag member = new CompoundTag(); member.putUUID("id", id); members.add(member); }
            tag.put("cars", members);
            CompoundTag head = new CompoundTag();
            train.head().ifPresent(cursor -> {
                head.merge(writeTraversal(cursor.traversal()));
                head.putDouble("distance", cursor.distance());
            });
            tag.put("head", head);
            ListTag tape = new ListTag();
            train.tape().forEach(step -> tape.add(writeTraversal(step)));
            tag.put("tape", tape);
            tag.putDouble("speed", train.speed());
            tag.putInt("headIndex", train.headIndex());
            tag.putLong("routeRevision", train.routeRevision());
            tag.putLong("consistRevision", train.consistRevision());
            tag.putLong("motionRevision", train.motionRevision());
            tag.putString("mode", train.mode().name());
            tag.putString("status", train.status().name());
            RailwayScheduleNbt.writeTrain(tag, state.dispatcher().runtime(train.id()));
            trains.add(tag);
        }
        root.put("trains", trains);
        RailwayScheduleNbt.writeStations(root, state);
        ListTag signals=new ListTag();
        for(var signal:state.signalDefinitions()) {
            CompoundTag item=new CompoundTag();
            item.putUUID("id",signal.id()); item.putUUID("piece",signal.boundary().piece());
            item.putString("end",signal.boundary().end().name()); item.putBoolean("intoPiece",signal.intoPiece());
            item.putString("kind",signal.kind().name()); signals.add(item);
        }
        root.put("signals",signals);
        root.putLong("networkRevision",state.signals().revision());
        ListTag corridors=new ListTag();
        for(var corridor:TrainReservations.brakingCorridors(state)) {
            CompoundTag item=new CompoundTag();item.putUUID("train",corridor.trainId());
            item.putInt("travelSign",corridor.travelSign());item.putDouble("brakingBonus",corridor.brakingBonus());
            item.putDouble("distance",corridor.distance());ListTag route=new ListTag();
            corridor.route().forEach(step->route.add(writeTraversal(step)));item.put("route",route);corridors.add(item);
        }
        root.put("brakingCorridors",corridors);
        // В том числе запрещает записывать ключи, потерявшие регистрацию предмета/компонента.
        read(root, registries);
        return root;
    }

    private static CompoundTag writeSlots(RailSlots slots) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("bar", slots.bar());
        ListTag list = new ListTag();
        for (RailSlots.Slot slot : slots.snapshot()) {
            CompoundTag item = new CompoundTag();
            item.putString("key", slot.key());
            item.putInt("count", slot.count());
            slot.filter().ifPresent(filter -> item.putString("filter", filter));
            list.add(item);
        }
        tag.put("slots", list);
        return tag;
    }

    private static CompoundTag writeTraversal(Traversal step) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("piece", step.piece());
        tag.putBoolean("forward", step.forward());
        return tag;
    }

    public static RailwayState read(CompoundTag tag, HolderLookup.Provider registries) {
        return read(tag, registries, () -> {});
    }

    static RailwayState read(CompoundTag tag, HolderLookup.Provider registries, Runnable changed) {
        require(tag, "schemaVersion", Tag.TAG_INT, "root");
        int schema = tag.getInt("schemaVersion");
        if (schema < 1 || schema > SCHEMA_VERSION) throw invalid("root.schemaVersion", "Неподдерживаемая версия " + tag.getInt("schemaVersion"));
        if (schema < 4) emptyList(tag, "stops", "root");
        if(schema<3) emptyList(tag, "signals", "root");
        RailwayState state = new RailwayState(key -> RailItemKeys.limit(key, registries), changed);
        List<Piece> pieces = new ArrayList<>();
        ListTag rails = list(tag, "rails", "root");
        for (int i = 0; i < rails.size(); i++) {
            String context = "rails[" + i + "]";
            CompoundTag rail = rails.getCompound(i);
            try {
                int rotation = integer(rail, "rotation", context);
                if (rotation < 0 || rotation > 3) throw invalid(context, "Некорректный поворот");
                pieces.add(new Piece(uuid(rail, "id", context), new Point(integer(rail, "x", context), integer(rail, "y", context), integer(rail, "z", context)),
                        Shape.valueOf(string(rail, "shape", context)), rotation, bool(rail, "mirrored", context)));
            } catch (RuntimeException error) { throw invalid(context, error); }
        }
        try { state.replaceRails(pieces); } catch (RuntimeException error) { throw invalid("rails", error); }
        ListTag cars = list(tag, "cars", "root");
        for (int i = 0; i < cars.size(); i++) {
            String context = "cars[" + i + "]";
            CompoundTag carTag = cars.getCompound(i);
            try {
                UUID id = uuid(carTag, "id", context);
                context += "(" + id + ")";
                StockKind kind = StockKind.valueOf(string(carTag, "kind", context));
                Car car = state.createCar(id, kind, bool(carTag, "reversed", context));
                car.setHp(number(carTag, "hp", context));
                if (kind == StockKind.CARGO) {
                    forbidden(carTag, context, "fuel", "activeFuel", "remainingJ", "fluid");
                    readSlots(compound(carTag, "cargo", context), car.cargo().orElseThrow(), registries, context + ".cargo");
                } else if (kind == StockKind.LOCOMOTIVE) {
                    forbidden(carTag, context, "cargo", "fluid");
                    readSlots(compound(carTag, "fuel", context), car.fuel().orElseThrow(), registries, context + ".fuel");
                    car.setActiveFuel(string(carTag, "activeFuel", context), new BigDecimal(string(carTag, "remainingJ", context)));
                } else {
                    forbidden(carTag, context, "cargo", "fuel", "activeFuel", "remainingJ");
                    CompoundTag box = compound(carTag, "fluid", context);
                    String fluid = string(box, "id", context + ".fluid");
                    double amount = number(box, "amount", context + ".fluid");
                    if (amount < 0 || amount == 0 && !fluid.isEmpty() || amount > 0 && car.insertFluid(fluid, amount) != amount)
                        throw invalid(context + ".fluid", "Недопустимая жидкость или объем");
                }
            } catch (RuntimeException error) { throw invalid(context, error); }
        }
        ListTag trains = list(tag, "trains", "root");
        for (int i = 0; i < trains.size(); i++) {
            String context = "trains[" + i + "]";
            CompoundTag train = trains.getCompound(i);
            try {
                UUID id = uuid(train, "id", context);
                context += "(" + id + ")";
                List<UUID> members = new ArrayList<>();
                for (Tag member : list(train, "cars", context)) members.add(uuid((CompoundTag) member, "id", context));
                Mode mode = Mode.valueOf(string(train, "mode", context));
                Status status = Status.valueOf(string(train, "status", context));
                double speed = number(train, "speed", context);
                if (schema == 1 && speed < 0) throw invalid(context, "Отрицательная скорость");
                if (schema < 4) {
                    emptyList(train, "schedule", context);
                    if (!compound(train, "runtime", context).isEmpty()) throw invalid(context + ".runtime", "Неподдерживаемые данные runtime");
                }
                Optional<Cursor> head = Optional.empty();
                List<Traversal> tape = new ArrayList<>();
                long routeRevision = schema == 1 ? 0 : revision(train, "routeRevision", context);
                long consistRevision = schema == 1 ? 0 : revision(train, "consistRevision", context);
                long motionRevision = schema == 1 ? 0 : revision(train, "motionRevision", context);
                int headIndex = -1;
                try {
                    CompoundTag cursor = compound(train, "head", context);
                    if (!cursor.isEmpty()) head = Optional.of(new Cursor(readTraversal(cursor, context + ".head"), number(cursor, "distance", context)));
                    for (Tag step : list(train, "tape", context)) tape.add(readTraversal((CompoundTag) step, context + ".tape"));
                    if (schema >= 2) headIndex = integer(train, "headIndex", context);
                    else if (head.isPresent()) {
                        for (int j=0;j<tape.size();j++) if (tape.get(j).equals(head.get().traversal())) {
                            if (headIndex != -1) throw invalid(context, "Неоднозначный проход головы");
                            headIndex = j;
                        }
                    }
                } catch (RuntimeException invalidRoute) {
                    head = Optional.empty();
                    headIndex = -1;
                    tape.clear();
                    speed = 0;
                    status = Status.PATH_LOST;
                }
                state.createTrain(new Train(id, members, head, headIndex, tape, speed, mode, status, routeRevision, consistRevision, motionRevision));
            } catch (RuntimeException error) { throw invalid(context, error); }
        }
        if(schema>=3) {
            List<RailSignals.Signal> definitions=new ArrayList<>();
            ListTag signals=list(tag,"signals","root");
            for(int i=0;i<signals.size();i++) {
                String context="signals["+i+"]"; CompoundTag signal=signals.getCompound(i);
                try {
                    definitions.add(new RailSignals.Signal(uuid(signal,"id",context),
                            new Port(uuid(signal,"piece",context),End.valueOf(string(signal,"end",context))),
                            bool(signal,"intoPiece",context),RailSignals.Kind.valueOf(string(signal,"kind",context))));
                } catch(RuntimeException error) { throw invalid(context,error); }
            }
            try { state.replaceSignals(definitions); } catch(RuntimeException error) { throw invalid("signals",error); }
            state.restoreNetworkRevision(revision(tag,"networkRevision","root"));
            List<TrainReservations.BrakingCorridor> corridors=new ArrayList<>();
            ListTag savedCorridors=list(tag,"brakingCorridors","root");
            for(int i=0;i<savedCorridors.size();i++) {
                String context="brakingCorridors["+i+"]";CompoundTag item=savedCorridors.getCompound(i);
                try {
                    List<Traversal> route=new ArrayList<>();
                    for(Tag step:list(item,"route",context)) route.add(readTraversal((CompoundTag)step,context+".route"));
                    corridors.add(new TrainReservations.BrakingCorridor(uuid(item,"train",context),integer(item,"travelSign",context),
                            number(item,"brakingBonus",context),number(item,"distance",context),route));
                } catch(RuntimeException error) { throw invalid(context,error); }
            }
            try { TrainReservations.restoreBrakingCorridors(state,corridors); } catch(RuntimeException error) { throw invalid("brakingCorridors",error); }
        }
        if (schema >= 4) RailwayScheduleNbt.read(tag, state);
        if(schema>=5) {
            Map<UUID,Pose> poses=new LinkedHashMap<>();
            for(Tag raw:list(tag,"lastPoses","root")) {
                CompoundTag p=(CompoundTag)raw; UUID id=uuid(p,"car","lastPoses");
                Pose pose=new Pose(new Vec(number(p,"x","lastPoses"),number(p,"y","lastPoses"),number(p,"z","lastPoses")),
                        new Vec(number(p,"tx","lastPoses"),0,number(p,"tz","lastPoses")));
                if(poses.put(id,pose)!=null) throw invalid("lastPoses","Повторный вагон");
            }
            state.restoreLastPoses(poses);
        }
        return state;
    }

    private static void readSlots(CompoundTag tag, RailSlots slots, HolderLookup.Provider registries, String context) {
        ListTag list = list(tag, "slots", context);
        if (list.size() != slots.snapshot().size()) throw invalid(context, "Неверное число слотов " + list.size());
        for (int i = 0; i < list.size(); i++) {
            String at = context + ".slots[" + i + "]";
            CompoundTag item = list.getCompound(i);
            String key = string(item, "key", at);
            int count = integer(item, "count", at);
            if (count < 0 || count == 0 && !key.isEmpty() || count > 0 && (RailItemKeys.limit(key, registries) < count
                    || slots.insertManual(i, key, count, false) != count))
                throw invalid(at, "Недопустимый ключ или количество " + count);
            // Фильтр может быть изменен после вставки и не должен уничтожать уже лежащий груз.
            if (item.contains("filter")) slots.setFilter(i, Optional.of(string(item, "filter", at)));
        }
        slots.setBar(integer(tag, "bar", context));
    }
    private static Traversal readTraversal(CompoundTag tag, String context) {
        return new Traversal(uuid(tag, "piece", context), bool(tag, "forward", context));
    }
    private static void forbidden(CompoundTag tag, String context, String... keys) {
        for (String key : keys) if (tag.contains(key)) throw invalid(context + "." + key, "Груз не соответствует виду вагона");
    }
    private static void emptyList(CompoundTag tag, String key, String context) {
        if (!list(tag, key, context).isEmpty()) throw invalid(context + "." + key, "Данные еще не поддерживаются этой реализацией");
    }
    private static void require(CompoundTag tag, String key, int type, String context) {
        if (!tag.contains(key, type)) throw invalid(context + "." + key, "Поле отсутствует или имеет неверный тип");
    }
    private static ListTag list(CompoundTag tag, String key, String context) {
        require(tag, key, Tag.TAG_LIST, context);
        ListTag result = (ListTag) tag.get(key);
        if (!result.isEmpty() && result.getElementType() != Tag.TAG_COMPOUND) throw invalid(context + "." + key, "Неверный тип списка");
        return result;
    }
    private static CompoundTag compound(CompoundTag tag, String key, String context) {
        require(tag, key, Tag.TAG_COMPOUND, context); return tag.getCompound(key);
    }
    private static String string(CompoundTag tag, String key, String context) {
        require(tag, key, Tag.TAG_STRING, context); return tag.getString(key);
    }
    private static int integer(CompoundTag tag, String key, String context) {
        require(tag, key, Tag.TAG_INT, context); return tag.getInt(key);
    }
    private static long revision(CompoundTag tag, String key, String context) {
        require(tag, key, Tag.TAG_LONG, context);
        long value = tag.getLong(key);
        if (value < 0) throw invalid(context + "." + key, "Отрицательная ревизия");
        return value;
    }
    private static double number(CompoundTag tag, String key, String context) {
        require(tag, key, Tag.TAG_DOUBLE, context);
        double value = tag.getDouble(key);
        if (!Double.isFinite(value)) throw invalid(context + "." + key, "Число не является конечным");
        return value;
    }
    private static boolean bool(CompoundTag tag, String key, String context) {
        require(tag, key, Tag.TAG_BYTE, context);
        byte value = tag.getByte(key);
        if (value != 0 && value != 1) throw invalid(context + "." + key, "Недопустимый boolean");
        return value == 1;
    }
    private static UUID uuid(CompoundTag tag, String key, String context) {
        if (!tag.hasUUID(key)) throw invalid(context + "." + key, "Недопустимый UUID");
        return tag.getUUID(key);
    }
    private static IllegalArgumentException invalid(String context, String message) {
        return new IllegalArgumentException("Железная дорога, " + context + ": " + message);
    }
    private static IllegalArgumentException invalid(String context, RuntimeException cause) {
        return new IllegalArgumentException("Железная дорога, " + context + ": " + cause.getMessage(), cause);
    }
}
