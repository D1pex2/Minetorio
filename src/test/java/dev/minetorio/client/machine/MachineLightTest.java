package dev.minetorio.client.machine;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.content.multiblock.MultiblockShape;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MachineLightTest {
    @Test
    void daylightComesFromOutsideTheDarkControllerWithoutCreatingBlockLight() {
        assertEquals(0xF00000, MachineRender.exteriorLight(0, 0xD00000, 0xF00000, 0xB00000));
    }

    @Test
    void unlitRoomStaysDark() {
        assertEquals(0, MachineRender.exteriorLight(0, 0, 0, 0, 0, 0));
    }

    @Test
    void torchlightDoesNotInventSkyLight() {
        assertEquals(0xA0, MachineRender.exteriorLight(0, 0, 0xA0, 0x70));
    }

    @Test
    void surroundingChannelsAndFallbackArePreservedIndependently() {
        assertEquals(0xC000A0, MachineRender.exteriorLight(0x300050, 0x1000A0, 0xC00030));
        assertEquals(0x5000B0, MachineRender.exteriorLight(0x5000B0));
    }

    @Test
    void probesUseExposedTopAndFourSidesOfTheRotatedFootprint() {
        BlockPos origin = new BlockPos(10, 64, 20);
        var shape = new MultiblockShape(3, 3, 2);
        assertEquals(Set.of(new BlockPos(11, 66, 19), new BlockPos(9, 64, 19), new BlockPos(13, 64, 19),
                new BlockPos(11, 64, 17), new BlockPos(11, 64, 21)), Set.copyOf(MachineRender.lightSamplePositions(origin, Direction.NORTH, shape)));
        assertEquals(Set.of(new BlockPos(11, 66, 21), new BlockPos(9, 64, 21), new BlockPos(13, 64, 21),
                new BlockPos(11, 64, 19), new BlockPos(11, 64, 23)), Set.copyOf(MachineRender.lightSamplePositions(origin, Direction.EAST, shape)));
    }

    @Test
    void everyFacingKeepsFiveProbesOutsideTheMachineAtNegativeCoordinates() {
        BlockPos origin = new BlockPos(-10, 30, -20);
        var shape = new MultiblockShape(2, 5, 3);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var probes = MachineRender.lightSamplePositions(origin, facing, shape);
            var filled = Set.copyOf(shape.footprint(origin, facing));
            assertEquals(5, Set.copyOf(probes).size(), "fixed probe count for " + facing);
            for (BlockPos probe : probes) assertFalse(filled.contains(probe), "sample inside the machine: " + probe + " " + facing);
        }
    }
}
