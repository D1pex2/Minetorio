package dev.minetorio.content.logistics;

import dev.minetorio.core.logistics.LogisticStats;
import dev.minetorio.core.logistics.RequestTable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Personal logistics GUI (opened with the personal logistics key): two rows of request slots, the
 * selected slot's minimum and maximum below them with the "personal logistics active" toggle, and the
 * player inventory to pick items from.
 */
public class PersonalLogisticsMenu extends RequestMenu {
    public static final int COLUMNS = 9;
    public static final int REQUEST_Y = 18;
    public static final int EDIT_Y = REQUEST_Y + 2 * 18 + 4;
    public static final int INVENTORY_Y = EDIT_Y + 44;

    private final PersonalLogistics personal;

    public PersonalLogisticsMenu(int id, Inventory inventory) {
        super(LogisticsRegistry.PERSONAL_MENU.get(), id, inventory, table(inventory));
        this.personal = PersonalLogistics.of(inventory.player);
        addPlayerInventory(inventory, INVENTORY_Y);
    }

    private static RequestTable table(Inventory inventory) {
        return inventory.player.level().isClientSide() ? new RequestTable(LogisticStats.PERSONAL_REQUEST_SLOTS)
                : PersonalLogistics.of(inventory.player).requests();
    }

    public static PersonalLogisticsMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        return new PersonalLogisticsMenu(id, inventory);
    }

    @Override
    public boolean hasMax() { return true; }

    @Override
    protected boolean serverToggle() { return personal.enabled(); }

    @Override
    protected void setServerToggle(boolean on) { personal.setEnabled(on); }

    @Override
    protected void changed() {}

    @Override
    public int requestX(int i) { return 8 + (i % COLUMNS) * 18; }

    @Override
    public int requestY(int i) { return REQUEST_Y + (i / COLUMNS) * 18; }

    @Override
    public boolean stillValid(Player player) { return player.isAlive(); }

    /** Shift-click moves between the main inventory and the hotbar. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Personal requests contain no physical second inventory to shift items into.
        return ItemStack.EMPTY;
    }
}
