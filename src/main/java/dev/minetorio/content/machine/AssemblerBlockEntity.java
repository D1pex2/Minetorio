package dev.minetorio.content.machine;

import dev.minetorio.content.alt.AltSource;
import dev.minetorio.core.alt.AltInfo;
import dev.minetorio.core.alt.AltRules;
import dev.minetorio.core.alt.AltSync;
import dev.minetorio.content.inserter.MachineEndpoint;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.content.power.ElectricConsumerHelper;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.content.research.RecipeLocks;
import dev.minetorio.core.Units;
import dev.minetorio.core.belt.LaneItem;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.inserter.DropTarget;
import dev.minetorio.core.inserter.PickupSource;
import dev.minetorio.core.machine.CraftingMachine;
import dev.minetorio.core.recipe.Ingredient;
import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.recipe.RecipeCategory;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
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
 * Assembling machine 1/2: a {@link CraftingMachine} (speed 0.5 / 0.75, 75 / 150 kW while it can
 * craft) powered through an {@link ElectricConsumerHelper}: it crafts at the satisfaction the grid
 * supplied at the end of the previous tick, and without power a craft neither starts nor advances.
 *
 * <p><b>Recipe.</b> Chosen in the GUI ({@link AssemblerMenu}); the client sends a
 * {@code SelectRecipePayload} handled by {@link #select}, which checks the player's distance, that
 * the recipe is a CRAFTING recipe and that it is unlocked ({@link RecipeLocks}). Changing it gives
 * the stored inputs, the inputs of a craft in progress and the outputs back to the player.
 *
 * <p><b>Items.</b> Slots: one per recipe input (up to {@link #MAX_INPUTS}), then the output
 * ({@link #OUTPUT_SLOT}). Rules as for the furnace: stacks with data components are refused;
 * automated access (inserters, the {@link #itemHandler()} capability) inserts inputs up to 2
 * crafts and extracts only the output; the player may insert up to a stack and take from every slot.
 *
 * <p><b>Client sync.</b> Besides the alt-mode info ({@link dev.minetorio.content.alt.AltSource}, on change), only {@link #isWorking()} (crafted with power recently) is synced; the
 * client spins the gear while it is set. It turns on at once and off after {@value #IDLE_GRACE}
 * idle ticks.
 */
public class AssemblerBlockEntity extends MultiblockControllerBlockEntity implements MachineEndpoint, MenuProvider, AltSource {
    public static final double SPEED_1 = 0.5;
    public static final long POWER_1 = Units.kw(75);
    public static final double SPEED_2 = 0.75;
    public static final long POWER_2 = Units.kw(150);
    /** Most inputs of any crafting recipe. */
    public static final int MAX_INPUTS = Phase1Data.recipes().all().stream()
            .filter(r -> r.category() == RecipeCategory.CRAFTING).mapToInt(r -> r.inputs().size()).max().orElse(1);
    public static final int OUTPUT_SLOT = MAX_INPUTS;
    public static final int SLOTS = MAX_INPUTS + 1;
    /** Farthest a player may be from the machine to choose its recipe, in blocks. */
    public static final double SELECT_DISTANCE = 8.0;
    /** Menu data: recipe index ({@link AssemblerMenu#RECIPES}, -1 none), progress ×1000, satisfaction ×1000, working. */
    public static final int DATA_RECIPE = 0;
    public static final int DATA_PROGRESS = 1;
    public static final int DATA_POWER = 2;
    public static final int DATA_WORKING = 3;
    public static final int DATA_COUNT = 4;
    private static final int IDLE_GRACE = 10;
    /**
     * Work animation: Factorio plays the assembler's 32 frames at 60 per second times the crafting speed,
     * i.e. 3 × speed frames per Minecraft tick; one loop is one animation cycle (phase 0..1).
     */
    private static final float ANIM_FRAMES = 32f;
    private static final String KEY_MACHINE = "machine";
    private static final String KEY_WORKING = "working";

    private final CraftingMachine machine;
    private final ElectricConsumerHelper power = new ElectricConsumerHelper(this, this::demand,
            () -> ElectricConsumerHelper.box(shape().footprint(worldPosition, facing())));
    private final Slots slots = new Slots();
    private final IItemHandler itemHandler = new MachineItemHandler(slots);
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_RECIPE -> AssemblerMenu.indexOf(machine.recipe());
                case DATA_PROGRESS -> (int) Math.round(machine.progress() * 1000);
                case DATA_POWER -> (int) Math.round(power.satisfaction() * 1000);
                case DATA_WORKING -> working ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() { return DATA_COUNT; }
    };
    private final AltSync altSync = new AltSync();
    private AltInfo clientAlt = AltInfo.NONE;
    private boolean working;
    private int idleTicks;
    // client animation: phase of the work loop (0..1), frozen while idle
    private final float phasePerTick;
    private float animPhase;
    private float prevAnimPhase;

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLING_MACHINE.get(), pos, state);
        AssemblerBlock block = (AssemblerBlock) state.getBlock();
        this.machine = new CraftingMachine(block.speed(), block.power());
        this.phasePerTick = (float) (3 * block.speed() / ANIM_FRAMES);
    }

    public CraftingMachine machine() { return machine; }

    /** Automated item access (capability of the controller and every part). */
    public IItemHandler itemHandler() { return itemHandler; }

    public Direction facing() { return getBlockState().getValue(MultiblockControllerBlock.FACING); }

    public MultiblockShape shape() { return ((MultiblockControllerBlock) getBlockState().getBlock()).shape(); }

    /** Crafted with power recently (synced to the client). */
    public boolean isWorking() { return working; }

    /** Power wanted this tick: the machine's while it can craft, else nothing. */
    public long demand() { return machine.powerDemand(); }

    /** Satisfaction last supplied by the grid. */
    public double satisfaction() { return power.satisfaction(); }

    /**
     * Sets the recipe (null clears it) and returns what the machine gave up: stored inputs, the
     * inputs of a craft in progress and the outputs. Choosing the current recipe changes nothing.
     */
    public Map<String, Integer> setRecipe(@Nullable Recipe recipe) {
        if (recipe == machine.recipe()) return Map.of();
        Map<String, Integer> returned = MachineEndpoints.empty(machine);
        machine.setRecipe(recipe);
        setChanged();
        return returned;
    }

    // --- recipe selection (server) -----------------------------------------------------------------

    public enum SelectResult { OK, SPECTATOR, NOT_ASSEMBLER, TOO_FAR, MENU_NOT_OPEN, UNKNOWN_RECIPE, NOT_CRAFTING, LOCKED }

    /**
     * Handles a player's recipe choice for the assembler at {@code pos}: the player must not be a
     * spectator and must have this assembler's GUI open (still valid), the assembler must be within
     * {@value #SELECT_DISTANCE} blocks, and the recipe a known, unlocked CRAFTING recipe. On success what the machine gave up goes into the player's inventory (the
     * rest is dropped at the player).
     */
    public static SelectResult select(ServerPlayer player, BlockPos pos, String recipeId) {
        if (player.isSpectator()) return SelectResult.SPECTATOR;
        Level level = player.level();
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof AssemblerBlockEntity be)) return SelectResult.NOT_ASSEMBLER;
        double reach = SELECT_DISTANCE * SELECT_DISTANCE;
        if (ElectricConsumerHelper.box(be.shape().footprint(pos, be.facing())).distanceToSqr(player.position()) > reach) {
            return SelectResult.TOO_FAR;
        }
        if (!(player.containerMenu instanceof AssemblerMenu menu) || !menu.pos().equals(pos) || !menu.stillValid(player)) {
            return SelectResult.MENU_NOT_OPEN;
        }
        Optional<Recipe> recipe = Phase1Data.recipes().get(recipeId);
        if (recipe.isEmpty()) return SelectResult.UNKNOWN_RECIPE;
        if (recipe.get().category() != RecipeCategory.CRAFTING) return SelectResult.NOT_CRAFTING;
        if (!RecipeLocks.isUnlocked(player.server, recipeId)) return SelectResult.LOCKED;
        MachineItems.give(player, be.setRecipe(recipe.get()));
        return SelectResult.OK;
    }

    // --- ticking -----------------------------------------------------------------------------------

    void tickServer(Level level) {
        boolean runnable = machine.powerDemand() > 0;
        double satisfaction = power.satisfaction();
        // Without power a craft neither starts nor advances; an idle machine still ticks (drops carried progress).
        if (!runnable || satisfaction > 0) machine.tick(satisfaction);
        boolean active = runnable && satisfaction > 0;
        if (active) setChanged();
        idleTicks = active ? 0 : Math.min(idleTicks + 1, IDLE_GRACE);
        boolean now = active || working && idleTicks < IDLE_GRACE;
        if (now != working) {
            working = now;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        AltSource.tickAlt(this, altSync);
    }

    /** Alt mode: the recipe's product; no recipe, no or low power, output full. */
    @Override
    public AltInfo computeAlt() {
        Recipe r = machine.recipe();
        String product = r == null || r.outputs().isEmpty() ? null : r.outputs().get(0).item();
        return AltRules.assembler(product, machine.isOutputFull(), AltRules.electric(power.satisfaction()));
    }

    @Override
    public AltInfo altInfo() { return clientAlt; }

    void tickClient() {
        prevAnimPhase = animPhase;
        if (working) {
            animPhase += phasePerTick;
            if (animPhase >= 1f) { // wrap, keeping the interpolation monotonic
                animPhase -= 1f;
                prevAnimPhase -= 1f;
            }
        }
    }

    /** Client: phase (0..1) of the work animation loop, interpolated between the last two ticks; just below 0 at a wrap. */
    public float renderAnimPhase(float partialTick) {
        return prevAnimPhase + (animPhase - prevAnimPhase) * partialTick;
    }

    // --- slots -------------------------------------------------------------------------------------

    /** Input of {@code slot} under the current recipe, or null. */
    @Nullable
    private Ingredient inputOf(int slot) {
        Recipe r = machine.recipe();
        return r == null || slot < 0 || slot >= r.inputs().size() || slot >= MAX_INPUTS ? null : r.inputs().get(slot);
    }

    @Nullable
    private String outputItem() {
        Recipe r = machine.recipe();
        if (r != null && !r.outputs().isEmpty()) return r.outputs().get(0).item();
        Map<String, Integer> out = machine.outputs();
        return out.isEmpty() ? null : out.keySet().iterator().next();
    }

    /** Input slots follow the recipe's inputs; the output slot shows its (first) output. */
    private final class Slots extends MachineContainer.View implements MachineItemHandler.Slots {
        @Override
        public int getContainerSize() { return SLOTS; }

        @Nullable
        @Override
        protected String itemIn(int slot) {
            String id = slot == OUTPUT_SLOT ? outputItem() : inputOf(slot) == null ? null : inputOf(slot).item();
            return id != null && countIn(slot, id) > 0 ? id : null;
        }

        private int countIn(int slot, String id) {
            return (slot == OUTPUT_SLOT ? machine.outputs() : machine.inputs()).getOrDefault(id, 0);
        }

        @Override
        protected int countIn(int slot) {
            String id = itemIn(slot);
            return id == null ? 0 : countIn(slot, id);
        }

        @Override
        public int room(int slot, ItemStack stack) { return room(slot, stack, false); }

        @Override
        public int room(int slot, ItemStack stack, boolean automated) {
            return MachineItems.isPlain(stack) ? room(slot, MachineItems.id(stack), automated) : 0;
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            Ingredient in = inputOf(slot);
            return in != null && MachineItems.isPlain(stack) && in.item().equals(MachineItems.id(stack));
        }

        int room(int slot, String id, boolean automated) {
            Ingredient in = inputOf(slot);
            return in != null && in.item().equals(id) ? machine.inputRoom(id, automated) : 0;
        }

        @Override
        public int insert(int slot, String id, int n, boolean automated) {
            if (room(slot, id, automated) <= 0) return 0;
            int accepted = machine.insertInput(id, n, automated);
            if (accepted > 0) setChanged();
            return accepted;
        }

        @Override
        protected int insert(int slot, String id, int n) { return insert(slot, id, n, false); }

        @Override
        protected int extract(int slot, int n) {
            String id = itemIn(slot);
            if (id == null) return 0;
            int taken = slot == OUTPUT_SLOT ? machine.extractOutput(id, n) : machine.extractInput(id, n);
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
            return !isRemoved() && Container.stillValidBlockEntity(AssemblerBlockEntity.this, player);
        }

        @Override
        public void setChanged() { AssemblerBlockEntity.this.setChanged(); }
    }

    // --- inserters -----------------------------------------------------------------------------------

    /** Offers the outputs. */
    @Override
    public PickupSource asSource() { return MachineEndpoints.outputs(machine, this::setChanged); }

    /** Recipe inputs with the automated limit (2 crafts); stack keys with data components are refused. */
    @Override
    public DropTarget asTarget() {
        return new DropTarget() {
            @Override
            public boolean wants(String item) {
                return LaneItem.isPlain(item) && machine.inputRoom(item, true) > 0;
            }

            @Override
            public boolean accept(String item) {
                if (!wants(item) || machine.insertInput(item, 1, true) != 1) return false;
                setChanged();
                return true;
            }
        };
    }

    // --- GUI -----------------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() { return getBlockState().getBlock().getName(); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AssemblerMenu(id, inventory, slots, data, worldPosition);
    }

    // --- power, drops, persistence and sync ----------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        power.onLoad();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        power.onRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        power.onRemoved();
    }

    /** Stored inputs, the inputs of a craft in progress and the outputs. */
    @Override
    protected void dropContents(Level level, BlockPos pos) {
        MachineItems.drop(level, pos, MachineEndpoints.empty(machine));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_MACHINE, CoreNbt.writeMachine(machine));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_MACHINE)) CoreNbt.loadMachineInto(machine, tag.getCompound(KEY_MACHINE), Phase1Data.recipes());
        if (tag.contains(KEY_WORKING)) working = tag.getBoolean(KEY_WORKING); // client update
        clientAlt = AltSource.read(tag, clientAlt);
    }

    /** The working flag and the alt-mode info. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_WORKING, working);
        AltSource.write(tag, this, altSync);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
