package dev.minetorio.core.oil;

import dev.minetorio.core.fluid.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TypedFluidTest {
    @Test void unusedZeroCapacityPortsHaveAnEmptyGauge() {
        FluidBox unused=new FluidBox(0);
        assertEquals(0,unused.fill());
        assertEquals(0,unused.insert("water",100));
    }
    @Test void wrongFluidCannotPoisonEmptyRecipeTankThroughSegmentOrContact() {
        FluidNetwork network=new FluidNetwork();
        FluidBox acid=new FluidBox(100,"sulfuric_acid"), water=new FluidBox(100);
        water.insert("water",100);
        network.addNode(1,100,"water",100);
        network.setAttachments(List.of(new FluidNetwork.Attachment(1,FluidNetwork.Port.of(acid,PortKind.INPUT,null))));
        network.setContacts(List.of(new FluidNetwork.Contact(FluidNetwork.Port.of(water,PortKind.OUTPUT,null),FluidNetwork.Port.of(acid,PortKind.INPUT,null))));
        network.tick();
        assertEquals(0,acid.amount());
        assertEquals(100,water.amount());
        assertEquals(100,network.segment(1).amount());
    }
}
