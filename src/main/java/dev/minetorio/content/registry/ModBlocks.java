package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import dev.minetorio.content.belt.BeltBlock;
import dev.minetorio.content.belt.SplitterBlock;
import dev.minetorio.content.belt.UndergroundBeltBlock;
import dev.minetorio.content.inserter.InserterBlock;
import dev.minetorio.content.machine.AssemblerBlock;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.machine.BurnerDrillBlock;
import dev.minetorio.content.machine.ElectricDrillBlock;
import dev.minetorio.content.machine.StoneFurnaceBlock;
import dev.minetorio.content.map.RadarBlock;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.content.power.BoilerBlock;
import dev.minetorio.content.power.ElectricPoleBlock;
import dev.minetorio.content.power.OffshorePumpBlock;
import dev.minetorio.content.power.PipeBlock;
import dev.minetorio.content.power.SteamEngineBlock;
import dev.minetorio.content.research.LabBlock;
import dev.minetorio.core.belt.BeltTier;
import dev.minetorio.core.inserter.InserterType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Minetorio.MODID);

    /**
     * Shared invisible filler of every multiblock; mining speed is delegated to the controller.
     * Its shape depends on the controller (block entity), so it must not be cached per state.
     */
    public static final DeferredBlock<MultiblockPartBlock> MULTIBLOCK_PART = BLOCKS.registerBlock("multiblock_part",
            MultiblockPartBlock::new, machine(MapColor.NONE).noLootTable().dynamicShape());

    public static final DeferredBlock<StoneFurnaceBlock> STONE_FURNACE = BLOCKS.registerBlock("stone_furnace",
            StoneFurnaceBlock::new, machine(MapColor.STONE)
                    .lightLevel(s -> s.getValue(StoneFurnaceBlock.LIT) ? StoneFurnaceBlock.LIGHT : 0));

    // Assembling machines
    public static final DeferredBlock<AssemblerBlock> ASSEMBLING_MACHINE_1 = BLOCKS.registerBlock("assembling_machine_1",
            p -> new AssemblerBlock(AssemblerBlockEntity.SPEED_1, AssemblerBlockEntity.POWER_1, p), machine(MapColor.METAL));
    public static final DeferredBlock<AssemblerBlock> ASSEMBLING_MACHINE_2 = BLOCKS.registerBlock("assembling_machine_2",
            p -> new AssemblerBlock(AssemblerBlockEntity.SPEED_2, AssemblerBlockEntity.POWER_2, p), machine(MapColor.METAL));

    // Research
    public static final DeferredBlock<LabBlock> LAB = BLOCKS.registerBlock("lab", LabBlock::new, machine(MapColor.METAL));

    // Map
    public static final DeferredBlock<RadarBlock> RADAR = BLOCKS.registerBlock("radar", RadarBlock::new, machine(MapColor.METAL));

    // Mining drills
    public static final DeferredBlock<BurnerDrillBlock> BURNER_MINING_DRILL = BLOCKS.registerBlock("burner_mining_drill",
            BurnerDrillBlock::new, machine(MapColor.METAL));
    public static final DeferredBlock<ElectricDrillBlock> ELECTRIC_MINING_DRILL = BLOCKS.registerBlock("electric_mining_drill",
            ElectricDrillBlock::new, machine(MapColor.METAL));

    // Belts
    public static final DeferredBlock<BeltBlock> TRANSPORT_BELT = BLOCKS.registerBlock("transport_belt",
            p -> new BeltBlock(BeltTier.YELLOW, p), belt(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<BeltBlock> FAST_TRANSPORT_BELT = BLOCKS.registerBlock("fast_transport_belt",
            p -> new BeltBlock(BeltTier.RED, p), belt(MapColor.COLOR_RED));
    public static final DeferredBlock<BeltBlock> EXPRESS_TRANSPORT_BELT = BLOCKS.registerBlock("express_transport_belt",
            p -> new BeltBlock(BeltTier.BLUE, p), belt(MapColor.COLOR_BLUE));
    public static final DeferredBlock<UndergroundBeltBlock> UNDERGROUND_BELT = BLOCKS.registerBlock("underground_belt",
            p -> new UndergroundBeltBlock(BeltTier.YELLOW, p), belt(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<UndergroundBeltBlock> FAST_UNDERGROUND_BELT = BLOCKS.registerBlock("fast_underground_belt",
            p -> new UndergroundBeltBlock(BeltTier.RED, p), belt(MapColor.COLOR_RED));
    public static final DeferredBlock<UndergroundBeltBlock> EXPRESS_UNDERGROUND_BELT = BLOCKS.registerBlock("express_underground_belt",
            p -> new UndergroundBeltBlock(BeltTier.BLUE, p), belt(MapColor.COLOR_BLUE));
    public static final DeferredBlock<SplitterBlock> SPLITTER = BLOCKS.registerBlock("splitter",
            p -> new SplitterBlock(BeltTier.YELLOW, p), belt(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<SplitterBlock> FAST_SPLITTER = BLOCKS.registerBlock("fast_splitter",
            p -> new SplitterBlock(BeltTier.RED, p), belt(MapColor.COLOR_RED));
    public static final DeferredBlock<SplitterBlock> EXPRESS_SPLITTER = BLOCKS.registerBlock("express_splitter",
            p -> new SplitterBlock(BeltTier.BLUE, p), belt(MapColor.COLOR_BLUE));

    // Inserters
    public static final DeferredBlock<InserterBlock> BURNER_INSERTER = BLOCKS.registerBlock("burner_inserter",
            p -> new InserterBlock(InserterType.BURNER, p), belt(MapColor.COLOR_BROWN));
    public static final DeferredBlock<InserterBlock> INSERTER = BLOCKS.registerBlock("inserter",
            p -> new InserterBlock(InserterType.BASIC, p), belt(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<InserterBlock> LONG_HANDED_INSERTER = BLOCKS.registerBlock("long_handed_inserter",
            p -> new InserterBlock(InserterType.LONG, p), belt(MapColor.COLOR_RED));
    public static final DeferredBlock<InserterBlock> FAST_INSERTER = BLOCKS.registerBlock("fast_inserter",
            p -> new InserterBlock(InserterType.FAST, p), belt(MapColor.COLOR_BLUE));

    // Steam power
    public static final DeferredBlock<OffshorePumpBlock> OFFSHORE_PUMP = BLOCKS.registerBlock("offshore_pump",
            OffshorePumpBlock::new, machine(MapColor.METAL));
    public static final DeferredBlock<PipeBlock> PIPE = BLOCKS.registerBlock("pipe",
            PipeBlock::new, belt(MapColor.METAL));
    public static final DeferredBlock<BoilerBlock> BOILER = BLOCKS.registerBlock("boiler",
            BoilerBlock::new, machine(MapColor.METAL));
    public static final DeferredBlock<SteamEngineBlock> STEAM_ENGINE = BLOCKS.registerBlock("steam_engine",
            SteamEngineBlock::new, machine(MapColor.METAL));
    public static final DeferredBlock<ElectricPoleBlock> SMALL_ELECTRIC_POLE = BLOCKS.registerBlock("small_electric_pole",
            ElectricPoleBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0f)
                    .sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK));

    private ModBlocks() {}

    /** Properties shared by belts, underground belts, splitters and inserters: low, metal, quick to mine. */
    private static BlockBehaviour.Properties belt(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(1.0f)
                .sound(SoundType.METAL)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK);
    }

    /** Properties shared by machine controllers and parts: stone-like, see-through to light, immovable. */
    private static BlockBehaviour.Properties machine(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(3.5f)
                .sound(SoundType.STONE)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
