package dev.minetorio.core.rail;

import java.util.*;
import dev.minetorio.core.rail.TrainSchedule.Comparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static dev.minetorio.core.rail.TrainSchedule.*;
import static dev.minetorio.core.rail.TrainWait.*;

class TrainWaitTest {
    static final SignalKey A=new SignalKey(SignalKind.VIRTUAL,"A");
    static Wait wait(Type type,long ticks) { return new Wait(type,Join.AND,ticks,Optional.empty()); }
    static Wait condition(Type type,SignalKey key,Comparator cmp,int value) {
        return new Wait(type,Join.AND,0,Optional.of(new Condition(Optional.of(key),cmp,Optional.empty(),value)));
    }
    static Context context(long time, Optional<Map<SignalKey,Integer>> input) { return new Context(time,time,List.of(),List.of(),Optional.empty(),input); }
    @ParameterizedTest @EnumSource(Type.class) void everyWaitHasItsOwnTrueCase(Type type) {
        var key=new SignalKey(type==Type.ITEM_COUNT?SignalKind.ITEM:type==Type.FLUID_COUNT?SignalKind.FLUID:SignalKind.VIRTUAL,"A");
        var w=type==Type.CIRCUIT||type==Type.ITEM_COUNT||type==Type.FLUID_COUNT?condition(type,key,Comparator.EQ,0):wait(type,60);
        var c=new Context(60,60,List.of(),List.of(),Optional.of(new PassengerState(type==Type.PASSENGER_PRESENT,0)),Optional.of(Map.of()));
        assertTrue(evaluate(List.of(w),c),type.toString());
    }
    @ParameterizedTest @EnumSource(Comparator.class) void sixComparators(Comparator cmp) {
        int rhs=switch(cmp){case LT,LE->6;case EQ,GE->5;case NE,GT->4;};
        assertTrue(evaluate(List.of(condition(Type.CIRCUIT,A,cmp,rhs)),context(0,Optional.of(Map.of(A,5)))));
        int falseRhs=switch(cmp){case LT->5;case LE,EQ->4;case NE->5;case GE,GT->6;};
        assertFalse(evaluate(List.of(condition(Type.CIRCUIT,A,cmp,falseRhs)),context(0,Optional.of(Map.of(A,5)))));
    }
    @Test void disconnectedIsNotConnectedZeroAndIncompleteIsAlwaysFalse() {
        var zero=condition(Type.CIRCUIT,A,Comparator.EQ,0);
        assertFalse(evaluate(List.of(zero),context(0,Optional.empty())));
        assertTrue(evaluate(List.of(zero),context(0,Optional.of(Map.of()))));
        assertFalse(evaluate(List.of(wait(Type.CIRCUIT,0)),context(0,Optional.of(Map.of()))));
        assertTrue(evaluate(List.of(zero,new Wait(Type.EMPTY,Join.OR,0,Optional.empty())),context(0,Optional.empty())));
    }
    @Test void andGroupsHavePriorityAndFirstJoinIsIgnored() {
        var t=new Wait(Type.TIME,Join.OR,0,Optional.empty());var f=new Wait(Type.TIME,Join.AND,1,Optional.empty());
        assertTrue(evaluate(List.of(f,wait(Type.EMPTY,0),t),context(0,Optional.empty())));
        assertTrue(evaluate(List.of(t,new Wait(Type.TIME,Join.OR,1,Optional.empty()),f),context(0,Optional.empty())));
        assertTrue(evaluate(List.of(),context(0,Optional.empty())));
        assertFalse(evaluate(List.of(wait(Type.TIME,60)),context(59,Optional.empty())));
        assertTrue(evaluate(List.of(wait(Type.TIME,60)),context(60,Optional.empty())));
    }
    @Test void wildcardDomainAndRightSignalExclusion() {
        var all=new SignalKey(SignalKind.VIRTUAL,"signal-everything");var any=new SignalKey(SignalKind.VIRTUAL,"signal-anything");
        assertTrue(evaluate(List.of(condition(Type.CIRCUIT,all,Comparator.GT,0)),context(0,Optional.of(Map.of()))));
        assertFalse(evaluate(List.of(condition(Type.CIRCUIT,any,Comparator.GT,0)),context(0,Optional.of(Map.of()))));
        var c=new Condition(Optional.of(all),Comparator.GT,Optional.of(A),999);
        assertTrue(evaluate(List.of(new Wait(Type.CIRCUIT,Join.AND,0,Optional.of(c))),context(0,Optional.of(Map.of(A,5,new SignalKey(SignalKind.ITEM,"A"),6)))));
        assertFalse(evaluate(List.of(condition(Type.ITEM_COUNT,A,Comparator.EQ,0)),context(0,Optional.of(Map.of()))));
        assertTrue(evaluate(List.of(condition(Type.ITEM_COUNT,new SignalKey(SignalKind.ITEM,"signal-everything"),Comparator.EQ,0)),context(0,Optional.empty())));
    }
    @Test void unknownPassengerDoesNotPretendAbsenceOrNoRobots() {
        for(var type:List.of(Type.PASSENGER_PRESENT,Type.PASSENGER_NOT_PRESENT,Type.ROBOTS_INACTIVE)) assertFalse(evaluate(List.of(wait(type,0)),context(0,Optional.empty())));
        assertThrows(IllegalArgumentException.class,()->new PassengerState(true,-1));
        assertFalse(evaluate(List.of(wait(Type.ROBOTS_INACTIVE,0)),new Context(0,0,List.of(),List.of(),Optional.of(new PassengerState(true,1)),Optional.empty())));
    }
    @Test void knownPassengerSurvivesUnknownRobotCount() {
        var context=new Context(0,0,List.of(),List.of(),Optional.of(new PassengerState(true,OptionalInt.empty())),Optional.empty());
        assertTrue(evaluate(List.of(wait(Type.PASSENGER_PRESENT,0)),context));
        assertFalse(evaluate(List.of(wait(Type.PASSENGER_NOT_PRESENT,0)),context));
        assertFalse(evaluate(List.of(wait(Type.ROBOTS_INACTIVE,0)),context));
    }
    @Test void fullUsesExactPhysicalKeyFilterAndBarWithoutMutating() {
        var slots=new RailSlots(2,key->key.equals("minetorio:pistol#charged")?1:5);slots.setBar(1);
        slots.insert(0,"minetorio:pistol#charged",1,false);
        assertEquals(2,slots.waitSnapshot().size());assertFalse(slots.waitSnapshot().getFirst().canInsert());
        assertTrue(evaluate(List.of(wait(Type.FULL,0)),new Context(0,0,List.of(slots.waitSnapshot()),List.of(),Optional.empty(),Optional.empty())));
        slots.extract(0,1,false);slots.insert(0,"minetorio:pistol",1,false);assertTrue(slots.waitSnapshot().getFirst().canInsert());
        slots.setFilter(0,Optional.of("minetorio:iron_plate"));assertFalse(slots.waitSnapshot().getFirst().canInsert());
        slots.setBar(0);assertFalse(evaluate(List.of(wait(Type.EMPTY,0)),new Context(0,0,List.of(slots.waitSnapshot()),List.of(),Optional.empty(),Optional.empty())));
    }
    @Test void fractionalFluidAndLargeItemCountsAreNotNarrowedToInt() {
        var slot=new RailSlots.WaitSlot(new RailSlots.Slot("minetorio:iron_plate",Integer.MAX_VALUE,Optional.empty()),false);
        var c=new Context(0,0,List.of(List.of(slot,slot)),List.of(new RailwayState.Fluid("water",0.5,25000)),Optional.empty(),Optional.empty());
        assertTrue(evaluate(List.of(condition(Type.ITEM_COUNT,new SignalKey(SignalKind.ITEM,"minetorio:iron_plate"),Comparator.GT,Integer.MAX_VALUE)),c));
        assertTrue(evaluate(List.of(condition(Type.FLUID_COUNT,new SignalKey(SignalKind.FLUID,"water"),Comparator.GT,0)),c));
    }
}
