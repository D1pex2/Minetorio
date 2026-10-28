package dev.minetorio.content.power;

import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.power.PoleType;
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
import net.minecraft.world.phys.Vec3;

/**
 * Small electric pole: registers with the {@link PowerGridManager} while loaded. Its wire
 * connections (poles it is connected to) are set by the manager and sent to the client in the
 * update tag; they are not saved (recomputed on load).
 */
public class ElectricPoleBlockEntity extends BlockEntity implements PowerPoleEntity {
    /** Wire attachment height above the lower block origin: the top insulator of the three-block pole. */
    public static final double ATTACH_Y = 47 / 16.0;
    /** The attachment height of a single-block pole from an older world (top of its short post). */
    public static final double ATTACH_Y_SHORT = 22 / 16.0;

    private List<BlockPos> connections = List.of();

    public ElectricPoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMALL_ELECTRIC_POLE.get(), pos, state);
    }

    @Override
    public PoleType poleType() { return PoleType.SMALL; }

    @Override
    public BlockPos footprintMin() { return worldPosition; }

    @Override
    public List<BlockPos> connections() { return connections; }

    @Override
    public void setConnections(Set<BlockPos> poles) {
        List<BlockPos> now = PowerPoleEntity.sorted(poles);
        if (now.equals(connections)) return;
        connections = now;
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public Vec3 wireAttach() {
        boolean full = level == null || ElectricPoleBlock.isFullHeight(level, worldPosition);
        return new Vec3(0.5, full ? ATTACH_Y : ATTACH_Y_SHORT, 0.5);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) PowerGridManager.get(server).registerPole(worldPosition, PoleType.SMALL, worldPosition);
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
        connections = PowerPoleEntity.readWires(tag, connections); // client update
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        PowerPoleEntity.writeWires(tag, connections);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
