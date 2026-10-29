package dev.minetorio.core.rail;

import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.RailSignals.*;
import static dev.minetorio.core.rail.RailwayState.*;
import static dev.minetorio.core.data.RailData.StockKind.*;

class RailSignalsTest {
    static Signal signal(TrainTestFixture f,int coordinate,Kind kind,boolean forward) {
        return new Signal(UUID.randomUUID(),new Port(f.tape.get(coordinate/2).piece(),End.A),forward,kind);
    }
    @Test void pairAllowsBothDirectionsSingleBlocksWrongWay() {
        var f=new TrainTestFixture(); var a=f.tape.get(19); var b=f.tape.get(20);
        var signal=signal(f,40,Kind.REGULAR,true); f.state.replaceSignals(List.of(signal));
        assertTrue(f.state.signals().allowed(a,b));
        assertFalse(f.state.signals().allowed(RailwayState.reverse(b),RailwayState.reverse(a)));
        f.state.replaceSignals(List.of(signal,signal(f,40,Kind.REGULAR,false)));
        assertTrue(f.state.signals().allowed(RailwayState.reverse(b),RailwayState.reverse(a)));
        assertTrue(Collections.disjoint(f.state.signals().blocks(a),f.state.signals().blocks(b)));
    }
    @Test void adjacentBlocksRemainIndependentDespiteEndpointGraphConflicts() {
        var f=new TrainTestFixture(); var signal=signal(f,40,Kind.REGULAR,true); f.state.replaceSignals(List.of(signal));
        var a=f.train(20,0,Mode.AUTOMATIC,f.car(LOCOMOTIVE,false));
        var b=f.train(60,0,Mode.AUTOMATIC,f.car(LOCOMOTIVE,false));
        assertTrue(f.state.graph().conflicts(f.tape.get(19).piece()).contains(f.tape.get(20).piece()));
        assertTrue(Collections.disjoint(f.state.signals().blocks(f.tape.get(19)),f.state.signals().blocks(f.tape.get(20))));
        assertTrue(TrainReservations.commit(f.state,a.id(),TrainReservationsTest.plan(f,a.id(),1,0,100),1,0));
        assertTrue(TrainReservations.commit(f.state,b.id(),TrainReservationsTest.plan(f,b.id(),1,0,100),1,0));
    }
    @Test void headExitDoesNotReleaseTailAndExactCouplerContactIsNotEntry() {
        for(double epsilon:new double[]{-1e-6,0,1e-6}) {
            var f=new TrainTestFixture(); var signal=signal(f,40,Kind.REGULAR,true); f.state.replaceSignals(List.of(signal));
            var train=f.train(50.5+epsilon,0,Mode.MANUAL,f.car(LOCOMOTIVE,false),f.car(CARGO,false));
            UUID rear=f.state.signals().blocks(f.tape.get(10)).iterator().next();
            assertEquals(epsilon<0,f.state.reservations().occupants(rear).contains(train.id()));
            assertEquals(Aspect.RED,f.state.signals().aspect(signal.id(),f.state.reservations()));
        }
        for(double epsilon:new double[]{-1e-6,0,1e-6}) {
            var f=new TrainTestFixture(); var signal=signal(f,40,Kind.REGULAR,true); f.state.replaceSignals(List.of(signal));
            f.train(36.5+epsilon,0,Mode.MANUAL,f.car(LOCOMOTIVE,false));
            assertEquals(epsilon>0?Aspect.RED:Aspect.GREEN,f.state.signals().aspect(signal.id(),f.state.reservations()));
        }
    }
    @Test void regularAspectIncludesDistantOccupancyAndDynamicClaims() {
        var f=new TrainTestFixture(); var signal=signal(f,40,Kind.REGULAR,true); f.state.replaceSignals(List.of(signal));
        var car=f.car(LOCOMOTIVE,false); car.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
        var train=f.train(36.499,0,Mode.AUTOMATIC,car);
        assertEquals(Aspect.GREEN,f.state.signals().aspect(signal.id(),f.state.reservations()));
        assertTrue(TrainReservations.commit(f.state,train.id(),TrainReservationsTest.plan(f,train.id(),1,0,100),1,0));
        assertEquals(Aspect.YELLOW,f.state.signals().aspect(signal.id(),f.state.reservations()));
        TrainReservations.releaseFuture(f.state,train.id());
        assertEquals(Aspect.GREEN,f.state.signals().aspect(signal.id(),f.state.reservations()));
        f.train(100,0,Mode.MANUAL,f.car(CARGO,false));
        assertEquals(Aspect.RED,f.state.signals().aspect(signal.id(),f.state.reservations()));
    }
    @Test void chainCycleWithoutExitStaysClosed() {
        var state=new RailwayState(k->100); state.replaceRails(TrainTestFixture.loop());
        var tape=TrainTestFixture.loopTape(state,1);
        var a=new Signal(UUID.randomUUID(),new Port(tape.get(0).piece(),tape.get(0).forward()?End.A:End.B),true,Kind.CHAIN);
        var b=new Signal(UUID.randomUUID(),new Port(tape.get(6).piece(),tape.get(6).forward()?End.A:End.B),true,Kind.CHAIN);
        state.replaceSignals(List.of(a,b));
        assertEquals(Aspect.RED,state.signals().aspect(a.id(),state.reservations()));
    }
    @Test void crossingTracksConflictWithoutGraphConnection() {
        var state=new RailwayState(k->100); List<Piece> rails=new ArrayList<>(); List<Traversal> north=new ArrayList<>(),east=new ArrayList<>();
        for(int i=-10;i<11;i++) {
            Piece a=new Piece(new UUID(10,i+20),new Point(0,0,-2*i),Shape.STRAIGHT,0,false);
            Piece b=new Piece(new UUID(11,i+20),new Point(2*i,0,0),Shape.STRAIGHT,1,false);
            rails.add(a);rails.add(b);north.add(new Traversal(a.id(),true));east.add(new Traversal(b.id(),true));
        }
        state.replaceRails(rails);
        assertFalse(state.graph().next(north.get(10)).stream().anyMatch(east::contains));
        assertFalse(Collections.disjoint(state.signals().blocks(north.get(10)),state.signals().blocks(east.get(10))));
        var car=state.createCar(UUID.randomUUID(),LOCOMOTIVE,false); var pos=state.locate(north,20);
        var train=state.createTrain(new Train(UUID.randomUUID(),List.of(car.id()),Optional.of(pos.cursor()),pos.index(),north,0,Mode.MANUAL,Status.MANUAL_CONTROL,0,0,0));
        assertTrue(state.signals().blocks(east.get(10)).stream().anyMatch(b->state.reservations().occupants(b).contains(train.id())));
    }

