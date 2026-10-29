package dev.minetorio.core.combat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NestPlacementTest {
    @Test void oneCandidateInEveryGridCellIncludingNegativeCoordinates() {
        for(int gx=-4;gx<=4;gx++)for(int gz=-4;gz<=4;gz++) {
            int count=0;
            for(int x=gx*8;x<gx*8+8;x++)for(int z=gz*8;z<gz*8+8;z++)if(NestPlacement.candidate(41,x,z))count++;
            assertEquals(1,count);
        }
    }
    @Test void startingSafetyUsesRealSpawnAndIncludesBoundary() {
        assertFalse(NestPlacement.outsideStart(1000,1000,1000,1000));
        assertFalse(NestPlacement.outsideStart(1319,1000,1000,1000));
        assertTrue(NestPlacement.outsideStart(1320,1000,1000,1000));
    }
}
