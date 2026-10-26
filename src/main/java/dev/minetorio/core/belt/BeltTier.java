package dev.minetorio.core.belt;

/** Belt tiers; speed in blocks per tick (Factorio 1.1: 1.875 / 3.75 / 5.625 tiles per second). */
public enum BeltTier {
    YELLOW(0.09375, 4),
    RED(0.1875, 6),
    BLUE(0.28125, 8);

    private final double speed;
    private final int undergroundMaxGap;

    BeltTier(double speed, int undergroundMaxGap) {
        this.speed = speed;
        this.undergroundMaxGap = undergroundMaxGap;
    }

    public double speed() { return speed; }

    public int undergroundMaxGap() { return undergroundMaxGap; }
}
