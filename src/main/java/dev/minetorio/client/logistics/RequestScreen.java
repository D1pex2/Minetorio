package dev.minetorio.client.logistics;

import dev.minetorio.content.logistics.LogisticsPayloads;
import dev.minetorio.content.logistics.RequestMenu;
import dev.minetorio.core.logistics.LogisticRequest;
import dev.minetorio.core.logistics.LogisticStats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Shared GUI of the request menus: a plain panel with a frame for every slot, the request ghost
 * slots (click with an item to set one, click an empty-handed slot to select it, right-click to
 * clear), and below them the selected slot's minimum (and maximum, for personal requests; empty =
 * no maximum) with a Set button. Counts are drawn on the ghost slots.
 */
public abstract class RequestScreen<M extends RequestMenu> extends AbstractContainerScreen<M> {
    protected static final int PANEL = 0xFFC6C6C6;
    protected static final int TEXT = 0x404040;
    private static final int HOVER = 0x80FFFFFF;
    private static final int SELECTED = 0xFFFFD34D;

    private int selected = -1;
    private EditBox min;
    private EditBox max;
    private Button set;

    protected RequestScreen(M menu, Inventory inventory, Component title, int height) {
        super(menu, inventory, title);
        imageHeight = height;
        inventoryLabelY = imageHeight - 94;
    }

    /** Top of the count editor row, or -1 if the menu has no counts. */
    protected abstract int editY();

    @Override
    protected void init() {
        super.init();
        if (editY() < 0 || !menu.hasCounts()) return;
        int y = topPos + editY();
        min = addRenderableWidget(new EditBox(font, leftPos + 30, y, 40, 14, Component.translatable("minetorio.logistics.min")));
        min.setFilter(s -> s.matches("\\d{0,7}"));
        int x = leftPos + 76;
        if (menu.hasMax()) {
            max = addRenderableWidget(new EditBox(font, leftPos + 100, y, 40, 14, Component.translatable("minetorio.logistics.max")));
            max.setFilter(s -> s.matches("\\d{0,7}"));
            x = leftPos + 144;
        }
        set = addRenderableWidget(Button.builder(Component.translatable("minetorio.logistics.set"), b -> apply())
                .bounds(x, y - 1, 26, 16).build());
        select(-1);
    }

    private void select(int slot) {
        selected = slot;
        LogisticRequest r = menu.table().get(slot);
        if (min != null) {
            min.setValue(r == null ? "" : Integer.toString(r.min()));
            min.setEditable(r != null);
        }
        if (max != null) {
            max.setValue(r == null || !r.hasMax() ? "" : Integer.toString(r.max()));
            max.setEditable(r != null);
        }
        if (set != null) set.active = r != null;
    }

    private void apply() {
        if (menu.table().get(selected) == null) return;
        int lo = parse(min.getValue(), 0);
        int hi = max == null ? -1 : parse(max.getValue(), -1);
        PacketDistributor.sendToServer(new LogisticsPayloads.EditRequest(menu.containerId, selected, lo, hi));
    }

    private static int parse(String s, int dflt) {
        try {
            return s.isEmpty() ? dflt : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    protected void clickButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    /** Request slot under the mouse, or -1. */
    protected int requestAt(double mouseX, double mouseY) {
        for (int i = 0; i < menu.table().size(); i++) {
            int x = leftPos + menu.requestX(i), y = topPos + menu.requestY(i);
            if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int slot = requestAt(mouseX, mouseY);
        if (slot >= 0) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                clickButton(RequestMenu.BUTTON_CLEAR + slot);
                select(-1);
            } else if (!menu.getCarried().isEmpty()) {
                clickButton(slot);
                selected = slot;
            } else {
                select(slot);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean editing = (min != null && min.isFocused()) || (max != null && max.isFocused());
        if (editing) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                apply();
                return true;
            }
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                return (min != null && min.keyPressed(key, scan, mods)) || (max != null && max.keyPressed(key, scan, mods)) || true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    private int lastVersion = -1;

    @Override
    protected void containerTick() {
        super.containerTick();
        // a set from the carried item (or a sync) changed the table: show the selected slot's counts
        if (menu.table().version() != lastVersion) {
            lastVersion = menu.table().version();
            boolean editing = (min != null && min.isFocused()) || (max != null && max.isFocused());
            if (!editing) select(selected);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int hovered = requestAt(mouseX, mouseY);
        LogisticRequest r = menu.table().get(hovered);
        if (hovered >= 0 && r != null && menu.getCarried().isEmpty()) {
            List<Component> lines = new ArrayList<>();
            lines.add(ghost(r.item()).getHoverName());
            if (menu.hasCounts()) {
                lines.add(Component.translatable("minetorio.logistics.request_min", r.min()));
                if (menu.hasMax()) {
                    lines.add(r.hasMax() ? Component.translatable("minetorio.logistics.request_max", r.max())
                            : Component.translatable("minetorio.logistics.request_no_max"));
                }
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    static ItemStack ghost(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(rl).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        panel(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot s : menu.slots) slotFrame(g, leftPos + s.x - 1, topPos + s.y - 1);
        int hovered = requestAt(mouseX, mouseY);
        for (int i = 0; i < menu.table().size(); i++) {
            int x = leftPos + menu.requestX(i), y = topPos + menu.requestY(i);
            if (i == selected) g.fill(x - 2, y - 2, x + 18, y + 18, SELECTED);
            slotFrame(g, x - 1, y - 1);
            g.fill(x, y, x + 16, y + 16, 0xFF7A8BA0); // ghost slots are tinted
            LogisticRequest r = menu.table().get(i);
            if (r != null) {
                ItemStack stack = ghost(r.item());
                if (!stack.isEmpty()) {
                    g.renderFakeItem(stack, x, y);
                    if (menu.hasCounts()) {
                        String n = shortCount(r.min());
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 200);
                        g.drawString(font, n, x + 17 - font.width(n), y + 9, 0xFFFFFF, true);
                        g.pose().popPose();
                    }
                }
            }
            if (i == hovered) g.fill(x, y, x + 16, y + 16, HOVER);
        }
    }

    static String shortCount(int n) {
        if (n >= 10_000) return (n / 1000) + "k";
        return Integer.toString(n);
    }

    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFFFFFFF);
        g.fill(x + 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL);
    }

    static void slotFrame(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        g.fill(x, y, x + 17, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + 17, 0xFF373737);
        g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        if (min != null) {
            g.drawString(font, Component.translatable("minetorio.logistics.min"), 8, editY() + 3, TEXT, false);
            if (max != null) g.drawString(font, Component.translatable("minetorio.logistics.max"), 74, editY() + 3, TEXT, false);
        }
    }

    /** {@link LogisticStats#UNLIMITED} as shown. */
    static boolean unlimited(int max) { return max == LogisticStats.UNLIMITED; }
}
