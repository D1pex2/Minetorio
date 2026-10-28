package dev.minetorio.core.inserter;

import java.util.Optional;
import java.util.function.Predicate;

public interface PickupSource {
    /** First available item id satisfying {@code wanted}, without removing it. */
    Optional<String> peek(Predicate<String> wanted);

    /** Removes one item; false if it is no longer available. */
    boolean take(String item);

    /**
     * Whether items keep arriving here (a belt): an inserter whose hand holds more than one item
     * waits at such a source for its hand to fill. A container or machine offers what it has now.
     */
    default boolean streaming() { return false; }
}
