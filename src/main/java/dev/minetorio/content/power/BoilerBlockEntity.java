package dev.minetorio.content.power;

import dev.minetorio.content.inserter.InserterEndpoints;
import dev.minetorio.content.inserter.MachineEndpoint;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.belt.LaneItem;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.fluid.FluidBox;
import dev.minetorio.core.fluid.SteamPlant;
import dev.minetorio.core.inserter.DropTarget;
import dev.minetorio.core.inserter.PickupSource;
import dev.minetorio.core.machine.Burner;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Boiler: the core {@link SteamPlant.Boiler} on its own {@link Burner} (1.8 MW). It turns up to 3
 * water per tick into steam and burns fuel only for the steam it makes, so a full steam box (no
 * engine drawing) burns nothing. Working (fire glow) = made steam recently. As a
 * {@link MachineEndpoint} it accepts fuel from inserters up to the automated limit and offers nothing.
 */
public class BoilerBlockEntity extends SteamMachineBlockEntity implements MachineEndpoint {
    private static final String KEY_WATER = "water";
    private static final String KEY_STEAM = "steam";
    private static final String KEY_BURNER = "burner";

    private final Burner burner = new Burner(SteamPlant.BOILER_POWER_J, Phase1Data.fuelValues());
    private final SteamPlant.Boiler boiler = new SteamPlant.Boiler(burner);

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOILER.get(), pos, state);
    }

    public Burner burner() { return burner; }

    public FluidBox water() { return boiler.water(); }

    public FluidBox steam() { return boiler.steam(); }

    /** Puts fuel into the slot by hand; returns the amount accepted. */
    public int addFuel(String item, int count) {
        int accepted = burner.insertFuel(item, count, false);
        if (accepted > 0) setChanged();
        return accepted;
    }

    @Override
    public FluidBox fluidBox(int port) { return port == 2 ? boiler.steam() : boiler.water(); }

    @Override
    protected void tickMachine(Level level) {
        double before = boiler.steam().amount();
        boiler.tick();
        boolean made = boiler.steam().amount() > before;
        if (made) setChanged();
        setActive(level, made);
    }

    @Override
    public Component status() {
        int fuel = burner.fuel().values().stream().mapToInt(Integer::intValue).sum();
        return Component.translatable("minetorio.boiler.status", units(boiler.water().amount()), units(boiler.steam().amount()), fuel);
    }

    // --- inserters -----------------------------------------------------------------------------------

    @Override
    public PickupSource asSource() { return InserterEndpoints.NO_SOURCE; }

    /** Fuel only, automated limit; fuel is stored by item id, so keys with data components are refused. */
    @Override
    public DropTarget asTarget() {
        return new DropTarget() {
            @Override
            public boolean wants(String item) { return LaneItem.isPlain(item) && Phase1Data.fuelValues().containsKey(item); }

            @Override
            public boolean accept(String item) {
                if (!LaneItem.isPlain(item) || burner.insertFuel(item, 1, true) != 1) return false;
                setChanged();
                return true;
            }
        };
    }

    // --- drops and persistence -----------------------------------------------------------------------

    @Override
    protected void dropContents(Level level, BlockPos pos) {
        MachineItems.drop(level, pos, burner.fuel());
        burner.load(Map.of(), burner.remainingJ());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_WATER, CoreNbt.writeFluid(boiler.water()));
        tag.put(KEY_STEAM, CoreNbt.writeFluid(boiler.steam()));
        tag.put(KEY_BURNER, CoreNbt.writeBurner(burner));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_WATER)) CoreNbt.loadFluidInto(boiler.water(), tag.getCompound(KEY_WATER));
        if (tag.contains(KEY_STEAM)) CoreNbt.loadFluidInto(boiler.steam(), tag.getCompound(KEY_STEAM));
        if (tag.contains(KEY_BURNER)) CoreNbt.loadBurnerInto(burner, tag.getCompound(KEY_BURNER));
    }
}
