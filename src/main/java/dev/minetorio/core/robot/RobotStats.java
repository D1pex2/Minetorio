package dev.minetorio.core.robot;

import dev.minetorio.core.Units;

/**
 * Factorio 1.1 construction robot and roboport numbers, converted to 20 ticks per second
 * (Factorio runs 60 updates per second; per-tick values are tripled, rates stay per second).
 * One Factorio tile is one block.
 */
public final class RobotStats {
    private RobotStats() {}

    // --- construction robot ---
    /** 0.06 tiles per update at 60 UPS = 3.6 tiles/s. */
    public static final double SPEED_BLOCKS_PER_TICK = 0.06 * 60 / Units.TICKS_PER_SECOND;
    /** Speed multiplier with an empty battery (Factorio: 20 %). */
    public static final double LOW_ENERGY_SPEED_FACTOR = 0.2;
    /** Battery: 1.5 MJ. */
    public static final long BATTERY_J = Units.mj(1.5);
    /** Flight cost: 5 kJ per tile. */
    public static final long ENERGY_PER_BLOCK_J = 5_000L;
    /** Constant drain while flying: 3 kW. */
    public static final long DRAIN_PER_TICK_J = Units.kw(3);
    /** Below this battery fraction a robot goes to charge before taking another job (Factorio min_to_charge). */
    public static final double MIN_TO_CHARGE = 0.2;
    /** Cruise height: the robot flies this many blocks above the top of the block it heads for. */
    public static final double CRUISE_HEIGHT = 2.0;

    // --- roboport ---
    /** Internal buffer: 100 MJ. */
    public static final long ROBOPORT_BUFFER_J = Units.mj(100);
    /** Maximum input: 5 MW. */
    public static final long ROBOPORT_INPUT_PER_TICK_J = Units.kw(5000);
    /** Idle drain: 50 kW. */
    public static final long ROBOPORT_DRAIN_PER_TICK_J = Units.kw(50);
    /** Charging power per charging station: 1 MW. */
    public static final long CHARGE_PER_TICK_J = Units.kw(1000);
    /** Robots charged at once (Factorio: 4 charging stations). */
    public static final int CHARGING_STATIONS = 4;
    /** Robot slots (Factorio: 7). */
    public static final int ROBOT_SLOTS = 7;
    /**
     * Construction area half-size in blocks around the roboport's center. Factorio's 110×110 is too
     * big for Minecraft; 27 gives 54×54 for the 4×4 roboport (an even footprint has a corner center).
     */
    public static final int CONSTRUCTION_RADIUS = 27;
}
