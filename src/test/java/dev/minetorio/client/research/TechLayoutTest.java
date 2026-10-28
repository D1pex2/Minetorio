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
            for (String pre : t.prerequisites()) assertTrue(layout.cell(pre).column() < c.column(), t.id() + " after " + pre);
        }
        assertEquals(new TechLayout.Cell(0, 0), layout.cell("automation"));
        assertEquals(1, layout.cell("automation_2").column());
        assertEquals(4, layout.columns()); // logistic_system after logistic_robotics after construction_robotics
        // column 1: fast inserter, automation 2, logistics 2, construction robotics, electric energy distribution 1, solar energy;
        // column 2: stack inserter, fluid handling, EED 2, accumulators, logistic robotics, personal roboport
        assertEquals(6, layout.rows());
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
    }

    private static Technology tech(String id, String... prereqs) {
        return new Technology(id, List.of(prereqs), Map.of("minetorio:automation_science_pack", 1), 1, 20, List.of());
    }
}
