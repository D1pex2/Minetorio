package dev.minetorio.content.orefield;

import dev.minetorio.Minetorio;
import dev.minetorio.content.registry.ModAttachments;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.core.orefield.AmountFormat;
import dev.minetorio.core.orefield.FieldStamp;
import dev.minetorio.core.orefield.OreAmounts;
import dev.minetorio.core.orefield.OrePatch;
import dev.minetorio.core.orefield.OreType;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Ore fields in the world: the field blocks ({@link OreFieldBlock}, one per {@link OreType}) and
 * their amounts in the chunk attachment {@link OreFieldData}. Stamping puts a field block on the
 * natural surface of a column ({@link #stampColumn}); mining takes one ore from a block and turns it
 * into dirt when it is empty ({@link #mineOne}). A field block without a stored amount (placed by
 * a command) holds its type's base amount.
 *
 * <p>Amount display (lightweight, no client code): the action bar shows a field block's amount when
 * a player starts breaking it, after each ore mined by hand, and on right-click with an empty hand.
 */
public final class OreFields {
    /** Natural surface blocks a field may replace (grass, dirt, sand, gravel, stone, clay, …). */
    public static final TagKey<Block> REPLACEABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "ore_field_replaceable"));
    /** How far below the given top a column is searched for its surface (past plants and snow). */
    public static final int MAX_SCAN = 16;

    private OreFields() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(OreFields::onLeftClick);
    }

    private static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getLevel().isClientSide() || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (typeOf(e.getLevel().getBlockState(e.getPos())) == null) return;
        e.getEntity().displayClientMessage(amountMessage(e.getLevel(), e.getPos()), true);
    }

    // --- blocks ----------------------------------------------------------------------------------------

    public static OreFieldBlock block(OreType type) {
        return switch (type) {
            case IRON -> ModBlocks.IRON_ORE_FIELD.get();
            case COPPER -> ModBlocks.COPPER_ORE_FIELD.get();
            case COAL -> ModBlocks.COAL_ORE_FIELD.get();
            case STONE -> ModBlocks.STONE_ORE_FIELD.get();
        };
    }

    /** The ore type of a field block, or null for any other block. */
    @Nullable
    public static OreType typeOf(BlockState state) {
        return state.getBlock() instanceof OreFieldBlock b ? b.type() : null;
    }

    public static ItemStack ore(OreType type) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(type.item())));
    }

    // --- amounts ---------------------------------------------------------------------------------------

    /** Ore left in the field block at {@code pos}; 0 if it is not a field block. */
    public static long remaining(Level level, BlockPos pos) {
        OreType type = typeOf(level.getBlockState(pos));
        if (type == null) return 0;
        OreFieldData data = level.getChunkAt(pos).getExistingDataOrNull(ModAttachments.ORE_FIELD.get());
        long left = data == null ? -1 : data.remaining(pos);
        return left < 0 ? type.baseAmount() : left;
    }

    /** Initial amount of the field block at {@code pos}; 0 if it is not a field block. */
    public static long initial(Level level, BlockPos pos) {
        OreType type = typeOf(level.getBlockState(pos));
        if (type == null) return 0;
        OreFieldData data = level.getChunkAt(pos).getExistingDataOrNull(ModAttachments.ORE_FIELD.get());
        long init = data == null ? -1 : data.initial(pos);
        return init < 0 ? type.baseAmount() : init;
    }

    /**
     * Puts a fresh field block of {@code type} holding {@code amount} ore at {@code pos} (live levels
     * and world generation alike: the amount goes to the chunk {@code level} hands out for {@code pos}).
     */
    public static void place(LevelAccessor level, BlockPos pos, OreType type, long amount) {
        place(level, pos, type, amount, 0);
    }

    /**
     * {@link #place} for the patch with key {@code patchKey} (0: no patch). Does not add the amount to
     * the patch's total (stamping does that, see {@link #placeOwned}); a field block it replaces is
     * subtracted from its own patch, on a live level.
     */
    public static void place(LevelAccessor level, BlockPos pos, OreType type, long amount, long patchKey) {
        level.setBlock(pos, block(type).defaultBlockState().setValue(OreFieldBlock.DEPLETED, OreAmounts.isDepleted(amount, amount)),
                Block.UPDATE_CLIENTS);
        ChunkAccess chunk = level.getChunk(pos);
        OreFieldData data = chunk.getData(ModAttachments.ORE_FIELD.get());
        // a field block of the same type was there (no removal happened): its ore leaves its patch
        if (level instanceof ServerLevel server && data.has(pos)) release(server, data, pos);
        data.set(pos, amount, amount);
        data.setOwner(pos, patchKey);
        chunk.setUnsaved(true);
    }

    /** {@link #place} for the patch with key {@code patchKey}, adding {@code amount} to its total. */
    public static void placeOwned(ServerLevel level, BlockPos pos, OreType type, long amount, long patchKey) {
        place(level, pos, type, amount, patchKey);
        OrePatchLedger.get(level).add(patchKey, amount);
    }

    /** The ore left in the block at {@code pos} leaves its patch's total (the block is going away). */
    private static void release(ServerLevel level, OreFieldData data, BlockPos pos) {
        long owner = data.owner(pos);
        long left = data.remaining(pos);
        if (owner != 0 && left > 0) OrePatchLedger.get(level).add(owner, -left);
    }

    /**
     * Takes one ore from the field block at {@code pos}: returns its type, or null if there is no
     * field block. The block becomes dirt when it runs empty and turns pale when it falls below 10 %.
     */
    @Nullable
    public static OreType mineOne(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        OreType type = typeOf(state);
        if (type == null) return null;
        ChunkAccess chunk = level.getChunkAt(pos);
        OreFieldData data = chunk.getData(ModAttachments.ORE_FIELD.get());
        long init = data.initial(pos);
        long left = data.remaining(pos);
        if (left < 0) {
            init = type.baseAmount();
            left = init;
        } else {
            OrePatchLedger.get(level).add(data.owner(pos), -1);
        }
        left--;
        if (left <= 0) {
            data.remove(pos);
            chunk.setUnsaved(true);
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        } else {
            data.set(pos, init, left);
            chunk.setUnsaved(true);
            boolean depleted = OreAmounts.isDepleted(left, init);
            if (depleted != state.getValue(OreFieldBlock.DEPLETED)) {
                level.setBlock(pos, state.setValue(OreFieldBlock.DEPLETED, depleted), Block.UPDATE_CLIENTS);
            }
        }
        return type;
    }

    /** A player broke a field block in survival: one ore pops out on top, the amount left goes to the action bar. */
    static void mineByHand(ServerLevel level, BlockPos pos, Player player) {
        OreType type = mineOne(level, pos);
        if (type == null) return;
        Block.popResourceFromFace(level, pos, Direction.UP, ore(type));
        player.displayClientMessage(amountMessage(type, remaining(level, pos)), true);
    }

    /** Turns the field block at {@code pos} into dirt (its amount is dropped); no-op for other blocks. */
    public static void exhaust(ServerLevel level, BlockPos pos) {
        if (typeOf(level.getBlockState(pos)) != null) level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** Drops the stored amount at {@code pos} (the field block there is gone). */
    static void forget(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        ChunkAccess chunk = level.getChunkAt(pos);
        OreFieldData data = chunk.getExistingDataOrNull(ModAttachments.ORE_FIELD.get());
        if (data == null || !data.has(pos)) return;
        if (level instanceof ServerLevel server) release(server, data, pos);
        data.remove(pos);
        chunk.setUnsaved(true);
    }

    // --- stamping ----------------------------------------------------------------------------------------

    /**
     * Puts a field block on the natural surface of column (x, z), searching down from {@code topY}
     * (at most {@value #MAX_SCAN} blocks, not below {@code minY}): air, plants and snow layers are
     * passed; the first other block is replaced if it is a natural surface block ({@link #REPLACEABLE})
     * or a field block. A column whose surface is a fluid (water, lava, waterlogged), leaves, a log or
     * anything else (buildings, paths) is skipped. Returns whether a block was stamped.
     */
    public static boolean stampColumn(LevelAccessor level, int x, int z, int topY, int minY, OreType type, long amount) {
        return stampColumn(level, x, z, topY, minY, type, amount, 0);
    }

    /** {@link #stampColumn} for the patch with key {@code patchKey} (0: none); the caller adds the amount to the patch. */
    public static boolean stampColumn(LevelAccessor level, int x, int z, int topY, int minY, OreType type, long amount, long patchKey) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = topY; y >= Math.max(minY, topY - MAX_SCAN); y--) {
            BlockState state = level.getBlockState(p.set(x, y, z));
            if (state.isAir()) continue;
            if (!state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) return false;
            if (state.is(REPLACEABLE) || typeOf(state) != null) {
                place(level, p.immutable(), type, amount, patchKey);
                return true;
            }
            if (state.canBeReplaced() || !state.blocksMotion()) continue;
            return false;
        }
        return false;
    }

    /** Stamps {@code cells} into a live level, each on its column's surface; returns how many blocks were stamped. */
    public static int stamp(ServerLevel level, List<FieldStamp.Cell> cells) {
        return stamp(level, cells, 0);
    }

    /**
     * {@link #stamp} for the patch with key {@code patchKey} (0: none), adding the stamped blocks'
     * amounts to its total ({@link OrePatchLedger}).
     */
    public static int stamp(ServerLevel level, List<FieldStamp.Cell> cells, long patchKey) {
        int n = 0;
        long sum = 0;
        for (FieldStamp.Cell c : cells) {
            level.getChunk(c.x() >> 4, c.z() >> 4); // load (or generate) before reading the height
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, c.x(), c.z()) - 1;
            if (stampColumn(level, c.x(), c.z(), top, level.getMinBuildHeight(), c.type(), c.amount(), patchKey)) {
                n++;
                sum += c.amount();
            }
        }
        OrePatchLedger.get(level).add(patchKey, sum);
        return n;
    }

    /**
     * The topmost field block of column (x, z) from {@code topY} down to {@code bottomY} (inclusive),
     * or null. Other blocks in between (a drill standing on the field, a plant) are passed.
     */
    @Nullable
    public static BlockPos fieldInColumn(Level level, int x, int z, int topY, int bottomY) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = topY; y >= bottomY; y--) {
            if (typeOf(level.getBlockState(p.set(x, y, z))) != null) return p.immutable();
        }
        return null;
    }

    /** The field block on the surface of column (x, z) (at most {@value #MAX_SCAN} blocks below the top), or null. */
    @Nullable
    public static BlockPos surfaceField(Level level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        return fieldInColumn(level, x, z, top, Math.max(level.getMinBuildHeight(), top - MAX_SCAN));
    }

    /**
     * Removes the stamped field blocks of a generated {@code patch} (on each column's surface, loading
     * or generating its chunks): each becomes the natural block under it if that is a surface block
     * ({@link #REPLACEABLE}), else dirt, and its amount is forgotten. Returns how many there were.
     */
    public static int unstamp(ServerLevel level, OrePatch patch) {
        int n = 0;
        int r = patch.radius();
        for (int x = patch.centerX() - r; x <= patch.centerX() + r; x++) {
            for (int z = patch.centerZ() - r; z <= patch.centerZ() + r; z++) {
                if (!patch.contains(x, z)) continue;
                level.getChunk(x >> 4, z >> 4);
                BlockPos pos = surfaceField(level, x, z);
                if (pos == null || typeOf(level.getBlockState(pos)) != patch.type()) continue;
                BlockState below = level.getBlockState(pos.below());
                BlockState fill = below.is(REPLACEABLE) && typeOf(below) == null ? below : Blocks.DIRT.defaultBlockState();
                level.setBlock(pos, fill, Block.UPDATE_ALL);
                n++;
            }
        }
        return n;
    }

    /** Turns every field block of {@code patch}'s disk at height {@code y} into dirt; returns how many there were. */
    public static int clear(ServerLevel level, OrePatch patch, int y) {
        int n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = patch.radius();
        for (int x = patch.centerX() - r; x <= patch.centerX() + r; x++) {
            for (int z = patch.centerZ() - r; z <= patch.centerZ() + r; z++) {
                if (!patch.contains(x, z) || typeOf(level.getBlockState(p.set(x, y, z))) == null) continue;
                exhaust(level, p);
                n++;
            }
        }
        return n;
    }

    // --- messages ------------------------------------------------------------------------------------------

    /** "Iron ore field: 190k left": the remaining ore of a whole patch ({@link AmountFormat#compact}). */
    public static Component patchMessage(OreType type, long total) {
        return Component.translatable("minetorio.ore_field.patch", patchName(type), AmountFormat.compact(total));
    }

    /** Capitalized patch name ("Iron ore field") for the start of a line. */
    public static Component patchName(OreType type) {
        return Component.translatable("minetorio.ore_patch." + type.name().toLowerCase(Locale.ROOT));
    }

    /** "Iron ore field: 1 234 left" for the block at {@code pos} (or "no ore" if it is not a field block). */
    public static Component amountMessage(Level level, BlockPos pos) {
        OreType type = typeOf(level.getBlockState(pos));
        return type == null ? Component.translatable("minetorio.ore_field.none") : amountMessage(type, remaining(level, pos));
    }

    public static Component amountMessage(OreType type, long remaining) {
        return remaining <= 0 ? Component.translatable("minetorio.ore_field.exhausted", oreName(type))
                : Component.translatable("minetorio.ore_field.amount", oreName(type), formatAmount(remaining));
    }

    /** Lower-case ore name for use inside a sentence. */
    public static Component oreName(OreType type) {
        return Component.translatable("minetorio.ore." + type.name().toLowerCase(Locale.ROOT));
    }

    /** {@code 12345} → {@code "12 345"} (digit groups separated by spaces, in every language). */
    public static String formatAmount(long n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', ' ');
    }
}
