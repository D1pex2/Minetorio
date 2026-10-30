package dev.minetorio.core.rail;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;
import dev.minetorio.core.combat.CombatRules;
import dev.minetorio.core.data.RailData.StockKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.minetorio.core.rail.RailwayState.*;
class TrainNativeImpactTest {
    @Test void nativeSmallResistanceIsProportionalToRawDamage() {
        var data=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/factorio-1.1.110/rail-impact-native.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        for(var row:data.getAsJsonObject("cases").getAsJsonArray("direct_resistance_events")) {
            var event=row.getAsJsonObject();double raw=event.get("original").getAsDouble(),expected=event.get("final").getAsDouble();
            assertEquals(expected,CombatRules.physicalDamage(raw,50,60),Math.max(1e-9,expected*2e-7),"raw="+raw);
        }
    }
    @Test void automaticTrainsAlreadyInsideUnsignalledBlockCanApproachPhysicalContact() {
        var f=new TrainTestFixture();var a=f.car(StockKind.LOCOMOTIVE,false);var b=f.car(StockKind.LOCOMOTIVE,true);
        a.fuel().orElseThrow().insert(0,"minetorio:coal",1,false);b.fuel().orElseThrow().insert(0,"minetorio:coal",1,false);
        var ta=f.train(50,0,Mode.AUTOMATIC,a);var tb=f.train(80,0,Mode.AUTOMATIC,b);
        var pa=TrainReservationsTest.plan(f,ta.id(),1,0,100);var pb=TrainReservationsTest.plan(f,tb.id(),-1,0,20);
        assertTrue(pa.safeDistance()>0,"Сигналы не останавливают поезд телепатически внутри общего блока");
        assertTrue(TrainReservations.commit(f.state,ta.id(),pa,1,0));assertTrue(TrainReservations.commit(f.state,tb.id(),pb,-1,0));
        TrainMotion.step(f.state,ta.id(),TrainMotionTest.GO,0);TrainMotion.step(f.state,tb.id(),new TrainMotion.Control(-1,false,TrainMotion.Turn.STRAIGHT),0);
        assertTrue(f.state.trains().get(ta.id()).speed()>0);assertTrue(f.state.trains().get(tb.id()).speed()<0);
    }
}
