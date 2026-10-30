package dev.minetorio.content.logistics;

import com.mojang.authlib.GameProfile;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

/** Изолированные startup fixtures после штатного shutdown подготовительного процесса. */
@GameTestHolder("minetorio_task7_startup")
@PrefixGameTestTemplate(false)
public final class RailwayStartupGameTest {
    private static final int CX=17000,CZ=17000;
    @GameTest(template="empty16",timeoutTicks=1400)
    public static void canonicalCensusRepairsLegacyAndCrashCaches(GameTestHelper h) throws Exception {
        var level=h.getLevel();var server=level.getServer();Path root=server.getWorldPath(LevelResource.ROOT),marker=root.resolve("task7-startup-marker.dat");String phase=System.getenv("MINETORIO_TASK7_STARTUP_PHASE");
        if("prepare".equals(phase)) {
            h.assertTrue(!Files.exists(marker),"Prepare требует нового task-owned каталога");UUID owner=UUID.randomUUID(),ghostOwner=UUID.randomUUID();
            var ghost=new LogisticRobotEntity(LogisticsRegistry.LOGISTIC_ROBOT_ENTITY.get(),level);ghost.moveTo(8,250,8);ghost.startPersonalBuild(ghostOwner,new BlockPos(8,80,8),new ItemStack(Items.STONE));h.assertTrue(level.addFreshEntity(ghost),"Fixture ghost index admitted");ghost.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);
            h.assertTrue(PersonalRobotActivity.count(server,Set.of(ghostOwner)).equals(OptionalInt.of(1)),"Fixture crash cache имеет ghost UUID без canonical entity");
            boolean empty="empty".equals(System.getenv("MINETORIO_TASK7_STARTUP_CASE"));var tag=new CompoundTag();tag.putUUID("owner",owner);tag.putUUID("ghostOwner",ghostOwner);tag.putUUID("ghost",ghost.getUUID());tag.putBoolean("empty",empty);
            if(empty){NbtIo.writeCompressed(tag,marker);h.succeed();return;}
            var player=FakePlayerFactory.get(level,new GameProfile(owner,"startup-owner"));player.moveTo(CX*16+8,80,CZ*16+8);LogisticsManager.addExtraPlayer(player);level.setChunkForced(CX,CZ,true);level.getChunk(CX,CZ);
            var robot=new LogisticRobotEntity(LogisticsRegistry.LOGISTIC_ROBOT_ENTITY.get(),level);robot.moveTo(CX*16+8,280,CZ*16+8);robot.startPersonalBuild(owner,new BlockPos(CX*16+8,80,CZ*16+8),new ItemStack(Items.STONE,17));h.assertTrue(level.addFreshEntity(robot),"Canonical fixture robot admitted");tag.putUUID("robot",robot.getUUID());level.setChunkForced(CX,CZ,false);
            h.succeedWhen(()->{
                h.assertTrue(!level.hasChunk(CX,CZ)&&level.getEntity(robot.getUUID())==null,"Canonical fixture полностью выгружена");h.assertTrue(PersonalRobotActivity.count(server,Set.of(owner)).equals(OptionalInt.of(1)),"Fixture cache содержит canonical unloaded UUID");
                try{NbtIo.writeCompressed(tag,marker);}catch(java.io.IOException e){throw new RuntimeException(e);}LogisticsManager.removeExtraPlayer(player);
            });return;
        }
        h.assertTrue("verify".equals(phase),"Явная startup verify phase");var tag=NbtIo.readCompressed(marker,NbtAccounter.unlimitedHeap());UUID owner=tag.getUUID("owner"),ghostOwner=tag.getUUID("ghostOwner");boolean corrupt="corrupt".equals(System.getenv("MINETORIO_TASK7_STARTUP_CASE"));
        h.assertTrue(level.getChunkSource().getChunkNow(CX,CZ)==null&&!level.hasChunk(CX,CZ),"До query canonical remote chunk не загружен");
        var count=PersonalRobotActivity.count(server,Set.of(owner));var ghostCount=PersonalRobotActivity.count(server,Set.of(ghostOwner));
        if(corrupt)h.assertTrue(count.isEmpty()&&ghostCount.isEmpty(),"Corrupt occupied store дает unknown, не known0");
        else {h.assertTrue(count.equals(OptionalInt.of(tag.getBoolean("empty")?0:1)),"Полная startup census восстанавливает canonical count без entity load");h.assertTrue(ghostCount.equals(OptionalInt.of(0)),"Полная census удаляет только ghost cache row");}
        h.assertTrue(level.getChunkSource().getChunkNow(CX,CZ)==null&&(!tag.hasUUID("robot")||level.getEntity(tag.getUUID("robot"))==null),"Activity query не загружает canonical entity/chunk");
        level.getDataStorage().save();net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();var saved=NbtIo.readCompressed(root.resolve("data/minetorio_personal_robot_activity.dat"),NbtAccounter.unlimitedHeap()).getCompound("data");var ids=new HashSet<UUID>();for(Tag entry:saved.getList("robots",Tag.TAG_COMPOUND))ids.add(((CompoundTag)entry).getUUID("id"));
        if(corrupt)h.assertTrue(ids.contains(tag.getUUID("ghost"))&&ids.contains(tag.getUUID("robot")),"Unknown сохраняет прежние index entries при нормальном save");
        else h.assertTrue(!ids.contains(tag.getUUID("ghost"))&&(tag.getBoolean("empty")||ids.contains(tag.getUUID("robot"))),"Canonical reconciliation сохранена для следующего restart");
        try(var reader=Files.newBufferedReader(root.resolve("task7-startup-hashes.json"))) {
            for(var entry:JsonParser.parseReader(reader).getAsJsonArray()) {
                var item=entry.getAsJsonObject();Path file=root.resolve(item.get("path").getAsString());String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
                h.assertTrue(hash.equalsIgnoreCase(item.get("hash").getAsString()),"Startup census сохранила canonical/corrupt original bytes: "+file);
            }
        }
        com.mojang.logging.LogUtils.getLogger().info("TASK7 startup case={} empty={} canonical={} ghost={} remoteLoaded=false bytes=identical",System.getenv("MINETORIO_TASK7_STARTUP_CASE"),tag.getBoolean("empty"),count,ghostCount);h.succeed();
    }
}
