package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.Unbreakable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The world's actual resources, independent of vanilla survival materials. */
public final class BaseMaterials {
    private BaseMaterials() {}
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Minetorio.MODID);
    public static final DeferredItem<Item> WOOD = ITEMS.registerSimpleItem("wood", new Item.Properties().stacksTo(100));
    public static final DeferredItem<Item> STONE = ITEMS.registerSimpleItem("stone", new Item.Properties().stacksTo(50));
    public static final DeferredItem<Item> COAL = ITEMS.registerSimpleItem("coal", new Item.Properties().stacksTo(50));
    public static final DeferredItem<PickaxeItem> ENGINEER_TOOL = ITEMS.registerItem("engineer_tool", p -> new PickaxeItem(Tiers.STONE,
            p.attributes(PickaxeItem.createAttributes(Tiers.STONE, 1, -2.8f)).component(DataComponents.UNBREAKABLE, new Unbreakable(true))));
    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
