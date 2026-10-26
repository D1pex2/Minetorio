package dev.minetorio.content.machine;

import static dev.minetorio.content.machine.StoneFurnaceBlockEntity.FUEL_SLOT;
import static dev.minetorio.content.machine.StoneFurnaceBlockEntity.INPUT_SLOT;

import dev.minetorio.content.registry.ModMenus;
import dev.minetorio.core.data.Phase1Data;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Stone furnace GUI (vanilla furnace layout): fuel, input and output slots, smelt progress and
 * the flame while lit.
 */
public class StoneFurnaceMenu extends MachineMenu {
    /** Server side, over the furnace's slots and data. */
    public StoneFurnaceMenu(int id, Inventory inventory, MachineContainer furnace, ContainerData data) {
        super(ModMenus.STONE_FURNACE.get(), id, furnace, data);
        addMachineSlot(new MachineSlot(furnace, FUEL_SLOT, 56, 53));
        addMachineSlot(new MachineSlot(furnace, INPUT_SLOT, 56, 17));
        addMachineSlot(new MachineSlot(furnace, StoneFurnaceBlockEntity.OUTPUT_SLOT, 116, 35));
        addPlayerInventory(inventory, 8, 84);
    }

    /** Client side: contents arrive through the menu sync; {@code room} is a prediction. */
    public static StoneFurnaceMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        MachineContainer container = MachineContainer.client(StoneFurnaceBlockEntity.SLOTS, (slot, stack) -> {
            if (!MachineItems.isPlain(stack)) return 0;
            String item = MachineItems.id(stack);
            boolean allowed = slot == FUEL_SLOT ? Phase1Data.fuelValues().containsKey(item)
                    : slot == INPUT_SLOT && Phase1Data.recipes().smeltingFor(item).isPresent();
            return allowed ? 64 : 0; // capped to a stack by MachineSlot
        });
        return new StoneFurnaceMenu(id, inventory, container, new SimpleContainerData(StoneFurnaceBlockEntity.DATA_COUNT));
    }

    /** Smelt progress 0..1. */
    public float progress() {
        return Math.min(1f, data.get(StoneFurnaceBlockEntity.DATA_PROGRESS) / 1000f);
    }

    public boolean isLit() { return data.get(StoneFurnaceBlockEntity.DATA_LIT) != 0; }
}
