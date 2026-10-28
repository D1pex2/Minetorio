package dev.minetorio.core.power;

/**
 * The electric pole kinds (Factorio 1.1 numbers, 1 tile = 1 block).
 *
 * <p>The supply area is the pole's footprint grown by {@link #supplyMargin} cells on every
 * horizontal side (small 5×5, medium 7×7, big 4×4, substation 18×18) and {@link #verticalReach}
 * blocks up and down from the pole's lowest layer (Factorio is flat; Minecraft needs some height).
 * Two poles are wired when the distance between their footprint centers is at most the smaller of
 * their two {@link #wireReach}es, as in Factorio.
 */
public enum PoleType {
    SMALL("small_electric_pole", 1, 7.5, 2, 2, 3),
    MEDIUM("medium_electric_pole", 1, 9, 3, 3, 4),
    BIG("big_electric_pole", 2, 30, 1, 2, 5),
    SUBSTATION("substation", 2, 18, 8, 8, 3);

    private final String id;
    private final int size;
    private final double wireReach;
    private final int supplyMargin;
    private final int verticalReach;
    private final int height;

    /**
     * @param id            block/item id
     * @param size          footprint edge (1×1 or 2×2)
     * @param wireReach     wire reach in blocks
     * @param supplyMargin  supply cells beyond the footprint on each horizontal side
     * @param verticalReach supply blocks above and below the pole's lowest layer
     * @param height        blocks the pole stands tall (scaled from the Factorio sprites; small = 3)
     */
    PoleType(String id, int size, double wireReach, int supplyMargin, int verticalReach, int height) {
        this.id = id;
        this.size = size;
        this.wireReach = wireReach;
        this.supplyMargin = supplyMargin;
        this.verticalReach = verticalReach;
        this.height = height;
    }

    public String id() { return id; }

    public int size() { return size; }

    public double wireReach() { return wireReach; }

    public int supplyMargin() { return supplyMargin; }

    public int verticalReach() { return verticalReach; }

    public int height() { return height; }

    /** Edge of the square supply area in blocks (5, 7, 4, 18). */
    public int supplyArea() { return size + 2 * supplyMargin; }

    /**
     * Wire reach used to space poles laid by dragging, measured between the cells the drag puts the
     * poles on. A 2×2 pole's center sits up to √2 blocks off that cell depending on how it is turned,
     * so its drag spacing keeps that much slack and dragged poles always connect.
     */
    public double dragReach() {
        return wireReach - (size - 1) * Math.sqrt(2);
    }
}
