package dev.minetorio.content.robot;

import dev.minetorio.content.belt.BeltBlock;
import dev.minetorio.content.belt.UndergroundBeltBlock;
import dev.minetorio.content.belt.UndergroundBeltBlockEntity;
import dev.minetorio.content.nbt.CopyableSettings;
import net.minecraft.nbt.CompoundTag;
import dev.minetorio.content.multiblock.MultiblockBlockItem;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.power.ElectricPoleBlock;
import dev.minetorio.content.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Creating and building ghosts. A ghost is placed at the cell a player's block would occupy (for a
 * multiblock: the controller position, the footprint's left-front-bottom corner) together with the
 * player's look direction; building it runs the item's normal placement with that look direction,
 * so belts, inserters and multiblocks come out exactly as if a player placed them there.
 *
 * <p>A ghost covers its {@link #footprint} (a multiblock's whole footprint, a pole's three blocks):
 * those cells are reserved ({@link GhostReservations}) and no other ghost may overlap them. Belt
 * ghosts preview their shape and slope against the planned world ({@link #planned}), where other
 * ghosts count as the blocks they stand for, and follow their neighbours as those change.
 */
public final class Ghosts {
    private Ghosts() {}

    /**
     * Places a ghost of {@code item} at {@code pos} as a player looking {@code facing} would place it.
     * False if the cell is not replaceable or the item is not a block item.
     */
    public static boolean place(ServerLevel level, BlockPos pos, Item item, Direction facing) {
        if (!(item instanceof BlockItem) || !level.getBlockState(pos).canBeReplaced()) return false;
        BlockState preview = previewState(level, pos, item, facing);
        return fits(level, pos, preview) && placePlanned(level, pos, preview, facing);
    }

    /**
     * Places a ghost of {@code target} (already checked: {@link HandGhosts#plan}) at {@code pos},
     * replacing a ghost there, and updates the belt ghosts around it.
     */
    public static boolean placePlanned(ServerLevel level, BlockPos pos, BlockState target, Direction facing) {
        return placePlanned(level, pos, target, facing, null);
    }

    /**
     * {@link #placePlanned(ServerLevel, BlockPos, BlockState, Direction)} with {@code settings}
     * the built block gets ({@link CopyableSettings}, e.g. a blueprint's recipe or filters).
     */
    public static boolean placePlanned(ServerLevel level, BlockPos pos, BlockState target, Direction facing, @Nullable CompoundTag settings) {
        if (level.getBlockState(pos).is(ModBlocks.GHOST.get())) level.removeBlock(pos, false);
        if (!put(level, pos, target, facing, 0, settings)) return false;
        refreshAround(level, pos);
        return true;
    }

    /**
     * Cells a ghost of {@code target} at {@code pos} covers: a multiblock's footprint (turned by its
     * facing), a pole's {@value ElectricPoleBlock#HEIGHT} blocks, otherwise {@code pos} alone. The
     * ghost's own cell comes first.
     */
    public static List<BlockPos> footprint(BlockState target, BlockPos pos) {
        if (target.getBlock() instanceof MultiblockControllerBlock mb && target.hasProperty(MultiblockControllerBlock.FACING)) {
            return mb.footprint(pos, target);
        }
        if (target.getBlock() instanceof ElectricPoleBlock) {
            List<BlockPos> out = new ArrayList<>(ElectricPoleBlock.HEIGHT);
            for (int i = 0; i < ElectricPoleBlock.HEIGHT; i++) out.add(pos.above(i));
            return out;
        }
        return List.of(pos);
    }

    /**
     * Whether a ghost of {@code target} at {@code pos} leaves other ghosts alone: none of its cells
     * is reserved by another ghost, and no other cell than {@code pos} (which it replaces) holds a ghost.
     */
    public static boolean fits(Level level, BlockPos pos, BlockState target) {
        for (BlockPos p : footprint(target, pos)) {
            if (!GhostReservations.freeFor(level, p, pos)) return false;
            if (!p.equals(pos) && level.getBlockState(p).is(ModBlocks.GHOST.get())) return false;
        }
        return true;
    }

    /** {@code state} as it would stand at {@code pos} among the planned blocks (belts: shape and slope from planned neighbours). */
    public static BlockState planned(Level level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof BeltBlock ? BeltBlock.withShape(new PlannedView(level), pos, state) : state;
    }

    /** Re-plans the belt ghosts whose shape may depend on {@code pos} (its sides at three levels, and below). */
    public static void refreshAround(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int dy = -1; dy <= 1; dy++) refresh(level, pos.relative(d).above(dy));
        }
        refresh(level, pos.below());
    }

    /** Re-plans the belt ghost at {@code pos}, if there is one. */
    static void refresh(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        GhostBlockEntity be = GhostBlock.at(level, pos);
        if (be != null && be.isValid() && be.target().getBlock() instanceof BeltBlock) be.retarget(planned(level, pos, be.target()));
    }

    private static boolean put(ServerLevel level, BlockPos pos, BlockState target, Direction facing, long seq, @Nullable CompoundTag settings) {
        if (!level.setBlock(pos, ModBlocks.GHOST.get().defaultBlockState(), Block.UPDATE_ALL)) return false;
        if (!(level.getBlockEntity(pos) instanceof GhostBlockEntity be)) return false;
        be.setSettings(settings);
        be.setup(target, facing, seq);
        level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
        return true;
    }

    /**
     * The state {@code item} would get at {@code pos} placed looking {@code facing} (neighbours
     * included, as now); the block's default state turned to {@code facing} if placement says no.
     */
    public static BlockState previewState(Level level, BlockPos pos, Item item, Direction facing) {
        Block block = ((BlockItem) item).getBlock();
        BlockState s = block.getStateForPlacement(context(level, pos, null, new ItemStack(item), facing));
        if (s != null) return planned(level, pos, s);
        BlockState d = block.defaultBlockState();
        return d.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? d.setValue(BlockStateProperties.HORIZONTAL_FACING, facing) : d;
    }

    /**
     * Whether the ghost at {@code pos} can be built right now: a multiblock's footprint may be
     * blocked (a marked obstacle not yet removed), and a ghost standing on another ghost waits until
     * that one is built (robots build bottom up). The ghost itself is replaceable, so it does not
     * block its own placement.
     */
    public static boolean canBuild(ServerLevel level, BlockPos pos) {
        GhostBlockEntity be = GhostBlock.at(level, pos);
        if (be == null || !be.isValid() || !(be.item() instanceof BlockItem bi)) return false;
        if (!supported(level, pos, be.target(), false)) return false;
        return bi.getBlock().getStateForPlacement(context(level, pos, null, new ItemStack(bi), be.facing())) != null;
    }

    /** Whether every bottom cell has support; planned support is sufficient only for previews. */
    public static boolean supported(Level level, BlockPos pos, BlockState target, boolean allowPlannedSupport) {
        for (BlockPos c : footprint(target, pos)) {
            if (c.getY() != pos.getY()) continue;
            BlockPos below = c.below();
            if (!level.isLoaded(below)) return false;
            GhostBlockEntity under = GhostBlock.at(level, below);
            BlockPos owner = GhostReservations.owner(level, below);
            GhostBlockEntity reserving = owner == null || owner.equals(pos) ? null : GhostBlock.at(level, owner);
            if ((under != null && under.isValid()) || (reserving != null && reserving.isValid())) {
                if (!allowPlannedSupport) return false;
                continue;
            }
            BlockState support = level.getBlockState(below);
            if (support.canBeReplaced() || !support.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    /**
     * Builds the ghost at {@code pos} with {@code stack} (one item of the ghost's item; not consumed
     * here) through the item's normal placement, by {@code player} or a robot (null). The ghost is
     * restored if placement fails. True once the real block is placed.
     *
     * <p>After placement: an underground belt gets the ghost's entrance/exit type and facing (the
     * placement rule only makes an exit when its entrance is already built; a blueprint's exit may
     * be built first), and the ghost's settings ({@link GhostBlockEntity#settings}) are applied to
     * the new block entity ({@link CopyableSettings}).
     */
    public static boolean build(ServerLevel level, BlockPos pos, @Nullable Player player, ItemStack stack) {
        GhostBlockEntity be = GhostBlock.at(level, pos);
        if (be == null || !be.isValid() || !stack.is(be.item()) || !(stack.getItem() instanceof BlockItem item)) return false;
        if (!canBuild(level, pos)) return false;
        BlockState target = be.target();
        Direction facing = be.facing();
        long seq = be.seq();
        CompoundTag settings = be.settings();
        level.removeBlock(pos, false);
        BlockPos click = MultiblockBlockItem.targetFor(item, pos, facing);
        boolean placed = item.place(context(level, click, player, stack.copyWithCount(1), facing)).consumesAction();
        if (!placed && level.getBlockState(pos).canBeReplaced()) put(level, pos, target, facing, seq, settings);
        if (placed) {
            matchUnderground(level, pos, target);
            if (settings != null && level.getBlockEntity(pos) instanceof CopyableSettings copy) copy.loadSettings(settings);
            refreshAround(level, pos);
        }
        return placed;
    }

    /**
     * An exit ghost built before its entrance would come out as an entrance: it is turned into the
     * exit the ghost shows (facing included), then links. (The other way round needs nothing: a
     * hand ghost planned as an entrance becomes an exit by the normal rule once its entrance stands,
     * and an entrance built behind a standing exit pairs with it on its own.)
     */
    private static void matchUnderground(ServerLevel level, BlockPos pos, BlockState target) {
        BlockState built = level.getBlockState(pos);
        if (!(built.getBlock() instanceof UndergroundBeltBlock) || !target.is(built.getBlock())) return;
        UndergroundBeltBlock.Type type = target.getValue(UndergroundBeltBlock.TYPE);
        Direction f = target.getValue(UndergroundBeltBlock.FACING);
        if (type != UndergroundBeltBlock.Type.OUTPUT || built.getValue(UndergroundBeltBlock.TYPE) == type) return;
        level.setBlock(pos, built.setValue(UndergroundBeltBlock.TYPE, type).setValue(UndergroundBeltBlock.FACING, f), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof UndergroundBeltBlockEntity u) u.tryLink();
    }

    /** A placement at {@code pos} (replacing what is there) looking {@code facing}. */
    static BlockPlaceContext context(Level level, BlockPos pos, @Nullable Player player, ItemStack stack, Direction facing) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, hit) {
            @Override
            public Direction getHorizontalDirection() { return facing; }

            @Override
            public Direction getNearestLookingDirection() { return facing; }

            @Override
            public float getRotation() { return facing.toYRot(); }
        };
    }

    /**
     * The level as it is planned: a ghost reads as the block it stands for. Read-only; block
     * entities are the level's own (a ghost cell has the ghost's).
     */
    private record PlannedView(Level level) implements BlockGetter {
        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) { return level.getBlockEntity(pos); }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState s = level.getBlockState(pos);
            if (s.is(ModBlocks.GHOST.get()) && level.getBlockEntity(pos) instanceof GhostBlockEntity be && be.isValid()) return be.target();
            return s;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) { return level.getFluidState(pos); }

        @Override
        public int getHeight() { return level.getHeight(); }

        @Override
        public int getMinBuildHeight() { return level.getMinBuildHeight(); }
    }
}
