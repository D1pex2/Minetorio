package dev.minetorio.gametest;

import com.mojang.authlib.GameProfile;
import dev.minetorio.content.rail.*;
import dev.minetorio.content.research.ResearchSavedData;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class RailwayWorldGameTest {

    private record Line(dev.minetorio.core.rail.RailwayState state, java.util.List<dev.minetorio.core.rail.RailGeometry.Piece> rails, java.util.UUID train, java.util.UUID car) {
        void cleanup() {
            var ids=rails.stream().map(dev.minetorio.core.rail.RailGeometry.Piece::id).collect(java.util.stream.Collectors.toSet());
            var remove=new java.util.HashSet<UUID>();remove.add(car);
            state.trains().values().stream().filter(t->t.id().equals(train)||t.tape().stream().anyMatch(step->ids.contains(step.piece()))).forEach(t->remove.addAll(t.cars()));
            remove.forEach(id->state.removeCar(id,true));state.replaceRails(state.rails().stream().filter(p->!ids.contains(p.id())).toList());
        }
    }
    private static Line remoteLine(GameTestHelper h) {
        var state=RailwaySavedData.get(h.getLevel()).state();
        java.util.List<dev.minetorio.core.rail.RailGeometry.Piece> rails=new java.util.ArrayList<>();
        int x=2000000+128*Math.floorMod(UUID.randomUUID().hashCode(),100000);
        for(int i=0;i<100;i++)rails.add(new dev.minetorio.core.rail.RailGeometry.Piece(java.util.UUID.randomUUID(),new dev.minetorio.core.rail.RailGeometry.Point(x,80,-i*2),dev.minetorio.core.rail.RailGeometry.Shape.STRAIGHT,0,false));
        var combined=new java.util.ArrayList<>(state.rails());combined.addAll(rails);state.replaceRails(combined);
        var car=state.createCar(java.util.UUID.randomUUID(),dev.minetorio.core.data.RailData.StockKind.LOCOMOTIVE,false);
        car.fuel().orElseThrow().insert(0,"minetorio:coal",5,false);
        var tape=rails.stream().map(p->new dev.minetorio.core.rail.RailGeometry.Traversal(p.id(),true)).toList();
        var train=state.createTrain(new dev.minetorio.core.rail.RailwayState.Train(java.util.UUID.randomUUID(),java.util.List.of(car.id()),java.util.Optional.of(new dev.minetorio.core.rail.RailGeometry.Cursor(tape.get(15),1)),15,tape,1.2,dev.minetorio.core.rail.RailwayState.Mode.AUTOMATIC,dev.minetorio.core.rail.RailwayState.Status.NO_SCHEDULE,0,0,0));
        var target=new dev.minetorio.core.rail.TrainSchedule.RailTarget(new dev.minetorio.core.rail.RailGeometry.Cursor(tape.get(90),1),false);
        var stop=new dev.minetorio.core.rail.TrainSchedule.Stop(java.util.Optional.empty(),java.util.Optional.of(target),false,java.util.List.of(new dev.minetorio.core.rail.TrainSchedule.Wait(dev.minetorio.core.rail.TrainSchedule.Type.TIME,dev.minetorio.core.rail.TrainSchedule.Join.AND,9999,java.util.Optional.empty())));
        state.dispatcher().setSchedule(train.id(),new dev.minetorio.core.rail.TrainSchedule(java.util.List.of(stop),0));
        return new Line(state,rails,train.id(),car.id());
    }
    @GameTest(template="empty16")
    public static void continuesAcrossUnloadedMiddleChunks(GameTestHelper h) {
        var line=remoteLine(h);var middle=line.rails().get(17).origin();
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(middle.x()>>4,middle.z()>>4)==null&&!h.getLevel().hasChunk(middle.x()>>4,middle.z()>>4),"Средний чанк действительно отсутствует");
        var expected=RailwayNbt.read(RailwayNbt.write(line.state(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        for(int i=0;i<3;i++)expected.dispatcher().tick(dev.minetorio.core.rail.RailCircuitInputs.NONE,0);
        long clock=line.state().railClock();
        h.runAfterDelay(1,()->{
            try {
            h.assertTrue(line.state().railClock()==clock+3,"Один настоящий server tick дает ровно три исходных шага");
            h.assertTrue(line.state().trains().get(line.train()).head().equals(expected.trains().get(line.train()).head()),"Путь совпадает с тремя core шагами");
            h.assertTrue(line.state().cars().get(line.car()).remainingJ().equals(expected.cars().get(line.car()).remainingJ()),"Топливо совпадает с тремя core шагами");
            h.assertTrue(!h.getLevel().hasChunk(middle.x()>>4,middle.z()>>4),"Симуляция не загрузила средний чанк");
            h.succeed();
            } finally {line.cleanup();}
        });
    }
    @GameTest(template="empty16")
    public static void brokenRailStopsWithoutDroppingCargo(GameTestHelper h) {
        var line=remoteLine(h);var before=dev.minetorio.core.rail.TrainMotion.carPoses(line.state(),line.train());
        var fuel=line.state().cars().get(line.car()).fuel().orElseThrow().snapshot();
        var removed=line.rails().get(15).id();line.state().replaceRails(line.state().rails().stream().filter(p->!p.id().equals(removed)).toList());
        var restored=RailwayNbt.read(RailwayNbt.write(line.state(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored.trains().get(line.train()).status()==dev.minetorio.core.rail.RailwayState.Status.PATH_LOST,"Внешнее разрушение останавливает поезд");
        h.assertTrue(dev.minetorio.core.rail.TrainMotion.carPoses(restored,line.train()).equals(before),"Последняя owner поза сохраняется через NBT");
        h.assertTrue(restored.cars().get(line.car()).fuel().orElseThrow().snapshot().equals(fuel),"Выгрузка не теряет топливо");line.cleanup();h.succeed();
    }
    @GameTest(template="empty16")
    public static void staleEntityNbtCannotResurrectTrain(GameTestHelper h) {
        var line=remoteLine(h);net.minecraft.nbt.CompoundTag tag=new net.minecraft.nbt.CompoundTag();
        var view=new RollingStockEntity(RailRegistry.STOCK.get(),h.getLevel());view.bind(line.car());view.saveWithoutId(tag);
        h.assertTrue(!tag.contains("cargo")&&!tag.contains("fuel")&&!tag.contains("fluid"),"Проекция не сохраняет ресурсы");
        line.state().removeCar(line.car(),true);
        var stale=new RollingStockEntity(RailRegistry.STOCK.get(),h.getLevel());stale.load(tag);RailwayManager.validateView(h.getLevel(),stale);
        h.assertTrue(stale.isRemoved()&&!line.state().cars().containsKey(line.car()),"Старый entity NBT не воскрешает car");line.cleanup();h.succeed();
    }

    @GameTest(template = "empty16")
    public static void railItemPlacesResearchGatedWorldRail(GameTestHelper h) {
        var level = h.getLevel();
        var research = ResearchSavedData.get(level.getServer());
        Set<String> researched = Set.copyOf(research.state().researched());
        String current = research.state().current().map(t -> t.id()).orElse(null);
        Map<String,Integer> progress = Map.copyOf(research.state().progressById());
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "rail-placement"));
        player.setGameMode(GameType.SURVIVAL);
        BlockPos raw = h.absolutePos(new BlockPos(8,1,10));
        BlockPos anchor = new BlockPos(Math.floorDiv(raw.getX(),2)*2,raw.getY(),Math.floorDiv(raw.getZ(),2)*2);
        for (int x=-2;x<=5;x++) for (int z=-9;z<=2;z++) {
            level.setBlockAndUpdate(anchor.offset(x,-1,z), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(anchor.offset(x,0,z), Blocks.AIR.defaultBlockState());
        }
        player.moveTo(anchor.getX()+1,anchor.getY(),anchor.getZ()+2,180,0);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RailRegistry.RAIL.get(),10));
        player.getMainHandItem().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Именованные рельсы"));
        var hit = new BlockHitResult(Vec3.atCenterOf(anchor.below()).add(0,.5,0),Direction.UP,anchor.below(),false);
        var previousRails=Set.copyOf(RailwaySavedData.get(level).state().rails());
        int before = previousRails.size();
        try {
            research.restore(Set.of(),null,Map.of());
            player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
            h.assertTrue(RailwaySavedData.get(level).state().rails().size()==before && player.getMainHandItem().getCount()==10,"Закрытая технология не разрешает постановку");
            research.restore(Set.of("railway"),null,Map.of());
            player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
            h.assertTrue(RailwaySavedData.get(level).state().rails().size()==before+1,"Предмет rail должен создать настоящий мировой рельс после исследования");
            h.assertTrue(player.getMainHandItem().getCount()==9,"Прямой рельс расходует один предмет");
            player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
            h.assertTrue(RailwaySavedData.get(level).state().rails().size()==before+1 && player.getMainHandItem().getCount()==9,"Повтор постановки не создает оплаченный дубликат");
            player.setYRot(0);var oppositeFloor=anchor.offset(2,-1,-2);
            var oppositeHit=new BlockHitResult(Vec3.atCenterOf(oppositeFloor).add(0,.5,0),Direction.UP,oppositeFloor,false);
            player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,oppositeHit));
            h.assertTrue(RailwaySavedData.get(level).state().rails().size()==before+1&&player.getMainHandItem().getCount()==9,"Обратное описание той же геометрии не создает rail и не оплачивается повторно");
            player.setYRot(180);
            player.setShiftKeyDown(true);player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
            h.assertTrue(RailwaySavedData.get(level).state().rails().size()==before+2&&player.getMainHandItem().getCount()==5,"Настоящая кривая стоит четыре rail");
            player.setShiftKeyDown(false);player.setYRot(225);var diagonalFloor=anchor.offset(4,-1,0);
            var diagonalHit=new BlockHitResult(Vec3.atCenterOf(diagonalFloor).add(0,.5,0),Direction.UP,diagonalFloor,false);
            player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,diagonalHit));
            h.assertTrue(RailwaySavedData.get(level).state().rails().size()==before+3&&player.getMainHandItem().getCount()==4,"Диагональ по реальному yaw стоит один rail");
        } finally { research.restore(researched,current,progress);var owner=RailwaySavedData.get(level).state();owner.replaceRails(owner.rails().stream().filter(previousRails::contains).toList());RailwayManager.reconcileViews(level); }
        h.succeed();
    }
    @GameTest(template="empty16")
    public static void globalSweepHitsUnloadedStockBeforeFuelCommit(GameTestHelper h) {
        var line=remoteLine(h);var state=line.state();state.dispatcher().setMode(line.train(),dev.minetorio.core.rail.RailwayState.Mode.MANUAL);
        var train=state.trains().get(line.train());
        state.commitMotion(train,state.graph().revision(),new dev.minetorio.core.rail.RailwayState.MotionUpdate(train.head(),train.headIndex(),train.tape(),.9,dev.minetorio.core.rail.RailwayState.Status.MANUAL_CONTROL));
        train=state.trains().get(line.train());var before=train.head();
        var target=state.createCar(UUID.randomUUID(),dev.minetorio.core.data.RailData.StockKind.CARGO,false);
        var tape=train.tape();state.createTrain(new dev.minetorio.core.rail.RailwayState.Train(UUID.randomUUID(),List.of(target.id()),Optional.of(new dev.minetorio.core.rail.RailGeometry.Cursor(tape.get(19),.4)),19,tape,0,dev.minetorio.core.rail.RailwayState.Mode.MANUAL,dev.minetorio.core.rail.RailwayState.Status.MANUAL_CONTROL_STOP,0,0,0));
        var proposal=dev.minetorio.core.rail.TrainMotion.propose(state,train.id(),new dev.minetorio.core.rail.TrainMotion.Control(1,false,dev.minetorio.core.rail.TrainMotion.Turn.STRAIGHT),0).orElseThrow();
        RailWorldCollision.resolve(h.getLevel(),state,List.of(proposal));
        h.assertTrue(target.hp()<600,"Глобальный sweep повреждает вагон без loaded entity");
        h.assertTrue(state.trains().get(train.id()).speed()==0&&state.trains().get(train.id()).head().equals(before),"Выжившая цель отменяет поступательное движение атакующего");
        h.assertTrue(state.cars().get(line.car()).remainingJ().compareTo(new java.math.BigDecimal("3990000"))==0,"Удар при тяге списывает один native бюджет");line.cleanup();h.succeed();
    }

    static ServerPlayer realPlayer(GameTestHelper h) {
        return realPlayer(h,UUID.randomUUID());
    }
    static ServerPlayer realPlayer(GameTestHelper h,UUID id) {
        var profile=new GameProfile(id,"rail-rider");
        var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        var cookie=new net.minecraft.server.network.CommonListenerCookie(profile,0,net.minecraft.server.level.ClientInformation.createDefault(),false,net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,player,cookie);player.setGameMode(GameType.SURVIVAL);return player;
    }
    static List<dev.minetorio.core.rail.RailGeometry.Piece> loadedTrack(GameTestHelper h) {
        var at=h.absolutePos(new BlockPos(8,2,24));int x=Math.floorDiv(at.getX(),2)*2,z=Math.floorDiv(at.getZ(),2)*2+20;
        List<dev.minetorio.core.rail.RailGeometry.Piece> rails=new ArrayList<>();
        for(int n=0;n<20;n++)rails.add(new dev.minetorio.core.rail.RailGeometry.Piece(UUID.randomUUID(),new dev.minetorio.core.rail.RailGeometry.Point(x,at.getY(),z-n*2),dev.minetorio.core.rail.RailGeometry.Shape.STRAIGHT,0,false));
        var state=RailwaySavedData.get(h.getLevel()).state();var combined=new ArrayList<>(state.rails());combined.addAll(rails);state.replaceRails(combined);RailwayManager.reconcileViews(h.getLevel());return rails;
    }
    static void cleanupTrack(GameTestHelper h,List<dev.minetorio.core.rail.RailGeometry.Piece> rails) {
        var state=RailwaySavedData.get(h.getLevel()).state();var ids=rails.stream().map(dev.minetorio.core.rail.RailGeometry.Piece::id).collect(java.util.stream.Collectors.toSet());
        var cars=state.trains().values().stream().filter(t->t.tape().stream().anyMatch(p->ids.contains(p.piece()))).flatMap(t->t.cars().stream()).distinct().toList();
        cars.forEach(id->state.removeCar(id,true));state.replaceRails(state.rails().stream().filter(p->!ids.contains(p.id())).toList());RailwayManager.reconcileViews(h.getLevel());
    }
    @GameTest(template="rail48",batch="railway_riding",timeoutTicks=100)
    public static void actualPassengerUsesServerInputAndReleasesTicketsOnLogout(GameTestHelper h) {
        var rails=loadedTrack(h);ServerPlayer player=realPlayer(h);var state=RailwaySavedData.get(h.getLevel()).state();
        var research=ResearchSavedData.get(player.server);var old=Set.copyOf(research.state().researched());String current=research.state().current().map(t->t.id()).orElse(null);var progress=Map.copyOf(research.state().progressById());
        UUID carId;
        try {
            research.restore(Set.of("railway"),null,Map.of());var piece=rails.get(10);var pos=dev.minetorio.core.rail.RailGeometry.sample(piece,1).position();
            player.moveTo(pos.x()+1,pos.y(),pos.z());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RailRegistry.LOCOMOTIVE.get()));
            carId=RailwayManager.placeStock(player,dev.minetorio.core.data.RailData.StockKind.LOCOMOTIVE,new dev.minetorio.core.rail.RailGeometry.Cursor(new dev.minetorio.core.rail.RailGeometry.Traversal(piece.id(),true),1),false).orElseThrow();
        } finally {research.restore(old,current,progress);}
        var car=state.cars().get(carId);car.fuel().orElseThrow().insert(0,"minetorio:coal",2,false);
        RollingStockEntity view=null;for(var entity:h.getLevel().getAllEntities())if(entity instanceof RollingStockEntity stock&&carId.equals(stock.carId()))view=stock;
        h.assertTrue(view!=null&&view.interact(player,InteractionHand.MAIN_HAND).consumesAction(),"Настоящий ServerPlayer садится через entity interaction");
        h.assertTrue(player.getVehicle()==view&&player.server.getPlayerList().getPlayer(player.getUUID())==player,"Игрок зарегистрирован в PlayerList и действительно едет");
        h.assertTrue(RailwayManager.passengerTicketCount(h.getLevel())>0,"Посадка выдает passenger ticket");
        UUID train=state.trains().values().stream().filter(t->t.cars().contains(carId)).findFirst().orElseThrow().id();
        player.setPlayerInput(0,1,false,false);
        h.runAfterDelay(2,()->{
            try {
                h.assertTrue(state.trains().get(train).speed()>0,"Серверный ввод реального пассажира дает тягу: speed="+state.trains().get(train).speed()+", input="+player.zza+", vehicle="+player.getVehicle()+", fuel="+car.remainingJ()+", status="+state.trains().get(train).status());
                h.assertTrue(h.getLevel().isPositionEntityTicking(player.blockPosition()),"Passenger ticket обеспечивает entity-ticking");
                player.server.getPlayerList().remove(player);
                RailwayManager.tick(h.getLevel());
                h.assertTrue(RailwayManager.passengerTicketCount(h.getLevel())==0,"Logout освобождает passenger tickets");
                h.assertTrue(state.cars().containsKey(carId),"Logout проекции не удаляет car owner");h.succeed();
            } finally {if(player.server.getPlayerList().getPlayer(player.getUUID())!=null)player.server.getPlayerList().remove(player);cleanupTrack(h,rails);}
        });
    }
    @GameTest(template="rail48")
    public static void loadedCellReconcilesReferencesIdempotently(GameTestHelper h) {
        var rails=loadedTrack(h);
        try {
            var piece=rails.get(10);var pos=RailPlacement.block(dev.minetorio.core.rail.RailGeometry.cells(piece).iterator().next());
            var be=(RailCellBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(be!=null,"Есть настоящий RailCell BE");
            be.project(List.of());RailwayManager.reconcileCell(h.getLevel(),pos);var first=be.pieces();
            RailwayManager.reconcileCell(h.getLevel(),pos);h.assertTrue(be.pieces().equals(first)&&first.stream().anyMatch(p->p.id().equals(piece.id())),"Owner восстанавливает ссылки без дубликатов");
            be.onChunkUnloaded();h.assertTrue(RailwaySavedData.get(h.getLevel()).state().graph().piece(piece.id()).isPresent(),"BE unload не удаляет мировой rail");
            h.succeed();
        } finally {cleanupTrack(h,rails);}
    }

    @GameTest(template="rail48")
    public static void allThreeStockItemsMineOnceAndPreserveComponentsAndHealth(GameTestHelper h) {
        var rails=loadedTrack(h);var state=RailwaySavedData.get(h.getLevel()).state();
        ServerPlayer player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"stock-items"));player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();
        var research=ResearchSavedData.get(player.server);var old=Set.copyOf(research.state().researched());String current=research.state().current().map(t->t.id()).orElse(null);var progress=Map.copyOf(research.state().progressById());
        Map<dev.minetorio.core.data.RailData.StockKind,UUID> ids=new EnumMap<>(dev.minetorio.core.data.RailData.StockKind.class);
        try {
            research.restore(Set.of("railway","fluid_wagon"),null,Map.of());int q=9;
            for(var kind:dev.minetorio.core.data.RailData.StockKind.values()) {
                var piece=rails.get(q/2);var pose=dev.minetorio.core.rail.RailGeometry.sample(piece,q%2).position();player.moveTo(pose.x()+1,pose.y(),pose.z());
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RailRegistry.stockItem(kind)));
                var before=Set.copyOf(state.cars().keySet());var hit=new BlockHitResult(new Vec3(pose.x(),pose.y(),pose.z()),Direction.UP,BlockPos.containing(pose.x(),pose.y(),pose.z()),false);
                h.assertTrue(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Настоящий item ставит "+kind);
                var car=state.cars().values().stream().filter(c->!before.contains(c.id())).findFirst().orElseThrow();ids.put(kind,car.id());h.assertTrue(player.getMainHandItem().isEmpty(),"Постановка списывает stock item");q+=7;
            }
            h.assertTrue(state.trains().values().stream().anyMatch(t->t.cars().size()==3&&t.cars().containsAll(ids.values())),"Три item placements автоматически сцепились в один состав");
            var cargo=state.cars().get(ids.get(dev.minetorio.core.data.RailData.StockKind.CARGO));cargo.setHp(300);
            ItemStack pistol=new ItemStack(dev.minetorio.content.combat.CombatRegistry.PISTOL.get());pistol.set(dev.minetorio.content.combat.CombatRegistry.ROUNDS.get(),7);pistol.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,1);
            String key=dev.minetorio.content.belt.LaneStacks.toLane(pistol,h.getLevel().registryAccess()).orElseThrow();cargo.cargo().orElseThrow().insertManual(0,key,1,false);
            var loco=state.cars().get(ids.get(dev.minetorio.core.data.RailData.StockKind.LOCOMOTIVE));loco.fuel().orElseThrow().insert(0,"minetorio:coal",3,false);loco.setActiveFuel("minetorio:coal",new java.math.BigDecimal("100000"));
            state.cars().get(ids.get(dev.minetorio.core.data.RailData.StockKind.FLUID)).insertFluid("water",1234);
            for(UUID id:ids.values()) {
                var pose=state.lastPoses().get(id).position();player.moveTo(pose.x()+1,pose.y(),pose.z());
                h.assertTrue(RailwayManager.removeStock(player,id),"Разбор stationary stock");h.assertTrue(!RailwayManager.removeStock(player,id),"Повторный разбор не выдает второй stock");
            }
            var inventory=dev.minetorio.content.character.CharacterInventory.main(player.getInventory());ItemStack minedCargo=ItemStack.EMPTY;int pistols=0,coal=0;
            for(int slot=0;slot<inventory.getContainerSize();slot++) {
                ItemStack stack=inventory.getItem(slot);if(stack.is(RailRegistry.CARGO_WAGON.get())) {minedCargo=stack;inventory.setItem(slot,ItemStack.EMPTY);}
                if(stack.is(dev.minetorio.content.combat.CombatRegistry.PISTOL.get())) {pistols+=stack.getCount();h.assertTrue(stack.getOrDefault(dev.minetorio.content.combat.CombatRegistry.ROUNDS.get(),0)==7,"Заряд оружия не потерян");}
                if(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("minetorio:coal"))coal+=stack.getCount();
            }
            h.assertTrue(pistols==1&&coal==3,"Возвращаются только физические слоты, без дополнительного горящего угля");
            h.assertTrue(!minedCargo.isEmpty()&&minedCargo.getOrDefault(RailRegistry.STOCK_HEALTH.get(),1.0)==.5,"Добытый предмет сохраняет долю HP");
            var piece=rails.get(10);var pose=dev.minetorio.core.rail.RailGeometry.sample(piece,1).position();player.moveTo(pose.x()+1,pose.y(),pose.z());player.setItemInHand(InteractionHand.MAIN_HAND,minedCargo);
            UUID restored=RailwayManager.placeStock(player,dev.minetorio.core.data.RailData.StockKind.CARGO,new dev.minetorio.core.rail.RailGeometry.Cursor(new dev.minetorio.core.rail.RailGeometry.Traversal(piece.id(),true),1),false).orElseThrow();
            h.assertTrue(state.cars().get(restored).hp()==300,"Переустановка не ремонтирует вагон бесплатно");
            var invalid=new ItemStack(RailRegistry.CARGO_WAGON.get());invalid.set(RailRegistry.STOCK_HEALTH.get(),Double.NaN);player.setItemInHand(InteractionHand.MAIN_HAND,invalid);int carCount=state.cars().size();
            h.assertTrue(RailwayManager.placeStock(player,dev.minetorio.core.data.RailData.StockKind.CARGO,new dev.minetorio.core.rail.RailGeometry.Cursor(new dev.minetorio.core.rail.RailGeometry.Traversal(rails.get(5).id(),true),1),false).isEmpty()&&invalid.getCount()==1&&state.cars().size()==carCount,"Неверная health fraction не создает car и не списывает item");h.succeed();
        } finally {research.restore(old,current,progress);cleanupTrack(h,rails);}
    }

    @GameTest(template="rail48")
    public static void normalRailRemovalChecksPhysicalBodyAndPreservesCargo(GameTestHelper h) {
        var rails=loadedTrack(h);var state=RailwaySavedData.get(h.getLevel()).state();
        ServerPlayer player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"rail-mining"));player.setGameMode(GameType.SURVIVAL);
        try {
            var car=state.createCar(UUID.randomUUID(),dev.minetorio.core.data.RailData.StockKind.CARGO,false);car.cargo().orElseThrow().insertManual(0,"minetorio:iron_plate",17,false);
            var tape=rails.stream().map(p->new dev.minetorio.core.rail.RailGeometry.Traversal(p.id(),true)).toList();
            var train=state.createTrain(new dev.minetorio.core.rail.RailwayState.Train(UUID.randomUUID(),List.of(car.id()),Optional.of(new dev.minetorio.core.rail.RailGeometry.Cursor(tape.get(10),1)),10,tape,0,dev.minetorio.core.rail.RailwayState.Mode.MANUAL,dev.minetorio.core.rail.RailwayState.Status.MANUAL_CONTROL_STOP,0,0,0));
            var occupied=rails.get(10).origin();player.moveTo(occupied.x()+1,occupied.y(),occupied.z());
            h.assertTrue(!RailPlacement.remove(player,rails.get(10).id()),"Нельзя снять rail под телом");
            var free=rails.get(17).origin();player.moveTo(free.x()+1,free.y(),free.z());
            h.assertTrue(RailPlacement.remove(player,rails.get(17).id()),"Незанятая будущая часть tape снимается обычным действием");
            h.assertTrue(state.cars().get(car.id()).cargo().orElseThrow().get(0).count()==17,"Снятие свободного rail сохраняет cargo");
            h.assertTrue(state.trains().containsKey(train.id()),"Нарушение пути не удаляет состав");h.succeed();
        } finally {cleanupTrack(h,rails);}
    }
    @GameTest(template="rail48")
    public static void actualSignalAndStopItemsKeepDisconnectedConfiguration(GameTestHelper h) throws Exception {
        var rails=loadedTrack(h);var data=RailwaySavedData.get(h.getLevel());var state=data.state();
        ServerPlayer player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"rail-attachments"));player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();
        var research=ResearchSavedData.get(player.server);var old=Set.copyOf(research.state().researched());String current=research.state().current().map(t->t.id()).orElse(null);var progress=Map.copyOf(research.state().progressById());
        Set<UUID> stopIds=new HashSet<>();Set<UUID> signalIds=new HashSet<>();
        try {
            research.restore(Set.of("railway","automated_rail_transportation","rail_signals"),null,Map.of());
            var piece=rails.get(10);
            for(boolean forward:new boolean[]{true,false}) {
                var pose=dev.minetorio.core.rail.RailGeometry.sample(piece,1).position();
                player.moveTo(pose.x()+1,pose.y(),pose.z());player.setYRot(forward?180:0);
                var base=BlockPos.containing(pose.x(),pose.y(),pose.z());for(int x=-3;x<=3;x++)for(int z=-2;z<=2;z++)h.getLevel().setBlockAndUpdate(base.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RailRegistry.TRAIN_STOP.get()));
                var before=Set.copyOf(data.worldStops().keySet());var hit=new BlockHitResult(new Vec3(pose.x(),pose.y(),pose.z()),Direction.UP,base,false);
                h.assertTrue(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Настоящий предмет ставит станцию в обоих направлениях");
                var stop=data.worldStops().values().stream().filter(v->!before.contains(v.id())).findFirst().orElseThrow();stopIds.add(stop.id());
                h.assertTrue(stop.forward()==forward&&Math.abs(stop.distance()-3.5-(1-3))<1e-9,"Coupler target сохраняет native отступ центра локомотива 3 tiles");
                data.putStop(new RailwaySavedData.WorldStop(stop.id(),stop.position(),"Именная станция",false,OptionalInt.of(2),stop.railOrigin(),stop.shape(),stop.rotation(),stop.mirrored(),stop.forward(),stop.distance()));
            }
            for(int kind=0;kind<2;kind++) {
                var signalPiece=rails.get(4+kind*2);var pose=dev.minetorio.core.rail.RailGeometry.sample(signalPiece,0).position();var base=BlockPos.containing(pose.x(),pose.y(),pose.z());
                for(int x=-3;x<=3;x++)h.getLevel().setBlockAndUpdate(base.offset(x,-1,0),Blocks.STONE.defaultBlockState());
                player.moveTo(pose.x()+1,pose.y(),pose.z());player.setYRot(180);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(kind==0?RailRegistry.RAIL_SIGNAL.get():RailRegistry.RAIL_CHAIN_SIGNAL.get()));
                var before=state.signalDefinitions().stream().map(dev.minetorio.core.rail.RailSignals.Signal::id).collect(java.util.stream.Collectors.toSet());
                var hit=new BlockHitResult(new Vec3(pose.x(),pose.y(),pose.z()),Direction.UP,base,false);
                h.assertTrue(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Настоящий предмет ставит сигнал "+kind);
                state.signalDefinitions().stream().filter(v->!before.contains(v.id())).forEach(v->signalIds.add(v.id()));
            }
            state.replaceRails(state.rails().stream().filter(p->!p.id().equals(piece.id())).toList());data.rebindStops();
            h.assertTrue(stopIds.stream().allMatch(id->data.worldStops().containsKey(id)&&!state.stations().containsKey(id)),"Отключение пути сохраняет world config без logical attachment");
            var folder=java.nio.file.Files.createTempDirectory("minetorio-world-stop-");var registries=h.getLevel().registryAccess();
            var tag=new net.minecraft.nbt.CompoundTag();tag.put("data",data.save(new net.minecraft.nbt.CompoundTag(),registries));net.minecraft.nbt.NbtIo.writeCompressed(tag,folder.resolve(RailwaySavedData.NAME+".dat"));
            var loaded=RailwaySavedData.get(new net.minecraft.world.level.storage.DimensionDataStorage(folder.toFile(),net.minecraft.util.datafix.DataFixers.getDataFixer(),registries),folder,registries);
            for(UUID id:stopIds)h.assertTrue(loaded.worldStops().get(id).equals(data.worldStops().get(id)),"Disconnected reload сохраняет все настройки");
            var rebuilt=new ArrayList<>(state.rails());rebuilt.add(piece);state.replaceRails(rebuilt);data.rebindStops();
            h.assertTrue(stopIds.stream().allMatch(id->state.stations().containsKey(id)),"Повторная укладка восстанавливает attachment");
            for(UUID id:stopIds)h.assertTrue(RailPlacement.destroyAttachment(h.getLevel(),data.worldStops().get(id).position()),"Демонтаж остановки удаляет owner config");h.succeed();
        } finally {
            for(UUID id:stopIds)data.removeStop(id);for(UUID id:signalIds)data.removeSignal(id);state.replaceSignals(state.signalDefinitions().stream().filter(v->!signalIds.contains(v.id())).toList());research.restore(old,current,progress);cleanupTrack(h,rails);
        }
    }
    @GameTest(template="rail48",batch="railway_riding_robot",timeoutTicks=800)
    public static void actualRiderRobotLaunchAndReturnDriveWaitInput(GameTestHelper h) {
        var rails=loadedTrack(h);var player=realPlayer(h);var state=RailwaySavedData.get(h.getLevel()).state();
        var research=ResearchSavedData.get(player.server);var old=Set.copyOf(research.state().researched());String current=research.state().current().map(t->t.id()).orElse(null);var progress=Map.copyOf(research.state().progressById());
        UUID carId;
        try {
            research.restore(Set.of("railway"),null,Map.of());var piece=rails.get(10);var pose=dev.minetorio.core.rail.RailGeometry.sample(piece,1).position();
            player.moveTo(pose.x()+1,pose.y(),pose.z());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RailRegistry.LOCOMOTIVE.get()));
            carId=RailwayManager.placeStock(player,dev.minetorio.core.data.RailData.StockKind.LOCOMOTIVE,new dev.minetorio.core.rail.RailGeometry.Cursor(new dev.minetorio.core.rail.RailGeometry.Traversal(piece.id(),true),1),false).orElseThrow();
        } finally {research.restore(old,current,progress);}
        RollingStockEntity view=null;for(var entity:h.getLevel().getAllEntities())if(entity instanceof RollingStockEntity stock&&carId.equals(stock.carId()))view=stock;
        h.assertTrue(view!=null&&view.interact(player,InteractionHand.MAIN_HAND).consumesAction(),"Владелец роботов действительно едет в локомотиве");
        UUID train=state.trains().values().stream().filter(t->t.cars().contains(carId)).findFirst().orElseThrow().id();
        var ghost=player.blockPosition().offset(4,-1,4);h.getLevel().setBlockAndUpdate(ghost.below(),Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(ghost,Blocks.AIR.defaultBlockState());
        player.getInventory().add(new ItemStack(dev.minetorio.content.logistics.LogisticsRegistry.PERSONAL_ROBOPORT.get()));player.getInventory().add(new ItemStack(dev.minetorio.content.registry.ModItems.CONSTRUCTION_ROBOT.get()));player.getInventory().add(new ItemStack(dev.minetorio.content.registry.ModItems.INSERTER.get()));
        h.assertTrue(dev.minetorio.content.robot.Ghosts.place(h.getLevel(),ghost,dev.minetorio.content.registry.ModItems.INSERTER.get(),Direction.EAST),"Настоящий ghost для запуска робота");
        h.assertTrue(dev.minetorio.content.logistics.PersonalRobotActivity.count(player.server,Set.of(player.getUUID())).equals(OptionalInt.of(0)),"До launch известен ноль");
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> cancel=event->{if(event.getEntity() instanceof dev.minetorio.content.logistics.LogisticRobotEntity robot&&player.getUUID().equals(robot.homePlayer()))event.setCanceled(true);};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
        try {dev.minetorio.content.logistics.PersonalRoboport.tick(player);} finally {net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);}
        h.assertTrue(dev.minetorio.content.logistics.PersonalRobotActivity.count(player.server,Set.of(player.getUUID())).equals(OptionalInt.of(0)),"Canceled actual EntityJoin не создает membership");
        h.assertTrue(dev.minetorio.content.logistics.PlayerItems.count(player,dev.minetorio.content.registry.ModItems.CONSTRUCTION_ROBOT.get())==1&&dev.minetorio.content.logistics.PlayerItems.count(player,dev.minetorio.content.registry.ModItems.INSERTER.get())==1,"Canceled launch вернул robot/material inventory ровно один раз");
        h.assertTrue(!dev.minetorio.content.robot.ConstructionManager.get(h.getLevel()).queue().get(dev.minetorio.content.robot.ConstructionManager.cell(ghost)).orElseThrow().isClaimed(),"Canceled launch освободил ghost claim");
        dev.minetorio.content.logistics.PersonalRoboport.tick(player);
        h.assertTrue(dev.minetorio.content.logistics.PersonalRobotActivity.count(player.server,Set.of(player.getUUID())).equals(OptionalInt.of(1)),"Реальный launch сразу учитывает активного робота");
        var inactive=new dev.minetorio.core.rail.TrainSchedule.Wait(dev.minetorio.core.rail.TrainSchedule.Type.ROBOTS_INACTIVE,dev.minetorio.core.rail.TrainSchedule.Join.AND,0,Optional.empty());
        UUID stationId=UUID.randomUUID();var leading=dev.minetorio.core.rail.TrainReservations.leading(state,train,1).start();
        state.dispatcher().upsertStation(new dev.minetorio.core.rail.RailwayState.Station(stationId,"Ожидание робота",leading,true,OptionalInt.empty()));
        var target=new dev.minetorio.core.rail.TrainSchedule.RailTarget(new dev.minetorio.core.rail.RailGeometry.Cursor(new dev.minetorio.core.rail.RailGeometry.Traversal(rails.get(17).id(),true),1),false);
        state.dispatcher().setSchedule(train,new dev.minetorio.core.rail.TrainSchedule(List.of(
                new dev.minetorio.core.rail.TrainSchedule.Stop(Optional.of("Ожидание робота"),Optional.empty(),false,List.of(inactive)),
                new dev.minetorio.core.rail.TrainSchedule.Stop(Optional.empty(),Optional.of(target),false,List.of(new dev.minetorio.core.rail.TrainSchedule.Wait(dev.minetorio.core.rail.TrainSchedule.Type.TIME,dev.minetorio.core.rail.TrainSchedule.Join.AND,1000,Optional.empty())))),0));
        state.cars().get(carId).fuel().orElseThrow().insert(0,"minetorio:coal",2,false);state.dispatcher().setMode(train,dev.minetorio.core.rail.RailwayState.Mode.AUTOMATIC);
        var waitingHead=state.trains().get(train).head();boolean[] sawReturning={false};String[] firstViolation={null};
        RailwayManager.tick(h.getLevel());
        var context=state.dispatcher().context(train,dev.minetorio.core.rail.RailCircuitInputs.NONE);
        h.assertTrue(context.passengers().orElseThrow().present()&&!dev.minetorio.core.rail.TrainWait.evaluate(List.of(inactive),context),"Actual rider bridge не разрешает robots inactive во время полета");
        h.startSequence().thenWaitUntil(()->{
            boolean activeRobot=false,returning=false;
            for(var entity:h.getLevel().getAllEntities())if(entity instanceof dev.minetorio.content.logistics.LogisticRobotEntity robot&&player.getUUID().equals(robot.homePlayer())) {
                activeRobot=true;if(robot.phase()==dev.minetorio.content.logistics.LogisticRobotEntity.Phase.RETURNING)returning=true;
            }
            sawReturning[0]|=returning;
            int returned=dev.minetorio.content.logistics.PlayerItems.count(player,dev.minetorio.content.registry.ModItems.CONSTRUCTION_ROBOT.get());
            // Polling ожидает возврат, но первое нарушение полета не исчезает на следующем тике.
            // Завершение полета определяется entity/inventory, а не проверяемым activity count.
            if(activeRobot||returned!=1) {
                var count=dev.minetorio.content.logistics.PersonalRobotActivity.count(player.server,Set.of(player.getUUID()));
                if(firstViolation[0]==null&&!count.equals(OptionalInt.of(1)))firstViolation[0]=(returning?"RETURNING":"Полет")+" сохраняет count1: "+count;
                if(firstViolation[0]==null&&(!state.trains().get(train).head().equals(waitingHead)||state.trains().get(train).status()!=dev.minetorio.core.rail.RailwayState.Status.WAIT_STATION))firstViolation[0]="Реальный AUTO не уезжает до возврата робота";
                if(firstViolation[0]==null&&dev.minetorio.core.rail.TrainWait.evaluate(List.of(inactive),state.dispatcher().context(train,dev.minetorio.core.rail.RailCircuitInputs.NONE)))firstViolation[0]="Actual rider bridge не разрешает robots inactive до inventory return";
            }
            h.assertTrue(!activeRobot&&returned==1,"Робот реально вернулся в inventory и удален из мира");
        }).thenExecute(()->{
            // thenExecute завершает тест ошибкой, в отличие от повторяемого thenWaitUntil.
            h.assertTrue(firstViolation[0]==null,"Первое нарушение до normal return: "+firstViolation[0]);
            h.assertTrue(sawReturning[0],"Фаза RETURNING фактически наблюдалась до terminal return");
            h.assertTrue(h.getLevel().getBlockState(ghost).is(dev.minetorio.content.registry.ModBlocks.INSERTER.get()),"Робот построил inserter");
            h.assertTrue(dev.minetorio.content.logistics.PlayerItems.count(player,dev.minetorio.content.registry.ModItems.CONSTRUCTION_ROBOT.get())==1&&dev.minetorio.content.logistics.PlayerItems.count(player,dev.minetorio.content.registry.ModItems.INSERTER.get())==0,"Normal return сохраняет ровно один robot и расходует один material на постройку");
            h.assertTrue(dev.minetorio.content.logistics.PersonalRobotActivity.count(player.server,Set.of(player.getUUID())).equals(OptionalInt.of(0)),"Только terminal return дает count0");
            RailwayManager.tick(h.getLevel());h.assertTrue(dev.minetorio.core.rail.TrainWait.evaluate(List.of(inactive),state.dispatcher().context(train,dev.minetorio.core.rail.RailCircuitInputs.NONE)),"Terminal return разрешает ожидание");
        }).thenWaitUntil(()->{
            h.assertTrue(!state.trains().get(train).head().equals(waitingHead)&&state.trains().get(train).speed()>0,"Actual AUTO departure после normal return");
        }).thenExecute(()->{
            state.dispatcher().removeStation(stationId);player.server.getPlayerList().remove(player);cleanupTrack(h,rails);
        }).thenSucceed();
    }
    @GameTest(template="rail48")
    public static void deniedLivingDamageStopsAtActualSurvivor(GameTestHelper h) {
        var rails=loadedTrack(h);var state=RailwaySavedData.get(h.getLevel()).state();
        var changed=new boolean[]{false};var actor=new dev.minetorio.core.rail.RailwayState.Car[1];
        var pig=new net.minecraft.world.entity.animal.Pig(net.minecraft.world.entity.EntityType.PIG,h.getLevel()) {
            @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount) {if(changed[0])actor[0].setActiveFuel("minetorio:coal",new java.math.BigDecimal("555"));return false;}
        };
        try {
            var car=state.createCar(UUID.randomUUID(),dev.minetorio.core.data.RailData.StockKind.LOCOMOTIVE,false);actor[0]=car;car.fuel().orElseThrow().insert(0,"minetorio:coal",2,false);
            var tape=rails.stream().map(p->new dev.minetorio.core.rail.RailGeometry.Traversal(p.id(),true)).toList();
            var train=state.createTrain(new dev.minetorio.core.rail.RailwayState.Train(UUID.randomUUID(),List.of(car.id()),Optional.of(new dev.minetorio.core.rail.RailGeometry.Cursor(tape.get(10),1)),10,tape,.9,dev.minetorio.core.rail.RailwayState.Mode.MANUAL,dev.minetorio.core.rail.RailwayState.Status.MANUAL_CONTROL,0,0,0));
            var at=dev.minetorio.core.rail.RailGeometry.sample(rails.get(12),1.2).position();pig.moveTo(at.x(),at.y()+.2,at.z());pig.setNoAi(true);pig.setHealth(.1f);h.assertTrue(h.getLevel().addFreshEntity(pig),"Actual living target admission");
            var proposal=dev.minetorio.core.rail.TrainMotion.propose(state,train.id(),new dev.minetorio.core.rail.TrainMotion.Control(1,false,dev.minetorio.core.rail.TrainMotion.Turn.STRAIGHT),0).orElseThrow();
            RailWorldCollision.resolve(h.getLevel(),state,List.of(proposal));
            h.assertTrue(pig.isAlive(),"Server hurt был отклонен, цель жива");
            h.assertTrue(state.trains().get(train.id()).speed()==0&&state.trains().get(train.id()).head().equals(train.head()),"Результат учитывает фактическое выживание, не предсказанную смерть");
            h.assertTrue(car.remainingJ().compareTo(new java.math.BigDecimal("3990000"))==0,"Реальная попытка тяги оплачивается при выжившей цели");
            var stopped=state.trains().get(train.id());state.commitMotion(stopped,state.graph().revision(),new dev.minetorio.core.rail.RailwayState.MotionUpdate(stopped.head(),stopped.headIndex(),stopped.tape(),.9,stopped.status()));
            var beforeCallback=state.trains().get(train.id());var stale=dev.minetorio.core.rail.TrainMotion.propose(state,train.id(),new dev.minetorio.core.rail.TrainMotion.Control(1,false,dev.minetorio.core.rail.TrainMotion.Turn.STRAIGHT),0).orElseThrow();changed[0]=true;
            RailWorldCollision.resolve(h.getLevel(),state,List.of(stale));
            h.assertTrue(state.trains().get(train.id()).equals(beforeCallback)&&car.remainingJ().compareTo(new java.math.BigDecimal("555"))==0,"Hurt callback инвалидирует proposal без отката чужих изменений и без расхода");h.succeed();
        } finally {pig.discard();cleanupTrack(h,rails);}
    }
    @GameTest(template="rail48")
    public static void parallelTrackStockPlacementUsesRealBodies(GameTestHelper h) {
        var first=loadedTrack(h);var second=first.stream().map(p->new dev.minetorio.core.rail.RailGeometry.Piece(UUID.randomUUID(),new dev.minetorio.core.rail.RailGeometry.Point(p.origin().x()+2,p.origin().y(),p.origin().z()),p.shape(),p.quarterTurns(),p.mirrored())).toList();
        var state=RailwaySavedData.get(h.getLevel()).state();var all=new ArrayList<>(state.rails());all.addAll(second);state.replaceRails(all);RailwayManager.reconcileViews(h.getLevel());
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"parallel-stock"));player.setGameMode(GameType.SURVIVAL);
        var research=ResearchSavedData.get(player.server);var old=Set.copyOf(research.state().researched());String current=research.state().current().map(t->t.id()).orElse(null);var progress=Map.copyOf(research.state().progressById());
        try {
            research.restore(Set.of("railway"),null,Map.of());
            for(var rails:List.of(first,second)) {
                var piece=rails.get(10);var pose=dev.minetorio.core.rail.RailGeometry.sample(piece,1).position();player.moveTo(pose.x()+1,pose.y(),pose.z());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RailRegistry.CARGO_WAGON.get()));
                var hit=new BlockHitResult(new Vec3(pose.x(),pose.y(),pose.z()),Direction.UP,BlockPos.containing(pose.x(),pose.y(),pose.z()),false);
                h.assertTrue(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Два параллельных пути с шагом2 допускают непересекающиеся вагоны");
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RailRegistry.CARGO_WAGON.get()));
                h.assertTrue(!player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction()&&player.getMainHandItem().getCount()==1,"Реальное перекрытие отвергается без оплаты");
            }
            var ids=java.util.stream.Stream.concat(first.stream(),second.stream()).map(dev.minetorio.core.rail.RailGeometry.Piece::id).collect(java.util.stream.Collectors.toSet());
            h.assertTrue(state.trains().values().stream().filter(t->t.tape().stream().anyMatch(v->ids.contains(v.piece()))).count()==2,"Параллельные вагоны не сцепляются через соседний путь");h.succeed();
        } finally {research.restore(old,current,progress);cleanupTrack(h,first);cleanupTrack(h,second);}
    }
    @GameTest(template="rail48wide",batch="railway_turn",timeoutTicks=160)
    public static void actualThreeCarTurnKeepsIntervalsAndMovesPassengerTicket(GameTestHelper h) {
        var level=h.getLevel();var state=RailwaySavedData.get(level).state();var base=h.absolutePos(new BlockPos(8,2,20));int x=Math.floorDiv(base.getX(),2)*2,z=Math.floorDiv(base.getZ(),2)*2,y=base.getY();
        List<dev.minetorio.core.rail.RailGeometry.Piece> rails=new ArrayList<>();
        for(int n=12;n>=1;n--)rails.add(new dev.minetorio.core.rail.RailGeometry.Piece(UUID.randomUUID(),new dev.minetorio.core.rail.RailGeometry.Point(x,y,z+2*n),dev.minetorio.core.rail.RailGeometry.Shape.STRAIGHT,0,false));
        rails.add(new dev.minetorio.core.rail.RailGeometry.Piece(UUID.randomUUID(),new dev.minetorio.core.rail.RailGeometry.Point(x,y,z),dev.minetorio.core.rail.RailGeometry.Shape.CURVE,0,false));
        rails.add(new dev.minetorio.core.rail.RailGeometry.Piece(UUID.randomUUID(),new dev.minetorio.core.rail.RailGeometry.Point(x+4,y,z-8),dev.minetorio.core.rail.RailGeometry.Shape.DIAGONAL,0,false));
        rails.add(new dev.minetorio.core.rail.RailGeometry.Piece(UUID.randomUUID(),new dev.minetorio.core.rail.RailGeometry.Point(x+12,y,z-12),dev.minetorio.core.rail.RailGeometry.Shape.CURVE,3,true));
        for(int n=0;n<12;n++)rails.add(new dev.minetorio.core.rail.RailGeometry.Piece(UUID.randomUUID(),new dev.minetorio.core.rail.RailGeometry.Point(x+12+2*n,y,z-12),dev.minetorio.core.rail.RailGeometry.Shape.STRAIGHT,1,false));
        var combined=new ArrayList<>(state.rails());combined.addAll(rails);state.replaceRails(combined);RailwayManager.reconcileViews(level);
        List<dev.minetorio.core.rail.RailGeometry.Traversal> tape=new ArrayList<>();var traversal=new dev.minetorio.core.rail.RailGeometry.Traversal(rails.getFirst().id(),true);
        for(int n=0;n<rails.size();n++){tape.add(traversal);if(n+1<rails.size())traversal=state.graph().next(traversal).getFirst();}
        List<UUID> cars=new ArrayList<>();for(var kind:dev.minetorio.core.data.RailData.StockKind.values())cars.add(state.createCar(UUID.randomUUID(),kind,false).id());state.cars().get(cars.getFirst()).fuel().orElseThrow().insert(0,"minetorio:coal",5,false);
        UUID trainId=UUID.randomUUID();state.createTrain(new dev.minetorio.core.rail.RailwayState.Train(trainId,cars,Optional.of(new dev.minetorio.core.rail.RailGeometry.Cursor(tape.get(10),1)),10,tape,.8,dev.minetorio.core.rail.RailwayState.Mode.MANUAL,dev.minetorio.core.rail.RailwayState.Status.MANUAL_CONTROL,0,0,0));RailwayManager.reconcileViews(level);
        var player=realPlayer(h);var firstPose=state.lastPoses().get(cars.getFirst()).position();player.moveTo(firstPose.x(),firstPose.y()+1,firstPose.z());
        RollingStockEntity view=null;for(var entity:level.getAllEntities())if(entity instanceof RollingStockEntity stock&&cars.getFirst().equals(stock.carId()))view=stock;
        h.assertTrue(view!=null&&view.interact(player,InteractionHand.MAIN_HAND).consumesAction(),"Реальный rider садится в трехвагонный состав");player.setPlayerInput(0,1,false,false);
        var firstChunk=player.chunkPosition();var actualView=view;boolean[] turned={false};long[] previousClock={state.railClock()};
        h.onEachTick(()->{
            var train=state.trains().get(trainId);if(train==null)return;
            var poses=dev.minetorio.core.rail.TrainMotion.carPoses(state,trainId);var head=poses.get(cars.getFirst());
            if(head.tangent().x()>.05&&head.tangent().z()<-.05)turned[0]=true;
            h.assertTrue(state.railClock()-previousClock[0]<=3,"Entity не добавляет четвертый physics step");previousClock[0]=state.railClock();
            for(int n=0;n<2;n++) {
                var a=dev.minetorio.core.rail.TrainMotion.bodyPoint(state,train,n,-3.5).position();var b=dev.minetorio.core.rail.TrainMotion.bodyPoint(state,train,n+1,3.5).position();
                h.assertTrue(Math.hypot(a.x()-b.x(),a.z()-b.z())<1e-8,"Интервал7 сохраняет общий coupler на повороте");
            }
            for(UUID id:cars) {
                var pose=poses.get(id);boolean present=false;for(var entity:level.getAllEntities())if(entity instanceof RollingStockEntity stock&&id.equals(stock.carId())){present=true;h.assertTrue(stock.position().distanceTo(new Vec3(pose.position().x(),pose.position().y()+.125,pose.position().z()))<1e-6,"Loaded view совпадает с owner pose и постоянной высотой модели0.125");}
                h.assertTrue(present,"Все три loaded stock views присутствуют");
            }
        });
        h.runAfterDelay(24,()->{
            try {
                var head=state.lastPoses().get(cars.getFirst());h.assertTrue(turned[0]&&head.tangent().x()>.99,"Поезд реально прошел curve и вышел на восточную прямую");
                h.assertTrue(!firstChunk.equals(player.chunkPosition())&&player.getVehicle()==actualView&&level.isPositionEntityTicking(player.blockPosition()),"Пассажир и entity-ticking ticket переехали в другой chunk через поворот");
                h.assertTrue(RailwayManager.passengerTicketCount(level)==1,"Старый ticket заменен одним текущим");
                player.stopRiding();RailwayManager.tick(level);h.assertTrue(RailwayManager.passengerTicketCount(level)==0,"Обычный выход на поворотном маршруте освобождает ticket");h.succeed();
            } finally {player.server.getPlayerList().remove(player);cleanupTrack(h,rails);}
        });
    }
    @GameTest(template="empty16")
    public static void opposingAutomaticTrainsActuallyCollideInSharedUnsignalledBlock(GameTestHelper h) {
        var line=remoteLine(h);var state=line.state();var first=state.trains().get(line.train());
        state.commitMotion(first,state.graph().revision(),new dev.minetorio.core.rail.RailwayState.MotionUpdate(first.head(),first.headIndex(),first.tape(),.2,first.status()));
        var other=state.createCar(UUID.randomUUID(),dev.minetorio.core.data.RailData.StockKind.LOCOMOTIVE,true);other.fuel().orElseThrow().insert(0,"minetorio:coal",5,false);
        var tape=first.tape();UUID second=UUID.randomUUID();state.createTrain(new dev.minetorio.core.rail.RailwayState.Train(second,List.of(other.id()),Optional.of(new dev.minetorio.core.rail.RailGeometry.Cursor(tape.get(19),.2)),19,tape,-.2,dev.minetorio.core.rail.RailwayState.Mode.AUTOMATIC,dev.minetorio.core.rail.RailwayState.Status.NO_SCHEDULE,0,0,0));
        var target=new dev.minetorio.core.rail.TrainSchedule.RailTarget(new dev.minetorio.core.rail.RailGeometry.Cursor(tape.get(1),1),false);
        var stop=new dev.minetorio.core.rail.TrainSchedule.Stop(Optional.empty(),Optional.of(target),false,List.of(new dev.minetorio.core.rail.TrainSchedule.Wait(dev.minetorio.core.rail.TrainSchedule.Type.TIME,dev.minetorio.core.rail.TrainSchedule.Join.AND,9999,Optional.empty())));
        state.dispatcher().setSchedule(second,new dev.minetorio.core.rail.TrainSchedule(List.of(stop),0));
        long clock=state.railClock();
        h.runAfterDelay(1,()->{
            try {
                h.assertTrue(state.railClock()==clock+3,"Actual manager выполнил три подшага встречных AUTO");
                h.assertTrue(state.cars().get(line.car()).hp()<1000||other.hp()<1000,"AUTO в одном исходном блоке действительно сталкиваются, без telepathic occupancy stop");
                h.assertTrue(state.cars().get(line.car()).fuel().orElseThrow().get(0).count()<5||other.fuel().orElseThrow().get(0).count()<5,"Разрешенная попытка движения имеет реальный fuel budget");h.succeed();
            } finally {line.cleanup();}
        });
    }
    @GameTest(template="empty16")
    public static void leadingFluidUsesE6AndDestructionHasNoMiningDrops(GameTestHelper h) {
        var line=remoteLine(h);var state=line.state();var tape=state.trains().get(line.train()).tape();state.removeCar(line.car(),true);
        try {
            var fluid=state.createCar(UUID.randomUUID(),dev.minetorio.core.data.RailData.StockKind.FLUID,false);var loco=state.createCar(UUID.randomUUID(),dev.minetorio.core.data.RailData.StockKind.LOCOMOTIVE,false);loco.fuel().orElseThrow().insert(0,"minetorio:coal",2,false);
            var train=state.createTrain(new dev.minetorio.core.rail.RailwayState.Train(line.train(),List.of(fluid.id(),loco.id()),Optional.of(new dev.minetorio.core.rail.RailGeometry.Cursor(tape.get(15),1)),15,tape,.9,dev.minetorio.core.rail.RailwayState.Mode.MANUAL,dev.minetorio.core.rail.RailwayState.Status.MANUAL_CONTROL,0,0,0));
            var target=state.createCar(UUID.randomUUID(),dev.minetorio.core.data.RailData.StockKind.CARGO,false);target.setHp(20);target.cargo().orElseThrow().insertManual(0,"minetorio:iron_plate",17,false);
            state.createTrain(new dev.minetorio.core.rail.RailwayState.Train(UUID.randomUUID(),List.of(target.id()),Optional.of(new dev.minetorio.core.rail.RailGeometry.Cursor(tape.get(19),.4)),19,tape,0,dev.minetorio.core.rail.RailwayState.Mode.MANUAL,dev.minetorio.core.rail.RailwayState.Status.MANUAL_CONTROL_STOP,0,0,0));
            int items=0;for(var entity:h.getLevel().getAllEntities())if(entity instanceof net.minecraft.world.entity.item.ItemEntity)items++;
            var proposal=dev.minetorio.core.rail.TrainMotion.propose(state,train.id(),new dev.minetorio.core.rail.TrainMotion.Control(1,false,dev.minetorio.core.rail.TrainMotion.Turn.STRAIGHT),0).orElseThrow();double expected=proposal.speed()-20*6.0/3000;
            RailWorldCollision.resolve(h.getLevel(),state,List.of(proposal));
            h.assertTrue(!state.cars().containsKey(target.id()),"Фактическое уничтожение loaded cargo owner");
            h.assertTrue(Math.abs(state.trains().get(train.id()).speed()-expected)<1e-12,"Ведущая цистерна использует E6 и HPbefore tax, не final damage");
            int after=0;for(var entity:h.getLevel().getAllEntities())if(entity instanceof net.minecraft.world.entity.item.ItemEntity)after++;
            h.assertTrue(after==items,"Destruction не выдает stock/cargo как mining");h.assertTrue(loco.remainingJ().compareTo(new java.math.BigDecimal("3990000"))==0,"Native budget списан один раз");h.succeed();
        } finally {line.cleanup();}
    }
}
