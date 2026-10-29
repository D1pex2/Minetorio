package dev.minetorio.core.rail;

public enum RailDir {
    N, NE, E, SE, S, SW, W, NW;

    public RailDir opposite() {
        return values()[(ordinal() + 4) % 8];
    }

    public RailDir rotateQuarter(int turns) {
        return values()[(ordinal() + 2 * Math.floorMod(turns, 4)) % 8];
    }

    public RailDir mirrorX() {
        return values()[Math.floorMod(-ordinal(), 8)];
    }
}
