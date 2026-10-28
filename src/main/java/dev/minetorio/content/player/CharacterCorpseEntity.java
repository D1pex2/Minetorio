package dev.minetorio.content.player;

import dev.minetorio.core.player.CharacterRules;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Factorio's character corpse: what a dead player carried (main inventory, hotbar, offhand, armour),
 * left where they died. Anyone can open it (right-click: a chest view) or take everything at once
 * (sneak + right-click). It vanishes once empty, or after {@link MinetorioGameRules#CORPSE_MINUTES}
 * of world time with what is still inside. Holds up to 54 stacks; a bigger inventory leaves several.
 */
public class CharacterCorpseEntity extends Entity {
    public static final int MAX_STACKS = 54;

    private int rows = 6;
    private SimpleContainer items = new CorpseContainer(rows * 9);
    /** Game time the corpse was made (−1 until known). */
    private long createdAt = -1;

    public CharacterCorpseEntity(EntityType<? extends CharacterCorpseEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** A corpse at the player's position holding these stacks (at most {@link #MAX_STACKS}). */
    public static CharacterCorpseEntity of(Player owner, List<ItemStack> stacks) {
        CharacterCorpseEntity c = new CharacterCorpseEntity(PlayerBasics.CHARACTER_CORPSE.get(), owner.level());
        c.rows = CharacterRules.corpseRows(stacks.size());
        c.items = c.new CorpseContainer(c.rows * 9);
        for (int i = 0; i < stacks.size() && i < c.rows * 9; i++) c.items.setItem(i, stacks.get(i));
        double y = Math.max(owner.getY(), owner.level().getMinBuildHeight() + 1);
        c.moveTo(owner.getX(), y, owner.getZ(), owner.getYRot(), 0);
        c.createdAt = owner.level().getGameTime();
        c.setCustomName(Component.translatable("entity.minetorio.character_corpse.of", owner.getDisplayName()));
        c.setCustomNameVisible(true);
        return c;
    }

    public SimpleContainer items() {
        return items;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (createdAt < 0) createdAt = level().getGameTime();
        long lifetime = CharacterRules.corpseLifetimeTicks(level().getGameRules().getInt(MinetorioGameRules.CORPSE_MINUTES));
        if (items.isEmpty() || level().getGameTime() - createdAt > lifetime) discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            takeAll(player);
        } else {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new ChestMenu(menuType(rows), id, inv, items, rows), getDisplayName()));
        }
        return InteractionResult.CONSUME;
    }

    /** Moves everything that fits into the player's inventory; the corpse goes away once empty. */
    public void takeAll(Player player) {
        for (int i = 0; i < items.getContainerSize(); i++) {
            ItemStack s = items.getItem(i);
            if (s.isEmpty()) continue;
            player.getInventory().add(s);
            items.setItem(i, s.isEmpty() ? ItemStack.EMPTY : s);
        }
        if (items.isEmpty()) discard();
    }

    private static MenuType<?> menuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        rows = Math.max(1, Math.min(6, tag.getInt("Rows")));
        items = new CorpseContainer(rows * 9);
        NonNullList<ItemStack> list = NonNullList.withSize(rows * 9, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, registryAccess());
        for (int i = 0; i < list.size(); i++) items.setItem(i, list.get(i));
        createdAt = tag.contains("CreatedAt") ? tag.getLong("CreatedAt") : -1;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Rows", rows);
        NonNullList<ItemStack> list = NonNullList.withSize(rows * 9, ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) list.set(i, items.getItem(i));
        ContainerHelper.saveAllItems(tag, list, registryAccess());
        tag.putLong("CreatedAt", createdAt);
    }

    /** The corpse's slots; usable while the corpse exists and the player is near. */
    private class CorpseContainer extends SimpleContainer {
        CorpseContainer(int size) {
            super(size);
        }

        @Override
        public boolean stillValid(Player player) {
            return CharacterCorpseEntity.this.isAlive() && player.distanceToSqr(CharacterCorpseEntity.this) < 64;
        }
    }
}
