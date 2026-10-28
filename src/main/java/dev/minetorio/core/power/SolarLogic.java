package dev.minetorio.core.power;

import dev.minetorio.core.Units;

/**
 * Solar panel output (pure). Peak {@value #PEAK_KW} kW (Factorio 1.1) scaled by Minecraft's
 * daylight, which is the sky-brightness curve of the vanilla day: full from mid-morning to
 * mid-afternoon, a smooth ramp through sunrise and sunset, nothing at night. A covered panel (any
 * block above any of its cells) gives nothing.
 *
 * <p>Weather (our call; Factorio has none): rain takes {@value #RAIN_LOSS} of the output away
 * and a thunderstorm another {@value #THUNDER_LOSS} of what is left (≈ vanilla's own sky darkening).
 */
public final class SolarLogic {
    public static final double PEAK_KW = 60;
    /** Joules per tick at full sun. */
    public static final long PEAK = Units.kw(PEAK_KW);
    public static final double RAIN_LOSS = 0.3;
    public static final double THUNDER_LOSS = 0.3;
    /** Ticks between sky-exposure checks (also redone when a neighbor changes). */
    public static final int EXPOSURE_RECHECK_TICKS = 20;

    private SolarLogic() {}

    /**
     * Daylight 0..1 at a celestial angle ({@code Level.getTimeOfDay}: 0 = noon, 0.5 = midnight):
     * vanilla's sky brightness {@code clamp(cos(2π·angle)·2 + 0.5, 0, 1)}.
     */
    public static double daylight(double celestialAngle) {
        double d = Math.cos(celestialAngle * Math.PI * 2) * 2 + 0.5;
        return Math.max(0, Math.min(1, d));
    }

    /** Output factor 0..1 of the weather: rain and thunder levels 0..1. */
    public static double weather(double rain, double thunder) {
        return (1 - RAIN_LOSS * clamp(rain)) * (1 - THUNDER_LOSS * clamp(thunder));
    }

    /** Joules per tick of a panel: 0 when not {@code exposed} to the sky. */
    public static long output(double celestialAngle, double rain, double thunder, boolean exposed) {
        if (!exposed) return 0;
        return Math.round(PEAK * daylight(celestialAngle) * weather(rain, thunder));
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
}
