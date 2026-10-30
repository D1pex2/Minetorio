package dev.minetorio.core.rail;
import java.util.*;
import static dev.minetorio.core.rail.RailGeometry.*;
import java.math.BigDecimal;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minetorio.core.data.RailData.StockKind;
import static dev.minetorio.core.rail.RailwayState.*;
import static dev.minetorio.core.rail.TrainMotion.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TrainMotionTest {
    static final Control GO = new Control(1,false,Turn.STRAIGHT);
    @Test void manualDriverContextMatchesNativeCoastAndAbsentBrakeStates() {
        var f=new TrainTestFixture();var car=f.car(StockKind.LOCOMOTIVE,false);var train=f.train(50,.45509709775705626,Mode.MANUAL,car);
        step(f.state,train.id(),new Control(0,false,Turn.STRAIGHT,DriverContext.SEATED),0);
        assertEquals(.4531414211404673,f.state.trains().get(train.id()).speed(),1e-14);assertEquals(Status.MANUAL_CONTROL,f.state.trains().get(train.id()).status());
        var coast=f.state.trains().get(train.id()).speed();step(f.state,train.id(),new Control(0,true,Turn.STRAIGHT,DriverContext.ABSENT),0);
        assertEquals(coast-.005,f.state.trains().get(train.id()).speed(),1e-14);assertEquals(Status.MANUAL_CONTROL_STOP,f.state.trains().get(train.id()).status());
        for(int n=0;n<100;n++)step(f.state,train.id(),new Control(0,true,Turn.STRAIGHT,DriverContext.ABSENT),0);
        assertEquals(0,f.state.trains().get(train.id()).speed());assertEquals(Status.MANUAL_CONTROL,f.state.trains().get(train.id()).status());
        step(f.state,train.id(),new Control(1,false,Turn.STRAIGHT,DriverContext.SEATED),0);assertEquals(0,f.state.trains().get(train.id()).speed());assertEquals(Status.MANUAL_CONTROL,f.state.trains().get(train.id()).status());
        assertEquals(DriverContext.SEATED,GO.driver());assertThrows(NullPointerException.class,()->new Control(0,false,Turn.STRAIGHT,null));
    }
    @Test void terminalProposalRetainsContactSpeedButCannotRaiseFinalSpeedOrReuseFuel() {
        var f=new TrainTestFixture();var car=f.car(StockKind.LOCOMOTIVE,false);car.setActiveFuel("minetorio:coal",new BigDecimal("100000"));var train=f.train(1196.49,.1,Mode.MANUAL,car);
        var proposal=propose(f.state,train.id(),GO,0).orElseThrow();assertTrue(proposal.distance()>0);assertEquals(0,proposal.speed());assertTrue(proposal.contactSpeed()>.1);
        assertThrows(IllegalArgumentException.class,()->proposal.commit(1,proposal.contactSpeed()));assertEquals(train,f.state.trains().get(train.id()));assertEquals(new BigDecimal("100000"),car.remainingJ());
        assertTrue(proposal.commit(1,0));assertEquals(0,f.state.trains().get(train.id()).speed());assertEquals(0,new BigDecimal("90000").compareTo(car.remainingJ()));assertFalse(proposal.commit(1,0));assertEquals(0,new BigDecimal("90000").compareTo(car.remainingJ()));
    }
    @Test void coalFirstThreeSubstepsAndSixtyFullBudgets() {
        var f=new TrainTestFixture(); var c=f.car(StockKind.LOCOMOTIVE,false); c.fuel().get().insert(0,"minetorio:coal",1,false);
        var t=f.train(50,0,Mode.MANUAL,c);
        for (double expected : new double[]{0.00498125,0.0096947578125,0.014390589970703125}) {
            step(f.state,t.id(),GO,0); assertEquals(expected,f.state.trains().get(t.id()).speed(),1e-14);
        }
        for (int i=3;i<60;i++) step(f.state,t.id(),GO,0);
        assertEquals(0,c.remainingJ().compareTo(new BigDecimal("3400000")));
    }
    private static JsonObject nativePhysics() {
        try(var input=TrainMotionTest.class.getResourceAsStream("/factorio-1.1.110/train-physics-native.json")) {
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(input),StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(java.io.IOException error) { throw new AssertionError(error); }
    }
    @Test void nativeCoalAtCapConsumesFullBudgetForSixHundredSteps() {
        var trace=nativePhysics().getAsJsonArray("steady_cap");
        var first=trace.get(0).getAsJsonObject();var last=trace.get(trace.size()-1).getAsJsonObject();
        double nativePerStep=(first.get("fuel").getAsDouble()-last.get("fuel").getAsDouble())/(last.get("tick").getAsInt()-first.get("tick").getAsInt());
        assertEquals(10000,nativePerStep,1e-9);
        for(var element:trace) {
            var sample=element.getAsJsonObject();assertEquals(1.2,sample.get("speed").getAsDouble());
            for(String property:List.of("heat","energy","coal")) assertEquals(first.get(property),sample.get(property));
            assertTrue(sample.get("has_path").getAsBoolean());assertEquals(0,sample.get("state").getAsInt());
            assertEquals("coal",sample.get("burning").getAsString());
        }
        for(Mode mode:Mode.values()) {
            var f=new TrainTestFixture();var c=f.car(StockKind.LOCOMOTIVE,false);
            c.setActiveFuel("minetorio:coal",BigDecimal.valueOf(4000000));c.fuel().orElseThrow().insert(0,"minetorio:coal",1,false);
            var t=f.train(50,1.2,mode,c);
            for(int i=0;i<600;i++) {
                if(mode==Mode.AUTOMATIC) TrainReservationsTest.accept(f,t.id(),1,0);
                step(f.state,t.id(),GO,0);assertEquals(1.2,f.state.trains().get(t.id()).speed(),1e-12);
            }
            assertEquals(600*nativePerStep,8000000-c.remainingJ().doubleValue(),1e-6,mode.toString());
        }
    }
    @Test void nativeFirstPoweredSpeedsAndBrakeStepRetainTheirMeasuredValues() {
        var trace=nativePhysics();var f=new TrainTestFixture();var c=f.car(StockKind.LOCOMOTIVE,false);
        c.fuel().orElseThrow().insert(0,"minetorio:coal",1,false);var t=f.train(50,0,Mode.MANUAL,c);
        var first=trace.getAsJsonArray("first_powered");
        for(int i=1;i<first.size();i++) {
            step(f.state,t.id(),GO,0);assertEquals(first.get(i).getAsJsonObject().get("speed").getAsDouble(),f.state.trains().get(t.id()).speed(),1e-14);
        }
        var brake=trace.getAsJsonArray("brake");var before=brake.get(0).getAsJsonObject();var after=brake.get(1).getAsJsonObject();
        for(String property:List.of("fuel","heat","energy","coal")) assertEquals(before.get(property),after.get(property));
        var bf=new TrainTestFixture();var bc=bf.car(StockKind.LOCOMOTIVE,false);bc.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
        var bt=bf.train(50,before.get("speed").getAsDouble(),Mode.MANUAL,bc);
        step(bf.state,bt.id(),new Control(1,true,Turn.STRAIGHT),0);
        assertEquals(after.get("speed").getAsDouble(),bf.state.trains().get(bt.id()).speed(),1e-12);
        assertEquals(0,bc.remainingJ().compareTo(BigDecimal.valueOf(100000)));
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
            if(mode==Mode.AUTOMATIC) TrainReservationsTest.accept(f,t.id(),-1,0);
            step(f.state,t.id(),new Control(-1,false,Turn.STRAIGHT),0);
            assertEquals(mode==Mode.MANUAL?-0.003*0.99625:0,f.state.trains().get(t.id()).speed(),1e-14);
            assertEquals(mode==Mode.MANUAL?90000:100000,car.remainingJ().doubleValue());
        }
    }
    @Test void opposedLocomotiveHelpsOnlyAllowedDirectionAndAllLocosContributeToCap() {
        var f=new TrainTestFixture(); var a=f.car(StockKind.LOCOMOTIVE,false); var b=f.car(StockKind.LOCOMOTIVE,true);
        a.setActiveFuel("minetorio:coal",new BigDecimal("100000")); b.setActiveFuel("minetorio:rocket_fuel",new BigDecimal("100000"));
        assertEquals(1.29,speedCap(List.of(a,b),Map.of()),1e-14);
        var t=f.train(50,0,Mode.AUTOMATIC,a,b); TrainReservationsTest.accept(f,t.id(),1,0); step(f.state,t.id(),GO,0);
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

    @Test void fullBudgetsUseEachLocomotivesOrderedFuelPortions() {
        var f=new TrainTestFixture(); var a=f.car(StockKind.LOCOMOTIVE,false); var b=f.car(StockKind.LOCOMOTIVE,false);
        a.setActiveFuel("minetorio:coal",new BigDecimal("5000")); a.fuel().get().insert(0,"minetorio:solid_fuel",1,false);
        b.setActiveFuel("minetorio:coal",new BigDecimal("100000"));
        var t=f.train(50,1.2,Mode.MANUAL,a,b);
        // Модель mixed-fuel еще не измерена нативно: два полных бюджета по10000J.
        // Первый дает5+6 единиц силы, второй10. Новый cap1.23 применяется после air.
        double request=10000;
        step(f.state,t.id(),GO,0);
        double drawA=5000+12000000-a.remainingJ().doubleValue(), drawB=100000-b.remainingJ().doubleValue();
        assertEquals(request,drawA,1e-8); assertEquals(request,drawB,1e-8);
        assertEquals((1.19975+21.0/4000)*0.998125,f.state.trains().get(t.id()).speed(),1e-12);
        assertEquals(1.23,speedCap(List.of(a,b),Map.of()),1e-12);
    }
    @Test void lowerFuelCapAppliesOnlyAfterAirAndDoesNotRefund() {
        var f=new TrainTestFixture(); var car=f.car(StockKind.LOCOMOTIVE,false);
        car.setActiveFuel("minetorio:solid_fuel",new BigDecimal("0.25")); car.fuel().get().insert(0,"minetorio:coal",1,false);
        var t=f.train(50,1.26,Mode.MANUAL,car); step(f.state,t.id(),GO,0);
        // При смене топлива cap меняется после air, полный бюджет не возвращается.
        double request=10000;
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
        TrainReservationsTest.accept(fixture,train.id(),1,0);
        step(fixture.state, train.id(), GO, 0);
        var stopped = fixture.state.trains().get(train.id());
        assertEquals(Status.NO_FUEL, stopped.status());
        assertEquals(0, stopped.speed());
        assertEquals(before, carPoses(fixture.state, train.id()));
        car.fuel().orElseThrow().insert(0, "minetorio:coal", 1, false);
        TrainReservationsTest.accept(fixture,train.id(),1,0);
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
            TrainReservationsTest.accept(fixture,train.id(),(int)sign,0);
            step(fixture.state, train.id(), control, 0);
            var stopped = fixture.state.trains().get(train.id());
            assertEquals(0, stopped.speed());
            assertEquals(Status.WAIT_SIGNAL, stopped.status());
            assertEquals(0, car.remainingJ().signum());
        }
    }
    @Test void automaticUnsafeInitialTrackEndWithoutAuthorityCannotMoveOrEraseSpeed() {
        for (double sign : new double[]{-1, 1}) for (double gap : new double[]{0, 0.01}) {
            var fixture = new TrainTestFixture();
            var car = fixture.car(StockKind.LOCOMOTIVE, sign < 0);
            car.setActiveFuel("minetorio:coal", new BigDecimal("100000"));
            var train = automatic(fixture, car, sign > 0 ? 1196.5 - gap : 3.5 + gap,
                    sign * 0.1, Status.ON_THE_PATH);
            step(fixture.state, train.id(), new Control(sign, false, Turn.STRAIGHT), 0);
            var stopped = fixture.state.trains().get(train.id());
            assertEquals(train,stopped);
            assertEquals(new BigDecimal("100000"),car.remainingJ());
            assertEquals(sign > 0 ? -1196.5 + gap : -3.5 - gap,
                    carPoses(fixture.state, train.id()).get(car.id()).position().z(), 1e-9);
        }
    }

    @Test void automaticSafeApproachToTrackEndUsesRealBrakingInBothDirections() {
        for(int sign:new int[]{-1,1}) {
            var f=new TrainTestFixture(); var c=f.car(StockKind.LOCOMOTIVE,sign<0);
            c.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
            var t=automatic(f,c,sign>0?1190:10,sign*0.1,Status.ARRIVE_SIGNAL);
            double expected=stoppingDistance(t,List.of(c),0),distance=0;
            while(f.state.trains().get(t.id()).speed()!=0) {
                TrainReservationsTest.accept(f,t.id(),sign,0);
                step(f.state,t.id(),new Control(sign,true,Turn.STRAIGHT),0);
                distance+=Math.abs(f.state.trains().get(t.id()).speed());
            }
            assertEquals(expected,distance,1e-9);
            assertTrue(distance<6.5); assertEquals(BigDecimal.valueOf(100000),c.remainingJ());
            assertEquals(Status.ON_THE_PATH,f.state.trains().get(t.id()).status());
        }
    }
}
