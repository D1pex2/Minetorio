package dev.minetorio.content.robot;

import dev.minetorio.content.registry.ModMenus;
import dev.minetorio.core.robot.RobotStats;
import java.util.function.Predicate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Roboport GUI: the robot slots in one row, the player inventory, and robots/energy figures. */
public class RoboportMenu extends AbstractContainerMenu {
    public static final int SLOT_X = 8 + (9 - RobotStats.ROBOT_SLOTS) * 9;
    public static final int SLOT_Y = 18;
    public static final int INVENTORY_Y = 85;

    private final Container robots;
    private final ContainerData data;
    private final Predicate<Player> valid;
    /** The roboport (server side; null on the client): its logistic network's figures are sent while open. */
    @javax.annotation.Nullable
    private final net.minecraft.core.BlockPos pos;

    public RoboportMenu(int id, Inventory inventory, Container robots, ContainerData data, Predicate<Player> valid) {
        this(id, inventory, robots, data, valid, null);
    }

    public RoboportMenu(int id, Inventory inventory, Container robots, ContainerData data, Predicate<Player> valid,
                        @javax.annotation.Nullable net.minecraft.core.BlockPos pos) {
        super(ModMenus.ROBOPORT.get(), id);
        this.pos = pos;
        this.robots = robots;
        this.data = data;
        this.valid = valid;
        for (int i = 0; i < RobotStats.ROBOT_SLOTS; i++) {
            addSlot(new Slot(robots, i, SLOT_X + i * 18, SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) { return RoboportBlockEntity.isRobotItem(stack); }
            });
        }
        var main=dev.minetorio.content.character.CharacterInventory.main(inventory);
        for(int row=0;row<8;row++)for(int col=0;col<10;col++)addSlot(new Slot(main,row*10+col,210+col*18,32+row*18));
        addDataSlots(data);
    }

    public static RoboportMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        return new RoboportMenu(id, inventory, new SimpleContainer(RobotStats.ROBOT_SLOTS),
                new SimpleContainerData(RoboportBlockEntity.DATA_COUNT), p -> true);
    }

    /** The roboport's controller position (server side only). */
    @javax.annotation.Nullable
    public net.minecraft.core.BlockPos pos() { return pos; }

    public int available() { return data.get(RoboportBlockEntity.DATA_AVAILABLE); }

    public int out() { return data.get(RoboportBlockEntity.DATA_OUT); }

    /** Buffer in MJ. */
    public double energyMj() { return data.get(RoboportBlockEntity.DATA_ENERGY) / 10.0; }

    @Override
    public boolean stillValid(Player player) { return valid.test(player); }

    @Override public void clicked(int slot,int button,net.minecraft.world.inventory.ClickType type,Player player) {
        if(!dev.minetorio.content.map.RemoteView.blocksTransfer(player))super.clicked(slot,button,type,player);
    }
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if(index<0 || index>=slots.size() || dev.minetorio.content.map.RemoteView.blocksTransfer(player))return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int n = RobotStats.ROBOT_SLOTS;
        if (index < n) {
            if (!moveItemStackTo(stack, n, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!RoboportBlockEntity.isRobotItem(stack) || !moveItemStackTo(stack, 0, n, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
