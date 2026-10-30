package dev.minetorio.content.rail;

import dev.minetorio.core.data.RailData.StockKind;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import static dev.minetorio.core.rail.RailGeometry.*;
import dev.minetorio.core.rail.RailGeometry.Pose;

/** Только видимая проекция и место пассажира. Ни груз, ни топливо здесь не сохраняются. */
public final class RollingStockEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> CAR=SynchedEntityData.defineId(RollingStockEntity.class,EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(RollingStockEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEALTH=SynchedEntityData.defineId(RollingStockEntity.class,EntityDataSerializers.FLOAT);
    public RollingStockEntity(EntityType<? extends RollingStockEntity> type,Level level) { super(type,level);noPhysics=true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(CAR,Optional.empty());builder.define(KIND,0);builder.define(HEALTH,1f); }
    public UUID carId() { return entityData.get(CAR).orElse(null); }
    public void bind(UUID id) { if(carId()!=null&&!carId().equals(id))throw new IllegalStateException("Проекция уже привязана");entityData.set(CAR,Optional.of(id)); }
    public StockKind kind() { int index=entityData.get(KIND);return StockKind.values()[Math.max(0,Math.min(index,StockKind.values().length-1))]; }
    public void project(dev.minetorio.core.rail.RailwayState.Car car,Pose pose) {
        bind(car.id());entityData.set(KIND,car.kind().ordinal());entityData.set(HEALTH,(float)car.hp());
        setPos(pose.position().x(),pose.position().y()+.125,pose.position().z());
        setYRot((float)Math.toDegrees(Math.atan2(-pose.tangent().x(),pose.tangent().z())));
        for(Entity rider:List.copyOf(getPassengers()))positionRider(rider);
    }
    @Override public void tick() { super.tick();setDeltaMovement(Vec3.ZERO);if(level() instanceof ServerLevel server)RailwayManager.validateView(server,this); }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty()&&passenger instanceof Player&&kind()==StockKind.LOCOMOTIVE; }
    @Override protected void positionRider(Entity rider,MoveFunction move) {
        if(hasPassenger(rider))move.accept(rider,getX(),getY()+1.35,getZ());
    }
    // getControllingPassenger остается null: Minecraft не принимает клиентскую позу транспорта.
    @Override public InteractionResult interact(Player player,InteractionHand hand) {
        if(level().isClientSide())return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer server))return InteractionResult.FAIL;
        return RailwayManager.interact(server,this,hand)?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(level() instanceof ServerLevel server && amount>0 && Float.isFinite(amount))return RailwayManager.damageStock(server,carId(),amount*12.5);
        return false;
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { if(carId()!=null)tag.putUUID("CarId",carId()); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { if(tag.hasUUID("CarId"))entityData.set(CAR,Optional.of(tag.getUUID("CarId"))); }
}
