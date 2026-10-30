package dev.minetorio.core.rail;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import dev.minetorio.core.rail.RailwayState.Car;
import static dev.minetorio.core.data.RailData.StockKind.*;
import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.RailwayState.*;
import static org.junit.jupiter.api.Assertions.*;

class TrainTargetBoundaryTest {
    private static final class Scene {
        final RailwayState state=new RailwayState(key->100);
        final UUID train=new UUID(3,1),station=new UUID(5,1);
        final Car loco;
        final int sign;
        final double goal;
        final List<Traversal> tape;
        Scene(int sign,boolean temporary,boolean behind) {
            this(sign,temporary,behind,false);
        }
        Scene(int sign,boolean temporary,boolean behind,boolean curvePrefix) {
            this.sign=sign;List<Piece> rails=new ArrayList<>();
            if(curvePrefix)rails.addAll(TrainTestFixture.loop());
            double prefix=rails.stream().mapToDouble(RailGeometry::length).sum();
            for(int i=0;i<30;i++)rails.add(new Piece(new UUID(1,i),new Point(0,80,-2*i),Shape.STRAIGHT,0,false));
            if(curvePrefix)rails.replaceAll(p->p.id().getMostSignificantBits()==7?new Piece(p.id(),new Point(p.origin().x(),80,p.origin().z()),p.shape(),p.quarterTurns(),p.mirrored()):p);
            state.replaceRails(rails);
            var steps=new ArrayList<Traversal>();
            if(curvePrefix){var step=new Traversal(rails.getFirst().id(),true);for(int i=0;i<12;i++){steps.add(step);step=state.graph().next(step).stream().filter(t->t.piece().getMostSignificantBits()==7).findFirst().orElseThrow();}}
            rails.stream().filter(p->p.id().getMostSignificantBits()==1).map(p->new Traversal(p.id(),true)).forEach(steps::add);tape=List.copyOf(steps);
            var wagon=state.createCar(new UUID(2,1),temporary?CARGO:FLUID,false);
            loco=state.createCar(new UUID(2,2),LOCOMOTIVE,sign<0);loco.fuel().orElseThrow().insert(0,"minetorio:coal",5,false);
            double headQ=curvePrefix?1:sign>0?21:38;double nose=headQ+(sign>0?3.5:-10.5);goal=behind?nose-sign*.1:nose+sign*7;
            var head=state.locate(tape,prefix+headQ);state.createTrain(new Train(train,List.of(wagon.id(),loco.id()),Optional.of(head.cursor()),head.index(),tape,0,Mode.MANUAL,Status.MANUAL_CONTROL_STOP,0,0,0));
            var target=new Cursor(new Traversal(new UUID(1,(int)(goal/2)),true),goal%2);if(sign<0)target=new Cursor(RailwayState.reverse(target.traversal()),2-target.distance());
            if(!temporary)state.dispatcher().upsertStation(new Station(station,"target",target,true,OptionalInt.empty()));
            var stop=new TrainSchedule.Stop(temporary?Optional.empty():Optional.of("target"),temporary?Optional.of(new TrainSchedule.RailTarget(target,false)):Optional.empty(),temporary,
                    List.of(new TrainSchedule.Wait(TrainSchedule.Type.TIME,TrainSchedule.Join.AND,999999,Optional.empty())));
            state.dispatcher().setSchedule(train,new TrainSchedule(List.of(stop),0));state.dispatcher().setMode(train,Mode.AUTOMATIC);
        }
        double nose() {
            var c=TrainReservations.leading(state,train,sign).start();return c.traversal().piece().getLeastSignificantBits()*2+(sign>0?c.distance():2-c.distance());
        }
    }
    private static void arrive(int sign,boolean temporary,boolean edit) {
        arrive(new Scene(sign,temporary,false),edit);
    }
    private static void arrive(Scene scene,boolean edit) {
        int sign=scene.sign;boolean terminal=false,noPath=false;String firstViolation=null;BigDecimal terminalFuel=null;
        for(int tick=0;tick<300;tick++) {
            var fuel=scene.loco.remainingJ();var slots=scene.loco.fuel().orElseThrow().snapshot();var before=scene.state.trains().get(scene.train);
            var proposals=scene.state.dispatcher().beginTick(RailCircuitInputs.NONE,0);
            assertEquals(fuel,scene.loco.remainingJ());assertEquals(slots,scene.loco.fuel().orElseThrow().snapshot());assertEquals(before.head(),scene.state.trains().get(scene.train).head());
            for(var proposal:proposals) {
                for(double fraction:new double[]{0,.5,1}) {
                    double q=-proposal.point(sign>0?0:1,sign>0?3.5:-3.5,fraction).position().z();
                    if(sign*(q-scene.goal)>0&&firstViolation==null)firstViolation="sweep tick="+tick+", q="+q+", goal="+scene.goal;
                }
                assertTrue(proposal.commit());
            }
            scene.state.dispatcher().finishTick(RailCircuitInputs.NONE);
            double nose=scene.nose();var current=scene.state.trains().get(scene.train);var runtime=scene.state.dispatcher().runtime(scene.train);
            if(sign*(nose-scene.goal)>0&&firstViolation==null)firstViolation="owner tick="+tick+", q="+nose+", goal="+scene.goal;
            noPath|=current.status()==Status.NO_PATH;
            if(current.status()==Status.WAIT_STATION){assertEquals(0,current.speed());break;}
            if(!terminal&&current.speed()!=0&&runtime.assignment().map(a->a.visits().getLast().remaining()<=1e-12).orElse(false)) {
                terminal=true;terminalFuel=scene.loco.remainingJ();assertEquals(scene.goal,nose,1e-10,"Тест меняет revision именно между terminal move и braking-to-zero");
                if(edit)scene.state.replaceRails(scene.state.rails());
            }
        }
        final String violation=firstViolation;final boolean invalidPath=noPath,reachedTerminal=terminal;
        assertAll(()->assertTrue(reachedTerminal,"Достигнут terminal substep с ненулевой скоростью"),
                ()->assertNull(violation,"Первое нарушение не теряется при eventual arrival"),
                ()->assertFalse(invalidPath,"Неизменная геометрия не делает назначенную цель недостижимой"),
                ()->assertEquals(Status.WAIT_STATION,scene.state.trains().get(scene.train).status()));
        assertEquals(terminalFuel,scene.loco.remainingJ(),"Финальный braking step не расходует топливо");
    }
    @Test void physicalForwardTerminalBoundarySurvivesRevision(){arrive(1,false,true);}
    @Test void temporaryForwardTerminalBoundarySurvivesRevision(){arrive(1,true,true);}
    @Test void physicalReverseTerminalBoundarySurvivesRevision(){arrive(-1,false,true);}
    @Test void temporaryReverseTerminalBoundarySurvivesRevision(){arrive(-1,true,true);}
    @Test void controlsWithoutRevisionAlsoNeverOverrunTarget(){for(int sign:new int[]{-1,1})for(boolean temporary:new boolean[]{false,true})arrive(sign,temporary,false);}
    @Test void terminalStraightAfterRealCurvePrefixSurvivesRevision(){arrive(new Scene(1,false,false,true),true);}
    @Test void actualTargetBehindStillHasNoForwardPath() {
        for(int sign:new int[]{-1,1})for(boolean temporary:new boolean[]{false,true}) {
            var scene=new Scene(sign,temporary,true);double start=scene.nose();
            for(int tick=0;tick<3;tick++)scene.state.dispatcher().tick(RailCircuitInputs.NONE,0);
            assertEquals(Status.NO_PATH,scene.state.trains().get(scene.train).status());assertEquals(start,scene.nose());
            assertEquals("",scene.loco.activeFuel());assertEquals(5,scene.loco.fuel().orElseThrow().get(0).count());
        }
    }
}
