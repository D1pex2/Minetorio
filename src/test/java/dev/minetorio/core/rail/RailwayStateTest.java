package dev.minetorio.core.rail;
import java.util.*;
import static dev.minetorio.core.rail.RailGeometry.*;
import dev.minetorio.core.data.RailData.StockKind;
import static dev.minetorio.core.rail.RailwayState.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RailwayStateTest {
    @Test void staleAndInvalidMotionAreAtomic() {
        var f=new TrainTestFixture(); var c=f.car(StockKind.LOCOMOTIVE,false); var t=f.train(50,0,Mode.MANUAL,c); long rev=f.state.graph().revision();
        var next=new MotionUpdate(t.head(),t.headIndex(),t.tape(),0.1,t.status());
        assertFalse(f.state.commitMotion(t,rev-1,next)); assertEquals(t,f.state.trains().get(t.id()));
        assertThrows(IllegalArgumentException.class,()->f.state.commitMotion(t,rev,new MotionUpdate(t.head(),0,t.tape(),0,t.status())));
        assertTrue(f.state.commitMotion(t,rev,next)); assertFalse(f.state.commitMotion(t,rev,next));
        var after=f.state.trains().get(t.id()); assertEquals(1,after.routeRevision()); assertEquals(1,after.motionRevision()); assertEquals(0,after.consistRevision());
    }
    @Test void removedRailsInvalidateSnapshotRevision() {
        var f=new TrainTestFixture(); var c=f.car(StockKind.LOCOMOTIVE,false); var t=f.train(50,0.1,Mode.MANUAL,c);
        f.state.replaceRails(List.of()); var after=f.state.trains().get(t.id());
        assertEquals(Status.PATH_LOST,after.status()); assertEquals(1,after.motionRevision()); assertEquals(1,after.routeRevision());
    }

    @Test void repeatedTraversalUsesExplicitHeadIndexAndNormalizesInternalBoundary() {
        var state=new RailwayState(key->100); state.replaceRails(TrainTestFixture.loop()); var tape=TrainTestFixture.loopTape(state,3);
        var car=state.createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,false); var tail=state.createCar(UUID.randomUUID(),StockKind.CARGO,true);
        for(double distance:new double[]{0,length(state.rails().getFirst())}) {
            var candidate=new Train(UUID.randomUUID(),List.of(car.id(),tail.id()),Optional.of(new Cursor(tape.get(12),distance)),12,tape,-0.1,Mode.MANUAL,Status.MANUAL_CONTROL,0,0,0);
            assertTrue(state.validLayout(candidate));
            double circumference=4*(2*7.842081225095013+Math.sqrt(2));
            assertEquals(circumference+distance,state.headCoordinate(candidate),1e-12);
            var before=state.poses(candidate,Set.of());
            assertTrue(RailwayState.near(state.pose(tape,circumference+distance-7,true).position(),before.get(tail.id()).position()));
        }
        var t=state.createTrain(new Train(UUID.randomUUID(),List.of(car.id(),tail.id()),Optional.of(new Cursor(tape.get(12),length(state.rails().getFirst()))),12,tape,-0.1,Mode.MANUAL,Status.MANUAL_CONTROL,0,0,0));
        assertEquals(13,t.headIndex()); assertEquals(0,t.head().get().distance());
        assertFalse(TrainCoupling.couple(state,car.id(),TrainCoupling.End.FRONT,tail.id(),TrainCoupling.End.BACK));
    }
    @Test void staleReplaceAndInvalidMembershipAreAtomic() {
        var f=new TrainTestFixture(); var a=f.car(StockKind.LOCOMOTIVE,false); var b=f.car(StockKind.CARGO,true); var t=f.train(50,0,Mode.MANUAL,a,b);
        assertFalse(f.state.replaceConsists(List.of(t),f.state.graph().revision()-1,List.of(t),Set.of()));
        var invalid=new Train(t.id(),List.of(a.id()),t.head(),t.headIndex(),t.tape(),0,t.mode(),t.status(),0,0,0);
        assertThrows(IllegalArgumentException.class,()->f.state.replaceConsists(List.of(t),f.state.graph().revision(),List.of(invalid),Set.of()));
        assertThrows(IllegalArgumentException.class,()->f.state.replaceConsists(List.of(t),f.state.graph().revision(),List.of(t),Set.of(b.id())));
        assertEquals(t,f.state.trains().get(t.id())); assertTrue(b.reversed());
    }
    @Test void insufficientBodyCoverageIsPathLostAndCannotMoveOrBurn() {
        var f=new TrainTestFixture(); var a=f.car(StockKind.LOCOMOTIVE,false); a.setActiveFuel("minetorio:coal",java.math.BigDecimal.TEN);
        var candidate=new Train(UUID.randomUUID(),List.of(a.id()),Optional.of(new Cursor(f.tape.getFirst(),0)),0,f.tape,0,Mode.MANUAL,Status.MANUAL_CONTROL_STOP,0,0,0);
        var t=f.state.createTrain(candidate); assertEquals(Status.PATH_LOST,t.status()); assertEquals(-1,t.headIndex());
        TrainMotion.step(f.state,t.id(),new TrainMotion.Control(1,false,TrainMotion.Turn.RIGHT),0);
        assertEquals(java.math.BigDecimal.TEN,a.remainingJ()); assertTrue(TrainMotion.carPoses(f.state,t.id()).isEmpty());
    }

    @Test void pathLostAlwaysUsesEmptyLayoutSentinel() {
        var f=new TrainTestFixture(); var car=f.car(StockKind.LOCOMOTIVE,false); var pos=f.state.locate(f.tape,50);
        var candidate=new Train(UUID.randomUUID(),List.of(car.id()),Optional.of(pos.cursor()),pos.index(),f.tape,-0.1,Mode.MANUAL,Status.PATH_LOST,0,0,0);
        var t=f.state.createTrain(candidate);
        assertTrue(t.head().isEmpty()); assertTrue(t.tape().isEmpty()); assertEquals(-1,t.headIndex()); assertEquals(0,t.speed());
    }
}
