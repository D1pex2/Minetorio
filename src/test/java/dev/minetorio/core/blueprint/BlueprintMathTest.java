package dev.minetorio.core.blueprint;

import static dev.minetorio.core.drag.DragDir.EAST;
import static dev.minetorio.core.drag.DragDir.NORTH;
import static dev.minetorio.core.drag.DragDir.SOUTH;
import static dev.minetorio.core.drag.DragDir.WEST;
import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.blueprint.Blueprints.Below;
import dev.minetorio.core.drag.DragDir;
import dev.minetorio.core.robot.Cell;
import dev.minetorio.core.robot.DeconstructionMarks;
import dev.minetorio.core.robot.GhostReach;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlueprintMathTest {
    private static Set<List<Integer>> cellSet(BlueprintPart p) {
        Set<List<Integer>> s = new HashSet<>();
        for (int[] c : p.cells()) s.add(List.of(c[0], c[1], c[2]));
        return s;
    }

    @Test void rotationTurnsDirectionsClockwise() {
        BlueprintTransform r = BlueprintTransform.IDENTITY.rotated();
        assertEquals(EAST, r.apply(NORTH));
        assertEquals(SOUTH, r.apply(EAST));
        assertEquals(WEST, r.apply(SOUTH));
        assertEquals(NORTH, r.apply(WEST));
        assertEquals(IDENTITY_AFTER_FOUR, r.rotated().rotated().rotated());
    }

    private static final BlueprintTransform IDENTITY_AFTER_FOUR = BlueprintTransform.IDENTITY;

    @Test void mirrorsFlipTheirAxisOnly() {
        BlueprintTransform f = BlueprintTransform.IDENTITY.flippedX();
        assertEquals(WEST, f.apply(EAST));
        assertEquals(NORTH, f.apply(NORTH));
        BlueprintTransform g = BlueprintTransform.IDENTITY.flippedZ();
        assertEquals(SOUTH, g.apply(NORTH));
        assertEquals(EAST, g.apply(EAST));
        // flipping twice is the identity, also after a rotation
        BlueprintTransform r = BlueprintTransform.IDENTITY.rotated();
        assertEquals(r, r.flippedX().flippedX());
        assertEquals(r, r.flippedZ().flippedZ());
        // a world flip after a turn: east turned to south, then z flipped -> north
        assertEquals(NORTH, r.flippedZ().apply(EAST));
        assertEquals(WEST, r.flippedX().apply(NORTH));
    }

    @Test void transformBitsRoundTrip() {
        for (int rot = 0; rot < 4; rot++) {
            for (boolean m : new boolean[] {false, true}) {
                BlueprintTransform t = new BlueprintTransform(rot, m);
                assertEquals(t, BlueprintTransform.ofBits(t.bits()));
            }
        }
    }

    @Test void positionsRotateAndRenormalize() {
        // a belt line running east: (0,0) (1,0) (2,0)
        List<BlueprintPart> line = List.of(
                BlueprintPart.single("belt", 0, 0, 0, EAST, 0),
                BlueprintPart.single("belt", 1, 0, 0, EAST, 0),
                BlueprintPart.single("belt", 2, 0, 0, EAST, 0));
        List<BlueprintPart> turned = BlueprintTransform.IDENTITY.rotated().apply(line);
        // now running south: (0,0) (0,1) (0,2)
        for (int i = 0; i < 3; i++) {
            assertEquals(0, turned.get(i).x());
            assertEquals(i, turned.get(i).z());
            assertEquals(SOUTH, turned.get(i).facing());
        }
        assertArrayEquals(new int[] {1, 1, 3}, Blueprints.size(turned));
        List<BlueprintPart> mirrored = BlueprintTransform.IDENTITY.flippedX().apply(line);
        assertEquals(2, mirrored.get(0).x());
        assertEquals(0, mirrored.get(2).x());
        assertEquals(WEST, mirrored.get(0).facing());
    }

    /** A curve: belt going east then turning south. Mirrored, it turns the other way, still connected. */
    @Test void beltCurveMirrorsIntoTheOtherCurve() {
        List<BlueprintPart> curve = List.of(
                BlueprintPart.single("belt", 0, 0, 0, EAST, 0),
                BlueprintPart.single("belt", 1, 0, 0, SOUTH, 0),
                BlueprintPart.single("belt", 1, 0, 1, SOUTH, 0));
        List<BlueprintPart> m = BlueprintTransform.IDENTITY.flippedX().apply(curve);
        // first belt now at x=1 pointing west into the corner at x=0
        assertEquals(1, m.get(0).x());
        assertEquals(WEST, m.get(0).facing());
        assertEquals(0, m.get(1).x());
        assertEquals(SOUTH, m.get(1).facing());
        // the first belt still feeds the corner: its front cell is the corner's cell
        assertEquals(m.get(1).x(), m.get(0).x() + m.get(0).facing().dx());
        assertEquals(m.get(1).z(), m.get(0).z() + m.get(0).facing().dz());
    }

    /** An underground pair keeps the entrance behind the exit relative to its facing. */
    @Test void undergroundPairStaysInOrder() {
        List<BlueprintPart> pair = List.of(
                BlueprintPart.single("ug", 0, 0, 0, EAST, 0),
                BlueprintPart.single("ug", 4, 0, 0, EAST, 1));
        for (BlueprintTransform t : all()) {
            List<BlueprintPart> p = t.apply(pair);
            BlueprintPart in = p.get(0), out = p.get(1);
            assertEquals(in.facing(), out.facing());
            assertEquals(out.x(), in.x() + 4 * in.facing().dx(), "transform " + t);
            assertEquals(out.z(), in.z() + 4 * in.facing().dz(), "transform " + t);
            assertEquals(0, in.variant());
            assertEquals(1, out.variant());
        }
    }

    /** An inserter between a belt and a machine keeps picking from the belt and dropping into the machine. */
    @Test void inserterKeepsItsEnds() {
        List<BlueprintPart> parts = List.of(
                BlueprintPart.single("belt", 0, 0, 0, NORTH, 0),
                BlueprintPart.single("inserter", 1, 0, 0, EAST, 0),
                new BlueprintPart("asm", 2, 0, 1, NORTH, 3, 3, 2, 0));
        for (BlueprintTransform t : all()) {
            List<BlueprintPart> p = t.apply(parts);
            BlueprintPart belt = p.get(0), ins = p.get(1), asm = p.get(2);
            DragDir f = ins.facing();
            assertEquals(belt.x(), ins.x() - f.dx(), "pickup x " + t);
            assertEquals(belt.z(), ins.z() - f.dz(), "pickup z " + t);
            int dx = ins.x() + f.dx(), dz = ins.z() + f.dz();
            assertTrue(dx >= asm.minX() && dx <= asm.maxX() && dz >= asm.minZ() && dz <= asm.maxZ(), "drop outside machine " + t);
        }
    }

    @Test void multiblockOriginIsTheLeftFrontCornerAfterEveryTransform() {
        // a 3 wide 5 deep machine facing north with its origin at (0, 0, 4): x 0..2, z 0..4
        BlueprintPart engine = new BlueprintPart("engine", 0, 0, 4, NORTH, 3, 5, 2, 0);
        assertEquals(0, engine.minX());
        assertEquals(2, engine.maxX());
        assertEquals(0, engine.minZ());
        assertEquals(4, engine.maxZ());
        for (BlueprintTransform t : all()) {
            BlueprintPart p = t.apply(List.of(engine)).getFirst();
            int[] size = Blueprints.size(List.of(p));
            boolean along = p.facing() == NORTH || p.facing() == SOUTH;
            assertArrayEquals(along ? new int[] {3, 2, 5} : new int[] {5, 2, 3}, size, "size " + t);
            // footprint from the origin with the new facing covers exactly the box
            assertEquals(30, cellSet(p).size());
            assertEquals(0, p.minX());
            assertEquals(0, p.minZ());
        }
        // a 2x1 splitter facing east (width along south) mirrored front-back: facing east still, cells swapped
        BlueprintPart splitter = new BlueprintPart("splitter", 0, 0, 0, EAST, 2, 1, 1, 0);
        assertEquals(1, splitter.maxZ());
        BlueprintPart m = BlueprintTransform.IDENTITY.flippedZ().apply(List.of(splitter)).getFirst();
        assertEquals(EAST, m.facing());
        // width runs south from the origin, so the origin stays at the north cell
        assertEquals(0, m.z());
        assertEquals(1, m.maxZ());
    }

    @Test void sizeBounds() {
        List<BlueprintPart> ok = List.of(BlueprintPart.single("b", 0, 0, 0, null, 0), BlueprintPart.single("b", 63, 31, 63, null, 0));
        assertTrue(Blueprints.withinLimits(ok));
        assertArrayEquals(new int[] {64, 32, 64}, Blueprints.size(ok));
        assertFalse(Blueprints.withinLimits(List.of(BlueprintPart.single("b", 64, 0, 0, null, 0))));
        assertFalse(Blueprints.withinLimits(List.of(BlueprintPart.single("b", 0, 32, 0, null, 0))));
        assertFalse(Blueprints.withinLimits(List.of(BlueprintPart.single("b", -1, 0, 0, null, 0))));
        // a pole three high at the top layer counts its height
        assertFalse(Blueprints.withinLimits(List.of(new BlueprintPart("pole", 0, 30, 0, null, 1, 1, 3, 0))));
        List<BlueprintPart> many = new ArrayList<>();
        for (int i = 0; i <= Blueprints.MAX_PARTS; i++) many.add(BlueprintPart.single("b", i % 64, 0, (i / 64) % 64, null, 0));
        assertFalse(Blueprints.withinLimits(many));
    }

    @Test void normalizeAndCentre() {
        List<BlueprintPart> parts = Blueprints.normalized(List.of(
                BlueprintPart.single("b", 10, 5, -3, null, 0), BlueprintPart.single("b", 14, 6, -1, null, 0)));
        assertEquals(0, parts.get(0).x());
        assertEquals(0, parts.get(0).y());
        assertEquals(0, parts.get(0).z());
        assertEquals(4, parts.get(1).x());
        int[] size = Blueprints.size(parts);
        assertArrayEquals(new int[] {5, 2, 3}, size);
        assertArrayEquals(new int[] {-2, -1}, Blueprints.anchorOffset(size));
    }

    @Test void pasteOrderIsBottomUp() {
        List<BlueprintPart> parts = List.of(
                BlueprintPart.single("b", 0, 2, 0, null, 0), BlueprintPart.single("b", 1, 0, 0, null, 0), BlueprintPart.single("b", 2, 1, 0, null, 0));
        assertEquals(List.of(1, 2, 0), Blueprints.pasteOrder(parts));
    }

    @Test void supportRule() {
        assertTrue(Blueprints.supported(List.of(Below.SOLID, Below.GHOST)));
        assertTrue(Blueprints.supported(List.of(Below.GHOST)));
        assertFalse(Blueprints.supported(List.of(Below.SOLID, Below.EMPTY)));
        assertTrue(Blueprints.supported(List.of()));
    }

    @Test void ghostReach() {
        assertTrue(GhostReach.inReach(40, 0, 10));
        assertFalse(GhostReach.inReach(100, 0, 10));
        assertFalse(GhostReach.inReach(40, 0, 2)); // view distance 2 chunks: 32 blocks
        assertTrue(GhostReach.inReach(0, 64, 0));
        assertFalse(GhostReach.inReach(46, 46, 32));
    }

    @Test void deconstructionMarks() {
        DeconstructionMarks m = new DeconstructionMarks();
        Cell c = new Cell(1, 2, 3);
        assertTrue(m.mark(c));
        assertFalse(m.mark(c));
        assertTrue(m.isMarked(c));
        assertTrue(m.unmark(c));
        assertFalse(m.isMarked(c));
        assertEquals(0, m.size());
    }

    private static List<BlueprintTransform> all() {
        List<BlueprintTransform> out = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            out.add(new BlueprintTransform(r, false));
            out.add(new BlueprintTransform(r, true));
        }
        return out;
    }
}
