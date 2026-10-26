package dev.minetorio.content.multiblock;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Size of a multiblock machine in blocks: {@code width} across the facing, {@code depth} along it,
 * {@code height} up. See {@link #footprint} for the orientation.
 */
public record MultiblockShape(int width, int depth, int height) {
    public static final MultiblockShape STONE_FURNACE = new MultiblockShape(2, 2, 2);
    public static final MultiblockShape BURNER_MINING_DRILL = new MultiblockShape(2, 2, 2);
    public static final MultiblockShape ELECTRIC_MINING_DRILL = new MultiblockShape(3, 3, 2);
    /** assembling_machine_1 and assembling_machine_2. */
    public static final MultiblockShape ASSEMBLING_MACHINE = new MultiblockShape(3, 3, 2);
    public static final MultiblockShape LAB = new MultiblockShape(3, 3, 2);
    public static final MultiblockShape BOILER = new MultiblockShape(3, 2, 2);
    public static final MultiblockShape STEAM_ENGINE = new MultiblockShape(3, 5, 2);
    public static final MultiblockShape OFFSHORE_PUMP = new MultiblockShape(1, 2, 1);
    /** All splitter tiers. */
    public static final MultiblockShape SPLITTER = new MultiblockShape(2, 1, 1);

    public MultiblockShape {
        if (width < 1 || depth < 1 || height < 1) {
            throw new IllegalArgumentException("non-positive size " + width + "x" + depth + "x" + height);
        }
    }

    public int volume() { return width * depth * height; }

    /**
     * All block positions occupied by the machine whose controller sits at {@code origin}.
     * Orientation: width runs along {@code facing.getClockWise()} (to the right when looking in the
     * facing direction), depth runs along {@code facing}, height runs up. The controller is the
     * left-front-bottom corner, i.e. the origin itself, which is always the first element.
     */
    public List<BlockPos> footprint(BlockPos origin, Direction facing) {
        Direction right = facing.getClockWise();
        List<BlockPos> out = new ArrayList<>(volume());
        for (int y = 0; y < height; y++) {
            for (int d = 0; d < depth; d++) {
                for (int w = 0; w < width; w++) {
                    out.add(origin.relative(right, w).relative(facing, d).above(y));
                }
            }
        }
        return out;
    }
}
