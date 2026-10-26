package dev.minetorio.core.research;

import java.util.*;

/** Immutable set of technologies keyed by id. */
public final class TechTree {
    private final Map<String, Technology> byId = new LinkedHashMap<>();

    public TechTree(Collection<Technology> techs) {
        for (Technology t : techs) byId.put(t.id(), t);
    }

    public Optional<Technology> get(String id) { return Optional.ofNullable(byId.get(id)); }

    public Collection<Technology> all() { return Collections.unmodifiableCollection(byId.values()); }
}
