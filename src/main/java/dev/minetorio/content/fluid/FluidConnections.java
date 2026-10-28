package dev.minetorio.content.fluid;

import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Who connects to whom, from block states only (so it works while things are being placed, and
 * the rules are symmetric: if A links to B, B links to A).
 * <ul>
 *   <li>pipe – pipe: both sides open (not capped);</li>
 *   <li>pipe – underground pipe: the pipe's side open and the underground's open end facing it;</li>
 *   <li>underground – underground: open ends facing each other, or a tunnel pair
 *       ({@link UndergroundPipeBlock#partner});</li>
 *   <li>anything above – a machine or tank port facing it ({@link FluidPorts}); tank ports also
 *       link to touching tank ports. Machines connect only through their ports.</li>
 * </ul>
 */
public final class FluidConnections {
    private FluidConnections() {}

    /** What is at one end of a connection: a segment node, or a machine port (not a node). */
    public sealed interface End permits NodeEnd, PortEnd {}

    /** A node with key {@code pos} (a tank's controller position for any of its port cells). */
    public record NodeEnd(BlockPos pos) implements End {}

    public record PortEnd(FluidPorts.Port port) implements End {}

    /**
     * What at {@code cell} accepts a connection coming from its {@code back} side (the side facing
     * the asker), or null.
     */
    @Nullable
    public static End endAt(BlockGetter level, BlockPos cell, Direction back) {
        if (level instanceof LevelReader reader && !reader.hasChunkAt(cell)) return null;
        BlockState state = level.getBlockState(cell);
        if (state.getBlock() instanceof PipeBlock) {
            return PipeBlock.isOpen(state, back) ? new NodeEnd(cell) : null;
        }
        if (state.getBlock() instanceof UndergroundPipeBlock) {
            return state.getValue(UndergroundPipeBlock.FACING) == back ? new NodeEnd(cell) : null;
        }
        FluidPorts.Port port = FluidPorts.portAt(level, cell, back);
        if (port == null) return null;
        if (level.getBlockState(port.controller()).getBlock() instanceof FluidNodeBlock) return new NodeEnd(port.controller());
        return new PortEnd(port);
    }

    /** Whether something at {@code pos + side} connects back to {@code pos} (for drawing pipe arms). */
    public static boolean connectsToward(BlockGetter level, BlockPos pos, Direction side) {
        return endAt(level, pos.relative(side), side.getOpposite()) != null;
    }

    /** Nodes the node at {@code pos} should be linked to (keys), from the current block states. */
    public static Set<Long> desiredLinks(BlockGetter level, BlockPos pos) {
        Set<Long> out = new LinkedHashSet<>();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PipeBlock) {
            for (Direction d : Direction.values()) {
                if (PipeBlock.isOpen(state, d)) addNode(out, endAt(level, pos.relative(d), d.getOpposite()));
            }
        } else if (state.getBlock() instanceof UndergroundPipeBlock) {
            Direction f = state.getValue(UndergroundPipeBlock.FACING);
            addNode(out, endAt(level, pos.relative(f), f.getOpposite()));
            BlockPos partner = UndergroundPipeBlock.partner(level, pos, f);
            if (partner != null) out.add(partner.asLong());
        } else if (state.getBlock() instanceof FluidNodeBlock) {
            for (FluidPorts.Port p : FluidPorts.ports(level, pos)) {
                addNode(out, endAt(level, p.neighbor(), p.side().getOpposite()));
            }
        }
        out.remove(pos.asLong());
        return out;
    }

    private static void addNode(Set<Long> out, @Nullable End end) {
        if (end instanceof NodeEnd n) out.add(n.pos().asLong());
    }

    /** Node key at {@code pos}: the position itself, or a tank's controller for any of its cells; null if none. */
    @Nullable
    public static BlockPos nodeAt(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PipeBlock || state.getBlock() instanceof UndergroundPipeBlock
                || state.getBlock() instanceof FluidNodeBlock) {
            return pos;
        }
        if (state.getBlock() instanceof dev.minetorio.content.multiblock.MultiblockPartBlock) {
            BlockPos c = dev.minetorio.content.multiblock.MultiblockPartBlock.controllerPos(level, pos);
            if (c != null && level.getBlockState(c).getBlock() instanceof FluidNodeBlock) return c;
        }
        return null;
    }
}
