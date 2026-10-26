package dev.minetorio.content.demo;

import dev.minetorio.content.deposit.Deposits;
import dev.minetorio.content.inserter.InserterBlockEntity;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.machine.BurnerDrillBlockEntity;
import dev.minetorio.content.machine.StoneFurnaceBlockEntity;
import dev.minetorio.content.power.BoilerBlockEntity;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.deposit.Deposit;
import dev.minetorio.core.deposit.OreType;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Builds the demo showcase ({@code /minetorio demo}): a {@value #SIZE}×{@value #SIZE} flat area with
 * eight stations along an east–west walkway, all running right away.
 *
 * <p>Coordinates below are (x, z) relative to the area's north-west corner; x grows east, z south.
 * Blocks stand on the floor (y + 1). The walkway is z 46..49; small electric poles on z = 46 every 7
 * blocks wire everything to the steam plant. North of the walkway (west → east): 1 belts, 2 inserters,
 * 3 mining, 4 smelting; south of it (east → west): 5 power, 6 assembly, 7 research, 8 hand crafting.
 * Each station has a sign on the walkway's edge (z 45 north, z 50 south).
 *
 * <p>Machines are placed through their items, like a player placing them (so multiblock parts and
 * belt shapes come out the same), with explicit facings. The endless parts (source/sink chests, belt
 * feeds, research selection) are {@link DemoSavedData}; the iron ore is a placed deposit
 * ({@link Deposits#placeDemoDeposit}).
 */
public final class DemoBuilder {
    public static final int SIZE = 96;
    /** Blocks cleared above the floor. */
    public static final int CLEAR_HEIGHT = 20;
    public static final long DEPOSIT_AMOUNT = 1_000_000;
    private static final int DEPOSIT_X = 58, DEPOSIT_Z = 34, DEPOSIT_RADIUS = 10;
    private static final String IRON = "minetorio:iron_plate";
    private static final String COPPER = "minetorio:copper_plate";
    private static final String COAL = "minecraft:coal";
    private static final String RED = "minetorio:automation_science_pack";
    /** Items a source chest keeps (fuel chests also start with this much). */
    private static final int KEEP = 256;

    /** Where the parts of a showcase are, in world coordinates (the GameTest checks these). */
    public record Layout(BlockPos origin, BoundingBox area, BlockPos entrance, BlockPos depositCenter, BlockPos smeltingOutput,
                         BlockPos gearAssembler, BlockPos gearChest, BlockPos redAssembler, List<BlockPos> labs,
                         List<BlockPos> engines) {}

    private final ServerLevel level;
    private final BlockPos origin;
    private final DemoSavedData data;

    private DemoBuilder(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin.immutable();
        this.data = DemoSavedData.get(level);
    }

    /** The area's north-west floor corner for a showcase around a player standing at {@code feet}. */
    public static BlockPos originAround(BlockPos feet) {
        return feet.offset(-SIZE / 2, -1, -SIZE / 2);
    }

    /** The cleared box of a showcase with floor corner {@code origin} (one layer below the floor up to the clear height). */
    public static BoundingBox area(BlockPos origin) {
        return new BoundingBox(origin.getX(), origin.getY() - 1, origin.getZ(),
                origin.getX() + SIZE - 1, origin.getY() + CLEAR_HEIGHT, origin.getZ() + SIZE - 1);
    }

    /** The layout of a showcase with floor corner {@code origin}. */
    public static Layout layout(BlockPos origin) {
        BlockPos o = origin.above();
        return new Layout(origin, area(origin), o.offset(1, 0, 47), o.offset(DEPOSIT_X, 0, DEPOSIT_Z), o.offset(80, 0, 37),
                o.offset(52, 0, 57), o.offset(56, 0, 58), o.offset(58, 0, 57), List.of(o.offset(30, 0, 58), o.offset(36, 0, 58)),
                List.of(o.offset(77, 0, 61), o.offset(77, 0, 56)));
    }

    /** Builds the showcase with its floor's north-west corner at {@code origin}. Overworld only. */
    public static Layout build(ServerLevel level, BlockPos origin) {
        new DemoBuilder(level, origin).build();
        return layout(origin);
    }

    private void build() {
        BoundingBox area = area(origin);
        data.removeIn(area);
        clear(area);
        BlockPos center = abs(DEPOSIT_X, DEPOSIT_Z);
        Deposit deposit = Deposits.placeDemoDeposit(level, OreType.IRON, center.getX(), center.getZ(), DEPOSIT_RADIUS, DEPOSIT_AMOUNT);
        floor(deposit);
        backbone();
        signs();
        belts();
        inserters();
        mining();
        smelting();
        power();
        assembly();
        research();
        handcraft();
        data.setResearch(true);
        DemoSavedData.selectNextResearch(level.getServer());
    }

    // --- area ------------------------------------------------------------------------------------

    private void clear(BoundingBox area) {
        AABB box = AABB.of(area);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, e -> e instanceof HangingEntity || e instanceof ItemEntity)) {
            e.discard();
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                for (int y = area.maxY(); y > origin.getY(); y--) {
                    p.set(x, y, z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, air, Block.UPDATE_CLIENTS);
                }
            }
        }
        // whatever the clearing dropped (machine and chest contents)
        for (Entity e : level.getEntitiesOfClass(ItemEntity.class, box)) e.discard();
    }

    private void floor(Deposit deposit) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                BlockPos f = origin.offset(x, 0, z);
                Block top = z >= 46 && z <= 49 ? Blocks.DIRT_PATH
                        : deposit.contains(f.getX(), f.getZ()) ? Blocks.RAW_IRON_BLOCK : Blocks.GRASS_BLOCK;
                level.setBlock(f, top.defaultBlockState(), Block.UPDATE_CLIENTS);
                p.set(f.getX(), f.getY() - 1, f.getZ());
                if (!level.getBlockState(p).isSolid()) level.setBlock(p, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Poles along the walkway's north edge, 7 apart: the wire backbone every station links to. */
    private void backbone() {
        for (int x = 3; x < SIZE; x += 7) pole(x, 46);
    }

    private void signs() {
        sign(4, 50, 4, "welcome", 4);
        sign(14, 45, 0, "belts", 4);
        sign(36, 45, 0, "inserters", 4);
        sign(58, 45, 0, "mining", 4);
        sign(80, 45, 0, "smelting", 4);
        sign(80, 50, 8, "power", 4);
        sign(58, 50, 8, "assembly", 4);
        sign(36, 50, 8, "research", 4);
        sign(14, 50, 8, "handcraft", 4);
    }

    // --- 1: belts --------------------------------------------------------------------------------

    private void belts() {
        // three tiers side by side, each fed compressed and ending in an emptied chest
        Item[] tiers = {ModItems.TRANSPORT_BELT.get(), ModItems.FAST_TRANSPORT_BELT.get(), ModItems.EXPRESS_TRANSPORT_BELT.get()};
        String[] labels = {"belt_yellow", "belt_red", "belt_blue"};
        for (int i = 0; i < 3; i++) {
            int z = 28 + 2 * i;
            for (int x = 6; x <= 20; x++) place(tiers[i], x, z, Direction.EAST);
            sink(21, z);
            data.addFeed(abs(6, z), IRON, COPPER);
            sign(4, z, 4, labels[i], 2);
        }
        // a line with a left curve, a right curve, a side-load, an underground under a wall and a splitter
        Item yellow = ModItems.TRANSPORT_BELT.get();
        for (int x = 6; x <= 9; x++) place(yellow, x, 41, Direction.EAST);
        for (int z = 41; z >= 39; z--) place(yellow, 10, z, Direction.NORTH); // (10,41) becomes a curve
        place(yellow, 10, 38, Direction.EAST);                               // curve again
        place(yellow, 11, 38, Direction.EAST);
        place(yellow, 12, 38, Direction.EAST);
        place(ModItems.UNDERGROUND_BELT.get(), 13, 38, Direction.EAST);
        for (int x = 14; x <= 16; x++) {
            for (int z = 37; z <= 39; z++) {
                set(x, 1, z, Blocks.STONE_BRICKS);
                set(x, 2, z, Blocks.STONE_BRICKS);
            }
        }
        place(ModItems.UNDERGROUND_BELT.get(), 17, 38, Direction.EAST);
        place(yellow, 18, 38, Direction.EAST);
        place(ModItems.SPLITTER.get(), 19, 38, Direction.EAST); // part at (19,39)
        for (int z = 38; z <= 39; z++) {
            place(yellow, 20, z, Direction.EAST);
            place(yellow, 21, z, Direction.EAST);
            sink(22, z);
        }
        data.addFeed(abs(6, 41), null, IRON);
        // side-load: copper joins the near (left) lane of (11,38) from the north
        for (int z = 35; z <= 37; z++) place(yellow, 11, z, Direction.SOUTH);
        data.addFeed(abs(11, 35), COPPER, COPPER);
        sign(9, 43, 0, "belt_curve", 2);
        sign(12, 34, 0, "belt_side_load", 2);
        sign(15, 41, 0, "belt_underground", 2);
        sign(20, 41, 0, "belt_splitter", 2);
    }

    // --- 2: inserters ----------------------------------------------------------------------------

    private void inserters() {
        // burner: coal chest → chest (it refuels itself from the coal it carries)
        source(29, 29, COAL, KEEP);
        inserter(ModItems.BURNER_INSERTER.get(), 30, 29, Direction.EAST);
        sink(31, 29);
        // basic: chest → belt → (basic) → chest
        source(29, 33, IRON, KEEP);
        inserter(ModItems.INSERTER.get(), 30, 33, Direction.EAST);
        for (int x = 31; x <= 35; x++) place(ModItems.TRANSPORT_BELT.get(), x, 33, Direction.EAST);
        inserter(ModItems.INSERTER.get(), 36, 33, Direction.EAST);
        sink(37, 33);
        // long-handed: reaches over a gap, 2 blocks each way
        source(29, 37, IRON, KEEP);
        inserter(ModItems.LONG_HANDED_INSERTER.get(), 31, 37, Direction.EAST);
        sink(33, 37);
        // fast
        source(29, 41, COPPER, KEEP);
        inserter(ModItems.FAST_INSERTER.get(), 30, 41, Direction.EAST);
        sink(31, 41);
        pole(32, 35);
        pole(36, 35);
        pole(32, 39);
        sign(28, 30, 0, "inserter_burner", 2);
        sign(28, 34, 0, "inserter_basic", 2);
        sign(28, 38, 0, "inserter_long", 2);
        sign(28, 42, 0, "inserter_fast", 2);
    }

    // --- 3: mining -------------------------------------------------------------------------------

    private void mining() {
        // burner drill (x 51..52, z 30..31) → chest; a burner inserter feeds it coal
        place(ModItems.BURNER_MINING_DRILL.get(), 51, 30, Direction.EAST);
        ((BurnerDrillBlockEntity) be(51, 30)).addFuel(COAL, 5);
        chest(53, 30);
        source(49, 30, COAL, KEEP);
        inserter(ModItems.BURNER_INSERTER.get(), 50, 30, Direction.EAST);
        // electric drill (x 56..58, z 36..38) → belt east to the smelting station
        place(ModItems.ELECTRIC_MINING_DRILL.get(), 56, 36, Direction.EAST);
        for (int x = 59; x <= 75; x++) place(ModItems.TRANSPORT_BELT.get(), x, 37, Direction.EAST);
        pole(56, 40);
        // the scanner: in a frame and in a chest
        set(63, 1, 43, Blocks.POLISHED_ANDESITE);
        ItemFrame frame = new ItemFrame(level, abs(63, 44), Direction.SOUTH);
        frame.setItem(new ItemStack(ModItems.SCANNER.get()), false);
        level.addFreshEntity(frame);
        source(65, 44, "minetorio:scanner", 4);
        sign(61, 44, 0, "scanner", 4);
    }

    // --- 4: smelting -----------------------------------------------------------------------------

    private void smelting() {
        inserter(ModItems.INSERTER.get(), 76, 37, Direction.EAST);
        place(ModItems.STONE_FURNACE.get(), 77, 37, Direction.EAST); // x 77..78, z 37..38
        insert(77, 37, StoneFurnaceBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 5));
        inserter(ModItems.INSERTER.get(), 79, 37, Direction.EAST);
        chest(80, 37);
        source(77, 35, COAL, KEEP);
        inserter(ModItems.BURNER_INSERTER.get(), 77, 36, Direction.SOUTH);
        pole(78, 39);
    }

    // --- 5: power --------------------------------------------------------------------------------

    /** Water → offshore pump → pipes → boiler (coal by a burner inserter) → 2 steam engines in a row. */
    private void power() {
        BlockPos water = abs(71, 63);
        level.setBlock(water.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(water.west(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(water.north(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(water.south(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(water, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        for (int x = 74; x <= 76; x++) place(ModItems.PIPE.get(), x, 63, Direction.NORTH); // before the machines
        place(ModItems.OFFSHORE_PUMP.get(), 73, 63, Direction.WEST);   // x 72..73
        place(ModItems.BOILER.get(), 77, 63, Direction.NORTH);         // x 77..79, z 62..63; steam port (78,62)
        place(ModItems.STEAM_ENGINE.get(), 77, 61, Direction.NORTH);   // z 57..61
        place(ModItems.STEAM_ENGINE.get(), 77, 56, Direction.NORTH);   // z 52..56
        ((BoilerBlockEntity) be(77, 63)).addFuel(COAL, 5);
        source(77, 65, COAL, KEEP);
        inserter(ModItems.BURNER_INSERTER.get(), 77, 64, Direction.NORTH);
        pole(81, 51);
        pole(81, 57);
    }

    // --- 6: assembly -----------------------------------------------------------------------------

    private void assembly() {
        for (int x = 50; x <= 66; x++) place(ModItems.TRANSPORT_BELT.get(), x, 55, Direction.EAST);
        sink(67, 55);
        data.addFeed(abs(50, 55), IRON, COPPER);
        assembler(52, 57, "iron_gear_wheel");                          // x 52..54, z 57..59
        inserter(ModItems.INSERTER.get(), 53, 56, Direction.SOUTH);    // plates from the belt
        inserter(ModItems.INSERTER.get(), 55, 58, Direction.EAST);     // gears out
        chest(56, 58);
        inserter(ModItems.INSERTER.get(), 57, 58, Direction.EAST);     // gears in
        assembler(58, 57, "automation_science_pack");                  // x 58..60, z 57..59
        inserter(ModItems.INSERTER.get(), 59, 56, Direction.SOUTH);    // copper from the belt
        inserter(ModItems.INSERTER.get(), 61, 58, Direction.EAST);     // red packs out
        chest(62, 58);
        pole(51, 56);
        pole(56, 56);
        pole(61, 56);
        pole(56, 51);
    }

    // --- 7: research -----------------------------------------------------------------------------

    private void research() {
        place(ModItems.LAB.get(), 30, 58, Direction.EAST); // x 30..32, z 58..60
        inserter(ModItems.INSERTER.get(), 33, 59, Direction.WEST);
        source(34, 59, RED, 128);
        inserter(ModItems.INSERTER.get(), 35, 59, Direction.EAST);
        place(ModItems.LAB.get(), 36, 58, Direction.EAST); // x 36..38
        pole(34, 57);
        pole(34, 52);
    }

    // --- 8: hand crafting ------------------------------------------------------------------------

    private void handcraft() {
        source(14, 52, IRON, 128);
        source(14, 52, COPPER, 128);
        source(14, 52, "minecraft:cobblestone", 64);
        source(14, 52, "minecraft:stick", 32);
        source(14, 52, COAL, 64);
    }

    // --- helpers ---------------------------------------------------------------------------------

    /** World position of relative (x, z), one block above the floor. */
    private BlockPos abs(int x, int z) {
        return origin.offset(x, 1, z);
    }

    private void set(int x, int y, int z, Block block) {
        level.setBlock(origin.offset(x, y, z), block.defaultBlockState(), Block.UPDATE_ALL);
    }

    @Nullable
    private net.minecraft.world.level.block.entity.BlockEntity be(int x, int z) {
        return level.getBlockEntity(abs(x, z));
    }

    /** Places {@code item} like a player looking {@code facing} would. */
    private void place(Item item, int x, int z, Direction facing) {
        BlockPos pos = abs(x, z);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        BlockPlaceContext ctx = new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, new ItemStack(item), hit) {
            @Override
            public Direction getHorizontalDirection() { return facing; }
        };
        if (!((BlockItem) item).place(ctx).consumesAction()) {
            throw new IllegalStateException("demo: could not place " + BuiltInRegistries.ITEM.getKey(item) + " at " + pos);
        }
    }

    private void inserter(Item item, int x, int z, Direction facing) {
        place(item, x, z, facing);
        InserterBlockEntity be = (InserterBlockEntity) be(x, z);
        if (be.type().burner) be.addFuel(COAL, 5);
    }

    private void assembler(int x, int z, String recipe) {
        place(ModItems.ASSEMBLING_MACHINE_1.get(), x, z, Direction.EAST);
        ((AssemblerBlockEntity) be(x, z)).setRecipe(Phase1Data.recipes().get(recipe).orElseThrow());
    }

    private void pole(int x, int z) {
        place(ModItems.SMALL_ELECTRIC_POLE.get(), x, z, Direction.NORTH);
    }

    private void chest(int x, int z) {
        level.setBlock(abs(x, z), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** A chest kept at {@code keep} items of {@code item}; filled right away. Several items may share a chest. */
    private void source(int x, int z, String item, int keep) {
        BlockPos pos = abs(x, z);
        if (!level.getBlockState(pos).is(Blocks.CHEST)) chest(x, z);
        data.addSource(pos, item, keep);
        Item it = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(item));
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        int max = new ItemStack(it).getMaxStackSize();
        for (int left = keep; left > 0; left -= max) ItemHandlerHelper.insertItemStacked(handler, new ItemStack(it, Math.min(left, max)), false);
    }

    /** A chest emptied regularly. */
    private void sink(int x, int z) {
        chest(x, z);
        data.addSink(abs(x, z));
    }

    private void insert(int x, int z, int slot, ItemStack stack) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, abs(x, z), null);
        handler.insertItem(slot, stack, false);
    }

    /**
     * A standing sign at (x, z) facing {@code rotation} (0 south, 4 west, 8 north, 12 east) showing the
     * lines {@code minetorio.demo.sign.<id>.1..lines} (translated on each client, vertically centered)
     * on both sides; waxed.
     */
    private void sign(int x, int z, int rotation, String id, int lines) {
        BlockPos pos = abs(x, z);
        level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
        SignText text = new SignText();
        int first = (4 - lines) / 2;
        for (int i = 0; i < lines; i++) text = text.setMessage(first + i, Component.translatable("minetorio.demo.sign." + id + "." + (i + 1)));
        sign.setText(text, true);
        sign.setText(text, false);
        sign.setWaxed(true);
    }
}
