package dev.minetorio.core.alt;

/**
 * Warning a machine shows in alt mode (Factorio's flashing status icons). {@link Severity#ERROR}
 * flashes red (the machine stands still), {@link Severity#WARNING} flashes yellow (it runs badly or
 * waits on the player). The order of the constants is the network encoding: append only.
 */
public enum AltStatus {
    NONE(Severity.NONE),
    NO_POWER(Severity.ERROR),
    LOW_POWER(Severity.WARNING),
    NO_FUEL(Severity.ERROR),
    OUTPUT_FULL(Severity.WARNING),
    NO_ORE(Severity.ERROR),
    NO_RECIPE(Severity.WARNING),
    NO_RESEARCH(Severity.WARNING);

    public enum Severity { NONE, WARNING, ERROR }

    private final Severity severity;

    AltStatus(Severity severity) { this.severity = severity; }

    public Severity severity() { return severity; }

    /** Decodes {@link #ordinal()}; unknown values give {@link #NONE}. */
    public static AltStatus byId(int id) {
        AltStatus[] all = values();
        return id >= 0 && id < all.length ? all[id] : NONE;
    }
}
