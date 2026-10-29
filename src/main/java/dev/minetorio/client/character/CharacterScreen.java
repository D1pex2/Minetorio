package dev.minetorio.client.character;

import dev.minetorio.client.gui.FactoryTheme;
import dev.minetorio.client.handcraft.HandcraftScreen;
import dev.minetorio.content.character.CharacterMenu;
import dev.minetorio.content.character.CharacterShortcuts;
import dev.minetorio.content.logistics.LogisticsPayloads;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.minetorio.client.gui.FactoryButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The player's real inventory on the left and the existing hand-crafting panel on the right. */
public final class CharacterScreen extends AbstractContainerScreen<CharacterMenu> {
    private final HandcraftScreen crafting = new HandcraftScreen();
    private float uiScale = 1.0f;

    public CharacterScreen(CharacterMenu menu, Inventory inventory) {
        super(menu, inventory, Component.translatable("container.inventory"));
        imageWidth = 420;
        imageHeight = 228;
    }

    @Override protected void init() {
        uiScale = Math.min(1.0f, Math.min(width / 420.0f, height / 260.0f));
        width = Math.max(420, (int) Math.floor(width / uiScale));
        height = Math.max(260, (int) Math.floor(height / uiScale));
        super.init();
        // HandcraftScreen centres its 216px panel. Supply a logical width giving it the right edge.
        addRenderableWidget(new FactoryButton(leftPos + 8, topPos + 15, 88, 18,
                Component.translatable("minetorio.personal_logistics.title"), b -> {
                    minecraft.player.closeContainer();
                    PacketDistributor.sendToServer(new LogisticsPayloads.OpenPersonal());
                }));
        addRenderableWidget(new FactoryButton(leftPos + 100, topPos + 15, 88, 18,
                Component.translatable("minetorio.stats.title"), b -> {
                    minecraft.player.closeContainer();
                    minecraft.setScreen(new dev.minetorio.client.stats.StatisticsScreen());
                }));
        addRenderableWidget(new FactoryButton(leftPos + 176, topPos + 202, 18, 18,
                Component.literal("+"), b -> {
                    if (minecraft.player instanceof CharacterShortcuts s) ShortcutHud.action(3100 + s.visibleRows() % 4 + 1);
                }));
        int craftLeft = leftPos + 204;
        crafting.init(minecraft, 2 * craftLeft + 216, height);
    }

    @Override protected void containerTick() { super.containerTick(); crafting.tick(); }

    @Override protected void renderBg(GuiGraphics g, float partial, int x, int y) {
        FactoryTheme.panel(g, leftPos, topPos, 196, imageHeight);
        for (int i = 0; i < 80; i++) FactoryTheme.slot(g, leftPos + 8 + i % 10 * 18, topPos + 36 + i / 10 * 18);
        FactoryTheme.slot(g, leftPos + 8, topPos + 204);
        FactoryTheme.slot(g, leftPos + 30, topPos + 204);
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {
        FactoryTheme.title(g, font, title, 8, 5);
        FactoryTheme.label(g, font, Component.translatable("minetorio.character.tool_trash"), 52, 209);
    }

    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        x = (int)(x / uiScale); y = (int)(y / uiScale);
        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1);
        super.render(g, x, y, partial);
        if (minecraft.player instanceof CharacterShortcuts s) {
            for (int col = 0; col < 10; col++) ShortcutHud.draw(g, leftPos + 8 + col * 18, topPos + 183,
                    s.shortcut(s.selectedRow() * 10 + col));
        }
        crafting.render(g, x, y, partial);
        renderTooltip(g, x, y);
        g.pose().popPose();
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        x /= uiScale; y /= uiScale;
        if (x >= leftPos + 8 && x < leftPos + 188 && y >= topPos + 183 && y < topPos + 201
                && minecraft.player instanceof CharacterShortcuts s) {
            int col = ((int)x - leftPos - 8) / 18;
            int index = s.selectedRow() * 10 + col;
            if (button == 1) ShortcutHud.action(1100 + index);
            else if (button == 0 && !menu.getCarried().isEmpty()) ShortcutHud.action(1000 + index);
            else if (button == 0) ShortcutHud.action(2000 + index);
            return true;
        }
        if (x >= leftPos + 204 && crafting.mouseClicked(x, y, button)) return true;
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(x / uiScale, y / uiScale, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x / uiScale, y / uiScale, button, dx / uiScale, dy / uiScale);
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return super.mouseScrolled(x / uiScale, y / uiScale, dx, dy);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (dev.minetorio.client.ModKeys.HANDCRAFT.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
}
