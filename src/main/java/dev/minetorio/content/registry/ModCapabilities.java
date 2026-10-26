package dev.minetorio.content.registry;

import dev.minetorio.content.power.FluidPorts;
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
            if (level.getBlockState(be.controllerPos()).getBlock() instanceof FluidPorts.Machine) {
                return FluidPorts.handler(level, be.getBlockPos(), side);
            }
            return be.controllerCapability(Capabilities.FluidHandler.BLOCK, side);
        });
        delegateToController(event, Capabilities.EnergyStorage.BLOCK);

        // Pipes and the fluid ports on the steam machines' controller cells.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.PIPE.get(), (be, side) -> be.fluidHandler());
        registerPorts(event, ModBlockEntities.OFFSHORE_PUMP.get());
        registerPorts(event, ModBlockEntities.BOILER.get());
        registerPorts(event, ModBlockEntities.STEAM_ENGINE.get());

        // Crafting machines: automated inserts into inputs/fuel, extraction from outputs only.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.STONE_FURNACE.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.ASSEMBLING_MACHINE.get(), (be, side) -> be.itemHandler());
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
