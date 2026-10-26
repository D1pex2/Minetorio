package dev.minetorio.core.belt;

/** Alternates outputs per lane index; falls back to the free output when one is blocked. */
public final class SplitterLogic {
    private final int[] next = new int[2];

    public SplitterLogic() {}

    /** @return 0 (left output), 1 (right output) or -1 (wait). */
    public int route(int laneIndex, boolean leftFree, boolean rightFree) {
        if (!leftFree && !rightFree) return -1;
        int chosen;
        if (next[laneIndex] == 0) chosen = leftFree ? 0 : 1;
        else chosen = rightFree ? 1 : 0;
        next[laneIndex] = 1 - chosen;
        return chosen;
    }
}
