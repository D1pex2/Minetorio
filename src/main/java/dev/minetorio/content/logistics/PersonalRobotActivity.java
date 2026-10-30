package dev.minetorio.content.logistics;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.*;
import com.mojang.logging.LogUtils;

/** Индекс активности; движение и груз по-прежнему принадлежат каноническим entity NBT. */
public final class PersonalRobotActivity extends SavedData {
    private static final String NAME="minetorio_personal_robot_activity";
    private static final Map<MinecraftServer,PersonalRobotCensus.Result> STARTUP=new WeakHashMap<>();
    private final Map<UUID,PersonalRobotCensus.Entry> robots=new LinkedHashMap<>();
    private final Map<UUID,LogisticRobotEntity> live=new HashMap<>();
    private boolean complete,imported;
    private PersonalRobotActivity() {}
    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event)->{
            var result=PersonalRobotCensus.scan(event.getServer().getWorldPath(LevelResource.ROOT));STARTUP.put(event.getServer(),result);
            LogUtils.getLogger().info("Personal robot census: complete={}, files={}, chunks={}, bytes={}, elapsedMs={}, error={}",result.complete(),result.files(),result.chunks(),result.bytes(),result.nanos()/1_000_000.0,result.error());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event)->STARTUP.remove(event.getServer()));
    }
    private static PersonalRobotActivity get(MinecraftServer server) {
        Path file=server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(NAME+".dat");AtomicReference<RuntimeException> failure=new AtomicReference<>();
        var data=server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(()->{
            if(!Files.notExists(file,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Поврежден индекс активности, исходный файл сохранен: "+file,failure.get());
            return new PersonalRobotActivity();
        },(tag,registries)->{try{return read(tag);}catch(RuntimeException error){failure.set(error);throw error;}}),NAME);
        if(!data.imported) {
            data.imported=true;var census=STARTUP.get(server);data.complete=census!=null&&census.complete();
            if(data.complete){data.robots.clear();data.robots.putAll(census.robots());}data.setDirty();
        }
        return data;
    }
    public static OptionalInt count(MinecraftServer server,Set<UUID> owners) {
        if(owners.isEmpty())return OptionalInt.of(0);
        var data=get(server);if(!data.complete)return OptionalInt.empty();
        return OptionalInt.of((int)data.robots.values().stream().filter(e->owners.contains(e.owner())).count());
    }
    public static void admitted(LogisticRobotEntity entity) {
        if(!(entity.level() instanceof ServerLevel level)||entity.kind()!=LogisticRobotEntity.Kind.PERSONAL_CONSTRUCTION)return;
        var data=get(level.getServer());UUID owner=entity.homePlayer();
        if(owner==null){data.complete=false;data.setDirty();return;}
        var entry=new PersonalRobotCensus.Entry(owner,level.dimension().location().toString(),entity.chunkPosition().x,entity.chunkPosition().z);
        var old=data.robots.get(entity.getUUID());
        if(old!=null&&!old.owner().equals(owner)){data.complete=false;data.setDirty();return;}
        data.robots.put(entity.getUUID(),entry);data.live.put(entity.getUUID(),entity);data.setDirty();
    }
    public static void removed(LogisticRobotEntity entity,Entity.RemovalReason reason) {
        if(!(entity.level() instanceof ServerLevel level))return;var data=get(level.getServer());
        if(data.live.get(entity.getUUID())!=entity)return;
        data.live.remove(entity.getUUID());
        if(reason==Entity.RemovalReason.KILLED||reason==Entity.RemovalReason.DISCARDED){data.robots.remove(entity.getUUID());data.setDirty();}
    }
    private static PersonalRobotActivity read(CompoundTag tag) {
        if(!tag.contains("schema",Tag.TAG_INT)||tag.getInt("schema")!=1||!tag.contains("robots",Tag.TAG_LIST))throw new IllegalArgumentException("Неверная схема индекса роботов");
        var data=new PersonalRobotActivity();ListTag list=(ListTag)tag.get("robots");if(!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Неверный список роботов");
        for(Tag raw:list) {
            CompoundTag item=(CompoundTag)raw;if(!item.hasUUID("id")||!item.hasUUID("owner")||!item.contains("dimension",Tag.TAG_STRING)||!item.contains("x",Tag.TAG_INT)||!item.contains("z",Tag.TAG_INT))throw new IllegalArgumentException("Неверная запись робота");
            var entry=new PersonalRobotCensus.Entry(item.getUUID("owner"),item.getString("dimension"),item.getInt("x"),item.getInt("z"));
            if(data.robots.put(item.getUUID("id"),entry)!=null)throw new IllegalArgumentException("Повторный UUID робота");
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putInt("schema",1);ListTag list=new ListTag();robots.forEach((id,entry)->{CompoundTag item=new CompoundTag();item.putUUID("id",id);item.putUUID("owner",entry.owner());item.putString("dimension",entry.dimension());item.putInt("x",entry.chunkX());item.putInt("z",entry.chunkZ());list.add(item);});tag.put("robots",list);return tag;
    }
}
