package dev.minetorio.core.belt;

import java.util.List;
import java.util.OptionalInt;

public final class UndergroundLink {
    private UndergroundLink() {}

    /**
     * Element i answers whether the block (i+1) behind the new piece is an unpaired matching entrance.
     * Returns the smallest such distance d = i+1 with d <= maxGap + 1.
     */
    public static OptionalInt findEntranceDistance(List<Boolean> isMatchingEntranceBehind, int maxGap) {
        int limit = Math.min(isMatchingEntranceBehind.size(), maxGap + 1);
        for (int i = 0; i < limit; i++) {
            if (isMatchingEntranceBehind.get(i)) return OptionalInt.of(i + 1);
        }
        return OptionalInt.empty();
    }
}
