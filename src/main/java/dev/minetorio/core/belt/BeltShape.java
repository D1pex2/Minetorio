package dev.minetorio.core.belt;

public enum BeltShape {
    STRAIGHT, CURVE_LEFT, CURVE_RIGHT;

    /** A curve only when nothing feeds from behind and exactly one side feeds in. */
    public static BeltShape of(boolean backInput, boolean leftInput, boolean rightInput) {
        if (backInput || leftInput == rightInput) return STRAIGHT;
        return leftInput ? CURVE_LEFT : CURVE_RIGHT;
    }

    public double laneLength(boolean innerLane) {
        return this != STRAIGHT && innerLane ? 0.5 : 1.0;
    }
}
