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
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import static dev.minetorio.core.rail.RailGeometry.*;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Хранилище измерения; существующий поврежденный файл никогда не заменяется пустой моделью. */
public final class RailwaySavedData extends SavedData {
    public static final String NAME = "minetorio_railway";
    private final RailwayState state;
    public record WorldStop(UUID id, BlockPos position, String name, boolean enabled, OptionalInt limit,
                            Point railOrigin, Shape shape, int rotation, boolean mirrored, boolean forward, double distance) {
        public WorldStop {
            Objects.requireNonNull(id);position=position.immutable();Objects.requireNonNull(railOrigin);Objects.requireNonNull(shape);Objects.requireNonNull(limit);
            if(name==null||name.isBlank()||rotation<0||rotation>3||!Double.isFinite(distance)||distance<0||limit.isPresent()&&limit.getAsInt()<0)
                throw new IllegalArgumentException("Неверная мировая станция");
        }
    }
    private final Map<UUID,WorldStop> stops=new LinkedHashMap<>();
    public Map<UUID,WorldStop> worldStops() { return Collections.unmodifiableMap(stops); }
    public void putStop(WorldStop stop) { stops.put(stop.id(),stop); rebindStops(); changed(); }
    public boolean removeStop(UUID id) { if(stops.remove(id)==null)return false;state.dispatcher().removeStation(id);changed();return true; }
    public void rebindStops() {
        for(WorldStop stop:stops.values()) {
            Optional<Piece> rail=state.rails().stream().filter(p->p.origin().equals(stop.railOrigin())&&p.shape()==stop.shape()&&p.quarterTurns()==stop.rotation()&&p.mirrored()==stop.mirrored()).findFirst();
            if(rail.isPresent()&&stop.distance()<=length(rail.get())) state.dispatcher().upsertStation(new RailwayState.Station(stop.id(),stop.name(),new Cursor(new Traversal(rail.get().id(),stop.forward()),stop.distance()),stop.enabled(),stop.limit()));
            else state.dispatcher().removeStation(stop.id());
        }
    }

    private RailwaySavedData(HolderLookup.Provider registries) {
        state = new RailwayState(key -> RailItemKeys.limit(key, registries), this::changed);
    }
    private RailwaySavedData(CompoundTag tag, HolderLookup.Provider registries) {
        state = RailwayNbt.read(tag, registries, this::changed);
        if(tag.getInt("schemaVersion")>=5) {
            if(!tag.contains("worldStops",Tag.TAG_LIST)) throw new IllegalArgumentException("Отсутствует worldStops");
            ListTag saved=(ListTag)tag.get("worldStops");if(!saved.isEmpty()&&saved.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Неверный список worldStops");
            for(Tag raw:saved) {
                CompoundTag t=(CompoundTag)raw;
                if(!t.hasUUID("id")||!t.contains("pos",Tag.TAG_LONG)||!t.contains("name",Tag.TAG_STRING)||!t.contains("shape",Tag.TAG_STRING)||!t.contains("distance",Tag.TAG_DOUBLE))throw new IllegalArgumentException("Неверная мировая станция");
                for(String key:List.of("rx","ry","rz","rotation"))if(!t.contains(key,Tag.TAG_INT))throw new IllegalArgumentException("Отсутствует поле станции "+key);
                for(String key:List.of("enabled","mirrored","forward"))if(!t.contains(key,Tag.TAG_BYTE)||t.getByte(key)<0||t.getByte(key)>1)throw new IllegalArgumentException("Неверный флаг станции "+key);
                if(t.contains("limit")&&!t.contains("limit",Tag.TAG_INT))throw new IllegalArgumentException("Неверный лимит станции");
                WorldStop stop=new WorldStop(t.getUUID("id"),BlockPos.of(t.getLong("pos")),t.getString("name"),t.getBoolean("enabled"),
                        t.contains("limit")?OptionalInt.of(t.getInt("limit")):OptionalInt.empty(),new Point(t.getInt("rx"),t.getInt("ry"),t.getInt("rz")),
                        Shape.valueOf(t.getString("shape")),t.getInt("rotation"),t.getBoolean("mirrored"),t.getBoolean("forward"),t.getDouble("distance"));
                if(stops.put(stop.id(),stop)!=null) throw new IllegalArgumentException("Повторная мировая станция");
            }
        }
        rebindStops(); setDirty(false);
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
        tag.merge(RailwayNbt.write(state, registries)); ListTag list=new ListTag();
        for(WorldStop stop:stops.values()) {
            CompoundTag t=new CompoundTag();t.putUUID("id",stop.id());t.putLong("pos",stop.position().asLong());t.putString("name",stop.name());t.putBoolean("enabled",stop.enabled());
            stop.limit().ifPresent(value->t.putInt("limit",value));t.putInt("rx",stop.railOrigin().x());t.putInt("ry",stop.railOrigin().y());t.putInt("rz",stop.railOrigin().z());
            t.putString("shape",stop.shape().name());t.putInt("rotation",stop.rotation());t.putBoolean("mirrored",stop.mirrored());t.putBoolean("forward",stop.forward());t.putDouble("distance",stop.distance());list.add(t);
        }
        tag.put("worldStops",list);return tag;
    }
}
