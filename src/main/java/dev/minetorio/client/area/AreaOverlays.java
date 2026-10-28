package dev.minetorio.client.area;

import dev.minetorio.core.area.GroundRect;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Registry of Factorio-style area highlights (client). A provider describes the ground areas of a
 * block or item; {@link AreaOverlayRenderer} asks it while the player holds the item (a preview at
 * the cell a right-click would place it) or looks at the placed block, and draws what it emits on
 * the terrain. Always on, not only in alt mode.
 *
 * <p>Register from client setup, e.g. the roboport's construction area:
 * <pre>{@code
 * AreaOverlays.register(ModBlocks.ROBOPORT.get(), (target, out) ->
 *         out.fill(AreaMath.poleSupply(target.pos().getX(), target.pos().getZ(), 25), 0x40FFAA33, target.pos().getY()));
 * }</pre>
 * A block's provider also serves its {@link BlockItem}, unless the item has its own.
 */
public final class AreaOverlays {
    /** Where the areas are asked for. */
    public record Target(Level level, BlockPos pos, Direction facing, boolean preview) {}

    /** Receives the areas; {@code baseY} is the y of the block the object stands in (the ground is found near it). */
    public interface Sink {
        /** A translucent ground fill of every column in {@code rect}, with a stronger border. */
        void fill(GroundRect rect, int argb, int baseY);

        /** Only the border of {@code rect}, on the ground. */
        void outline(GroundRect rect, int argb, int baseY);

        /** A thin flat ring of {@code radius} around (cx, cz), at the ground level of its center column. */
        void circle(double cx, double cz, double radius, int argb, int baseY);
    }

    @FunctionalInterface
    public interface Provider {
        void areas(Target target, Sink out);
    }

    private static final Map<Item, Provider> ITEMS = new ConcurrentHashMap<>();
    private static final Map<Block, Provider> BLOCKS = new ConcurrentHashMap<>();

    private AreaOverlays() {}

    /** Areas shown while the player holds {@code item} (overrides the provider of its block). */
    public static void register(Item item, Provider provider) { ITEMS.put(item, provider); }

    /** Areas shown while the player looks at a placed {@code block} (or holds its item). */
    public static void register(Block block, Provider provider) { BLOCKS.put(block, provider); }

    @Nullable
    public static Provider forItem(Item item) {
        Provider p = ITEMS.get(item);
        if (p == null && item instanceof BlockItem bi) p = BLOCKS.get(bi.getBlock());
        return p;
    }

    @Nullable
    public static Provider forBlock(Block block) { return BLOCKS.get(block); }
}
