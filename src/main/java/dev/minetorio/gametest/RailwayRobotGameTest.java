package dev.minetorio.gametest;

import dev.minetorio.content.rail.*;
import dev.minetorio.content.logistics.*;
import dev.minetorio.content.robot.*;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.data.RailData.StockKind;
import dev.minetorio.core.rail.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.RailwayState.*;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public final class RailwayRobotGameTest {
    private static void travelFixture(GameTestHelper h,ServerPlayer player,net.minecraft.server.level.ServerLevel target,double x,double y,double z) {
        var source=player.serverLevel();int[] changed={0};
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent> allow=event->{
            if(event.getEntity()==player&&event.getEntity().level()==source&&event.getDimension()==target.dimension())event.setCanceled(false);
        };
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent> observe=event->{if(event.getEntity()==player&&event.getFrom()==source.dimension()&&event.getTo()==target.dimension())changed[0]++;};
        var bus=net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        bus.addListener(net.neoforged.bus.api.EventPriority.LOWEST,true,net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent.class,allow);bus.addListener(observe);
        try {
            if(target.dimension()==net.minecraft.world.level.Level.NETHER) {
                var other=RailwayWorldGameTest.realPlayer(h);
                try{other.teleportTo(target,x,y,z,0,0);h.assertTrue(other.serverLevel()==h.getLevel(),"Scoped fixture exception не открывает Nether другому UUID");}
                finally{other.server.getPlayerList().remove(other);}
            }
            player.teleportTo(target,x,y,z,0,0);h.assertTrue(player.serverLevel()==target&&changed[0]==1,"Actual platform changeDimension и один PlayerChangedDimensionEvent");
        } finally {bus.unregister(allow);bus.unregister(observe);}
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final List<Piece> rails;final RailwayState state;final List<ServerPlayer> players=new ArrayList<>();final UUID train;
        boolean closed;
        Fixture(GameTestHelper h,int riders) {
            this.h=h;rails=RailwayWorldGameTest.loadedTrack(h);state=RailwaySavedData.get(h.getLevel()).state();var cars=new ArrayList<UUID>();
            for(int n=0;n<riders;n++)cars.add(state.createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,false).id());
            var tape=rails.stream().map(p->new Traversal(p.id(),true)).toList();train=UUID.randomUUID();state.createTrain(new Train(train,cars,Optional.of(new Cursor(tape.get(10),1)),10,tape,0,Mode.MANUAL,Status.MANUAL_CONTROL,0,0,0));RailwayManager.reconcileViews(h.getLevel());
            for(UUID car:cars) {
                var p=RailwayWorldGameTest.realPlayer(h);players.add(p);RollingStockEntity view=null;
                for(var entity:h.getLevel().getAllEntities())if(entity instanceof RollingStockEntity stock&&car.equals(stock.carId()))view=stock;
                h.assertTrue(view!=null,"Actual locomotive projection");p.moveTo(view.getX()+1,view.getY(),view.getZ());h.assertTrue(view.interact(p,InteractionHand.MAIN_HAND).consumesAction()&&p.getVehicle()==view,"Actual passenger boarding");
            }
            onFailure(h,this::close);
        }
        TrainWait.PassengerState passengers(){RailwayManager.tick(h.getLevel());return state.dispatcher().context(train,RailCircuitInputs.NONE).passengers().orElseThrow();}
        @Override public void close(){if(closed)return;closed=true;for(var p:players)if(p.server.getPlayerList().getPlayer(p.getUUID())==p)p.server.getPlayerList().remove(p);var owned=state.trains().get(train);if(owned!=null)owned.cars().forEach(id->state.removeCar(id,true));RailwayWorldGameTest.cleanupTrack(h,rails);}
    }
    private static void onFailure(GameTestHelper h,Runnable cleanup) {
        h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo test){}public void testPassed(GameTestInfo test,GameTestRunner runner){}public void testFailed(GameTestInfo test,GameTestRunner runner){cleanup.run();}public void testAddedForRerun(GameTestInfo oldTest,GameTestInfo newTest,GameTestRunner runner){}});
    }
    private static LogisticRobotEntity launch(GameTestHelper h,ServerPlayer p,int offset) {
        var ghost=p.blockPosition().offset(offset,-1,4);h.getLevel().setBlockAndUpdate(ghost.below(),Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(ghost,Blocks.AIR.defaultBlockState());
        p.getInventory().add(new ItemStack(LogisticsRegistry.PERSONAL_ROBOPORT.get()));p.getInventory().add(new ItemStack(ModItems.CONSTRUCTION_ROBOT.get()));p.getInventory().add(new ItemStack(ModItems.INSERTER.get()));
        h.assertTrue(Ghosts.place(h.getLevel(),ghost,ModItems.INSERTER.get(),Direction.EAST),"Actual ghost");PersonalRoboport.tick(p);
        for(var e:h.getLevel().getAllEntities())if(e instanceof LogisticRobotEntity r&&p.getUUID().equals(r.homePlayer()))return r;
        throw new IllegalStateException("Personal launch не создал робота");
    }
    @GameTest(template="rail48",batch="railway_robot_review")
    public static void actualMultiplePassengersUnionOwnersAndDismount(GameTestHelper h) {
        var robots=new ArrayList<LogisticRobotEntity>();
        try(var f=new Fixture(h,2)) {
            for(var p:f.players)robots.add(launch(h,p,4));
            h.assertTrue(f.passengers().present()&&f.passengers().activePersonalRobots().equals(OptionalInt.of(2)),"Два actual riders дают union двух robot UUID");
            var first=f.players.getFirst();first.stopRiding();first.moveTo(first.getX()+10,first.getY(),first.getZ());
            h.assertTrue(f.passengers().present()&&f.passengers().activePersonalRobots().equals(OptionalInt.of(1)),"Dismount исключает только owner вышедшего пассажира");
            h.assertTrue(PersonalRobotActivity.count(first.server,Set.of(first.getUUID())).equals(OptionalInt.of(1)),"Dismount не удаляет чужой activity owner");
            var second=f.players.getLast();second.stopRiding();second.moveTo(second.getX()+10,second.getY(),second.getZ());
            h.assertTrue(!f.passengers().present()&&f.passengers().activePersonalRobots().equals(OptionalInt.of(0)),"NONE passengers имеет известный ноль");h.succeed();
        } finally {for(var robot:robots)robot.discard();}
    }
    @GameTest(template="rail48",batch="railway_robot_review")
    public static void acceptedCrossDimensionDuplicateKeepsActualRiderWaitUnknown(GameTestHelper h) {
        LogisticRobotEntity robot=null,duplicate=null;
        try(var f=new Fixture(h,1)) {
            var player=f.players.getFirst();robot=launch(h,player,4);var tag=new net.minecraft.nbt.CompoundTag();h.assertTrue(robot.save(tag),"Actual canonical NBT");
            var other=Objects.requireNonNull(player.server.getLevel(net.minecraft.world.level.Level.NETHER));duplicate=(LogisticRobotEntity)net.minecraft.world.entity.EntityType.loadEntityRecursive(tag,other,e->e);h.assertTrue(duplicate!=null,"Copied UUID fixture");duplicate.moveTo(8,80,8);h.assertTrue(other.addFreshEntity(duplicate),"Second level действительно принимает duplicate UUID");duplicate.discard();
            var input=f.passengers();h.assertTrue(input.present()&&input.activePersonalRobots().isEmpty(),"F1: actual rider сохраняет presence при ambiguous robot owner");
            var inactive=new TrainSchedule.Wait(TrainSchedule.Type.ROBOTS_INACTIVE,TrainSchedule.Join.AND,0,Optional.empty());h.assertTrue(!TrainWait.evaluate(List.of(inactive),f.state.dispatcher().context(f.train,RailCircuitInputs.NONE)),"F1: actual rider wait не принимает ложный ноль");
            h.assertTrue(PersonalRobotActivity.count(player.server,Set.of(UUID.randomUUID())).equals(OptionalInt.of(0)),"F1: другой owner не теряет known completeness");h.succeed();
        } finally {if(robot!=null)robot.discard();if(duplicate!=null)duplicate.discard();}
    }
    @GameTest(template="rail48",batch="railway_robot_review")
    public static void actualNonTickingLoadedRobotReleasesClaimButStaysActive(GameTestHelper h) {
        LogisticRobotEntity robot=null;
        try(var f=new Fixture(h,1)) {
            var player=f.players.getFirst();robot=launch(h,player,4);var ghost=player.blockPosition().offset(4,-1,4);var job=ConstructionManager.get(h.getLevel()).queue().get(ConstructionManager.cell(ghost)).orElseThrow();h.assertTrue(job.isClaimed(),"Launch claim held");
            int cx=45000,cz=45000;h.getLevel().getChunk(cx,cz);robot.moveTo(cx*16+8,250,cz*16+8);
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(cx,cz)!=null&&!h.getLevel().isPositionEntityTicking(robot.blockPosition()),"Actual loaded chunk без entity ticking");
            for(int n=0;n<10;n++)net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(()->true,h.getLevel()));
            h.assertTrue(robot.phase()==LogisticRobotEntity.Phase.RETURNING&&!job.isClaimed(),"Actual manager sweep/dropTrip освобождает claim");
            h.assertTrue(f.passengers().activePersonalRobots().equals(OptionalInt.of(1)),"Non-ticking RETURNING остается activity1 через actual rider bridge");
            robot.moveTo(player.getX(),player.getY()+player.getBbHeight()+.4,player.getZ());robot.tick();
            h.assertTrue(robot.isRemoved()&&f.passengers().activePersonalRobots().equals(OptionalInt.of(0)),"Normal inventory return завершает activity");
            h.assertTrue(PlayerItems.count(player,ModItems.CONSTRUCTION_ROBOT.get())==1&&PlayerItems.count(player,ModItems.INSERTER.get())==1,"Отмененный trip вернул robot/material ровно один раз");h.succeed();
        } finally {if(robot!=null)robot.discard();}
    }
    @GameTest(template="rail48",batch="railway_robot_unload",timeoutTicks=1400)
    public static void actualUnloadBlocksWaitThenOwnerRejoinsAndRobotReturns(GameTestHelper h) {
        var f=new Fixture(h,1);var level=h.getLevel();var initial=f.players.getFirst();var robot=launch(h,initial,4);UUID owner=initial.getUUID(),robotId=robot.getUUID();
        var launchTarget=Endpoint.load(robot.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getCompound("target"));
        h.assertTrue(launchTarget!=null&&launchTarget.type()==Endpoint.Type.GHOST&&level.getBlockState(launchTarget.pos()).is(dev.minetorio.content.registry.ModBlocks.GHOST.get()),"Launch сохраняет фактическую цель существующего ghost из robot NBT");
        var inactive=new TrainSchedule.Wait(TrainSchedule.Type.ROBOTS_INACTIVE,TrainSchedule.Join.AND,0,Optional.empty());UUID station=UUID.randomUUID();
        f.state.dispatcher().upsertStation(new Station(station,"Выгруженный робот",TrainReservations.leading(f.state,f.train,1).start(),true,OptionalInt.empty()));
        f.state.dispatcher().setSchedule(f.train,new TrainSchedule(List.of(new TrainSchedule.Stop(Optional.of("Выгруженный робот"),Optional.empty(),false,List.of(inactive)),
                new TrainSchedule.Stop(Optional.empty(),Optional.of(new TrainSchedule.RailTarget(new Cursor(new Traversal(f.rails.get(17).id(),true),1),false)),false,List.of(new TrainSchedule.Wait(TrainSchedule.Type.TIME,TrainSchedule.Join.AND,1000,Optional.empty())))),0));
        f.state.cars().get(f.state.trains().get(f.train).cars().getFirst()).fuel().orElseThrow().insert(0,"minetorio:coal",2,false);f.state.dispatcher().setMode(f.train,Mode.AUTOMATIC);RailwayManager.tick(level);var head=f.state.trains().get(f.train).head();
        int cx=46000,cz=46000;level.setChunkForced(cx,cz,true);level.getChunk(cx,cz);robot.moveTo(cx*16+8,280,cz*16+8);level.setChunkForced(cx,cz,false);
        net.minecraft.world.level.ChunkPos[] savedChunk={null};ServerPlayer[] rider={initial};
        onFailure(h,()->{level.setChunkForced(cx,cz,false);if(savedChunk[0]!=null)level.setChunkForced(savedChunk[0].x,savedChunk[0].z,false);var loaded=level.getEntity(robotId);if(loaded!=null)loaded.discard();f.state.dispatcher().removeStation(station);});
        h.startSequence().thenWaitUntil(()->h.assertTrue(!level.hasChunk(cx,cz)&&level.getChunkSource().getChunkNow(cx,cz)==null&&level.getEntity(robotId)==null,"Actual chunk unload/entity absence")).thenExecute(()->{
                h.assertTrue(f.passengers().activePersonalRobots().equals(OptionalInt.of(1))&&f.state.trains().get(f.train).head().equals(head)&&f.state.trains().get(f.train).status()==Status.WAIT_STATION,"Unloaded robot действительно блокирует actual AUTO departure");
                savedChunk[0]=robot.chunkPosition();f.state.dispatcher().setMode(f.train,Mode.MANUAL);
                initial.server.getPlayerList().remove(initial);h.assertTrue(PersonalRobotActivity.count(initial.server,Set.of(owner)).equals(OptionalInt.of(1)),"Logout не удаляет unloaded owner row");
                var returned=RailwayWorldGameTest.realPlayer(h,owner);f.players.add(returned);rider[0]=returned;var at=head.orElseThrow();var pose=sample(f.state.graph().piece(at.traversal().piece()).orElseThrow(),at.distance()).position();
                var other=level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);h.assertTrue(other!=null,"Actual second dimension");travelFixture(h,returned,other,8,80,8);
                h.assertTrue(returned.serverLevel()==other&&PersonalRobotActivity.count(returned.server,Set.of(owner)).equals(OptionalInt.of(1)),"Actual PlayerChangedDimension сохраняет unloaded membership");
                travelFixture(h,returned,level,pose.x()+1,pose.y(),pose.z());RailwayManager.reconcileViews(level);returned.getInventory().selected=8;RollingStockEntity view=null;for(var e:level.getAllEntities())if(e instanceof RollingStockEntity stock&&f.state.trains().get(f.train).cars().contains(stock.carId()))view=stock;
                h.assertTrue(view!=null&&view.interact(returned,InteractionHand.MAIN_HAND).consumesAction(),"Rejoin/back dimension реально возвращает пассажира");
                f.state.dispatcher().setMode(f.train,Mode.AUTOMATIC);
                h.assertTrue(f.passengers().activePersonalRobots().equals(OptionalInt.of(1))&&f.state.trains().get(f.train).status()==Status.WAIT_STATION&&f.state.trains().get(f.train).head().equals(head),"После rejoin actual AUTO снова ждет unloaded robot");level.setChunkForced(savedChunk[0].x,savedChunk[0].z,true);level.getChunk(savedChunk[0].x,savedChunk[0].z);
            }).thenWaitUntil(()->h.assertTrue(level.getEntity(robotId) instanceof LogisticRobotEntity,"Actual disk reload возвращает UUID")).thenExecute(()->{
                var loaded=(LogisticRobotEntity)level.getEntity(robotId);
                h.assertTrue(loaded.carried().is(ModItems.INSERTER.get())&&loaded.carried().getCount()==1&&f.passengers().activePersonalRobots().equals(OptionalInt.of(1))&&f.state.trains().get(f.train).status()==Status.WAIT_STATION&&f.state.trains().get(f.train).head().equals(head),"Reload сохраняет carry/count1 и actual AUTO wait до normal return");
                // Позиция rider после посадки меняется; цель берем из actual launch, не выводим заново.
                var ghost=launchTarget.pos();
                h.assertTrue(level.getBlockState(ghost).is(dev.minetorio.content.registry.ModBlocks.GHOST.get()),"После reload удаляется именно существующий target ghost запуска");
                level.setBlockAndUpdate(ghost,Blocks.AIR.defaultBlockState());loaded.moveTo(ghost.getX()+.5,ghost.getY()+1+dev.minetorio.core.robot.RobotStats.CRUISE_HEIGHT,ghost.getZ()+.5);loaded.tick();
                // Снятый после reload ghost отменяет задание штатной entity tick; материал не копируется fixture.
                h.assertTrue(loaded.phase()==LogisticRobotEntity.Phase.RETURNING,"Снятие ghost после reload переводит отмененное задание в RETURNING");loaded.moveTo(rider[0].getX(),rider[0].getY()+rider[0].getBbHeight()+.4,rider[0].getZ());loaded.tick();
            h.assertTrue(PersonalRobotActivity.count(level.getServer(),Set.of(owner)).equals(OptionalInt.of(0)),"Normal return после actual reload очищает membership");
            h.assertTrue(PlayerItems.count(rider[0],ModItems.CONSTRUCTION_ROBOT.get())==1&&PlayerItems.count(rider[0],ModItems.INSERTER.get())==1,"После logout/rejoin/dimension/unload robot и material возвращены ровно один раз");
            h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,rider[0].getBoundingBox().inflate(8)).stream().noneMatch(item->item.getItem().is(ModItems.CONSTRUCTION_ROBOT.get())||item.getItem().is(ModItems.INSERTER.get())),"Normal inventory return не дублирует robot/material в world drops");
            RailwayManager.tick(level);h.assertTrue(f.state.trains().get(f.train).speed()>0&&!f.state.trains().get(f.train).head().equals(head),"Actual AUTO departure после normal return выгруженного робота");
            level.setChunkForced(savedChunk[0].x,savedChunk[0].z,false);f.state.dispatcher().removeStation(station);f.close();
        }).thenSucceed();
    }
    @GameTest(template="rail48",batch="railway_robot_review")
    public static void actualLoadedOwnerLogoutOrDimensionLossBalancesDrops(GameTestHelper h) {
        for(boolean logout:new boolean[]{true,false}) {
            LogisticRobotEntity robot=null;
            try(var f=new Fixture(h,1)) {
                var player=f.players.getFirst();robot=launch(h,player,4);var ghost=player.blockPosition().offset(4,-1,4);h.getLevel().setBlockAndUpdate(ghost,Blocks.AIR.defaultBlockState());
                robot.moveTo(ghost.getX()+.5,ghost.getY()+1.35,ghost.getZ()+.5);for(int n=0;n<20&&robot.phase()!=LogisticRobotEntity.Phase.RETURNING;n++)robot.tick();h.assertTrue(robot.phase()==LogisticRobotEntity.Phase.RETURNING&&!robot.carried().isEmpty(),"Loaded отмененное задание сохраняет carried material");
                var dropArea=robot.getBoundingBox().inflate(2);var before=new HashSet<UUID>();for(var item:h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,dropArea))before.add(item.getUUID());
                if(logout)player.server.getPlayerList().remove(player);else travelFixture(h,player,Objects.requireNonNull(player.server.getLevel(net.minecraft.world.level.Level.NETHER)),8,80,8);
                h.assertTrue(PersonalRobotActivity.count(player.server,Set.of(player.getUUID())).equals(OptionalInt.of(1)),"Событие owner ухода само не удаляет loaded robot");robot.tick();
                h.assertTrue(robot.isRemoved()&&PersonalRobotActivity.count(player.server,Set.of(player.getUUID())).equals(OptionalInt.of(0)),"Actual lose/terminal после ухода owner очищает activity");
                int robots=0,materials=0;var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,dropArea).stream().filter(e->!before.contains(e.getUUID())).toList();
                for(var item:drops){if(item.getItem().is(ModItems.CONSTRUCTION_ROBOT.get()))robots+=item.getItem().getCount();if(item.getItem().is(ModItems.INSERTER.get()))materials+=item.getItem().getCount();}
                h.assertTrue(robots==1&&materials==1&&PlayerItems.count(player,ModItems.CONSTRUCTION_ROBOT.get())==0&&PlayerItems.count(player,ModItems.INSERTER.get())==0,"Loaded loss возвращает robot/material только world drops, ровно один раз");
                if(logout){var back=RailwayWorldGameTest.realPlayer(h,player.getUUID());f.players.add(back);h.assertTrue(PlayerItems.count(back,ModItems.CONSTRUCTION_ROBOT.get())==0&&PlayerItems.count(back,ModItems.INSERTER.get())==0,"Rejoin не дублирует world drops в inventory");}
                else travelFixture(h,player,h.getLevel(),dropArea.minX+5,dropArea.minY,dropArea.minZ);
                drops.forEach(net.minecraft.world.entity.item.ItemEntity::discard);
            } finally {if(robot!=null)robot.discard();}
        }
        h.succeed();
    }
}
