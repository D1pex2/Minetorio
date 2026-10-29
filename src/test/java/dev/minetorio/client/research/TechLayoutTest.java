package dev.minetorio.client.research;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.research.TechTree;
import dev.minetorio.core.research.Technology;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TechLayoutTest {
    @Test
    void prerequisitesAreInEarlierColumns() {
        TechTree tree = Phase1Data.techTree();
        TechLayout layout = new TechLayout(tree);
        Set<TechLayout.Cell> used = new HashSet<>();
        for (Technology t : tree.all()) {
            TechLayout.Cell c = layout.cell(t.id());
            assertNotNull(c, t.id());
            assertTrue(used.add(c), "two technologies share " + c);
            assertTrue(c.column() >= 0 && c.row() >= 0, "negative tree position for " + t.id());
            for (String pre : t.prerequisites()) {
                if (tree.get(pre).isPresent()) assertTrue(layout.cell(pre).column() < c.column(), t.id() + " after " + pre);
                else assertTrue(layout.futurePrerequisites(t.id()).contains(pre), t.id() + " must explain unavailable " + pre);
            }
        }
    }

    @Test
    void chainsGetOneColumnPerStep() {
        TechTree tree = new TechTree(List.of(
                tech("c", "b"), tech("a"), tech("b", "a"), tech("d", "a", "c", "missing")));
        TechLayout layout = new TechLayout(tree);
        assertEquals(0, layout.cell("a").column());
        assertEquals(1, layout.cell("b").column());
        assertEquals(2, layout.cell("c").column());
        assertEquals(3, layout.cell("d").column());
        assertEquals(4, layout.columns());
        assertEquals(1, layout.rows());
        assertEquals(List.of("missing"), layout.futurePrerequisites("d"));
        assertEquals(List.of(), layout.futurePrerequisites("b"));
    }

    @Test
    void wideTreeCanExposeBothItsFirstAndLastColumns() {
        var view = new TechLayout.Viewport(320, 180, 700, 300, 0, 0);
        assertEquals(0, view.originX(), "the first column must start inside the viewport");
        var end = view.scrolled(1000, 1000);
        assertEquals(-380, end.originX());
        assertEquals(-120, end.originY());
        assertEquals(320, end.originX() + 700, "the last column must end at the viewport edge");
        assertEquals(180, end.originY() + 300, "the last row must end at the viewport edge");
        assertEquals(view, end.scrolled(-1000, -1000), "panning back must expose the first column again");
    }

    @Test
    void fittingTreeStaysCenteredWithoutScrollingPastItsEdges() {
        var view = new TechLayout.Viewport(500, 180, 300, 100, 0, 0);
        assertEquals(100, view.originX());
        assertEquals(0, view.originY(), "rows remain top-aligned");
        assertEquals(view, view.scrolled(1000, -1000));
    }

    @Test
    void horizontalPanPreservesVerticalPositionAndResizeClampsOffsets() {
        var start = new TechLayout.Viewport(320, 180, 700, 300, 0, 50);
        var moved = start.scrolled(125, 0);
        assertEquals(-125, moved.originX());
        assertEquals(-50, moved.originY());
        var resized = new TechLayout.Viewport(800, 400, 700, 300, moved.scrollX(), moved.scrollY());
        assertEquals(50, resized.originX());
        assertEquals(0, resized.originY());
    }

    private static Technology tech(String id, String... prereqs) {
        return new Technology(id, List.of(prereqs), Map.of("minetorio:automation_science_pack", 1), 1, 20, List.of());
    }
}
