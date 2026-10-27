package dev.minetorio.content.orefield;

import javax.annotation.Nullable;

/** The client's latest answer about an ore field column ({@link OreFieldPayloads.Info}); plain data, no rendering. */
public final class ClientOreInfo {
    @Nullable
    private static volatile OreFieldPayloads.Info last;

    private ClientOreInfo() {}

    static void accept(OreFieldPayloads.Info info) { last = info; }

    /** The latest answer if it is about column (x, z), else null. */
    @Nullable
    public static OreFieldPayloads.Info at(int x, int z) {
        OreFieldPayloads.Info i = last;
        return i != null && i.x() == x && i.z() == z ? i : null;
    }

    public static void reset() { last = null; }
}
