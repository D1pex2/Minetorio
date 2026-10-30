package dev.minetorio.content.logistics;

import com.mojang.authlib.GameProfile;
import dev.minetorio.content.rail.*;
import dev.minetorio.core.rail.*;
import dev.minetorio.core.data.RailData.StockKind;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import static dev.minetorio.core.rail.RailGeometry.*;

/** Два отдельных процесса и один каталог мира. Обычная полная suite этот namespace не запускает. */
@GameTestHolder("minetorio_task7_restart")
@PrefixGameTestTemplate(false)
public final class RailwayRestartGameTest {
    private static final int CX=16384,CZ=16384;
    @GameTest(template="empty16",timeoutTicks=1400)
    public static void returnsWithSameCargoAndCarIds(GameTestHelper h) throws Exception {
        var level=h.getLevel();var server=level.getServer();var state=RailwaySavedData.get(level).state();
        var marker=server.getWorldPath(LevelResource.ROOT).resolve("task7-restart-marker.dat");
        if("save".equals(System.getenv("MINETORIO_TASK7_RESTART_PHASE"))) {
            h.assertTrue(!java.nio.file.Files.exists(marker),"Фаза save требует нового task-owned мира");
            UUID owner=UUID.randomUUID();var player=FakePlayerFactory.get(level,new GameProfile(owner,"restart-owner"));
            player.moveTo(CX*16+8,80,CZ*16+8);LogisticsManager.addExtraPlayer(player);
            level.setChunkForced(CX,CZ,true);level.getChunk(CX,CZ);
            var robot=new LogisticRobotEntity(LogisticsRegistry.LOGISTIC_ROBOT_ENTITY.get(),level);robot.moveTo(CX*16+8,280,CZ*16+8);
            var stack=new ItemStack(Items.STONE,17);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Перезапуск"));
            robot.startPersonalBuild(owner,new BlockPos(CX*16+8,80,CZ*16+8),stack);
            h.assertTrue(level.addFreshEntity(robot),"Настоящее accepted entity admission до unload");
            List<Piece> rails=new ArrayList<>();for(int n=0;n<60;n++)rails.add(new Piece(UUID.randomUUID(),new Point(300000,80,-n*2),Shape.STRAIGHT,0,false));
            var combined=new ArrayList<>(state.rails());combined.addAll(rails);state.replaceRails(combined);
            List<UUID> cars=new ArrayList<>();for(var kind:StockKind.values())cars.add(state.createCar(UUID.randomUUID(),kind,false).id());
            state.cars().get(cars.get(0)).fuel().orElseThrow().insert(0,"minetorio:coal",13,false);
            state.cars().get(cars.get(0)).setActiveFuel("minetorio:coal",new java.math.BigDecimal("1234567"));
            state.cars().get(cars.get(1)).cargo().orElseThrow().insertManual(0,"minetorio:iron_plate",17,false);
            state.cars().get(cars.get(2)).insertFluid("water",1234);state.cars().get(cars.get(1)).setHp(500);
            var tape=rails.stream().map(p->new Traversal(p.id(),true)).toList();UUID train=UUID.randomUUID();
            state.createTrain(new RailwayState.Train(train,cars,Optional.of(new Cursor(tape.get(25),1)),25,tape,0,RailwayState.Mode.MANUAL,RailwayState.Status.MANUAL_CONTROL_STOP,0,0,0));
            level.setChunkForced(CX,CZ,false);
            h.succeedWhen(()->{
                h.assertTrue(level.getChunkSource().getChunkNow(CX,CZ)==null&&!level.hasChunk(CX,CZ),"Настоящий chunk unload завершился");
                h.assertTrue(level.getEntity(robot.getUUID())==null,"Проекция робота действительно выгружена");
                h.assertTrue(PersonalRobotActivity.count(server,Set.of(owner)).equals(OptionalInt.of(1)),"Unload не обнулил active membership");
                CompoundTag tag=new CompoundTag();tag.putUUID("owner",owner);tag.putUUID("robot",robot.getUUID());tag.putUUID("train",train);tag.putLong("clock",state.railClock());
                for(int n=0;n<cars.size();n++)tag.putUUID("car"+n,cars.get(n));
                try{NbtIo.writeCompressed(tag,marker);}catch(java.io.IOException error){throw new RuntimeException(error);}
                LogisticsManager.removeExtraPlayer(player);
                com.mojang.logging.LogUtils.getLogger().info("TASK7 restart save: unloaded robot={}, owner={}, railClock={}",robot.getUUID(),owner,state.railClock());
            });
        } else {
            h.assertTrue("load".equals(System.getenv("MINETORIO_TASK7_RESTART_PHASE")),"Задана явная фаза load");
            CompoundTag tag=NbtIo.readCompressed(marker,NbtAccounter.unlimitedHeap());UUID owner=tag.getUUID("owner"),robotId=tag.getUUID("robot"),trainId=tag.getUUID("train");
            h.assertTrue(level.getChunkSource().getChunkNow(CX,CZ)==null,"До reload robot chunk отсутствует");
            h.assertTrue(PersonalRobotActivity.count(server,Set.of(owner)).equals(OptionalInt.of(1)),"Startup census видит выгруженного робота после process restart");
            var train=state.trains().get(trainId);h.assertTrue(train!=null&&state.railClock()>tag.getLong("clock"),"Owner и настоящий ticker восстановлены новым процессом");
            for(int n=0;n<3;n++)h.assertTrue(train.cars().get(n).equals(tag.getUUID("car"+n)),"Car ID сохранен");
            var loco=state.cars().get(train.cars().get(0));var cargo=state.cars().get(train.cars().get(1));var fluid=state.cars().get(train.cars().get(2));
            h.assertTrue(loco.fuel().orElseThrow().get(0).count()==13&&loco.remainingJ().compareTo(new java.math.BigDecimal("1234567"))==0,"Топливо сохранено");
            h.assertTrue(cargo.cargo().orElseThrow().get(0).count()==17&&cargo.hp()==500&&fluid.fluid().orElseThrow().amount()==1234,"Cargo, fluid, HP сохранены");
            var player=FakePlayerFactory.get(level,new GameProfile(owner,"restart-owner"));player.moveTo(CX*16+8,80,CZ*16+8);LogisticsManager.addExtraPlayer(player);
            level.setChunkForced(CX,CZ,true);level.getChunk(CX,CZ);
            for(var pose:TrainMotion.carPoses(state,trainId).values())level.setChunkForced((int)Math.floor(pose.position().x())>>4,(int)Math.floor(pose.position().z())>>4,true);
            h.succeedWhen(()->{
                h.assertTrue(level.getEntity(robotId) instanceof LogisticRobotEntity,"Canonical entity reload вернул тот же UUID");
                var robot=(LogisticRobotEntity)level.getEntity(robotId);
                h.assertTrue(robot.carried().getCount()==17&&robot.carried().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)!=null,"Робот сохранил components/carry");
                h.assertTrue(PersonalRobotActivity.count(server,Set.of(owner)).equals(OptionalInt.of(1)),"Reload не удвоил membership");
                RailwayManager.reconcileViews(level);Set<UUID> views=new HashSet<>();for(var e:level.getAllEntities())if(e instanceof RollingStockEntity stock&&train.cars().contains(stock.carId()))views.add(stock.carId());
                h.assertTrue(views.equals(Set.copyOf(train.cars())),"Loaded проекции имеют исходные car IDs");
                robot.discard();h.assertTrue(PersonalRobotActivity.count(server,Set.of(owner)).equals(OptionalInt.of(0)),"Terminal removal после reload очищает membership");
                level.setChunkForced(CX,CZ,false);LogisticsManager.removeExtraPlayer(player);
                for(var pose:TrainMotion.carPoses(state,trainId).values())level.setChunkForced((int)Math.floor(pose.position().x())>>4,(int)Math.floor(pose.position().z())>>4,false);
                var rails=state.trains().get(trainId).tape().stream().map(Traversal::piece).collect(java.util.stream.Collectors.toSet());train.cars().forEach(id->state.removeCar(id,true));state.replaceRails(state.rails().stream().filter(p->!rails.contains(p.id())).toList());
                com.mojang.logging.LogUtils.getLogger().info("TASK7 restart load: robot={}, same car IDs={}, resources preserved",robotId,train.cars());
            });
        }
    }
}
