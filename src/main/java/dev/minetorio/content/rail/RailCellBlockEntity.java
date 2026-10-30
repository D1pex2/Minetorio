package dev.minetorio.content.rail;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import static dev.minetorio.core.rail.RailGeometry.*;

/** NBT клетки является восстанавливаемой проекцией, а не источником графа. */
public final class RailCellBlockEntity extends BlockEntity {
    private List<Piece> pieces = List.of();
    public RailCellBlockEntity(BlockPos pos, BlockState state) { super(RailRegistry.CELL_ENTITY.get(),pos,state); }
    public List<Piece> pieces() { return pieces; }
    public void project(List<Piece> next) {
        next = List.copyOf(next);
        if (pieces.equals(next)) return;
        pieces = next; setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) RailwayManager.reconcileCell(server,worldPosition);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        ListTag list = new ListTag();
        for (Piece piece : pieces) {
            CompoundTag t=new CompoundTag(); t.putUUID("id",piece.id()); t.putInt("x",piece.origin().x()); t.putInt("y",piece.origin().y()); t.putInt("z",piece.origin().z());
            t.putString("shape",piece.shape().name()); t.putInt("rotation",piece.quarterTurns()); t.putBoolean("mirror",piece.mirrored()); list.add(t);
        }
        tag.put("references",list);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries); List<Piece> result=new ArrayList<>();
        for(Tag raw:tag.getList("references",Tag.TAG_COMPOUND)) {
            CompoundTag t=(CompoundTag)raw;
            try { result.add(new Piece(t.getUUID("id"),new Point(t.getInt("x"),t.getInt("y"),t.getInt("z")),Shape.valueOf(t.getString("shape")),t.getInt("rotation"),t.getBoolean("mirror"))); }
            catch (IllegalArgumentException ignored) { /* Устаревшая проекция будет восстановлена владельцем. */ }
        }
        pieces=List.copyOf(result);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
