package dev.minetorio.core.fluid;

import dev.minetorio.core.Units;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluid tables: the fluids and the numbers of the fluid system, as data so they can be redesigned
 * later without touching the logic. Values are Factorio's, converted to 20 ticks per second.
 */
public final class FluidData {
    /**
     * A fluid. Colours are Factorio's (RGB, no alpha): {@code base} fills tanks, windows and alt-mode
     * gauges, {@code flow} is the lighter colour of moving fluid (outlines, highlights).
     */
    public record FluidType(String id, int base, int flow) {}

    private static final Map<String, FluidType> FLUIDS = new LinkedHashMap<>();

    static {
        fluid("water", 0x005799, 0xB3B3B3);
        fluid("steam", 0x808080, 0xB3B3B3);
        fluid("crude_oil", 0x2A2622, 0x808080);
        fluid("heavy_oil", 0x800A00, 0xD99A4D);
        fluid("light_oil", 0x915400, 0xFFBA12);
        fluid("petroleum_gas", 0x4D1A4D, 0xCCCCCC);
        fluid("sulfuric_acid", 0xBFA61A, 0xB3FF1A);
        fluid("lubricant", 0x265208, 0x6EBF4F);
    }

    // --- capacities (units) -----------------------------------------------------------------------
    public static final double PIPE_CAPACITY = 100;
    public static final double UNDERGROUND_PIPE_CAPACITY = 100;
    public static final double STORAGE_TANK_CAPACITY = 25_000;
    /** The pump's own box: one tick of its full rate, so it can move its rate every tick. */
    public static final double PUMP_CAPACITY = 600;

    // --- underground pipes --------------------------------------------------------------------------
    /** Farthest partner of an underground pipe, in blocks (Factorio 1.1 pipe-to-ground: 10). */
    public static final int UNDERGROUND_PIPE_REACH = 10;

    // --- pump -----------------------------------------------------------------------------------------
    /** 12 000 per second. */
    public static final double PUMP_RATE_PER_TICK = 12_000.0 / Units.TICKS_PER_SECOND;
    public static final long PUMP_POWER_J = Units.kw(30);

    // --- flow limits ----------------------------------------------------------------------------------
    /**
     * Most one machine port moves per tick into or out of what it touches (Factorio 2.0: 6000/s per
     * connection). Pumps are exempt.
     */
    public static final double CONNECTION_LIMIT_PER_TICK = 6_000.0 / Units.TICKS_PER_SECOND;

    /**
     * Segment throughput by length (number of pipes, underground pipes and tanks in it), per second;
     * linear in between, the last value beyond. Factorio's pipe-length table (6000/s for one pipe,
     * about 1000/s at 200 pipes, less on longer runs). Limits the flow into and, separately, out of
     * a segment per tick, except what pumps move.
     */
    private static final int[] LENGTHS = {1, 2, 7, 12, 17, 20, 30, 50, 100, 150, 200, 261, 300, 400, 1000};
    private static final double[] FLOW_PER_SECOND = {6000, 3000, 2000, 1500, 1338, 1287, 1200, 1154, 1124, 1112, 1105, 1000,
        800, 600, 400};

    private FluidData() {}

    private static void fluid(String id, int base, int flow) {
        FLUIDS.put(id, new FluidType(id, base, flow));
    }

    /** All fluids, in display order. */
    public static Collection<FluidType> fluids() { return FLUIDS.values(); }

    /** The fluid with {@code id}, or null. */
    public static FluidType fluid(String id) { return id == null ? null : FLUIDS.get(id); }

    public static boolean isFluid(String id) { return fluid(id) != null; }

    /** Base colour of {@code id} (RGB), grey for unknown ids. */
    public static int color(String id) {
        FluidType t = fluid(id);
        return t == null ? 0x808080 : t.base();
    }

    /** Segment throughput per second for a segment of {@code length} entities. */
    public static double segmentLimitPerSecond(int length) {
        if (length <= LENGTHS[0]) return FLOW_PER_SECOND[0];
        for (int i = 1; i < LENGTHS.length; i++) {
            if (length <= LENGTHS[i]) {
                double t = (double) (length - LENGTHS[i - 1]) / (LENGTHS[i] - LENGTHS[i - 1]);
                return FLOW_PER_SECOND[i - 1] + t * (FLOW_PER_SECOND[i] - FLOW_PER_SECOND[i - 1]);
            }
        }
        return FLOW_PER_SECOND[FLOW_PER_SECOND.length - 1];
    }

    /** Segment throughput per tick for a segment of {@code length} entities. */
    public static double segmentLimitPerTick(int length) {
        return segmentLimitPerSecond(length) / Units.TICKS_PER_SECOND;
    }
}
