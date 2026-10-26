package dev.minetorio.content.power;

import dev.minetorio.content.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Small electric pole: registers with the {@link PowerGridManager} while loaded. Its wire
 * connections (poles it is connected to) are set by the manager and sent to the client in the
 * update tag; they are not saved (recomputed on load).
 */
public class ElectricPoleBlockEntity extends BlockEntity {
    private static final String KEY_WIRES = "wires";

    private List<BlockPos> connections = List.of();

    public ElectricPoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMALL_ELECTRIC_POLE.get(), pos, state);
    }

    /** Poles this one is wired to (server: as last set by the manager; client: as last synced). */
    public List<BlockPos> connections() { return connections; }

    /** Server: new wire connections from the manager; synced to clients when they changed. */
    void setConnections(Set<BlockPos> poles) {
        List<BlockPos> now = new ArrayList<>(poles);
        now.sort(null);
        if (now.equals(connections)) return;
        connections = List.copyOf(now);
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) PowerGridManager.get(server).registerPole(worldPosition);
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
            PowerGridManager grid = PowerGridManager.getIfPresent(server);
            if (grid != null) grid.unregisterPole(worldPosition);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_WIRES)) { // client update
            List<BlockPos> list = new ArrayList<>();
            for (long l : tag.getLongArray(KEY_WIRES)) list.add(BlockPos.of(l));
            connections = List.copyOf(list);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLongArray(KEY_WIRES, connections.stream().mapToLong(BlockPos::asLong).toArray());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
