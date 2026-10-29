package dev.minetorio.content.machine;

import dev.minetorio.content.registry.ModMenus;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.recipe.RecipeCategory;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Assembling machine GUI: recipe list (drawn by the screen), one input slot per recipe input
 * (the rest inactive), the output slot, craft progress and power satisfaction. The recipe is
 * synced as an index into {@link #RECIPES}.
 */
public class AssemblerMenu extends MachineMenu {
    /** Every recipe in book order; data slot {@link AssemblerBlockEntity#DATA_RECIPE} indexes it. */
    public static final List<Recipe> RECIPES = List.copyOf(Phase1Data.recipes().all());
    /** Recipes an assembler can be set to (CRAFTING), unlocked or not. */
    public static final List<Recipe> CRAFTING = RECIPES.stream().filter(r -> r.category() != RecipeCategory.SMELTING).toList();
    public static final int SLOTS_Y = 80;
    public static final int OUTPUT_X = 116;
    public static final int INVENTORY_Y = 136;

    private final BlockPos pos;

    /** Server side, over the assembler's slots and data. */
    public AssemblerMenu(int id, Inventory inventory, MachineContainer assembler, ContainerData data, BlockPos pos) {
        super(ModMenus.ASSEMBLING_MACHINE.get(), id, assembler, data, AssemblerBlockEntity.DATA_COUNT);
        this.pos = pos;
        for (int i = 0; i < AssemblerBlockEntity.MAX_INPUTS; i++) {
            int slot = i;
            addMachineSlot(new MachineSlot(assembler, slot, inputX(slot), SLOTS_Y) {
                @Override
                public boolean isActive() {
                    Recipe r = recipe();
                    return r != null && slot < r.inputs().size();
                }
                @Override
                public int getMaxStackSize() {
                    Recipe r=recipe();
                    return dev.minetorio.core.item.ItemStackSizes.MAX;
                }
                @Override
                public int getMaxStackSize(ItemStack stack) {
                    // Exact express underground recipes need 80 gears in one ingredient buffer.
                    // Player inventory stacks retain Minecraft's limit; this is only a machine buffer.
                    return Math.min(Math.min(getMaxStackSize(),stack.getMaxStackSize()),getItem().getCount()+Math.max(0,assembler.room(slot,stack)));
                }
            });
        }
        addMachineSlot(new MachineSlot(assembler, AssemblerBlockEntity.OUTPUT_SLOT, OUTPUT_X, SLOTS_Y) {
            @Override
            public boolean isActive() { return recipe() != null || hasItem(); }
        });
        addPlayerInventory(inventory, 210, 32);
    }

    /** Client side: reads the machine position; contents arrive through the menu sync. */
    public static AssemblerMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        BlockPos pos = extra.readBlockPos();
        SimpleContainerData data = new SimpleContainerData(AssemblerBlockEntity.DATA_COUNT);
        MachineContainer container = MachineContainer.client(AssemblerBlockEntity.SLOTS, (slot, stack) -> {
            Recipe r = byIndex(data.get(AssemblerBlockEntity.DATA_RECIPE));
            if (r == null || slot >= r.inputs().size() || !MachineItems.isPlain(stack)) return 0;
            return r.inputs().get(slot).item().equals(MachineItems.id(stack)) ? Math.max(dev.minetorio.core.item.ItemStackSizes.limit(MachineItems.id(stack)),r.inputs().get(slot).count()) : 0;
        });
        return new AssemblerMenu(id, inventory, container, data, pos);
    }

    public static int inputX(int slot) { return 8 + slot * 18; }

    public static int indexOf(@Nullable Recipe recipe) { return recipe == null ? -1 : RECIPES.indexOf(recipe); }

    @Nullable
    public static Recipe byIndex(int index) { return index >= 0 && index < RECIPES.size() ? RECIPES.get(index) : null; }

    /** Position of the assembler's controller. */
    public BlockPos pos() { return pos; }

    @Nullable
    public Recipe recipe() { return byIndex(data.get(AssemblerBlockEntity.DATA_RECIPE)); }

    /** Craft progress 0..1. */
    public float progress() { return Math.min(1f, data.get(AssemblerBlockEntity.DATA_PROGRESS) / 1000f); }

    /** Power satisfaction 0..1 last supplied by the grid. */
    public float satisfaction() { return data.get(AssemblerBlockEntity.DATA_POWER) / 1000f; }

    public boolean isWorking() { return data.get(AssemblerBlockEntity.DATA_WORKING) != 0; }
    public boolean supportsFluids() { return data.get(AssemblerBlockEntity.DATA_TIER)==2; }
    public int inputFluidAmount() { return data.get(AssemblerBlockEntity.DATA_FLUID_IN); }
    public int outputFluidAmount() { return data.get(AssemblerBlockEntity.DATA_FLUID_OUT); }
}
