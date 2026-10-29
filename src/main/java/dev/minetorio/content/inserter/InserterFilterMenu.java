package dev.minetorio.content.inserter;

import dev.minetorio.content.registry.ModMenus;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Filter inserter GUI: the filter's ghost slots (no items are stored: clicking one with an item on
 * the cursor sets that item, clicking it with an empty cursor clears it), a whitelist/blacklist
 * toggle and the player inventory (to pick items up). The buttons go to the server as menu button
 * clicks ({@link #clickMenuButton}): {@code 0..slots-1} set/clear a filter slot from the carried
 * item, {@link #BUTTON_TOGGLE_MODE} toggles the mode. The screen reads the filter from the (synced)
 * client block entity.
 */
public class InserterFilterMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE_MODE = 100;
    public static final int FILTER_Y = 20;
    public static final int INVENTORY_Y = 84;
    private static final double MAX_DISTANCE_SQ = 64;

    private final BlockPos pos;
    @Nullable
    private final InserterBlockEntity inserter;

    public InserterFilterMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.FILTER_INSERTER.get(), id);
        this.pos = pos;
        this.inserter = inventory.player.level().getBlockEntity(pos) instanceof InserterBlockEntity be ? be : null;
        var main=dev.minetorio.content.character.CharacterInventory.main(inventory);
        for(int row=0;row<8;row++)for(int col=0;col<10;col++)addSlot(new Slot(main,row*10+col,210+col*18,32+row*18));
    }

    public static InserterFilterMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        return new InserterFilterMenu(id, inventory, extra.readBlockPos());
    }

    public BlockPos pos() { return pos; }

    /** The inserter, or null if it is gone. */
    @Nullable
    public InserterBlockEntity inserter() { return inserter != null && !inserter.isRemoved() ? inserter : null; }

    /** Number of filter slots (0 if the inserter is gone). */
    public int filterSlots() {
        InserterBlockEntity be = inserter();
        return be == null || be.filter() == null ? 0 : be.filter().size();
    }

    /** X of filter slot {@code i}'s top-left item corner, centered in the 176 px panel. */
    public static int filterX(int i, int slots) { return 88 - slots * 9 + 1 + i * 18; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        InserterBlockEntity be = inserter();
        if (!stillValid(player) || player.isSpectator() || be == null || be.filter() == null) return false;
        if (id == BUTTON_TOGGLE_MODE) {
            be.setBlacklist(!be.filter().isBlacklist());
            return true;
        }
        if (id < 0 || id >= be.filter().size()) return false;
        ItemStack carried = getCarried();
        be.setFilter(id, carried.isEmpty() ? null : carried.getItemHolder().getRegisteredName());
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return inserter() != null && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_DISTANCE_SQ;
    }

    /** Shift-click does nothing (the filter holds no items). */
    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
