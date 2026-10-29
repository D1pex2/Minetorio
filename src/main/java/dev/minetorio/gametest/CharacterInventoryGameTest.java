package dev.minetorio.gametest;

import static dev.minetorio.gametest.MachineGameTest.player;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class CharacterInventoryGameTest {
    @GameTest(template="empty16")
    public static void pickupFillsFactorioStackAboveNinetyNine(GameTestHelper h) {
        var p=player(h,new BlockPos(3,1,3));var inv=p.getInventory();inv.clearContent();
        var main=dev.minetorio.content.character.CharacterInventory.main(inv);
        for(int i=0;i<80;i++)main.setItem(i,new ItemStack(Items.STONE,64));
        var cable=dev.minetorio.content.registry.ModItems.COPPER_CABLE.get();
        main.setItem(79,new ItemStack(cable,199));ItemStack last=new ItemStack(cable);
        h.assertTrue(inv.add(last) && last.isEmpty(),"last cable cannot enter extra stack above99");
        h.assertTrue(main.getItem(79).getCount()==200,"cable stack did not reach200");
        h.succeed();
    }
    @GameTest(template = "empty16")
    public static void pickupBeyondVanillaAndSave(GameTestHelper h) {
        var p = player(h, new BlockPos(3, 1, 3));
        var inv = p.getInventory();
        inv.clearContent();
        for (int i = 0; i < 36; i++) inv.items.set(i, new ItemStack(Items.STONE, 64));
        ItemStack input = new ItemStack(Items.DIAMOND, 3);
        h.assertTrue(inv.add(input) && input.isEmpty(), "pickup beyond vanilla inventory failed");
        ListTag saved = inv.save(new ListTag());
        inv.clearContent();
        inv.load(saved);
        h.assertTrue(inv.countItem(Items.DIAMOND) == 3, "extra inventory not saved or restored");
        h.succeed();
    }
    @GameTest(template = "empty16")
    public static void damagedPickupAndVanillaSlotsStayDistinct(GameTestHelper h) {
        var p = player(h, new BlockPos(3, 1, 3));
        var inv = p.getInventory();
        inv.clearContent();
        for (int i = 0; i < 36; i++) inv.items.set(i, new ItemStack(Items.STONE, 64));
        inv.offhand.set(0, new ItemStack(Items.TORCH, 4));
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        pick.setDamageValue(1);
        h.assertTrue(inv.add(pick) && pick.isEmpty(), "damaged item did not fit in extra slot");
        h.assertTrue(inv.getItem(40).is(Items.TORCH), "offhand was overwritten");
        h.assertTrue(inv.countItem(Items.IRON_PICKAXE) == 1, "damaged item lost");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void extraSlotsBelongToDefaultMenuAndCrafting(GameTestHelper h) {
        var p = player(h, new BlockPos(3, 1, 3));
        var inv = p.getInventory();
        inv.clearContent();
        var main = dev.minetorio.content.character.CharacterInventory.main(inv);
        main.setItem(79, new ItemStack(Items.IRON_INGOT, 7));
        h.assertTrue(!inv.isEmpty(), "inventory claims to be empty with an extra stack");
        h.assertTrue(p.inventoryMenu instanceof dev.minetorio.content.character.CharacterMenu, "wrong default menu");
        int slot = dev.minetorio.content.character.CharacterMenu.menuIndex(79);
        h.assertTrue(p.inventoryMenu.getSlot(slot).getItem().getCount() == 7, "default menu did not expose extra stack");
        var adapter = new dev.minetorio.content.handcraft.PlayerInventoryAdapter(p);
        h.assertTrue(adapter.count("minecraft:iron_ingot") == 7, "crafting did not count extra stack");
        h.assertTrue(adapter.remove("minecraft:iron_ingot", 4) == 4 && main.getItem(79).getCount() == 3,
                "crafting did not consume extra stack");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void shortcutsRememberAndMoveRealStack(GameTestHelper h) {
        var p = player(h, new BlockPos(3, 1, 3));
        var inv = p.getInventory();
        inv.clearContent();
        var main = dev.minetorio.content.character.CharacterInventory.main(inv);
        var menu = (dev.minetorio.content.character.CharacterMenu) p.inventoryMenu;
        var shortcuts = (dev.minetorio.content.character.CharacterShortcuts) p;
        main.setItem(79, new ItemStack(Items.DIAMOND, 7));
        menu.setCarried(main.removeItemNoUpdate(79));
        h.assertTrue(menu.clickMenuButton(p, 1009), "shortcut binding failed");
        main.setItem(79, menu.getCarried());
        menu.setCarried(ItemStack.EMPTY);
        h.assertTrue(menu.clickMenuButton(p, 2009), "tenth shortcut failed");
        h.assertTrue(inv.getSelected().is(Items.DIAMOND) && inv.getSelected().getCount() == 7, "shortcut did not move real stack");
        h.assertTrue(main.getItem(79).isEmpty() && inv.countItem(Items.DIAMOND) == 7, "shortcut duplicated items");
        inv.removeFromSelected(true);
        h.assertTrue(shortcuts.shortcut(9) == net.minecraft.world.item.Item.getId(Items.DIAMOND), "depleted shortcut was forgotten");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void shortcutBindingAndSelectionWithFullInventory(GameTestHelper h) {
        var p = player(h, new BlockPos(3, 1, 3));
        var inv = p.getInventory();
        inv.clearContent();
        var main = dev.minetorio.content.character.CharacterInventory.main(inv);
        for (int i = 0; i < 80; i++) main.setItem(i, new ItemStack(Items.STONE, 50));
        var menu = (dev.minetorio.content.character.CharacterMenu) p.inventoryMenu;
        var state = (dev.minetorio.content.character.CharacterShortcuts) p;
        main.setItem(79, new ItemStack(Items.DIAMOND, 7));
        menu.setCarried(main.removeItemNoUpdate(79));
        h.assertTrue(menu.clickMenuButton(p, 1099), "could not bind last shortcut");
        h.assertTrue(menu.getCarried().getCount() == 7, "binding consumed carried item");
        main.setItem(79, menu.getCarried());
        menu.setCarried(ItemStack.EMPTY);
        h.assertTrue(menu.clickMenuButton(p, 2099), "could not select last shortcut");
        h.assertTrue(inv.getSelected().is(Items.DIAMOND) && inv.getSelected().getCount() == 7, "extra stack not selected");
        h.assertTrue(main.getItem(79).is(Items.STONE) && inv.countItem(Items.STONE) == 79 * 50, "full inventory swap lost items");
        h.assertTrue(state.shortcut(99) == net.minecraft.world.item.Item.getId(Items.DIAMOND), "binding changed");
        h.assertTrue(state.selectedRow() == 9 && state.selectedColumn() == 9, "tenth virtual selection not recorded");
        h.assertTrue(menu.clickMenuButton(p, 2088), "empty shortcut rejected");
        h.assertTrue(state.selectedRow() == 8 && state.selectedColumn() == 8 && state.emptyHand(), "empty virtual selection not recorded");
        h.assertTrue(inv.countItem(Items.STONE) == 79 * 50 && inv.countItem(Items.DIAMOND) == 7, "empty selection lost items");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void lockedToolAndTrashAreSeparate(GameTestHelper h) {
        var p = player(h, new BlockPos(3, 1, 3));
        var inv = p.getInventory();
        inv.clearContent();
        inv.tick();
        var tool = inv.getItem(dev.minetorio.content.character.CharacterInventory.TOOL_SLOT);
        h.assertTrue(tool.is(dev.minetorio.content.registry.BaseMaterials.ENGINEER_TOOL.get()), "missing tool");
        var menu = (dev.minetorio.content.character.CharacterMenu) p.inventoryMenu;
        h.assertTrue(!menu.getSlot(90).mayPickup(p) && !menu.getSlot(90).mayPlace(new ItemStack(Items.DIAMOND)), "tool not locked");
        inv.setItem(dev.minetorio.content.character.CharacterInventory.TRASH_SLOT, new ItemStack(Items.DIAMOND, 3));
        var saved = inv.save(new ListTag());
        inv.clearContent();
        inv.load(saved);
        h.assertTrue(inv.getItem(dev.minetorio.content.character.CharacterInventory.TRASH_SLOT).getCount() == 3, "trash lost during save/load");
        h.assertTrue(dev.minetorio.content.logistics.PlayerItems.take(p, Items.DIAMOND, 2) == 2, "robots cannot take trash");
        h.assertTrue(inv.getItem(dev.minetorio.content.character.CharacterInventory.TRASH_SLOT).getCount() == 1, "wrong trash remainder");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void deathDropKeepsLockedToolAndDropsTrash(GameTestHelper h) {
        var p = player(h, new BlockPos(3, 1, 3));
        var inv = p.getInventory();
        inv.clearContent();
        inv.tick();
        inv.setItem(dev.minetorio.content.character.CharacterInventory.TRASH_SLOT, new ItemStack(Items.DIAMOND, 3));
        var main = dev.minetorio.content.character.CharacterInventory.main(inv);
        main.setItem(79, new ItemStack(Items.EMERALD, 5));
        inv.dropAll();
        h.assertTrue(inv.getItem(dev.minetorio.content.character.CharacterInventory.TOOL_SLOT).is(dev.minetorio.content.registry.BaseMaterials.ENGINEER_TOOL.get()), "tool dropped");
        h.assertTrue(inv.getItem(dev.minetorio.content.character.CharacterInventory.TRASH_SLOT).isEmpty() && main.getItem(79).isEmpty(), "inventory not drained");
        h.assertTrue(dev.minetorio.gametest.BeltGameTest.dropped(h, Items.DIAMOND) == 3, "trash not dropped");
        h.assertTrue(dev.minetorio.gametest.BeltGameTest.dropped(h, Items.EMERALD) == 5, "extra inventory not dropped");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void personalLogisticsUsesAllMainSlots(GameTestHelper h) {
        var p = player(h, new BlockPos(3, 1, 3));
        var main = dev.minetorio.content.character.CharacterInventory.main(p.getInventory());
        main.clearContent();
        main.setItem(79, new ItemStack(Items.EMERALD, 6));
        var menu = new dev.minetorio.content.logistics.PersonalLogisticsMenu(1, p.getInventory());
        h.assertTrue(menu.slots.size() == 80 && menu.getSlot(79).getItem().getCount() == 6, "personal logistics does not expose full main inventory");
        menu.getSlot(79).remove(2);
        h.assertTrue(main.getItem(79).getCount() == 4, "personal logistics slot is not the real inventory");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void pickBlockFindsExtraStackWithoutDuplicating(GameTestHelper h) {
        var p = player(h, new BlockPos(3, 1, 3));
        var inv = p.getInventory();
        inv.clearContent();
        var main = dev.minetorio.content.character.CharacterInventory.main(inv);
        main.setItem(79, new ItemStack(Items.DIAMOND, 4));
        int index = inv.findSlotMatchingItem(new ItemStack(Items.DIAMOND));
        h.assertTrue(index == 84, "pick block cannot locate the extra stack");
        inv.pickSlot(index);
        h.assertTrue(inv.getSelected().is(Items.DIAMOND) && inv.getSelected().getCount() == 4, "pick block did not move stack");
        h.assertTrue(main.getItem(79).isEmpty() && inv.countItem(Items.DIAMOND) == 4, "pick block duplicated stack");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void lockedToolRejectsCreativeCloneAndThrow(GameTestHelper h) {
        var p = player(h, new BlockPos(3, 1, 3));
        p.getInventory().clearContent();
        p.getInventory().tick();
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var menu = (dev.minetorio.content.character.CharacterMenu) p.inventoryMenu;
        menu.clicked(90, 2, net.minecraft.world.inventory.ClickType.CLONE, p);
        h.assertTrue(menu.getCarried().isEmpty(), "locked tool could be cloned");
        menu.clicked(90, 1, net.minecraft.world.inventory.ClickType.THROW, p);
        h.assertTrue(menu.getSlot(90).hasItem(), "locked tool could be thrown");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void depletedShortcutWithFullInventoryKeepsEveryItem(GameTestHelper h) {
        var p = player(h, new BlockPos(3, 1, 3));
        var inv = p.getInventory();
        inv.clearContent();
        var main = dev.minetorio.content.character.CharacterInventory.main(inv);
        for (int i = 0; i < 80; i++) main.setItem(i, new ItemStack(Items.STONE, 64));
        var state = (dev.minetorio.content.character.CharacterShortcuts) p;
        state.shortcut(9, net.minecraft.world.item.Item.getId(Items.DIAMOND));
        int selected = inv.selected;
        var menu = (dev.minetorio.content.character.CharacterMenu) p.inventoryMenu;
        h.assertTrue(menu.clickMenuButton(p, 2009), "full inventory refused missing item");
        h.assertTrue(inv.getSelected().isEmpty(), "depleted shortcut exposed unrelated item");
        h.assertTrue(inv.removeFromSelected(true).isEmpty(), "drop removed an unrelated item");
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        h.assertTrue(inv.selected == selected && inv.countItem(Items.STONE) == 80 * 64, "selection lost inventory contents");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void pickPacketRejectsHiddenAndInvalidSlots(GameTestHelper h) {
        var p = player(h, new BlockPos(3, 1, 3));
        var inv = p.getInventory();
        inv.clearContent();
        inv.tick();
        for (int index : new int[]{-1, 36, 40, 85, 86, 9999}) {
            p.connection.handlePickItem(new net.minecraft.network.protocol.game.ServerboundPickItemPacket(index));
        }
        h.assertTrue(inv.getItem(dev.minetorio.content.character.CharacterInventory.TOOL_SLOT).is(dev.minetorio.content.registry.BaseMaterials.ENGINEER_TOOL.get()), "invalid packet moved locked tool");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void emptyShortcutCannotOverwriteFullInventoryWithPlanner(GameTestHelper h) {
        var p = player(h, new BlockPos(3, 1, 3));
        var inv = p.getInventory();
        inv.clearContent();
        var main = dev.minetorio.content.character.CharacterInventory.main(inv);
        for (int i = 0; i < 80; i++) main.setItem(i, new ItemStack(Items.STONE, 64));
        var shortcuts = (dev.minetorio.content.character.CharacterShortcuts) p;
        shortcuts.shortcut(9, net.minecraft.world.item.Item.getId(Items.DIAMOND));
        ((dev.minetorio.content.character.CharacterMenu) p.inventoryMenu).clickMenuButton(p, 2009);
        dev.minetorio.content.robot.Planners.give(p, dev.minetorio.content.robot.Planners.Kind.DECONSTRUCTION);
        h.assertTrue(inv.countItem(Items.STONE) == 80 * 64, "planner overwrote suppressed hand");
        var result = dev.minetorio.content.blueprint.BlueprintCapture.give(p);
        h.assertTrue(result == dev.minetorio.content.blueprint.BlueprintCapture.GiveResult.NO_ROOM,
                "blueprint did not refuse a physically full inventory");
        h.assertTrue(inv.countItem(Items.STONE) == 80 * 64, "blueprint overwrote suppressed hand");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void shortcutCycleStaysWithinVisibleRows(GameTestHelper h) {
        var p = player(h, new BlockPos(3, 1, 3));
        var state = (dev.minetorio.content.character.CharacterShortcuts) p;
        var menu = (dev.minetorio.content.character.CharacterMenu) p.inventoryMenu;
        int saved = net.minecraft.world.item.Item.getId(Items.DIAMOND);
        state.shortcut(99, saved);
        for (int rows : new int[]{2, 3, 4}) {
            h.assertTrue(menu.clickMenuButton(p, 3100 + rows), "could not configure rows");
            state.selectedRow(0);
            h.assertTrue(state.displayedRow(0) == 0 && state.displayedRow(1) == rows - 1,
                    "display used a hidden row when " + rows + " rows enabled");
            for (int expected = 1; expected < rows; expected++) {
                menu.clickMenuButton(p, 3000);
                h.assertTrue(state.selectedRow() == expected, "wrong next row for " + rows + ": " + state.selectedRow());
            }
            menu.clickMenuButton(p, 3000);
            h.assertTrue(state.selectedRow() == 0, "cycle did not wrap at " + rows + " rows");
        }
        state.selectedRow(3);
        menu.clickMenuButton(p, 3102);
        h.assertTrue(state.selectedRow() < 2, "reduced rows left selection outside visible range");
        h.assertTrue(state.shortcut(99) == saved, "hidden binding was lost");
        var tag = new net.minecraft.nbt.CompoundTag();
        p.addAdditionalSaveData(tag);
        tag.getCompound("MinetorioShortcuts").putInt("row", 9);
        p.readAdditionalSaveData(tag);
        h.assertTrue(state.selectedRow() == 1, "saved selection was not normalized to visible rows");
        h.assertTrue(state.shortcut(99) == saved, "hidden binding was lost on load");
        h.succeed();
    }

}
