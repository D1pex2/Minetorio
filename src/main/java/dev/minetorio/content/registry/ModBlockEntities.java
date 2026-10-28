package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import dev.minetorio.content.belt.BeltBlockEntity;
import dev.minetorio.content.belt.SplitterBlockEntity;
import dev.minetorio.content.belt.UndergroundBeltBlockEntity;
import dev.minetorio.content.inserter.InserterBlockEntity;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.machine.BurnerDrillBlockEntity;
import dev.minetorio.content.machine.ElectricDrillBlockEntity;
import dev.minetorio.content.machine.StoneFurnaceBlockEntity;
import dev.minetorio.content.map.RadarBlockEntity;
import dev.minetorio.content.multiblock.MultiblockPartBlockEntity;
import dev.minetorio.content.power.BoilerBlockEntity;
import dev.minetorio.content.power.ElectricPoleBlockEntity;
import dev.minetorio.content.power.OffshorePumpBlockEntity;
import dev.minetorio.content.power.PipeBlockEntity;
import dev.minetorio.content.power.SteamEngineBlockEntity;
import dev.minetorio.content.research.LabBlockEntity;
import dev.minetorio.content.robot.GhostBlockEntity;
import dev.minetorio.content.robot.RoboportBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Minetorio.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockPartBlockEntity>> MULTIBLOCK_PART =
            BLOCK_ENTITIES.register("multiblock_part", () -> BlockEntityType.Builder
                    .of(MultiblockPartBlockEntity::new, ModBlocks.MULTIBLOCK_PART.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StoneFurnaceBlockEntity>> STONE_FURNACE =
            BLOCK_ENTITIES.register("stone_furnace", () -> BlockEntityType.Builder
                    .of(StoneFurnaceBlockEntity::new, ModBlocks.STONE_FURNACE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblerBlockEntity>> ASSEMBLING_MACHINE =
            BLOCK_ENTITIES.register("assembling_machine", () -> BlockEntityType.Builder
                    .of(AssemblerBlockEntity::new, ModBlocks.ASSEMBLING_MACHINE_1.get(), ModBlocks.ASSEMBLING_MACHINE_2.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LabBlockEntity>> LAB =
            BLOCK_ENTITIES.register("lab", () -> BlockEntityType.Builder
                    .of(LabBlockEntity::new, ModBlocks.LAB.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadarBlockEntity>> RADAR =
            BLOCK_ENTITIES.register("radar", () -> BlockEntityType.Builder
                    .of(RadarBlockEntity::new, ModBlocks.RADAR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeltBlockEntity>> TRANSPORT_BELT =
            BLOCK_ENTITIES.register("transport_belt", () -> BlockEntityType.Builder
                    .of(BeltBlockEntity::new, ModBlocks.TRANSPORT_BELT.get(), ModBlocks.FAST_TRANSPORT_BELT.get(),
                            ModBlocks.EXPRESS_TRANSPORT_BELT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UndergroundBeltBlockEntity>> UNDERGROUND_BELT =
            BLOCK_ENTITIES.register("underground_belt", () -> BlockEntityType.Builder
                    .of(UndergroundBeltBlockEntity::new, ModBlocks.UNDERGROUND_BELT.get(), ModBlocks.FAST_UNDERGROUND_BELT.get(),
                            ModBlocks.EXPRESS_UNDERGROUND_BELT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SplitterBlockEntity>> SPLITTER =
            BLOCK_ENTITIES.register("splitter", () -> BlockEntityType.Builder
                    .of(SplitterBlockEntity::new, ModBlocks.SPLITTER.get(), ModBlocks.FAST_SPLITTER.get(),
                            ModBlocks.EXPRESS_SPLITTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InserterBlockEntity>> INSERTER =
            BLOCK_ENTITIES.register("inserter", () -> BlockEntityType.Builder
                    .of(InserterBlockEntity::new, ModBlocks.BURNER_INSERTER.get(), ModBlocks.INSERTER.get(),
                            ModBlocks.LONG_HANDED_INSERTER.get(), ModBlocks.FAST_INSERTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BurnerDrillBlockEntity>> BURNER_MINING_DRILL =
            BLOCK_ENTITIES.register("burner_mining_drill", () -> BlockEntityType.Builder
                    .of(BurnerDrillBlockEntity::new, ModBlocks.BURNER_MINING_DRILL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricDrillBlockEntity>> ELECTRIC_MINING_DRILL =
            BLOCK_ENTITIES.register("electric_mining_drill", () -> BlockEntityType.Builder
                    .of(ElectricDrillBlockEntity::new, ModBlocks.ELECTRIC_MINING_DRILL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OffshorePumpBlockEntity>> OFFSHORE_PUMP =
            BLOCK_ENTITIES.register("offshore_pump", () -> BlockEntityType.Builder
                    .of(OffshorePumpBlockEntity::new, ModBlocks.OFFSHORE_PUMP.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PipeBlockEntity>> PIPE =
            BLOCK_ENTITIES.register("pipe", () -> BlockEntityType.Builder
                    .of(PipeBlockEntity::new, ModBlocks.PIPE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoilerBlockEntity>> BOILER =
            BLOCK_ENTITIES.register("boiler", () -> BlockEntityType.Builder
                    .of(BoilerBlockEntity::new, ModBlocks.BOILER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamEngineBlockEntity>> STEAM_ENGINE =
            BLOCK_ENTITIES.register("steam_engine", () -> BlockEntityType.Builder
                    .of(SteamEngineBlockEntity::new, ModBlocks.STEAM_ENGINE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricPoleBlockEntity>> SMALL_ELECTRIC_POLE =
            BLOCK_ENTITIES.register("small_electric_pole", () -> BlockEntityType.Builder
                    .of(ElectricPoleBlockEntity::new, ModBlocks.SMALL_ELECTRIC_POLE.get()).build(null));

    private ModBlockEntities() {}

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RoboportBlockEntity>> ROBOPORT =
            BLOCK_ENTITIES.register("roboport", () -> BlockEntityType.Builder
                    .of(RoboportBlockEntity::new, ModBlocks.ROBOPORT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GhostBlockEntity>> GHOST =
            BLOCK_ENTITIES.register("ghost", () -> BlockEntityType.Builder
                    .of(GhostBlockEntity::new, ModBlocks.GHOST.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
