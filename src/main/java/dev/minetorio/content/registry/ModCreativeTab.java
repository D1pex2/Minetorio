package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import dev.minetorio.content.robot.GhostItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Minetorio.MODID);

    /** Lists every item in the minetorio namespace, in registration order. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MINETORIO = TABS.register(Minetorio.MODID,
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + Minetorio.MODID))
                    .icon(() -> new ItemStack(ModItems.STONE_FURNACE.get()))
                    .displayItems((params, output) -> {
                        for (Item item : BuiltInRegistries.ITEM) {
                            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Minetorio.MODID) && !(item instanceof GhostItem)) {
                                output.accept(item);
                            }
                        }
                    })
                    .build());

    private ModCreativeTab() {}

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
