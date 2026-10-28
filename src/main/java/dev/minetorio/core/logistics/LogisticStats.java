package dev.minetorio.core.logistics;

import dev.minetorio.core.Units;

/**
 * Factorio 1.1 logistic robot, logistic chest and personal roboport numbers, converted to 20 ticks
 * per second (Factorio runs 60 updates per second). One Factorio tile is one block. Battery, flight
 * cost and charging are the construction robot's ({@code RobotStats}): the two robots share them.
 */
public final class LogisticStats {
    private LogisticStats() {}

    /** Logistic robot speed: 0.05 tiles per update at 60 UPS = 3 tiles/s. */
    public static final double LOGISTIC_SPEED_BLOCKS_PER_TICK = 0.05 * 60 / Units.TICKS_PER_SECOND;
    /** Items a logistic robot carries per trip (Factorio base cargo size; no cargo size research yet). */
    public static final int CARGO = 1;
    /**
     * Logistic area half-size in blocks around the roboport's center (Factorio: logistics_radius 25,
     * a 50×50 area). Roboports whose logistic areas touch or overlap form one network.
     */
    public static final int LOGISTIC_RADIUS = 25;
    /** Logistic robots are sent out every this many ticks (per level). */
    public static final int DISPATCH_INTERVAL = 10;
    /** A robot hands items to or takes them from a player within this distance (blocks). */
    public static final double PLAYER_REACH = 1.5;

    // --- logistic chests ---
    /** Inventory slots of a logistic chest (Factorio: 48; here a vanilla chest's 27 so the GUI fits). */
    public static final int CHEST_SLOTS = 27;
    /** Request slots of a requester or buffer chest (one row). */
    public static final int CHEST_REQUEST_SLOTS = 9;

    // --- personal logistics ---
    /** The player's personal logistic request slots (Factorio 1.1 starts with 10 and grows; here two rows). */
    public static final int PERSONAL_REQUEST_SLOTS = 18;
    /** A request without a maximum. */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    // --- personal roboport (Factorio 1.1 personal roboport equipment, MK1) ---
    /** Construction radius around the player (Factorio: 15 → a 30×30 area). */
    public static final int PERSONAL_CONSTRUCTION_RADIUS = 15;
    /** Construction robots one personal roboport sends out at once (Factorio: robot_limit 10). */
    public static final int PERSONAL_ROBOT_LIMIT = 10;
}
