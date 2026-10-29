package dev.minetorio.content.oil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import dev.minetorio.core.oil.OilYield;
import net.minecraft.server.level.ServerLevel;

/** Production history belongs to the well, not to the pumpjack. */
public final class OilSourceBlockEntity extends BlockEntity {
    private OilYield yield = new OilYield(1);
    private long cycles;
    private long fieldKey;
    public OilSourceBlockEntity(BlockPos pos, BlockState state) { super(OilRegistry.OIL_SOURCE_BE.get(), pos, state); }
    public void initialize(double value) { initialize(0, value); }
    public void initialize(long fieldKey, double value) { this.fieldKey = fieldKey; yield = new OilYield(Math.max(0.2, value)); cycles = 0; setChanged(); updateMap(); }
    public long fieldKey() { return fieldKey; }
    public double yield() { return yield.yield(); }
    public double amountPerCycle() { return yield.amountPerCycle(); }
    public long cycles() { return cycles; }
    public void extracted() { yield.extractCycle(); cycles++; setChanged(); updateMap(); }
    @Override public void onLoad() { super.onLoad(); updateMap(); }
    private void updateMap() { if (level instanceof ServerLevel server) dev.minetorio.content.oilmap.OilMapData.update(server, worldPosition, fieldKey, this.yield()); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup); tag.putLong("fieldKey", fieldKey); tag.putDouble("initialYield", yield.initialYield()); tag.putDouble("remainingCycles", yield.remainingCycles()); tag.putLong("cycles", cycles);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        double initial = tag.contains("initialYield") ? Math.max(0.2, tag.getDouble("initialYield")) : 1;
        yield = OilYield.restore(initial, tag.contains("remainingCycles") ? tag.getDouble("remainingCycles") : initial * 30000);
        fieldKey = tag.getLong("fieldKey");
        cycles = Math.max(0, tag.getLong("cycles"));
    }
}
