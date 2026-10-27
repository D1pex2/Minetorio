package dev.minetorio.content.machine;

import dev.minetorio.content.inserter.MachineEndpoint;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.Units;
import dev.minetorio.core.belt.LaneItem;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.inserter.DropTarget;
import dev.minetorio.core.inserter.PickupSource;
import dev.minetorio.core.machine.Burner;
import dev.minetorio.core.machine.CraftingMachine;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Stone furnace (2×2×2): a smelter {@link CraftingMachine} (speed 1, 90 kW) on its own
 * {@link Burner} (90 kW). The recipe follows the input (raw iron/copper, iron/copper ore,
 * cobblestone → stone brick). Fuel burns only while a craft can progress; without fuel no smelt
 * starts or advances.
 *
 * <p><b>Items.</b> Three slots: fuel ({@value #FUEL_SLOT}), input ({@value #INPUT_SLOT}), output
 * ({@value #OUTPUT_SLOT}); the fuel slot holds one fuel type at a time. Machines store items by
 * id, so stacks with data components are refused everywhere. Automated access (inserters through
 * {@link MachineEndpoint}, hoppers through the {@link #itemHandler()} capability) inserts fuel and
 * input with the automated limits (5 fuel, 2 crafts of input) and extracts only the output; the GUI
 * ({@link StoneFurnaceMenu}) lets the player insert up to a stack and take from every slot.
 *
 * <p><b>Client sync.</b> The block state's {@link StoneFurnaceBlock#LIT} is the working flag (lit
 * front, light); it turns on at once and off after {@value #IDLE_GRACE} idle ticks.
 */
public class StoneFurnaceBlockEntity extends MultiblockControllerBlockEntity implements MachineEndpoint, MenuProvider {
    public static final long POWER = Units.kw(90);
    public static final double SPEED = 1.0;
    public static final int FUEL_SLOT = 0;
    public static final int INPUT_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;
    public static final int SLOTS = 3;
    /** Menu data: progress ×1000, lit (0/1). */
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_LIT = 1;
    public static final int DATA_COUNT = 2;
    private static final int IDLE_GRACE = 10;
    private static final String KEY_BURNER = "burner";
    private static final String KEY_MACHINE = "machine";

    private final Burner burner = new Burner(POWER, Phase1Data.fuelValues());
    private final CraftingMachine machine = CraftingMachine.smelter(SPEED, POWER, Phase1Data.recipes());
    private final Slots slots = new Slots();
    private final IItemHandler itemHandler = new MachineItemHandler(slots);
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> (int) Math.round(machine.progress() * 1000);
                case DATA_LIT -> isWorking() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() { return DATA_COUNT; }
    };
    private int idleTicks;

    public StoneFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STONE_FURNACE.get(), pos, state);
    }

    public Burner burner() { return burner; }

    public CraftingMachine machine() { return machine; }

    /** Automated item access (capability of the controller and every part). */
    public IItemHandler itemHandler() { return itemHandler; }

    /** Smelting with fuel (synced through the block state). */
    public boolean isWorking() { return getBlockState().getValue(StoneFurnaceBlock.LIT); }

    // --- ticking -----------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, StoneFurnaceBlockEntity be) {
        be.tickServer(level);
    }

    private void tickServer(Level level) {
        long demand = machine.powerDemand();
        double satisfaction = demand > 0 ? burner.draw(demand) : 0;
        // Without fuel a smelt neither starts nor advances; an idle machine still ticks (drops carried progress).
        if (demand == 0 || satisfaction > 0) machine.tick(satisfaction);
        if (satisfaction > 0) setChanged();
        setLit(level, satisfaction > 0);
    }

    private void setLit(Level level, boolean active) {
        idleTicks = active ? 0 : Math.min(idleTicks + 1, IDLE_GRACE);
        boolean lit = active || isWorking() && idleTicks < IDLE_GRACE;
        if (lit != isWorking()) level.setBlock(worldPosition, getBlockState().setValue(StoneFurnaceBlock.LIT, lit), Block.UPDATE_CLIENTS);
    }

    // --- slots -------------------------------------------------------------------------------------

    /** Room for {@code id} in the fuel slot: one fuel type at a time. */
    private int fuelRoom(String id, boolean automated) {
        Map<String, Integer> fuel = burner.fuel();
        if (!fuel.isEmpty() && !fuel.containsKey(id)) return 0;
        return burner.fuelRoom(id, automated);
    }

    @Nullable
    private static String first(Map<String, Integer> items) {
        return items.isEmpty() ? null : items.keySet().iterator().next();
    }

    /** Fuel, input and output slots over the core state; see the class doc for the rules. */
    private final class Slots extends MachineContainer.View implements MachineItemHandler.Slots {
        @Override
        public int getContainerSize() { return SLOTS; }

        @Nullable
        @Override
        protected String itemIn(int slot) {
            return switch (slot) {
                case FUEL_SLOT -> first(burner.fuel());
                case INPUT_SLOT -> first(machine.inputs());
                case OUTPUT_SLOT -> first(machine.outputs());
                default -> null;
            };
        }

        @Override
        protected int countIn(int slot) {
            String id = itemIn(slot);
            if (id == null) return 0;
            return switch (slot) {
                case FUEL_SLOT -> burner.fuel().get(id);
                case INPUT_SLOT -> machine.inputs().get(id);
                default -> machine.outputs().get(id);
            };
        }

        @Override
        public int room(int slot, ItemStack stack) { return room(slot, stack, false); }

        @Override
        public int room(int slot, ItemStack stack, boolean automated) {
            if (!MachineItems.isPlain(stack)) return 0;
            return room(slot, MachineItems.id(stack), automated);
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            if (!MachineItems.isPlain(stack)) return false;
            String id = MachineItems.id(stack);
            return switch (slot) {
                case FUEL_SLOT -> Phase1Data.fuelValues().containsKey(id);
                case INPUT_SLOT -> Phase1Data.recipes().smeltingFor(id).isPresent();
                default -> false;
            };
        }

        int room(int slot, String id, boolean automated) {
            return switch (slot) {
                case FUEL_SLOT -> fuelRoom(id, automated);
                case INPUT_SLOT -> machine.inputRoom(id, automated);
                default -> 0;
            };
        }

        @Override
        public int insert(int slot, String id, int n, boolean automated) {
            int accepted = switch (slot) {
                case FUEL_SLOT -> burner.insertFuel(id, Math.min(n, fuelRoom(id, automated)), automated);
                case INPUT_SLOT -> machine.insertInput(id, n, automated);
                default -> 0;
            };
            if (accepted > 0) setChanged();
            return accepted;
        }

        @Override
        protected int insert(int slot, String id, int n) { return insert(slot, id, n, false); }

        @Override
        protected int extract(int slot, int n) {
            String id = itemIn(slot);
            if (id == null) return 0;
            int taken = switch (slot) {
                case FUEL_SLOT -> burner.extractFuel(id, n);
                case INPUT_SLOT -> machine.extractInput(id, n);
                default -> machine.extractOutput(id, n);
            };
            if (taken > 0) setChanged();
            return taken;
        }

        @Override
        public boolean isOutput(int slot) { return slot == OUTPUT_SLOT; }

        @Override
        public int extractOutput(int slot, int n, boolean simulate) {
            if (slot != OUTPUT_SLOT) return 0;
            return simulate ? Math.min(n, countIn(slot)) : extract(slot, n);
        }

        @Override
        protected boolean usableBy(Player player) {
            return !isRemoved() && Container.stillValidBlockEntity(StoneFurnaceBlockEntity.this, player);
        }

        @Override
        public void setChanged() { StoneFurnaceBlockEntity.this.setChanged(); }
    }

    // --- inserters -----------------------------------------------------------------------------------

    /** Offers the output. */
    @Override
    public PickupSource asSource() { return MachineEndpoints.outputs(machine, this::setChanged); }

    /** Fuel and smeltable input with the automated limits; stack keys with data components are refused. */
    @Override
    public DropTarget asTarget() {
        return new DropTarget() {
            @Override
            public boolean wants(String item) {
                return LaneItem.isPlain(item) && (slots.room(FUEL_SLOT, item, true) > 0 || slots.room(INPUT_SLOT, item, true) > 0);
            }

            @Override
            public boolean accept(String item) {
                if (!LaneItem.isPlain(item)) return false;
                int slot = slots.room(FUEL_SLOT, item, true) > 0 ? FUEL_SLOT : INPUT_SLOT;
                return slots.insert(slot, item, 1, true) == 1;
            }
        };
    }

    // --- GUI -----------------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() { return getBlockState().getBlock().getName(); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new StoneFurnaceMenu(id, inventory, slots, data);
    }

    // --- drops and persistence -----------------------------------------------------------------------

    /** Everything the furnace holds: fuel, input, output and the input of a smelt in progress. */
    @Override
    protected void dropContents(Level level, BlockPos pos) {
        Map<String, Integer> all = new LinkedHashMap<>(burner.fuel());
        burner.load(Map.of(), burner.remainingJ());
        MachineEndpoints.empty(machine).forEach((k, v) -> all.merge(k, v, Integer::sum));
        MachineItems.drop(level, pos, all);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_BURNER, CoreNbt.writeBurner(burner));
        tag.put(KEY_MACHINE, CoreNbt.writeMachine(machine));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_BURNER)) CoreNbt.loadBurnerInto(burner, tag.getCompound(KEY_BURNER));
        if (tag.contains(KEY_MACHINE)) CoreNbt.loadMachineInto(machine, tag.getCompound(KEY_MACHINE), Phase1Data.recipes());
    }
}
