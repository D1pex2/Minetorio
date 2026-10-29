package dev.minetorio.content.mixin;

import dev.minetorio.content.character.CharacterMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Construct the same extended default menu on client and server. */
@Mixin(Player.class)
public abstract class CharacterPlayerMenuMixin {
    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/inventory/InventoryMenu"))
    private InventoryMenu minetorio$menu(Inventory inventory, boolean active, Player player) {
        return new CharacterMenu(inventory, active, player);
    }
}
