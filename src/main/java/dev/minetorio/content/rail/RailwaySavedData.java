package dev.minetorio.content.rail;

import dev.minetorio.core.rail.RailwayState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Хранилище измерения; существующий поврежденный файл никогда не заменяется пустой моделью. */
public final class RailwaySavedData extends SavedData {
    public static final String NAME = "minetorio_railway";
    private final RailwayState state;

    private RailwaySavedData(HolderLookup.Provider registries) {
        state = new RailwayState(key -> RailItemKeys.limit(key, registries), this::changed);
    }
    private RailwaySavedData(CompoundTag tag, HolderLookup.Provider registries) {
        state = RailwayNbt.read(tag, registries, this::changed);
        setDirty(false);
    }
    public RailwayState state() { return state; }
    public void changed() { setDirty(); }

    public static RailwaySavedData get(ServerLevel level) {
        Path folder = DimensionType.getStorageFolder(level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT)).resolve("data");
        return get(level.getDataStorage(), folder, level.registryAccess());
    }

    /** Та же граница для изолированного storage; folder должен быть его реальной папкой data. */
    public static RailwaySavedData get(DimensionDataStorage storage, Path dataFolder, HolderLookup.Provider registries) {
        Path file = dataFolder.resolve(NAME + ".dat");
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        return storage.computeIfAbsent(new SavedData.Factory<>(() -> {
            // readSavedData платформы глотает Exception и возвращает null. Отсутствие файла
            // подтверждаем отдельно; exists=false недостаточно при неизвестных правах доступа.
            if (!Files.notExists(file, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalStateException("Не удалось загрузить железную дорогу из " + file
                        + "; исходный файл сохранен, создание пустой модели запрещено", failure.get());
            RailwaySavedData created = new RailwaySavedData(registries);
            created.changed();
            return created;
        }, (tag, lookup) -> {
            try { return new RailwaySavedData(tag, lookup); }
            catch (RuntimeException invalid) { failure.set(invalid); throw invalid; }
        }), NAME);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return tag.merge(RailwayNbt.write(state, registries));
    }
}
