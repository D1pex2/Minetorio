package dev.minetorio.core.logistics;

/**
 * One request slot: keep at least {@code min} of {@code item}; above {@code max} the rest is trash
 * (players only; {@link LogisticStats#UNLIMITED} = no maximum). A max below min is raised to min.
 */
public record LogisticRequest(String item, int min, int max) {
    public LogisticRequest {
        if (item == null || item.isEmpty()) throw new IllegalArgumentException("no item");
        min = Math.max(0, min);
        max = Math.max(min, max);
    }

    public boolean hasMax() { return max != LogisticStats.UNLIMITED; }
}
