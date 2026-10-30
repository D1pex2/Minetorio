package dev.minetorio.core.rail;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.minetorio.core.rail.RailGeometry.*;

class RailDockGeometryTest {
    private static final Vec[] TANGENTS={new Vec(0,0,-1),new Vec(1,0,0),new Vec(0,0,1),new Vec(-1,0,0)};
    private static Pose shifted(Pose p,double x,double y,double z){var c=p.position();return new Pose(new Vec(c.x()+x,c.y()+y,c.z()+z),p.tangent());}
    @Test void allCardinalsSidesConnectorsAndThreeFiniteAnchors() {
        for(int axis=0;axis<4;axis++)for(int side:new int[]{-1,1})for(int connector=-1;connector<=1;connector++) {
            Vec t=TANGENTS[axis];Pose pose=new Pose(new Vec(10,64,20),t);
            Vec d=RailDockGeometry.dockPoint(pose,side,connector);
            Vec outward=new Vec(side*t.z(),0,-side*t.x());
            RailDir direction=outward.x()<0?RailDir.W:outward.x()>0?RailDir.E:outward.z()<0?RailDir.N:RailDir.S;
            Point cell=new Point((int)Math.floor(d.x()-.5-.5*outward.x()),64,(int)Math.floor(d.z()-.5-.5*outward.z()));
            assertTrue(RailDockGeometry.matches(pose,side,connector,cell,direction));
            assertFalse(RailDockGeometry.matches(pose,side,connector,cell,direction.opposite()));
            for(int sign=-1;sign<=1;sign++) {
                Vec anchor=RailDockGeometry.pumpPoint(cell,direction,sign);
                Pose aligned=shifted(pose,anchor.x()-d.x(),anchor.y()-d.y(),anchor.z()-d.z());
                assertEquals(sign,RailDockGeometry.anchor(aligned,side,connector,cell,direction).orElseThrow());
                for(double distance:new double[]{0,.0625,.0625+1e-7}) {
                    assertEquals(distance<=.0625,RailDockGeometry.matches(shifted(aligned,outward.x()*distance,0,outward.z()*distance),side,connector,cell,direction));
                    assertEquals(distance<=.0625,RailDockGeometry.matches(shifted(aligned,0,distance,0),side,connector,cell,direction));
                }
                assertFalse(RailDockGeometry.matches(shifted(aligned,outward.x()*.05,.05,outward.z()*.05),side,connector,cell,direction));
            }
            // Между якорями нет непрерывного сдвига сопла.
            var f=RailDockGeometry.pumpPoint(cell,direction);
            Pose gap=shifted(pose,f.x()-d.x()+.25*t.x(),0,f.z()-d.z()+.25*t.z());
            assertFalse(RailDockGeometry.matches(gap,side,connector,cell,direction));
        }
    }
    @Test void invalidArgumentsDoNotMatch() {
        Pose p=new Pose(new Vec(0,0,0),TANGENTS[0]);Point cell=new Point(1,0,0);
        assertFalse(RailDockGeometry.matches(p,0,0,cell,RailDir.W));
        assertFalse(RailDockGeometry.matches(p,1,2,cell,RailDir.W));
        assertFalse(RailDockGeometry.matches(p,1,Integer.MIN_VALUE,cell,RailDir.W));
        assertFalse(RailDockGeometry.matches(p,1,0,cell,RailDir.NW));
        assertFalse(RailDockGeometry.matches(null,1,0,cell,RailDir.W));
        assertFalse(RailDockGeometry.matches(new Pose(new Vec(Double.NaN,0,0),TANGENTS[0]),1,0,cell,RailDir.W));
        assertFalse(RailDockGeometry.matches(new Pose(new Vec(0,0,0),new Vec(0,0,0)),1,0,cell,RailDir.W));
        assertFalse(RailDockGeometry.matches(new Pose(new Vec(0,0,0),new Vec(0,1,0)),1,0,cell,RailDir.W));
    }
    @Test void itemContactUsesExactOrientedBodyAndThreeDimensions() {
        Pose p=new Pose(new Vec(0,64,0),TANGENTS[0]);
        assertEquals(.9,RailDockGeometry.itemContact(p,new Vec(.5,64.5,0),new Vec(1,0,0)).orElseThrow(),1e-10);
        assertEquals(.9,RailDockGeometry.itemContact(p,new Vec(-.5,64.5,0),new Vec(-1,0,0)).orElseThrow(),1e-10);
        assertEquals(0,RailDockGeometry.itemContact(p,new Vec(0,64.5,0),new Vec(0,0,1)).orElseThrow());
        assertTrue(RailDockGeometry.itemContact(p,new Vec(0,66.5,0),new Vec(0,1,0)).isPresent());
        assertTrue(RailDockGeometry.itemContact(p,new Vec(0,64.5,2.7),new Vec(0,0,1)).isPresent());
        assertTrue(RailDockGeometry.itemContact(p,new Vec(0,63.5,0),new Vec(1,0,0)).isEmpty());
        assertTrue(RailDockGeometry.itemContact(p,new Vec(0,67.5,0),new Vec(1,0,0)).isEmpty());
        assertTrue(RailDockGeometry.itemContact(p,new Vec(0,64.5,3),new Vec(1,0,0)).isEmpty());
        double v=Math.sqrt(.5);Pose diagonal=new Pose(new Vec(0,64,0),new Vec(v,0,v));
        assertTrue(RailDockGeometry.itemContact(diagonal,new Vec(1.5,64.5,1.5),new Vec(1,0,0)).isPresent());
        assertTrue(RailDockGeometry.itemContact(diagonal,new Vec(1.5,64.5,-1.5),new Vec(1,0,0)).isEmpty());
    }
    @Test void eachConnectorUsesItsOwnOccurrenceAndReversedAxis() {
        for(boolean reversed:new boolean[]{false,true}) {
            var state=new RailwayState(key->64);List<Piece> rails=new ArrayList<>();
            for(int z=10;z>=2;z-=2)rails.add(new Piece(UUID.randomUUID(),new Point(0,64,z),Shape.STRAIGHT,0,false));
            rails.add(new Piece(UUID.randomUUID(),new Point(0,64,0),Shape.CURVE,0,false));state.replaceRails(rails);
            var car=state.createCar(UUID.randomUUID(),dev.minetorio.core.data.RailData.StockKind.FLUID,reversed);
            var tape=rails.stream().map(p->new Traversal(p.id(),true)).toList();
            var train=state.createTrain(new RailwayState.Train(UUID.randomUUID(),List.of(car.id()),Optional.of(new Cursor(tape.get(4),1.8)),4,tape,0,RailwayState.Mode.MANUAL,RailwayState.Status.MANUAL_CONTROL_STOP,0,0,0));
            assertTrue(RailDockGeometry.connectorFrame(state,train.id(),car.id(),0).isPresent());
            assertTrue(RailDockGeometry.connectorFrame(state,train.id(),car.id(),reversed?1:-1).isPresent());
            assertTrue(RailDockGeometry.connectorFrame(state,train.id(),car.id(),reversed?-1:1).isEmpty());
            assertEquals(reversed?1:-1,RailDockGeometry.connectorFrame(state,train.id(),car.id(),0).orElseThrow().tangent().z());
            state.commitMotion(train,state.graph().revision(),new RailwayState.MotionUpdate(Optional.of(new Cursor(tape.get(5),.05)),5,tape,0,train.status()));
            assertTrue(TrainMotion.carPoses(state,train.id()).get(car.id()).tangent().x()==0);
            assertTrue(RailDockGeometry.connectorFrame(state,train.id(),car.id(),0).isEmpty(),"Cardinal lead-in кривой не становится STRAIGHT");
        }
    }
}
