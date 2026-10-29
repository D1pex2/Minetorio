package dev.minetorio.core.rail;
import java.math.BigDecimal;
import java.util.UUID;
import dev.minetorio.core.data.RailData.StockKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TrainFuelTest {
    @Test void crossingFuelBoundaryConservesJoules() {
        var state = new RailwayState(key -> 100);
        var car = state.createCar(UUID.randomUUID(), StockKind.LOCOMOTIVE, false);
        car.setActiveFuel("minetorio:coal", new BigDecimal("0.25"));
        car.fuel().orElseThrow().insert(0, "minetorio:solid_fuel", 1, false);
        var draw = TrainFuel.draw(car, BigDecimal.ONE);
        assertEquals(0, draw.deliveredJ().compareTo(BigDecimal.ONE));
        assertEquals(2, draw.portions().size());
        assertEquals(0, car.remainingJ().compareTo(new BigDecimal("11999999.25")));
    }
    @Test void exactExhaustionRetainsLastModifier() {
        var car = new RailwayState(key -> 100).createCar(UUID.randomUUID(), StockKind.LOCOMOTIVE, false);
        car.setActiveFuel("minetorio:solid_fuel", BigDecimal.ZERO);
        assertEquals(1.05, TrainFuel.activeTopSpeed(car));
        assertFalse(TrainFuel.available(car));
    }

    @Test void fractionalDrawsAndInvalidRequestsNeverRoundOrMutate() {
        var car = new RailwayState(key -> 100).createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,false);
        car.setActiveFuel("minetorio:coal",new BigDecimal("200"));
        for(int i=0;i<400;i++) TrainFuel.draw(car,new BigDecimal("0.25"));
        assertEquals(0,car.remainingJ().compareTo(new BigDecimal("100")));
        for(BigDecimal q:java.util.List.of(new BigDecimal("-0.25"),new BigDecimal("10000.01"))) {
            assertThrows(IllegalArgumentException.class,()->TrainFuel.draw(car,q));
            assertThrows(IllegalArgumentException.class,()->TrainFuel.preview(car,q));
            assertEquals(0,car.remainingJ().compareTo(new BigDecimal("100")));
        }
        assertTrue(TrainFuel.draw(car,BigDecimal.ZERO).portions().isEmpty());
        assertEquals("minetorio:coal",car.activeFuel());
    }
    @Test void previewMatchesDrawAndPreservesFullComponentKeys() {
        var state=new RailwayState(key->100);
        var a=state.createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,false);
        var b=state.createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,false);
        String key="minetorio:solid_fuel#{custom_name:test}";
        for(var c:java.util.List.of(a,b)) {
            c.setActiveFuel("minetorio:coal",new BigDecimal("0.25"));
            c.fuel().get().insert(0,key,2,false);
        }
        var slots=a.fuel().get().snapshot();
        var preview=TrainFuel.preview(a,BigDecimal.ONE);
        assertEquals(preview,TrainFuel.draw(b,BigDecimal.ONE));
        assertEquals(slots,a.fuel().get().snapshot()); assertEquals(new BigDecimal("0.25"),a.remainingJ());
        assertEquals(key,b.fuel().get().get(0).key()); assertEquals(1,b.fuel().get().get(0).count());
        assertEquals("minetorio:solid_fuel",b.activeFuel());
        double force=preview.portions().stream().mapToDouble(p->10*p.acceleration()*p.joules().doubleValue()/10000/2000).sum();
        assertEquals(5.75e-7,force,1e-18);
    }
    @Test void zeroDrawAndPreviewKeepExhaustedModifierUntilPositiveActivation() {
        var car=new RailwayState(key->100).createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,false);
        car.setActiveFuel("minetorio:solid_fuel",new BigDecimal("0.25"));
        TrainFuel.draw(car,new BigDecimal("0.25"));
        car.fuel().get().insert(0,"minetorio:coal",1,false);
        TrainFuel.draw(car,BigDecimal.ZERO); TrainFuel.preview(car,BigDecimal.ONE);
        assertEquals(1.05,TrainFuel.activeTopSpeed(car));
        TrainFuel.draw(car,BigDecimal.ONE); assertEquals(1,TrainFuel.activeTopSpeed(car));
        assertThrows(IllegalArgumentException.class,()->car.setActiveFuel("minetorio:missing",BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,()->car.setActiveFuel("",BigDecimal.ONE));
    }
}
