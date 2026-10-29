package dev.minetorio.content.combat;

import dev.minetorio.core.combat.CombatRules;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Собственный малый кусака: погоня и укус без ванильного монстра в роли замены. */
public final class SmallBiterEntity extends PathfinderMob {
    private UUID nest;
    private long nextBite;
    public SmallBiterEntity(EntityType<? extends SmallBiterEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, CombatRules.health(15))
                .add(Attributes.MOVEMENT_SPEED, 0.6).add(Attributes.FOLLOW_RANGE, 30).add(Attributes.ATTACK_DAMAGE, CombatRules.health(7));
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    public UUID nest() { return nest; }
    public void nest(UUID owner) { nest = owner; }
    /** Расстояние между границами, а не центрами существ. */
    public boolean withinBiteRange(LivingEntity target) {
        var a = getBoundingBox(); var b = target.getBoundingBox();
        double dx = Math.max(0, Math.max(b.minX - a.maxX, a.minX - b.maxX));
        double dy = Math.max(0, Math.max(b.minY - a.maxY, a.minY - b.maxY));
        double dz = Math.max(0, Math.max(b.minZ - a.maxZ, a.minZ - b.maxZ));
        return dx * dx + dy * dy + dz * dz <= 0.25;
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide() || !isAlive()) return;
        heal(CombatRules.health(0.03));
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || target instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        getLookControl().setLookAt(target, 30, 30);
        if (tickCount % 5 == 0) getNavigation().moveTo(target, 1);
        long now = level().getGameTime() * 3;
        if (withinBiteRange(target) && hasLineOfSight(target) && now >= nextBite) {
            // Сохраняется дробная часть 35/3 MC тиков и отклонение оригинала 15%.
            long base = nextBite > now - 6 ? nextBite : now;
            nextBite = base + Math.round(35 * (0.85 + random.nextDouble() * 0.3));
            target.hurt(CombatManager.source(this, CombatManager.BITE), CombatRules.health(7));
        }
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && source.getEntity() instanceof LivingEntity attacker) setTarget(attacker);
        return hurt;
    }
    @Override protected void dropAllDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source) {}
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (nest != null) tag.putUUID("MinetorioNest", nest);
        tag.putLong("NextBite", nextBite);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        nest = tag.hasUUID("MinetorioNest") ? tag.getUUID("MinetorioNest") : null;
        nextBite = tag.getLong("NextBite");
    }
}
