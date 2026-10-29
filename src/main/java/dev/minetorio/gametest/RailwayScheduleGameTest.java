package dev.minetorio.gametest;

import java.util.*;
import dev.minetorio.content.belt.LaneStacks;
import dev.minetorio.content.combat.CombatRegistry;
import dev.minetorio.content.rail.RailItemKeys;
import dev.minetorio.content.rail.RailwayNbt;
import dev.minetorio.core.data.RailData.StockKind;
import dev.minetorio.core.rail.*;
import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.RailwayState.*;
import static dev.minetorio.core.rail.TrainSchedule.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class RailwayScheduleGameTest {
    private static final UUID TRAIN=new UUID(12,1), LOCO=new UUID(12,2), CARGO=new UUID(12,3), STATION=new UUID(12,4);
    private static RailwayState waiting(GameTestHelper h) {
        var state=new RailwayState(key->RailItemKeys.limit(key,h.getLevel().registryAccess()));
        List<Piece> pieces=new ArrayList<>();List<Traversal> tape=new ArrayList<>();
        for(int i=0;i<80;i++) {var p=new Piece(new UUID(13,i),new Point(0,0,-2*i),Shape.STRAIGHT,0,false);pieces.add(p);tape.add(new Traversal(p.id(),true));}
        state.replaceRails(pieces);var loco=state.createCar(LOCO,StockKind.LOCOMOTIVE,false);state.createCar(CARGO,StockKind.CARGO,false);
        loco.fuel().orElseThrow().insert(0,"minetorio:coal",10,false);
        state.createTrain(new Train(TRAIN,List.of(LOCO,CARGO),Optional.of(new Cursor(tape.get(25),0)),25,tape,0,Mode.AUTOMATIC,Status.ON_THE_PATH,0,0,0));
        state.dispatcher().upsertStation(new Station(STATION,"A",new Cursor(tape.get(26),1.5),true,OptionalInt.of(1)));
        var wait=new Wait(Type.TIME,Join.AND,60,Optional.empty());
        state.dispatcher().setSchedule(TRAIN,new TrainSchedule(List.of(new Stop(Optional.of("A"),Optional.empty(),false,List.of(wait))),0));
        for(int i=0;i<60;i++) state.dispatcher().tick(RailCircuitInputs.NONE,0);
        return state;
    }
    @GameTest(template="empty16")
    public static void waitingClockAndAllTypedConditionsSurviveReload(GameTestHelper h) {
        var state=waiting(h);var d=state.dispatcher();h.assertTrue(d.context(TRAIN,RailCircuitInputs.NONE).elapsed()==59,"Исходные часы");
        var saved=RailwayNbt.write(state,h.getLevel().registryAccess());var restored=RailwayNbt.read(saved,h.getLevel().registryAccess());
        h.assertTrue(restored.dispatcher().runtime(TRAIN).schedule().equals(d.runtime(TRAIN).schedule()),"Расписание потеряно");
        h.assertTrue(restored.railClock()==60 && restored.dispatcher().context(TRAIN,RailCircuitInputs.NONE).elapsed()==59,"Часы сброшены на загрузке");
        h.assertTrue(restored.dispatcher().stationOutput(STATION).assignedCount()==1,"Hold не восстановлен");
        restored.dispatcher().tick(RailCircuitInputs.NONE,0);
        h.assertTrue(restored.dispatcher().context(TRAIN,RailCircuitInputs.NONE).elapsed()==60,"Повторный arrival сбросил часы");
        var condition=new Condition(Optional.of(new SignalKey(SignalKind.VIRTUAL,"signal-everything")),TrainSchedule.Comparator.GE,Optional.of(new SignalKey(SignalKind.ITEM,"minetorio:coal")),0);
        List<Stop> records=new ArrayList<>();for(Type type:Type.values()) records.add(new Stop(Optional.empty(),Optional.of(new RailTarget(new Cursor(state.trains().get(TRAIN).head().get().traversal(),0.25),true)),true,List.of(new Wait(type,Join.OR,123,Optional.of(condition)))));
        var schedule=new TrainSchedule(records,4);d.setSchedule(TRAIN,schedule);
        var second=RailwayNbt.read(RailwayNbt.write(state,h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(second.dispatcher().runtime(TRAIN).schedule().equals(schedule),"Typed условия/temporary/eitherDirection потеряны");
        h.assertTrue(second.dispatcher().context(TRAIN,RailCircuitInputs.NONE).passengers().isEmpty(),"Загрузка выдумала пассажиров");h.succeed();
    }
    @GameTest(template="empty16")
    public static void actualComponentLimitDrivesFullBeforeAndAfterNbt(GameTestHelper h) {
        var state=waiting(h);var slots=state.cars().get(CARGO).cargo().orElseThrow();slots.setBar(1);
        var pistol=new ItemStack(CombatRegistry.PISTOL.get());pistol.set(CombatRegistry.ROUNDS.get(),7);pistol.set(DataComponents.MAX_STACK_SIZE,1);
        String key=LaneStacks.toLane(pistol,h.getLevel().registryAccess()).orElseThrow();slots.insert(0,key,1,false);
        var full=new Wait(Type.FULL,Join.AND,0,Optional.empty());
        for(var current:List.of(state,RailwayNbt.read(RailwayNbt.write(state,h.getLevel().registryAccess()),h.getLevel().registryAccess()))) {
            var snapshot=current.cars().get(CARGO).cargo().orElseThrow();long clock=current.dispatcher().runtime(TRAIN).activityTick().orElseThrow();
            h.assertTrue(!snapshot.waitSnapshot().getFirst().canInsert(),"Полный ключ MAX_STACK_SIZE=1 допускает вставку");
            h.assertTrue(TrainWait.evaluate(List.of(full),current.dispatcher().context(TRAIN,RailCircuitInputs.NONE)),"FULL не видит реальный лимит");
            h.assertTrue(current.dispatcher().runtime(TRAIN).activityTick().orElseThrow()==clock,"Snapshot создал активность");
            snapshot.extract(0,1,false);snapshot.insert(0,"minetorio:pistol",1,false);
            h.assertTrue(snapshot.waitSnapshot().getFirst().canInsert(),"Обычный пистолет count1/limit5 ошибочно полный");
            h.assertTrue(!TrainWait.evaluate(List.of(full),current.dispatcher().context(TRAIN,RailCircuitInputs.NONE)),"Обычный пистолет ошибочно FULL");
        }
        h.succeed();
    }
    @GameTest(template="empty16")
    public static void invalidTypedScheduleAndTicksAreRejected(GameTestHelper h) {
        var state=waiting(h);var saved=RailwayNbt.write(state,h.getLevel().registryAccess());
        for(int kind=0;kind<3;kind++) {
            var broken=saved.copy();var train=broken.getList("trains",Tag.TAG_COMPOUND).getCompound(0);
            if(kind==0) train.getCompound("runtime").putLong("arrivalTick",-1);
            if(kind==1) train.putInt("current",10);
            if(kind==2) train.getList("schedule",Tag.TAG_COMPOUND).getCompound(0).getList("waits",Tag.TAG_COMPOUND).getCompound(0).putString("type","INTERRUPT_2_0");
            boolean rejected=false;try {RailwayNbt.read(broken,h.getLevel().registryAccess());}catch(IllegalArgumentException expected){rejected=true;}
            h.assertTrue(rejected,"Невалидный runtime/typed condition молча принят: "+kind);
        }
        h.succeed();
    }
    @GameTest(template="empty16")
    public static void schemaThreeMigratesWithoutInventingWaitAndKeepsCargo(GameTestHelper h) {
        var state=waiting(h);state.cars().get(CARGO).cargo().orElseThrow().insert(0,"minetorio:iron_plate",17,false);
        var old=RailwayNbt.write(state,h.getLevel().registryAccess());old.putInt("schemaVersion",3);old.put("stops",new ListTag());
        for(Tag item:old.getList("trains",Tag.TAG_COMPOUND)) {var train=(CompoundTag)item;train.put("schedule",new ListTag());train.put("runtime",new CompoundTag());}
        var loaded=RailwayNbt.read(old,h.getLevel().registryAccess());
        h.assertTrue(loaded.dispatcher().runtime(TRAIN).schedule().stops().isEmpty() && loaded.dispatcher().runtime(TRAIN).arrivalTick().isEmpty(),"Миграция придумала ожидание");
        h.assertTrue(loaded.cars().get(CARGO).cargo().orElseThrow().get(0).count()==17,"Миграция потеряла груз");h.succeed();
    }
    @GameTest(template="empty16")
    public static void ownerRailDeletionRoundTripsWithoutLosingOtherStationsScheduleOrCargo(GameTestHelper h) {
        var state=waiting(h);var d=state.dispatcher();d.removeStation(STATION);
        var station=new Station(STATION,"A",new Cursor(new Traversal(new UUID(13,50),true),0),true,OptionalInt.of(1));d.upsertStation(station);
        UUID survivor=new UUID(12,5);var other=new Station(survivor,"B",new Cursor(new Traversal(new UUID(13,60),true),0),true,OptionalInt.of(1));d.upsertStation(other);
        var schedule=d.runtime(TRAIN).schedule();state.cars().get(CARGO).cargo().orElseThrow().insert(0,"minetorio:iron_plate",17,false);d.tick(RailCircuitInputs.NONE,0);
        var before=RailwayNbt.write(state,h.getLevel().registryAccess());
        state.replaceRails(state.rails().stream().filter(p->!p.id().equals(station.stop().traversal().piece())).toList());
        CompoundTag saved=null;try {saved=RailwayNbt.write(state,h.getLevel().registryAccess());}catch(IllegalArgumentException invalid){/* Проверяем штатную owner mutation как assertion. */}
        h.assertTrue(saved!=null,"Штатное удаление рельса станции блокирует NBT write/read");
        var restored=RailwayNbt.read(saved,h.getLevel().registryAccess());
        h.assertTrue(restored.stations().equals(Map.of(survivor,other)),"Удалены посторонние станции или сохранена станция без рельса");
        h.assertTrue(restored.dispatcher().runtime(TRAIN).schedule().equals(schedule),"Удаление рельса потеряло расписание");
        h.assertTrue(saved.getList("cars",Tag.TAG_COMPOUND).equals(before.getList("cars",Tag.TAG_COMPOUND)),"Удаление рельса изменило груз или топливо");
        h.assertTrue(restored.dispatcher().runtime(TRAIN).assignment().isEmpty() && restored.dispatcher().runtime(TRAIN).arrivalTick().isEmpty(),"Удаленный target продолжает назначение/ожидание");h.succeed();
    }
    private static CompoundTag railWaiting(GameTestHelper h) {
        var state=waiting(h);var point=state.stations().get(STATION).stop();var d=state.dispatcher();d.removeStation(STATION);
        state.cars().get(CARGO).cargo().orElseThrow().insert(0,"minetorio:iron_plate",17,false);
        d.setSchedule(TRAIN,new TrainSchedule(List.of(new Stop(Optional.empty(),Optional.of(new RailTarget(point,false)),true,List.of(new Wait(Type.TIME,Join.AND,60,Optional.empty())))),0));
        d.tick(RailCircuitInputs.NONE,0);h.assertTrue(d.runtime(TRAIN).arrivalTick().isPresent(),"Fixture не прибыл на rail target");
        return RailwayNbt.write(state,h.getLevel().registryAccess());
    }
    @GameTest(template="empty16")
    public static void staleRailTargetWaitDoesNotSurvivePhysicalMismatchOrMovingTrain(GameTestHelper h) {
        var saved=railWaiting(h);
        for(int variant=0;variant<3;variant++) {
            var stale=saved.copy();var train=stale.getList("trains",Tag.TAG_COMPOUND).getCompound(0);var record=train.getList("schedule",Tag.TAG_COMPOUND).getCompound(0);var point=record.getCompound("railTarget");
            if(variant==0) {point.putUUID("piece",new UUID(13,40));point.putDouble("distance",0);}
            if(variant==1) train.putDouble("speed",0.05);
            if(variant==2) {point.putBoolean("forward",false);point.putDouble("distance",0.5);}
            var restored=RailwayNbt.read(stale,h.getLevel().registryAccess());var runtime=restored.dispatcher().runtime(TRAIN);
            h.assertTrue(runtime.arrivalTick().isEmpty() && runtime.activityTick().isEmpty() && !runtime.waitComplete(),"Stale rail wait продолжает часы: "+variant);
            h.assertTrue(runtime.schedule().stops().size()==1 && runtime.schedule().stops().getFirst().temporary(),"Stale wait потерял temporary schedule");
            h.assertTrue(restored.cars().get(CARGO).cargo().orElseThrow().get(0).count()==17,"Stale wait потерял груз");
            h.assertTrue(restored.trains().get(TRAIN).speed()==train.getDouble("speed"),"Restore обнулил скорость");
        }
        h.succeed();
    }
    @GameTest(template="empty16")
    public static void validRailTargetWaitRetainsClockIncludingEitherDirection(GameTestHelper h) {
        var saved=railWaiting(h);long arrival=saved.getList("trains",Tag.TAG_COMPOUND).getCompound(0).getCompound("runtime").getLong("arrivalTick");
        for(boolean inverse:new boolean[]{false,true}) {
            var valid=saved.copy();var record=valid.getList("trains",Tag.TAG_COMPOUND).getCompound(0).getList("schedule",Tag.TAG_COMPOUND).getCompound(0);
            if(inverse) {record.putBoolean("eitherDirection",true);var point=record.getCompound("railTarget");point.putBoolean("forward",false);point.putDouble("distance",0.5);}
            var restored=RailwayNbt.read(valid,h.getLevel().registryAccess());h.assertTrue(restored.dispatcher().runtime(TRAIN).arrivalTick().orElse(-1)==arrival,"Корректное rail wait потеряло clock");
            restored.dispatcher().tick(RailCircuitInputs.NONE,0);h.assertTrue(restored.dispatcher().context(TRAIN,RailCircuitInputs.NONE).elapsed()==1,"Rail wait повторил arrival");
        }
        h.succeed();
    }
}
