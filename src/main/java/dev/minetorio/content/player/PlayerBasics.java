package dev.minetorio.content.player;

import dev.minetorio.Minetorio;
import dev.minetorio.content.fish.MinimalFishEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Factorio player instead of the vanilla one: no vanilla recipes ({@link VanillaRecipeFilter}), no mobs
 * and no Nether/End ({@link NoVanillaWorld}), no hunger and Factorio regeneration ({@link CharacterHealth}),
 * fish caught by hand and eaten to heal ({@link FishCatching}, {@link RawFishItem}), a corpse on death
 * ({@link CorpseOnDeath}) and Factorio-length days ({@link DayLength}). Settings: {@link MinetorioGameRules}.
 */
public final class PlayerBasics {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Minetorio.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CharacterCorpseEntity>> CHARACTER_CORPSE =
            ENTITIES.register("character_corpse", () -> EntityType.Builder
                    .<CharacterCorpseEntity>of(CharacterCorpseEntity::new, MobCategory.MISC)
                    .sized(0.9f, 0.6f)
                    .clientTrackingRange(8)
                    .updateInterval(40)
                    .fireImmune()
                    .build("character_corpse"));

    private PlayerBasics() {}

    public static void register(IEventBus modBus) {
        MinetorioGameRules.init();
        ENTITIES.register(modBus);
        MinimalFishEntity.register(modBus);
        VanillaRecipeFilter.register();
        NoVanillaWorld.register();
        CharacterHealth.register();
        FishCatching.register();
        CorpseOnDeath.register();
        DayLength.register();
    }
}
