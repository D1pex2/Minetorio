package dev.minetorio.gametest;

import com.mojang.authlib.GameProfile;
import dev.minetorio.content.rail.*;
import dev.minetorio.content.research.ResearchSavedData;
import dev.minetorio.core.data.RailData;
import dev.minetorio.core.rail.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.RailwayState.*;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public final class RailwayReviewGameTest {
    @GameTest(template="empty16",batch="railway_review")
    public static void legacyOrphanPhysicalSignalCanBeMinedOnce(GameTestHelper h) {
        var p=player(h);var data=RailwaySavedData.get(h.getLevel());var pos=h.absolutePos(new BlockPos(5,2,5));
        h.getLevel().setBlockAndUpdate(pos.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(pos,RailRegistry.SIGNAL_BLOCK.get().defaultBlockState());p.moveTo(pos.getX(),pos.getY(),pos.getZ());
        h.assertTrue(data.worldSignals().values().stream().noneMatch(s->s.position().equals(pos)),"C5 legacy fixture: физический orphan, logical definition уже потерян старой schema5");
        h.assertTrue(p.gameMode.destroyBlock(pos),"C5 legacy: actual mining orphan block");
        h.assertTrue(dev.minetorio.content.logistics.PlayerItems.count(p,RailRegistry.RAIL_SIGNAL.get())==1,"C5 legacy: оплаченный physical orphan возвращает один item");p.gameMode.destroyBlock(pos);
        h.assertTrue(dev.minetorio.content.logistics.PlayerItems.count(p,RailRegistry.RAIL_SIGNAL.get())==1&&data.worldSignals().values().stream().noneMatch(s->s.position().equals(pos)),"C5 legacy: нет дубля item или выдуманного owner");h.succeed();
    }
    private static net.minecraft.world.phys.Vec3 modelPoint(RollingStockEntity view,double x,double y,double z) {
        double a=Math.toRadians(StockPickShape.rotation(view.getYRot()));return view.position().add(x*Math.cos(a)+z*Math.sin(a),y,-x*Math.sin(a)+z*Math.cos(a));
    }
    @GameTest(template="rail48",batch="railway_review")
    public static void actualDiagonalPickingUsesCuboidsAndNearbyParallelStock(GameTestHelper h) {
        var state=RailwaySavedData.get(h.getLevel()).state();var player=player(h);var views=new ArrayList<RollingStockEntity>();var ids=new ArrayList<UUID>();
        try {
            for(var kind:RailData.StockKind.values()) {
                var car=state.createCar(UUID.randomUUID(),kind,false);ids.add(car.id());var view=new RollingStockEntity(RailRegistry.STOCK.get(),h.getLevel());views.add(view);
                var pos=h.absolutePos(new BlockPos(16,2,16));view.project(car,new Pose(new Vec(pos.getX(),pos.getY(),pos.getZ()),new Vec(0,0,1)));h.assertTrue(h.getLevel().addFreshEntity(view),"Actual projection admission");
                for(float yaw:new float[]{0,45,90,135}) {
                    view.setYRot(yaw);var start=modelPoint(view,-4,.35,2);var end=modelPoint(view,4,.35,2);player.moveTo(start.x,start.y,start.z);
                    var hit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player,start,end,new net.minecraft.world.phys.AABB(start,end).inflate(1),e->e==view,64);
                    h.assertTrue(hit!=null&&hit.getEntity()==view,"D3: actual ray по кузову "+kind+" yaw="+yaw);
                    var far=modelPoint(view,1.8,.35,2.8);h.assertTrue(!view.getBoundingBox().contains(far),"D3: пустой угол не является кузовом");
                    h.assertTrue(view.getBoundingBox().distanceToSqr(modelPoint(view,0,.35,2))==0,"D3: reach измеряется до видимого конца");
                }
                view.setYRot(45);var start=modelPoint(view,1.7,.35,-1);var end=modelPoint(view,1.7,.35,1);
                var miss=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player,start,end,new net.minecraft.world.phys.AABB(start,end).inflate(1),e->e==view,64);
                h.assertTrue(miss==null,"D3: ray вдоль соседнего пути не выбирает пустую часть diagonal AABB");
                var neighborCar=state.createCar(UUID.randomUUID(),kind,false);ids.add(neighborCar.id());var neighbor=new RollingStockEntity(RailRegistry.STOCK.get(),h.getLevel());views.add(neighbor);var neighborAt=modelPoint(view,2.4,-.125,0);
                neighbor.project(neighborCar,new Pose(new Vec(neighborAt.x,neighborAt.y,neighborAt.z),new Vec(0,0,1)));neighbor.setYRot(45);h.assertTrue(h.getLevel().addFreshEntity(neighbor),"Actual соседний diagonal stock");
                var alongStart=modelPoint(view,2.4,.35,-4);var alongEnd=modelPoint(view,2.4,.35,4);player.moveTo(alongStart.x,alongStart.y,alongStart.z);
                var selected=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player,alongStart,alongEnd,new net.minecraft.world.phys.AABB(alongStart,alongEnd).inflate(1),e->e==view||e==neighbor,64);
                h.assertTrue(selected!=null&&selected.getEntity()==neighbor,"D3: actual ray выбирает соседний параллельный stock без false hit первого");neighbor.discard();view.discard();
            }
            h.succeed();
        } finally {for(var view:views)view.discard();for(UUID id:ids)state.removeCar(id,false);}
    }
    private static final TrainMotion.Control GO=new TrainMotion.Control(1,false,TrainMotion.Turn.STRAIGHT);
    private static RailwaySavedData reload(GameTestHelper h,net.minecraft.nbt.CompoundTag tag) throws java.io.IOException {
        var folder=java.nio.file.Files.createTempDirectory("minetorio-rail-review-");var root=new net.minecraft.nbt.CompoundTag();root.put("data",tag);
        net.minecraft.nbt.NbtIo.writeCompressed(root,folder.resolve(RailwaySavedData.NAME+".dat"));
        return RailwaySavedData.get(new net.minecraft.world.level.storage.DimensionDataStorage(folder.toFile(),net.minecraft.util.datafix.DataFixers.getDataFixer(),h.getLevel().registryAccess()),folder,h.getLevel().registryAccess());
    }
    @GameTest(template="empty16",batch="railway_review")
    public static void worldSignalMigratesSchemasOneToFiveAndRejectsBrokenSix(GameTestHelper h) throws Exception {
        var registries=h.getLevel().registryAccess();var state=new RailwayState(key->64);var rail=new Piece(UUID.randomUUID(),new Point(0,80,0),Shape.STRAIGHT,0,false);state.replaceRails(List.of(rail));
        var signal=new RailSignals.Signal(UUID.randomUUID(),new Port(rail.id(),End.A),true,RailSignals.Kind.CHAIN);state.replaceSignals(List.of(signal));
        var current=RailwayNbt.write(state,registries);net.minecraft.nbt.CompoundTag migrated=null;
        // Старые схемы1/2 не имели logical signal definitions; их пустой мир также мигрируется строго.
        for(int schema=1;schema<=5;schema++) {
            var legacy=current.copy();legacy.putInt("schemaVersion",schema);legacy.remove("worldSignals");
            if(schema<3){legacy.put("signals",new net.minecraft.nbt.ListTag());legacy.remove("networkRevision");}
            var data=reload(h,legacy);
            if(schema>=3)h.assertTrue(data.worldSignals().get(signal.id()).geometry().equals(rail)&&data.state().signalDefinitions().equals(List.of(signal)),"C5: schema"+schema+" сохраняет оплаченный подключенный сигнал");
            else h.assertTrue(data.worldSignals().isEmpty(),"C5: schema"+schema+" не изобретает сигналов");
            migrated=data.save(new net.minecraft.nbt.CompoundTag(),registries);h.assertTrue(migrated.getInt("schemaVersion")==6,"C5: миграция записывает schema6");
            h.assertTrue(reload(h,migrated).worldSignals().equals(data.worldSignals()),"C5: повторный reload после миграции стабилен");
        }
        for(String bad:List.of("railId","kind","into","duplicate","worldSignals")) {
            var tag=migrated.copy();var list=tag.getList("worldSignals",net.minecraft.nbt.Tag.TAG_COMPOUND);var row=list.getCompound(0);
            switch(bad){case "duplicate"->list.add(row.copy());case "kind"->row.putString("kind","BROKEN");case "into"->row.putByte("into",(byte)2);case "worldSignals"->tag.remove(bad);default->row.remove(bad);}
            var folder=java.nio.file.Files.createTempDirectory("minetorio-signal-invalid-");var file=folder.resolve(RailwaySavedData.NAME+".dat");var root=new net.minecraft.nbt.CompoundTag();root.put("data",tag);net.minecraft.nbt.NbtIo.writeCompressed(root,file);byte[] bytes=java.nio.file.Files.readAllBytes(file);
            var storage=new net.minecraft.world.level.storage.DimensionDataStorage(folder.toFile(),net.minecraft.util.datafix.DataFixers.getDataFixer(),registries);boolean rejected=false;
            try{RailwaySavedData.get(storage,folder,registries);}catch(IllegalStateException expected){rejected=true;}
            h.assertTrue(rejected,"C5: invalid schema6 отвергнута: "+bad);storage.save();net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();h.assertTrue(Arrays.equals(bytes,java.nio.file.Files.readAllBytes(file)),"C5: invalid original bytes сохранены: "+bad);
        }
        h.succeed();
    }
    private static Train train(RailwayState state,List<Piece> rails,double q,double speed,RailData.StockKind kind) {
        var car=state.createCar(UUID.randomUUID(),kind,false);car.fuel().ifPresent(f->f.insert(0,"minetorio:coal",2,false));
        var tape=rails.stream().map(p->new Traversal(p.id(),true)).toList();int index=(int)(q/2);double local=q-index*2;
        return state.createTrain(new Train(UUID.randomUUID(),List.of(car.id()),Optional.of(new Cursor(tape.get(index),local)),index,tape,speed,Mode.MANUAL,Status.MANUAL_CONTROL,0,0,0));
    }
    private static ServerPlayer player(GameTestHelper h) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"rail-review"));p.setGameMode(GameType.SURVIVAL);return p;
    }
    private static UUID place(GameTestHelper h,ServerPlayer player,List<Piece> rails,double q,RailData.StockKind kind) {
        var piece=rails.get((int)(q/2));var pose=sample(piece,q%2).position();player.moveTo(pose.x()+1,pose.y(),pose.z());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RailRegistry.stockItem(kind)));
        return RailwayManager.placeStock(player,kind,new Cursor(new Traversal(piece.id(),true),q%2),false).orElseThrow();
    }
    private record Research(ResearchSavedData data,Set<String> completed,String current,Map<String,Integer> progress) implements AutoCloseable {
        Research(GameTestHelper h) {this(ResearchSavedData.get(h.getLevel().getServer()),Set.copyOf(ResearchSavedData.get(h.getLevel().getServer()).state().researched()),ResearchSavedData.get(h.getLevel().getServer()).state().current().map(t->t.id()).orElse(null),Map.copyOf(ResearchSavedData.get(h.getLevel().getServer()).state().progressById()));}
        public void close(){data.restore(completed,current,progress);}
    }
    @GameTest(template="rail48",batch="railway_review")
    public static void stoppedLeaderRechecksFollowingTrajectory(GameTestHelper h) {
        var rails=RailwayWorldGameTest.loadedTrack(h);var state=RailwaySavedData.get(h.getLevel()).state();
        try {
            var a=train(state,rails,21,.9,RailData.StockKind.LOCOMOTIVE);var b=train(state,rails,13,.9,RailData.StockKind.LOCOMOTIVE);var c=train(state,rails,29,0,RailData.StockKind.CARGO);
            for(int step=0;step<3;step++) {
                var proposals=List.of(TrainMotion.propose(state,a.id(),GO,0).orElseThrow(),TrainMotion.propose(state,b.id(),GO,0).orElseThrow());
                RailWorldCollision.resolve(h.getLevel(),state,proposals);
                h.assertTrue(RailSweep.pair(RailSweep.stationary(state,state.trains().get(a.id())),RailSweep.stationary(state,state.trains().get(b.id()))).isEmpty(),"C1: остановка A о C не оставляет непроверенное пересечение B/A на подшаге "+step);
            }
            h.assertTrue(state.cars().get(c.cars().getFirst()).hp()<600,"C1: исходный удар повреждает выжившую цель");
            h.assertTrue(state.cars().get(a.cars().getFirst()).remainingJ().doubleValue()==3970000&&state.cars().get(b.cars().getFirst()).remainingJ().doubleValue()==3970000,"C1: три реальные попытки тяги оплачены ровно три раза");h.succeed();
        } finally {RailwayWorldGameTest.cleanupTrack(h,rails);}
    }
    @GameTest(template="rail48",batch="railway_review")
    public static void terminalPartialStepStillChecksActualLivingContact(GameTestHelper h) {
        var rails=RailwayWorldGameTest.loadedTrack(h);var state=RailwaySavedData.get(h.getLevel()).state();
        var target=new net.minecraft.world.entity.animal.Pig(net.minecraft.world.entity.EntityType.PIG,h.getLevel()) {@Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){return false;}};
        try {
            var train=train(state,rails,36,.9,RailData.StockKind.LOCOMOTIVE);var end=sample(rails.getLast(),2).position();target.moveTo(end.x(),end.y()+.2,end.z()-.2);target.setNoAi(true);h.assertTrue(h.getLevel().addFreshEntity(target),"Actual living admission");
            var proposal=TrainMotion.propose(state,train.id(),GO,0).orElseThrow();h.assertTrue(proposal.distance()==.5&&proposal.speed()==0,"C2 fixture: полшага до края, конечная скорость0");
            RailWorldCollision.resolve(h.getLevel(),state,List.of(proposal));
            h.assertTrue(state.trains().get(train.id()).head().equals(train.head()),"C2: terminal partial sweep о выжившую цель отменяет translation");
            h.assertTrue(state.cars().get(train.cars().getFirst()).remainingJ().doubleValue()==3990000,"C2: попытка тяги оплачена");h.succeed();
        } finally {target.discard();RailwayWorldGameTest.cleanupTrack(h,rails);}
    }
    @GameTest(template="rail48",batch="railway_review")
    public static void addedStockPreservesExistingTrainScheduleAndIdentity(GameTestHelper h) {
        var rails=RailwayWorldGameTest.loadedTrack(h);var state=RailwaySavedData.get(h.getLevel()).state();var p=player(h);
        try(var research=new Research(h)) {
            research.data().restore(Set.of("railway"),null,Map.of());UUID car=place(h,p,rails,21,RailData.StockKind.LOCOMOTIVE);var existing=state.trains().values().stream().filter(t->t.cars().contains(car)).findFirst().orElseThrow();
            var target=new TrainSchedule.RailTarget(new Cursor(new Traversal(rails.get(17).id(),true),1),false);
            var schedule=new TrainSchedule(List.of(new TrainSchedule.Stop(Optional.empty(),Optional.of(target),false,List.of(new TrainSchedule.Wait(TrainSchedule.Type.TIME,TrainSchedule.Join.AND,200,Optional.empty())))),0);state.dispatcher().setSchedule(existing.id(),schedule);
            UUID added=place(h,p,rails,14,RailData.StockKind.CARGO);
            h.assertTrue(state.trains().containsKey(existing.id()),"C3: auto-couple сохраняет UUID существующего поезда");
            h.assertTrue(state.trains().get(existing.id()).cars().contains(added)&&state.trains().get(existing.id()).mode()==existing.mode(),"C3: membership расширен без смены mode");
            h.assertTrue(state.dispatcher().runtime(existing.id()).schedule().equals(schedule),"C3: расписание основного состава сохранено");h.succeed();
        } finally {RailwayWorldGameTest.cleanupTrack(h,rails);}
    }
    @GameTest(template="rail48",batch="railway_review")
    public static void worldManualBrakingUsesActualResearch(GameTestHelper h) {
        var rails=RailwayWorldGameTest.loadedTrack(h);var state=RailwaySavedData.get(h.getLevel()).state();
        try(var research=new Research(h)) {
            Set<String> levels=new HashSet<>();for(int i=1;i<=7;i++)levels.add("braking_force_"+i);research.data().restore(levels,null,Map.of());
            var train=train(state,rails,21,.3,RailData.StockKind.LOCOMOTIVE);var expected=RailwayNbt.read(RailwayNbt.write(state,h.getLevel().registryAccess()),h.getLevel().registryAccess());
            for(int i=0;i<3;i++)TrainMotion.step(expected,train.id(),new TrainMotion.Control(0,true,TrainMotion.Turn.STRAIGHT),RailData.brakingBonus(levels));
            RailwayManager.tick(h.getLevel());h.assertTrue(Math.abs(state.trains().get(train.id()).speed()-expected.trains().get(train.id()).speed())<1e-12,"C4: world manual принимает bonus семи реальных исследований");
            h.assertTrue(state.trains().get(train.id()).head().equals(expected.trains().get(train.id()).head()),"C4: path совпадает с тремя researched core steps");h.succeed();
        } finally {RailwayWorldGameTest.cleanupTrack(h,rails);}
    }
    @GameTest(template="rail48",batch="railway_review")
    public static void actualAutoResearchChangeRefreshesAuthority(GameTestHelper h) {
        var rails=RailwayWorldGameTest.loadedTrack(h);var state=RailwaySavedData.get(h.getLevel()).state();
        try(var research=new Research(h)) {
            var train=train(state,rails,16,.1,RailData.StockKind.LOCOMOTIVE);
            var target=new TrainSchedule.RailTarget(new Cursor(new Traversal(rails.get(17).id(),true),1),false);
            state.dispatcher().setSchedule(train.id(),new TrainSchedule(List.of(new TrainSchedule.Stop(Optional.empty(),Optional.of(target),false,List.of(new TrainSchedule.Wait(TrainSchedule.Type.TIME,TrainSchedule.Join.AND,1000,Optional.empty())))),0));state.dispatcher().setMode(train.id(),Mode.AUTOMATIC);
            for(Set<String> levels:List.of(Set.<String>of(),Set.of("braking_force_1"),Set.of("braking_force_1","braking_force_2","braking_force_3","braking_force_4","braking_force_5","braking_force_6","braking_force_7"))) {
                research.data().restore(levels,null,Map.of());double bonus=RailData.brakingBonus(levels);
                var expected=RailwayNbt.read(RailwayNbt.write(state,h.getLevel().registryAccess()),h.getLevel().registryAccess());for(int n=0;n<3;n++)expected.dispatcher().tick(RailCircuitInputs.NONE,bonus);
                RailwayManager.tick(h.getLevel());var actual=state.trains().get(train.id());var planned=TrainReservations.brakingCorridors(state).stream().filter(c->c.trainId().equals(train.id())).findFirst().orElseThrow();
                h.assertTrue(planned.brakingBonus()==bonus,"C4: AUTO authority обновлена после изменения actual research");
                h.assertTrue(actual.head().equals(expected.trains().get(train.id()).head())&&actual.speed()==expected.trains().get(train.id()).speed(),"C4: world AUTO физика совпала с researched dispatcher");
            }
            h.succeed();
        } finally {RailwayWorldGameTest.cleanupTrack(h,rails);}
    }
    @GameTest(template="rail48",batch="railway_review")
    public static void disconnectedStopRebindsEquivalentReverseRail(GameTestHelper h) throws Exception {
        var rails=RailwayWorldGameTest.loadedTrack(h);var data=RailwaySavedData.get(h.getLevel());var state=data.state();UUID id=UUID.randomUUID(),replacementId=UUID.randomUUID();var createdCars=new ArrayList<UUID>();
        try {
            var original=rails.get(10);var config=new RailwaySavedData.WorldStop(id,RailPlacement.block(original.origin()).offset(3,0,0),"Обратная укладка",true,OptionalInt.of(2),original.origin(),original.shape(),0,false,true,1.5);data.putStop(config);
            state.replaceRails(state.rails().stream().filter(p->!p.id().equals(original.id())).toList());data.rebindStops();
            var reversed=new Piece(replacementId,new Point(original.origin().x()+2,original.origin().y(),original.origin().z()-2),Shape.STRAIGHT,2,false);var replacement=new ArrayList<>(state.rails());replacement.add(reversed);state.replaceRails(replacement);data.rebindStops();
            h.assertTrue(state.stations().containsKey(id),"C6: эквивалентная reversed geometry восстанавливает attachment");
            var stop=state.stations().get(id).stop();h.assertTrue(!stop.traversal().forward()&&Math.abs(stop.distance()-1.5)<1e-12,"C6: world target/direction сохранены при перевороте representation");
            for(boolean forward:new boolean[]{true,false}) {
                var directed=new RailwaySavedData.WorldStop(id,config.position(),config.name(),true,config.limit(),original.origin(),original.shape(),0,false,forward,1.5);data.putStop(directed);
                var restored=reload(h,data.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()));data.putStop(restored.worldStops().get(id));
                var rebound=state.stations().get(id).stop();var actualPose=sample(reversed,rebound.traversal().forward()?rebound.distance():length(reversed)-rebound.distance());var oldPose=sample(original,forward?1.5:.5);
                h.assertTrue(actualPose.position().equals(oldPose.position())&&rebound.traversal().forward()!=forward,"C6: reload сохраняет actual world point и направление");
                var car=state.createCar(UUID.randomUUID(),RailData.StockKind.LOCOMOTIVE,!forward);createdCars.add(car.id());car.fuel().orElseThrow().insert(0,"minetorio:coal",2,false);
                var tape=rails.stream().map(p->p.id().equals(original.id())?new Traversal(replacementId,false):new Traversal(p.id(),true)).toList();int index=forward?7:14;
                var moving=state.createTrain(new Train(UUID.randomUUID(),List.of(car.id()),Optional.of(new Cursor(tape.get(index),1)),index,tape,0,Mode.MANUAL,Status.MANUAL_CONTROL,0,0,0));
                state.dispatcher().setSchedule(moving.id(),new TrainSchedule(List.of(new TrainSchedule.Stop(Optional.of(config.name()),Optional.empty(),false,List.of(new TrainSchedule.Wait(TrainSchedule.Type.TIME,TrainSchedule.Join.AND,1000,Optional.empty())))),0));state.dispatcher().setMode(moving.id(),Mode.AUTOMATIC);
                for(int n=0;n<500&&state.dispatcher().runtime(moving.id()).arrivalTick().isEmpty();n++)RailwayManager.tick(h.getLevel());
                h.assertTrue(state.dispatcher().runtime(moving.id()).stoppedStation().equals(Optional.of(id)),"C6: actual world AUTO прибывает после reverse/reload, forward="+forward);
                state.removeCar(car.id(),true);RailwayManager.reconcileViews(h.getLevel());
            }
            h.succeed();
        } finally {createdCars.forEach(car->state.removeCar(car,true));data.removeStop(id);state.replaceRails(state.rails().stream().filter(p->!p.id().equals(replacementId)).toList());RailwayWorldGameTest.cleanupTrack(h,rails);}
    }
    @GameTest(template="rail48",batch="railway_review")
    public static void detachedSignalStillReturnsPaidItem(GameTestHelper h) throws Exception {
        var rails=RailwayWorldGameTest.loadedTrack(h);var data=RailwaySavedData.get(h.getLevel());var state=data.state();var p=player(h);BlockPos[] signalPos={null};
        try(var research=new Research(h)) {
            research.data().restore(Set.of("railway","automated_rail_transportation","rail_signals"),null,Map.of());var piece=rails.get(10);
            var definition=new RailSignals.Signal(UUID.randomUUID(),new Port(piece.id(),End.A),true,RailSignals.Kind.REGULAR);signalPos[0]=RailPlacement.signalPosition(state,definition);h.getLevel().setBlockAndUpdate(signalPos[0].below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            p.moveTo(signalPos[0].getX(),signalPos[0].getY(),signalPos[0].getZ());p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RailRegistry.RAIL_SIGNAL.get()));
            UUID signalId=RailPlacement.attachSignal(p,definition.boundary(),true,RailSignals.Kind.REGULAR).orElseThrow();
            var ownerBefore=data.worldSignals().get(signalId);
            java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> cancel=event->{if(event.getPlayer()==p&&event.getPos().equals(signalPos[0]))event.setCanceled(true);};
            var bus=net.neoforged.neoforge.common.NeoForge.EVENT_BUS;bus.addListener(net.neoforged.bus.api.EventPriority.LOWEST,true,net.neoforged.neoforge.event.level.BlockEvent.BreakEvent.class,cancel);
            try{h.assertTrue(!p.gameMode.destroyBlock(signalPos[0]),"C5: later canceled actual BreakEvent запрещает mining");}finally{bus.unregister(cancel);}
            h.assertTrue(data.worldSignals().get(signalId).equals(ownerBefore)&&h.getLevel().getBlockState(signalPos[0]).is(RailRegistry.SIGNAL_BLOCK.get())&&dev.minetorio.content.logistics.PlayerItems.count(p,RailRegistry.RAIL_SIGNAL.get())==0,"C5: canceled mining сохраняет owner/block без раннего refund");
            h.assertTrue(RailPlacement.remove(p,piece.id()),"C5 setup: снят свободный rail");
            RailwayManager.reconcileViews(h.getLevel());
            h.assertTrue(data.worldSignals().containsKey(signalId)&&state.signalDefinitions().stream().noneMatch(s->s.id().equals(signalId)),"C5: disconnected owner сохраняется без logical permission");
            h.assertTrue(h.getLevel().getBlockState(signalPos[0]).getValue(RailSignalBlock.ASPECT)>=4,"C5: отдельный disconnected blinking display");
            var restored=reload(h,data.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()));
            h.assertTrue(restored.worldSignals().get(signalId).equals(data.worldSignals().get(signalId)),"C5: disconnected owner пережил actual storage reload");
            var reverse=new Piece(UUID.randomUUID(),new Point(piece.origin().x()+2,piece.origin().y(),piece.origin().z()-2),Shape.STRAIGHT,2,false);var replacement=new ArrayList<>(restored.state().rails());replacement.add(reverse);restored.state().replaceRails(replacement);restored.rebindSignals();
            var attached=restored.state().signalDefinitions().stream().filter(s->s.id().equals(signalId)).findFirst().orElseThrow();
            h.assertTrue(attached.boundary().equals(new Port(reverse.id(),End.B))&&attached.intoPiece(),"C5: equivalent reverse rebind сохраняет boundary/direction");
            h.assertTrue(p.gameMode.destroyBlock(signalPos[0]),"C5: штатный mining оставшегося signal block");
            h.assertTrue(dev.minetorio.content.logistics.PlayerItems.count(p,RailRegistry.RAIL_SIGNAL.get())==1,"C5: отключенный от пути сигнал возвращает оплаченный item ровно один раз");
            RailPlacement.destroyAttachment(h.getLevel(),signalPos[0]);h.assertTrue(!data.worldSignals().containsKey(signalId)&&dev.minetorio.content.logistics.PlayerItems.count(p,RailRegistry.RAIL_SIGNAL.get())==1,"C5: повтор удаления не дублирует возврат");h.succeed();
        } finally {if(signalPos[0]!=null)RailPlacement.destroyAttachment(h.getLevel(),signalPos[0]);RailwayWorldGameTest.cleanupTrack(h,rails);}
    }
    @GameTest(template="rail48",batch="railway_review")
    public static void actualSeatedNeutralCoastsAndUnattendedBrakes(GameTestHelper h) {
        var rails=RailwayWorldGameTest.loadedTrack(h);var state=RailwaySavedData.get(h.getLevel()).state();var p=RailwayWorldGameTest.realPlayer(h);
        try {
            var train=train(state,rails,21,.45509709775705626,RailData.StockKind.LOCOMOTIVE);RailwayManager.reconcileViews(h.getLevel());var pose=state.lastPoses().get(train.cars().getFirst()).position();p.moveTo(pose.x()+1,pose.y(),pose.z());
            RollingStockEntity view=null;for(var e:h.getLevel().getAllEntities())if(e instanceof RollingStockEntity stock&&train.cars().contains(stock.carId()))view=stock;
            h.assertTrue(view!=null&&view.interact(p,InteractionHand.MAIN_HAND).consumesAction(),"Actual ServerPlayer seated");p.setPlayerInput(0,0,false,false);
            var expected=RailwayNbt.read(RailwayNbt.write(state,h.getLevel().registryAccess()),h.getLevel().registryAccess());for(int n=0;n<3;n++)TrainMotion.step(expected,train.id(),new TrainMotion.Control(0,false,TrainMotion.Turn.STRAIGHT),0);
            RailwayManager.tick(h.getLevel());h.assertTrue(Math.abs(state.trains().get(train.id()).speed()-expected.trains().get(train.id()).speed())<1e-12,"D1: seated neutral дает native coast, не тормоз");
            h.assertTrue(state.trains().get(train.id()).head().equals(expected.trains().get(train.id()).head()),"D1: coast path трех подшагов совпал");
            p.stopRiding();p.moveTo(p.getX()+10,p.getY(),p.getZ());var speed=state.trains().get(train.id()).speed();RailwayManager.tick(h.getLevel());h.assertTrue(Math.abs(state.trains().get(train.id()).speed()-(speed-.015))<1e-12,"D1: unattended продолжает тормозить");h.succeed();
        } finally {p.server.getPlayerList().remove(p);RailwayWorldGameTest.cleanupTrack(h,rails);}
    }
    @GameTest(template="rail48",batch="railway_review")
    public static void actualDriverContextDefinesManualStatus(GameTestHelper h) {
        var rails=RailwayWorldGameTest.loadedTrack(h);var state=RailwaySavedData.get(h.getLevel()).state();var p=RailwayWorldGameTest.realPlayer(h);
        try {
            var train=train(state,rails,21,0,RailData.StockKind.LOCOMOTIVE);RailwayManager.reconcileViews(h.getLevel());var pose=state.lastPoses().get(train.cars().getFirst()).position();p.moveTo(pose.x()+1,pose.y(),pose.z());
            RollingStockEntity view=null;for(var e:h.getLevel().getAllEntities())if(e instanceof RollingStockEntity stock&&train.cars().contains(stock.carId()))view=stock;
            h.assertTrue(view!=null&&view.interact(p,InteractionHand.MAIN_HAND).consumesAction(),"Actual ServerPlayer seated");RailwayManager.tick(h.getLevel());
            h.assertTrue(state.trains().get(train.id()).status()==Status.MANUAL_CONTROL,"D2: seated stopped имеет native state9");
            var car=state.cars().get(train.cars().getFirst());car.fuel().orElseThrow().extract(0,2,false);p.setPlayerInput(0,1,false,false);RailwayManager.tick(h.getLevel());h.assertTrue(state.trains().get(train.id()).status()==Status.MANUAL_CONTROL,"D2: seated nofuel остается state9");
            p.stopRiding();p.moveTo(p.getX()+10,p.getY(),p.getZ());var before=state.trains().get(train.id());state.commitMotion(before,state.graph().revision(),new MotionUpdate(before.head(),before.headIndex(),before.tape(),.03,before.status()));RailwayManager.tick(h.getLevel());
            h.assertTrue(state.trains().get(train.id()).status()==Status.MANUAL_CONTROL_STOP&&state.trains().get(train.id()).speed()>0,"D2: absent moving/braking имеет native state8");RailwayManager.tick(h.getLevel());RailwayManager.tick(h.getLevel());
            h.assertTrue(state.trains().get(train.id()).status()==Status.MANUAL_CONTROL&&state.trains().get(train.id()).speed()==0,"D2: absent fully stopped переходит в native state9");h.succeed();
        } finally {p.server.getPlayerList().remove(p);RailwayWorldGameTest.cleanupTrack(h,rails);}
    }
    @GameTest(template="rail48",batch="railway_review")
    public static void actualRayPicksVisibleStockEnd(GameTestHelper h) {
        var rails=RailwayWorldGameTest.loadedTrack(h);var state=RailwaySavedData.get(h.getLevel()).state();var p=player(h);
        try {
            var train=train(state,rails,21,0,RailData.StockKind.LOCOMOTIVE);RailwayManager.reconcileViews(h.getLevel());RollingStockEntity view=null;for(var e:h.getLevel().getAllEntities())if(e instanceof RollingStockEntity stock&&train.cars().contains(stock.carId()))view=stock;
            h.assertTrue(view!=null,"Actual stock view присутствует");var start=view.position().add(-4,1,2);var end=view.position().add(4,1,2);p.moveTo(start.x,start.y,start.z);
            var picked=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p,start,end,new net.minecraft.world.phys.AABB(start,end).inflate(1),e->e instanceof RollingStockEntity,64);
            h.assertTrue(picked!=null&&picked.getEntity()==view,"D3: actual ProjectileUtil выбирает видимый борт кузова у конца");h.succeed();
        } finally {RailwayWorldGameTest.cleanupTrack(h,rails);}
    }
    @GameTest(template="rail48",batch="railway_review")
    public static void terminalPartialDeadTargetUsesContactSpeedButKeepsEndSpeedZero(GameTestHelper h) {
        var rails=RailwayWorldGameTest.loadedTrack(h);var state=RailwaySavedData.get(h.getLevel()).state();var target=new net.minecraft.world.entity.animal.Pig(net.minecraft.world.entity.EntityType.PIG,h.getLevel());
        try {
            var train=train(state,rails,36,.9,RailData.StockKind.LOCOMOTIVE);var end=sample(rails.getLast(),2).position();target.moveTo(end.x(),end.y()+.2,end.z()-.2);target.setNoAi(true);target.setHealth(.1f);h.assertTrue(h.getLevel().addFreshEntity(target),"Actual target admission");
            var proposal=TrainMotion.propose(state,train.id(),GO,0).orElseThrow();h.assertTrue(proposal.distance()==.5&&proposal.speed()==0,"C2 dead fixture: terminal partial");
            RailWorldCollision.resolve(h.getLevel(),state,List.of(proposal));
            h.assertTrue(!target.isAlive(),"C2: contact speed дает реальный damage и смерть");
            h.assertTrue(!state.trains().get(train.id()).head().equals(train.head())&&state.trains().get(train.id()).speed()==0,"C2: остаток пути пройден, final speed0 не повышена до contact speed");h.succeed();
        } finally {target.discard();RailwayWorldGameTest.cleanupTrack(h,rails);}
    }
}
