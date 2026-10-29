package dev.minetorio.core.orefield;

/** The four ore field types: the item one ore yields, the generation weight and the base amount per field block. */
public enum OreType {
    IRON("minetorio:iron_ore", 40, 600),
    COPPER("minetorio:copper_ore", 30, 600),
    COAL("minetorio:coal", 20, 600),
    STONE("minetorio:stone", 10, 400);

    private final String item;
    private final int weight;
    private final long baseAmount;

    OreType(String item, int weight, long baseAmount) {
        this.item = item;
        this.weight = weight;
        this.baseAmount = baseAmount;
    }

    /** Item id one mined ore yields. */
    public String item() { return item; }

    /** Relative chance among generated patches. */
    public int weight() { return weight; }

    /** Ore in one field block at spawn, before the radial and distance factors ({@link OreAmounts}). */
    public long baseAmount() { return baseAmount; }
}
