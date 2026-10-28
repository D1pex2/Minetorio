package dev.minetorio.content.registry;

import dev.minetorio.content.fluid.FluidPortBlock;
import dev.minetorio.content.fluid.FluidPorts;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class ModCapabilities {
    private ModCapabilities() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ModCapabilities::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        // Multiblock parts expose whatever their controller exposes.
        delegateToController(event, Capabilities.ItemHandler.BLOCK);
        // Fluid: parts of a machine with fluid ports expose only their declared ports (others: nothing);
        // parts of any other machine expose the controller's handler.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.MULTIBLOCK_PART.get(), (be, side) -> {
            Level level = be.getLevel();
            if (level == null || !be.isLinked() || !level.isLoaded(be.controllerPos())) return null;
            if (level.getBlockState(be.controllerPos()).getBlock() instanceof FluidPortBlock) {
                return FluidPorts.handler(level, be.getBlockPos(), side);
            }
            return be.controllerCapability(Capabilities.FluidHandler.BLOCK, side);
        });
        delegateToController(event, Capabilities.EnergyStorage.BLOCK);

        // Pipes and the fluid ports on the steam machines' controller cells.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.PIPE.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.UNDERGROUND_PIPE.get(), (be, side) -> be.fluidHandler());
        FluidPorts.registerCapability(event, ModBlockEntities.OFFSHORE_PUMP.get());
        FluidPorts.registerCapability(event, ModBlockEntities.BOILER.get());
        FluidPorts.registerCapability(event, ModBlockEntities.STEAM_ENGINE.get());
        FluidPorts.registerCapability(event, ModBlockEntities.STORAGE_TANK.get());
        FluidPorts.registerCapability(event, ModBlockEntities.PUMP.get());

        // Crafting machines: automated inserts into inputs/fuel, extraction from outputs only.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.STONE_FURNACE.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.ASSEMBLING_MACHINE.get(), (be, side) -> be.itemHandler());
        // Labs: automated inserts of science packs, no extraction.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.LAB.get(), (be, side) -> be.itemHandler());
    }

    private static <T> void delegateToController(RegisterCapabilitiesEvent event, BlockCapability<T, Direction> capability) {
        event.registerBlockEntity(capability, ModBlockEntities.MULTIBLOCK_PART.get(),
                (be, side) -> be.controllerCapability(capability, side));
    }
}
