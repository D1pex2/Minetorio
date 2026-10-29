package dev.minetorio.content.rail;

import dev.minetorio.Minetorio;
import dev.minetorio.core.item.ItemStackSizes;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Предметы каталога; типы блоков и подвижного состава добавляются вместе с их реализацией. */
public final class RailRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Minetorio.MODID);
    public static final DeferredItem<Item> RAIL = item("rail");
    public static final DeferredItem<Item> LOCOMOTIVE = item("locomotive");
    public static final DeferredItem<Item> CARGO_WAGON = item("cargo_wagon");
    public static final DeferredItem<Item> FLUID_WAGON = item("fluid_wagon");
    public static final DeferredItem<Item> TRAIN_STOP = item("train_stop");
    public static final DeferredItem<Item> RAIL_SIGNAL = item("rail_signal");
    public static final DeferredItem<Item> RAIL_CHAIN_SIGNAL = item("rail_chain_signal");

    private RailRegistry() {}

    private static DeferredItem<Item> item(String id) {
        return ITEMS.registerSimpleItem(id, new Item.Properties().stacksTo(ItemStackSizes.limit(Minetorio.MODID + ":" + id)));
    }

    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
