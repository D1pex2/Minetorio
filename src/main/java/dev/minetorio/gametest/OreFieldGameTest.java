package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.dropped;

import dev.minetorio.Minetorio;
import dev.minetorio.content.orefield.OreFieldBlock;
import dev.minetorio.content.orefield.OreFieldData;
import dev.minetorio.content.orefield.OreFieldPayloads;
import dev.minetorio.content.orefield.OreFields;
import dev.minetorio.content.orefield.OrePatchLedger;
import dev.minetorio.content.orefield.StartingFields;
import dev.minetorio.content.registry.ModAttachments;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.registry.ModFeatures;
import dev.minetorio.core.orefield.FieldStamp;
import dev.minetorio.core.orefield.OrePatch;
import dev.minetorio.core.orefield.OreType;
import dev.minetorio.core.orefield.PatchGenerator;
import dev.minetorio.core.orefield.PatchKind;
import dev.minetorio.core.orefield.PatchLedger;
import dev.minetorio.core.orefield.PatchLookup;
import dev.minetorio.core.orefield.PatchShape;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Ore field blocks: hand mining, depletion, persistence, stamping onto the natural surface, world generation setup. */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class OreFieldGameTest {
    private static ServerPlayer miner(GameTestHelper h, ItemStack tool) {
        ServerPlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        p.setItemInHand(InteractionHand.MAIN_HAND, tool);
        return p;
    }

    /**
     * Breaking a field block as a survival player yields exactly one ore and keeps the block (one
     * ore less) until it is empty, then it is dirt. Bare hands work too; the pickaxe takes wear.
     */
    @GameTest(template = "empty16")
    public static void handMiningTakesOneOreAtATime(GameTestHelper h) {
        BlockPos rel = new BlockPos(4, 1, 4);
        BlockPos abs = h.absolutePos(rel);
        OreFields.place(h.getLevel(), abs, OreType.IRON, 3);
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        ServerPlayer player = miner(h, pick);
        h.assertTrue(player.gameMode.destroyBlock(abs), "break refused");
        h.assertTrue(dropped(h, ModItems.IRON_ORE.get()) == 1, "dropped " + dropped(h, ModItems.IRON_ORE.get()));
        h.assertTrue(OreFields.typeOf(h.getBlockState(rel)) == OreType.IRON, "block gone: " + h.getBlockState(rel));
        h.assertTrue(OreFields.remaining(h.getLevel(), abs) == 2, "remaining " + OreFields.remaining(h.getLevel(), abs));
        h.assertTrue(pick.getDamageValue() == 1, "pickaxe damage " + pick.getDamageValue());

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // bare hands: slow, but still ore
        player.gameMode.destroyBlock(abs);
        h.assertTrue(dropped(h, ModItems.IRON_ORE.get()) == 2, "bare hands dropped " + dropped(h, ModItems.IRON_ORE.get()));
        h.assertTrue(OreFields.remaining(h.getLevel(), abs) == 1, "remaining " + OreFields.remaining(h.getLevel(), abs));

        player.gameMode.destroyBlock(abs);
        h.assertTrue(dropped(h, ModItems.IRON_ORE.get()) == 3, "dropped " + dropped(h, ModItems.IRON_ORE.get()));
        h.assertTrue(h.getBlockState(rel).is(Blocks.DIRT), "not dirt when empty: " + h.getBlockState(rel));
        LevelChunk chunk = h.getLevel().getChunkAt(abs);
        OreFieldData data = chunk.getExistingDataOrNull(ModAttachments.ORE_FIELD.get());
        h.assertTrue(data == null || !data.has(abs), "amount left behind");
        h.succeed();
    }

    /** Each field type yields its own ore: mod iron/copper ore, vanilla coal and cobblestone. Silk touch changes nothing. */
    @GameTest(template = "empty16")
    public static void eachFieldDropsItsOre(GameTestHelper h) {
        ItemStack silk = new ItemStack(Items.DIAMOND_PICKAXE);
        silk.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        ServerPlayer player = miner(h, silk);
        OreType[] types = OreType.values();
        for (int i = 0; i < types.length; i++) {
            BlockPos abs = h.absolutePos(new BlockPos(2 + 2 * i, 1, 2));
            OreFields.place(h.getLevel(), abs, types[i], 100);
            player.gameMode.destroyBlock(abs);
        }
        h.assertTrue(dropped(h, ModItems.IRON_ORE.get()) == 1, "iron ore");
        h.assertTrue(dropped(h, ModItems.COPPER_ORE.get()) == 1, "copper ore");
        h.assertTrue(dropped(h, Items.COAL) == 1, "coal");
        h.assertTrue(dropped(h, Items.COBBLESTONE) == 1, "cobblestone");
        h.assertTrue(h.getEntities(EntityType.ITEM).size() == 4, "other drops");
        h.succeed();
    }

    /** Below 10 % of the initial amount the block switches to its pale (depleted) state, keeping its amount. */
    @GameTest(template = "empty16")
    public static void depletedStateBelowTenPercent(GameTestHelper h) {
        BlockPos abs = h.absolutePos(new BlockPos(3, 1, 3));
        OreFields.place(h.getLevel(), abs, OreType.COPPER, 100);
        LevelChunk chunk = h.getLevel().getChunkAt(abs);
        chunk.getData(ModAttachments.ORE_FIELD.get()).set(abs, 100, 11);
        OreFields.mineOne(h.getLevel(), abs);
        h.assertFalse(h.getLevel().getBlockState(abs).getValue(OreFieldBlock.DEPLETED), "pale at 10");
        OreFields.mineOne(h.getLevel(), abs);
        h.assertTrue(h.getLevel().getBlockState(abs).getValue(OreFieldBlock.DEPLETED), "not pale at 9");
        h.assertTrue(OreFields.remaining(h.getLevel(), abs) == 9 && OreFields.initial(h.getLevel(), abs) == 100,
                "amounts " + OreFields.remaining(h.getLevel(), abs) + "/" + OreFields.initial(h.getLevel(), abs));
        h.succeed();
    }

    /** Fields resist pistons and explosions; a block replaced by other means takes its amount with it. */
    @GameTest(template = "empty16")
    public static void fieldsAreSturdy(GameTestHelper h) {
        BlockPos rel = new BlockPos(5, 1, 5);
        BlockPos abs = h.absolutePos(rel);
        OreFields.place(h.getLevel(), abs, OreType.COAL, 500);
        BlockState state = h.getBlockState(rel);
        h.assertTrue(state.getPistonPushReaction() == PushReaction.BLOCK, "pushable");
        h.assertTrue(state.getBlock().getExplosionResistance() >= Blocks.OBSIDIAN.getExplosionResistance(), "weak to explosions");
        h.assertTrue(state.getBlock().asItem() == Items.AIR, "has an item");
        h.setBlock(rel, Blocks.STONE);
        h.setBlock(rel, OreFields.block(OreType.COAL));
        h.assertTrue(OreFields.remaining(h.getLevel(), abs) == OreType.COAL.baseAmount(), "old amount kept: " + OreFields.remaining(h.getLevel(), abs));
        h.succeed();
    }

    /** Field amounts are written with the chunk and read back by the chunk loader. */
    @GameTest(template = "empty16")
    public static void fieldDataSurvivesSaveAndLoad(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(1, 1, 1));
        BlockPos b = h.absolutePos(new BlockPos(1, 2, 1));
        OreFields.place(level, a, OreType.IRON, 777);
        OreFields.place(level, b, OreType.STONE, 5);
        OreFields.mineOne(level, a);
        LevelChunk chunk = level.getChunkAt(a);
        h.assertTrue(chunk.isUnsaved(), "chunk not marked for saving");
        CompoundTag saved = ChunkSerializer.write(level, chunk);
        h.assertTrue(saved.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY), "no attachments saved");
        ProtoChunk loaded = new ProtoChunk(chunk.getPos(), UpgradeData.EMPTY, level,
                level.registryAccess().registryOrThrow(Registries.BIOME), null);
        loaded.readAttachmentsFromNBT(level.registryAccess(), saved.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY));
        OreFieldData data = loaded.getExistingDataOrNull(ModAttachments.ORE_FIELD.get());
        h.assertTrue(data != null, "no field data after loading");
        h.assertTrue(data.remaining(a) == 776 && data.initial(a) == 777, "a: " + data.remaining(a) + "/" + data.initial(a));
        h.assertTrue(data.remaining(b) == 5 && data.initial(b) == 5, "b: " + data.remaining(b) + "/" + data.initial(b));
        h.succeed();
    }

    /**
     * Stamping a column replaces only its natural surface block: grass (under a plant), sand, stone
     * under a snow layer and an older field block are stamped; columns topped by water, leaves, a
     * log or a building block are skipped and stay as they were.
     */
    @GameTest(template = "empty16")
    public static void stampingOnlyOnNaturalSurface(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        record Column(int x, int z, BlockState surface, BlockState above, boolean stamped) {}
        Column[] columns = {
            new Column(1, 1, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState(), true),
            new Column(3, 1, Blocks.SAND.defaultBlockState(), Blocks.AIR.defaultBlockState(), true),
            new Column(5, 1, Blocks.STONE.defaultBlockState(), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1), true),
            new Column(7, 1, Blocks.GRAVEL.defaultBlockState(), Blocks.AIR.defaultBlockState(), true),
            new Column(9, 1, OreFields.block(OreType.COPPER).defaultBlockState(), Blocks.AIR.defaultBlockState(), true),
            new Column(1, 4, Blocks.DIRT.defaultBlockState(), Blocks.WATER.defaultBlockState(), false),
            new Column(3, 4, Blocks.DIRT.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(), false),
            new Column(5, 4, Blocks.DIRT.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), false),
            new Column(7, 4, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.AIR.defaultBlockState(), false),
            new Column(9, 4, Blocks.COBBLESTONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false),
            new Column(11, 4, Blocks.DIRT.defaultBlockState(), Blocks.LAVA.defaultBlockState(), false),
        };
        for (Column c : columns) {
            h.setBlock(new BlockPos(c.x, 1, c.z), c.surface);
            h.setBlock(new BlockPos(c.x, 2, c.z), c.above);
        }
        for (Column c : columns) {
            BlockPos top = h.absolutePos(new BlockPos(c.x, 6, c.z));
            boolean stamped = OreFields.stampColumn(level, top.getX(), top.getZ(), top.getY(), top.getY() - 5, OreType.IRON, 321);
            BlockPos surface = new BlockPos(c.x, 1, c.z);
            h.assertTrue(stamped == c.stamped, "column " + c.surface + " under " + c.above + ": stamped " + stamped);
            if (c.stamped) {
                h.assertTrue(OreFields.typeOf(h.getBlockState(surface)) == OreType.IRON, "not iron: " + h.getBlockState(surface));
                h.assertTrue(OreFields.remaining(level, h.absolutePos(surface)) == 321, "amount " + OreFields.remaining(level, h.absolutePos(surface)));
            } else {
                h.assertTrue(h.getBlockState(surface).equals(c.surface), "changed: " + h.getBlockState(surface));
            }
        }
        h.succeed();
    }

    /** The world generation feature, its placement and the Overworld biome modifier are registered. */
    @GameTest(template = "empty16")
    public static void worldGenerationIsSetUp(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "ore_fields");
        h.assertTrue(registries.registryOrThrow(Registries.CONFIGURED_FEATURE).containsKey(id), "no configured feature");
        h.assertTrue(registries.registryOrThrow(Registries.PLACED_FEATURE).containsKey(id), "no placed feature");
        h.assertTrue(registries.registryOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .containsKey(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, id)), "no biome modifier");
        h.succeed();
    }

    /**
     * The world generation feature, run on a chunk far away that holds part of a generated patch,
     * stamps exactly that part of the patch onto the surface (the test world's flat grass), with the
     * planned amounts; running it on the neighboring chunks completes the patch the same way.
     */
    @GameTest(template = "empty16")
    public static void featureStampsTheGeneratedPatch(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        PatchGenerator gen = new PatchGenerator(level.getSeed(), 0, 0);
        OrePatch patch = null;
        for (int rx = 3000; patch == null; rx++) patch = gen.inRegion(rx, 3000);
        runFeature(level, patch);
        int stamped = 0;
        List<FieldStamp.Cell> plan = FieldStamp.plan(List.of(patch), patch.centerX() - patch.radius(), patch.centerZ() - patch.radius(),
                patch.centerX() + patch.radius(), patch.centerZ() + patch.radius(), 0, 0);
        for (FieldStamp.Cell c : plan) {
            BlockPos top = new BlockPos(c.x(), level.getHeight(Heightmap.Types.WORLD_SURFACE, c.x(), c.z()) - 1, c.z());
            if (OreFields.typeOf(level.getBlockState(top)) != patch.type()) continue;
            h.assertTrue(OreFields.remaining(level, top) == c.amount(), "amount at " + top + ": " + OreFields.remaining(level, top) + " vs " + c.amount());
            stamped++;
        }
        h.assertTrue(stamped == plan.size(), "stamped " + stamped + " of " + plan.size() + " columns of " + patch);
        h.assertTrue(OreFields.typeOf(level.getBlockState(new BlockPos(patch.centerX() + patch.radius() + 1,
                level.getHeight(Heightmap.Types.WORLD_SURFACE, patch.centerX() + patch.radius() + 1, patch.centerZ()) - 1, patch.centerZ()))) == null,
                "stamped outside the patch");
        h.succeed();
    }

    /** Runs the world generation feature on every chunk {@code patch} touches (the flat test world has no features). */
    private static void runFeature(ServerLevel level, OrePatch patch) {
        int minCx = (patch.centerX() - patch.radius()) >> 4, maxCx = (patch.centerX() + patch.radius()) >> 4;
        int minCz = (patch.centerZ() - patch.radius()) >> 4, maxCz = (patch.centerZ() + patch.radius()) >> 4;
        var generator = level.getChunkSource().getGenerator();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                level.getChunk(cx, cz);
                BlockPos origin = new BlockPos(cx << 4, level.getMinBuildHeight(), cz << 4);
                ModFeatures.ORE_FIELD.get().place(new FeaturePlaceContext<>(Optional.empty(), level, generator, RandomSource.create(),
                        origin, NoneFeatureConfiguration.INSTANCE));
            }
        }
    }

    /** Field blocks of {@code type} on the surface of {@code patch}'s disk. */
    private static int fieldsOf(ServerLevel level, OrePatch patch, OreType type) {
        int n = 0;
        int r = patch.radius();
        for (int x = patch.centerX() - r; x <= patch.centerX() + r; x++) {
            for (int z = patch.centerZ() - r; z <= patch.centerZ() + r; z++) {
                if (!patch.contains(x, z)) continue;
                BlockPos pos = OreFields.surfaceField(level, x, z);
                if (pos != null && OreFields.typeOf(level.getBlockState(pos)) == type) n++;
            }
        }
        return n;
    }

    /**
     * A generated patch whose edge reaches into the starting area of a spawn far from the origin is
     * removed whole (its blocks back to the ground under them, amounts forgotten) before the starting
     * fields are stamped; the starting fields are then stamped around that spawn.
     */
    @GameTest(template = "empty16", timeoutTicks = 400)
    public static void startingAreaIsClearedOfGeneratedPatches(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        PatchGenerator gen = new PatchGenerator(level.getSeed(), 0, 0);
        OrePatch patch = null;
        for (int rx = 3000; patch == null; rx++) patch = gen.inRegion(rx, -3000);
        runFeature(level, patch);
        int stamped = fieldsOf(level, patch, patch.type());
        h.assertTrue(stamped > 0, "the generated patch was not stamped: " + patch);
        // a spawn east of the patch, with only the patch's eastern edge inside the starting area
        int sx = patch.centerX() + PatchGenerator.STARTING_AREA_RADIUS + patch.radius() - 3, sz = patch.centerZ();
        h.assertTrue(gen.patchesInStartingArea(sx, sz).contains(patch), "patch not in the starting area");
        int cleared = StartingFields.clearGenerated(level, sx, sz);
        h.assertTrue(cleared >= stamped, "cleared " + cleared + " of " + stamped);
        h.assertTrue(fieldsOf(level, patch, patch.type()) == 0, "generated field blocks left: " + fieldsOf(level, patch, patch.type()));
        BlockPos center = new BlockPos(patch.centerX(), level.getHeight(Heightmap.Types.WORLD_SURFACE, patch.centerX(), patch.centerZ()) - 1,
                patch.centerZ());
        h.assertTrue(OreFields.remaining(level, center) == 0, "amount left at the center");
        h.assertTrue(StartingFields.stampAround(level, sx, sz) > 0, "starting fields not stamped");
        for (OrePatch s : PatchGenerator.startingPatches(sx, sz)) {
            BlockPos pos = OreFields.surfaceField(level, s.centerX(), s.centerZ());
            h.assertTrue(pos != null && OreFields.typeOf(level.getBlockState(pos)) == s.type(), "starting field missing: " + s);
        }
        h.succeed();
    }

    /**
     * The map hover answer: the type and amount of the field block on a column's surface (also under
     * a machine block standing on it), "none" for a column without one, and "not loaded" for a chunk
     * that is not loaded, which the lookup does not load.
     */
    @GameTest(template = "empty16")
    public static void mapHoverLookup(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos field = h.absolutePos(new BlockPos(3, 0, 3)), covered = h.absolutePos(new BlockPos(5, 0, 3));
        OreFields.place(level, field, OreType.COPPER, 777);
        OreFields.place(level, covered, OreType.COAL, 55);
        h.setBlock(new BlockPos(5, 1, 3), Blocks.IRON_BLOCK);
        OreFieldPayloads.Info a = OreFieldPayloads.lookup(level, field.getX(), field.getZ());
        h.assertTrue(a.oreType() == OreType.COPPER && a.remaining() == 777, "field: " + a);
        OreFieldPayloads.Info b = OreFieldPayloads.lookup(level, covered.getX(), covered.getZ());
        h.assertTrue(b.oreType() == OreType.COAL && b.remaining() == 55, "covered field: " + b);
        BlockPos plain = h.absolutePos(new BlockPos(8, 0, 8));
        OreFieldPayloads.Info c = OreFieldPayloads.lookup(level, plain.getX(), plain.getZ());
        h.assertTrue(c.ore() == OreFieldPayloads.NONE && c.oreType() == null, "plain: " + c);
        int far = 20_000_000;
        OreFieldPayloads.Info d = OreFieldPayloads.lookup(level, far, far);
        h.assertTrue(d.ore() == OreFieldPayloads.NOT_LOADED, "far: " + d);
        h.assertTrue(level.getChunkSource().getChunkNow(far >> 4, far >> 4) == null, "the lookup loaded a chunk");
        h.succeed();
    }

    /** The starting fields were stamped once, on server start, and that is remembered. */
    @GameTest(template = "empty16")
    public static void startingFieldsAreRemembered(GameTestHelper h) {
        ServerLevel overworld = h.getLevel().getServer().getLevel(Level.OVERWORLD);
        StartingFields data = StartingFields.get(overworld);
        h.assertTrue(data.stamped(), "starting fields not stamped");
        h.assertTrue(PatchGenerator.startingPatches(data.spawnX(), data.spawnZ()).size() == 4, "starting patches");
        h.succeed();
    }

    // --- patch totals ------------------------------------------------------------------------------

    /** Stamps a recorded test patch ({@code disk}, at height y) with {@code amount} per cell; returns its key. */
    static long testPatch(ServerLevel level, OrePatch disk, int y, long amount) {
        long key = OrePatchLedger.get(level).register(PatchKind.DEMO, disk, 0, 0);
        for (PatchShape.Run r : PatchShape.of(disk).runs()) {
            for (int x = r.x0(); x <= r.x1(); x++) OreFields.placeOwned(level, new BlockPos(x, y, r.z()), disk.type(), amount, key);
        }
        return key;
    }

    /**
     * Mining a patch block by hand takes one ore off the whole patch's total, a block broken in
     * creative takes all it had; the hover answer for any of its blocks names the patch and its total.
     */
    @GameTest(template = "empty16")
    public static void patchTotalDropsWhenMinedByHand(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos center = h.absolutePos(new BlockPos(4, 1, 4));
        OrePatch disk = new OrePatch(OreType.IRON, center.getX(), center.getZ(), 1); // 5 cells
        long key = testPatch(level, disk, center.getY(), 100);
        OrePatchLedger ledger = OrePatchLedger.get(level);
        h.assertTrue(ledger.total(key) == 500, "stamped total " + ledger.total(key));
        ServerPlayer player = miner(h, new ItemStack(Items.IRON_PICKAXE));
        h.assertTrue(player.gameMode.destroyBlock(center), "break refused");
        h.assertTrue(ledger.total(key) == 499, "after one ore " + ledger.total(key));
        OreFieldPayloads.Info info = OreFieldPayloads.lookup(level, center.getX() + 1, center.getZ());
        h.assertTrue(info.patch() == key && info.total() == 499 && info.radius() == 1 && info.centerX() == center.getX(), "hover: " + info);
        OreFields.exhaust(level, center.east());
        h.assertTrue(ledger.total(key) == 399, "after removing a block " + ledger.total(key));
        ledger.remove(key);
        h.succeed();
    }

    /** The ledger survives a save and reload: remainders, stamped chunks (so pending formula amounts) and the recorded order. */
    @GameTest(template = "empty16")
    public static void patchLedgerSurvivesSaveAndLoad(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        PatchGenerator gen = new PatchGenerator(level.getSeed(), 0, 0);
        OrePatch generated = null;
        for (int rx = -3000; generated == null; rx--) generated = gen.inRegion(rx, 3000);
        int cx = generated.centerX() >> 4, cz = generated.centerZ() >> 4;
        OrePatchLedger.chunkStamped(level, generated, cx, cz, 123);
        OrePatch demo = new OrePatch(OreType.COAL, -9_000_000, 9_000_000, 3);
        OrePatchLedger ledger = OrePatchLedger.get(level);
        long demoKey = ledger.register(PatchKind.DEMO, demo, 0, 0);
        ledger.add(demoKey, 4242);
        long genKey = PatchLedger.key(PatchKind.GENERATED, generated);
        long genTotal = ledger.total(genKey);
        h.assertTrue(genTotal == 123 + PatchLedger.pending(generated, 1L << PatchLedger.chunkBit(generated, cx, cz), 0, 0),
                "generated total " + genTotal);
        h.assertTrue(genTotal < 123 + PatchLedger.formulaTotal(generated, 0, 0), "the stamped chunk still counts its formula");
        OrePatchLedger loaded = OrePatchLedger.reload(ledger, level.registryAccess());
        h.assertTrue(loaded.total(genKey) == genTotal, "generated after load " + loaded.total(genKey) + " vs " + genTotal);
        h.assertTrue(loaded.total(demoKey) == 4242, "demo after load " + loaded.total(demoKey));
        h.assertTrue(loaded.ledger().recorded().equals(ledger.ledger().recorded()), "recorded order");
        h.assertTrue(loaded.ledger().size() == ledger.ledger().size(), "entries " + loaded.ledger().size() + " vs " + ledger.ledger().size());
        ledger.remove(demoKey);
        ledger.remove(genKey);
        h.succeed();
    }

    /**
     * A generated patch: before its chunks generate it has no entry (the hover would show its formula
     * total); once stamped, a block at its very edge belongs to it, the total is every stamped amount,
     * the next column out is no field, and mining the edge block lowers the total.
     */
    @GameTest(template = "empty16")
    public static void lookupFindsThePatchAtItsEdge(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        PatchGenerator gen = new PatchGenerator(level.getSeed(), 0, 0);
        OrePatchLedger ledger = OrePatchLedger.get(level);
        // The test world persists between runs, so take the first patch an earlier run hasn't stamped yet.
        OrePatch patch = null;
        long key = 0;
        for (int rx = 3000; patch == null; rx++) {
            OrePatch p = gen.inRegion(rx, 2990);
            if (p == null) continue;
            key = PatchLedger.key(PatchKind.GENERATED, p);
            if (ledger.total(key) == -1) patch = p;
        }
        runFeature(level, patch);
        int r = patch.radius();
        long planned = 0;
        for (FieldStamp.Cell c : FieldStamp.plan(List.of(patch), patch.centerX() - r, patch.centerZ() - r, patch.centerX() + r,
                patch.centerZ() + r, 0, 0)) {
            planned += c.amount();
        }
        h.assertTrue(planned == PatchLedger.formulaTotal(patch, 0, 0), "formula");
        int ex = patch.centerX() + r, ez = patch.centerZ();
        PatchLookup.Ref ref = OrePatchLedger.get(level).lookup(level, ex, ez, patch.type());
        h.assertTrue(ref != null && ref.patch().equals(patch), "lookup at the edge: " + ref);
        OreFieldPayloads.Info info = OreFieldPayloads.lookup(level, ex, ez);
        h.assertTrue(info.oreType() == patch.type() && info.patch() == key && info.centerX() == patch.centerX()
                && info.centerZ() == patch.centerZ() && info.radius() == r, "edge: " + info);
        h.assertTrue(info.total() == planned, "total " + info.total() + " vs " + planned);
        h.assertTrue(OreFieldPayloads.lookup(level, ex + 1, ez).ore() == OreFieldPayloads.NONE, "outside the edge");
        BlockPos edge = OreFields.surfaceField(level, ex, ez);
        OreFields.mineOne(level, edge);
        h.assertTrue(ledger.total(key) == planned - 1, "after mining " + ledger.total(key));
        h.succeed();
    }
}
