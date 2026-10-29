package dev.minetorio.client.handcraft;

import dev.minetorio.client.ModKeys;
import dev.minetorio.client.gui.FactoryTheme;
import dev.minetorio.content.robot.GhostPayloads;
import dev.minetorio.client.handcraft.HandcraftGroups.Group;
import dev.minetorio.content.handcraft.ClientHandcraft;
import dev.minetorio.content.handcraft.HandcraftPayloads;
import dev.minetorio.content.handcraft.PlayerInventoryAdapter;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.research.ClientResearch;
import dev.minetorio.content.research.RecipeLocks;
import dev.minetorio.core.Units;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.handcraft.HandcraftQueue;
import dev.minetorio.core.item.Inventory;
import dev.minetorio.core.recipe.Ingredient;
import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.recipe.RecipeBook;
import dev.minetorio.core.research.ResearchState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Hand crafting (key R), as in Factorio: a tab per group ({@link HandcraftGroups}), a grid of the
 * unlocked crafting recipes of the selected group (locked ones are hidden) with the number that can
 * be crafted from the inventory's plain items on each icon, and the queue below. Left click queues
 * one craft, right click five, shift + left click as many as possible; the server validates every
 * request. Clicking a queue entry cancels it. Craftable counts are computed client-side
 * ({@link HandcraftQueue#maxCraftable} against a snapshot of the plain item counts) and recomputed only when the inventory or the research changes.
 */
public class HandcraftScreen extends Screen {
    private static final int COLS = 10;
    private static final int SLOT = 20;
    private static final int PAD = 8;
    private static final int TAB_H = 24;
    private static final int QUEUE_ROWS = 2;
    private static final int RIGHT_CLICK_TIMES = 5;

    private static final int PANEL = 0xF0202228;
    private static final int PANEL_BORDER = 0xFF6A6F7A;
    private static final int TAB = 0xFF2C3038;
    private static final int TAB_SELECTED = 0xFF4A505C;
    private static final int SLOT_OK = 0xFF3A3F4A;
    private static final int SLOT_NONE = 0xFF4A2B2B;
    private static final int SLOT_QUEUE = 0xFF34404A;
    private static final int HOVER = 0xFFFFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFA0A0A0;
    private static final int BAR_BACK = 0xFF101010;
    private static final int BAR_FILL = 0xFF3FB93F;

    /** Tab kept between openings. */
    private static Group selected = Group.LOGISTICS;

    private final RecipeBook book = Phase1Data.recipes();
    private final HandcraftQueue planner = new HandcraftQueue(book);
    private final Map<Group, List<String>> visible = new EnumMap<>(Group.class);
    private final Map<String, Integer> craftable = new HashMap<>();
    @Nullable
    private Map<String, Integer> cachedInventory;
    @Nullable
    private ResearchState cachedResearch;

    private final int gridRows;
    private int left;
    private int top;

    public HandcraftScreen() {
        super(Component.translatable("minetorio.handcraft.title"));
        int rows = 1;
        for (Group g : Group.values()) rows = Math.max(rows, (HandcraftGroups.recipes(g, book, id -> true).size() + COLS - 1) / COLS);
        gridRows = rows;
    }

    private static int panelWidth() { return PAD * 2 + COLS * SLOT; }

    private int panelHeight() { return gridTop() - top + gridRows * SLOT + 16 + QUEUE_ROWS * SLOT + PAD; }

    private int tabTop() { return top + 20; }

    private int gridTop() { return tabTop() + TAB_H + 6; }

    private int queueTop() { return gridTop() + gridRows * SLOT + 16; }

    private static int tabWidth() { return COLS * SLOT / Group.values().length; }

    @Override
    protected void init() {
        left = (width - panelWidth()) / 2;
        top = 0;
        top = Math.max(4, (height - panelHeight()) / 2);
        refresh(true);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        refresh(false);
    }

    /** Recomputes the visible recipes and craftable counts if the inventory or the research changed. */
    private void refresh(boolean force) {
        if (minecraft == null || minecraft.player == null) return;
        Map<String, Integer> inv = PlayerInventoryAdapter.plainCounts(minecraft.player);
        ResearchState research = ClientResearch.state();
        if (!force && inv.equals(cachedInventory) && research == cachedResearch) return;
        cachedInventory = inv;
        cachedResearch = research;
        Inventory snapshot = new Inventory() {
            @Override
            public int count(String item) { return inv.getOrDefault(item, 0); }

            @Override
            public int remove(String item, int n) { return 0; }

            @Override
            public int add(String item, int n) { return 0; }
        };
        craftable.clear();
        for (Group g : Group.values()) {
            List<String> ids = HandcraftGroups.recipes(g, book, RecipeLocks::isUnlockedOnClient);
            visible.put(g, ids);
            for (String id : ids) craftable.put(id, planner.maxCraftable(id, snapshot, RecipeLocks::isUnlockedOnClient));
        }
    }

    // --- geometry ------------------------------------------------------------------------------------

    @Nullable
    private Group tabAt(double mx, double my) {
        if (my < tabTop() || my >= tabTop() + TAB_H) return null;
        int i = (int) Math.floor((mx - left - PAD) / tabWidth());
        return mx >= left + PAD && i >= 0 && i < Group.values().length ? Group.values()[i] : null;
    }

    /** Index into a grid of {@code rows} rows starting at {@code y0}, or -1. */
    private int cellAt(double mx, double my, int y0, int rows) {
        double x = mx - left - PAD;
        double y = my - y0;
        if (x < 0 || y < 0 || x >= COLS * SLOT || y >= rows * SLOT) return -1;
        int cx = (int) x / SLOT;
        int cy = (int) y / SLOT;
        if (x - cx * SLOT >= 18 || y - cy * SLOT >= 18) return -1;
        return cy * COLS + cx;
    }

    @Nullable
    private String recipeAt(double mx, double my) {
        List<String> ids = visible.getOrDefault(selected, List.of());
        int i = cellAt(mx, my, gridTop(), gridRows);
        return i >= 0 && i < ids.size() ? ids.get(i) : null;
    }

    private int queueIndexAt(double mx, double my) {
        int i = cellAt(mx, my, queueTop(), QUEUE_ROWS);
        return i >= 0 && i < shownQueue() ? i : -1;
    }

    private static int shownQueue() { return Math.min(ClientHandcraft.entries().size(), COLS * QUEUE_ROWS); }

    private int slotX(int i) { return left + PAD + (i % COLS) * SLOT; }

    private static int slotY(int y0, int i) { return y0 + (i / COLS) * SLOT; }

    // --- rendering -----------------------------------------------------------------------------------

    static ItemStack icon(String recipeId) {
        return Phase1Data.recipes().get(recipeId).filter(r -> !r.outputs().isEmpty())
                .map(r -> MachineItems.stack(r.outputs().get(0).item(), 1)).orElse(ItemStack.EMPTY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int w = panelWidth();
        int h = panelHeight();
        g.fill(left - 1, top - 1, left + w + 1, top + h + 1, PANEL_BORDER);
        g.fill(left, top, left + w, top + h, PANEL);
        g.drawString(font, title.copy().append(" — ").append(Component.translatable(selected.key())), left + PAD, top + 7, TEXT, false);

        Group hoveredTab = tabAt(mouseX, mouseY);
        for (int i = 0; i < Group.values().length; i++) {
            Group grp = Group.values()[i];
            int x = left + PAD + i * tabWidth();
            int y = tabTop();
            FactoryTheme.inset(g, x, y, tabWidth() - 2, TAB_H);
            g.fill(x + 1, y + 1, x + tabWidth() - 3, y + 2,
                    grp == selected ? FactoryTheme.ACCENT : grp == hoveredTab ? FactoryTheme.TEXT : FactoryTheme.MUTED);
            g.renderItem(MachineItems.stack(grp.icon, 1), x + (tabWidth() - 2 - 16) / 2, y + 4);
        }

        String hoveredRecipe = recipeAt(mouseX, mouseY);
        List<String> ids = visible.getOrDefault(selected, List.of());
        if (ids.isEmpty()) {
            g.drawString(font, Component.translatable("minetorio.handcraft.empty"), left + PAD, gridTop() + 5, TEXT_DIM, false);
        }
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            int n = craftable.getOrDefault(id, 0);
            drawSlot(g, slotX(i), slotY(gridTop(), i), n > 0 ? SLOT_OK : SLOT_NONE, id.equals(hoveredRecipe));
            ItemStack stack = icon(id);
            g.renderItem(stack, slotX(i) + 1, slotY(gridTop(), i) + 1);
            if (n > 0) g.renderItemDecorations(font, stack, slotX(i) + 1, slotY(gridTop(), i) + 1, String.valueOf(n));
        }

        int qy = queueTop();
        List<HandcraftQueue.Entry> queue = ClientHandcraft.entries();
        g.drawString(font, Component.translatable("minetorio.handcraft.queue", queue.size()), left + PAD, qy - 11, TEXT_DIM, false);
        if (queue.isEmpty()) {
            g.drawString(font, Component.translatable("minetorio.handcraft.queue_empty"), left + PAD, qy + 5, TEXT_DIM, false);
        }
        int hoveredQueue = queueIndexAt(mouseX, mouseY);
        for (int i = 0; i < shownQueue(); i++) {
            HandcraftQueue.Entry e = queue.get(i);
            int x = slotX(i);
            int y = slotY(qy, i);
            drawSlot(g, x, y, SLOT_QUEUE, i == hoveredQueue);
            ItemStack stack = icon(e.recipeId());
            g.renderItem(stack, x + 1, y + 1);
            g.renderItemDecorations(font, stack, x + 1, y + 1, e.times() > 1 ? String.valueOf(e.times()) : null);
            if (i == 0) drawProgress(g, x, y + 16, 18, ClientHandcraft.frontProgress());
        }
        if (queue.size() > shownQueue()) {
            g.drawString(font, "+" + (queue.size() - shownQueue()), left + PAD + COLS * SLOT - 16, qy - 11, TEXT_DIM, false);
        }

        if (hoveredTab != null) {
            g.renderTooltip(font, Component.translatable(hoveredTab.key()), mouseX, mouseY);
        } else if (hoveredRecipe != null) {
            g.renderComponentTooltip(font, recipeTooltip(hoveredRecipe), mouseX, mouseY);
        } else if (hoveredQueue >= 0) {
            HandcraftQueue.Entry e = queue.get(hoveredQueue);
            g.renderComponentTooltip(font, List.of(
                    Component.translatable("minetorio.assembler.amount", e.times(), icon(e.recipeId()).getHoverName()),
                    Component.translatable("minetorio.handcraft.cancel").withStyle(ChatFormatting.YELLOW)), mouseX, mouseY);
        }
    }

    private static void drawSlot(GuiGraphics g, int x, int y, int fill, boolean hovered) {
        FactoryTheme.slot(g, x, y);
        if (fill == SLOT_NONE) g.fill(x + 1, y + 1, x + 17, y + 17, 0x703D1111);
        if (hovered) g.fill(x + 1, y + 1, x + 17, y + 17, 0x50FFFFFF);
    }

    /** Thin progress bar (used by the HUD too). */
    static void drawProgress(GuiGraphics g, int x, int y, int w, double progress) {
        g.fill(x, y, x + w, y + 2, BAR_BACK);
        g.fill(x, y, x + (int) Math.round(w * Math.max(0, Math.min(1, progress))), y + 2, BAR_FILL);
    }

    private static String seconds(int ticks) {
        double s = ticks / (double) Units.TICKS_PER_SECOND;
        return s == Math.rint(s) ? String.valueOf((long) s) : String.valueOf(s);
    }

    private List<Component> recipeTooltip(String id) {
        List<Component> lines = new ArrayList<>();
        Recipe r = book.get(id).orElseThrow();
        Ingredient out = r.outputs().get(0);
        MutableComponent name = MachineItems.stack(out.item(), 1).getHoverName().copy().withStyle(ChatFormatting.BOLD);
        lines.add(out.count() > 1 ? Component.translatable("minetorio.assembler.amount", out.count(), name) : name);
        lines.add(Component.translatable("minetorio.handcraft.time", seconds(r.ticks())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("minetorio.handcraft.ingredients").withStyle(ChatFormatting.GRAY));
        Map<String, Integer> inv = cachedInventory == null ? Map.of() : cachedInventory;
        for (Ingredient in : r.inputs()) {
            ChatFormatting color = inv.getOrDefault(in.item(), 0) >= in.count() ? ChatFormatting.GRAY : ChatFormatting.RED;
            lines.add(Component.literal("  ").append(Component.translatable("minetorio.assembler.amount", in.count(),
                    MachineItems.stack(in.item(), 1).getHoverName())).withStyle(color));
        }
        int n = craftable.getOrDefault(id, 0);
        lines.add(n > 0 ? Component.translatable("minetorio.handcraft.craftable", n).withStyle(ChatFormatting.GREEN)
                : Component.translatable("minetorio.handcraft.missing").withStyle(ChatFormatting.RED));
        lines.add(Component.translatable("minetorio.handcraft.controls").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    // --- input ---------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Group tab = button == 0 ? tabAt(mouseX, mouseY) : null;
        if (tab != null) {
            selected = tab;
            click();
            return true;
        }
        String id = recipeAt(mouseX, mouseY);
        if (id != null && (button == 2 || (button == 0 && ModKeys.ghostKeyDown()))) {
            // a ghost item of the output (Factorio: a ghost cursor); the server checks research and placeability
            PacketDistributor.sendToServer(new GhostPayloads.FromRecipe(id));
            click();
            return true;
        }
        if (id != null && (button == 0 || button == 1)) {
            int max = craftable.getOrDefault(id, 0);
            int want = button == 1 ? RIGHT_CLICK_TIMES : hasShiftDown() ? max : 1;
            int times = Math.min(want, max);
            if (times > 0) {
                PacketDistributor.sendToServer(new HandcraftPayloads.Enqueue(id, times));
                click();
            }
            return true;
        }
        int q = button == 0 ? queueIndexAt(mouseX, mouseY) : -1;
        if (q >= 0) {
            HandcraftQueue.Entry e = ClientHandcraft.entries().get(q);
            PacketDistributor.sendToServer(new HandcraftPayloads.Cancel(q, e.recipeId(), e.times()));
            click();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeys.HANDCRAFT.matches(keyCode, scanCode) || minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
