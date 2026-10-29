package dev.minetorio.core.rail;
import java.util.*;
import static dev.minetorio.core.rail.RailGeometry.*;
import java.math.BigDecimal;
import dev.minetorio.core.data.RailData.StockKind;
import static dev.minetorio.core.rail.RailwayState.*;
import static dev.minetorio.core.rail.TrainMotion.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TrainMotionTest {
    static final Control GO = new Control(1,false,Turn.STRAIGHT);
    @Test void coalFirstThreeSubstepsAndSixtyFullBudgets() {
        var f=new TrainTestFixture(); var c=f.car(StockKind.LOCOMOTIVE,false); c.fuel().get().insert(0,"minetorio:coal",1,false);
        var t=f.train(50,0,Mode.MANUAL,c);
        for (double expected : new double[]{0.00498125,0.0096947578125,0.014390589970703125}) {
            step(f.state,t.id(),GO,0); assertEquals(expected,f.state.trains().get(t.id()).speed(),1e-14);
        }
        for (int i=3;i<60;i++) step(f.state,t.id(),GO,0);
        assertEquals(0,c.remainingJ().compareTo(new BigDecimal("3400000")));
    }
    @Test void capAfterAirConsumesOnlyRequiredJoules() {
        var f=new TrainTestFixture(); var c=f.car(StockKind.LOCOMOTIVE,false); c.setActiveFuel("minetorio:coal",new BigDecimal("4000000")); c.fuel().get().insert(0,"minetorio:coal",1,false);
        var t=f.train(50,1.2,Mode.MANUAL,c);
        step(f.state,t.id(),GO,0);
        assertEquals(1.2,f.state.trains().get(t.id()).speed(),1e-12);
        assertEquals(9533.877038895859,new BigDecimal("4000000").subtract(c.remainingJ()).doubleValue(),1e-8);
        for(int i=1;i<600;i++) step(f.state,t.id(),GO,0);
        assertEquals(1.2,f.state.trains().get(t.id()).speed(),1e-12);
        assertEquals(600*9533.877038895859,8000000-c.remainingJ().doubleValue(),1e-6);
    }
    @Test void reverseBrakesToZeroBeforeTractionBothSigns() {
        for(int sign:new int[]{-1,1}) {
            var f=new TrainTestFixture(); var c=f.car(StockKind.LOCOMOTIVE,false); c.setActiveFuel("minetorio:coal",new BigDecimal("100000"));
            var t=f.train(50,sign*0.003,Mode.MANUAL,c); var reverse=new Control(-sign,false,Turn.STRAIGHT);
            step(f.state,t.id(),reverse,0); assertEquals(0,f.state.trains().get(t.id()).speed()); assertEquals(new BigDecimal("100000"),c.remainingJ());
            step(f.state,t.id(),reverse,0); assertEquals(-sign,Math.signum(f.state.trains().get(t.id()).speed()));
        }
    }
    @Test void independentBrakeDistances() {
        var f=new TrainTestFixture(); var a=f.car(StockKind.LOCOMOTIVE,false); var b=f.car(StockKind.CARGO,false); var c=f.car(StockKind.FLUID,false);
        for(int sign:new int[]{-1,1}) for(double bonus:new double[]{0,0.1,1}) {
            var t=new Train(UUID.randomUUID(),List.of(a.id(),b.id(),c.id()),Optional.empty(),-1,List.of(),sign*1.2,Mode.MANUAL,Status.MANUAL_CONTROL,0,0,0);
            double expected=bonus==0?179.4:bonus==0.1?163.0368:89.4;
            assertEquals(expected,stoppingDistance(t,List.of(a,b,c),bonus),1e-9);
        }
    }

    @Test void manualReverseUsesPointSixAndAutomaticCannotReverseWithoutMover() {
        for(Mode mode:Mode.values()) {
            var f=new TrainTestFixture(); var car=f.car(StockKind.LOCOMOTIVE,false); car.setActiveFuel("minetorio:coal",new BigDecimal("100000")); var t=f.train(50,0,mode,car);
            step(f.state,t.id(),new Control(-1,false,Turn.STRAIGHT),0);
            assertEquals(mode==Mode.MANUAL?-0.003*0.99625:0,f.state.trains().get(t.id()).speed(),1e-14);
            assertEquals(mode==Mode.MANUAL?90000:100000,car.remainingJ().doubleValue());
        }
    }
    @Test void opposedLocomotiveHelpsOnlyAllowedDirectionAndAllLocosContributeToCap() {
        var f=new TrainTestFixture(); var a=f.car(StockKind.LOCOMOTIVE,false); var b=f.car(StockKind.LOCOMOTIVE,true);
        a.setActiveFuel("minetorio:coal",new BigDecimal("100000")); b.setActiveFuel("minetorio:rocket_fuel",new BigDecimal("100000"));
        assertEquals(1.29,speedCap(List.of(a,b),Map.of()),1e-14);
        var t=f.train(50,0,Mode.AUTOMATIC,a,b); step(f.state,t.id(),GO,0);
        assertEquals(0.0025*(1-0.0075/4),f.state.trains().get(t.id()).speed(),1e-14);
        assertEquals(new BigDecimal("100000"),b.remainingJ());
    }
    @Test void negativeSpeedUsesLastCarsAirResistanceAndKeepsBodyYaw() {
        var f=new TrainTestFixture(); var a=f.car(StockKind.LOCOMOTIVE,false); var b=f.car(StockKind.CARGO,true); var t=f.train(50,-0.1,Mode.MANUAL,a,b);
        var before=carPoses(f.state,t.id()); step(f.state,t.id(),new Control(0,false,Turn.STRAIGHT),0);
        assertEquals(-(0.1-1.0/3000)*(1-0.01/3),f.state.trains().get(t.id()).speed(),1e-14);
        assertEquals(t.cars(),f.state.trains().get(t.id()).cars());
        before.forEach((id,pose)->assertEquals(pose.tangent(),carPoses(f.state,t.id()).get(id).tangent()));
    }
    @Test void noFuelNearCapDoesNotCreateFreeThrustAndFuelBoundaryUsesOneFinalCap() {
        for(boolean upgrade:new boolean[]{true,false}) {
            var f=new TrainTestFixture(); var a=f.car(StockKind.LOCOMOTIVE,false);
            a.setActiveFuel(upgrade?"minetorio:coal":"minetorio:solid_fuel",new BigDecimal("0.25"));
            a.fuel().get().insert(0,upgrade?"minetorio:solid_fuel":"minetorio:coal",1,false);
            var t=f.train(50,upgrade?1.2:1.26,Mode.MANUAL,a);
            step(f.state,t.id(),GO,0);
            assertEquals(upgrade?"minetorio:solid_fuel":"minetorio:coal",a.activeFuel());
            assertTrue(f.state.trains().get(t.id()).speed()<= (upgrade?1.26:1.2)+1e-12);
            assertTrue(a.remainingJ().doubleValue()<(upgrade?12000000:4000000));
        }
        var f=new TrainTestFixture(); var a=f.car(StockKind.LOCOMOTIVE,false); a.setActiveFuel("minetorio:coal",BigDecimal.ONE); var t=f.train(50,1.2,Mode.MANUAL,a);
        step(f.state,t.id(),GO,0); assertEquals((1.2-0.00025+0.0000005)*0.99625,f.state.trains().get(t.id()).speed(),1e-12);
        assertEquals(0,a.remainingJ().signum());
    }
    @Test void invalidControlDoesNotMutateAndBrakeNeverBurns() {
        var f=new TrainTestFixture(); var a=f.car(StockKind.LOCOMOTIVE,false); a.setActiveFuel("minetorio:coal",BigDecimal.TEN); var t=f.train(50,0,Mode.MANUAL,a);
        for(double throttle:new double[]{Double.NaN,Double.POSITIVE_INFINITY,1.1,-1.1}) assertThrows(IllegalArgumentException.class,()->step(f.state,t.id(),new Control(throttle,false,Turn.STRAIGHT),0));
        for(double bonus:new double[]{Double.NaN,-0.1,Double.POSITIVE_INFINITY}) assertThrows(IllegalArgumentException.class,()->step(f.state,t.id(),GO,bonus));
        assertEquals(t,f.state.trains().get(t.id())); assertEquals(BigDecimal.TEN,a.remainingJ());
        step(f.state,t.id(),new Control(1,true,Turn.RIGHT),0); assertEquals(BigDecimal.TEN,a.remainingJ()); assertEquals(0,f.state.trains().get(t.id()).speed());
    }
    @Test void trackEndLimitsExternalCouplerAndDoesNotBurnAtStop() {
        var f=new TrainTestFixture(); var a=f.car(StockKind.LOCOMOTIVE,false); a.setActiveFuel("minetorio:coal",new BigDecimal("100000")); var t=f.train(1196.49,0.1,Mode.MANUAL,a);
        step(f.state,t.id(),GO,0); var after=f.state.trains().get(t.id());
        assertEquals(0,after.speed()); assertEquals(-1196.5,carPoses(f.state,t.id()).get(a.id()).position().z(),1e-9);
        BigDecimal remaining=a.remainingJ(); step(f.state,t.id(),GO,0); assertEquals(remaining,a.remainingJ());
    }

    @Test void turnIsRelativeToTravelDirectionAndPrependPreservesYaw() {
        for(boolean reverse:new boolean[]{false,true}) for(Turn turn:Turn.values()) {
            var state=new RailwayState(key->100); List<Piece> rails=new ArrayList<>(); List<Traversal> tape=new ArrayList<>();
            for(int z=12;z>=2;z-=2) { var p=new Piece(new UUID(5,z),new Point(0,0,z),Shape.STRAIGHT,0,false); rails.add(p); tape.add(new Traversal(p.id(),true)); }
            Piece straight=new Piece(new UUID(5,20),new Point(0,0,0),Shape.STRAIGHT,0,false), right=new Piece(new UUID(5,21),new Point(0,0,0),Shape.CURVE,0,false);
            rails.add(straight); rails.add(right); state.replaceRails(rails);
            var car=state.createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,reverse);
            double q=8.49;
            if(reverse) { Collections.reverse(tape); tape.replaceAll(RailwayState::reverse); q=12-q; }
            var pos=state.locate(tape,q); var t=state.createTrain(new Train(UUID.randomUUID(),List.of(car.id()),Optional.of(pos.cursor()),pos.index(),tape,reverse?-0.1:0.1,Mode.MANUAL,Status.MANUAL_CONTROL,0,0,0));
            var yaw=carPoses(state,t.id()).get(car.id()).tangent();
            step(state,t.id(),new Control(0,false,turn),0); var after=state.trains().get(t.id());
            UUID expected=turn==Turn.RIGHT?right.id():straight.id(); assertTrue(after.tape().stream().anyMatch(x->x.piece().equals(expected)));
            assertEquals(yaw,carPoses(state,t.id()).get(car.id()).tangent());
            assertEquals(1,after.routeRevision());
        }
    }
    @Test void reverseWhileTailStillOnOldBranchUsesSavedTape() {
        var state=new RailwayState(key->100); List<Piece> rails=new ArrayList<>(); List<Traversal> tape=new ArrayList<>();
        for(int z=20;z>=2;z-=2) { var p=new Piece(new UUID(6,z),new Point(0,0,z),Shape.STRAIGHT,0,false); rails.add(p); tape.add(new Traversal(p.id(),true)); }
        Piece turn=new Piece(new UUID(6,30),new Point(0,0,0),Shape.CURVE,0,false), straight=new Piece(new UUID(6,31),new Point(0,0,0),Shape.STRAIGHT,0,false);
        rails.add(turn); rails.add(straight); tape.add(new Traversal(turn.id(),true)); state.replaceRails(rails);
        var a=state.createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,false); var b=state.createCar(UUID.randomUUID(),StockKind.CARGO,false);
        var pos=state.locate(tape,24); var t=state.createTrain(new Train(UUID.randomUUID(),List.of(a.id(),b.id()),Optional.of(pos.cursor()),pos.index(),tape,-0.1,Mode.MANUAL,Status.MANUAL_CONTROL,0,0,0));
        double q=24;
        for(int i=0;i<12;i++) {
            step(state,t.id(),new Control(0,false,Turn.LEFT),0); var current=state.trains().get(t.id()); q+=current.speed();
            var poses=carPoses(state,t.id());
            assertTrue(RailwayState.near(state.pose(tape,q,false).position(),poses.get(a.id()).position()));
            assertTrue(RailwayState.near(state.pose(tape,q-7,false).position(),poses.get(b.id()).position()));
            assertFalse(current.tape().stream().anyMatch(x->x.piece().equals(straight.id())));
        }
    }

    @Test void sharedLambdaUsesEachLocomotivesOrderedFuelPortions() {
        var f=new TrainTestFixture(); var a=f.car(StockKind.LOCOMOTIVE,false); var b=f.car(StockKind.LOCOMOTIVE,false);
        a.setActiveFuel("minetorio:coal",new BigDecimal("5000")); a.fuel().get().insert(0,"minetorio:solid_fuel",1,false);
        b.setActiveFuel("minetorio:coal",new BigDecimal("100000"));
        var t=f.train(50,1.2,Mode.MANUAL,a,b);
        // Независимый расчет: air=0.998125; u=1.19975; до 5000 J сила 0.002*J,
        // далее сила 10 + 0.0022*(J-5000), целевая сила=(cap/air-u)*4000.
        double need=(1.2/0.998125-1.19975)*4000;
        double request=5000+(need-10)/0.0022;
        step(f.state,t.id(),GO,0);
        double drawA=5000+12000000-a.remainingJ().doubleValue(), drawB=100000-b.remainingJ().doubleValue();
        assertEquals(request,drawA,1e-8); assertEquals(request,drawB,1e-8);
        assertEquals(1.2,f.state.trains().get(t.id()).speed(),1e-12);
        assertEquals(1.23,speedCap(List.of(a,b),Map.of()),1e-12);
    }
    @Test void lowerFuelCapAppliesOnlyAfterAirAndDoesNotRefund() {
        var f=new TrainTestFixture(); var car=f.car(StockKind.LOCOMOTIVE,false);
        car.setActiveFuel("minetorio:solid_fuel",new BigDecimal("0.25")); car.fuel().get().insert(0,"minetorio:coal",1,false);
        var t=f.train(50,1.26,Mode.MANUAL,car); step(f.state,t.id(),GO,0);
        // Запрос ограничен прежним cap; после смены топлива потраченная энергия не возвращается.
        double request=(1.26/0.99625-1.25975)*2000000-0.05;
        assertEquals(request,4000000.25-car.remainingJ().doubleValue(),1e-8);
        assertEquals(1.2,f.state.trains().get(t.id()).speed(),1e-12);
    }

    private static Train automatic(TrainTestFixture fixture, RailwayState.Car car, double coordinate,
                                   double speed, Status status) {
        var head = fixture.state.locate(fixture.tape, coordinate);
        return fixture.state.createTrain(new Train(UUID.randomUUID(), List.of(car.id()), Optional.of(head.cursor()),
                head.index(), fixture.tape, speed, Mode.AUTOMATIC, status, 0, 0, 0));
    }
    @Test void automaticIdlePreservesReasonAndRevisionsOnAvailablePath() {
        var fixture = new TrainTestFixture();
        var car = fixture.car(StockKind.LOCOMOTIVE, false);
        var train = automatic(fixture, car, 50, 0, Status.NO_SCHEDULE);
        step(fixture.state, train.id(), new Control(0, false, Turn.STRAIGHT), 0);
        assertEquals(train, fixture.state.trains().get(train.id()));
    }
    @Test void automaticEmptyFuelReportsFuelInsteadOfMissingPath() {
        var fixture = new TrainTestFixture();
        var car = fixture.car(StockKind.LOCOMOTIVE, false);
        var train = automatic(fixture, car, 50, 0, Status.NO_SCHEDULE);
        var before = carPoses(fixture.state, train.id());
        step(fixture.state, train.id(), GO, 0);
        var stopped = fixture.state.trains().get(train.id());
        assertEquals(Status.NO_FUEL, stopped.status());
        assertEquals(0, stopped.speed());
        assertEquals(before, carPoses(fixture.state, train.id()));
        car.fuel().orElseThrow().insert(0, "minetorio:coal", 1, false);
        step(fixture.state, train.id(), GO, 0);
        assertEquals(Status.ON_THE_PATH, fixture.state.trains().get(train.id()).status());
        assertTrue(fixture.state.trains().get(train.id()).speed() > 0);
    }
    @Test void automaticBrakeToZeroPreservesExistingReasonOnAvailablePath() {
        for (double sign : new double[]{-1, 1}) for (boolean explicitBrake : new boolean[]{false, true}) {
            var fixture = new TrainTestFixture();
            var car = fixture.car(StockKind.LOCOMOTIVE, false);
            var train = automatic(fixture, car, 50, sign * 0.003, Status.WAIT_SIGNAL);
            var control = new Control(explicitBrake ? sign : -sign, explicitBrake, Turn.STRAIGHT);
            step(fixture.state, train.id(), control, 0);
            var stopped = fixture.state.trains().get(train.id());
            assertEquals(0, stopped.speed());
            assertEquals(Status.WAIT_SIGNAL, stopped.status());
            assertEquals(0, car.remainingJ().signum());
        }
    }
    @Test void automaticActualTrackEndReportsMissingPathInBothDirections() {
        for (double sign : new double[]{-1, 1}) for (double gap : new double[]{0, 0.01}) {
            var fixture = new TrainTestFixture();
            var car = fixture.car(StockKind.LOCOMOTIVE, sign < 0);
            car.setActiveFuel("minetorio:coal", new BigDecimal("100000"));
            var train = automatic(fixture, car, sign > 0 ? 1196.5 - gap : 3.5 + gap,
                    sign * 0.1, Status.ON_THE_PATH);
            step(fixture.state, train.id(), new Control(sign, false, Turn.STRAIGHT), 0);
            var stopped = fixture.state.trains().get(train.id());
            assertEquals(0, stopped.speed());
            assertEquals(Status.NO_PATH, stopped.status());
            assertEquals(sign > 0 ? -1196.5 : -3.5,
                    carPoses(fixture.state, train.id()).get(car.id()).position().z(), 1e-9);
        }
    }
}
