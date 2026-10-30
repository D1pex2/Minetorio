package dev.minetorio.core.rail;

import dev.minetorio.content.rail.RailwayNbt;
import dev.minetorio.core.data.RailData.StockKind;
import java.util.*;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import static dev.minetorio.core.rail.RailGeometry.*;
import static org.junit.jupiter.api.Assertions.*;

class RailwayNbtSchemaTest {
    @Test void schemaSixStillReadsOneToFiveWithoutDroppingSchemaFiveOwnerPose() {
        var state=new RailwayState(key->64);var car=state.createCar(UUID.randomUUID(),StockKind.CARGO,false);car.setHp(312.5);
        var pose=new Pose(new Vec(100,80,-200),new Vec(0,0,-1));state.restoreLastPoses(Map.of(car.id(),pose));var current=RailwayNbt.write(state,null);
        assertEquals(6,current.getInt("schemaVersion"));
        for(int schema=1;schema<=5;schema++) {
            var old=current.copy();old.putInt("schemaVersion",schema);old.remove("worldSignals");var restored=RailwayNbt.read(old,null);
            assertEquals(312.5,restored.cars().get(car.id()).hp());assertEquals(schema>=5?Map.of(car.id(),pose):Map.of(),restored.lastPoses());assertEquals(6,RailwayNbt.write(restored,null).getInt("schemaVersion"));
        }
        var invalid=current.copy();invalid.getList("lastPoses",Tag.TAG_COMPOUND).getCompound(0).putDouble("x",Double.NaN);assertThrows(IllegalArgumentException.class,()->RailwayNbt.read(invalid,null));
        var future=current.copy();future.putInt("schemaVersion",7);assertThrows(IllegalArgumentException.class,()->RailwayNbt.read(future,null));
    }
}
