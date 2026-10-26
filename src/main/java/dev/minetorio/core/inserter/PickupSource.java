package dev.minetorio.core.inserter;

import java.util.Optional;
import java.util.function.Predicate;

public interface PickupSource {
    /** First available item id satisfying {@code wanted}, without removing it. */
    Optional<String> peek(Predicate<String> wanted);

    /** Removes one item; false if it is no longer available. */
    boolean take(String item);
}
