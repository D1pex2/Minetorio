package dev.minetorio.client.research;

import dev.minetorio.client.ModKeys;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.research.ClientResearch;
import dev.minetorio.content.research.ResearchSavedData;
import dev.minetorio.content.research.SelectResearchPayload;
import dev.minetorio.core.Units;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.research.ResearchState;
import dev.minetorio.core.research.TechTree;
import dev.minetorio.core.research.Technology;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * Technology tree (key Y): one node per technology laid out by {@link TechLayout}, with its icon
 * (the output of its first unlocked recipe), name, cost (packs per unit × units, time per unit) and
 * state color — researched (green), current (gold, with a progress bar), available (blue-grey,
 * clickable) or locked (dark red). Lines connect prerequisites to the technologies needing them.
 * Clicking an available node sends {@link SelectResearchPayload}; the server validates it. State is
 * read from {@link ClientResearch} every frame, so syncs show at once. Drawn with plain fills.
 */
public class ResearchScreen extends Screen {
    private static final int NODE_W = 150;
    private static final int NODE_H = 44;
    private static final int GAP_X = 36;
    private static final int GAP_Y = 10;
    private static final int TOP = 40;

    private static final int LINE_DONE = 0xFF5FD35F;
    private static final int LINE_OPEN = 0xFF8A8F99;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFA0A0A0;
    private static final int BAR_BACK = 0xFF202020;
    private static final int BAR_FILL = 0xFF3FB93F;

    private enum NodeState {
        RESEARCHED(0xFF24432A, 0xFF5FD35F),
        CURRENT(0xFF4F4520, 0xFFFFD84A),
        AVAILABLE(0xFF333946, 0xFFA0A8B8),
        LOCKED(0xFF252527, 0xFF6A3434);

        final int fill;
        final int border;

        NodeState(int fill, int border) {
            this.fill = fill;
            this.border = border;
        }

