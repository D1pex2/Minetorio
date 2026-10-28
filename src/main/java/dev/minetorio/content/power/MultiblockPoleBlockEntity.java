package dev.minetorio.content.power;

import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.power.PoleType;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Block entity of a {@link MultiblockPoleBlock} (medium/big pole, substation): registers the pole
 * with the {@link PowerGridManager} under its footprint corner while loaded; wires as for the small
 * pole (set by the manager, sent in the update tag, not saved).
 */
public class MultiblockPoleBlockEntity extends MultiblockControllerBlockEntity implements PowerPoleEntity {
    /** Wire attachment height (px above the footprint bottom) per type, at the top insulators of art/gen_energy.py. */
    public static double attachPx(PoleType type) {
        return switch (type) {
            case MEDIUM -> 61;
            case BIG -> 76;
            case SUBSTATION -> 45;
            default -> 47;
        };
    }

    private List<BlockPos> connections = List.of();

    public MultiblockPoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MULTIBLOCK_POLE.get(), pos, state);
    }

    public Direction facing() { return getBlockState().getValue(MultiblockControllerBlock.FACING); }

    public MultiblockShape shape() { return ((MultiblockControllerBlock) getBlockState().getBlock()).shape(); }

    @Override
    public PoleType poleType() { return ((PowerPole) getBlockState().getBlock()).poleType(); }

    public AABB footprintBox() {
        return ElectricConsumerHelper.box(shape().footprint(worldPosition, facing()));
    }

    @Override
    public BlockPos footprintMin() {
        AABB b = footprintBox();
        return BlockPos.containing(b.minX, b.minY, b.minZ);
    }

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
        AABB b = footprintBox();
        return new Vec3((b.minX + b.maxX) / 2 - worldPosition.getX(), attachPx(poleType()) / 16,
                (b.minZ + b.maxZ) / 2 - worldPosition.getZ());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) PowerGridManager.get(server).registerPole(worldPosition, poleType(), footprintMin());
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
        connections = PowerPoleEntity.readWires(tag, connections);
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
