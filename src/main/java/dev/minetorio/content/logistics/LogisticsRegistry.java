package dev.minetorio.content.logistics;

import dev.minetorio.Minetorio;
import dev.minetorio.core.logistics.LogisticMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Everything the logistic network (stage 4) registers, kept apart from the shared registries: the five
 * logistic chests, the logistic robot (item and entity), the personal roboport item, the chest and
 * personal logistics menus, the personal logistics player attachment and the chests' item handler.
 */
public final class LogisticsRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Minetorio.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Minetorio.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Minetorio.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Minetorio.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Minetorio.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Minetorio.MODID);

    /** The chest blocks by mode, in {@link LogisticMode} order (Factorio's colours as map colours). */
    public static final Map<LogisticMode, DeferredBlock<LogisticChestBlock>> CHESTS = new EnumMap<>(LogisticMode.class);
    public static final Map<LogisticMode, DeferredItem<BlockItem>> CHEST_ITEMS = new EnumMap<>(LogisticMode.class);

    static {
        for (LogisticMode mode : LogisticMode.values()) {
            MapColor color = switch (mode) {
                case PASSIVE_PROVIDER -> MapColor.COLOR_RED;
                case ACTIVE_PROVIDER -> MapColor.COLOR_PURPLE;
                case STORAGE -> MapColor.COLOR_YELLOW;
                case REQUESTER -> MapColor.COLOR_LIGHT_BLUE;
                case BUFFER -> MapColor.COLOR_GREEN;
            };
            DeferredBlock<LogisticChestBlock> block = BLOCKS.registerBlock(mode.id(), p -> new LogisticChestBlock(mode, p),
                    BlockBehaviour.Properties.of().mapColor(color).strength(2.0f).sound(SoundType.METAL).noOcclusion());
            CHESTS.put(mode, block);
            CHEST_ITEMS.put(mode, ITEMS.registerSimpleBlockItem(block, new Item.Properties().stacksTo(50)));
        }
    }

    public static final DeferredItem<Item> LOGISTIC_ROBOT = ITEMS.registerSimpleItem("logistic_robot", new Item.Properties().stacksTo(50));
    /** Factorio's personal roboport equipment, carried in the inventory instead of armor ({@link PersonalRoboport}). */
    public static final DeferredItem<Item> PERSONAL_ROBOPORT = ITEMS.registerItem("personal_roboport", p -> new Item(p.stacksTo(20)));

    /** Paths of every item registered here (checked against {@link #ITEMS} and the lang files by a unit test). */
    public static final List<String> IDS = ids();

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LogisticChestBlockEntity>> CHEST =
            BLOCK_ENTITIES.register("logistic_chest", () -> BlockEntityType.Builder
                    .of(LogisticChestBlockEntity::new, CHESTS.values().stream().map(Supplier::get).toArray(LogisticChestBlock[]::new))
                    .build(null));

    /** Like the construction robot: half a block; clients fly it themselves, position packets only correct drift. */
    public static final DeferredHolder<EntityType<?>, EntityType<LogisticRobotEntity>> LOGISTIC_ROBOT_ENTITY =
            ENTITIES.register("logistic_robot", () -> EntityType.Builder
                    .<LogisticRobotEntity>of(LogisticRobotEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .fireImmune()
                    .build("logistic_robot"));

    public static final DeferredHolder<MenuType<?>, MenuType<LogisticChestMenu>> CHEST_MENU =
            MENUS.register("logistic_chest", () -> IMenuTypeExtension.create(LogisticChestMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<PersonalLogisticsMenu>> PERSONAL_MENU =
            MENUS.register("personal_logistics", () -> IMenuTypeExtension.create(PersonalLogisticsMenu::client));

    /** A player's personal logistic requests; kept on death (Factorio keeps them). */
    public static final Supplier<AttachmentType<PersonalLogistics>> PERSONAL = ATTACHMENTS.register("personal_logistics",
            () -> AttachmentType.serializable(PersonalLogistics::new).copyOnDeath().build());

    private LogisticsRegistry() {}

    private static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (LogisticMode m : LogisticMode.values()) out.add(m.id());
        out.add("logistic_robot");
        out.add("personal_roboport");
        return List.copyOf(out);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        MENUS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(LogisticsRegistry::onCapabilities);
        LogisticsPayloads.register(modBus);
        LogisticsManager.register();
        PersonalRoboport.register();
    }

    private static void onCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CHEST.get(), (be, side) -> be.itemHandler());
    }
}
