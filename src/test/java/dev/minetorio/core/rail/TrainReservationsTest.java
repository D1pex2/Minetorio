package dev.minetorio.core.rail;
import java.math.BigDecimal;
import java.util.*;
import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.RailSignals.*;
import static dev.minetorio.core.rail.RailSignalsTest.signal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.minetorio.core.data.RailData.StockKind.*;
import static dev.minetorio.core.rail.RailwayState.*;
class TrainReservationsTest {
    @Test void automaticWithoutAuthorityCannotMoveOrBurnFuel() {
        var f=new TrainTestFixture(); var car=f.car(LOCOMOTIVE,false);
        car.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
        var train=f.train(50,0,Mode.AUTOMATIC,car);
        TrainMotion.step(f.state,train.id(),new TrainMotion.Control(1,false,TrainMotion.Turn.STRAIGHT),0);
        assertEquals(train,f.state.trains().get(train.id()));
        assertEquals(BigDecimal.valueOf(100000),car.remainingJ());
    }

    static TrainReservations.Plan plan(TrainTestFixture f,UUID id,int sign,double bonus,double goal) {
        var leading=TrainReservations.leading(f.state,id,sign);
        var position=f.state.locate(f.tape,goal).cursor();
        if(sign<0) position=new Cursor(RailwayState.reverse(position.traversal()),f.state.railLength(position.traversal())-position.distance());
        if(position.traversal().equals(leading.start().traversal()) && Math.abs(position.distance()-leading.start().distance())<1e-10) position=leading.start();
        var route=RailRouter.route(f.state.graph(),leading.start(),Set.of(position),f.state.signals()::allowed).orElseThrow();
        return TrainReservations.plan(f.state,id,route,sign,bonus);
    }
    static void accept(TrainTestFixture f,UUID id,int sign,double bonus) {
        assertTrue(TrainReservations.commit(f.state,id,plan(f,id,sign,bonus,sign>0?1200:0),sign,bonus));
    }
    @Test void leadingUsesExternalCouplerAndStableAxisInBothDirections() {
        var f=new TrainTestFixture(); var t=f.train(20,0,Mode.AUTOMATIC,f.car(LOCOMOTIVE,false),f.car(CARGO,true));
        var forward=TrainReservations.leading(f.state,t.id(),1); var reverse=TrainReservations.leading(f.state,t.id(),-1);
        assertEquals(f.state.locate(f.tape,23.5).cursor(),forward.start());
        var expected=f.state.locate(f.tape,9.5);
        assertEquals(expected.index(),reverse.tapeIndex());
        assertEquals(new Cursor(RailwayState.reverse(expected.cursor().traversal()),2-expected.cursor().distance()),reverse.start());
        assertEquals(t,f.state.trains().get(t.id()));
    }
    @Test void wrongBonusDirectionAndForeignPlanNeverMutateClaims() {
        var f=new TrainTestFixture(); var t=f.train(20,0,Mode.AUTOMATIC,f.car(LOCOMOTIVE,false));
        var p=plan(f,t.id(),1,0,100);
        assertFalse(TrainReservations.commit(f.state,t.id(),p,1,0.1));
        assertFalse(TrainReservations.commit(f.state,t.id(),p,-1,0));
        for(double invalid:new double[]{-0.1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->TrainReservations.commit(f.state,t.id(),p,1,invalid));
        assertEquals(t,f.state.trains().get(t.id())); assertTrue(f.state.reservations().acceptedPlan(t.id()).isEmpty());
        assertThrows(IllegalArgumentException.class,()->TrainReservations.leading(f.state,t.id(),0));
    }
    @Test void successfulAuthorityIsConsumedOnceWithoutSecondFuelDraw() {
        var f=new TrainTestFixture(); var c=f.car(LOCOMOTIVE,false); c.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
        var t=f.train(20,0,Mode.AUTOMATIC,c); var p=plan(f,t.id(),1,0,100);
        assertTrue(TrainReservations.commit(f.state,t.id(),p,1,0));
        TrainMotion.step(f.state,t.id(),new TrainMotion.Control(1,false,TrainMotion.Turn.STRAIGHT),0);
        var moved=f.state.trains().get(t.id()); assertTrue(moved.speed()>0); assertEquals(90000,c.remainingJ().intValue());
        assertEquals(p.safeDistance()-moved.speed(),f.state.reservations().remainingDistance(t.id()),1e-9);
        assertFalse(TrainReservations.commit(f.state,t.id(),p,1,0));
        TrainMotion.step(f.state,t.id(),new TrainMotion.Control(1,false,TrainMotion.Turn.STRAIGHT),0);
        assertEquals(moved,f.state.trains().get(t.id())); assertEquals(90000,c.remainingJ().intValue());
    }
    @Test void lookaheadClaimsOnlyNecessaryBlocks() {
        var f=new TrainTestFixture(); f.state.replaceSignals(List.of(signal(f,40,Kind.REGULAR,true),signal(f,80,Kind.REGULAR,true),signal(f,120,Kind.REGULAR,true)));
        var a=f.train(20,0,Mode.AUTOMATIC,f.car(LOCOMOTIVE,false)); var p=plan(f,a.id(),1,0,1100);
        assertEquals(16.5,p.safeDistance(),1e-9); assertTrue(TrainReservations.commit(f.state,a.id(),p,1,0));
        var b=f.train(100,0,Mode.AUTOMATIC,f.car(LOCOMOTIVE,false));
        assertTrue(TrainReservations.commit(f.state,b.id(),plan(f,b.id(),1,0,1100),1,0));
        assertTrue(Collections.disjoint(p.blocks(),f.state.signals().blocks(f.tape.get(50))));
    }
    @Test void oppositeTrainsOneReservationWinsAndChangeIsAtomic() {
        var f=new TrainTestFixture(); f.state.replaceSignals(List.of(signal(f,40,Kind.REGULAR,true),signal(f,40,Kind.REGULAR,false),signal(f,80,Kind.REGULAR,true),signal(f,80,Kind.REGULAR,false)));
        var ac=f.car(LOCOMOTIVE,false); var bc=f.car(LOCOMOTIVE,true); ac.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));bc.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
        var a=f.train(36.499,0,Mode.AUTOMATIC,ac); var b=f.train(83.501,0,Mode.AUTOMATIC,bc);
        var ap=plan(f,a.id(),1,0,100); var bp=plan(f,b.id(),-1,0,20);
        assertTrue(TrainReservations.commit(f.state,a.id(),ap,1,0));
        assertFalse(TrainReservations.commit(f.state,b.id(),bp,-1,0));
        assertTrue(f.state.reservations().acceptedPlan(b.id()).isEmpty());
        assertTrue(f.state.reservations().occupants(f.state.signals().blocks(f.tape.get(50)).iterator().next()).contains(b.id()));
        assertEquals(ap,f.state.reservations().acceptedPlan(a.id()).orElseThrow());
    }
    @Test void chainWaitsForWholeTrainExit() {
        for(double available:new double[]{7,14-1e-6,14}) {
            var f=new TrainTestFixture(); f.state.replaceSignals(List.of(signal(f,40,Kind.CHAIN,true),signal(f,60,Kind.REGULAR,true)));
            var c=f.car(LOCOMOTIVE,false); c.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
            var t=f.train(36.499,0,Mode.AUTOMATIC,c,f.car(CARGO,false));
            var p=plan(f,t.id(),1,0,60+available);
            assertEquals(available>=14,p.exitFits());
            assertEquals(available>=14?60+available-39.999:0.001,p.safeDistance(),1e-8);
        }
    }
    @Test void bonusIsDerivedFromActualPhysicsForBothSigns() {
        for(int sign:new int[]{-1,1}) {
            var f=new TrainTestFixture(); var c=f.car(LOCOMOTIVE,sign<0); var t=f.train(500,sign*0.4,Mode.AUTOMATIC,c);
            var p=plan(f,t.id(),sign,0.1,sign>0?1000:0);
            assertEquals(TrainMotion.stoppingDistance(t,List.of(c),0.1),p.brakingDistance());
            assertFalse(TrainReservations.commit(f.state,t.id(),p,sign,0));
        }
    }
    @Test void signalEditsInvalidateTractionButKeepRealBrakingCorridor() {
        var f=new TrainTestFixture(); var c=f.car(LOCOMOTIVE,false); c.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
        var t=f.train(50,0.4,Mode.AUTOMATIC,c); var old=plan(f,t.id(),1,0,200);
        assertTrue(TrainReservations.commit(f.state,t.id(),old,1,0));
        long graph=f.state.graph().revision(); f.state.replaceSignals(List.of(signal(f,54,Kind.REGULAR,false)));
        assertEquals(graph,f.state.graph().revision()); assertFalse(TrainReservations.commit(f.state,t.id(),old,1,0));
        var competitor=f.train(150,0,Mode.AUTOMATIC,f.car(LOCOMOTIVE,true));
        var competing=plan(f,competitor.id(),-1,0,80); assertEquals(0,competing.safeDistance());
        double traveled=0,previous=0.4;
        for(int i=0;i<200 && f.state.trains().get(t.id()).speed()>0;i++) {
            TrainMotion.step(f.state,t.id(),new TrainMotion.Control(1,false,TrainMotion.Turn.STRAIGHT),0);
            double speed=f.state.trains().get(t.id()).speed(); assertTrue(speed<previous); traveled+=speed;previous=speed;
        }
        assertEquals(0,f.state.trains().get(t.id()).speed()); assertEquals(old.brakingDistance(),traveled,1e-9);
        assertEquals(BigDecimal.valueOf(100000),c.remainingJ());
    }
    @Test void invalidSignalReplacementAndNoOpAreAtomicAndCutUnderTailPreservesOccupancy() {
        var f=new TrainTestFixture(); var t=f.train(50,0,Mode.AUTOMATIC,f.car(LOCOMOTIVE,false),f.car(CARGO,false));
        accept(f,t.id(),1,0); long revision=f.state.signals().revision();
        assertThrows(IllegalArgumentException.class,()->f.state.replaceSignals(List.of(new Signal(UUID.randomUUID(),new Port(UUID.randomUUID(),End.A),true,Kind.REGULAR))));
        assertEquals(revision,f.state.signals().revision()); assertTrue(f.state.reservations().acceptedPlan(t.id()).isPresent());
        f.state.replaceSignals(List.of()); assertEquals(revision,f.state.signals().revision());
        f.state.replaceSignals(List.of(signal(f,46,Kind.REGULAR,true)));
        assertTrue(f.state.reservations().acceptedPlan(t.id()).isEmpty());
        for(int piece:new int[]{20,25}) assertTrue(f.state.signals().blocks(f.tape.get(piece)).stream().anyMatch(b->f.state.reservations().occupants(b).contains(t.id())));
    }
    @Test void zeroAndShortAuthorityNeverTurnBrakingIntoAnUnauthorizedMove() {
        for(double allowed:new double[]{0,0.01}) {
            var f=new TrainTestFixture(); var c=f.car(LOCOMOTIVE,false); c.setActiveFuel("minetorio:coal",BigDecimal.TEN);
            var t=f.train(50,0.4,Mode.AUTOMATIC,c); var p=plan(f,t.id(),1,0,53.5+allowed);
            assertTrue(TrainReservations.commit(f.state,t.id(),p,1,0));
            TrainMotion.step(f.state,t.id(),new TrainMotion.Control(1,true,TrainMotion.Turn.STRAIGHT),0);
            assertEquals(t,f.state.trains().get(t.id())); assertEquals(BigDecimal.TEN,c.remainingJ());
        }
    }

    @Test void chainAtExactLeadingBoundaryCannotBeSkipped() {
        var f=new TrainTestFixture(); f.state.replaceSignals(List.of(signal(f,40,Kind.CHAIN,true)));
        var c=f.car(LOCOMOTIVE,false); c.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
        var t=f.train(36.5,0,Mode.AUTOMATIC,c);
        var p=plan(f,t.id(),1,0,100);
        assertFalse(p.exitFits()); assertEquals(0,p.safeDistance());
    }
    @Test void reverseLeadingConsumesExactlyOneTileWithoutChangingYaw() {
        var f=new TrainTestFixture(); var c=f.car(LOCOMOTIVE,false); var b=f.car(CARGO,true);
        var t=f.train(20,-1.004,Mode.AUTOMATIC,c,b); var poses=TrainMotion.carPoses(f.state,t.id());
        var p=plan(f,t.id(),-1,0,8.5); assertEquals(1,p.safeDistance());
        // Отдельная проверка owner commit: предлагаемый шаг останавливается на границе authority.
        assertTrue(TrainReservations.commit(f.state,t.id(),p,-1,0));
        var position=f.state.locate(f.tape,19);
        assertTrue(f.state.commitMotion(t,f.state.graph().revision(),new MotionUpdate(Optional.of(position.cursor()),position.index(),f.tape,0,t.status()),-1,1,0,true,()->{}));
        assertEquals(0,f.state.reservations().remainingDistance(t.id()));
        assertEquals(19,f.state.headCoordinate(f.state.trains().get(t.id())));
        poses.forEach((id,pose)->assertEquals(pose.tangent(),TrainMotion.carPoses(f.state,t.id()).get(id).tangent()));
    }
    @Test void actualReverseBrakingAndExactStopBoundaries() {
        for(int sign:new int[]{-1,1}) for(double epsilon:new double[]{-1e-6,0,1e-6}) {
            var f=new TrainTestFixture(); var c=f.car(LOCOMOTIVE,sign<0); var b=f.car(CARGO,false);
            c.setActiveFuel("minetorio:coal",BigDecimal.ONE);
            var t=f.train(500,sign*0.1,Mode.AUTOMATIC,c,b); double stop=TrainMotion.stoppingDistance(t,List.of(c,b),0);
            double nose=sign>0?503.5:489.5;
            var p=plan(f,t.id(),sign,0,nose+sign*(stop+epsilon)); assertTrue(TrainReservations.commit(f.state,t.id(),p,sign,0));
            TrainMotion.step(f.state,t.id(),new TrainMotion.Control(sign,true,TrainMotion.Turn.STRAIGHT),0);
            assertEquals(epsilon<0,t.equals(f.state.trains().get(t.id())));
            assertEquals(BigDecimal.ONE,c.remainingJ());
        }
    }
    @Test void wholeTrainExitLengthInReverseIsFourteenNotCenterSpan() {
        for(double available:new double[]{7,14-1e-6,14}) {
            var f=new TrainTestFixture(); f.state.replaceSignals(List.of(signal(f,80,Kind.CHAIN,false),signal(f,60,Kind.REGULAR,false)));
            var c=f.car(LOCOMOTIVE,true); c.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
            var t=f.train(90.501,0,Mode.AUTOMATIC,c,f.car(CARGO,false));
            var p=plan(f,t.id(),-1,0,60-available);
            assertEquals(available>=14,p.exitFits());
        }
    }
    @Test void plansFromAnotherOwnerOrAfterSplitCannotBeCommitted() {
        var f=new TrainTestFixture(); var a=f.car(LOCOMOTIVE,false); var b=f.car(CARGO,false);
        var t=f.train(50,0,Mode.AUTOMATIC,a,b); var p=plan(f,t.id(),1,0,100);
        var other=new RailwayState(k->100); other.replaceRails(f.state.rails());
        other.createCar(a.id(),a.kind(),a.reversed()); other.createCar(b.id(),b.kind(),b.reversed()); other.createTrain(t);
        assertFalse(TrainReservations.commit(other,t.id(),p,1,0));
        TrainCoupling.split(f.state,a.id(),TrainCoupling.End.BACK).orElseThrow();
        assertFalse(TrainReservations.commit(f.state,t.id(),p,1,0));
        assertTrue(f.state.reservations().occupants(f.state.signals().blocks(f.tape.get(25)).iterator().next()).contains(t.id()));
    }
    @Test void consumerRejectsDistanceBeyondAuthorityBeforeFuelCallback() {
        var f=new TrainTestFixture(); var t=f.train(50,0,Mode.AUTOMATIC,f.car(LOCOMOTIVE,false));
        var p=plan(f,t.id(),1,0,54.5); assertTrue(TrainReservations.commit(f.state,t.id(),p,1,0));
        var cursor=f.state.locate(f.tape,51.000001);
        assertFalse(f.state.commitMotion(t,f.state.graph().revision(),new MotionUpdate(Optional.of(cursor.cursor()),cursor.index(),f.tape,0,t.status()),
                1,1.000001,0,true,()->fail("Расход до проверки authority")));
        assertEquals(t,f.state.trains().get(t.id())); assertEquals(1,f.state.reservations().remainingDistance(t.id()));
    }
    @Test void repeatedRouteOccurrenceCannotTeleportAcrossAWholeLoop() {
        var state=new RailwayState(k->100); state.replaceRails(TrainTestFixture.loop()); var tape=TrainTestFixture.loopTape(state,3);
        var c=state.createCar(UUID.randomUUID(),LOCOMOTIVE,false); var head=state.locate(tape,30);
        var t=state.createTrain(new Train(UUID.randomUUID(),List.of(c.id()),Optional.of(head.cursor()),head.index(),tape,0,Mode.AUTOMATIC,Status.WAIT_SIGNAL,0,0,0));
        var leading=TrainReservations.leading(state,t.id(),1); var route=tape.subList(leading.tapeIndex(),leading.tapeIndex()+14);
        var p=TrainReservations.plan(state,t.id(),new RailRouter.Route(route,route.stream().mapToDouble(state::railLength).sum()-leading.start().distance(),state.graph().revision()),1,0);
        assertTrue(TrainReservations.commit(state,t.id(),p,1,0));
        var teleport=new MotionUpdate(t.head(),t.headIndex()+12,t.tape(),0,t.status());
        assertFalse(state.commitMotion(t,state.graph().revision(),teleport,1,0,0,true,()->fail("Телепорт списал топливо")));
        assertEquals(t,state.trains().get(t.id()));
    }

    @Test void everySignalOnlyEditInvalidatesPlanAtUnchangedGraphRevision() {
        for(int edit=0;edit<5;edit++) {
            var f=new TrainTestFixture(); var original=signal(f,40,Kind.REGULAR,true); f.state.replaceSignals(List.of(original));
            var t=f.train(20,0,Mode.AUTOMATIC,f.car(LOCOMOTIVE,false)); var p=plan(f,t.id(),1,0,100);
            long graph=f.state.graph().revision(),network=f.state.signals().revision();
            List<Signal> next=switch(edit) {
                case 0 -> List.of();
                case 1 -> List.of(original,signal(f,80,Kind.REGULAR,true));
                case 2 -> List.of(new Signal(original.id(),original.boundary(),false,original.kind()));
                case 3 -> List.of(new Signal(original.id(),original.boundary(),true,Kind.CHAIN));
                default -> List.of(new Signal(original.id(),new Port(f.tape.get(30).piece(),End.A),true,original.kind()));
            };
            f.state.replaceSignals(next);
            assertEquals(graph,f.state.graph().revision());assertEquals(network+1,f.state.signals().revision());
            assertFalse(TrainReservations.commit(f.state,t.id(),p,1,0));
        }
    }
    @Test void actualCoastOrTinyFuelCannotUseUpTheBrakingReserve() {
        for(int sign:new int[]{-1,1}) {
            var f=new TrainTestFixture(); var c=f.car(LOCOMOTIVE,sign<0); c.setActiveFuel("minetorio:coal",BigDecimal.ONE);
            var t=f.train(500,sign*0.1,Mode.AUTOMATIC,c); double stop=TrainMotion.stoppingDistance(t,List.of(c),0);
            double goal=500+sign*(3.5+stop),distance=0;
            for(int i=0;i<100 && f.state.trains().get(t.id()).speed()!=0;i++) {
                var p=plan(f,t.id(),sign,0,goal); assertTrue(TrainReservations.commit(f.state,t.id(),p,sign,0));
                TrainMotion.step(f.state,t.id(),new TrainMotion.Control(sign,false,TrainMotion.Turn.STRAIGHT),0);
                distance+=Math.abs(f.state.trains().get(t.id()).speed());
            }
            assertEquals(0,f.state.trains().get(t.id()).speed()); assertEquals(stop,distance,1e-9);
            assertEquals(BigDecimal.ONE,c.remainingJ());
        }
    }
    @Test void fullLoopRouteConsumesOccurrencesInsteadOfShortcutByEqualCursor() {
        var state=new RailwayState(k->100); state.replaceRails(TrainTestFixture.loop()); var tape=TrainTestFixture.loopTape(state,3);
        var c=state.createCar(UUID.randomUUID(),LOCOMOTIVE,false); var head=state.locate(tape,30);
        var t=state.createTrain(new Train(UUID.randomUUID(),List.of(c.id()),Optional.of(head.cursor()),head.index(),tape,0,Mode.AUTOMATIC,Status.WAIT_SIGNAL,0,0,0));
        var leading=TrainReservations.leading(state,t.id(),1); var route=tape.subList(leading.tapeIndex(),leading.tapeIndex()+14);
        double lap=tape.subList(0,12).stream().mapToDouble(state::railLength).sum();
        var p=TrainReservations.plan(state,t.id(),new RailRouter.Route(route,route.stream().mapToDouble(state::railLength).sum()-leading.start().distance(),state.graph().revision()),1,0);
        assertTrue(TrainReservations.commit(state,t.id(),p,1,0));
        assertTrue(state.commitMotion(t,state.graph().revision(),new MotionUpdate(t.head(),t.headIndex()+12,t.tape(),0,t.status()),1,lap,0,true,()->{}));
        assertEquals(p.safeDistance()-lap,state.reservations().remainingDistance(t.id()),1e-8);
        assertFalse(TrainReservations.commit(state,t.id(),p,1,0));
    }

    @Test void releaseAfterSignalEditKeepsOnlyBrakingCorridorUntilRealStop() {
        var f=new TrainTestFixture(); var c=f.car(LOCOMOTIVE,false); c.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
        var t=f.train(50,0.4,Mode.AUTOMATIC,c); var p=plan(f,t.id(),1,0,200);
        assertTrue(TrainReservations.commit(f.state,t.id(),p,1,0));
        f.state.replaceSignals(List.of(signal(f,54,Kind.REGULAR,false),signal(f,100,Kind.REGULAR,false)));
        TrainReservations.releaseFuture(f.state,t.id());
        assertEquals(p.brakingDistance(),f.state.reservations().remainingDistance(t.id()),1e-9);
        assertTrue(f.state.signals().blocks(f.tape.get(30)).stream().anyMatch(b->f.state.reservations().claimants(b).contains(t.id())));
        assertTrue(f.state.signals().blocks(f.tape.get(70)).stream().allMatch(b->!f.state.reservations().claimants(b).contains(t.id())));
        double previous=t.speed();
        for(int i=0;i<200 && previous>0;i++) {
            TrainMotion.step(f.state,t.id(),new TrainMotion.Control(1,false,TrainMotion.Turn.STRAIGHT),0);
            double speed=f.state.trains().get(t.id()).speed();assertTrue(speed<previous);previous=speed;
        }
        assertEquals(0,previous); assertEquals(BigDecimal.valueOf(100000),c.remainingJ());
        assertTrue(f.state.signals().blocks(f.tape.get(30)).stream().allMatch(b->!f.state.reservations().claimants(b).contains(t.id())));
        assertTrue(f.state.signals().blocks(f.tape.get(30)).stream().anyMatch(b->f.state.reservations().occupants(b).contains(t.id())));
    }

    @Test void reservationChangeIsAtomicWhenPreviousAuthorityAlreadyExists() {
        var f=new TrainTestFixture(); f.state.replaceSignals(List.of(signal(f,40,Kind.REGULAR,true),signal(f,40,Kind.REGULAR,false),signal(f,80,Kind.REGULAR,true),signal(f,80,Kind.REGULAR,false)));
        var ac=f.car(LOCOMOTIVE,false);var bc=f.car(LOCOMOTIVE,true);
        ac.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));bc.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
        var a=f.train(36.499,0,Mode.AUTOMATIC,ac); var b=f.train(83.501,0,Mode.AUTOMATIC,bc);
        var old=plan(f,b.id(),-1,0,80.0005);assertTrue(TrainReservations.commit(f.state,b.id(),old,-1,0));
        var replacement=plan(f,b.id(),-1,0,20); var competing=plan(f,a.id(),1,0,100);
        assertFalse(TrainReservations.commit(f.state,b.id(),competing,-1,0));
        assertTrue(TrainReservations.commit(f.state,a.id(),competing,1,0));
        assertFalse(TrainReservations.commit(f.state,b.id(),replacement,-1,0));
        assertEquals(old,f.state.reservations().acceptedPlan(b.id()).orElseThrow());
        for(UUID block:old.blocks()) assertTrue(f.state.reservations().claimants(block).contains(b.id()));
        assertTrue(f.state.signals().blocks(f.tape.get(41)).stream().anyMatch(block->f.state.reservations().occupants(block).contains(b.id())));
    }
    @Test void consumerRejectsAnotherBranchBeforeFuelOrPartialMovement() {
        var f=new TrainTestFixture(); var rails=new ArrayList<>(f.state.rails());
        var curve=new Piece(new UUID(90,1),new Point(0,0,-60),Shape.CURVE,0,false);rails.add(curve);f.state.replaceRails(rails);
        var t=f.train(56.499,0,Mode.AUTOMATIC,f.car(LOCOMOTIVE,false));
        var p=plan(f,t.id(),1,0,100);assertTrue(TrainReservations.commit(f.state,t.id(),p,1,0));
        var otherTape=new ArrayList<>(f.tape.subList(0,30));otherTape.add(new Traversal(curve.id(),true));
        var head=f.state.locate(otherTape,56.509);
        assertFalse(f.state.commitMotion(t,f.state.graph().revision(),new MotionUpdate(Optional.of(head.cursor()),head.index(),otherTape,0,t.status()),1,0.01,0,true,()->fail("Иная ветка списала топливо")));
        assertEquals(t,f.state.trains().get(t.id()));assertEquals(p.safeDistance(),f.state.reservations().remainingDistance(t.id()));
    }

    @Test void signalEditBrakesAlongConfirmedBranchAndPreservesOccupiedTape() {
        for(int sign:new int[]{-1,1}) for(boolean edit:new boolean[]{false,true}) {
            var f=new TrainTestFixture();var rails=new ArrayList<>(f.state.rails());
            var curve=new Piece(new UUID(91,1),new Point(0,0,-60),Shape.CURVE,0,false); rails.add(curve);f.state.replaceRails(rails);
            var c=f.car(LOCOMOTIVE,sign<0);c.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
            var tape=new ArrayList<>(f.tape);
            if(sign<0) { Collections.reverse(tape);tape.replaceAll(RailwayState::reverse); }
            var head=f.state.locate(tape,sign>0?56.499:1200-56.499);
            var t=f.state.createTrain(new Train(UUID.randomUUID(),List.of(c.id()),Optional.of(head.cursor()),head.index(),tape,edit?sign*0.1:0,Mode.AUTOMATIC,Status.ON_THE_PATH,0,0,0));
            var start=TrainReservations.leading(f.state,t.id(),sign);
            var route=RailRouter.route(f.state.graph(),start.start(),Set.of(new Cursor(new Traversal(curve.id(),true),length(curve))),f.state.signals()::allowed).orElseThrow();
            var p=TrainReservations.plan(f.state,t.id(),route,sign,0); assertTrue(TrainReservations.commit(f.state,t.id(),p,sign,0));
            if(edit) f.state.replaceSignals(List.of(signal(f,80,Kind.REGULAR,true)));
            double moved=0,previous=Math.abs(t.speed());
            for(int i=0;i<(edit?100:1);i++) {
                TrainMotion.step(f.state,t.id(),new TrainMotion.Control(sign,false,TrainMotion.Turn.STRAIGHT),0);
                var current=f.state.trains().get(t.id()); double speed=Math.abs(current.speed());
                if(edit) assertTrue(speed<previous,"sign="+sign+", шаг="+i); else assertTrue(speed>0,"sign="+sign);
                previous=speed;moved+=speed; if(speed==0) break;
            }
            if(edit) { assertEquals(0,previous);assertEquals(p.brakingDistance(),moved,1e-9); }
            assertEquals(curve.id(),TrainReservations.leading(f.state,t.id(),sign).start().traversal().piece());
            assertEquals(0,BigDecimal.valueOf(edit?100000:90000).compareTo(c.remainingJ()));
        }
    }

    @Test void automaticReverseKeepsTheRealOldBranchUnderTheBody() {
        var state=new RailwayState(k->100);List<Piece> rails=new ArrayList<>();List<Traversal> tape=new ArrayList<>();
        for(int z=20;z>=2;z-=2) { var p=new Piece(new UUID(92,z),new Point(0,0,z),Shape.STRAIGHT,0,false);rails.add(p);tape.add(new Traversal(p.id(),true)); }
        var curve=new Piece(new UUID(92,30),new Point(0,0,0),Shape.CURVE,0,false);
        var other=new Piece(new UUID(92,31),new Point(0,0,0),Shape.STRAIGHT,0,false);rails.add(curve);rails.add(other);tape.add(new Traversal(curve.id(),true));state.replaceRails(rails);
        var a=state.createCar(UUID.randomUUID(),LOCOMOTIVE,false);var b=state.createCar(UUID.randomUUID(),CARGO,true);var head=state.locate(tape,24);
        var t=state.createTrain(new Train(UUID.randomUUID(),List.of(a.id(),b.id()),Optional.of(head.cursor()),head.index(),tape,-0.1,Mode.AUTOMATIC,Status.ON_THE_PATH,0,0,0));
        var start=TrainReservations.leading(state,t.id(),-1);var route=RailRouter.route(state.graph(),start.start(),Set.of(new Cursor(RailwayState.reverse(tape.getFirst()),2)),state.signals()::allowed).orElseThrow();
        assertTrue(TrainReservations.commit(state,t.id(),TrainReservations.plan(state,t.id(),route,-1,0),-1,0));
        TrainMotion.step(state,t.id(),new TrainMotion.Control(-1,true,TrainMotion.Turn.LEFT),0);
        var current=state.trains().get(t.id());assertTrue(current.speed()>-0.1 && current.speed()<0);
        var poses=TrainMotion.carPoses(state,t.id());double q=24+current.speed();
        assertTrue(RailwayState.near(state.pose(tape,q,false).position(),poses.get(a.id()).position()));
        assertTrue(RailwayState.near(state.pose(tape,q-7,true).position(),poses.get(b.id()).position()));
        assertTrue(current.tape().stream().anyMatch(step->step.piece().equals(curve.id())));
        assertFalse(current.tape().stream().anyMatch(step->step.piece().equals(other.id())));
    }

    @Test void shortReplanCannotReleaseFreshNormalStoppingPrefix() { shortReplanPreservesStoppingPrefix(false); }
    @Test void shortReplanCannotReleaseConsumedNormalStoppingPrefix() { shortReplanPreservesStoppingPrefix(true); }
    private void shortReplanPreservesStoppingPrefix(boolean consumeFirst) {
        var f=new TrainTestFixture();f.state.replaceSignals(List.of(signal(f,54,Kind.REGULAR,true),signal(f,54,Kind.REGULAR,false),signal(f,80,Kind.REGULAR,true),signal(f,80,Kind.REGULAR,false)));
        var ac=f.car(LOCOMOTIVE,false);var bc=f.car(LOCOMOTIVE,true);ac.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));bc.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
        var a=f.train(50,0.4,Mode.AUTOMATIC,ac);var b=f.train(83.501,0,Mode.AUTOMATIC,bc);
        assertTrue(TrainReservations.commit(f.state,a.id(),plan(f,a.id(),1,0,100),1,0));
        if(consumeFirst) TrainMotion.step(f.state,a.id(),new TrainMotion.Control(1,false,TrainMotion.Turn.STRAIGHT),0);
        var current=f.state.trains().get(a.id());double remaining=f.state.reservations().remainingDistance(a.id());
        double expected=TrainMotion.stoppingDistance(current,List.of(ac),0);BigDecimal fuel=ac.remainingJ();
        var leading=TrainReservations.leading(f.state,a.id(),1);
        var route=RailRouter.route(f.state.graph(),leading.start(),Set.of(leading.start()),f.state.signals()::allowed).orElseThrow();
        var shortPlan=TrainReservations.plan(f.state,a.id(),route,1,0);
        assertEquals(0,shortPlan.safeDistance());assertFalse(TrainReservations.commit(f.state,a.id(),shortPlan,1,0));
        assertEquals(remaining,f.state.reservations().remainingDistance(a.id()));assertEquals(current,f.state.trains().get(a.id()));
        var opposing=plan(f,b.id(),-1,0,20);assertTrue(opposing.safeDistance()<=0.001+1e-9);
        assertTrue(Collections.disjoint(opposing.blocks(),f.state.signals().blocks(f.tape.get(30))));
        assertTrue(TrainReservations.commit(f.state,b.id(),opposing,-1,0));
        TrainReservations.releaseFuture(f.state,a.id());double moved=0,previous=current.speed();
        for(int i=0;i<200 && previous>0;i++) {
            TrainMotion.step(f.state,a.id(),new TrainMotion.Control(1,false,TrainMotion.Turn.STRAIGHT),0);
            double speed=f.state.trains().get(a.id()).speed();assertTrue(speed<previous);moved+=speed;previous=speed;
        }
        assertEquals(0,previous);assertEquals(expected,moved,1e-9);assertEquals(fuel,ac.remainingJ());
    }
}
