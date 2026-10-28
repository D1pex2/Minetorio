package dev.minetorio.core.blueprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Blueprint math and limits: normalizing parts to start at (0, 0, 0), the size, where a paste's
 * cells go relative to the cursor cell, the order parts are pasted in and the support rule.
 */
public final class Blueprints {
    /** Largest blueprint footprint along x and z, in blocks. */
    public static final int MAX_SPAN = 64;
    /** Largest blueprint height, in blocks. */
    public static final int MAX_HEIGHT = 32;
    /** Most parts one blueprint may hold. */
    public static final int MAX_PARTS = 4096;
    /** Largest settings tag of one part, in bytes (as NBT counts it). */
    public static final int MAX_SETTINGS_BYTES = 2048;

    private Blueprints() {}

    /** {@code parts} moved so the smallest footprint cell is at (0, 0, 0); order kept. */
    public static List<BlueprintPart> normalized(List<BlueprintPart> parts) {
        if (parts.isEmpty()) return List.of();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlueprintPart p : parts) {
            minX = Math.min(minX, p.minX());
            minY = Math.min(minY, p.y());
            minZ = Math.min(minZ, p.minZ());
        }
        List<BlueprintPart> out = new ArrayList<>(parts.size());
        for (BlueprintPart p : parts) out.add(p.moved(-minX, -minY, -minZ));
        return out;
    }

    /** Size {x, y, z} of normalized {@code parts} (0s when empty). */
    public static int[] size(List<BlueprintPart> parts) {
        int sx = 0, sy = 0, sz = 0;
        for (BlueprintPart p : parts) {
            sx = Math.max(sx, p.maxX() + 1);
            sy = Math.max(sy, p.y() + p.height());
            sz = Math.max(sz, p.maxZ() + 1);
        }
        return new int[] {sx, sy, sz};
    }

    /** Whether normalized {@code parts} fit the limits (count, span, height, no negative cells). */
    public static boolean withinLimits(List<BlueprintPart> parts) {
        if (parts.size() > MAX_PARTS) return false;
        for (BlueprintPart p : parts) {
            if (p.minX() < 0 || p.y() < 0 || p.minZ() < 0) return false;
        }
        int[] s = size(parts);
        return s[0] <= MAX_SPAN && s[2] <= MAX_SPAN && s[1] <= MAX_HEIGHT;
    }

    /**
     * Offset {x, z} from the cursor cell to blueprint cell (0, 0) for a paste of size {@code size}:
     * the blueprint is centred on the cursor (the cursor cell is cell {@code size / 2}).
     */
    public static int[] anchorOffset(int[] size) {
        return new int[] {-(size[0] / 2), -(size[2] / 2)};
    }

    /** The order parts are pasted (and built): bottom layer first, then as captured. */
    public static List<Integer> pasteOrder(List<BlueprintPart> parts) {
        List<Integer> order = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) order.add(i);
        order.sort(Comparator.comparingInt((Integer i) -> parts.get(i).y()).thenComparingInt(i -> i));
        return order;
    }

    /** What lies under a cell of a part's bottom layer, for {@link #supported}. */
    public enum Below {
        /** A real block that is not replaceable and holds no fluid (terrain, a machine, a belt). */
        SOLID,
        /** A ghost, a cell another ghost reserves, or a cell this paste puts a ghost in. */
        GHOST,
        /** Air, grass, a fluid: nothing to stand on. */
        EMPTY
    }

    /**
     * The support rule: a ghost may go where every cell under its bottom layer is a real solid
     * block or a ghost (which robots build first, bottom up). A ghost over a drop is refused.
     */
    public static boolean supported(List<Below> below) {
        for (Below b : below) if (b == Below.EMPTY) return false;
        return true;
    }
}
