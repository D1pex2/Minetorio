package dev.minetorio.client.blueprint;

import dev.minetorio.content.blueprint.BlueprintCapture;
import dev.minetorio.content.blueprint.BlueprintMeta;
import dev.minetorio.content.blueprint.BlueprintRecord;
import dev.minetorio.content.blueprint.BlueprintStacks;
import dev.minetorio.core.blueprint.BlueprintBooks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Factorio's blueprint window, for one blueprint or book (in the inventory, in a book, or in the
 * library): its name, up to four icons (click an item in the inventory below to add it, click an
 * icon to remove it; "Auto" picks the most frequent parts), and the blueprint string: Export (or
 * Ctrl+C outside the name field) copies it to the clipboard. Done (or Enter) applies.
 */
public class BlueprintMetaScreen extends Screen {
    private static final int W = BlueprintGui.INV_W + 16;
    private static final int H = 186;

    @Nullable
    private final Screen parent;
    private final Supplier<BlueprintRecord> record;
    private final Consumer<BlueprintMeta> apply;
    private final List<ResourceLocation> icons = new ArrayList<>();
    private EditBox name;
    private int left, top;
    @Nullable
    private Component message;

    /**
     * @param record the current record (re-read when exporting; null closes the window)
     * @param apply  what Done does with the new name and icons
     */
    public BlueprintMetaScreen(@Nullable Screen parent, Supplier<BlueprintRecord> record, Consumer<BlueprintMeta> apply) {
        super(Component.translatable("minetorio.blueprint.gui.edit"));
        this.parent = parent;
        this.record = record;
        this.apply = apply;
        BlueprintRecord r = record.get();
        if (r != null) icons.addAll(r.meta().icons());
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        BlueprintRecord r = record.get();
        String old = name == null ? (r == null ? "" : r.meta().label()) : name.getValue();
        name = new EditBox(font, left + 8, top + 20, W - 16, 16, Component.translatable("minetorio.blueprint.gui.name"));
        name.setMaxLength(BlueprintBooks.MAX_LABEL);
        name.setValue(old);
        name.setHint(Component.translatable("minetorio.blueprint.gui.name_hint"));
        addRenderableWidget(name);
        setInitialFocus(name);
        int bx = left + 8 + 4 * BlueprintGui.CELL + 6;
        Button auto = Button.builder(Component.translatable("minetorio.blueprint.gui.auto_icons"), b -> autoIcons())
                .bounds(bx, top + 42, 50, 16).build();
        auto.active = r != null && r.data() != null;
        addRenderableWidget(auto);
        addRenderableWidget(Button.builder(Component.translatable("minetorio.blueprint.gui.clear_icons"), b -> icons.clear())
                .bounds(bx + 52, top + 42, 40, 16).build());
        int bw = (W - 16 - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("minetorio.blueprint.gui.export"), b -> export())
                .bounds(left + 8, top + 64, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> done())
                .bounds(left + 12 + bw, top + 64, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + 16 + 2 * bw, top + 64, bw, 18).build());
    }

    private void autoIcons() {
        BlueprintRecord r = record.get();
        if (r == null || r.data() == null) return;
        icons.clear();
        icons.addAll(BlueprintCapture.defaultIcons(r.data()));
    }

    private void export() {
        BlueprintRecord r = record.get();
        if (r == null) return;
        message = BlueprintTransfer.export(r.withMeta(meta()));
    }

    private BlueprintMeta meta() {
        return new BlueprintMeta(BlueprintBooks.cleanLabel(name.getValue()), List.copyOf(icons));
    }

    private void done() {
        if (record.get() != null) apply.accept(meta());
        onClose();
    }

    @Override
    public void tick() {
        if (record.get() == null) onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int iconX(int i) { return left + 8 + i * BlueprintGui.CELL; }

    private int invY() { return top + H - 8 - BlueprintGui.INV_H; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        g.drawString(font, title, left + 8, top + 7, BlueprintGui.TEXT, false);
        for (int i = 0; i < BlueprintBooks.MAX_ICONS; i++) {
            int x = iconX(i), y = top + 41;
            BlueprintGui.slot(g, x, y, BlueprintGui.SLOT);
            if (i < icons.size()) {
                g.renderFakeItem(new ItemStack(BuiltInRegistries.ITEM.get(icons.get(i))), x + 1, y + 1);
                if (BlueprintGui.inside(mx, my, x, y, BlueprintGui.CELL, BlueprintGui.CELL)) {
                    g.fill(x, y, x + BlueprintGui.CELL - 1, y + BlueprintGui.CELL - 1, BlueprintGui.HOVER);
                }
            }
        }
        g.drawString(font, Component.translatable("minetorio.blueprint.gui.icons_hint"), left + 8, top + 88, BlueprintGui.TEXT_DIM, false);
        int hovered = BlueprintGui.drawInventory(g, font, minecraft.player.getInventory(), left + 8, invY(), mx, my, -1, -1);
        if (message != null) g.drawCenteredString(font, message, width / 2, top + H + 4, BlueprintGui.TEXT);
        if (hovered >= 0 && !minecraft.player.getInventory().getItem(hovered).isEmpty()) {
            g.renderTooltip(font, minecraft.player.getInventory().getItem(hovered), mx, my);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        super.renderBackground(g, mx, my, partialTick);
        BlueprintGui.panel(g, left, top, W, H);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < icons.size(); i++) {
            if (BlueprintGui.inside(mx, my, iconX(i), top + 41, BlueprintGui.CELL, BlueprintGui.CELL)) {
                icons.remove(i);
                return true;
            }
        }
        int slot = BlueprintGui.invSlotAt(mx, my, left + 8, invY());
        if (slot >= 0) {
            ItemStack s = minecraft.player.getInventory().getItem(slot);
            if (!s.isEmpty() && s.getItem() != Items.AIR && icons.size() < BlueprintBooks.MAX_ICONS) {
                icons.add(BuiltInRegistries.ITEM.getKey(s.getItem()));
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            done();
            return true;
        }
        if (Screen.isCopy(key) && (!name.isFocused() || name.getHighlighted().isEmpty())) {
            export();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** The editor for the blueprint or book at {@code path} in inventory slot {@code slot}. */
    static BlueprintMetaScreen forSlot(@Nullable Screen parent, int slot, List<Integer> path) {
        Supplier<BlueprintRecord> rec = () -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return null;
            BlueprintRecord root = BlueprintStacks.record(mc.player.getInventory().getItem(slot));
            return root == null ? null : dev.minetorio.content.blueprint.BlueprintBookOps.at(root, path);
        };
        return new BlueprintMetaScreen(parent, rec, m -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new dev.minetorio.content.blueprint.BlueprintPayloads.SetMeta(slot, List.copyOf(path), m)));
    }
}
