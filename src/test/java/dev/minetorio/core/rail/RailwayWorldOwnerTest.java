package dev.minetorio.core.rail;
import dev.minetorio.core.data.RailData.StockKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.minetorio.core.rail.RailwayState.*;
class RailwayWorldOwnerTest {
    @Test void brokenRailRetainsLastAuthoritativePoses() {
        var f=new TrainTestFixture();var c=f.car(StockKind.CARGO,false);var t=f.train(50,0,Mode.MANUAL,c);
        c.cargo().orElseThrow().insert(0,"minetorio:iron_plate",23,false);
        var before=TrainMotion.carPoses(f.state,t.id());
        f.state.replaceRails(java.util.List.of());
        assertEquals(Status.PATH_LOST,f.state.trains().get(t.id()).status());
        assertEquals(before,TrainMotion.carPoses(f.state,t.id()),"Последняя поза принадлежит owner даже без рельса");
        assertEquals(23,c.cargo().orElseThrow().get(0).count());
    }
    @Test void onlyPhysicalSpanPreventsRailRemoval() {
        var f=new TrainTestFixture();var car=f.car(StockKind.CARGO,false);var train=f.train(50,0,Mode.MANUAL,car);
        assertTrue(dev.minetorio.content.rail.RailPlacement.occupied(f.state,train.head().orElseThrow().traversal().piece()));
        assertFalse(dev.minetorio.content.rail.RailPlacement.occupied(f.state,f.tape.get(40).piece()),"Будущая лента не является физической занятостью");
        assertFalse(dev.minetorio.content.rail.RailPlacement.occupied(f.state,f.tape.get(1).piece()),"Старая история не является физической занятостью");
    }
}
