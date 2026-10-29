package dev.minetorio.gametest;

import dev.minetorio.content.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class NauvisStorageGameTest {
    @GameTest(template = "empty16")
    public static void basicChestsHaveSixteenAndThirtyTwoRealAutomationSlots(GameTestHelper h) {
        checkChest(h, "wooden_chest", 16, new BlockPos(3, 1, 3));
        checkChest(h, "iron_chest", 32, new BlockPos(6, 1, 3));
        h.succeed();
    }

    private static void checkChest(GameTestHelper h, String name, int slots, BlockPos relative) {
        var id = ResourceLocation.fromNamespaceAndPath("minetorio", name);
        h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "missing basic chest " + name);
        h.setBlock(relative, BuiltInRegistries.BLOCK.get(id));
        var pos = h.absolutePos(relative);
        var handler = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        h.assertTrue(handler != null && handler.getSlots() == slots, name + " capacity");
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        var be = (dev.minetorio.content.world.BasicChestBlockEntity) h.getLevel().getBlockEntity(pos);
        var menu = new dev.minetorio.content.world.BasicChestMenu(1, player.getInventory(), pos, be.inventory());
        h.assertTrue(menu.slots.size() == slots + 80, name + " does not expose all engineer slots");
        ItemStack source = new ItemStack(ModItems.ELECTRONIC_CIRCUIT.get(), 200);
        ItemStack remainder = handler.insertItem(slots - 1, source, false);
        h.assertTrue(remainder.isEmpty() && handler.getStackInSlot(slots - 1).getCount() == 200, name + " must hold a circuit stack");
        h.assertTrue(handler.extractItem(slots - 1, 200, false).getCount() == 200, name + " extraction lost items");
    }

    @GameTest(template = "empty16")
    public static void factorioTwoHundredStacksSurvivePersistentCodecAndContainer(GameTestHelper h) {
        ItemStack stack = new ItemStack(ModItems.COPPER_CABLE.get(), 200);
        h.assertTrue(stack.getMaxStackSize() == 200, "copper cable item stack size");
        var encoded = (net.minecraft.nbt.CompoundTag) stack.save(h.getLevel().registryAccess());
        ItemStack restored = ItemStack.parseOptional(h.getLevel().registryAccess(), encoded);
        h.assertTrue(restored.getCount() == 200 && restored.is(ModItems.COPPER_CABLE.get()), "persistent codec lost cable count");
        Container container = new net.minecraft.world.SimpleContainer(1);
        container.setItem(0, restored);
        h.assertTrue(container.getMaxStackSize(stack) == 200 && container.getItem(0).getCount() == 200, "container clips stack");
        h.succeed();
    }
}
