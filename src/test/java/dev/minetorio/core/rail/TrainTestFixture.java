package dev.minetorio.core.rail;
import java.util.*;
import dev.minetorio.core.rail.RailwayState.Car;
import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.RailwayState.*;
import dev.minetorio.core.data.RailData.StockKind;
final class TrainTestFixture {
    final RailwayState state = new RailwayState(key -> 100);
    final List<Traversal> tape = new ArrayList<>();
    TrainTestFixture() {
        List<Piece> rails = new ArrayList<>();
        for (int i=0;i<600;i++) { var p=new Piece(new UUID(1,i),new Point(0,0,-2*i),Shape.STRAIGHT,0,false); rails.add(p); tape.add(new Traversal(p.id(),true)); }
        state.replaceRails(rails);
    }
    Car car(StockKind kind, boolean reversed) { return state.createCar(UUID.randomUUID(),kind,reversed); }
    Train train(double q,double speed,Mode mode,Car... cars) {
        var head=state.locate(tape,q);
        return state.createTrain(new Train(UUID.randomUUID(),Arrays.stream(cars).map(Car::id).toList(),Optional.of(head.cursor()),head.index(),tape,speed,mode,Status.MANUAL_CONTROL,0,0,0));
    }

    static List<Piece> loop() {
        List<Piece> result=new ArrayList<>(); int[][] translations={{0,0},{12,-12},{24,0},{12,12}}; long id=1;
        for(int q=0;q<4;q++) for(Piece p:RailGeometryTest.symmetry(RailGeometryTest.quarter(1),q,false))
            result.add(new Piece(new UUID(7,id++),new Point(p.origin().x()+translations[q][0],0,p.origin().z()+translations[q][1]),p.shape(),p.quarterTurns(),p.mirrored()));
        return result;
    }
    static List<Traversal> loopTape(RailwayState state,int rounds) {
        Traversal current=new Traversal(state.rails().getFirst().id(),true); List<Traversal> result=new ArrayList<>();
        for(int i=0;i<12*rounds;i++) { result.add(current); current=state.graph().next(current).getFirst(); }
        return result;
    }
}
