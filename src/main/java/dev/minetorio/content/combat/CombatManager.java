package dev.minetorio.content.combat;

import dev.minetorio.Minetorio;
import dev.minetorio.content.character.CharacterInventory;
import dev.minetorio.content.map.RemoteView;
import dev.minetorio.content.player.CharacterHealth;
import dev.minetorio.core.combat.CombatRules;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Единственное место расхода патронов и серверного урона оружия. */
public final class CombatManager {
    public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "bullet"));
    public static final ResourceKey<DamageType> BITE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "bite"));
    private static final Map<UUID, Long> NEXT_SHOT = new HashMap<>();
    private static final Set<UUID> CAMERA_BLOCKED = new HashSet<>();
    public static final ResourceLocation SLOWDOWN = ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "shooting_slowdown");
    private CombatManager() {}
    public enum Result { FIRED, FORBIDDEN, COOLDOWN, NO_AMMO }
    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            NEXT_SHOT.remove(e.getEntity().getUUID()); CAMERA_BLOCKED.remove(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> { NEXT_SHOT.clear(); CAMERA_BLOCKED.clear(); });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) tick(player);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.AttackEntityEvent e) -> {
            if (e.getEntity().getMainHandItem().getItem() instanceof GunItem) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock e) -> {
            if (e.getEntity().getMainHandItem().getItem() instanceof GunItem) e.setCanceled(true);
        });
    }
    /** Клиент может только дополнительно запретить стрельбу свободной камерой. */
    public static void cameraBlocked(ServerPlayer player, boolean blocked) {
        if (blocked) CAMERA_BLOCKED.add(player.getUUID()); else CAMERA_BLOCKED.remove(player.getUUID());
    }
    public static DamageSource source(LivingEntity attacker, ResourceKey<DamageType> key) {
        return new DamageSource(attacker.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), attacker);
    }
    /** movement_slow_down_cooldown по API 1.1.110 равен cooldown оружия. Модификатор не сохраняется. */
    public static void tick(ServerPlayer player) {
        if (player.serverLevel().getGameTime() >= NEXT_SHOT.getOrDefault(player.getUUID(), Long.MIN_VALUE)) {
            var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.removeModifier(SLOWDOWN);
        }
    }
    public static Result fire(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || RemoteView.isRemote(player) || CAMERA_BLOCKED.contains(player.getUUID())
                || player.containerMenu != player.inventoryMenu || player.getMainHandItem().getCount() != 1
                || !(player.getMainHandItem().getItem() instanceof GunItem weapon)) return Result.FORBIDDEN;
        long now = player.serverLevel().getGameTime();
        if (!CombatRules.canFire(now, NEXT_SHOT.getOrDefault(player.getUUID(), Long.MIN_VALUE))) return Result.COOLDOWN;
        if (!consumeAmmo(player)) return Result.NO_AMMO;
        NEXT_SHOT.put(player.getUUID(), now + weapon.gun.cooldownTicks);
        var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOWDOWN);
            speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SLOWDOWN, -weapon.gun.slowDown,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        CharacterHealth.markDamaged(player);
        Vec3 from = player.getEyePosition();
        Vec3 end = from.add(player.getLookAngle().scale(weapon.gun.range));
        var level = player.serverLevel();
        BlockHitResult block = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 impact = block.getLocation();
        double nearest = from.distanceToSqr(impact);
        LivingEntity target = null;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().expandTowards(end.subtract(from)).inflate(1),
                e -> e != player && e.isAlive() && e.isPickable() && !e.isSpectator())) {
            Optional<Vec3> hit = candidate.getBoundingBox().clip(from, end);
            if (hit.isPresent() && from.distanceToSqr(hit.get()) < nearest) {
                nearest = from.distanceToSqr(hit.get()); target = candidate; impact = hit.get();
            }
        }
        boolean hitEntity = target != null;
        if (target != null) target.hurt(source(player, BULLET), CombatRules.health(5));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new CombatPayloads.Shot(player.getId(), from.x, from.y, from.z, impact.x, impact.y, impact.z, hitEntity,
                        weapon.gun == CombatRules.Gun.SUBMACHINE_GUN));
        return Result.FIRED;
    }
    public static int rounds(ItemStack stack) {
        if (!stack.is(CombatRegistry.FIREARM_MAGAZINE.get())) return 0;
        return Math.max(1, Math.min(10, stack.getOrDefault(CombatRegistry.ROUNDS.get(), 10)));
    }
    /** Сначала активный магазин оружия; затем один магазин из всех 80 слотов. */
    private static boolean consumeAmmo(ServerPlayer player) {
        ItemStack weapon = player.getMainHandItem();
        int loaded = weapon.getOrDefault(CombatRegistry.ROUNDS.get(), 0);
        if (loaded > 0) {
            if (loaded == 1) {
                weapon.remove(CombatRegistry.ROUNDS.get());
                weapon.remove(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE);
            } else weapon.set(CombatRegistry.ROUNDS.get(), loaded - 1);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            return true;
        }
        Container inventory = CharacterInventory.main(player.getInventory());
        int selected = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(CombatRegistry.FIREARM_MAGAZINE.get())) continue;
            if (selected < 0) selected = i;
            if (stack.has(CombatRegistry.ROUNDS.get())) { selected = i; break; }
        }
        if (selected < 0) return false;
        ItemStack stack = inventory.getItem(selected);
        int left = CombatRules.consumeRound(rounds(stack));
        // Активный магазин хранится на оружии, поэтому полный рюкзак не выбрасывает патроны.
        if (weapon.getCount() > 1) return false;
        stack.shrink(1);
        if (stack.isEmpty()) inventory.setItem(selected, ItemStack.EMPTY);
        if (left > 0) {
            weapon.set(CombatRegistry.ROUNDS.get(), left);
            weapon.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 1);
        }
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
        return true;
    }
}
