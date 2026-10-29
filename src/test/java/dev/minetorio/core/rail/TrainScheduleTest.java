package dev.minetorio.core.rail;

import java.util.*;
import dev.minetorio.core.rail.TrainSchedule.Comparator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.minetorio.core.rail.TrainSchedule.*;

class TrainScheduleTest {
    static Stop stop(String name, boolean temporary) { return new Stop(Optional.of(name),Optional.empty(),temporary,List.of()); }
    @Test void currentAndTargetsAreValidated() {
        assertThrows(IllegalArgumentException.class,()->new TrainSchedule(List.of(),1));
        assertThrows(IllegalArgumentException.class,()->new TrainSchedule(List.of(stop("A",false)),-1));
        assertThrows(IllegalArgumentException.class,()->new Stop(Optional.empty(),Optional.empty(),false,List.of()));
        assertThrows(IllegalArgumentException.class,()->new Wait(Type.TIME,Join.AND,-1,Optional.empty()));
    }
    @Test void temporaryRemovalDoesNotSkipNextRecord() {
        var schedule=new TrainSchedule(List.of(stop("A",false),stop("T",true),stop("B",false)),1);
        var next=schedule.advance();
        assertEquals(2,next.stops().size()); assertEquals("B",next.stops().get(next.current()).station().orElseThrow());
        assertEquals(0,new TrainSchedule(List.of(stop("T",true)),0).advance().stops().size());
    }
    @Test void immutableScheduleAndInvalidWildcardOperands() {
        var stops=new ArrayList<>(List.of(stop("A",false)));var schedule=new TrainSchedule(stops,0);stops.clear();
        assertEquals(1,schedule.stops().size());
        var a=new SignalKey(SignalKind.VIRTUAL,"A");
        assertThrows(IllegalArgumentException.class,()->new Condition(Optional.of(a),Comparator.EQ,Optional.of(new SignalKey(SignalKind.VIRTUAL,"signal-anything")),0));
        assertThrows(IllegalArgumentException.class,()->new Condition(Optional.of(new SignalKey(SignalKind.VIRTUAL,"signal-each")),Comparator.EQ,Optional.empty(),0));
    }
}
