package dev.minetorio.content.machine;

import dev.minetorio.content.inserter.InserterEndpoints;
import dev.minetorio.content.inserter.MachineEndpoint;
import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.Units;
import dev.minetorio.core.belt.LaneItem;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.inserter.DropTarget;
import dev.minetorio.core.inserter.PickupSource;
import dev.minetorio.core.machine.Burner;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Burner mining drill: 0.25 ore/s on its own {@link Burner} (150 kW), mining area 2×2. It burns
 * fuel only while it can mine. As a {@link MachineEndpoint} it accepts fuel from inserters (and
 * drills) up to the automated limit and offers nothing: the ore goes straight into the cell in
 * front of it.
 */
public class BurnerDrillBlockEntity extends MiningDrillBlockEntity implements MachineEndpoint {
    public static final double RATE = 0.25;
    public static final int AREA = 2;
    public static final long POWER = Units.kw(150);
    private static final String KEY_BURNER = "burner";
    private static final String KEY_FUEL = "fuel";
    private static final String KEY_REMAINING = "remaining";

    private final Burner burner = new Burner(POWER, Phase1Data.fuelValues());

    public BurnerDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BURNER_MINING_DRILL.get(), pos, state);
    }

    public Burner burner() { return burner; }

    @Override
    protected double miningRate() { return RATE; }

    @Override
    protected int areaSize() { return AREA; }

    @Override
    protected double energy() { return burner.draw(); }

    @Override
    protected String noEnergyKey() { return "minetorio.drill.no_fuel"; }

    /** Puts fuel into the slot by hand; returns the amount accepted. */
    public int addFuel(String item, int count) {
        int accepted = burner.insertFuel(item, count, false);
        if (accepted > 0) setChanged();
        return accepted;
    }

    // --- inserters -----------------------------------------------------------------------------------

    @Override
    public PickupSource asSource() { return InserterEndpoints.NO_SOURCE; }

    /** Fuel only, automated limit; fuel is stored by item id (extra item data is not kept). */
    @Override
    public DropTarget asTarget() {
        return new DropTarget() {
            @Override
            public boolean wants(String item) { return Phase1Data.fuelValues().containsKey(LaneItem.baseId(item)); }

            @Override
            public boolean accept(String item) {
                if (burner.insertFuel(LaneItem.baseId(item), 1, true) != 1) return false;
                setChanged();
                return true;
            }
        };
    }

    // --- drops and persistence -----------------------------------------------------------------------

    @Override
    protected void dropContents(Level level, BlockPos pos) {
        super.dropContents(level, pos);
        for (Map.Entry<String, Integer> e : burner.fuel().entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) continue;
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, new ItemStack(item, e.getValue()));
        }
        burner.load(Map.of(), burner.remainingJ());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag b = new CompoundTag();
        b.put(KEY_FUEL, CoreNbt.writeCounts(burner.fuel()));
        b.putLong(KEY_REMAINING, burner.remainingJ());
        tag.put(KEY_BURNER, b);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_BURNER)) {
            CompoundTag b = tag.getCompound(KEY_BURNER);
            burner.load(CoreNbt.readCounts(b.getCompound(KEY_FUEL)), b.getLong(KEY_REMAINING));
        }
    }
}
