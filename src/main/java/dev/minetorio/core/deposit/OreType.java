package dev.minetorio.core.deposit;

public enum OreType {
    IRON("minetorio:iron_ore", 40, 20_000),
    COPPER("minetorio:copper_ore", 30, 20_000),
    COAL("minecraft:coal", 20, 15_000),
    STONE("minecraft:cobblestone", 10, 10_000);

    private final String item;
    private final int weight;
    private final long baseAmount;

    OreType(String item, int weight, long baseAmount) {
        this.item = item;
        this.weight = weight;
        this.baseAmount = baseAmount;
    }

    public String item() { return item; }
    public int weight() { return weight; }
    public long baseAmount() { return baseAmount; }
}
