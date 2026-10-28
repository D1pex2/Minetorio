package dev.minetorio.core.logistics;

/** The five Factorio 1.1 logistic chests. */
public enum LogisticMode {
    /** Offers its contents to requests; never emptied otherwise. */
    PASSIVE_PROVIDER("passive_provider_chest"),
    /** Pushes its contents out: to requests first, the rest to storage. */
    ACTIVE_PROVIDER("active_provider_chest"),
    /** Where robots put away items nobody requested (active provider contents, trash); optional one-item filter. */
    STORAGE("storage_chest"),
    /** Requests items (request slots); never a source. */
    REQUESTER("requester_chest"),
    /** Requests items like a requester, and offers them to players, construction and (opt-in) requesters. */
    BUFFER("buffer_chest");

    private final String id;

    LogisticMode(String id) { this.id = id; }

    /** Block and item id (path). */
    public String id() { return id; }

    /** Its items count as being in the network (passive, active, storage, buffer). */
    public boolean countsInNetwork() { return this != REQUESTER; }

    /** Construction robots may take items from it (Factorio: providers, storage and buffers). */
    public boolean suppliesConstruction() { return this != REQUESTER; }

    /** Request slots: requester and buffer chests one row, a storage chest its single filter slot, providers none. */
    public int requestSlots() {
        return switch (this) {
            case REQUESTER, BUFFER -> LogisticStats.CHEST_REQUEST_SLOTS;
            case STORAGE -> 1;
            default -> 0;
        };
    }

    /** The planner role of a chest in this mode. */
    public LogisticPlanner.Role role() {
        return switch (this) {
            case PASSIVE_PROVIDER -> LogisticPlanner.Role.PASSIVE_PROVIDER;
            case ACTIVE_PROVIDER -> LogisticPlanner.Role.ACTIVE_PROVIDER;
            case STORAGE -> LogisticPlanner.Role.STORAGE;
            case REQUESTER -> LogisticPlanner.Role.REQUESTER;
            case BUFFER -> LogisticPlanner.Role.BUFFER;
        };
    }
}
