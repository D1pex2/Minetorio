package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import dev.minetorio.content.machine.AssemblerMenu;
import dev.minetorio.content.machine.StoneFurnaceMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Machine GUIs; the server writes the controller position as extra data when opening one. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Minetorio.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<StoneFurnaceMenu>> STONE_FURNACE =
            MENUS.register("stone_furnace", () -> IMenuTypeExtension.create(StoneFurnaceMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<AssemblerMenu>> ASSEMBLING_MACHINE =
            MENUS.register("assembling_machine", () -> IMenuTypeExtension.create(AssemblerMenu::client));

    public static final DeferredHolder<MenuType<?>, MenuType<dev.minetorio.content.robot.RoboportMenu>> ROBOPORT =
            MENUS.register("roboport", () -> IMenuTypeExtension.create(dev.minetorio.content.robot.RoboportMenu::client));

    public static final DeferredHolder<MenuType<?>, MenuType<dev.minetorio.content.inserter.InserterFilterMenu>> FILTER_INSERTER =
            MENUS.register("filter_inserter", () -> IMenuTypeExtension.create(dev.minetorio.content.inserter.InserterFilterMenu::client));

    private ModMenus() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
