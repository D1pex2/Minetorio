package dev.minetorio.content.fluid;

import dev.minetorio.core.fluid.FluidBox;
import dev.minetorio.core.fluid.FluidNetwork;
import dev.minetorio.core.fluid.FluidStore;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Helpers for machines and tests to read, insert and extract fluid. A machine works on its own
 * {@link FluidBox}es (one per port, see {@link FluidMachine}); the network moves fluid between
 * boxes and pipes after the machines ticked.
 *
 * <p>Typical recipe machine tick:
 * <pre>{@code
 * if (FluidAccess.has(fluidBox(IN), "crude_oil", 100) && FluidAccess.fits(fluidBox(OUT), "heavy_oil", 25)) {
 *     fluidBox(IN).extract(100);
 *     fluidBox(OUT).insert("heavy_oil", 25);
 * }
 * }</pre>
 */
public final class FluidAccess {
    private static final double EPS = 1e-9;

    private FluidAccess() {}

    /** Whether {@code box} holds at least {@code amount} of {@code fluid}. */
    public static boolean has(FluidStore box, String fluid, double amount) {
        return fluid.equals(box.fluid()) && box.amount() + EPS >= amount;
    }

    /** Whether {@code amount} of {@code fluid} fits into {@code box} (empty, or the same fluid with room). */
    public static boolean fits(FluidStore box, String fluid, double amount) {
        return (box.fluid() == null || fluid.equals(box.fluid())) && box.room() + EPS >= amount;
    }

    /** Whether every (fluid, amount) is in the matching box: {@code boxes[i]} for {@code needs} in order. */
    public static boolean hasAll(Map<String, Double> needs, FluidStore... boxes) {
        int i = 0;
        for (Map.Entry<String, Double> e : needs.entrySet()) {
            if (i >= boxes.length || !has(boxes[i++], e.getKey(), e.getValue())) return false;
        }
        return true;
    }

    /** Moves up to {@code amount} from one store to another (same fluid); returns the amount moved. */
    public static double transfer(FluidStore from, FluidStore to, double amount) {
        String f = from.fluid();
        if (f == null || amount <= 0) return 0;
        double x = Math.min(Math.min(amount, from.amount()), to.room());
        if (to.fluid() != null && !to.fluid().equals(f)) return 0;
        if (x <= 0) return 0;
        double inserted = to.insert(f, x);
        from.extract(inserted);
        return inserted;
    }

    /** The box behind port {@code port} of the machine at {@code controller}, or null. */
    @Nullable
    public static FluidBox box(Level level, BlockPos controller, int port) {
        return level.getBlockEntity(controller) instanceof FluidMachine m ? m.fluidBox(port) : null;
    }

    /**
     * The segment of the pipe, underground pipe or tank at {@code pos} (any tank cell), or null
     * (client, not a node, or not registered yet: nodes register at the end of the tick they load in).
     */
    @Nullable
    public static FluidNetwork.Segment segmentAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return null;
        FluidNetworkManager m = FluidNetworkManager.getIfPresent(server);
        return m == null ? null : m.segmentAt(pos);
    }
}
