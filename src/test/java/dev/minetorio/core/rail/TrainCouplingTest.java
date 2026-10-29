package dev.minetorio.core.rail;
import java.util.*;
import dev.minetorio.core.data.RailData.StockKind;
import static dev.minetorio.core.rail.RailwayState.*;
import static dev.minetorio.core.rail.TrainCoupling.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TrainCouplingTest {
    @Test void splitMergeConserveEverySlotAndFluid() {
        var f=new TrainTestFixture(); var a=f.car(StockKind.LOCOMOTIVE,false); var b=f.car(StockKind.CARGO,false); var c=f.car(StockKind.FLUID,false);
        b.cargo().get().insert(39,"minetorio:pistol",5,false); c.insertFluid("water",12345);
        var slots=b.cargo().get(); var t=f.train(50,0,Mode.MANUAL,a,b,c); var before=TrainMotion.carPoses(f.state,t.id());
        UUID second=split(f.state,a.id(),End.BACK).orElseThrow();
        assertEquals(List.of(b.id(),c.id()),f.state.trains().get(second).cars());
        assertTrue(couple(f.state,a.id(),End.BACK,b.id(),End.FRONT));
        assertEquals(before,TrainMotion.carPoses(f.state,t.id())); assertSame(slots,b.cargo().get()); assertEquals(5,slots.get(39).count()); assertEquals(12345,c.fluid().get().amount());
    }
    @Test void movingSplitPreservesSignedMomentumAndCannotMerge() {
        for(double speed:new double[]{0.1,-0.1}) {
            var f=new TrainTestFixture(); var a=f.car(StockKind.LOCOMOTIVE,false); var b=f.car(StockKind.CARGO,true); var t=f.train(50,speed,Mode.AUTOMATIC,a,b);
            UUID second=split(f.state,a.id(),End.BACK).orElseThrow();
            assertEquals(speed,f.state.trains().get(t.id()).speed()); assertEquals(speed,f.state.trains().get(second).speed());
            assertEquals(Mode.MANUAL,f.state.trains().get(second).mode()); assertFalse(couple(f.state,a.id(),End.BACK,b.id(),End.BACK));
        }
    }

    @Test void allFourPhysicalEndPairsWithBothOrientationsPreservePoses() {
        for(End ea:End.values()) for(End eb:End.values()) for(boolean ar:new boolean[]{false,true}) for(boolean br:new boolean[]{false,true}) {
            var f=new TrainTestFixture(); var a=f.car(StockKind.CARGO,ar); var b=f.car(StockKind.CARGO,br);
            boolean frontA=(ea==End.FRONT)!=ar, frontB=(eb==End.FRONT)!=br;
            var ta=f.train(50,0,Mode.AUTOMATIC,a);
            double bWorldQ=50+(frontA?7:-7);
            var tape=new ArrayList<>(f.tape); double bQ=bWorldQ;
            if(frontA==frontB) { Collections.reverse(tape); tape.replaceAll(RailwayState::reverse); bQ=f.state.tapeLength(tape)-bWorldQ; }
            var cursor=f.state.locate(tape,bQ);
            var tb=f.state.createTrain(new Train(UUID.randomUUID(),List.of(b.id()),Optional.of(cursor.cursor()),cursor.index(),tape,0,Mode.MANUAL,Status.MANUAL_CONTROL_STOP,0,0,0));
            var poses=new HashMap<>(TrainMotion.carPoses(f.state,ta.id())); poses.putAll(TrainMotion.carPoses(f.state,tb.id()));
            assertTrue(couple(f.state,a.id(),ea,b.id(),eb),ea+"/"+eb+"/"+ar+"/"+br);
            assertEquals(1,f.state.trains().size()); assertEquals(Mode.AUTOMATIC,f.state.trains().get(ta.id()).mode());
            var after=TrainMotion.carPoses(f.state,ta.id());
            poses.forEach((id,pose)-> { assertTrue(RailwayState.near(pose.position(),after.get(id).position())); assertTrue(RailwayState.near(pose.tangent(),after.get(id).tangent())); });
            assertEquals(1,f.state.trains().get(ta.id()).consistRevision());
        }
    }
    @Test void wrongGapMovingBothSignsAndSelfTrainLeaveStateUnchanged() {
        for(double speed:new double[]{-0.01,0.01,0}) {
            var f=new TrainTestFixture(); var a=f.car(StockKind.CARGO,false); var b=f.car(StockKind.CARGO,false);
            var ta=f.train(50,speed,Mode.MANUAL,a); var tb=f.train(speed==0?42.9:43,0,Mode.MANUAL,b);
            var before=new HashMap<>(f.state.trains());
            assertFalse(couple(f.state,a.id(),End.BACK,b.id(),End.FRONT)); assertEquals(before,f.state.trains());
            assertFalse(couple(f.state,a.id(),End.BACK,a.id(),End.FRONT)); assertEquals(before,f.state.trains());
            assertTrue(split(f.state,a.id(),End.FRONT).isEmpty());
        }
    }

    @Test void curveSplitMergePreservesEveryBodyAndCouplerPose() {
        var state=new RailwayState(key->100); state.replaceRails(TrainTestFixture.loop()); var tape=TrainTestFixture.loopTape(state,2);
        var a=state.createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,true); var b=state.createCar(UUID.randomUUID(),StockKind.CARGO,false);
        var pos=state.locate(tape,23.5); var t=state.createTrain(new Train(UUID.randomUUID(),List.of(a.id(),b.id()),Optional.of(pos.cursor()),pos.index(),tape,0,Mode.MANUAL,Status.MANUAL_CONTROL_STOP,0,0,0));
        var before=TrainMotion.carPoses(state,t.id()); var joint=state.pose(tape,20,false);
        split(state,a.id(),End.FRONT).orElseThrow(); assertTrue(couple(state,a.id(),End.FRONT,b.id(),End.FRONT));
        var after=TrainMotion.carPoses(state,t.id()); before.forEach((id,p)-> { assertTrue(RailwayState.near(p.position(),after.get(id).position())); assertTrue(RailwayState.near(p.tangent(),after.get(id).tangent())); });
        var merged=state.trains().get(t.id()); assertTrue(RailwayState.near(joint.position(),state.pose(merged.tape(),state.headCoordinate(merged)-3.5,false).position()));
    }
}
