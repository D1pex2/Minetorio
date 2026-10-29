package dev.minetorio.content.world;

import dev.minetorio.content.registry.ModWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The crashed ship's cargo hold: nine slots, filled from the loot table
 * {@code minetorio:chests/crash_site} (Factorio's freeplay starting items) the first time it opens.
 */
public class CrashSiteChestBlockEntity extends RandomizableContainerBlockEntity {
    public static final int SIZE = 9;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public CrashSiteChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModWorld.CRASH_SITE_CHEST_BE.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items, registries);
    }

    @Override
    public int getContainerSize() { return SIZE; }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.minetorio.crash_site_chest");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return new BasicChestMenu(id, player, worldPosition, this);
    }
}
