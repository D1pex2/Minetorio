package dev.minetorio.content.registry;

import dev.minetorio.content.power.FluidPorts;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.wrapper.EmptyItemHandler;

public final class ModCapabilities {
    private ModCapabilities() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ModCapabilities::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        // Multiblock parts expose whatever their controller exposes.
        delegateToController(event, Capabilities.ItemHandler.BLOCK);
        // Fluid: a part that is a fluid port of its machine exposes that port (other parts: the controller's).
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.MULTIBLOCK_PART.get(), (be, side) -> {
            IFluidHandler port = be.getLevel() == null ? null : FluidPorts.handler(be.getLevel(), be.getBlockPos(), side);
            return port != null ? port : be.controllerCapability(Capabilities.FluidHandler.BLOCK, side);
        });
        delegateToController(event, Capabilities.EnergyStorage.BLOCK);

        // Pipes and the fluid ports on the steam machines' controller cells.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.PIPE.get(), (be, side) -> be.fluidHandler());
        registerPorts(event, ModBlockEntities.OFFSHORE_PUMP.get());
        registerPorts(event, ModBlockEntities.BOILER.get());
        registerPorts(event, ModBlockEntities.STEAM_ENGINE.get());

        // Placeholder until the stone furnace gets its real inventory.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.STONE_FURNACE.get(),
                (be, side) -> EmptyItemHandler.INSTANCE);
    }

    private static <B extends BlockEntity> void registerPorts(RegisterCapabilitiesEvent event, BlockEntityType<B> type) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type,
                (be, side) -> be.getLevel() == null ? null : FluidPorts.handler(be.getLevel(), be.getBlockPos(), side));
    }

    private static <T> void delegateToController(RegisterCapabilitiesEvent event, BlockCapability<T, Direction> capability) {
        event.registerBlockEntity(capability, ModBlockEntities.MULTIBLOCK_PART.get(),
                (be, side) -> be.controllerCapability(capability, side));
    }
}