        String key() { return "minetorio.research.state." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    private final TechTree tree = Phase1Data.techTree();
    private final TechLayout layout = new TechLayout(tree);
    private int originX;
    private int originY;

    public ResearchScreen() {
        super(Component.translatable("minetorio.research.title"));
    }

    @Override
    protected void init() {
        int w = layout.columns() * NODE_W + Math.max(0, layout.columns() - 1) * GAP_X;
        originX = (width - w) / 2;
        originY = TOP;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private int nodeX(Technology t) { return originX + layout.cell(t.id()).column() * (NODE_W + GAP_X); }

    private int nodeY(Technology t) { return originY + layout.cell(t.id()).row() * (NODE_H + GAP_Y); }

    private static NodeState stateOf(ResearchState s, Technology t) {
        if (s.isResearched(t.id())) return NodeState.RESEARCHED;
        if (s.current().map(c -> c.id().equals(t.id())).orElse(false)) return NodeState.CURRENT;
        return s.canResearch(t.id()) ? NodeState.AVAILABLE : NodeState.LOCKED;
    }

    /** Technology under the mouse, or null. */
    @Nullable
    private Technology techAt(double mouseX, double mouseY) {
        for (Technology t : tree.all()) {
            int x = nodeX(t);
            int y = nodeY(t);
            if (mouseX >= x && mouseX < x + NODE_W && mouseY >= y && mouseY < y + NODE_H) return t;
        }
        return null;
    }

    /** Output of the technology's first unlocked recipe. */
    private static ItemStack icon(Technology t) {
        for (String id : t.unlocksRecipes()) {
            Optional<Recipe> r = Phase1Data.recipes().get(id);
            if (r.isPresent() && !r.get().outputs().isEmpty()) return MachineItems.stack(r.get().outputs().get(0).item(), 1);
        }
        return ItemStack.EMPTY;
    }

    private static String seconds(int ticks) {
        double s = ticks / (double) Units.TICKS_PER_SECOND;
        return s == Math.rint(s) ? String.valueOf((long) s) : String.valueOf(s);
    }

    private static Component name(Technology t) { return Component.translatable(ResearchSavedData.nameKey(t.id())); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ResearchState s = ClientResearch.state();
        g.drawCenteredString(font, title, width / 2, 10, TEXT);
        g.drawCenteredString(font, header(s), width / 2, 22, TEXT_DIM);
        for (Technology t : tree.all()) {
            for (String pre : t.prerequisites()) tree.get(pre).ifPresent(p -> drawLink(g, p, t, s.isResearched(p.id())));
        }
        Technology hovered = techAt(mouseX, mouseY);
        for (Technology t : tree.all()) drawNode(g, s, t, t == hovered);
        if (hovered != null) g.renderComponentTooltip(font, tooltip(s, hovered), mouseX, mouseY);
    }

    private Component header(ResearchState s) {
        Optional<Technology> cur = s.current();
        if (cur.isEmpty()) return Component.translatable("minetorio.research.none");
        return Component.translatable("minetorio.research.current", name(cur.get()), s.unitsDone(), cur.get().units());
    }

    /** Elbow line from the right edge of {@code from} to the left edge of {@code to}. */
    private void drawLink(GuiGraphics g, Technology from, Technology to, boolean done) {
        int color = done ? LINE_DONE : LINE_OPEN;
        int x1 = nodeX(from) + NODE_W;
        int y1 = nodeY(from) + NODE_H / 2;
        int x2 = nodeX(to);
        int y2 = nodeY(to) + NODE_H / 2;
        int mid = x2 - GAP_X / 2;
        g.hLine(x1, mid, y1, color);
        g.vLine(mid, Math.min(y1, y2) - 1, Math.max(y1, y2) + 1, color);
        g.hLine(mid, x2 - 1, y2, color);
    }

    private void drawNode(GuiGraphics g, ResearchState s, Technology t, boolean hovered) {
        NodeState state = stateOf(s, t);
        int x = nodeX(t);
        int y = nodeY(t);
        int border = hovered && state == NodeState.AVAILABLE ? TEXT : state.border;
        g.fill(x, y, x + NODE_W, y + NODE_H, border);
        g.fill(x + 1, y + 1, x + NODE_W - 1, y + NODE_H - 1, state.fill);
        g.renderItem(icon(t), x + 4, y + 4);
        int textColor = state == NodeState.LOCKED ? TEXT_DIM : TEXT;
        g.drawString(font, font.plainSubstrByWidth(name(t).getString(), NODE_W - 28), x + 24, y + 5, textColor, false);
        // cost: a half-size icon per pack, then "units × seconds"
        int cx = x + 24;
        for (String pack : t.packsPerUnit().keySet()) {
            g.pose().pushPose();
            g.pose().translate(cx, y + 17, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.renderItem(MachineItems.stack(pack, 1), 0, 0);
            g.pose().popPose();
            cx += 9;
        }
        g.drawString(font, Component.translatable("minetorio.research.cost", t.units(), seconds(t.ticksPerUnit())),
                cx + 2, y + 17, textColor, false);
        int done = state == NodeState.RESEARCHED ? t.units() : s.progressById().getOrDefault(t.id(), 0);
        if (done > 0 || state == NodeState.CURRENT) {
            int bx = x + 4;
            int by = y + NODE_H - 9;
            int bw = NODE_W - 8;
            g.fill(bx, by, bx + bw, by + 5, BAR_BACK);
            g.fill(bx, by, bx + Math.min(bw, bw * done / Math.max(1, t.units())), by + 5, BAR_FILL);
        }
    }

    private List<Component> tooltip(ResearchState s, Technology t) {
        List<Component> lines = new ArrayList<>();
        NodeState state = stateOf(s, t);
        lines.add(name(t).copy().withStyle(ChatFormatting.BOLD));
        lines.add(Component.translatable(state.key()).withStyle(state == NodeState.LOCKED ? ChatFormatting.RED : ChatFormatting.GRAY));
        int done = state == NodeState.RESEARCHED ? t.units() : s.progressById().getOrDefault(t.id(), 0);
        lines.add(Component.translatable("minetorio.research.units", done, t.units(), seconds(t.ticksPerUnit()))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("minetorio.research.per_unit").withStyle(ChatFormatting.GRAY));
        for (Map.Entry<String, Integer> e : t.packsPerUnit().entrySet()) {
            lines.add(Component.literal("  ").append(Component.translatable("minetorio.assembler.amount", e.getValue(),
                    MachineItems.stack(e.getKey(), 1).getHoverName())).withStyle(ChatFormatting.GRAY));
        }
        if (state == NodeState.LOCKED) {
            MutableComponent missing = Component.empty();
            boolean first = true;
            for (String pre : t.prerequisites()) {
                if (s.isResearched(pre)) continue;
                if (!first) missing.append(", ");
                missing.append(Component.translatable(ResearchSavedData.nameKey(pre)));
                first = false;
            }
            lines.add(Component.translatable("minetorio.research.requires", missing).withStyle(ChatFormatting.RED));
        }
        lines.add(Component.translatable("minetorio.research.unlocks").withStyle(ChatFormatting.GRAY));
        for (String id : t.unlocksRecipes()) {
            Phase1Data.recipes().get(id).filter(r -> !r.outputs().isEmpty()).ifPresent(r -> lines.add(
                    Component.literal("  ").append(MachineItems.stack(r.outputs().get(0).item(), 1).getHoverName())));
        }
        if (state == NodeState.AVAILABLE) lines.add(Component.translatable("minetorio.research.click").withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Technology t = button == 0 ? techAt(mouseX, mouseY) : null;
        if (t != null && stateOf(ClientResearch.state(), t) == NodeState.AVAILABLE) {
            PacketDistributor.sendToServer(new SelectResearchPayload(t.id()));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeys.RESEARCH.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
