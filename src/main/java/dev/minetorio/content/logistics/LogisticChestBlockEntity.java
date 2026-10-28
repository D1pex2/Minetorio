package dev.minetorio.content.logistics;

import dev.minetorio.core.logistics.LogisticMode;
import dev.minetorio.core.logistics.LogisticStats;
import dev.minetorio.core.logistics.RequestTable;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * A logistic chest's {@value LogisticStats#CHEST_SLOTS}-slot inventory, its request slots (requester and
 * buffer chests; a storage chest's single slot is its filter, counts unused) and the requester's
 * "request from buffer chests" toggle. Registers itself with the level's {@link LogisticsManager}
 * while loaded. Clients get the requests (for the GUI and alt mode), not the items.
 */
public class LogisticChestBlockEntity extends BlockEntity {
    private static final String KEY_ITEMS = "items";
    private static final String KEY_REQUESTS = "requests";
    private static final String KEY_FROM_BUFFERS = "from_buffers";

    private final SimpleContainer items = new SimpleContainer(LogisticStats.CHEST_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            LogisticChestBlockEntity.this.setChanged();
        }
    };
    private final IItemHandler handler = new InvWrapper(items);
    private final RequestTable requests;
    private boolean fromBuffers;

    public LogisticChestBlockEntity(BlockPos pos, BlockState state) {
        super(LogisticsRegistry.CHEST.get(), pos, state);
        this.requests = new RequestTable(modeOf(state).requestSlots());
    }

    private static LogisticMode modeOf(BlockState state) {
        return state.getBlock() instanceof LogisticChestBlock b ? b.mode() : LogisticMode.PASSIVE_PROVIDER;
    }

    public LogisticMode mode() { return modeOf(getBlockState()); }

    public SimpleContainer container() { return items; }

    public IItemHandler itemHandler() { return handler; }

    public RequestTable requests() { return requests; }

    /** A storage chest's filter item id, or null. */
    @Nullable
    public String filter() {
        return mode() == LogisticMode.STORAGE && requests.get(0) != null ? requests.get(0).item() : null;
    }

    public boolean requestFromBuffers() { return fromBuffers; }

    public void setRequestFromBuffers(boolean on) {
        fromBuffers = on;
        requestsChanged();
    }

    /** Saves and sends the requests to clients after an edit. */
    public void requestsChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Item counts by id. */
    public Map<String, Integer> counts() {
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i < items.getContainerSize(); i++) {
            ItemStack s = items.getItem(i);
            if (!s.isEmpty()) out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        return out;
    }

    /** How many more plain items of {@code itemId} fit (partial stacks of it plus empty slots). */
    public int room(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return 0;
        Item item = BuiltInRegistries.ITEM.get(rl);
        ItemStack plain = new ItemStack(item);
        int max = plain.getMaxStackSize();
        int room = 0;
        for (int i = 0; i < items.getContainerSize(); i++) {
            ItemStack s = items.getItem(i);
            if (s.isEmpty()) room += max;
            else if (ItemStack.isSameItemSameComponents(s, plain)) room += Math.max(0, max - s.getCount());
        }
        return room;
    }

    // --- lifecycle --------------------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) LogisticsManager.get(server).addChest(worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        unregister();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        unregister();
    }

    private void unregister() {
        if (level instanceof ServerLevel server) {
            LogisticsManager m = LogisticsManager.getIfPresent(server);
            if (m != null) m.removeChest(worldPosition);
        }
    }

    // --- persistence and sync ---------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag inv = new CompoundTag();
        ContainerHelper.saveAllItems(inv, items.getItems(), registries);
        tag.put(KEY_ITEMS, inv);
        saveRequests(tag);
    }

    private void saveRequests(CompoundTag tag) {
        tag.put(KEY_REQUESTS, RequestNbt.save(requests));
        tag.putBoolean(KEY_FROM_BUFFERS, fromBuffers);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_ITEMS)) {
            NonNullList<ItemStack> list = NonNullList.withSize(LogisticStats.CHEST_SLOTS, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag.getCompound(KEY_ITEMS), list, registries);
            for (int i = 0; i < list.size(); i++) items.setItem(i, list.get(i));
        }
        RequestNbt.load(requests, tag.getList(KEY_REQUESTS, net.minecraft.nbt.Tag.TAG_COMPOUND));
        fromBuffers = tag.getBoolean(KEY_FROM_BUFFERS);
    }

    /** Clients get the requests and the toggle only. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveRequests(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
