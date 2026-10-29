package dev.minetorio.client.logistics;

import dev.minetorio.content.logistics.PersonalLogisticsMenu;
import dev.minetorio.content.logistics.RequestMenu;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Personal logistics GUI (key {@code K}): two rows of request slots with a minimum and an optional
 * maximum each (above it the rest is trash that robots take to storage), the "personal logistics"
 * on/off toggle and the inventory to pick items from.
 */
public class PersonalLogisticsScreen extends RequestScreen<PersonalLogisticsMenu> {
    private Button toggle;

    public PersonalLogisticsScreen(PersonalLogisticsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PersonalLogisticsMenu.INVENTORY_Y + 152);
    }

    @Override
    protected int editY() { return PersonalLogisticsMenu.EDIT_Y; }

    @Override
    protected void init() {
        super.init();
        toggle = addRenderableWidget(Button.builder(toggleText(), b -> clickButton(RequestMenu.BUTTON_TOGGLE))
                .bounds(leftPos + 8, topPos + PersonalLogisticsMenu.EDIT_Y + 16, 100, 12).build());
    }

    private Component toggleText() {
        return Component.translatable(menu.toggle() ? "minetorio.personal_logistics.on" : "minetorio.personal_logistics.off");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (toggle != null) toggle.setMessage(toggleText());
    }
}
