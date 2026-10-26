package dev.minetorio.content.research;

import dev.minetorio.core.research.ResearchState;

/**
 * The client's mirror of the world's research, filled by {@link ResearchSyncPayload} (on login and
 * on every change). Holds start recipes only until the first sync. Read on the client thread only
 * (recipe lists, research screen); the server never consults it.
 */
public final class ClientResearch {
    private static ResearchState state = ResearchSavedData.newState();

    private ClientResearch() {}

    public static ResearchState state() { return state; }

    static void apply(ResearchSyncPayload p) {
        ResearchState s = ResearchSavedData.newState();
        ResearchSavedData.loadInto(s, p.researched(), p.current(), p.progressById());
        state = s;
    }
}
