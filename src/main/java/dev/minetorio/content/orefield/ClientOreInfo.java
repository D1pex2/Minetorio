package dev.minetorio.content.orefield;

import dev.minetorio.core.orefield.OrePatch;
import javax.annotation.Nullable;

/**
 * The client's latest answers about ore field columns ({@link OreFieldPayloads.Info}); plain data,
 * no rendering. Besides the latest answer it keeps the latest one that named a patch, so the map can
 * keep a patch highlighted (and its total shown) while the cursor moves over its other cells and
 * their answers are on the way.
 */
public final class ClientOreInfo {
    @Nullable
    private static volatile OreFieldPayloads.Info last;
    @Nullable
    private static volatile OreFieldPayloads.Info lastPatch;

    private ClientOreInfo() {}

    static void accept(OreFieldPayloads.Info info) {
        last = info;
        if (info.patch() != 0) {
            lastPatch = info;
        } else if (info.ore() >= 0) {
            // a field block of no patch: drop a remembered patch covering it
            OreFieldPayloads.Info p = lastPatch;
            OrePatch shape = p == null ? null : p.patchShape();
            if (shape != null && shape.contains(info.x(), info.z())) lastPatch = null;
        }
    }

    /** The latest answer if it is about column (x, z), else null. */
    @Nullable
    public static OreFieldPayloads.Info at(int x, int z) {
        OreFieldPayloads.Info i = last;
        return i != null && i.x() == x && i.z() == z ? i : null;
    }

    /** The latest answer naming a patch whose disk covers column (x, z), else null. */
    @Nullable
    public static OreFieldPayloads.Info patchAt(int x, int z) {
        OreFieldPayloads.Info i = lastPatch;
        OrePatch shape = i == null ? null : i.patchShape();
        return shape != null && shape.contains(x, z) ? i : null;
    }

    public static void reset() {
        last = null;
        lastPatch = null;
    }
}
