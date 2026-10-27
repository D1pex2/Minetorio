package dev.minetorio.content.research;

import dev.minetorio.content.inserter.MachineEndpoint;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.content.power.ElectricConsumerHelper;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.Units;
import dev.minetorio.core.belt.LaneItem;
import dev.minetorio.core.inserter.DropTarget;
import dev.minetorio.core.inserter.PickupSource;
import dev.minetorio.core.research.LabLogic;
import dev.minetorio.core.research.Technology;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Lab: a {@link LabLogic} (speed 1, 60 kW while a unit runs) researching the world's current
 * technology ({@link ResearchSavedData}), powered through an {@link ElectricConsumerHelper}.
 * Without power nothing happens: no unit starts (no packs are taken) and a running unit does not
 * advance.
 *
 * <p><b>Packs.</b> Only automation and logistic science packs without data components enter.
 * Inserters ({@link #asTarget}) and the {@link #itemHandler()} capability insert as automated (up
 * to 2 of each pack); right-click with packs inserts by hand (up to 64). Nothing can be taken out;
 * breaking the lab drops the stored packs and those of the unit in progress.
 *
 * <p><b>Client sync.</b> Only {@link #isWorking()} (researched with power recently) is synced; the
 * client lights and turns the dome while it is set. It turns on at once and off after
 * {@value #IDLE_GRACE} idle ticks.
 */
public class LabBlockEntity extends MultiblockControllerBlockEntity implements MachineEndpoint {
    public static final double SPEED = 1.0;
    public static final long POWER = Units.kw(60);
    /** Item handler slots: one per pack type. */
    public static final List<String> PACKS = List.of("minetorio:automation_science_pack", "minetorio:logistic_science_pack");
    private static final int IDLE_GRACE = 10;
    private static final float DOME_DEGREES_PER_TICK = 3f;
    private static final String KEY_LAB = "lab";
    private static final String KEY_WORKING = "working";

    private final LabLogic lab = new LabLogic(SPEED, POWER);
    private final ElectricConsumerHelper power = new ElectricConsumerHelper(this, lab::powerDemand,
            () -> ElectricConsumerHelper.box(shape().footprint(worldPosition, facing())));
    private final IItemHandler itemHandler = new PackHandler();
    private boolean working;
    private int idleTicks;
    // client animation
    private float domeAngle;
    private float prevDomeAngle;

    public LabBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAB.get(), pos, state);
    }

    public LabLogic lab() { return lab; }

    /** Automated pack insertion (capability of the controller and every part); no extraction. */
    public IItemHandler itemHandler() { return itemHandler; }

    public Direction facing() { return getBlockState().getValue(MultiblockControllerBlock.FACING); }

    public MultiblockShape shape() { return ((MultiblockControllerBlock) getBlockState().getBlock()).shape(); }

    /** Last supplied power satisfaction (server). */
    public double satisfaction() { return power.satisfaction(); }

    /** Researched with power recently (synced to the client). */
    public boolean isWorking() { return working; }

    /** Whether the stack is a science pack the lab takes (no data components). */
    public static boolean isPack(ItemStack stack) {
        return MachineItems.isPlain(stack) && PACKS.contains(MachineItems.id(stack));
    }

    /** Inserts packs by hand (up to 64 of each stored); returns how many the stack should lose (not shrunk here). */
    public int insertManually(ItemStack stack) {
        if (!isPack(stack)) return 0;
        int n = lab.insertPack(MachineItems.id(stack), stack.getCount(), false);
        if (n > 0) setChanged();
        return n;
    }

    /** Action-bar status: stored packs and the current research. */
    public Component status() {
        Component packs = Component.translatable("minetorio.lab.packs",
                lab.packs().getOrDefault(PACKS.get(0), 0), lab.packs().getOrDefault(PACKS.get(1), 0));
        Optional<Technology> cur = level == null || level.getServer() == null ? Optional.empty()
                : ResearchSavedData.get(level.getServer()).state().current();
        Component research = cur.<Component>map(t -> Component.translatable("minetorio.lab.researching",
                        Component.translatable(ResearchSavedData.nameKey(t.id()))))
                .orElseGet(() -> Component.translatable("minetorio.lab.no_research", Component.keybind("key.minetorio.research")));
        return Component.empty().append(packs).append(" · ").append(research);
    }

    // --- ticking -----------------------------------------------------------------------------------

    void tickServer(Level level) {
        double satisfaction = power.satisfaction();
        boolean active = false;
        if (satisfaction > 0 && level.getServer() != null) {
            int packsBefore = packCount();
            ResearchSavedData.get(level.getServer()).tickLab(lab, satisfaction);
            active = lab.isWorking();
            if (active || packCount() != packsBefore) setChanged();
        }
        idleTicks = active ? 0 : Math.min(idleTicks + 1, IDLE_GRACE);
        boolean now = active || working && idleTicks < IDLE_GRACE;
        if (now != working) {
            working = now;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private int packCount() {
        int n = 0;
        for (int c : lab.packs().values()) n += c;
        return n;
    }

    void tickClient() {
        prevDomeAngle = domeAngle;
        if (working) domeAngle = (domeAngle + DOME_DEGREES_PER_TICK) % 360f;
        if (domeAngle < prevDomeAngle) prevDomeAngle -= 360f; // keep interpolation monotonic across the wrap
    }

    /** Client: dome rotation in degrees, interpolated between the last two ticks. */
    public float renderDomeAngle(float partialTick) {
        return prevDomeAngle + (domeAngle - prevDomeAngle) * partialTick;
    }

    // --- inserters and capability ----------------------------------------------------------------------

    /** A lab offers nothing. */
    @Override
    public PickupSource asSource() {
        return new PickupSource() {
            @Override
            public Optional<String> peek(Predicate<String> wanted) { return Optional.empty(); }

            @Override
            public boolean take(String item) { return false; }
        };
    }

    /** Science packs with the automated limit (2 of each); stack keys with data components are refused. */
    @Override
    public DropTarget asTarget() {
        return new DropTarget() {
            @Override
            public boolean wants(String item) {
                return LaneItem.isPlain(item) && lab.packRoom(item, true) > 0;
            }

            @Override
            public boolean accept(String item) {
                if (!wants(item) || lab.insertPack(item, 1, true) != 1) return false;
                setChanged();
                return true;
            }
        };
    }

    /** One slot per pack type ({@link #PACKS}); automated inserts only. */
    private final class PackHandler implements IItemHandler {
        @Override
        public int getSlots() { return PACKS.size(); }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= PACKS.size()) return ItemStack.EMPTY;
            String id = PACKS.get(slot);
            return MachineItems.stack(id, lab.packs().getOrDefault(id, 0));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !isItemValid(slot, stack)) return stack;
            String id = MachineItems.id(stack);
            int n = Math.min(stack.getCount(), lab.packRoom(id, true));
            if (n <= 0) return stack;
            if (!simulate) {
                n = lab.insertPack(id, n, true);
                setChanged();
            }
            return stack.copyWithCount(stack.getCount() - n);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }

        @Override
        public int getSlotLimit(int slot) { return 64; }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < PACKS.size() && isPack(stack) && PACKS.get(slot).equals(MachineItems.id(stack));
        }
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

    /** The stored packs and those held by the unit in progress. */
    @Override
    protected void dropContents(Level level, BlockPos pos) {
        Map<String, Integer> all = new LinkedHashMap<>(lab.packs());
        lab.unitPacks().forEach((k, v) -> all.merge(k, v, Integer::sum));
        MachineItems.drop(level, pos, all);
        lab.load(Map.of(), null, Map.of(), 0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_LAB, CoreNbt.writeLab(lab));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_LAB)) CoreNbt.loadLabInto(lab, tag.getCompound(KEY_LAB));
        if (tag.contains(KEY_WORKING)) working = tag.getBoolean(KEY_WORKING); // client update
    }

    /** Only the working flag (see class doc). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_WORKING, working);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
