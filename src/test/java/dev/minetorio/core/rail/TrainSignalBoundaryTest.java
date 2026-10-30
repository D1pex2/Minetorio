package dev.minetorio.core.rail;

import java.math.BigDecimal;
import java.util.*;
import dev.minetorio.core.rail.RailwayState.Car;
import org.junit.jupiter.api.Test;
import static dev.minetorio.core.data.RailData.StockKind.*;
import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.RailwayState.*;
import static org.junit.jupiter.api.Assertions.*;

class TrainSignalBoundaryTest {
    private static final class Scene {
        final RailwayState state=new RailwayState(key->100);
        final List<Traversal> tape;
        final UUID train=new UUID(3,1);
        final Car loco,blocker;
        final int sign;
        Scene(int sign,boolean exact,boolean shared) {
            this.sign=sign;List<Piece> rails=new ArrayList<>();
            for(int i=0;i<30;i++)rails.add(new Piece(new UUID(1,i),new Point(0,80,-2*i),Shape.STRAIGHT,0,false));
            state.replaceRails(rails);tape=rails.stream().map(p->new Traversal(p.id(),true)).toList();
            var cargo=state.createCar(new UUID(2,1),CARGO,false);
            loco=state.createCar(new UUID(2,2),LOCOMOTIVE,sign<0);loco.setActiveFuel("minetorio:coal",BigDecimal.valueOf(4000000));
            double nose=shared?26+sign*.25:exact?26:26-sign*1.5;
            var head=state.locate(tape,nose+(sign>0?-3.5:10.5));
            state.createTrain(new Train(train,List.of(cargo.id(),loco.id()),Optional.of(head.cursor()),head.index(),tape,0,Mode.AUTOMATIC,Status.NO_SCHEDULE,0,0,0));
            blocker=state.createCar(new UUID(2,3),CARGO,false);var blockHead=state.locate(tape,sign>0?34:18);
            state.createTrain(new Train(new UUID(3,2),List.of(blocker.id()),Optional.of(blockHead.cursor()),blockHead.index(),tape,0,Mode.MANUAL,Status.MANUAL_CONTROL_STOP,0,0,0));
            state.replaceSignals(List.of(new RailSignals.Signal(new UUID(4,1),new Port(rails.get(13).id(),End.A),sign>0,RailSignals.Kind.REGULAR)));
            var target=state.locate(tape,sign>0?31.5:20.5).cursor();
            if(sign<0)target=new Cursor(RailwayState.reverse(target.traversal()),2-target.distance());
            state.dispatcher().upsertStation(new Station(new UUID(5,1),"boundary",target,true,OptionalInt.empty()));
            state.dispatcher().setSchedule(train,new TrainSchedule(List.of(new TrainSchedule.Stop(Optional.of("boundary"),Optional.empty(),false,
                    List.of(new TrainSchedule.Wait(TrainSchedule.Type.TIME,TrainSchedule.Join.AND,999999,Optional.empty())))),0));
        }
        double nose() {
            var cursor=TrainReservations.leading(state,train,sign).start();
            return 2*cursor.traversal().piece().getLeastSignificantBits()+(sign>0?cursor.distance():2-cursor.distance());
        }
        void step(boolean blocked,int tick) {
            var before=state.trains().get(train);var fuel=loco.remainingJ();
            var proposals=state.dispatcher().beginTick(RailCircuitInputs.NONE,0);
            assertEquals(fuel,loco.remainingJ(),"Планирование не расходует топливо");
            assertEquals(before.head(),state.trains().get(train).head(),"Планирование не двигает owner");
            for(var proposal:proposals) {
                assertTrue(proposal.validFor(state));
                if(blocked)for(double fraction:new double[]{0,.5,1}) {
                    double q=-proposal.point(sign>0?0:1,sign>0?3.5:-3.5,fraction).position().z();
                    assertTrue(sign*(q-26)<=0,"World sweep вышел за RED на подшаге "+tick+": "+q);
                }
                assertTrue(proposal.commit());
            }
            state.dispatcher().finishTick(RailCircuitInputs.NONE);
            if(blocked)assertTrue(sign*(nose()-26)<=0,"Owner пересек RED на подшаге "+tick+": "+nose());
        }
    }
    private static void waitThenRelease(int sign,boolean exact) {
        var scene=new Scene(sign,exact,false);
        for(int tick=0;tick<100;tick++)scene.step(true,tick);
        assertEquals(0,scene.state.trains().get(scene.train).speed());
        assertEquals(Status.WAIT_SIGNAL,scene.state.trains().get(scene.train).status());
        var stoppedFuel=scene.loco.remainingJ();double stoppedNose=scene.nose();
        for(int tick=100;tick<110;tick++)scene.step(true,tick);
        assertEquals(stoppedFuel,scene.loco.remainingJ());assertEquals(stoppedNose,scene.nose());
        scene.state.removeCar(scene.blocker.id(),true);
        for(int tick=0;tick<10;tick++)scene.step(false,tick);
        assertTrue(sign*(scene.nose()-stoppedNose)>0,"Освобождение блока разрешило штатный departure");
        assertTrue(scene.loco.remainingJ().compareTo(stoppedFuel)<0);
    }
    @Test void forwardApproachNeverCrossesOccupiedBoundary(){waitThenRelease(1,false);}
    @Test void reverseApproachNeverCrossesOccupiedBoundary(){waitThenRelease(-1,false);}
    @Test void exactForwardBoundaryWaitsThenDeparts(){waitThenRelease(1,true);}
    @Test void exactReverseBoundaryWaitsThenDeparts(){waitThenRelease(-1,true);}
    @Test void legitimatelySharedInitialBlockStillAllowsApproach() {
        for(int sign:new int[]{-1,1}) {
            var scene=new Scene(sign,false,true);double start=scene.nose();
            scene.step(false,0);
            assertTrue(sign*(scene.nose()-start)>0,"Настоящий shared interior не запрещен");
        }
    }
    @Test void ownerRejectsEvenNumericalExcessBeforeFuelOrClaimsChange() {
        var f=new TrainTestFixture();var t=f.train(50,0,Mode.AUTOMATIC,f.car(LOCOMOTIVE,false));
        var plan=TrainReservationsTest.plan(f,t.id(),1,0,54.5);assertTrue(TrainReservations.commit(f.state,t.id(),plan,1,0));
        var cursor=f.state.locate(f.tape,Math.nextUp(51d));double excess=Math.nextUp(1d);
        assertFalse(f.state.commitMotion(t,f.state.graph().revision(),new MotionUpdate(Optional.of(cursor.cursor()),cursor.index(),f.tape,0,t.status()),
                1,excess,0,true,()->fail("Расход до проверки численной границы authority")));
        assertEquals(t,f.state.trains().get(t.id()));assertEquals(plan,f.state.reservations().acceptedPlan(t.id()).orElseThrow());
    }
}
