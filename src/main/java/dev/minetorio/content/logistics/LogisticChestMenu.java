package dev.minetorio.content.logistics;

import dev.minetorio.core.logistics.LogisticMode;
import dev.minetorio.core.logistics.LogisticStats;
import dev.minetorio.core.logistics.RequestTable;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Logistic chest GUI: for a requester or buffer chest a row of request slots with the selected
 * slot's count below (and, for a requester, "request from buffer chests"); for a storage chest its
 * filter slot; then the chest's {@value LogisticStats#CHEST_SLOTS} slots and the player inventory.
 */
public class LogisticChestMenu extends RequestMenu {
    public static final int REQUEST_Y = 18;
    public static final int EDIT_Y = 38;
    private static final double MAX_DISTANCE_SQ = 64;

    private final BlockPos pos;
    private final LogisticMode mode;
    private final Container chest;
    @Nullable
    private final LogisticChestBlockEntity be;
    private final int chestY;
    private final int inventoryY;

    public LogisticChestMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, inventory.player.level().getBlockEntity(pos) instanceof LogisticChestBlockEntity b ? b : null);
    }

    private LogisticChestMenu(int id, Inventory inventory, BlockPos pos, @Nullable LogisticChestBlockEntity be) {
        super(LogisticsRegistry.CHEST_MENU.get(), id, inventory, table(inventory, be));
        this.pos = pos;
        this.be = be;
        this.mode = be != null ? be.mode() : LogisticMode.PASSIVE_PROVIDER;
        this.chest = be != null && !inventory.player.level().isClientSide() ? be.container() : new SimpleContainer(LogisticStats.CHEST_SLOTS);
        this.chestY = chestY(mode);
        this.inventoryY = chestY + 3 * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(chest, col + row * 9, 8 + col * 18, chestY + row * 18));
        }
        addPlayerInventory(inventory, inventoryY);
    }

    private static RequestTable table(Inventory inventory, @Nullable LogisticChestBlockEntity be) {
        if (be == null) return new RequestTable(0);
        return inventory.player.level().isClientSide() ? new RequestTable(be.mode().requestSlots()) : be.requests();
    }

    public static LogisticChestMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        return new LogisticChestMenu(id, inventory, extra.readBlockPos());
    }

    /** Top of the chest's slots: below the request row and its count editor, or the filter row. */
    public static int chestY(LogisticMode mode) {
        return switch (mode.requestSlots()) {
            case 0 -> 18;
            case 1 -> 42;
            default -> 60;
        };
    }

    public LogisticMode mode() { return mode; }

    public BlockPos pos() { return pos; }

    public int inventoryY() { return inventoryY; }

    @Override
    public boolean hasMax() { return false; }

    @Override
    public boolean hasCounts() { return mode != LogisticMode.STORAGE; }

    @Override
    protected boolean serverToggle() { return be != null && be.requestFromBuffers(); }

    @Override
    protected void setServerToggle(boolean on) {
        if (be != null && mode == LogisticMode.REQUESTER) be.setRequestFromBuffers(on);
    }

    @Override
    protected void changed() {
        if (be != null) be.requestsChanged();
    }

    @Override
    public int requestX(int i) { return 8 + i * 18; }

    @Override
    public int requestY(int i) { return REQUEST_Y; }

    @Override
    public boolean stillValid(Player player) {
        return be != null && !be.isRemoved() && player.level().getBlockEntity(pos) == be
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_DISTANCE_SQ;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int n = LogisticStats.CHEST_SLOTS;
        if (index < n) {
            if (!moveItemStackTo(stack, n, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, n, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
