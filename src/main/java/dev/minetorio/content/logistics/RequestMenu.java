package dev.minetorio.content.logistics;

import dev.minetorio.core.logistics.LogisticRequest;
import dev.minetorio.core.logistics.LogisticStats;
import dev.minetorio.core.logistics.RequestTable;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A menu with logistic request slots (ghost slots: no items are stored) and one toggle: a logistic
 * chest's requests or filter ({@link LogisticChestMenu}) or a player's personal requests
 * ({@link PersonalLogisticsMenu}).
 *
 * <p>Edits go to the server as menu buttons ({@link #clickMenuButton}): {@code 0..n-1} sets slot i to
 * the carried item (count: one stack), {@link #BUTTON_CLEAR}{@code + i} clears it, {@link #BUTTON_TOGGLE}
 * flips the toggle; counts come as a {@link LogisticsPayloads.EditRequest}. The server's table is
 * mirrored to the client menu with {@link LogisticsPayloads.RequestSync} whenever it changes.
 */
public abstract class RequestMenu extends AbstractContainerMenu {
    public static final int BUTTON_CLEAR = 50;
    public static final int BUTTON_TOGGLE = 100;

    /** Server: the real table; client: a copy kept in sync. */
    protected final RequestTable table;
    @Nullable
    private final ServerPlayer owner;
    private int sentVersion = -1;
    private boolean sentToggle;
    /** Client: the toggle as last synced. */
    private boolean clientToggle;

    protected RequestMenu(@Nullable MenuType<?> type, int id, Inventory inventory, RequestTable table) {
        super(type, id);
        this.table = table;
        this.owner = inventory.player instanceof ServerPlayer sp ? sp : null;
    }

    public RequestTable table() { return table; }

    /** Whether the slots have a maximum too (personal requests). */
    public abstract boolean hasMax();

    /** Whether the slots have counts at all (not for a storage filter). */
    public boolean hasCounts() { return true; }

    /** The toggle (request from buffers, personal logistics active). */
    public boolean toggle() { return owner == null ? clientToggle : serverToggle(); }

    protected abstract boolean serverToggle();

    protected abstract void setServerToggle(boolean on);

    /** Server: the table was edited (save, sync the block). */
    protected abstract void changed();

    /** X of request slot {@code i}'s item corner. */
    public abstract int requestX(int i);

    /** Y of request slot {@code i}'s item corner. */
    public abstract int requestY(int i);

    protected void addPlayerInventory(Inventory inventory, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, y + 58));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_TOGGLE) {
            setServerToggle(!serverToggle());
            changed();
            return true;
        }
        if (id >= BUTTON_CLEAR && id < BUTTON_CLEAR + table.size()) {
            table.set(id - BUTTON_CLEAR, null);
            changed();
            return true;
        }
        if (id < 0 || id >= table.size()) return false;
        ItemStack carried = getCarried();
        if (carried.isEmpty()) return false;
        int stack = carried.getMaxStackSize();
        table.set(id, new LogisticRequest(carried.getItemHolder().getRegisteredName(), hasCounts() ? stack : 0, LogisticStats.UNLIMITED));
        changed();
        return true;
    }

    /** Server: new counts for slot {@code slot} from the client. */
    public void editCounts(int slot, int min, int max) {
        if (!hasCounts()) return;
        int cap = 1_000_000;
        min = Math.max(0, Math.min(cap, min));
        max = !hasMax() || max < 0 || max >= cap ? LogisticStats.UNLIMITED : max;
        if (table.setCounts(slot, min, max)) changed();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (owner == null) return;
        boolean t = serverToggle();
        if (table.version() != sentVersion || t != sentToggle) {
            sentVersion = table.version();
            sentToggle = t;
            PacketDistributor.sendToPlayer(owner, new LogisticsPayloads.RequestSync(containerId, RequestNbt.entries(table), t));
        }
    }

    /** Client: the server's table. */
    public void applySync(List<RequestNbt.Entry> entries, boolean toggle) {
        RequestNbt.apply(table, entries);
        clientToggle = toggle;
    }
}
