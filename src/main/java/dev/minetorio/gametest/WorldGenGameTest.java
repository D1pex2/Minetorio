package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.dropped;

import dev.minetorio.content.player.FishCatching;
import dev.minetorio.content.fish.MinimalFishEntity;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.registry.ModWorld;
import dev.minetorio.content.registry.BaseMaterials;
import dev.minetorio.content.world.CrashSiteChestBlockEntity;
import dev.minetorio.content.world.MinetorioPreset;
import dev.minetorio.content.world.NauvisDensity;
import dev.minetorio.content.world.NauvisSpawn;
import dev.minetorio.content.world.NauvisTreeFeature;
import dev.minetorio.core.world.NauvisBiome;
import dev.minetorio.core.world.NauvisTerrain;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Minetorio world type on the GameTest server (whose own world is flat): the preset and its noise
 * router built from the loaded data agree with the pure terrain; trees, rocks and cliffs as blocks;
 * the crash site; the fish.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class WorldGenGameTest {
    private static final long SEED = 8675309L;

    private static ServerPlayer survivor(GameTestHelper h, ItemStack tool) {
        ServerPlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        p.setGameMode(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, tool);
        return p;
    }

    /**
     * The preset's Overworld uses the nauvis noise settings; its final density is solid exactly up to
     * the pure terrain's surface, and its multi-noise biome source picks the biome {@link NauvisBiome} predicts.
     */
    @GameTest(template = "empty16")
    public static void presetRouterMatchesThePureTerrain(GameTestHelper h) {
        RegistryAccess access = h.getLevel().registryAccess();
        WorldPreset preset = access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(MinetorioPreset.KEY);
        LevelStem stem = preset.createWorldDimensions().dimensions().get(LevelStem.OVERWORLD);
        h.assertTrue(stem != null, "no overworld in the preset");
        h.assertTrue(stem.type().is(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION_TYPE,
                net.minecraft.resources.ResourceLocation.parse("minetorio:nauvis"))), "dimension type");
        h.assertTrue(stem.type().value().minY() == -16 && stem.type().value().height() == 144, "height -16..128");
        RandomState rs = RandomState.create(access.registryOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NauvisDensity.SETTINGS),
                access.lookupOrThrow(Registries.NOISE), SEED);
        NauvisTerrain t = NauvisDensity.terrain(rs.getOrCreateNoise(NauvisDensity.SEED_NOISE));
        NoiseRouter router = rs.router();
        int biomeMismatch = 0;
        for (int i = 0; i < 40; i++) {
            int x = -2000 + i * 97;
            int z = 1500 - i * 83;
            int top = t.surfaceY(x, z);
            DensityFunction fd = router.finalDensity();
            h.assertTrue(fd.compute(new DensityFunction.SinglePointContext(x, top, z)) > 0, "top block not solid at " + x + "," + z);
            h.assertTrue(fd.compute(new DensityFunction.SinglePointContext(x, top + 1, z)) < 0, "above the top solid at " + x + "," + z);
            Holder<Biome> b = stem.generator().getBiomeSource().getNoiseBiome(x >> 2, 16, z >> 2, rs.sampler());
            NauvisBiome expected = NauvisBiome.at(t, (x >> 2) << 2, (z >> 2) << 2);
            if (!b.is(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minetorio", expected.id()))) biomeMismatch++;
        }
        // the multi-noise source quantizes the climate; only exact boundary cases may differ
        h.assertTrue(biomeMismatch <= 2, "biome source disagrees with NauvisBiome at " + biomeMismatch + " of 40 points");
        h.succeed();
    }

    /**
     * Generates real chunks with the preset's generator (noise, biomes, surface rules; no features):
     * the top block is at the pure terrain's height, a Minetorio ground tile on land or water up to
     * y 62 over lakes; solid all the way down (no caves); bedrock floor at y −16.
     */
    @GameTest(template = "empty16", timeoutTicks = 400)
    public static void generatedChunksFollowTheTerrain(GameTestHelper h) {
        // off the server thread: chunk generation's async steps look up structure references through the
        // level, which hops to the server thread (joining them here would deadlock)
        var result = new java.util.concurrent.atomic.AtomicReference<String>();
        Thread worker = new Thread(() -> {
            try {
                checkGeneratedChunks(h);
                result.set("");
            } catch (Throwable e) {
                result.set(String.valueOf(e.getMessage()));
            }
        }, "minetorio-worldgen-test");
        worker.setDaemon(true);
        worker.start();
        h.succeedWhen(() -> {
            String r = result.get();
            if (r == null) throw new net.minecraft.gametest.framework.GameTestAssertException("generating");
            if (!r.isEmpty()) h.fail(r);
        });
    }

    private static void checkGeneratedChunks(GameTestHelper h) {
        RegistryAccess access = h.getLevel().registryAccess();
        WorldPreset preset = access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(MinetorioPreset.KEY);
        LevelStem stem = preset.createWorldDimensions().dimensions().get(LevelStem.OVERWORLD);
        var gen = (net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator) stem.generator();
        RandomState rs = RandomState.create(gen.generatorSettings().value(), access.lookupOrThrow(Registries.NOISE), SEED);
        NauvisTerrain t = NauvisDensity.terrain(rs.getOrCreateNoise(NauvisDensity.SEED_NOISE));
        var biomes = access.registryOrThrow(Registries.BIOME);
        var height = net.minecraft.world.level.LevelHeightAccessor.create(-16, 144);
        var sm = h.getLevel().structureManager();
        int lakes = 0;
        int land = 0;
        for (int[] c : new int[][] {{0, 0}, {40, -25}, {-63, 18}, {90, 90}, {-120, -40}, {13, 77}}) {
            var pos = new net.minecraft.world.level.ChunkPos(c[0], c[1]);
            var chunk = new net.minecraft.world.level.chunk.ProtoChunk(pos, net.minecraft.world.level.chunk.UpgradeData.EMPTY, height,
                    biomes, null);
            var blender = net.minecraft.world.level.levelgen.blending.Blender.empty();
            gen.createBiomes(rs, blender, sm, chunk).join();
            chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.BIOMES);
            gen.fillFromNoise(blender, rs, sm, chunk).join();
            chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.NOISE);
            gen.buildSurface(chunk, new net.minecraft.world.level.levelgen.WorldGenerationContext(gen, height), rs, sm,
                    new net.minecraft.world.level.biome.BiomeManager(chunk, net.minecraft.world.level.biome.BiomeManager.obfuscateSeed(SEED)),
                    biomes, blender);
            for (int dx = 0; dx < 16; dx += 3) {
                for (int dz = 0; dz < 16; dz += 3) {
                    int x = pos.getMinBlockX() + dx;
                    int z = pos.getMinBlockZ() + dz;
                    int top = t.surfaceY(x, z);
                    var at = new BlockPos.MutableBlockPos(x, top, z);
                    var topState = chunk.getBlockState(at);
                    h.assertTrue(!topState.isAir() && topState.getFluidState().isEmpty(), "top not solid at " + at + ": " + topState);
                    var above = chunk.getBlockState(at.setY(top + 1));
                    if (t.isWater(x, z)) {
                        lakes++;
                        h.assertTrue(above.is(Blocks.WATER) && chunk.getBlockState(at.setY(62)).is(Blocks.WATER)
                                && chunk.getBlockState(at.setY(63)).isAir(), "lake water wrong at " + x + "," + z);
                    } else {
                        land++;
                        h.assertTrue(above.isAir(), "land column covered at " + x + "," + z + ": " + above);
                        h.assertTrue(topState.is(ModWorld.NAUVIS_GROUND), "land top is " + topState + " at " + x + "," + z);
                    }
                    for (int y = -15; y < top; y++) {
                        h.assertTrue(!chunk.getBlockState(at.setY(y)).isAir(), "cave/air at " + x + "," + y + "," + z);
                    }
                    h.assertTrue(chunk.getBlockState(at.setY(-16)).is(Blocks.BEDROCK), "no bedrock floor at " + x + "," + z);
                }
            }
        }
        h.assertTrue(land > 0, "no land sampled");
    }

    /** Breaking one leaf block of a tree as a survival player removes the whole tree and yields 4 wood. */
    @GameTest(template = "empty16")
    public static void treeIsMinedWholeForFourWood(GameTestHelper h) {
        for (int x = 3; x <= 11; x++) for (int z = 3; z <= 11; z++) h.setBlock(new BlockPos(x, 0, z), ModWorld.GRASS_1.get());
        BlockPos foot = h.absolutePos(new BlockPos(7, 1, 7));
        var cfg = new NauvisTreeFeature.Config(ModWorld.TREE_TRUNK.get().defaultBlockState(),
                Optional.of(ModWorld.TREE_LEAVES_GREEN.get().defaultBlockState()), Optional.empty(), 4, 4, false, 1f);
        h.assertTrue(NauvisTreeFeature.grow(h.getLevel(), foot, cfg, RandomSource.create(1)), "tree did not grow");
        BlockPos leaf = foot.above(4);
        h.assertTrue(h.getLevel().getBlockState(leaf).is(ModWorld.TREE_LEAVES_GREEN.get()), "no crown top");
        ServerPlayer p = survivor(h, ItemStack.EMPTY);
        h.assertTrue(p.gameMode.destroyBlock(leaf), "break refused");
        for (int y = 0; y < 8; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    var s = h.getLevel().getBlockState(foot.offset(dx, y, dz));
                    h.assertTrue(s.isAir(), "tree part left at " + dx + "," + y + "," + dz + ": " + s);
                }
            }
        }
        h.assertTrue(dropped(h, ModWorld.WOOD.get()) == 4, "wood dropped: " + dropped(h, ModWorld.WOOD.get()));
        h.succeed();
    }

    /** A huge rock of several blocks is mined in one go for stone and coal; a big rock gives 20 stone. */
    @GameTest(template = "empty16")
    public static void rocksYieldStoneAndCoal(GameTestHelper h) {
        BlockPos[] huge = {new BlockPos(3, 1, 3), new BlockPos(4, 1, 3), new BlockPos(3, 2, 3), new BlockPos(3, 1, 4)};
        for (BlockPos b : huge) h.setBlock(b, ModWorld.HUGE_ROCK.get());
        h.setBlock(new BlockPos(11, 1, 11), ModWorld.BIG_ROCK.get());
        ServerPlayer p = survivor(h, new ItemStack(Items.IRON_PICKAXE));
        h.assertTrue(p.gameMode.destroyBlock(h.absolutePos(huge[2])), "break refused");
        for (BlockPos b : huge) h.assertBlockNotPresent(ModWorld.HUGE_ROCK.get(), b);
        int stone = dropped(h, BaseMaterials.STONE.get());
        int coal = dropped(h, BaseMaterials.COAL.get());
        h.assertTrue(stone >= 24 && stone <= 50 && coal >= 24 && coal <= 50, "huge rock gave " + stone + " stone, " + coal + " coal");
        h.assertTrue(p.gameMode.destroyBlock(h.absolutePos(new BlockPos(11, 1, 11))), "big rock refused");
        h.assertTrue(dropped(h, BaseMaterials.STONE.get()) == stone + 20, "big rock stone");
        h.succeed();
    }

    /** Cliff rock does not break by pickaxe (only cliff explosives, later); ground tiles drop themselves. */
    @GameTest(template = "empty16")
    public static void cliffRockIsUnbreakableGroundDropsItself(GameTestHelper h) {
        h.setBlock(new BlockPos(5, 1, 5), ModWorld.CLIFF_ROCK.get());
        h.setBlock(new BlockPos(8, 1, 8), ModWorld.RED_DESERT_0.get());
        ServerPlayer p = survivor(h, new ItemStack(Items.DIAMOND_PICKAXE));
        h.assertTrue(ModWorld.CLIFF_ROCK.get().defaultBlockState().getDestroySpeed(h.getLevel(), h.absolutePos(new BlockPos(5, 1, 5))) < 0,
                "cliff rock must be unbreakable");
        h.assertTrue(p.gameMode.destroyBlock(h.absolutePos(new BlockPos(8, 1, 8))), "ground refused");
        h.assertTrue(dropped(h, ModWorld.RED_DESERT_0.get().asItem()) == 1, "ground tile drop");
        h.assertBlockPresent(ModWorld.CLIFF_ROCK.get(), new BlockPos(5, 1, 5));
        h.succeed();
    }

    /** The crash site lands on the ground with its cargo chest holding Factorio's freeplay starting items. */
    @GameTest(template = "empty16")
    public static void crashSiteHoldsTheStarterKit(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int ax = 20_000 + (int) (level.getGameTime() % 1000) * 64;
        BlockPos chest = NauvisSpawn.placeCrashSite(level, ax, 20_000);
        h.assertTrue(chest != null && level.getBlockEntity(chest) instanceof CrashSiteChestBlockEntity, "no cargo chest");
        CrashSiteChestBlockEntity be = (CrashSiteChestBlockEntity) level.getBlockEntity(chest);
        Player p = survivor(h, ItemStack.EMPTY);
        be.unpackLootTable(p);
        int plates = 0, wood = 0, drills = 0, furnaces = 0;
        for (int i = 0; i < be.getContainerSize(); i++) {
            ItemStack s = be.getItem(i);
            if (s.is(ModItems.IRON_PLATE.get())) plates += s.getCount();
            if (s.is(ModWorld.WOOD.get())) wood += s.getCount();
            if (s.is(ModItems.BURNER_MINING_DRILL.get())) drills += s.getCount();
            if (s.is(ModItems.STONE_FURNACE.get())) furnaces += s.getCount();
        }
        h.assertTrue(plates == 8 && wood == 1 && drills == 1 && furnaces == 1,
                "kit: " + plates + " plates, " + wood + " wood, " + drills + " drills, " + furnaces + " furnaces");
        h.assertTrue(level.getBlockState(chest.below()).isSolid() && !level.getBlockState(chest.below()).is(Blocks.AIR), "chest floats");
        h.succeed();
    }

    /** The world's fish is this mod's {@code minetorio:fish}, the entity fish catching looks for. */
    @GameTest(template = "empty16")
    public static void fishIsTheCatchableFish(GameTestHelper h) {
        h.assertTrue(BuiltInRegistries.ENTITY_TYPE.get(FishCatching.FISH_ID) == MinimalFishEntity.FISH.get(), "minetorio:fish is not the world fish");
        var fish = MinimalFishEntity.FISH.get().create(h.getLevel());
        h.assertTrue(fish != null && FishCatching.isFish(fish), "not catchable");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void touchingDifferentRocksKeepTheirIndividualYields(GameTestHelper h) {
        h.setBlock(new BlockPos(5, 1, 5), ModWorld.BIG_ROCK.get());
        h.setBlock(new BlockPos(6, 1, 5), ModWorld.HUGE_ROCK.get());
        ServerPlayer p = survivor(h, ItemStack.EMPTY);
        h.assertTrue(p.gameMode.destroyBlock(h.absolutePos(new BlockPos(5, 1, 5))), "rock refused");
        h.assertBlockPresent(ModWorld.HUGE_ROCK.get(), new BlockPos(6, 1, 5));
        h.assertTrue(dropped(h, BaseMaterials.STONE.get()) == 20, "big rock yield");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void generatedTreesCannotMergeIntoOneHarvestableObject(GameTestHelper h) {
        BlockPos foot = h.absolutePos(new BlockPos(5, 1, 5));
        var cfg = new NauvisTreeFeature.Config(ModWorld.TREE_TRUNK.get().defaultBlockState(),
                Optional.of(ModWorld.TREE_LEAVES_GREEN.get().defaultBlockState()), Optional.empty(), 4, 4, false, 1f);
        h.assertTrue(NauvisTreeFeature.grow(h.getLevel(), foot, cfg, RandomSource.create(1)), "first tree refused");
        h.assertTrue(!NauvisTreeFeature.grow(h.getLevel(), foot.east(3), cfg, RandomSource.create(2)),
                "overlapping crown accepted: trees would yield only one tree's wood");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void crashHullUsesOriginalGroundForEveryLayer(GameTestHelper h) {
        BlockPos anchor = h.absolutePos(new BlockPos(4, 1, 4));
        for (int x=0;x<16;x++) for(int z=0;z<16;z++) for(int y=1;y<16;y++) h.setBlock(new BlockPos(x,y,z), Blocks.AIR);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(new BlockPos(x, 0, z), ModWorld.GRASS_1.get());
        BlockPos finColumn = h.absolutePos(new BlockPos(8, 0, 5));
        int before = h.getLevel().getChunk(finColumn).getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, finColumn.getX() & 15, finColumn.getZ() & 15);
        h.assertTrue(before == finColumn.getY(), "test column top=" + before + " expected=" + finColumn.getY() + " block=" + h.getLevel().getBlockState(new BlockPos(finColumn.getX(), before, finColumn.getZ())));
        NauvisSpawn.placeCrashSite(h.getLevel(), anchor.getX(), anchor.getZ());
        // The fin is exactly dy=3 over ground, regardless of earlier hull pieces in this column.
        h.assertBlockPresent(ModWorld.SHIP_WRECK_HULL.get(), new BlockPos(8, 4, 5));
        h.assertBlockNotPresent(ModWorld.SHIP_WRECK_HULL.get(), new BlockPos(8, 5, 5));
        h.succeed();
    }
}
