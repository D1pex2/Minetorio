package dev.minetorio.core.rail;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.minetorio.core.rail.RailGeometry.*;
class RailSweepTest {
    private static RailSweep.Body line(double x,double y,double z,double dx,double dz,boolean east) {
        return new RailSweep.Body(){public int cars(){return 1;}public double travel(){return Math.hypot(dx,dz);}
            public Vec point(int car,double q,double t){return new Vec(x+dx*t+(east?q:0),y,z+dz*t+(east?0:q));}};
    }
    @Test void continuousSweepFindsFastCrossingWithClearEndpoints() {
        var a=line(-10,0,0,20,0,true);var b=line(0,0,0,0,0,false);
        var hit=RailSweep.pair(a,b).orElseThrow();assertTrue(hit.time()>.2&&hit.time()<.5);
        assertTrue(RailSweep.pair(line(-10,6,0,20,0,true),b).isEmpty());
    }
    @Test void reverseAndRelativeMotionUseTheWholeInterval() {
        assertTrue(RailSweep.pair(line(10,0,0,-10,0,true),line(-10,0,0,10,0,true)).isPresent());
        assertTrue(RailSweep.pair(line(10,0,10,-10,0,true),line(-10,0,0,10,0,true)).isEmpty());
    }
    @Test void loadedThinObstacleCannotBeSkippedAndHeightIsRespected() {
        var body=line(-10,0,0,20,0,true);
        assertTrue(RailSweep.box(body,new RailSweep.Box(0,.2,-.1,.01,1,.1)).isPresent());
        assertTrue(RailSweep.box(body,new RailSweep.Box(0,4,-.1,.01,5,.1)).isEmpty());
    }
}
