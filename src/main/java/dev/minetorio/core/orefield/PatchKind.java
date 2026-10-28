package dev.minetorio.core.orefield;

import javax.annotation.Nullable;

/** Where an ore patch comes from; part of its key ({@link PatchLedger#key}). Codes are saved, never reuse them. */
public enum PatchKind {
    /** Laid out by {@link PatchGenerator} around the origin, stamped chunk by chunk by world generation. */
    GENERATED(1),
    /** One of the four starting patches ({@link PatchGenerator#startingPatches}), stamped whole once. */
    STARTING(2),
    /** A showcase field stamped by the demo command (fixed amount per block), stamped whole. */
    DEMO(3);

    private final int code;

    PatchKind(int code) { this.code = code; }

    public int code() { return code; }

    @Nullable
    public static PatchKind byCode(int code) {
        for (PatchKind k : values()) if (k.code == code) return k;
        return null;
    }
}
