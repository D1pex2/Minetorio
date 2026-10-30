package dev.minetorio.core.rail;
import java.math.BigDecimal;
import dev.minetorio.core.data.RailData.StockKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.minetorio.core.rail.RailwayState.*;
class TrainProposalTest {
    @Test void proposalDoesNotSpendUntilCommitAndCannotReplay() {
        var f=new TrainTestFixture();var car=f.car(StockKind.LOCOMOTIVE,false);car.setActiveFuel("minetorio:coal",new BigDecimal("100000"));var train=f.train(50,.5,Mode.MANUAL,car);
        var proposal=TrainMotion.propose(f.state,train.id(),TrainMotionTest.GO,0).orElseThrow();
        assertEquals(train,f.state.trains().get(train.id()));assertEquals(new BigDecimal("100000"),car.remainingJ());
        assertNotEquals(proposal.poses(0),proposal.poses(1));assertTrue(proposal.commit());
        var after=f.state.trains().get(train.id());assertFalse(proposal.commit());assertEquals(after,f.state.trains().get(train.id()));assertEquals(0,new BigDecimal("90000").compareTo(car.remainingJ()));
    }
    @Test void mutationOfFuelOrGraphRejectsPreparedMoveWithoutSpending() {
        for(boolean fuel:new boolean[]{true,false}) {
            var f=new TrainTestFixture();var car=f.car(StockKind.LOCOMOTIVE,false);car.setActiveFuel("minetorio:coal",new BigDecimal("100000"));var train=f.train(50,.5,Mode.MANUAL,car);
            var proposal=TrainMotion.propose(f.state,train.id(),TrainMotionTest.GO,0).orElseThrow();
            if(fuel)car.fuel().orElseThrow().insert(0,"minetorio:coal",1,false);else f.state.replaceRails(f.state.rails());
            assertFalse(proposal.commit());assertEquals(new BigDecimal("100000"),car.remainingJ());assertEquals(train.head(),f.state.trains().get(train.id()).head());
        }
    }
    @Test void contactCommitFollowsPrefixAndNeverInventsFuelOrDistance() {
        var f=new TrainTestFixture();var car=f.car(StockKind.LOCOMOTIVE,false);car.setActiveFuel("minetorio:coal",new BigDecimal("100000"));var train=f.train(50,.5,Mode.MANUAL,car);
        var proposal=TrainMotion.propose(f.state,train.id(),TrainMotionTest.GO,0).orElseThrow();var contact=proposal.poses(.4);
        assertTrue(proposal.commit(.4,0));assertEquals(contact,TrainMotion.carPoses(f.state,train.id()));assertEquals(0,f.state.trains().get(train.id()).speed());assertEquals(0,new BigDecimal("90000").compareTo(car.remainingJ()));
    }
}
