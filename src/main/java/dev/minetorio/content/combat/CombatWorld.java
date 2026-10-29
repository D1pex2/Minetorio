package dev.minetorio.content.combat;

import dev.minetorio.Minetorio;
import dev.minetorio.content.world.NauvisDensity;
import dev.minetorio.content.registry.ModWorld;
import dev.minetorio.core.combat.NestPlacement;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.*;

/** Только новые чанки Наувиса. Очередь не запрашивает загрузку чанков или билеты. */
public final class CombatWorld {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Minetorio.MODID);
    /** 0: старый чанк, 1: новая кандидатура ожидает FULL, 2: размещение завершено. */
    public static final java.util.function.Supplier<AttachmentType<Integer>> GENERATION = TYPES.register("nest_generation",
            () -> AttachmentType.builder(() -> 0).serialize(com.mojang.serialization.Codec.INT).build());
    private static final Map<ServerLevel, Set<ChunkPos>> QUEUED = new IdentityHashMap<>();
    private CombatWorld() {}
    public static void register(IEventBus bus) {
        TYPES.register(bus);
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load e) -> {
            if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
            var chunk = e.getChunk(); var pos = chunk.getPos();
            if (e.isNewChunk() && NestPlacement.candidate(level.getSeed(), pos.x, pos.z)) {
                chunk.setData(GENERATION, 1);
                chunk.setUnsaved(true);
            }
            if (chunk.getData(GENERATION) == 1) QUEUED.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(pos);
        });
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload e) -> {
            if (e.getLevel() instanceof ServerLevel level && QUEUED.containsKey(level)) QUEUED.get(level).remove(e.getChunk().getPos());
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (!(e.getLevel() instanceof ServerLevel level)) return;
            Set<ChunkPos> queue = QUEUED.get(level);
            if (queue == null) return;
            int budget = 4;
            for (Iterator<ChunkPos> it = queue.iterator(); it.hasNext() && budget-- > 0;) {
                ChunkPos pos = it.next();
                var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
                if (chunk == null) continue;
                it.remove();
                if (chunk.getData(GENERATION) != 1) continue;
                chunk.setData(GENERATION, 2);
                chunk.setUnsaved(true);
                if (NauvisDensity.terrain(level) == null) continue;
                int x = pos.getMinBlockX() + 8, z = pos.getMinBlockZ() + 8;
                BlockPos spawn = level.getSharedSpawnPos();
                if (!NestPlacement.outsideStart(x, z, spawn.getX(), spawn.getZ())) continue;
                int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 8, 8) + 1;
                BlockPos ground = new BlockPos(x, y - 1, z);
                if (!level.getBlockState(ground).is(ModWorld.NAUVIS_GROUND)) continue;
                var nest = CombatRegistry.BITER_SPAWNER.get().create(level);
                if (nest == null) continue;
                nest.moveTo(x + 0.5, y, z + 0.5);
                if (level.noCollision(nest)) level.addFreshEntity(nest);
            }
        });
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (!(e.getEntity().level() instanceof ServerLevel level)) return;
            if (e.getEntity() instanceof BiterSpawnerEntity nest) NestRoster.get(level).destroyed(nest.getUUID());
            if (e.getEntity() instanceof SmallBiterEntity b && b.nest() != null) {
                NestRoster.get(level).died(b.nest(), b.getUUID());
                if (level.getEntity(b.nest()) instanceof BiterSpawnerEntity nest) nest.childDied(b.getUUID());
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> QUEUED.clear());
    }
}
