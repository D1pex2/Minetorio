package dev.minetorio.client.research;

import dev.minetorio.client.ModKeys;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.research.ClientResearch;
import dev.minetorio.content.research.ResearchSavedData;
import dev.minetorio.content.research.SelectResearchPayload;
import dev.minetorio.core.Units;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.data.OilData;
import dev.minetorio.core.fluid.FluidData;
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
    private static final int MARGIN = 8;
    private static final int BOTTOM = 18;
    private static final int MIN_NODE_W = 100;
    private static final int MIN_GAP_X = 14;

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
    private int nodeW = NODE_W;
    private int gapX = GAP_X;
    private int originY;
    private TechLayout.Viewport viewport = new TechLayout.Viewport(0, 0, 0, 0, 0, 0);
    private int panButton = -1;

    public ResearchScreen() {
        super(Component.translatable("minetorio.research.title"));
    }

    @Override
    protected void init() {
        // narrower nodes and gaps when the tree is wider than the screen (names are cut to fit)
        int cols = layout.columns();
        nodeW = NODE_W;
        gapX = GAP_X;
        if (cols * NODE_W + Math.max(0, cols - 1) * GAP_X > width - 2 * MARGIN) {
            gapX = MIN_GAP_X;
            nodeW = Math.max(MIN_NODE_W, (width - 2 * MARGIN - Math.max(0, cols - 1) * gapX) / Math.max(1, cols));
        }
        int w = cols * nodeW + Math.max(0, cols - 1) * gapX;
        int h = Math.max(0, layout.rows() * (NODE_H + GAP_Y) - GAP_Y);
        viewport = new TechLayout.Viewport(width - 2 * MARGIN, height - TOP - BOTTOM, w, h, viewport.scrollX(), viewport.scrollY());
        updateOrigin();
    }

    private void updateOrigin() {
        originX = MARGIN + viewport.originX();
        originY = TOP + viewport.originY();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double deltaY) {
        if (!inViewport(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, scrollX, deltaY);
        double step = (NODE_H + GAP_Y) / 2.0;
        viewport = viewport.scrolled(-(scrollX + (hasShiftDown() ? deltaY : 0)) * step, hasShiftDown() ? 0 : -deltaY * step);
        updateOrigin();
        return true;
    }

    private boolean inViewport(double x, double y) {
        return x >= MARGIN && x < width - MARGIN && y >= TOP && y < height - BOTTOM;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private int nodeX(Technology t) { return originX + layout.cell(t.id()).column() * (nodeW + gapX); }

    private int nodeY(Technology t) { return originY + layout.cell(t.id()).row() * (NODE_H + GAP_Y); }

    private NodeState stateOf(ResearchState s, Technology t) {
        if (s.isResearched(t.id())) return NodeState.RESEARCHED;
        if (!layout.futurePrerequisites(t.id()).isEmpty()) return NodeState.LOCKED;
        if (s.current().map(c -> c.id().equals(t.id())).orElse(false)) return NodeState.CURRENT;
        return s.canResearch(t.id()) ? NodeState.AVAILABLE : NodeState.LOCKED;
    }

    /** Technology under the mouse, or null. */
    @Nullable
    private Technology techAt(double mouseX, double mouseY) {
        if (!inViewport(mouseX, mouseY)) return null;
        for (Technology t : tree.all()) {
            int x = nodeX(t);
            int y = nodeY(t);
            if (mouseX >= x && mouseX < x + nodeW && mouseY >= y && mouseY < y + NODE_H) return t;
        }
        return null;
    }

    private record Icon(ItemStack item, @Nullable String fluid) {}

    /** Output of the first recipe: a normal item, a mixed-recipe item, or the produced fluid. */
    private static Icon icon(Technology t) {
        for (String id : t.unlocksRecipes()) {
            Optional<Recipe> r = Phase1Data.recipes().get(id);
            if (r.isPresent() && !r.get().outputs().isEmpty()) return new Icon(MachineItems.stack(r.get().outputs().get(0).item(), 1), null);
            var oil = OilData.recipe(id);
            if (oil == null) continue;
            if (!oil.itemOutputs().isEmpty()) return new Icon(MachineItems.stack(oil.itemOutputs().getFirst().item(), 1), null);
            if (!oil.fluidOutputs().isEmpty()) return new Icon(ItemStack.EMPTY, oil.fluidOutputs().keySet().iterator().next());
        }
        return new Icon(ItemStack.EMPTY, null);
    }

    /** A small fluid drop in the same palette used by tank and pipe gauges. */
    private static void drawIcon(GuiGraphics g, Icon icon, int x, int y) {
        if (icon.fluid() == null) {
            g.renderItem(icon.item(), x, y);
            return;
        }
        int color = 0xFF000000 | FluidData.color(icon.fluid());
        for (int row = 0; row < 14; row++) {
            int half = row < 8 ? Math.max(1, row / 2) : row < 12 ? 5 : 3;
            g.fill(x + 8 - half, y + 1 + row, x + 8 + half, y + 2 + row, color);
        }
        var fluid = FluidData.fluid(icon.fluid());
        if (fluid != null) g.fill(x + 5, y + 9, x + 6, y + 12, 0xFF000000 | fluid.flow());
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
        g.drawString(font, Component.translatable("minetorio.research.pan_hint"), MARGIN, height - font.lineHeight - 4, TEXT_DIM, false);
        g.enableScissor(MARGIN, TOP, width - MARGIN, height - BOTTOM);
        for (Technology t : tree.all()) {
            for (String pre : t.prerequisites()) tree.get(pre).ifPresent(p -> drawLink(g, p, t, s.isResearched(p.id())));
        }
        Technology hovered = techAt(mouseX, mouseY);
        for (Technology t : tree.all()) drawNode(g, s, t, t == hovered);
        g.disableScissor();
        if (hovered != null && panButton < 0) g.renderComponentTooltip(font, tooltip(s, hovered), mouseX, mouseY);
    }

    private Component header(ResearchState s) {
        Optional<Technology> cur = s.current();
        if (cur.isEmpty()) return Component.translatable("minetorio.research.none");
        return Component.translatable("minetorio.research.current", name(cur.get()), s.unitsDone(), cur.get().units());
    }

    /** Elbow line from the right edge of {@code from} to the left edge of {@code to}. */
    private void drawLink(GuiGraphics g, Technology from, Technology to, boolean done) {
        int color = done ? LINE_DONE : LINE_OPEN;
        int x1 = nodeX(from) + nodeW;
        int y1 = nodeY(from) + NODE_H / 2;
        int x2 = nodeX(to);
        int y2 = nodeY(to) + NODE_H / 2;
        int mid = x2 - gapX / 2;
        g.hLine(x1, mid, y1, color);
        g.vLine(mid, Math.min(y1, y2) - 1, Math.max(y1, y2) + 1, color);
        g.hLine(mid, x2 - 1, y2, color);
    }

    private void drawNode(GuiGraphics g, ResearchState s, Technology t, boolean hovered) {
        NodeState state = stateOf(s, t);
        int x = nodeX(t);
        int y = nodeY(t);
        int border = hovered && state == NodeState.AVAILABLE ? TEXT : state.border;
        g.fill(x, y, x + nodeW, y + NODE_H, border);
        g.fill(x + 1, y + 1, x + nodeW - 1, y + NODE_H - 1, state.fill);
        drawIcon(g, icon(t), x + 4, y + 4);
        int textColor = state == NodeState.LOCKED ? TEXT_DIM : TEXT;
        g.drawString(font, font.plainSubstrByWidth(name(t).getString(), nodeW - 28), x + 24, y + 5, textColor, false);
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
            int bw = nodeW - 8;
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
                if (s.isResearched(pre) || tree.get(pre).isEmpty()) continue;
                if (!first) missing.append(", ");
                missing.append(Component.translatable(ResearchSavedData.nameKey(pre)));
                first = false;
            }
            if (!first) lines.add(Component.translatable("minetorio.research.requires", missing).withStyle(ChatFormatting.RED));
            List<String> future = layout.futurePrerequisites(t.id());
            if (!future.isEmpty()) {
                MutableComponent later = Component.empty();
                for (String pre : future) {
                    if (!later.getString().isEmpty()) later.append(", ");
                    later.append(Component.translatable(ResearchSavedData.nameKey(pre)));
                }
                lines.add(Component.translatable("minetorio.research.future_requires", later).withStyle(ChatFormatting.RED));
            }
        }
        lines.add(Component.translatable("minetorio.research.unlocks").withStyle(ChatFormatting.GRAY));
        for (String id : t.unlocksRecipes()) {
            Optional<Recipe> r = Phase1Data.recipes().get(id);
            if (r.isPresent() && !r.get().outputs().isEmpty()) {
                lines.add(Component.literal("  ").append(MachineItems.stack(r.get().outputs().getFirst().item(), 1).getHoverName()));
            } else {
                var oil = OilData.recipe(id);
                if (oil == null) continue;
                for (var item : oil.itemOutputs()) lines.add(Component.literal("  ").append(MachineItems.stack(item.item(), 1).getHoverName()));
                for (String fluid : oil.fluidOutputs().keySet()) lines.add(Component.literal("  ").append(Component.translatable("fluid.minetorio." + fluid)));
            }
        }
        if (state == NodeState.AVAILABLE) lines.add(Component.translatable("minetorio.research.click").withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Technology t = button == 0 ? techAt(mouseX, mouseY) : null;
        if (inViewport(mouseX, mouseY) && (button == 2 || (button == 0 && t == null))) {
            panButton = button;
            return true;
        }
        if (t != null && stateOf(ClientResearch.state(), t) == NodeState.AVAILABLE) {
            PacketDistributor.sendToServer(new SelectResearchPayload(t.id()));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (panButton != button) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        viewport = viewport.scrolled(-dx, -dy);
        updateOrigin();
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (panButton != button) return super.mouseReleased(mouseX, mouseY, button);
        panButton = -1;
        return true;
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
