package dev.minetorio.content.registry;

import com.mojang.serialization.MapCodec;
import dev.minetorio.Minetorio;
import dev.minetorio.content.world.CliffRockFeature;
import dev.minetorio.content.world.CrashSiteChestBlock;
import dev.minetorio.content.world.CrashSiteChestBlockEntity;
import dev.minetorio.content.world.NaturalObjectBlock;
import dev.minetorio.content.world.NaturalObjectBlock.Group;
import dev.minetorio.content.world.NauvisDensity;
import dev.minetorio.content.world.NauvisRockFeature;
import dev.minetorio.content.world.NauvisSpawn;
import dev.minetorio.content.world.NauvisTreeFeature;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Minetorio world ("Nauvis"): ground tiles, cliff rock, trees, rocks, the crash site,
 * and the world generation types (density functions, features). Own registers, so world content
 * stays apart from the machines; ids are all in the {@code minetorio} namespace.
 */
public final class ModWorld {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Minetorio.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Minetorio.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Minetorio.MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Minetorio.MODID);
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Minetorio.MODID);

    /** Every Minetorio ground tile block (grass, dirt, sand, red desert): what trees and rocks stand on. */
    public static final TagKey<Block> NAUVIS_GROUND = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "nauvis_ground"));

    // --- ground tiles (Factorio's Nauvis tiles) ------------------------------------------------------
    public static final DeferredBlock<Block> GRASS_1 = ground("grass_1", MapColor.COLOR_GREEN, SoundType.GRASS);
    public static final DeferredBlock<Block> GRASS_2 = ground("grass_2", MapColor.TERRACOTTA_LIGHT_GREEN, SoundType.GRASS);
    public static final DeferredBlock<Block> GRASS_3 = ground("grass_3", MapColor.TERRACOTTA_GREEN, SoundType.GRASS);
    public static final DeferredBlock<Block> GRASS_4 = ground("grass_4", MapColor.TERRACOTTA_YELLOW, SoundType.GRASS);
    public static final DeferredBlock<Block> DRY_DIRT = ground("dry_dirt", MapColor.WOOD, SoundType.GRAVEL);
    public static final DeferredBlock<Block> DIRT_1 = ground("dirt_1", MapColor.DIRT, SoundType.GRAVEL);
    public static final DeferredBlock<Block> DIRT_2 = ground("dirt_2", MapColor.TERRACOTTA_BROWN, SoundType.GRAVEL);
    public static final DeferredBlock<Block> SAND_1 = ground("sand_1", MapColor.SAND, SoundType.SAND);
    public static final DeferredBlock<Block> SAND_2 = ground("sand_2", MapColor.TERRACOTTA_WHITE, SoundType.SAND);
    public static final DeferredBlock<Block> RED_DESERT_0 = ground("red_desert_0", MapColor.TERRACOTTA_ORANGE, SoundType.SAND);
    public static final DeferredBlock<Block> RED_DESERT_1 = ground("red_desert_1", MapColor.TERRACOTTA_RED, SoundType.SAND);
    public static final List<DeferredBlock<Block>> GROUND = List.of(GRASS_1, GRASS_2, GRASS_3, GRASS_4, DRY_DIRT, DIRT_1, DIRT_2,
            SAND_1, SAND_2, RED_DESERT_0, RED_DESERT_1);

    /** Cliff walls: no pickaxe breaks them (destroy time −1, like bedrock); cliff explosives will. */
    public static final DeferredBlock<Block> CLIFF_ROCK = BLOCKS.registerSimpleBlock("cliff_rock", BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_BROWN).strength(-1.0f, 3_600_000f).sound(SoundType.STONE).noLootTable()
            .pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false));

    // --- trees (mined whole: 4 wood) -----------------------------------------------------------------
    public static final DeferredBlock<NaturalObjectBlock> TREE_TRUNK = treePart("tree_trunk", MapColor.TERRACOTTA_BROWN,
            Block.box(5, 0, 5, 11, 16, 11), true, false);
    public static final DeferredBlock<NaturalObjectBlock> DRY_TREE_TRUNK = treePart("dry_tree_trunk", MapColor.TERRACOTTA_GRAY,
            Block.box(5.5, 0, 5.5, 10.5, 16, 10.5), true, false);
    public static final DeferredBlock<NaturalObjectBlock> TREE_LEAVES_GREEN = treePart("tree_leaves_green", MapColor.PLANT,
            Shapes.block(), true, true);
    public static final DeferredBlock<NaturalObjectBlock> TREE_LEAVES_OLIVE = treePart("tree_leaves_olive", MapColor.COLOR_GREEN,
            Shapes.block(), true, true);
    public static final DeferredBlock<NaturalObjectBlock> TREE_LEAVES_RED = treePart("tree_leaves_red", MapColor.TERRACOTTA_RED,
            Shapes.block(), true, true);
    public static final DeferredBlock<NaturalObjectBlock> DRY_TREE_BRANCHES = treePart("dry_tree_branches", MapColor.TERRACOTTA_GRAY,
            Block.box(2, 0, 2, 14, 14, 14), false, true);

    // --- rocks (Factorio: big rock 20 stone, sand rock 19–25 stone, huge rock 24–50 stone and coal) -
    public static final DeferredBlock<NaturalObjectBlock> BIG_ROCK = rock("big_rock", MapColor.STONE, Block.box(1, 0, 1, 15, 12, 15));
    public static final DeferredBlock<NaturalObjectBlock> SAND_ROCK = rock("sand_rock", MapColor.SAND, Block.box(1, 0, 1, 15, 11, 15));
    public static final DeferredBlock<NaturalObjectBlock> HUGE_ROCK = rock("huge_rock", MapColor.TERRACOTTA_LIGHT_GRAY, Shapes.block());

    // --- the crash site -------------------------------------------------------------------------------
    public static final DeferredBlock<Block> SHIP_WRECK_HULL = BLOCKS.registerSimpleBlock("ship_wreck_hull", BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(3.0f, 6f).sound(SoundType.METAL).noLootTable().pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<Block> SHIP_WRECK_DEBRIS = BLOCKS.registerBlock("ship_wreck_debris",
            p -> new NaturalObjectBlock(Group.ROCK, Block.box(1, 0, 2, 15, 6, 14), true, p),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f, 6f).sound(SoundType.METAL).noLootTable()
                    .noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<CrashSiteChestBlock> CRASH_SITE_CHEST = BLOCKS.registerBlock("crash_site_chest",
            CrashSiteChestBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f, 6f).sound(SoundType.METAL)
                    .noLootTable().noOcclusion().pushReaction(PushReaction.BLOCK));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrashSiteChestBlockEntity>> CRASH_SITE_CHEST_BE =
            BLOCK_ENTITIES.register("crash_site_chest", () -> BlockEntityType.Builder
                    .of(CrashSiteChestBlockEntity::new, CRASH_SITE_CHEST.get()).build(null));

    // --- items --------------------------------------------------------------------------------------
    /**
     * Factorio's wood (trees yield 4). An alias for the shared resource item, never a second registration.
     */
    public static final DeferredItem<Item> WOOD = BaseMaterials.WOOD;
    static {
        for (DeferredBlock<Block> g : GROUND) ITEMS.registerSimpleBlockItem(g);
    }

    /** Paths of every item in {@link #ITEMS} (checked by LangCompletenessTest). */
    public static final List<String> ITEM_IDS = List.of("grass_1", "grass_2", "grass_3", "grass_4", "dry_dirt", "dirt_1", "dirt_2",
            "sand_1", "sand_2", "red_desert_0", "red_desert_1");

    // --- world generation ---------------------------------------------------------------------------
    public static final Supplier<CliffRockFeature> CLIFF_ROCK_FEATURE = FEATURES.register("cliff_rock", CliffRockFeature::new);
    public static final Supplier<NauvisTreeFeature> TREE_FEATURE = FEATURES.register("nauvis_tree", NauvisTreeFeature::new);
    public static final Supplier<NauvisRockFeature> ROCK_FEATURE = FEATURES.register("nauvis_rock", NauvisRockFeature::new);

    static {
        DENSITY_FUNCTION_TYPES.register("nauvis_surface", () -> NauvisDensity.Surface.DATA_CODEC);
        DENSITY_FUNCTION_TYPES.register("nauvis_climate", () -> NauvisDensity.Climate.DATA_CODEC);
    }

    private ModWorld() {}

    private static DeferredBlock<Block> ground(String id, MapColor color, SoundType sound) {
        return BLOCKS.registerSimpleBlock(id, BlockBehaviour.Properties.of().mapColor(color).strength(0.6f).sound(sound));
    }

    private static DeferredBlock<NaturalObjectBlock> treePart(String id, MapColor color, net.minecraft.world.phys.shapes.VoxelShape shape,
                                                              boolean collides, boolean seeThrough) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(color).strength(0.8f).sound(seeThrough ? SoundType.GRASS : SoundType.WOOD)
                .pushReaction(PushReaction.DESTROY).noOcclusion().isSuffocating((s, l, q) -> false).isViewBlocking((s, l, q) -> false)
                .isValidSpawn((s, l, q, e) -> false);
        return BLOCKS.registerBlock(id, q -> new NaturalObjectBlock(Group.TREE, shape, collides, q), p);
    }

    private static DeferredBlock<NaturalObjectBlock> rock(String id, MapColor color, net.minecraft.world.phys.shapes.VoxelShape shape) {
        return BLOCKS.registerBlock(id, q -> new NaturalObjectBlock(Group.ROCK, shape, true, q), BlockBehaviour.Properties.of()
                .mapColor(color).strength(2.0f, 6f).sound(SoundType.STONE).pushReaction(PushReaction.BLOCK).noOcclusion()
                .isValidSpawn((s, l, q, e) -> false));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        FEATURES.register(modBus);
        DENSITY_FUNCTION_TYPES.register(modBus);
        NauvisSpawn.register();
        NaturalObjectBlock.registerStatistics();
    }
}
