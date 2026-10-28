package dev.minetorio.core.logistics;

/** Request and trash amounts (Factorio 1.1 requester chests and personal logistics). */
public final class RequestMath {
    private RequestMath() {}

    /** Items still to send so {@code have} plus what is on its way reaches {@code min}. */
    public static int deficit(int min, int have, int incoming) {
        return Math.max(0, min - have - incoming);
    }

    /** Items above {@code max} not yet being taken away (0 without a maximum). */
    public static int trash(int max, int have, int outgoing) {
        if (max == LogisticStats.UNLIMITED) return 0;
        return Math.max(0, have - outgoing - max);
    }
}
