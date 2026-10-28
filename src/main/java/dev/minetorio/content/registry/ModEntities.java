package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import dev.minetorio.content.robot.ConstructionRobotEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Minetorio.MODID);

    /**
     * Half a block. Clients simulate the flight from the synced target and speed (see
     * ConstructionRobotEntity), so position packets only correct drift: every 10 ticks is plenty.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ConstructionRobotEntity>> CONSTRUCTION_ROBOT =
            ENTITIES.register("construction_robot", () -> EntityType.Builder
                    .<ConstructionRobotEntity>of(ConstructionRobotEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .fireImmune()
                    .build("construction_robot"));

    private ModEntities() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
