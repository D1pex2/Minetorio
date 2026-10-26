package dev.minetorio.core.belt;

/**
 * An item on a lane; {@code pos} in [0, length], 0 is the lane entrance. {@code item} is an opaque
 * stack key: a plain item id ("minetorio:iron_plate"), or {@code id + "#" + extra data} for items
 * with data components (see {@link #baseId}).
 */
public record LaneItem(String item, double pos) {
    /** The item id of a stack key: everything before the first '#'. */
    public static String baseId(String key) {
        int i = key.indexOf('#');
        return i < 0 ? key : key.substring(0, i);
    }
}
