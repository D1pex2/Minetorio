package dev.minetorio.content.combat;

import dev.minetorio.core.combat.CombatRules;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** Стационарное гнездо. Первый срез использует эволюцию 0 и только малых кусак. */
public final class BiterSpawnerEntity extends PathfinderMob {
    private int spawnCooldown = 120;
    private final Set<UUID> owned = new HashSet<>();
    public BiterSpawnerEntity(EntityType<? extends BiterSpawnerEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, CombatRules.health(350))
                .add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }
    public int spawnCooldown() { return spawnCooldown; }
    public Set<UUID> ownedUnits() { return Collections.unmodifiableSet(owned); }
    @Override public void travel(net.minecraft.world.phys.Vec3 motion) { setDeltaMovement(0, getDeltaMovement().y, 0); super.travel(net.minecraft.world.phys.Vec3.ZERO); }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive()) return;
        Set<UUID> known = NestRoster.get(level).units(getUUID(), owned);
        owned.clear(); owned.addAll(known);
        heal(CombatRules.health(0.06));
        if (--spawnCooldown > 0) return;
        spawnCooldown = 120; // 360 тиков Factorio при нулевой эволюции.
        trySpawn(level);
    }
    private void trySpawn(ServerLevel level) {
        // Не забываем выгруженных потомков: их отсутствие в level не означает гибель.
        List<SmallBiterEntity> friends = level.getEntitiesOfClass(SmallBiterEntity.class, getBoundingBox().inflate(10), LivingEntity::isAlive);
        if (owned.size() >= 7 || friends.size() >= 5) return;
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 4 + random.nextDouble() * 6;
            int x = (int) Math.floor(getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(getZ() + Math.sin(angle) * radius);
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            SmallBiterEntity biter = CombatRegistry.SMALL_BITER.get().create(level);
            if (biter == null) return;
            biter.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
            if (!level.getFluidState(biter.blockPosition().below()).isEmpty() || !level.noCollision(biter)
                    || friends.stream().anyMatch(f -> f.distanceToSqr(biter) < 9)) continue;
            biter.nest(getUUID());
            if (level.addFreshEntity(biter)) {
                owned.add(biter.getUUID());
                NestRoster.get(level).born(getUUID(), biter.getUUID());
            }
            return;
        }
    }
    public void childDied(UUID child) { owned.remove(child); }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.is(CombatManager.BULLET)) amount = CombatRules.health(CombatRules.physicalDamage(amount / CombatRules.HEALTH_SCALE, 2, 15));
        return super.hurt(source, amount);
    }
    @Override protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {}
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SpawnCooldown", spawnCooldown);
        ListTag units = new ListTag();
        for (UUID id : owned) units.add(NbtUtils.createUUID(id));
        tag.put("OwnedUnits", units);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        spawnCooldown = tag.contains("SpawnCooldown") ? tag.getInt("SpawnCooldown") : 120;
        owned.clear();
        for (Tag id : tag.getList("OwnedUnits", Tag.TAG_INT_ARRAY)) owned.add(NbtUtils.loadUUID(id));
    }
}