    @Test void blueChainRejectsBlockedChosenExitAndAcceptsFreeRoute() {
        var f=new TrainTestFixture(); var rails=new ArrayList<>(f.state.rails());
        var quarter=RailGeometryTest.quarter(1);
        for(Piece p:quarter) rails.add(new Piece(new UUID(55,p.id().getLeastSignificantBits()),new Point(p.origin().x(),0,p.origin().z()-60),p.shape(),p.quarterTurns(),p.mirrored()));
        List<Traversal> branch=new ArrayList<>();
        for(int i=0;i<30;i++) {
            Piece p=new Piece(new UUID(56,i),new Point(12+2*i,0,-72),Shape.STRAIGHT,1,false);
            rails.add(p); branch.add(new Traversal(p.id(),true));
        }
        f.state.replaceRails(rails);
        var entry=signal(f,40,Kind.CHAIN,true);
        var rightExit=new Signal(UUID.randomUUID(),new Port(branch.get(0).piece(),End.A),true,Kind.REGULAR);
        f.state.replaceSignals(List.of(entry,signal(f,80,Kind.REGULAR,true),rightExit));
        f.train(100,0,Mode.MANUAL,f.car(CARGO,false));
        assertEquals(Aspect.BLUE,f.state.signals().aspect(entry.id(),f.state.reservations()));
        var c=f.car(LOCOMOTIVE,false);c.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
        var train=f.train(36.499,0,Mode.AUTOMATIC,c);
        var blocked=TrainReservationsTest.plan(f,train.id(),1,0,120);
        assertFalse(blocked.exitFits());assertEquals(0.001,blocked.safeDistance(),1e-8);
        var start=TrainReservations.leading(f.state,train.id(),1);
        var route=RailRouter.route(f.state.graph(),start.start(),Set.of(new Cursor(branch.get(20),0)),f.state.signals()::allowed).orElseThrow();
        var open=TrainReservations.plan(f.state,train.id(),route,1,0);
        assertTrue(open.exitFits()); assertTrue(open.safeDistance()>30);
        assertTrue(TrainReservations.commit(f.state,train.id(),open,1,0));
    }

    @Test void reservedEntranceIsYellowAndOppositeEntranceIsRed() {
        var f=new TrainTestFixture(); var entrance=signal(f,40,Kind.REGULAR,true); var opposite=signal(f,80,Kind.REGULAR,false);
        f.state.replaceSignals(List.of(entrance,opposite,signal(f,40,Kind.REGULAR,false),signal(f,80,Kind.REGULAR,true)));
        var car=f.car(LOCOMOTIVE,false);car.setActiveFuel("minetorio:coal",BigDecimal.valueOf(100000));
        var train=f.train(36.499,0,Mode.AUTOMATIC,car);
        assertTrue(TrainReservations.commit(f.state,train.id(),TrainReservationsTest.plan(f,train.id(),1,0,100),1,0));
        assertEquals(Aspect.YELLOW,f.state.signals().aspect(entrance.id(),f.state.reservations()));
        assertEquals(Aspect.RED,f.state.signals().aspect(opposite.id(),f.state.reservations()));
    }
    @Test void inputOrderCannotChangeBlockIdentities() {
        var f=new TrainTestFixture(); var signals=List.of(signal(f,40,Kind.REGULAR,true),signal(f,80,Kind.CHAIN,true));
        var first=RailSignals.rebuild(f.state.graph(),signals,11);
        var rails=new ArrayList<>(f.state.rails()); Collections.reverse(rails); var reordered=new ArrayList<>(signals);Collections.reverse(reordered);
        var second=RailSignals.rebuild(new RailGraph(rails),reordered,11);
        for(Traversal traversal:f.tape) assertEquals(first.blocks(traversal),second.blocks(traversal));
        assertThrows(UnsupportedOperationException.class,()->f.state.graph().pieces().clear());
    }
}
