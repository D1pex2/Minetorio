package dev.minetorio.client.blueprint;

import dev.minetorio.content.blueprint.BlueprintBookOps;
import dev.minetorio.content.blueprint.BlueprintBookOps.Op;
import dev.minetorio.content.blueprint.BlueprintPayloads;
import dev.minetorio.content.blueprint.BlueprintRecord;
import dev.minetorio.content.blueprint.BlueprintStacks;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The blueprint book window (right-click a book in the inventory): the book's entries, the active
 * one framed blue. Click an entry to make it active; double-click a book inside to open it ("Up"
 * goes back); drag to reorder, onto the inventory to take out, from the inventory to put in;
 * right-click or "Edit" for an entry's name and icons, "Rename book" for the book's own; Delete
 * deletes, Export / Ctrl+C copies the selected entry's string (the open book's with nothing
 * selected), Ctrl+V imports a string as a new item. Every change goes to the server
 * ({@link BlueprintBookOps}); the window shows the synced item.
 */
public class BlueprintBookScreen extends BlueprintGridScreen {
    @Nullable
    private final Screen parent;
    private final int slot;
    private final List<Integer> path = new ArrayList<>();
    private Button up;

    public BlueprintBookScreen(@Nullable Screen parent, int slot) {
        super(Component.translatable("item.minetorio.blueprint_book"), 4);
        this.parent = parent;
        this.slot = slot;
    }

    /** The open (sub-)book, or null when the item is gone. */
    @Nullable
    private BlueprintRecord book() {
        BlueprintRecord root = minecraft == null || minecraft.player == null ? null
                : BlueprintStacks.record(minecraft.player.getInventory().getItem(slot));
        if (root == null || !root.isBook()) return null;
        BlueprintRecord r = BlueprintBookOps.at(root, path);
        while (r == null || !r.isBook()) { // the sub-book went away: back up
            if (path.isEmpty()) return null;
            path.removeLast();
            r = BlueprintBookOps.at(root, path);
        }
        return r;
    }

    @Override
    public Component getTitle() {
        BlueprintRecord b = book();
        return b == null ? super.getTitle() : BlueprintStacks.name(b);
    }

    @Override
    public void tick() {
        if (book() == null) onClose();
        if (up != null) up.active = !path.isEmpty();
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
        BlueprintRecord b = book();
        if (b == null) return;
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void send(Op op, int a, int b) {
        PacketDistributor.sendToServer(new BlueprintPayloads.BookEdit(slot, List.copyOf(path), op.ordinal(), a, b));
    }

    @Override
    protected List<BlueprintRecord> entries() {
        BlueprintRecord b = book();
        return b == null ? List.of() : b.book().get().entries();
    }

    @Override
    protected int active() {
        BlueprintRecord b = book();
        return b == null ? -1 : b.book().get().active();
    }

    @Override
    protected int lockedSlot() { return slot; }

    @Override
    protected void select(int i) { send(Op.SELECT, i, 0); }

    @Override
    protected void move(int from, int to) { send(Op.MOVE, from, to); }

    @Override
    protected void toInventory(int i, int invSlot) { send(Op.EXTRACT, i, invSlot); }

    @Override
    protected void fromInventory(int invSlot, int at) { send(Op.INSERT, invSlot, at); }

    @Override
    protected void open(int i) {
        List<BlueprintRecord> l = entries();
        if (i < l.size() && l.get(i).isBook() && path.size() + 1 < dev.minetorio.core.blueprint.BlueprintBooks.MAX_DEPTH) {
            path.add(i);
            selected = -1;
        }
    }

    @Override
    protected void edit(int i) {
        List<Integer> p = new ArrayList<>(path);
        p.add(i);
        minecraft.setScreen(BlueprintMetaScreen.forSlot(this, slot, p));
    }

    @Override
    protected void delete(int i) {
        send(Op.REMOVE, i, 0);
        selected = -1;
    }

    @Override
    protected void export() {
        BlueprintRecord r = selectedRecord();
        if (r == null) r = book();
        if (r != null) message = BlueprintTransfer.export(r);
    }

    @Override
    protected void importString() {
        message = BlueprintTransfer.importClipboard(-1);
    }

    @Override
    protected void addButtons(int y) {
        up = Button.builder(Component.translatable("minetorio.blueprint.gui.up"), b -> {
            if (!path.isEmpty()) {
                path.removeLast();
                selected = -1;
            }
        }).bounds(buttonX(0, 2), y, buttonW(2), 18).build();
        up.active = !path.isEmpty();
        addRenderableWidget(up);
        addRenderableWidget(Button.builder(Component.translatable("minetorio.blueprint.gui.rename_book"),
                b -> minecraft.setScreen(BlueprintMetaScreen.forSlot(this, slot, List.copyOf(path)))).bounds(buttonX(1, 2), y, buttonW(2), 18).build());
        addRenderableWidget(Button.builder(Component.translatable("minetorio.blueprint.gui.edit_entry"), b -> {
            if (selectedRecord() != null) edit(selected);
        }).bounds(buttonX(0, 3), y + 20, buttonW(3), 18).build());
        addRenderableWidget(Button.builder(Component.translatable("minetorio.blueprint.gui.delete"), b -> {
            if (selectedRecord() != null) delete(selected);
        }).bounds(buttonX(1, 3), y + 20, buttonW(3), 18).build());
        addRenderableWidget(Button.builder(Component.translatable("minetorio.blueprint.gui.export"), b -> export())
                .bounds(buttonX(2, 3), y + 20, buttonW(3), 18).build());
    }
}
