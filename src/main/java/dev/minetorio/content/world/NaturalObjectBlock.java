package dev.minetorio.content.world;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A part of a natural object of the Factorio map (a tree or a rock), mined as a whole like in
 * Factorio: breaking any part removes every connected part of the same {@link Group} (up to
 * {@value #MAX_PARTS}) and drops the object's yield once, from the broken part's loot table
 * (every part of an object has the whole object's loot table).
 */
public class NaturalObjectBlock extends Block {
    public enum Group { TREE, ROCK }

    public static final int MAX_PARTS = 128;

    private final Group group;
    private final VoxelShape shape;
    private final boolean collides;

    public NaturalObjectBlock(Group group, VoxelShape shape, boolean collides, Properties props) {
        super(props);
        this.group = group;
        this.shape = shape;
        this.collides = collides;
    }

    public Group group() { return group; }

    /** Observe actual loot after loot-table modifiers, so random rock yields are recorded exactly. */
    public static void registerStatistics() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                NaturalObjectBlock::recordDrops);
    }

    private static void recordDrops(net.neoforged.neoforge.event.level.BlockDropsEvent event) {
        if (event.isCanceled() || !(event.getState().getBlock() instanceof NaturalObjectBlock)) return;
        for (var entity : event.getDrops()) {
            var stack = entity.getItem();
            if (stack.isEmpty()) continue;
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            dev.minetorio.content.stats.Statistics.record(event.getLevel(), id.toString(), stack.getCount(), 0);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return collides ? shape : Shapes.empty();
    }

    /** Every connected part of the object at {@code start} (itself included), 26-connected. */
    public static Set<BlockPos> objectAt(BlockGetter level, BlockPos start, Group group) {
        Block objectBlock = level.getBlockState(start).getBlock();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty() && seen.size() < MAX_PARTS) {
            BlockPos p = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (seen.contains(n)) continue;
                        if (seen.size() < MAX_PARTS && level.getBlockState(n).getBlock() instanceof NaturalObjectBlock b
                                && b.group == group && (group == Group.TREE || b == objectBlock)) {
                            seen.add(n);
                            queue.add(n);
                        }
                    }
                }
            }
        }
        return seen;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            for (BlockPos p : objectAt(level, pos, group)) {
                if (!p.equals(pos)) level.removeBlock(p, false); // no drops: the broken part drops the whole yield
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
