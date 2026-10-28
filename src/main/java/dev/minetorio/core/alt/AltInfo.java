package dev.minetorio.core.alt;

import java.util.Objects;

/**
 * What a machine shows in alt mode: an item icon (item id, or null for none) and a status warning.
 *
 * @param icon   item id drawn on the dark backdrop, or null
 * @param status warning badge ({@link AltStatus#NONE} for none)
 */
public record AltInfo(String icon, AltStatus status) {
    public static final AltInfo NONE = new AltInfo(null, AltStatus.NONE);

    public AltInfo {
        if (icon != null && icon.isEmpty()) icon = null;
        status = Objects.requireNonNullElse(status, AltStatus.NONE);
    }

    public static AltInfo of(String icon, AltStatus status) { return new AltInfo(icon, status); }

    /** Nothing to draw. */
    public boolean isEmpty() { return icon == null && status == AltStatus.NONE; }
}
