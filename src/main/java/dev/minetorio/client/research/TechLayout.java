package dev.minetorio.client.research;

import dev.minetorio.core.research.TechTree;
import dev.minetorio.core.research.Technology;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Grid position of every technology in the research screen: the column is the technology's depth
 * (0 without prerequisites, else one more than its deepest prerequisite), so prerequisite lines
 * always run left to right; rows follow the tree's order within a column.
 */
public final class TechLayout {
    public record Cell(int column, int row) {}

    /** Tree viewport, independent of the game's renderer and input APIs. */
    public record Viewport(int width, int height, int contentWidth, int contentHeight, int scrollX, int scrollY) {
        public Viewport {
            width = Math.max(0, width);
            height = Math.max(0, height);
            contentWidth = Math.max(0, contentWidth);
            contentHeight = Math.max(0, contentHeight);
            scrollX = Math.max(0, Math.min(scrollX, Math.max(0, contentWidth - width)));
            scrollY = Math.max(0, Math.min(scrollY, Math.max(0, contentHeight - height)));
        }

        public int originX() { return Math.max(0, (width - contentWidth) / 2) - scrollX; }
        public int originY() { return -scrollY; }

        public Viewport scrolled(double dx, double dy) {
            return new Viewport(width, height, contentWidth, contentHeight,
                    (int) Math.round(scrollX + dx), (int) Math.round(scrollY + dy));
        }
    }

    private final Map<String, Cell> cells = new LinkedHashMap<>();
    private final Map<String, List<String>> futurePrerequisites = new HashMap<>();
    private final int columns;
    private final int rows;

    public TechLayout(TechTree tree) {
        Map<String, Integer> depth = new HashMap<>();
        for (Technology t : tree.all()) depth(tree, t.id(), depth, 0);
        List<Integer> rowsPerColumn = new ArrayList<>();
        for (Technology t : tree.all()) {
            int col = depth.get(t.id());
            while (rowsPerColumn.size() <= col) rowsPerColumn.add(0);
            int row = rowsPerColumn.get(col);
            rowsPerColumn.set(col, row + 1);
            cells.put(t.id(), new Cell(col, row));
            futurePrerequisites.put(t.id(), t.prerequisites().stream().filter(pre -> tree.get(pre).isEmpty()).toList());
        }
        columns = rowsPerColumn.size();
        rows = rowsPerColumn.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** Depth of {@code id}; unknown prerequisites count as depth 0, cycles are cut after the tree's size. */
    private static int depth(TechTree tree, String id, Map<String, Integer> memo, int guard) {
        Integer known = memo.get(id);
        if (known != null) return known;
        Technology t = tree.get(id).orElse(null);
        if (t == null || guard > tree.all().size()) return 0;
        int d = 0;
        for (String pre : t.prerequisites()) {
            if (tree.get(pre).isPresent()) d = Math.max(d, depth(tree, pre, memo, guard + 1) + 1);
        }
        memo.put(id, d);
        return d;
    }

    public Cell cell(String techId) { return cells.get(techId); }

    public Map<String, Cell> cells() { return cells; }

    public int columns() { return columns; }

    public int rows() { return rows; }

    /** Exact prerequisites whose technology is reserved for a later stage rather than implemented now. */
    public List<String> futurePrerequisites(String techId) { return futurePrerequisites.getOrDefault(techId, List.of()); }
}
