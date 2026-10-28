package dev.minetorio.content.fish;

import dev.minetorio.Minetorio;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Minimal {@code minetorio:fish}: a harmless fish swimming in water, caught by the player
 * (dev.minetorio.content.player.FishCatching) for raw fish. Kept self-contained (own register, own
 * renderer in dev.minetorio.client.fish) so it can be dropped when the world-generation fish (with its
 * spawning) is merged: FishCatching only looks for the entity id {@code minetorio:fish}.
 */
public class MinimalFishEntity extends AbstractFish {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Minetorio.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MinimalFishEntity>> FISH =
            ENTITIES.register("fish", () -> EntityType.Builder.<MinimalFishEntity>of(MinimalFishEntity::new, MobCategory.WATER_AMBIENT)
                    .sized(0.5f, 0.3f)
                    .clientTrackingRange(4)
                    .build("fish"));

    public MinimalFishEntity(EntityType<? extends MinimalFishEntity> type, Level level) {
        super(type, level);
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> e.put(FISH.get(), AbstractFish.createAttributes().build()));
    }

    @Override
    public ItemStack getBucketItemStack() {
        return ItemStack.EMPTY;
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.COD_FLOP;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }
}
