package dev.minetorio.client.blueprint;

import dev.minetorio.content.blueprint.BlueprintRecord;
import dev.minetorio.content.blueprint.BlueprintStacks;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The blueprint library window (key B): the personal library ({@link BlueprintLibrary}, on this
 * computer, the same in every world and server). Drag an entry onto the inventory to get a copy
 * (sent to the server, which checks it like a blueprint string); drag a blueprint or book from the
 * inventory into the grid to store a copy. Right-click or "Edit" renames and sets icons; Delete
 * (asks once more) deletes; Export / Ctrl+C copies the blueprint string; Import / Ctrl+V turns the
 * clipboard's string into a new item.
 */
public class BlueprintLibraryScreen extends BlueprintGridScreen {
    private Button delete;
    private int armedDelete = -1;

    public BlueprintLibraryScreen() {
        super(Component.translatable("minetorio.blueprint.gui.library"), 5);
    }

    @Override
    protected List<BlueprintRecord> entries() {
        return BlueprintLibrary.entries();
    }

    @Override
    protected void select(int i) {
        armedDelete = -1;
    }

    @Override
    protected void move(int from, int to) {
        BlueprintLibrary.move(from, to);
    }

    @Override
    protected void toInventory(int i, int slot) {
        Component error = BlueprintTransfer.give(entries().get(i), slot);
        message = error;
    }

    @Override
    protected void fromInventory(int slot, int at) {
        ItemStack s = minecraft.player.getInventory().getItem(slot);
        BlueprintRecord r = BlueprintStacks.record(s);
        if (r == null) return;
        message = BlueprintLibrary.add(at, r) ? Component.translatable("minetorio.blueprint.library.stored")
                : Component.translatable("minetorio.blueprint.library.full");
    }

    @Override
    protected void edit(int i) {
        BlueprintRecord r = entries().get(i);
        minecraft.setScreen(new BlueprintMetaScreen(this, () -> i < entries().size() && entries().get(i) == r ? r : null,
                m -> BlueprintLibrary.replace(i, r.withMeta(m))));
    }

    @Override
    protected void delete(int i) {
        if (armedDelete != i) {
            armedDelete = i;
            message = Component.translatable("minetorio.blueprint.library.confirm_delete");
            return;
        }
        BlueprintLibrary.remove(i);
        armedDelete = -1;
        selected = -1;
        message = null;
    }

    @Override
    protected void export() {
        BlueprintRecord r = selectedRecord();
        if (r != null) message = BlueprintTransfer.export(r);
    }

    @Override
    protected void importString() {
        message = BlueprintTransfer.importClipboard(-1);
    }

    @Override
    protected void addButtons(int y) {
        addRenderableWidget(Button.builder(Component.translatable("minetorio.blueprint.gui.import"), b -> importString())
                .bounds(buttonX(0, 2), y, buttonW(2), 18).build());
        addRenderableWidget(Button.builder(Component.translatable("minetorio.blueprint.gui.export"), b -> export())
                .bounds(buttonX(1, 2), y, buttonW(2), 18).build());
        addRenderableWidget(Button.builder(Component.translatable("minetorio.blueprint.gui.edit_entry"), b -> {
            if (selectedRecord() != null) edit(selected);
        }).bounds(buttonX(0, 2), y + 20, buttonW(2), 18).build());
        delete = Button.builder(Component.translatable("minetorio.blueprint.gui.delete"), b -> {
            if (selectedRecord() != null) delete(selected);
        }).bounds(buttonX(1, 2), y + 20, buttonW(2), 18).build();
        addRenderableWidget(delete);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (delete != null) {
            delete.setMessage(Component.translatable(armedDelete >= 0 && armedDelete == selected
                    ? "minetorio.blueprint.gui.delete_confirm" : "minetorio.blueprint.gui.delete"));
        }
        super.render(g, mx, my, pt);
        String problem = BlueprintLibrary.problem();
        if (problem != null) g.drawCenteredString(font, problem, width / 2, top - 12, 0xFFFF8060);
        if (entries().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("minetorio.blueprint.library.empty"), left + W / 2,
                    gridY() + rows * 9 - 4, BlueprintGui.TEXT_DIM);
        }
    }
}
