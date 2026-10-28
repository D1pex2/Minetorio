package dev.minetorio.core.robot;

import dev.minetorio.core.data.UpgradeGroups;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An upgrade planner's mapping rows ("from → to", Factorio-like). A row may be half filled while
 * the player edits it (empty string = not chosen yet); only complete rows whose items share a
 * fast-replace group ({@link UpgradeGroups}) count. The first matching row wins.
 */
public record UpgradeRules(List<Rule> rules) {
    /** Most rows a planner holds. */
    public static final int MAX_RULES = 12;

    public record Rule(String from, String to) {
        public Rule {
            from = from == null ? "" : from;
            to = to == null ? "" : to;
        }

        /** Both items chosen and compatible. */
        public boolean valid() {
            return !from.isEmpty() && !to.isEmpty() && UpgradeGroups.compatible(from, to);
        }
    }

    public static final UpgradeRules EMPTY = new UpgradeRules(List.of());

    public UpgradeRules {
        rules = List.copyOf(rules.size() > MAX_RULES ? rules.subList(0, MAX_RULES) : rules);
    }

    /** What {@code item} is to be upgraded to, if a valid row says so. */
    public Optional<String> target(String item) {
        for (Rule r : rules) {
            if (r.valid() && r.from.equals(item)) return Optional.of(r.to);
        }
        return Optional.empty();
    }

    /** Whether any row is valid (selecting with the planner would do something). */
    public boolean hasValid() {
        return rules.stream().anyMatch(Rule::valid);
    }

    /**
     * The rules as a client sent them, cleaned: at most {@link #MAX_RULES} rows, items outside every
     * group dropped (the row kept half filled), a "to" that does not fit its "from" cleared.
     */
    public static UpgradeRules sanitize(List<Rule> in) {
        List<Rule> out = new ArrayList<>();
        for (Rule r : in) {
            if (out.size() >= MAX_RULES) break;
            String from = UpgradeGroups.groupOf(r.from()).isPresent() ? r.from() : "";
            String to = UpgradeGroups.groupOf(r.to()).isPresent() ? r.to() : "";
            if (!from.isEmpty() && !to.isEmpty() && !UpgradeGroups.compatible(from, to)) to = "";
            out.add(new Rule(from, to));
        }
        return new UpgradeRules(out);
    }
}
