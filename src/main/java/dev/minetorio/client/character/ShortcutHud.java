package dev.minetorio.client.character;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minetorio.Minetorio;
import dev.minetorio.client.gui.FactoryTheme;
import dev.minetorio.content.character.CharacterInventory;
import dev.minetorio.content.character.CharacterMenu;
import dev.minetorio.content.character.CharacterShortcuts;
import dev.minetorio.core.character.HudLayout;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class ShortcutHud {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "shortcuts");
    private static final KeyMapping CYCLE = new KeyMapping("key.minetorio.shortcut_cycle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.minetorio");
    private static final KeyMapping TENTH = new KeyMapping("key.minetorio.shortcut_tenth", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_0, "key.categories.minetorio");
    private ShortcutHud() {}

    private static void select(CharacterShortcuts state, int column) {
        int selectedColumn = Math.floorMod(column, CharacterShortcuts.COLUMNS);
        state.selectedColumn(selectedColumn);
        action(2000 + state.displayedRow(0) * CharacterShortcuts.COLUMNS + selectedColumn);
    }

    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { e.register(CYCLE); e.register(TENTH); }
    @SubscribeEvent public static void layers(RegisterGuiLayersEvent e) { e.registerAbove(VanillaGuiLayers.HOTBAR, LAYER, ShortcutHud::render); }
    @SubscribeEvent public static void hideVanilla(RenderGuiLayerEvent.Pre e) {
        if (e.getName().equals(VanillaGuiLayers.HOTBAR) && Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.inventoryMenu instanceof CharacterMenu) e.setCanceled(true);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        while (CYCLE.consumeClick()) if (mc.screen == null) action(3000);
        while (TENTH.consumeClick()) if (mc.screen == null && mc.player instanceof CharacterShortcuts s) select(s, 9);
    }
    @SubscribeEvent public static void numbers(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getAction() != GLFW.GLFW_PRESS || mc.screen != null || !(mc.player instanceof CharacterShortcuts s)) return;
        for (int i = 0; i < mc.options.keyHotbarSlots.length; i++) {
            if (mc.options.keyHotbarSlots[i].matches(e.getKey(), e.getScanCode())) {
                select(s, i);
                break;
            }
        }
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW)
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || e.getScrollDeltaY() == 0 || !(mc.player instanceof CharacterShortcuts state)) return;
        select(state, state.selectedColumn() + (e.getScrollDeltaY() > 0 ? -1 : 1));
        e.setCanceled(true);
    }

    static void selectIndex(int index) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player instanceof CharacterShortcuts state) state.selectedColumn(Math.floorMod(index, CharacterShortcuts.COLUMNS));
        action(2000 + index);
    }

    static void action(int action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gameMode != null && mc.player.containerMenu == mc.player.inventoryMenu
                && mc.player.inventoryMenu instanceof CharacterMenu) mc.gameMode.handleInventoryButtonClick(0, action);
    }
    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !(mc.player instanceof CharacterShortcuts state)) return;
        HudLayout layout = HudLayout.of(g.guiWidth(), g.guiHeight(), state.visibleRows());
        g.pose().pushPose();
        g.pose().translate(layout.left(), layout.hotbarTop(), 0);
        g.pose().scale(layout.scale(), layout.scale(), 1);
        int x = 0;
        for (int row = 0; row < state.visibleRows(); row++) {
            int rowId = state.displayedRow(row);
            int y = (state.visibleRows() - row - 1) * 20;
            for (int col = 0; col < 10; col++) {
                draw(g, x + col * 18, y, state.shortcut(rowId * 10 + col));
                if (row == 0 && col == state.selectedColumn()) g.renderOutline(x + col * 18, y, 18, 18, 0xFFFFD36A);
            }
        }
        g.pose().popPose();
    }
    static void draw(GuiGraphics g, int x, int y, int id) {
        FactoryTheme.slot(g, x, y);
        if (id == 0) return;
        Minecraft mc = Minecraft.getInstance();
        Item item = Item.byId(id);
        int count = 0;
        Container main = CharacterInventory.main(mc.player.getInventory());
        for (int i = 0; i < main.getContainerSize(); i++) {
            ItemStack stack = main.getItem(i);
            if (stack.is(item)) count += stack.getCount();
        }
        ItemStack icon = new ItemStack(item);
        g.renderItem(icon, x + 1, y + 1);
        if (count == 0) g.fill(x + 1, y + 1, x + 17, y + 17, 0xA0000000);
        else g.renderItemDecorations(mc.font, icon, x + 1, y + 1, String.valueOf(count));
    }
}
