package dev.minetorio.content.logistics;

import javax.annotation.Nullable;

/** Client side: the last {@link LogisticsPayloads.NetworkStats} received (for the open roboport GUI). */
public final class NetworkStatsView {
    @Nullable
    private static volatile LogisticsPayloads.NetworkStats latest;

    private NetworkStatsView() {}

    static void set(LogisticsPayloads.NetworkStats s) { latest = s; }

    /** The stats for the menu {@code containerId}, or null if none arrived for it yet. */
    @Nullable
    public static LogisticsPayloads.NetworkStats forMenu(int containerId) {
        LogisticsPayloads.NetworkStats s = latest;
        return s != null && s.containerId() == containerId ? s : null;
    }
}
