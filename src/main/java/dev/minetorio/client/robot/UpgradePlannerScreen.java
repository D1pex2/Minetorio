package dev.minetorio.client.robot;

import dev.minetorio.content.robot.PlannerItems;
import dev.minetorio.content.robot.PlannerPayloads;
import dev.minetorio.core.data.UpgradeGroups;
import dev.minetorio.core.robot.UpgradeRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The upgrade planner's rules, Factorio-like: rows "from → to" in two columns. Click a slot to pick
 * its item from the list below (for "to": only items of the same fast-replace group), right-click a
 * slot to clear it, × removes the row, + adds one. Changes are sent when the screen closes.
 */
public class UpgradePlannerScreen extends Screen {
    private static final int SLOT = 18, ROW_H = 22, COL_W = 116, ROWS_PER_COL = UpgradeRules.MAX_RULES / 2;
    private static final int PICK_COLS = 9;

    private final List<String[]> rows = new ArrayList<>();
    /** Row and side being picked (-1: none). */
    private int pickRow = -1;
    private boolean pickFrom;
    private int left, top;

    public UpgradePlannerScreen() {
        super(Component.translatable("item.minetorio.upgrade_planner"));
    }

    @Override
    protected void init() {
        if (rows.isEmpty()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                for (UpgradeRules.Rule r : PlannerItems.rules(player.getMainHandItem()).rules()) rows.add(new String[] {r.from(), r.to()});
            }
            if (rows.isEmpty()) rows.add(new String[] {"", ""});
        }
        left = width / 2 - COL_W;
        top = height / 2 - 100;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        for (int i = 0; i < rows.size(); i++) {
            int idx = i;
            int x = left + (i / ROWS_PER_COL) * COL_W, y = top + 20 + (i % ROWS_PER_COL) * ROW_H;
            addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                rows.remove(idx);
                if (pickRow == idx) pickRow = -1;
                else if (pickRow > idx) pickRow--;
                rebuild();
            }).bounds(x + 2 * SLOT + 30, y + 1, 16, 16).build());
        }
        if (rows.size() < UpgradeRules.MAX_RULES) {
            int i = rows.size();
            int x = left + (i / ROWS_PER_COL) * COL_W, y = top + 20 + (i % ROWS_PER_COL) * ROW_H;
            addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                rows.add(new String[] {"", ""});
                rebuild();
            }).bounds(x, y + 1, 16, 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 50, top + 20 + ROWS_PER_COL * ROW_H + 70, 100, 20).build());
    }

    private int slotX(int row, boolean from) {
        return left + (row / ROWS_PER_COL) * COL_W + (from ? 0 : SLOT + 12);
    }

    private int slotY(int row) {
        return top + 20 + (row % ROWS_PER_COL) * ROW_H;
    }

    private List<String> candidates() {
        if (pickRow < 0 || pickRow >= rows.size()) return List.of();
        String from = rows.get(pickRow)[0];
        return pickFrom || from.isEmpty() ? UpgradeGroups.items() : UpgradeGroups.replacements(from);
    }

    private int pickTop() { return top + 20 + ROWS_PER_COL * ROW_H + 8; }

    private static ItemStack stack(String id) {
        return id.isEmpty() ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < rows.size(); i++) {
            for (boolean from : new boolean[] {true, false}) {
                int x = slotX(i, from), y = slotY(i);
                if (mx >= x && mx < x + SLOT && my >= y && my < y + SLOT) {
                    if (button == 1) {
                        rows.get(i)[from ? 0 : 1] = "";
                        if (from) rows.get(i)[1] = "";
                    } else {
                        pickRow = i;
                        pickFrom = from;
                    }
                    return true;
                }
            }
        }
        List<String> c = candidates();
        for (int k = 0; k < c.size(); k++) {
            int x = width / 2 - PICK_COLS * SLOT / 2 + (k % PICK_COLS) * SLOT, y = pickTop() + (k / PICK_COLS) * SLOT;
            if (mx >= x && mx < x + SLOT && my >= y && my < y + SLOT) {
                String[] row = rows.get(pickRow);
                if (pickFrom) {
                    row[0] = c.get(k);
                    if (!row[1].isEmpty() && !UpgradeGroups.compatible(row[0], row[1])) row[1] = "";
                    pickFrom = false; // go on with "to"
                } else {
                    row[1] = c.get(k);
                    if (!row[0].isEmpty() && !UpgradeGroups.compatible(row[0], row[1])) row[0] = "";
                    pickRow = -1;
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, top, 0xFFFFFFFF);
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            for (boolean from : new boolean[] {true, false}) {
                int x = slotX(i, from), y = slotY(i);
                boolean picking = pickRow == i && pickFrom == from;
                g.fill(x, y, x + SLOT, y + SLOT, picking ? 0xFF4A7A30 : 0xFF3A3A3A);
                g.renderOutline(x, y, SLOT, SLOT, 0xFF8B8B8B);
                ItemStack s = stack(row[from ? 0 : 1]);
                g.renderItem(s, x + 1, y + 1);
                if (!s.isEmpty() && mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) hovered = s;
            }
            boolean valid = new UpgradeRules.Rule(row[0], row[1]).valid();
            g.drawString(font, "→", slotX(i, true) + SLOT + 2, slotY(i) + 5, valid ? 0xFF60E060 : 0xFF909090, false);
        }
        List<String> c = candidates();
        if (!c.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(pickFrom ? "minetorio.planner.upgrade.pick_from" : "minetorio.planner.upgrade.pick_to"),
                    width / 2, pickTop() - 10 + 0, 0xFFB0B0B0);
        }
        for (int k = 0; k < c.size(); k++) {
            int x = width / 2 - PICK_COLS * SLOT / 2 + (k % PICK_COLS) * SLOT, y = pickTop() + (k / PICK_COLS) * SLOT;
            g.fill(x, y, x + SLOT, y + SLOT, 0xFF2A2A2A);
            ItemStack s = stack(c.get(k));
            g.renderItem(s, x + 1, y + 1);
            if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) hovered = s;
        }
        if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mouseX, mouseY);
    }

    @Override
    public void onClose() {
        List<UpgradeRules.Rule> out = new ArrayList<>();
        for (String[] r : rows) out.add(new UpgradeRules.Rule(r[0], r[1]));
        PacketDistributor.sendToServer(new PlannerPayloads.SetRules(out));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
