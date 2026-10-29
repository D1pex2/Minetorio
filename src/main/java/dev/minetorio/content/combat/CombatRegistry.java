package dev.minetorio.content.combat;

import dev.minetorio.Minetorio;
import dev.minetorio.core.combat.CombatRules;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.*;

public final class CombatRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Minetorio.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Minetorio.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Minetorio.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Minetorio.MODID);
    public static final DeferredItem<GunItem> PISTOL = ITEMS.registerItem("pistol", p -> new GunItem(p.stacksTo(5), CombatRules.Gun.PISTOL));
    public static final DeferredItem<GunItem> SUBMACHINE_GUN = ITEMS.registerItem("submachine_gun", p -> new GunItem(p.stacksTo(5), CombatRules.Gun.SUBMACHINE_GUN));
    public static final DeferredItem<Item> FIREARM_MAGAZINE = ITEMS.registerSimpleItem("firearm_magazine", new Item.Properties().stacksTo(200));
    /** Остаток активного магазина на одиночном оружии; полный стек магазинов не меняет компоненты. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ROUNDS = COMPONENTS.registerComponentType("magazine_rounds",
            b -> b.persistent(com.mojang.serialization.Codec.intRange(1, 10)).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<EntityType<?>, EntityType<SmallBiterEntity>> SMALL_BITER = ENTITIES.register("small_biter",
            () -> EntityType.Builder.of(SmallBiterEntity::new, MobCategory.MISC).sized(0.8f, 0.65f).clientTrackingRange(10).build("small_biter"));
    public static final DeferredHolder<EntityType<?>, EntityType<BiterSpawnerEntity>> BITER_SPAWNER = ENTITIES.register("biter_spawner",
            () -> EntityType.Builder.of(BiterSpawnerEntity::new, MobCategory.MISC).sized(5.4f, 2.2f).clientTrackingRange(12).build("biter_spawner"));
    public static final DeferredHolder<SoundEvent, SoundEvent> PISTOL_SHOT = sound("pistol_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUBMACHINE_SHOT = sound("submachine_shot");
    public static final List<String> ITEM_IDS = List.of("pistol", "submachine_gun", "firearm_magazine");
    private CombatRegistry() {}
    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, id)));
    }
    public static void register(IEventBus bus) {
        ITEMS.register(bus); ENTITIES.register(bus); COMPONENTS.register(bus); SOUNDS.register(bus);
        bus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(SMALL_BITER.get(), SmallBiterEntity.attributes().build());
            e.put(BITER_SPAWNER.get(), BiterSpawnerEntity.attributes().build());
        });
        CombatManager.register();
        CombatWorld.register(bus);
    }
}
