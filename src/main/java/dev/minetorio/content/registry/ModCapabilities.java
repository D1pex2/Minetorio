package dev.minetorio.content.registry;

import net.minecraft.core.Direction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.EmptyItemHandler;

public final class ModCapabilities {
    private ModCapabilities() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ModCapabilities::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        // Multiblock parts expose whatever their controller exposes.
        delegateToController(event, Capabilities.ItemHandler.BLOCK);
        delegateToController(event, Capabilities.FluidHandler.BLOCK);
        delegateToController(event, Capabilities.EnergyStorage.BLOCK);

        // Placeholder until the stone furnace gets its real inventory.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.STONE_FURNACE.get(),
                (be, side) -> EmptyItemHandler.INSTANCE);
    }

    private static <T> void delegateToController(RegisterCapabilitiesEvent event, BlockCapability<T, Direction> capability) {
        event.registerBlockEntity(capability, ModBlockEntities.MULTIBLOCK_PART.get(),
                (be, side) -> be.controllerCapability(capability, side));
    }
}
