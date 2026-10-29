package dev.minetorio.core.rail;

import java.util.*;
import java.math.BigDecimal;
import dev.minetorio.core.rail.RailwayState.Car;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.minetorio.core.data.RailData.StockKind;
import static dev.minetorio.core.rail.RailwayState.*;
import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.TrainSchedule.*;

class TrainDispatcherTest {
    static final RailCircuitInputs NONE=RailCircuitInputs.NONE;
    static Wait time(long ticks) { return new Wait(Type.TIME,Join.AND,ticks,Optional.empty()); }
    static Stop stop(String name, Wait... waits) { return new Stop(Optional.of(name),Optional.empty(),false,List.of(waits)); }
    static Station station(TrainTestFixture f, String name, double q, int sign, int limit) {
        var point=f.state.locate(f.tape,q).cursor();
        if(sign<0) point=new Cursor(RailwayState.reverse(point.traversal()),f.state.railLength(point.traversal())-point.distance());
        return new Station(UUID.randomUUID(),name,point,true,OptionalInt.of(limit));
    }
    static Train train(TrainTestFixture f, double q, int sign, Car... rest) {
        var loco=f.car(StockKind.LOCOMOTIVE,sign<0);loco.fuel().orElseThrow().insert(0,"minetorio:coal",20,false);
        var cars=new ArrayList<Car>();cars.add(loco);cars.addAll(List.of(rest));return f.train(q,0,Mode.AUTOMATIC,cars.toArray(Car[]::new));
    }
    static void ticks(TrainDispatcher d, int n) { for(int i=0;i<n;i++) d.tick(NONE,0); }
    @Test void realMotionArrivesAtExternalCouplerInBothDirectionsWithoutMovingBodiesByCommand() {
        for(int sign:new int[]{1,-1}) {
            var f=new TrainTestFixture();var t=train(f,100,sign);var d=f.state.dispatcher();
            var target=station(f,"A",sign>0?108.127:90.123,sign,1);d.upsertStation(target);
            var poses=TrainMotion.carPoses(f.state,t.id());d.setSchedule(t.id(),new TrainSchedule(List.of(stop("A",time(9999))),0));
            assertEquals(poses,TrainMotion.carPoses(f.state,t.id()));
            for(int i=0;i<2000 && d.runtime(t.id()).arrivalTick().isEmpty();i++) {
                d.tick(NONE,0); var head=f.state.trains().get(t.id()).head().orElseThrow();
                double q=2*f.tape.indexOf(head.traversal())+head.distance()+sign*3.5;
                assertTrue(sign>0?q<=108.127+1e-8:q>=90.123-1e-8,"Поезд проехал цель");
            }
            assertTrue(d.runtime(t.id()).arrivalTick().isPresent(),"Нет физического прибытия");
            assertEquals(0,f.state.trains().get(t.id()).speed());
            var actual=TrainReservations.leading(f.state,t.id(),sign).start();assertEquals(target.stop().traversal(),actual.traversal());assertEquals(target.stop().distance(),actual.distance(),1e-8);
            assertEquals(poses.values().iterator().next().tangent(),TrainMotion.carPoses(f.state,t.id()).values().iterator().next().tangent());
        }
    }
    @Test void arrivalTimeStartsAtZeroAndIsNotResetOnNextTick() {
        var f=new TrainTestFixture();var t=train(f,50,1);var d=f.state.dispatcher();var a=station(f,"A",53.5,1,1);
        d.upsertStation(a);d.setSchedule(t.id(),new TrainSchedule(List.of(stop("A",time(60))),0));d.tick(NONE,0);
        assertTrue(d.runtime(t.id()).arrivalTick().isPresent());assertEquals(0,d.context(t.id(),NONE).elapsed());
        ticks(d,59);assertEquals(59,d.context(t.id(),NONE).elapsed());assertFalse(d.runtime(t.id()).waitComplete());
    }
    @Test void physicalCargoFuelAndFluidResetInactivityButMetadataSimulationAndEnergyDoNot() {
        var f=new TrainTestFixture();var cargo=f.car(StockKind.CARGO,false);var fluid=f.car(StockKind.FLUID,false);var t=train(f,50,1,cargo,fluid);var d=f.state.dispatcher();
        d.upsertStation(station(f,"A",53.5,1,1));d.setSchedule(t.id(),new TrainSchedule(List.of(stop("A",time(9999))),0));ticks(d,11);
        assertEquals(10,d.context(t.id(),NONE).inactive());
        cargo.cargo().orElseThrow().insert(0,"minetorio:iron_plate",1,false);assertEquals(0,d.context(t.id(),NONE).inactive());ticks(d,5);
        cargo.cargo().orElseThrow().setBar(0);cargo.cargo().orElseThrow().setFilter(0,Optional.of("minetorio:coal"));cargo.cargo().orElseThrow().extract(0,1,true);d.passengers(t.id(),true,1);
        assertEquals(5,d.context(t.id(),NONE).inactive());
        var loco=f.state.cars().get(t.cars().getFirst());loco.fuel().orElseThrow().extract(0,1,false);assertEquals(0,d.context(t.id(),NONE).inactive());ticks(d,5);
        loco.setActiveFuel("minetorio:coal",BigDecimal.valueOf(10000));assertEquals(5,d.context(t.id(),NONE).inactive());
        fluid.insertFluid("water",0.5);assertEquals(0,d.context(t.id(),NONE).inactive());ticks(d,5);fluid.extractFluid(0.25);assertEquals(0,d.context(t.id(),NONE).inactive());
        assertTrue(d.context(t.id(),NONE).passengers().isPresent());
    }
    @Test void disabledIsSkippedButZeroLimitIsNotSkipped() {
        var f=new TrainTestFixture();var t=train(f,50,1);var d=f.state.dispatcher();var a=station(f,"A",100,1,1);var b=station(f,"B",120,1,0);
        d.upsertStation(new Station(a.id(),a.name(),a.stop(),false,a.trainLimit()));d.upsertStation(b);
        d.setSchedule(t.id(),new TrainSchedule(List.of(stop("missing",time(10)),stop("A",time(10)),stop("B",time(10))),0));d.tick(NONE,0);
        assertEquals(Status.DESTINATION_FULL,f.state.trains().get(t.id()).status());assertEquals(2,d.runtime(t.id()).schedule().current());assertEquals(0,d.stationOutput(b.id()).assignedCount());
    }
    @Test void loweredLimitPreservesAssignmentAndDisableRepathsToOtherStation() {
        var f=new TrainTestFixture();var t=train(f,50,1);var d=f.state.dispatcher();var a=station(f,"A",100,1,1);var b=station(f,"A",120,1,1);
        d.upsertStation(a);d.upsertStation(b);d.setSchedule(t.id(),new TrainSchedule(List.of(stop("A",time(10))),0));d.tick(NONE,0);
        assertEquals(1,d.stationOutput(a.id()).assignedCount());d.upsertStation(new Station(a.id(),a.name(),a.stop(),true,OptionalInt.of(0)));d.tick(NONE,0);assertEquals(1,d.stationOutput(a.id()).assignedCount());
        d.upsertStation(new Station(a.id(),a.name(),a.stop(),false,OptionalInt.of(0)));d.tick(NONE,0);assertEquals(0,d.stationOutput(a.id()).assignedCount());assertEquals(1,d.stationOutput(b.id()).assignedCount());
    }
    @Test void fullNextStationKeepsOldHoldAndClocksUntilAtomicTransfer() {
        var f=new TrainTestFixture();var t=train(f,50,1);var d=f.state.dispatcher();var a=station(f,"A",53.5,1,1);var b=station(f,"B",100,1,0);
        d.upsertStation(a);d.upsertStation(b);d.setSchedule(t.id(),new TrainSchedule(List.of(stop("A",time(1)),stop("B",time(10))),0));ticks(d,5);
        assertEquals(1,d.stationOutput(a.id()).assignedCount());assertEquals(0,d.stationOutput(b.id()).assignedCount());assertEquals(4,d.context(t.id(),NONE).elapsed());assertTrue(d.runtime(t.id()).waitComplete());
        d.upsertStation(new Station(b.id(),b.name(),b.stop(),true,OptionalInt.of(1)));d.tick(NONE,0);
        assertEquals(0,d.stationOutput(a.id()).assignedCount());assertEquals(1,d.stationOutput(b.id()).assignedCount());
    }
    @Test void lastQuotaHasOneWinnerAndCommandsPreservePoses() {
        var f=new TrainTestFixture();var first=train(f,50,1);var second=train(f,30,1);var d=f.state.dispatcher();var a=station(f,"A",100,1,1);d.upsertStation(a);
        for(var t:List.of(first,second)) d.setSchedule(t.id(),new TrainSchedule(List.of(stop("A",time(10))),0));d.tick(NONE,0);
        assertEquals(1,d.stationOutput(a.id()).assignedCount());
        assertEquals(1,f.state.trains().values().stream().filter(t->t.status()==Status.DESTINATION_FULL).count());
        var poses=TrainMotion.carPoses(f.state,first.id());var speed=f.state.trains().get(first.id()).speed();d.setMode(first.id(),Mode.MANUAL);
        assertEquals(poses,TrainMotion.carPoses(f.state,first.id()));assertEquals(speed,f.state.trains().get(first.id()).speed());
        d.removeStation(a.id());assertEquals(0,d.stationOutput(a.id()).assignedCount());
    }
    @Test void negativeLimitRejectedAndOwnerIsolation() {
        var f=new TrainTestFixture();var a=station(f,"A",53.5,1,1);
        assertThrows(IllegalArgumentException.class,()->new Station(a.id(),a.name(),a.stop(),true,OptionalInt.of(-1)));
        var t=train(f,50,1);var d=f.state.dispatcher();d.upsertStation(a);d.setSchedule(t.id(),new TrainSchedule(List.of(stop("A",time(10))),0));d.tick(NONE,0);
        var other=new RailwayState(key->100);other.dispatcher().cargoChanged(t.cars().getFirst());
        assertEquals(1,d.stationOutput(a.id()).assignedCount());assertSame(d,f.state.dispatcher());
    }
    @Test void temporaryWaypointIsRemovedOnceWithoutArtificialStop() {
        var f=new TrainTestFixture();var t=train(f,50,1);var d=f.state.dispatcher();var a=station(f,"A",54,1,2);var b=station(f,"B",80,1,1);
        d.upsertStation(a);d.upsertStation(b);var waypoint=new Stop(Optional.of("A"),Optional.empty(),true,List.of());
        d.setSchedule(t.id(),new TrainSchedule(List.of(waypoint,stop("B",time(999))),0));
        for(int i=0;i<200 && d.runtime(t.id()).schedule().stops().size()!=1;i++) d.tick(NONE,0);
        assertEquals(1,d.runtime(t.id()).schedule().stops().size());assertTrue(f.state.trains().get(t.id()).speed()>0);assertEquals("B",d.runtime(t.id()).schedule().stops().getFirst().station().orElseThrow());
    }
    @Test void railTargetAcceptsEitherDirectionAndNoPassengerBridgeIsUnknown() {
        var f=new TrainTestFixture();var t=train(f,50,-1);var point=f.state.locate(f.tape,46.5).cursor();var d=f.state.dispatcher();
        d.setSchedule(t.id(),new TrainSchedule(List.of(new Stop(Optional.empty(),Optional.of(new RailTarget(point,true)),false,List.of(time(100)))),0));d.tick(NONE,0);
        assertTrue(d.runtime(t.id()).arrivalTick().isPresent());assertTrue(d.context(t.id(),NONE).passengers().isEmpty());
    }
    @Test void sameCursorOnNextLoopDoesNotArriveAtEarlierOccurrence() {
        var state=new RailwayState(key->100);state.replaceRails(TrainTestFixture.loop());var tape=TrainTestFixture.loopTape(state,3);
        var car=state.createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,false);car.fuel().orElseThrow().insert(0,"minetorio:coal",10,false);
        var pos=state.locate(tape,30);UUID id=UUID.randomUUID();state.createTrain(new Train(id,List.of(car.id()),Optional.of(pos.cursor()),pos.index(),tape,0,Mode.AUTOMATIC,Status.ON_THE_PATH,0,0,0));
        var d=state.dispatcher();var a=new Station(UUID.randomUUID(),"loop",TrainReservations.leading(state,id,1).start(),true,OptionalInt.of(1));d.upsertStation(a);d.setSchedule(id,new TrainSchedule(List.of(stop("loop",time(1))),0));d.tick(NONE,0);
        assertTrue(d.runtime(id).arrivalTick().isPresent());d.tick(NONE,0);
        assertTrue(d.runtime(id).arrivalTick().isEmpty());assertTrue(d.runtime(id).assignment().orElseThrow().visits().getFirst().remaining()>20);
        long trip=d.runtime(id).tripId();for(int i=0;i<2000 && d.runtime(id).arrivalTick().isEmpty();i++)d.tick(NONE,0);
        assertTrue(d.runtime(id).arrivalTick().isPresent());assertEquals(trip,d.runtime(id).tripId());
    }
    @Test void repeatedWaypointClaimsCountOccurrencesOfOneTrain() {
        var state=new RailwayState(key->100);state.replaceRails(TrainTestFixture.loop());var tape=TrainTestFixture.loopTape(state,3);
        var car=state.createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,false);car.fuel().orElseThrow().insert(0,"minetorio:coal",10,false);
        var pos=state.locate(tape,30);UUID id=UUID.randomUUID();state.createTrain(new Train(id,List.of(car.id()),Optional.of(pos.cursor()),pos.index(),tape,1.2,Mode.AUTOMATIC,Status.ON_THE_PATH,0,0,0));
        var d=state.dispatcher();var point=state.locate(tape,34).cursor();var a=new Station(UUID.randomUUID(),"loop",point,true,OptionalInt.of(10));d.upsertStation(a);d.setSchedule(id,new TrainSchedule(List.of(stop("loop")),0));
        var before=TrainMotion.carPoses(state,id).get(car.id()).position();d.tick(NONE,0);
        assertTrue(d.stationOutput(a.id()).assignedCount()>=2);assertEquals(1,d.claims().stream().map(TrainDispatcher.Claim::trainId).distinct().count());
        var after=TrainMotion.carPoses(state,id).get(car.id()).position();
        assertTrue(Math.hypot(after.x()-before.x(),after.z()-before.z())>0.01,"Короткий lookahead заморозил движущийся поезд");
    }
    @Test void staleCandidateDoesNotTakeQuotaOrChangePoses() {
        var f=new TrainTestFixture();var t=train(f,50,1);var d=f.state.dispatcher();var a=station(f,"A",100,1,1);d.upsertStation(a);var schedule=new TrainSchedule(List.of(stop("A",time(10))),0);d.setSchedule(t.id(),schedule);
        var expected=f.state.trains().get(t.id());var route=RailRouter.route(f.state.graph(),TrainReservations.leading(f.state,t.id(),1).start(),Set.of(a.stop()),f.state.signals()::allowed).orElseThrow();
        var visit=new TrainDispatcher.Visit(Optional.of(a.id()),a.stop(),0,2,route.steps().size()-1,route.length());
        var assignment=new TrainDispatcher.Assignment(1,expected.routeRevision()+1,f.state.graph().revision(),f.state.signals().revision(),route.steps(),0,List.of(visit));
        var candidate=new TrainDispatcher.Candidate(expected,f.state.graph().revision(),f.state.signals().revision(),f.state.stationRevision(),d.runtime(t.id()),schedule,assignment);
        d.renameStation(a.id(),"renamed");var poses=TrainMotion.carPoses(f.state,t.id());assertFalse(d.commitCandidate(candidate));assertEquals(0,d.stationOutput(a.id()).assignedCount());assertEquals(poses,TrainMotion.carPoses(f.state,t.id()));
    }
    @Test void splitAndMergeCopyValueScheduleAndClearAllClaims() {
        var f=new TrainTestFixture();var cargo=f.car(StockKind.CARGO,false);var t=train(f,50,1,cargo);var d=f.state.dispatcher();var a=station(f,"A",53.5,1,1);d.upsertStation(a);var schedule=new TrainSchedule(List.of(stop("A",time(99))),0);d.setSchedule(t.id(),schedule);d.tick(NONE,0);
        var loco=f.state.cars().get(t.cars().getFirst());UUID split=TrainCoupling.split(f.state,loco.id(),TrainCoupling.End.BACK).orElseThrow();
        assertEquals(0,d.stationOutput(a.id()).assignedCount());assertEquals(schedule,d.runtime(split).schedule());assertEquals(schedule,d.runtime(t.id()).schedule());assertEquals(Mode.MANUAL,f.state.trains().get(split).mode());
        assertTrue(TrainCoupling.couple(f.state,loco.id(),TrainCoupling.End.BACK,cargo.id(),TrainCoupling.End.FRONT));assertEquals(schedule,d.runtime(t.id()).schedule());assertEquals(1,f.state.trains().size());assertEquals(0,d.stationOutput(a.id()).assignedCount());
    }
    @Test void sameUuidInTwoOwnersNeverResetsOtherClock() {
        var f=new TrainTestFixture();var t=train(f,50,1);var d=f.state.dispatcher();d.upsertStation(station(f,"A",53.5,1,1));d.setSchedule(t.id(),new TrainSchedule(List.of(stop("A",time(999))),0));ticks(d,6);
        var other=new RailwayState(key->100);other.replaceRails(f.state.rails());var car=other.createCar(t.cars().getFirst(),StockKind.LOCOMOTIVE,false);other.createTrain(t);car.fuel().orElseThrow().insert(0,"minetorio:coal",1,false);
        assertEquals(5,d.context(t.id(),NONE).inactive());assertTrue(other.dispatcher().runtime(t.id()).arrivalTick().isEmpty());
    }
    @Test void stationOutputExcludesFuelAndRoundsOnlyCircuitFluid() {
        var f=new TrainTestFixture();var cargo=f.car(StockKind.CARGO,false);var fluid=f.car(StockKind.FLUID,false);var t=train(f,50,1,cargo,fluid);var d=f.state.dispatcher();var a=station(f,"A",53.5,1,1);d.upsertStation(a);d.setSchedule(t.id(),new TrainSchedule(List.of(stop("A",time(999))),0));d.tick(NONE,0);
        cargo.cargo().orElseThrow().insert(0,"minetorio:iron_plate",7,false);fluid.insertFluid("water",0.5);
        var output=d.stationOutput(a.id());assertEquals(Map.of(new SignalKey(SignalKind.ITEM,"minetorio:iron_plate"),7,new SignalKey(SignalKind.FLUID,"water"),1),output.cargo());
        assertEquals(0.5,d.context(t.id(),NONE).fluids().getFirst().amount());
    }
    @Test void allSkippedIsBoundedAndFuelActivationResetsOnlyPhysicalInventory() {
        var f=new TrainTestFixture();var t=train(f,50,1);var d=f.state.dispatcher();d.setSchedule(t.id(),new TrainSchedule(List.of(stop("missing",time(1)),stop("disabled",time(1))),0));d.tick(NONE,0);
        assertEquals(Status.NO_PATH,f.state.trains().get(t.id()).status());assertEquals(1,f.state.railClock());
        var a=station(f,"A",53.5,1,1);d.upsertStation(a);d.setSchedule(t.id(),new TrainSchedule(List.of(stop("A",time(999))),0));ticks(d,6);
        var loco=f.state.cars().get(t.cars().getFirst());assertEquals(5,d.context(t.id(),NONE).inactive());TrainFuel.draw(loco,BigDecimal.ONE);assertEquals(0,d.context(t.id(),NONE).inactive());ticks(d,5);TrainFuel.draw(loco,BigDecimal.ONE);assertEquals(5,d.context(t.id(),NONE).inactive());
    }
    @Test void redSignalKeepsNewStationClaimAndCannotUseOldPlan() {
        var f=new TrainTestFixture();f.state.replaceSignals(List.of(RailSignalsTest.signal(f,54,RailSignals.Kind.REGULAR,true)));
        var t=train(f,50.5,1);var d=f.state.dispatcher();var a=station(f,"A",54,1,1);var b=station(f,"B",90,1,1);d.upsertStation(a);d.upsertStation(b);
        d.setSchedule(t.id(),new TrainSchedule(List.of(stop("A",time(1)),stop("B",time(999))),0));d.tick(NONE,0);
        var oldPlan=TrainReservationsTest.plan(f,t.id(),1,0,100);
        var blocked=new RailCircuitInputs() {
            public Optional<Map<SignalKey,Integer>> forTrain(UUID id,Optional<UUID> station){return Optional.empty();}
            public Optional<Boolean> stationEnabled(UUID id){return Optional.empty();}
            public OptionalInt stationLimit(UUID id){return OptionalInt.empty();}
            public boolean signalClosed(UUID id){return true;}
        };
        var before=f.state.trains().get(t.id());var energy=f.state.cars().get(t.cars().getFirst()).remainingJ();d.tick(blocked,0);
        assertEquals(0,d.stationOutput(a.id()).assignedCount());assertEquals(1,d.stationOutput(b.id()).assignedCount());assertEquals(Status.WAIT_SIGNAL,f.state.trains().get(t.id()).status());
        assertEquals(before.head(),f.state.trains().get(t.id()).head());assertEquals(energy,f.state.cars().get(t.cars().getFirst()).remainingJ());assertFalse(TrainReservations.commit(f.state,t.id(),oldPlan,1,0));
    }
    @Test void renameWaitingStationEndsOldWaitButKeepsPhysicalHoldUntilNextAssignment() {
        var f=new TrainTestFixture();var t=train(f,50,1);var d=f.state.dispatcher();var a=station(f,"A",53.5,1,1);var b=station(f,"B",100,1,0);
        d.upsertStation(a);d.upsertStation(b);d.setSchedule(t.id(),new TrainSchedule(List.of(stop("A",time(99999)),stop("B",time(99999))),0));d.tick(NONE,0);
        long arrival=d.runtime(t.id()).arrivalTick().orElseThrow();var poses=TrainMotion.carPoses(f.state,t.id());
        d.renameStation(a.id(),"renamed");d.tick(NONE,0);
        assertTrue(d.runtime(t.id()).waitComplete(),"Rename оставил ожидание исчезнувшего имени");
        assertEquals(arrival,d.runtime(t.id()).arrivalTick().orElseThrow());assertEquals(1,d.stationOutput(a.id()).assignedCount());
        assertEquals(Optional.of(t.id()),d.stationOutput(a.id()).stoppedTrain());assertEquals(Status.DESTINATION_FULL,f.state.trains().get(t.id()).status());assertEquals(poses,TrainMotion.carPoses(f.state,t.id()));
        d.upsertStation(new Station(b.id(),b.name(),b.stop(),true,OptionalInt.of(1)));d.tick(NONE,0);
        assertEquals(0,d.stationOutput(a.id()).assignedCount());assertEquals(1,d.stationOutput(b.id()).assignedCount());assertTrue(d.runtime(t.id()).arrivalTick().isEmpty());
    }
    @Test void movingWaitingStationDropsFalsePhysicalHoldAndReplansWithoutTeleport() {
        var f=new TrainTestFixture();var t=train(f,50,1);var d=f.state.dispatcher();var a=station(f,"A",53.5,1,1);
        d.upsertStation(a);d.setSchedule(t.id(),new TrainSchedule(List.of(stop("A",time(99999))),0));d.tick(NONE,0);
        var poses=TrainMotion.carPoses(f.state,t.id());var moved=station(f,"A",80,1,1);d.upsertStation(new Station(a.id(),a.name(),moved.stop(),true,a.trainLimit()));
        assertEquals(poses,TrainMotion.carPoses(f.state,t.id()));assertTrue(d.runtime(t.id()).arrivalTick().isEmpty(),"Перенос станции сохранил ложное ожидание");
        assertEquals(0,d.stationOutput(a.id()).assignedCount());assertTrue(d.stationOutput(a.id()).stoppedTrain().isEmpty());d.tick(NONE,0);
        assertTrue(d.runtime(t.id()).arrivalTick().isEmpty());assertEquals(1,d.stationOutput(a.id()).assignedCount());assertTrue(d.stationOutput(a.id()).stoppedTrain().isEmpty());
        assertEquals(moved.stop(),d.runtime(t.id()).assignment().orElseThrow().visits().getFirst().point());
    }
    @Test void railDeletionRemovesOnlyStationsWhoseGeometryDisappeared() {
        var f=new TrainTestFixture();var t=train(f,50,1);var d=f.state.dispatcher();var a=station(f,"A",100,1,1);var b=station(f,"B",120,1,1);
        d.upsertStation(a);d.upsertStation(b);var schedule=new TrainSchedule(List.of(stop("A",time(99999)),stop("B",time(99999))),0);d.setSchedule(t.id(),schedule);d.tick(NONE,0);
        f.state.replaceRails(f.state.rails().stream().filter(p->!p.id().equals(a.stop().traversal().piece())).toList());
        assertFalse(f.state.stations().containsKey(a.id()),"Станция осталась на удаленном рельсе");assertEquals(Map.of(b.id(),b),f.state.stations());assertEquals(schedule,d.runtime(t.id()).schedule());assertEquals(0,d.stationOutput(a.id()).assignedCount());
    }
}
