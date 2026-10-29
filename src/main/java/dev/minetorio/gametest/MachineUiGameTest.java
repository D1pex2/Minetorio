package dev.minetorio.gametest;

import dev.minetorio.content.machine.BurnerDrillBlockEntity;
import dev.minetorio.content.machineui.MachineUi;
import dev.minetorio.content.machineui.MachineStatusMenu;
import dev.minetorio.content.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class MachineUiGameTest {
    @GameTest(template="empty16",timeoutTicks=20)
    public static void drillMenuUsesLiveFuelAndOutput(GameTestHelper h) {
        BlockPos at=new BlockPos(2,1,2);
        BeltGameTest.place(h,ModItems.BURNER_MINING_DRILL.get(),at,Direction.EAST);
        var drill=(BurnerDrillBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(at));
        var player=MachineGameTest.player(h,at);
        var menu=MachineUi.menu(1,player.getInventory(),drill);
        h.assertTrue(menu.slots.size()==82,"two machine slots plus eighty unique inventory slots");
        player.getInventory().setItem(0,new ItemStack(dev.minetorio.content.registry.BaseMaterials.COAL.get(),12));
        menu.quickMoveStack(player,2);
        h.assertTrue(drill.burner().fuel().getOrDefault("minetorio:coal",0)==12,"GUI inserts directly into live burner");
        h.assertTrue(player.getInventory().getItem(0).isEmpty(),"moved coal has no duplicate");
        menu.clicked(0,0,ClickType.PICKUP,player);
        h.assertTrue(menu.getCarried().getCount()==12 && drill.burner().fuel().isEmpty(),"GUI extracts live fuel");
        menu.clicked(2,0,ClickType.PICKUP,player);
        h.assertTrue(menu.getCarried().isEmpty() && player.getInventory().getItem(0).getCount()==12,"fuel is returned without loss");
        drill.output().hold("minetorio:iron_ore");
        menu.quickMoveStack(player,1);
        h.assertTrue(drill.output().isEmpty(),"GUI output extraction debits live buffer");
        h.assertTrue(MachineGameTest.inInventory(player,ModItems.IRON_ORE.get())==1,"one ore returned to inventory");
        h.succeed();
    }
    @GameTest(template="empty16",timeoutTicks=20)
    public static void labMenuUsesStoredScience(GameTestHelper h) {
        BlockPos at=new BlockPos(2,1,2);
        BeltGameTest.place(h,ModItems.LAB.get(),at,Direction.EAST);
        var lab=(dev.minetorio.content.research.LabBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(at));
        var player=MachineGameTest.player(h,at);
        var menu=MachineUi.menu(3,player.getInventory(),lab);
        player.getInventory().setItem(0,new ItemStack(ModItems.AUTOMATION_SCIENCE_PACK.get(),16));
        menu.quickMoveStack(player,dev.minetorio.content.research.LabBlockEntity.PACKS.size());
        h.assertTrue(lab.lab().packs().getOrDefault("minetorio:automation_science_pack",0)==16,"manual science GUI bypasses automated two-pack cap");
        menu.quickMoveStack(player,0);
        h.assertTrue(lab.lab().packs().isEmpty(),"science removed from actual lab");
        h.assertTrue(MachineGameTest.inInventory(player,ModItems.AUTOMATION_SCIENCE_PACK.get())==16,"science returned without loss");
        h.succeed();
    }
    @GameTest(template="empty16",timeoutTicks=20)
    public static void removedMachineInvalidatesMenu(GameTestHelper h) {
        BlockPos at=new BlockPos(2,1,2);
        BeltGameTest.place(h,ModItems.BURNER_MINING_DRILL.get(),at,Direction.EAST);
        var drill=(BurnerDrillBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(at));
        var player=MachineGameTest.player(h,at);
        var menu=MachineUi.menu(2,player.getInventory(),drill);
        h.assertTrue(menu.stillValid(player),"nearby live machine valid");
        h.getLevel().removeBlock(h.absolutePos(at),false);
        h.assertFalse(menu.stillValid(player),"removed machine closes GUI");
        h.succeed();
    }
}
