package dev.minetorio.gametest;

import com.mojang.authlib.GameProfile;
import dev.minetorio.content.fluid.*;
import dev.minetorio.content.rail.*;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.research.ResearchSavedData;
import dev.minetorio.core.data.RailData.StockKind;
import dev.minetorio.core.rail.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.RailwayState.*;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public final class RailwayLoadingGameTest {
    private static BlockPos relative(GameTestHelper h,BlockPos absolute) {
        BlockPos result=absolute.subtract(h.absolutePos(BlockPos.ZERO));
        h.assertTrue(h.absolutePos(result).equals(absolute),"Fixture использует нулевой поворот шаблона");
        return result;
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final List<Piece> rails;
        final RailwayState state;
        final UUID train;
        final RailwayState.Car car;
        UUID station;
        final List<UUID> signals=new ArrayList<>();
        boolean closed;
        Fixture(GameTestHelper h, StockKind kind, boolean automatic) {
            this(h,kind,automatic,-1,false);
        }
        Fixture(GameTestHelper h, StockKind kind, boolean automatic,int rotation,boolean mirrored) {
            this(h,kind,automatic,rotation,mirrored,false);
        }
        Fixture(GameTestHelper h, StockKind kind, boolean automatic,int rotation,boolean mirrored,boolean reversed) {
            this.h=h;
            state=RailwaySavedData.get(h.getLevel()).state();
            if(rotation<0)rails=RailwayWorldGameTest.loadedTrack(h);
            else {
                rails=new ArrayList<>();var origin=h.absolutePos(new BlockPos(24,2,24));
                int bx=Math.floorDiv(origin.getX(),2)*2,bz=Math.floorDiv(origin.getZ(),2)*2;
                for(int i=0;i<20;i++) {
                    int x=0,z=20-2*i;
                    for(int turn=0;turn<rotation;turn++){int oldX=x;x=-z;z=oldX;}
                    rails.add(new Piece(UUID.randomUUID(),new Point(bx+x,origin.getY(),bz+z),Shape.STRAIGHT,rotation,mirrored));
                }
                var all=new ArrayList<>(state.rails());all.addAll(rails);state.replaceRails(all);RailwayManager.reconcileViews(h.getLevel());
            }
            car=state.createCar(UUID.randomUUID(),kind,reversed);
            List<UUID> cars=new ArrayList<>(List.of(car.id()));
            if (automatic) {
                var loco=state.createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,false);
                loco.fuel().orElseThrow().insert(0,"minetorio:coal",5,false);
                if(rotation<0)cars.add(loco.id());else cars.addFirst(loco.id());
            }
            var tape=rails.stream().map(p->new Traversal(p.id(),true)).toList();
            train=UUID.randomUUID();
            state.createTrain(new Train(train,cars,Optional.of(new Cursor(tape.get(10),1)),10,tape,0,
                    automatic?Mode.AUTOMATIC:Mode.MANUAL,automatic?Status.NO_SCHEDULE:Status.MANUAL_CONTROL_STOP,0,0,0));
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo t) {}
                public void testPassed(GameTestInfo t,GameTestRunner r) {close();}
                public void testFailed(GameTestInfo t,GameTestRunner r) {close();}
                public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r) {}
            });
            if (automatic) attachStation();
        }
        private void attachStation() {
            var level=h.getLevel();var piece=rails.get(15);var p=sample(piece,1).position();
            var tangent=sample(piece,1).tangent();
            var at=BlockPos.containing(p.x()-2*tangent.z(),p.y(),p.z()+2*tangent.x());
            level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
            var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"loading-stop"));
            player.setGameMode(GameType.CREATIVE);player.moveTo(at.getX(),at.getY(),at.getZ()+1);
            var research=ResearchSavedData.get(level.getServer());var old=Set.copyOf(research.state().researched());
            String current=research.state().current().map(t->t.id()).orElse(null);var progress=Map.copyOf(research.state().progressById());
            try {
                research.restore(Set.of("automated_rail_transportation"),null,Map.of());
                station=RailPlacement.attachStop(player,new Cursor(new Traversal(piece.id(),true),1),"Loading-"+train).orElseThrow();
            } finally {research.restore(old,current,progress);}
            var stop=new TrainSchedule.Stop(Optional.of("Loading-"+train),Optional.empty(),false,
                    List.of(new TrainSchedule.Wait(TrainSchedule.Type.TIME,TrainSchedule.Join.AND,999999,Optional.empty())));
            state.dispatcher().setSchedule(train,new TrainSchedule(List.of(stop),0));
        }
        Pose pose(){return TrainMotion.carPoses(state,train).get(car.id());}
        String diagnostic() {
            var t=state.trains().get(train);var runtime=state.dispatcher().runtime(train);
            var assignment=runtime.assignment();
            int sign=assignment.map(TrainDispatcher.Assignment::sign).orElse(t.speed()<0?-1:1);
            String leading=t.head().isPresent()?TrainReservations.leading(state,train,sign).start().toString():"none";
            String target=assignment.filter(a->!a.visits().isEmpty()).map(a->{var v=a.visits().getLast();return v.point()+", remaining="+v.remaining();}).orElseGet(()->{
                var schedule=runtime.schedule();if(schedule.stops().isEmpty())return "none";
                var stop=schedule.stops().get(schedule.current());
                return stop.railTarget().map(tgt->tgt.point().toString()).orElseGet(()->state.stations().values().stream()
                        .filter(s->stop.station().equals(Optional.of(s.name()))).map(s->s.stop().toString()).findFirst().orElse("missing station"));
            });
            return "status="+t.status()+", head="+t.head()+", leading="+leading+", speed="+t.speed()+", target="+target
                    +", revisions="+state.graph().revision()+"/"+state.signals().revision()+"/"+t.routeRevision()+"/"+t.motionRevision();
        }
        void assertArrived(){h.assertTrue(state.trains().get(train).status()==Status.WAIT_STATION,"AUTO должен прибыть: "+diagnostic());}
        PumpBlockEntity pump() {
            return pump(1,0);
        }
        PumpBlockEntity pump(int side,int connector) {
            return pump(side,connector,TestPower.UNLIMITED_J);
        }
        PumpBlockEntity pump(int side,int connector,long powerJ) {
            Vec c=pose().position(),t=pose().tangent();var d=RailDockGeometry.dockPoint(pose(),side,connector);
            Direction facing=side*t.z()>.5?Direction.EAST:side*t.z()<-.5?Direction.WEST:-side*t.x()>.5?Direction.SOUTH:Direction.NORTH;
            var cell=BlockPos.containing(d.x()-.5-.5*facing.getStepX(),d.y()-.5,d.z()-.5-.5*facing.getStepZ());
            var controller=cell.relative(facing,-1);
            var relative=relative(h,controller);
            BeltGameTest.place(h,ModItems.PUMP.get(),relative,facing);
            if(powerJ>0)TestPower.limitedPowerAt(h,relative.above(2),powerJ);
            h.assertTrue(h.getLevel().getBlockEntity(controller) instanceof PumpBlockEntity,"Помпа стоит в controller="+controller+", pose="+c+", relative="+relative+", block="+h.getLevel().getBlockState(controller)+", above="+h.getLevel().getBlockState(controller.above()));
            return (PumpBlockEntity)h.getLevel().getBlockEntity(controller);
        }
        @Override public void close(){
            if(closed)return;closed=true;
            if(station!=null)RailwaySavedData.get(h.getLevel()).removeStop(station);
            signals.forEach(id->RailwaySavedData.get(h.getLevel()).removeSignal(id));
            RailwayWorldGameTest.cleanupTrack(h,rails);
            state.removeCar(car.id(),true);
        }
    }

    @GameTest(template="rail48",batch="railway_loading",timeoutTicks=300)
    public static void actualStationOriginalNozzleAlignment(GameTestHelper h) {
        var f=new Fixture(h,StockKind.FLUID,true);
        h.startSequence().thenWaitUntil(f::assertArrived).thenExecute(()->{
            var pump=f.pump();var port=FluidPorts.ports(h.getLevel(),pump.getBlockPos()).get(1);
            Vec c=f.pose().position();
            Vec dock=new Vec(c.x()+1,c.y()+.5,c.z());
            Vec nozzle=new Vec(port.cell().getX()+.5+.5*port.side().getStepX(),port.cell().getY()+.5,port.cell().getZ()+.5+.5*port.side().getStepZ());
            double distance=Math.sqrt(Math.pow(dock.x()-nozzle.x(),2)+Math.pow(dock.y()-nozzle.y(),2)+Math.pow(dock.z()-nozzle.z(),2));
            h.assertTrue(distance==.5,"Исходный face center сохраняет диагностическое расхождение0.5");
            h.assertTrue(RailDockGeometry.matches(f.pose(),1,0,new Point(port.cell().getX(),port.cell().getY(),port.cell().getZ()),RailDir.W),"Общий конечный nozzle adapter совмещается со штатным arrival");
        }).thenSucceed();
    }

    private static void cardinalNozzles(GameTestHelper h,int rotation,boolean mirrored) {
        var f=new Fixture(h,StockKind.FLUID,true,rotation,mirrored);
        h.startSequence().thenWaitUntil(f::assertArrived).thenExecute(()->{
            for(int side:new int[]{-1,1})for(int connector=-1;connector<=1;connector++) {
                var pump=f.pump(side,connector);var port=FluidPorts.ports(h.getLevel(),pump.getBlockPos()).get(1);
                RailDir dir=RailDir.valueOf(port.side().name().substring(0,1));
                var point=new Point(port.cell().getX(),port.cell().getY(),port.cell().getZ());
                h.assertTrue(RailDockGeometry.matches(f.pose(),side,connector,point,dir),"Actual station/pump для rotation="+rotation+", mirror="+mirrored+", side="+side+", connector="+connector);
                var pose=f.pose();var p=pose.position();
                for(double dy:new double[]{.0625,.0625+1e-7}) {
                    var moved=new Pose(new Vec(p.x(),p.y()+dy,p.z()),pose.tangent());
                    h.assertTrue(RailDockGeometry.matches(moved,side,connector,point,dir)==(dy<=.0625),"Общая nozzle geometry сохраняет вертикальную границу у реального port");
                }
            }
        }).thenSucceed();
    }
    @GameTest(template="rail48wide",batch="railway_loading_cardinal",timeoutTicks=300)
    public static void actualEastStationAllNozzles(GameTestHelper h){cardinalNozzles(h,1,false);}
    @GameTest(template="rail48wide",batch="railway_loading_cardinal",timeoutTicks=300)
    public static void actualSouthStationAllNozzles(GameTestHelper h){cardinalNozzles(h,2,false);}
    @GameTest(template="rail48wide",batch="railway_loading_cardinal",timeoutTicks=300)
    public static void actualWestMirroredStationAllNozzles(GameTestHelper h){cardinalNozzles(h,3,true);}
    @GameTest(template="rail48wide",batch="railway_loading_cardinal",timeoutTicks=300)
    public static void actualNorthMirroredStationAllNozzles(GameTestHelper h){cardinalNozzles(h,0,true);}

    @GameTest(template="rail48",batch="railway_loading",timeoutTicks=100)
    public static void actualInserterLoadsStationaryCargo(GameTestHelper h) {
        var f=new Fixture(h,StockKind.CARGO,false);var c=f.pose().position();
        var target=BlockPos.containing(c.x(),c.y(),c.z());
        var chest=relative(h,target.east(2));var arm=relative(h,target.east());
        InserterGameTest.container(h,Blocks.CHEST,chest,new ItemStack(ModItems.IRON_PLATE.get(),3));
        var inserter=InserterGameTest.placeInserter(h,ModItems.INSERTER.get(),arm,Direction.WEST);
        h.runAfterDelay(85,()->{
            h.assertTrue(f.car.cargo().orElseThrow().get(0).count()==3,
                    "Actual inserter cargo RED: cargo="+f.car.cargo().orElseThrow().get(0).count()+", chest="+InserterGameTest.count(h,chest,ModItems.IRON_PLATE.get())+", hand="+inserter.logic().held());
            h.succeed();
        });
    }

    @GameTest(template="rail48",batch="railway_loading",timeoutTicks=300)
    public static void actualFluidNetworkLoadsStationaryWagon(GameTestHelper h) {
        var f=new Fixture(h,StockKind.FLUID,true);PumpBlockEntity[] pump={null};
        h.startSequence().thenWaitUntil(f::assertArrived).thenExecute(()->{
            pump[0]=f.pump();pump[0].box().insert("water",600);
        }).thenIdle(20).thenExecute(()->{
            h.assertTrue(pump[0].satisfaction()>.99,"Настоящая сеть питает помпу");
            h.assertTrue(f.car.fluid().orElseThrow().amount()==600,
                    "Actual FluidNetworkManager wagon RED: wagon="+f.car.fluid().orElseThrow().amount()+", pump="+pump[0].box().amount()+", satisfaction="+pump[0].satisfaction());
        }).thenSucceed();
    }

    @GameTest(template="rail48",batch="railway_loading")
    public static void ownerSlotsPreserveComponentsBarsFuelAndSimulation(GameTestHelper h) {
        try(var f=new Fixture(h,StockKind.CARGO,false)) {
            var handler=RailCargoAccess.itemHandler(h.getLevel(),f.car.id());
            h.assertTrue(handler!=null&&handler.getSlots()==40,"Грузовой handler имеет40 owner slots");
            var stack=new ItemStack(ModItems.IRON_PLATE.get(),7);
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Груз"));
            stack.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,4);
            var slots=f.car.cargo().orElseThrow();slots.setFilter(0,Optional.of("minetorio:iron_plate"));slots.setBar(1);
            var before=slots.snapshot();var data=RailwaySavedData.get(h.getLevel());data.setDirty(false);
            h.assertTrue(handler.insertItem(0,stack,true).getCount()==3,"Simulate учитывает component limit4");
            h.assertTrue(before.equals(slots.snapshot())&&!data.isDirty(),"Simulate не меняет owner и dirty");
            h.assertTrue(handler.insertItem(0,stack,false).getCount()==3&&stack.getCount()==7,"Вставка не меняет входной ItemStack");
            h.assertTrue(handler.getSlotLimit(0)==4&&data.isDirty(),"Реальная вместимость и dirty после успеха");
            var external=handler.getStackInSlot(0);external.setCount(1);
            h.assertTrue(handler.getStackInSlot(0).getCount()==4,"Возвращенная стопка не владеет cargo");
            h.assertTrue(handler.insertItem(1,new ItemStack(ModItems.IRON_PLATE.get()),false).getCount()==1,"Bar запрещает автоматическую вставку");
            h.assertTrue(!handler.isItemValid(0,new ItemStack(ModItems.COPPER_PLATE.get())),"Фильтр сравнивает baseId");
            h.assertTrue(handler.extractItem(0,2,true).getCount()==2&&slots.get(0).count()==4,"Extract simulate чистый");
            slots.setBar(0);var extracted=handler.extractItem(0,2,false);
            h.assertTrue(extracted.getCount()==2&&ItemStack.isSameItemSameComponents(extracted,stack),"Выгрузка за bar сохраняет components");
            slots.setBar(1);
            h.assertTrue(handler.insertItem(0,new ItemStack(ModItems.IRON_PLATE.get()),false).getCount()==1,"Разные components не объединяются");
            slots.setBar(2);
            var pistol=new ItemStack(dev.minetorio.content.combat.CombatRegistry.PISTOL.get());
            pistol.set(dev.minetorio.content.combat.CombatRegistry.ROUNDS.get(),7);
            pistol.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,1);
            pistol.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Заряжен"));
            h.assertTrue(handler.insertItem(1,pistol,false).isEmpty(),"Заряженный pistol принимается целиком");
            h.assertTrue(ItemStack.isSameItemSameComponents(handler.extractItem(1,1,false),pistol),"Заряд7 и имя pistol сохранены");
            f.state.removeCar(f.car.id(),true);
            h.assertTrue(handler.getSlots()==0&&handler.extractItem(0,1,false).isEmpty(),"Старый handler не пишет в удаленный car");
            var replacement=f.state.createCar(f.car.id(),StockKind.CARGO,false);
            h.assertTrue(handler.getStackInSlot(0).isEmpty(),"После смены owner car handler не читает прежние RailSlots");
            handler.insertItem(0,new ItemStack(ModItems.COPPER_PLATE.get()),false);
            h.assertTrue(replacement.cargo().orElseThrow().get(0).count()==1&&slots.get(0).count()==2,"Handler разрешает текущий UUID заново без stale supplier");
        }
        var state=RailwaySavedData.get(h.getLevel()).state();
        var locomotive=state.createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,false);
        var fluid=state.createCar(UUID.randomUUID(),StockKind.FLUID,false);
        try {
            var fuel=RailCargoAccess.itemHandler(h.getLevel(),locomotive.id());
            h.assertTrue(fuel!=null&&fuel.getSlots()==3&&RailCargoAccess.itemHandler(h.getLevel(),fluid.id())==null,"Три топливных слота, у цистерны нет item handler");
            for(int slot=0;slot<3;slot++) {
                h.assertTrue(fuel.insertItem(slot,new ItemStack(dev.minetorio.content.registry.BaseMaterials.COAL.get(),50),false).isEmpty(),"Топливный slot принимает50, без Burner limit5");
                h.assertTrue(fuel.getStackInSlot(slot).getCount()==50,"Все три owner slots заполнены");
            }
            h.assertTrue(!fuel.isItemValid(0,new ItemStack(ModItems.IRON_PLATE.get())),"Нетопливный предмет отвергнут");
            h.succeed();
        } finally {state.removeCar(locomotive.id(),true);state.removeCar(fluid.id(),true);}
    }

    @GameTest(template="rail48",batch="railway_loading",timeoutTicks=150)
    public static void actualFilterAndStackInsertersRoundTripComponents(GameTestHelper h) {
        var f=new Fixture(h,StockKind.CARGO,false);var c=f.pose().position();var cell=BlockPos.containing(c.x(),c.y(),c.z());
        var source=relative(h,cell.east(2));var destination=relative(h,cell.west(3));
        var pistol=new ItemStack(dev.minetorio.content.combat.CombatRegistry.PISTOL.get());
        pistol.set(dev.minetorio.content.combat.CombatRegistry.ROUNDS.get(),7);
        pistol.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,1);
        pistol.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Поезд"));
        InserterGameTest.container(h,Blocks.CHEST,source,pistol,new ItemStack(ModItems.COPPER_PLATE.get()));
        h.setBlock(destination,Blocks.CHEST);
        var load=InserterGameTest.placeInserter(h,ModItems.FILTER_INSERTER.get(),relative(h,cell.east()),Direction.WEST);
        load.setFilter(0,"minetorio:pistol");
        var unload=InserterGameTest.placeInserter(h,ModItems.STACK_INSERTER.get(),relative(h,cell.west(2)),Direction.WEST);
        String[] violation={null};
        h.onEachTick(()->{
            if(f.closed)return;
            int total=InserterGameTest.count(h,source,pistol.getItem())+InserterGameTest.count(h,destination,pistol.getItem())
                    +f.car.cargo().orElseThrow().snapshot().stream().mapToInt(s->s.key().startsWith("minetorio:pistol")?s.count():0).sum()
                    +(load.logic().held()!=null&&load.logic().held().startsWith("minetorio:pistol")?load.logic().heldCount():0)
                    +(unload.logic().held()!=null&&unload.logic().held().startsWith("minetorio:pistol")?unload.logic().heldCount():0);
            if(total!=1&&violation[0]==null)violation[0]="pistol total="+total;
        });
        h.startSequence().thenWaitUntil(()->h.assertTrue(InserterGameTest.count(h,destination,pistol.getItem())==1,"Второй настоящий inserter выгрузил pistol"))
                .thenExecute(()->{
                    h.assertTrue(violation[0]==null,"Непрерывная сохранность: "+violation[0]);
                    h.assertTrue(ItemStack.isSameItemSameComponents(InserterGameTest.handler(h,destination).getStackInSlot(0),pistol),"Заряд и имя прошли chest/inserter/cargo/inserter/chest");
                    h.assertTrue(InserterGameTest.count(h,source,ModItems.COPPER_PLATE.get())==1,"Filter оставил медь в source");
                }).thenSucceed();
    }

    @GameTest(template="rail48",batch="railway_loading_fuel",timeoutTicks=140)
    public static void actualInserterFillsAllThreeLocomotiveFuelSlots(GameTestHelper h) {
        var f=new Fixture(h,StockKind.LOCOMOTIVE,false);var p=f.pose().position();var cell=BlockPos.containing(p.x(),p.y(),p.z());
        var fuel=f.car.fuel().orElseThrow();for(int i=0;i<3;i++)fuel.insert(i,"minetorio:coal",49,false);
        var source=relative(h,cell.east(2));var coal=dev.minetorio.content.registry.BaseMaterials.COAL.get();
        InserterGameTest.container(h,Blocks.CHEST,source,new ItemStack(coal,3));
        var arm=InserterGameTest.placeInserter(h,ModItems.INSERTER.get(),relative(h,cell.east()),Direction.WEST);
        String[] violation={null};h.onEachTick(()->{
            if(f.closed)return;int total=fuel.snapshot().stream().mapToInt(s->s.count()).sum()+InserterGameTest.count(h,source,coal)+arm.logic().heldCount();
            if(total!=150&&violation[0]==null)violation[0]="Fuel conservation="+total;
        });
        h.startSequence().thenWaitUntil(()->h.assertTrue(fuel.snapshot().stream().allMatch(s->s.count()==50),"Настоящий inserter заполнил каждый fuel slot до50"))
                .thenExecute(()->h.assertTrue(violation[0]==null,"Fuel не ограничен Burner5 и не потерян: "+violation[0])).thenSucceed();
    }

    @GameTest(template="rail48",batch="railway_loading",timeoutTicks=120)
    public static void departedTargetKeepsActualHeldItem(GameTestHelper h) {
        var f=new Fixture(h,StockKind.CARGO,false);var c=f.pose().position();var cell=BlockPos.containing(c.x(),c.y(),c.z());
        var source=relative(h,cell.east(2));
        InserterGameTest.container(h,Blocks.CHEST,source,new ItemStack(ModItems.IRON_PLATE.get()));
        var arm=InserterGameTest.placeInserter(h,ModItems.INSERTER.get(),relative(h,cell.east()),Direction.WEST);
        var captured=RailCargoAccess.target(h.getLevel(),cell,Direction.EAST);
        h.assertTrue(captured!=null,"Target действительно был доступен до pickup");
        h.startSequence().thenWaitUntil(()->h.assertTrue(arm.logic().heldCount()==1,"Реальный pickup из сундука"))
                .thenExecute(()->{
                    var train=f.state.trains().get(f.train);
                    f.state.commitMotion(train,f.state.graph().revision(),new MotionUpdate(train.head(),train.headIndex(),train.tape(),.4,Status.MANUAL_CONTROL));
                }).thenIdle(20).thenExecute(()->{
                    h.assertTrue(!f.pose().position().equals(c),"Owner действительно переместился серверными тиками");
                    h.assertTrue(!captured.wants("minetorio:iron_plate")&&!captured.accept("minetorio:iron_plate"),"Captured target отказывает после отправления");
                    h.assertTrue(arm.logic().heldCount()==1&&f.car.cargo().orElseThrow().get(0).count()==0&&InserterGameTest.count(h,source,ModItems.IRON_PLATE.get())==0,"Предмет остался в руке без возврата/потери");
                }).thenSucceed();
    }

    @GameTest(template="rail48",batch="railway_loading")
    public static void cargoContactsModesBlockPriorityAndStaleRemoval(GameTestHelper h) {
        try(var f=new Fixture(h,StockKind.CARGO,false)) {
            var c=f.pose().position();var cell=BlockPos.containing(c.x(),c.y(),c.z());
            var target=RailCargoAccess.target(h.getLevel(),cell,Direction.EAST);
            h.assertTrue(target!=null&&target.accept("minetorio:iron_plate"),"Stopped MANUAL загружается");
            h.assertTrue(RailCargoAccess.source(h.getLevel(),cell.west(),Direction.WEST).peek(key->true).isPresent(),"Второй борт читает тот же owner");
            h.assertTrue(RailCargoAccess.target(h.getLevel(),cell.above(2),Direction.DOWN)!=null&&RailCargoAccess.target(h.getLevel(),cell.above(3),Direction.DOWN)==null,"3D contact учитывает actual Y");
            var captured=RailCargoAccess.source(h.getLevel(),cell,Direction.EAST);
            f.state.dispatcher().setMode(f.train,Mode.AUTOMATIC);
            h.assertTrue(f.state.trains().get(f.train).status()==Status.NO_SCHEDULE,"AUTO действительно без расписания");
            h.assertTrue(RailCargoAccess.target(h.getLevel(),cell,Direction.EAST)==null&&!target.accept("minetorio:iron_plate")&&captured.peek(key->true).isEmpty()&&!captured.take("minetorio:iron_plate"),"AUTO NO_SCHEDULE не загружается через свежий или captured endpoint");
            f.state.dispatcher().setMode(f.train,Mode.MANUAL);
            var chest=relative(h,cell);h.setBlock(chest,Blocks.CHEST);
            var handler=InserterGameTest.handler(h,chest);
            for(int i=0;i<handler.getSlots();i++)handler.insertItem(i,new ItemStack(net.minecraft.world.item.Items.STONE,64),false);
            var blocked=dev.minetorio.content.inserter.InserterEndpoints.target(h.getLevel(),cell,Direction.EAST);
            h.assertTrue(!blocked.accept("minetorio:iron_plate")&&f.car.cargo().orElseThrow().get(0).count()==1,"Полный block capability не превращается в fallback wagon");
            f.state.removeCar(f.car.id(),true);
            h.assertTrue(!target.accept("minetorio:iron_plate")&&!captured.take("minetorio:iron_plate"),"Captured endpoints отвергают удаленного owner");
            h.succeed();
        }
    }

    @GameTest(template="rail48",batch="railway_loading_item_modes",timeoutTicks=40)
    public static void actualNoPathProducerRejectsFreshAndCapturedCargoEndpoints(GameTestHelper h) {
        var f=new Fixture(h,StockKind.CARGO,true);f.state.dispatcher().setMode(f.train,Mode.MANUAL);
        var pose=f.pose();var p=pose.position();var cell=BlockPos.containing(p.x(),p.y(),p.z());
        var target=RailCargoAccess.target(h.getLevel(),cell,Direction.EAST);var source=RailCargoAccess.source(h.getLevel(),cell,Direction.EAST);
        h.assertTrue(target!=null&&source!=null&&target.accept("minetorio:iron_plate"),"Оба endpoint захвачены при доступном stopped owner");
        var slots=f.car.cargo().orElseThrow().snapshot();
        f.state.dispatcher().setSchedule(f.train,new TrainSchedule(List.of(new TrainSchedule.Stop(Optional.of("missing-"+f.train),Optional.empty(),false,List.of())),0));
        f.state.dispatcher().setMode(f.train,Mode.AUTOMATIC);
        h.runAfterDelay(10,()->{
            h.assertTrue(f.state.trains().get(f.train).status()==Status.NO_PATH,"Настоящий dispatcher получил NO_PATH для отсутствующей станции");
            h.assertTrue(f.pose().equals(pose)&&f.rails.stream().allMatch(r->f.state.graph().piece(r.id()).isPresent()),"Pose и рельсы сохранны, отказ не маскируется потерей geometry");
            h.assertTrue(RailCargoAccess.source(h.getLevel(),cell,Direction.EAST)==null&&RailCargoAccess.target(h.getLevel(),cell,Direction.EAST)==null,"Свежие endpoints не допускают NO_PATH");
            h.assertTrue(source.peek(key->true).isEmpty()&&!source.take("minetorio:iron_plate")&&!target.wants("minetorio:iron_plate")&&!target.accept("minetorio:iron_plate"),"Захваченные endpoints повторно проверяют NO_PATH");
            h.assertTrue(slots.equals(f.car.cargo().orElseThrow().snapshot()),"NO_PATH не изменил canonical slots");h.succeed();
        });
    }

    @GameTest(template="rail48",batch="railway_loading_item_pose")
    public static void stoppedOwnerPoseChangesInvalidateCapturedEndpointsWithinSameTick(GameTestHelper h) {
        try(var f=new Fixture(h,StockKind.CARGO,false)) {
            long tick=h.getLevel().getGameTime();var p=f.pose().position();var oldCell=BlockPos.containing(p.x(),p.y(),p.z());
            var target=RailCargoAccess.target(h.getLevel(),oldCell,Direction.EAST);var source=RailCargoAccess.source(h.getLevel(),oldCell,Direction.EAST);
            h.assertTrue(target!=null&&source!=null&&target.accept("minetorio:iron_plate"),"Исходный owner доступен");
            var train=f.state.trains().get(f.train);
            h.assertTrue(f.state.commitMotion(train,f.state.graph().revision(),new MotionUpdate(Optional.of(new Cursor(train.tape().get(14),1)),14,train.tape(),0,train.status())),"Настоящий MANUAL owner переместился при speed0");
            h.assertTrue(h.getLevel().getGameTime()==tick&&f.state.trains().get(f.train).speed()==0,"Нет межтикового или speed guard обхода");
            h.assertTrue(source.peek(key->true).isEmpty()&&!source.take("minetorio:iron_plate")&&!target.wants("minetorio:iron_plate")&&!target.accept("minetorio:iron_plate"),"Старая захваченная pose запрещает все операции");
            h.assertTrue(RailCargoAccess.source(h.getLevel(),oldCell,Direction.EAST)==null&&RailCargoAccess.target(h.getLevel(),oldCell,Direction.EAST)==null,"Старое место больше не индексируется");
            var next=f.pose().position();var cell=BlockPos.containing(next.x(),next.y(),next.z());
            var fresh=RailCargoAccess.target(h.getLevel(),cell,Direction.EAST);var pickup=RailCargoAccess.source(h.getLevel(),cell,Direction.EAST);
            h.assertTrue(fresh!=null&&pickup!=null&&fresh.accept("minetorio:iron_plate")&&pickup.take("minetorio:iron_plate"),"Новые endpoints этого же tick используют новую pose");
            h.assertTrue(f.car.cargo().orElseThrow().get(0).count()==1,"Остался ровно исходный предмет");h.succeed();
        }
    }

    @GameTest(template="rail48wide",batch="railway_loading_item_pose")
    public static void reversedWorldCargoUsesAllDirectionsAndExactSpeedThreshold(GameTestHelper h) {
        try(var f=new Fixture(h,StockKind.CARGO,false,0,false,true)) {
            h.assertTrue(f.car.reversed()&&f.pose().tangent().z()>0,"Реальный reversed owner");
            var p=f.pose().position();var cell=BlockPos.containing(p.x(),p.y(),p.z());
            for(double speed:new double[]{0,1e-6,-1e-6,Math.nextUp(1e-6),-Math.nextUp(1e-6)}) {
                var train=f.state.trains().get(f.train);h.assertTrue(f.state.commitMotion(train,f.state.graph().revision(),new MotionUpdate(train.head(),train.headIndex(),train.tape(),speed,train.status())),"Установлена настоящая скорость owner");
                for(Direction from:Direction.values()) {
                    var target=RailCargoAccess.target(h.getLevel(),cell,from);var source=RailCargoAccess.source(h.getLevel(),cell,from);
                    boolean eligible=Math.abs(speed)<=1e-6;
                    h.assertTrue((target!=null)==eligible&&(source!=null)==eligible,"Reversed, from="+from+", speed="+speed);
                    if(eligible)h.assertTrue(target.accept("minetorio:iron_plate")&&source.take("minetorio:iron_plate"),"Каждая сторона меняет настоящий owner");
                }
            }
            h.assertTrue(f.car.cargo().orElseThrow().get(0).count()==0,"Матрица направлений сохраняет груз");h.succeed();
        }
    }

    @GameTest(template="rail48",batch="railway_loading_item_selection")
    public static void nearestContactThenTrainUuidSelectCanonicalCargo(GameTestHelper h) {
        try(var f=new Fixture(h,StockKind.CARGO,false)) {
            var p=f.pose().position();var cell=BlockPos.containing(p.x(),p.y(),p.z());
            var tape=f.state.trains().get(f.train).tape();f.state.removeCar(f.car.id(),true);
            UUID nearCar=new UUID(81,101),farCar=new UUID(81,102),nearTrain=new UUID(81,202),farTrain=new UUID(81,201);
            for(boolean tied:new boolean[]{false,true}) {
                for(UUID id:tied?List.of(nearCar,farCar):List.of(farCar,nearCar)) {
                    var car=f.state.createCar(id,StockKind.CARGO,false);double distance=id.equals(nearCar)||tied?.75:1;
                    f.state.createTrain(new Train(id.equals(nearCar)?nearTrain:farTrain,List.of(car.id()),Optional.of(new Cursor(tape.get(11),distance)),11,tape,0,Mode.MANUAL,Status.MANUAL_CONTROL_STOP,0,0,0));
                }
                var q=new Vec(cell.getX()+.5,cell.getY()+.5,cell.getZ()+.5);var from=new Vec(0,0,1);
                double a=RailDockGeometry.itemContact(TrainMotion.carPoses(f.state,nearTrain).get(nearCar),q,from).orElseThrow();
                double b=RailDockGeometry.itemContact(TrainMotion.carPoses(f.state,farTrain).get(farCar),q,from).orElseThrow();
                h.assertTrue(tied?a==b:a<b,"Оба кандидата допустимы; " +(tied?"точный tie":"первый геометрически ближе"));
                var endpoint=RailCargoAccess.target(h.getLevel(),cell,Direction.SOUTH);
                h.assertTrue(endpoint!=null&&endpoint.accept("minetorio:iron_plate"),"Выбран реальный canonical cargo");
                UUID selected=tied?farCar:nearCar;
                h.assertTrue(f.state.cars().get(selected).cargo().orElseThrow().get(0).count()==1&&f.state.cars().get(tied?nearCar:farCar).cargo().orElseThrow().get(0).count()==0,"Nearest имеет приоритет, при tie побеждает меньший train UUID, а не car UUID или порядок вставки");
                h.assertTrue(RailCargoAccess.source(h.getLevel(),cell,Direction.SOUTH).take("minetorio:iron_plate"),"Source выбирает тот же canonical owner");
                f.state.removeCar(nearCar,true);f.state.removeCar(farCar,true);
            }
            h.succeed();
        }
    }

    @GameTest(template="rail48wide",batch="railway_loading_item_selection")
    public static void sameTrainContactTieUsesCarUuidOnRepeatedRealLoop(GameTestHelper h) {
        var state=RailwaySavedData.get(h.getLevel()).state();var origin=h.absolutePos(new BlockPos(10,2,24));
        int bx=Math.floorDiv(origin.getX(),2)*2,bz=Math.floorDiv(origin.getZ(),2)*2;
        var quarter=List.of(new Piece(UUID.randomUUID(),new Point(0,0,0),Shape.CURVE,0,false),
                new Piece(UUID.randomUUID(),new Point(4,0,-8),Shape.DIAGONAL,0,false),
                new Piece(UUID.randomUUID(),new Point(12,0,-12),Shape.CURVE,3,true));
        int[][] translations={{0,0},{12,-12},{24,0},{12,12}};List<Piece> rails=new ArrayList<>();List<UUID> cars=new ArrayList<>();
        try {
            for(int turn=0;turn<4;turn++)for(var p:quarter) {
                var transformed=transform(p,turn,false);var at=transformed.origin();
                rails.add(new Piece(UUID.randomUUID(),new Point(bx+at.x()+translations[turn][0],origin.getY(),bz+at.z()+translations[turn][1]),transformed.shape(),transformed.quarterTurns(),transformed.mirrored()));
            }
            var all=new ArrayList<>(state.rails());all.addAll(rails);state.replaceRails(all);
            List<Traversal> tape=new ArrayList<>();var step=new Traversal(rails.getFirst().id(),true);
            for(int i=0;i<36;i++){tape.add(step);step=state.graph().next(step).getFirst();}
            for(int i=0;i<11;i++){UUID id=new UUID(82,i==10?1:100+i);cars.add(id);state.createCar(id,StockKind.CARGO,false);}
            UUID train=new UUID(82,300);state.createTrain(new Train(train,cars,Optional.of(new Cursor(tape.get(24),2)),24,tape,0,Mode.MANUAL,Status.MANUAL_CONTROL_STOP,0,0,0));
            h.assertTrue(state.trains().get(train).head().isPresent(),"Повторенный реальный loop принят owner без PATH_LOST/fake poses");
            var poses=TrainMotion.carPoses(state,train);var at=poses.get(cars.getFirst()).position();var cell=BlockPos.containing(at.x(),at.y(),at.z());
            var q=new Vec(cell.getX()+.5,cell.getY()+.5,cell.getZ()+.5);var up=new Vec(0,1,0);
            h.assertTrue(RailDockGeometry.itemContact(poses.get(cars.getFirst()),q,up).orElseThrow()==0&&RailDockGeometry.itemContact(poses.get(cars.getLast()),q,up).orElseThrow()==0,"Два вагона одного canonical train одновременно допустимы с точным tie0");
            var target=RailCargoAccess.target(h.getLevel(),cell,Direction.UP);
            h.assertTrue(target!=null&&target.accept("minetorio:iron_plate"),"Contact выбрал canonical slot");
            h.assertTrue(state.cars().get(cars.getLast()).cargo().orElseThrow().get(0).count()==1&&cars.subList(0,10).stream().allMatch(id->state.cars().get(id).cargo().orElseThrow().get(0).count()==0),"Меньший car UUID победил при равном train/contact, хотя стоит последним в consist");
            h.succeed();
        } finally {cars.forEach(id->state.removeCar(id,true));RailwayWorldGameTest.cleanupTrack(h,rails);}
    }

    @GameTest(template="rail48",batch="railway_loading_reload")
    public static void capturedEndpointsRejectReloadedOwnerAndNewAccessReadsSavedCargo(GameTestHelper h) throws Exception {
        var original=RailwaySavedData.get(h.getLevel());
        try(var f=new Fixture(h,StockKind.CARGO,false)) {
            var c=f.pose().position();var cell=BlockPos.containing(c.x(),c.y(),c.z());
            var captured=RailCargoAccess.target(h.getLevel(),cell,Direction.EAST);
            var source=RailCargoAccess.source(h.getLevel(),cell,Direction.EAST);
            var handler=RailCargoAccess.itemHandler(h.getLevel(),f.car.id());
            captured.accept("minetorio:iron_plate");
            var folder=java.nio.file.Files.createTempDirectory(h.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT),"rail-loading-");
            var file=folder.resolve(RailwaySavedData.NAME+".dat");
            try {
                var tag=new net.minecraft.nbt.CompoundTag();tag.put("data",original.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()));
                net.minecraft.nbt.NbtIo.writeCompressed(tag,file);
                var storage=new net.minecraft.world.level.storage.DimensionDataStorage(folder.toFile(),net.minecraft.util.datafix.DataFixers.getDataFixer(),h.getLevel().registryAccess());
                var loaded=RailwaySavedData.get(storage,folder,h.getLevel().registryAccess());
                h.getLevel().getDataStorage().set(RailwaySavedData.NAME,loaded);
                h.assertTrue(!captured.accept("minetorio:iron_plate")&&!source.take("minetorio:iron_plate"),"Старые физические endpoints отвергают смену SavedData owner");
                h.assertTrue(handler.getStackInSlot(0).getCount()==1,"Прямой handler читает текущий восстановленный owner");
                var fresh=RailCargoAccess.target(h.getLevel(),cell,Direction.EAST);
                h.assertTrue(fresh!=null&&fresh.accept("minetorio:iron_plate"),"Индекс обновлен на owner после disk load");
                h.assertTrue(loaded.state().cars().get(f.car.id()).cargo().orElseThrow().get(0).count()==2&&f.car.cargo().orElseThrow().get(0).count()==1,"Запись идет только в новый owner");
            } finally {
                h.getLevel().getDataStorage().set(RailwaySavedData.NAME,original);
                java.nio.file.Files.deleteIfExists(file);java.nio.file.Files.deleteIfExists(folder);
            }
            h.succeed();
        }
    }

    @GameTest(template="rail48",batch="railway_loading")
    public static void capturedEndpointsRejectSplitAndFreshIndexFindsCargo(GameTestHelper h) {
        try(var f=new Fixture(h,StockKind.CARGO,true)) {
            f.state.dispatcher().setMode(f.train,Mode.MANUAL);
            var c=f.pose().position();var cell=BlockPos.containing(c.x(),c.y(),c.z());
            var target=RailCargoAccess.target(h.getLevel(),cell,Direction.EAST);target.accept("minetorio:iron_plate");
            var source=RailCargoAccess.source(h.getLevel(),cell,Direction.EAST);
            h.assertTrue(TrainCoupling.split(f.state,f.car.id(),TrainCoupling.End.BACK).isPresent(),"Настоящий split изменяет consist revision без перемещения cargo");
            h.assertTrue(!target.accept("minetorio:iron_plate")&&!source.take("minetorio:iron_plate"),"Старые endpoints отвергают split");
            var fresh=RailCargoAccess.target(h.getLevel(),cell,Direction.EAST);
            h.assertTrue(fresh!=null&&fresh.accept("minetorio:iron_plate")&&f.car.cargo().orElseThrow().get(0).count()==2,"Новый endpoint на том же тике использует актуальный состав");
            h.succeed();
        }
    }

    @GameTest(template="rail48",batch="railway_loading",timeoutTicks=300)
    public static void temporaryRailTargetAllowsActualCargoLoading(GameTestHelper h) {
        var f=new Fixture(h,StockKind.CARGO,true);
        var stop=f.state.stations().get(f.station).stop();RailwaySavedData.get(h.getLevel()).removeStop(f.station);
        f.state.dispatcher().setSchedule(f.train,new TrainSchedule(List.of(new TrainSchedule.Stop(Optional.empty(),
                Optional.of(new TrainSchedule.RailTarget(stop,false)),true,
                List.of(new TrainSchedule.Wait(TrainSchedule.Type.TIME,TrainSchedule.Join.AND,999999,Optional.empty())))),0));
        h.startSequence().thenWaitUntil(f::assertArrived).thenExecute(()->{
            h.assertTrue(f.state.dispatcher().runtime(f.train).stoppedStation().isEmpty(),"Temporary WAIT_STATION не имеет physical station");
            var p=f.pose().position();var cell=BlockPos.containing(p.x(),p.y(),p.z());
            InserterGameTest.container(h,Blocks.CHEST,relative(h,cell.east(2)),new ItemStack(ModItems.IRON_PLATE.get()));
            InserterGameTest.placeInserter(h,ModItems.INSERTER.get(),relative(h,cell.east()),Direction.WEST);
        }).thenIdle(30).thenExecute(()->h.assertTrue(f.car.cargo().orElseThrow().get(0).count()==1,"Реальный inserter загрузил temporary wait"))
                .thenSucceed();
    }

    @GameTest(template="rail48",batch="railway_loading_signal",timeoutTicks=300)
    public static void actualSignalStoppedAutoRefusesInserterLoading(GameTestHelper h) {
        var f=new Fixture(h,StockKind.CARGO,true);var state=f.state;
        var blocker=state.createCar(UUID.randomUUID(),StockKind.CARGO,false);var tape=state.trains().get(f.train).tape();
        state.createTrain(new Train(UUID.randomUUID(),List.of(blocker.id()),Optional.of(new Cursor(tape.get(17),0)),17,tape,0,Mode.MANUAL,Status.MANUAL_CONTROL_STOP,0,0,0));
        var signal=new RailSignals.Signal(UUID.randomUUID(),new Port(f.rails.get(13).id(),End.A),true,RailSignals.Kind.REGULAR);
        RailwaySavedData.get(h.getLevel()).putSignal(new RailwaySavedData.WorldSignal(signal.id(),RailPlacement.signalPosition(state,signal),f.rails.get(13),End.A,true,RailSignals.Kind.REGULAR));
        f.signals.add(signal.id());
        RailwayManager.reconcileViews(h.getLevel());
        BlockPos[] source={null};
        h.startSequence().thenIdle(1).thenExecute(()->{
            h.assertTrue(state.signalDefinitions().contains(signal),"Сигнал остается в owner");
            h.assertTrue(!state.signals().blocks(tape.get(12)).equals(state.signals().blocks(tape.get(13))),"Сигнал разделил блоки");
            h.assertTrue(state.signals().aspect(signal.id(),state.reservations())==RailSignals.Aspect.RED,"Занятый блок реально дает RED, actual="+state.signals().aspect(signal.id(),state.reservations()));
        }).thenWaitUntil(()->h.assertTrue(state.trains().get(f.train).status()==Status.WAIT_SIGNAL,"Настоящий AUTO должен ждать занятый signal block: "+f.diagnostic()))
                .thenExecute(()->{
                    var p=f.pose().position();var cell=BlockPos.containing(p.x(),p.y(),p.z());source[0]=relative(h,cell.east(2));
                    InserterGameTest.container(h,Blocks.CHEST,source[0],new ItemStack(ModItems.IRON_PLATE.get()));
                    InserterGameTest.placeInserter(h,ModItems.INSERTER.get(),relative(h,cell.east()),Direction.WEST);
                }).thenIdle(30).thenExecute(()->{
                    h.assertTrue(state.trains().get(f.train).status()==Status.WAIT_SIGNAL,"Состав все еще реально ждет сигнал");
                    h.assertTrue(f.car.cargo().orElseThrow().get(0).count()==0&&InserterGameTest.count(h,source[0],ModItems.IRON_PLATE.get())==1,"Существующий inserter не грузит signal wait");
                }).thenSucceed();
    }

    @GameTest(template="rail48wide",batch="railway_loading")
    public static void diagonalContactsChooseRealBodyOfParallelOwnerWithoutViews(GameTestHelper h) {
        var state=RailwaySavedData.get(h.getLevel()).state();var at=h.absolutePos(new BlockPos(12,2,32));
        int bx=Math.floorDiv(at.getX(),2)*2,bz=Math.floorDiv(at.getZ(),2)*2;
        List<Piece> rails=new ArrayList<>();List<UUID> cars=new ArrayList<>();List<UUID> trains=new ArrayList<>();
        try {
            for(int lane=0;lane<2;lane++) {
                List<Traversal> tape=new ArrayList<>();
                for(int i=0;i<16;i++) {
                    int x=bx+2*lane+i,z=bz+2*lane+1-i;boolean forward=i%2==0;
                    var piece=new Piece(UUID.randomUUID(),new Point(forward?x:x+1,at.getY(),forward?z-1:z),Shape.DIAGONAL,forward?0:2,false);
                    rails.add(piece);tape.add(new Traversal(piece.id(),forward));
                }
                var all=new ArrayList<>(state.rails());all.addAll(rails.subList(16*lane,16*(lane+1)));state.replaceRails(all);
                var car=state.createCar(UUID.randomUUID(),StockKind.CARGO,false);cars.add(car.id());
                var train=state.createTrain(new Train(UUID.randomUUID(),List.of(car.id()),Optional.of(new Cursor(tape.get(8),Math.sqrt(2)/2)),8,tape,0,Mode.MANUAL,Status.MANUAL_CONTROL_STOP,0,0,0));trains.add(train.id());
            }
            var first=TrainMotion.carPoses(state,trains.getFirst()).get(cars.getFirst()).position();
            var second=TrainMotion.carPoses(state,trains.getLast()).get(cars.getLast()).position();
            var cell=BlockPos.containing(second.x(),second.y(),second.z());
            var target=RailCargoAccess.target(h.getLevel(),cell,Direction.WEST);
            h.assertTrue(target!=null&&target.accept("minetorio:iron_plate"),"Индекс находит соседний diagonal owner без проекции entity");
            h.assertTrue(state.cars().get(cars.getFirst()).cargo().orElseThrow().get(0).count()==0&&state.cars().get(cars.getLast()).cargo().orElseThrow().get(0).count()==1,"Пустой угол enclosing AABB не получает чужой cargo");
            var empty=BlockPos.containing(first.x()-1,first.y(),first.z()-1);
            h.assertTrue(RailCargoAccess.target(h.getLevel(),empty,Direction.EAST)==null,"Ложный угол диагонального AABB отвергнут");
            h.succeed();
        } finally {
            cars.forEach(id->state.removeCar(id,true));var ids=rails.stream().map(Piece::id).collect(java.util.stream.Collectors.toSet());
            state.replaceRails(state.rails().stream().filter(p->!ids.contains(p.id())).toList());RailwayManager.reconcileViews(h.getLevel());
        }
    }

    private static BlockPos sourceTank(GameTestHelper h,PumpBlockEntity pump,double amount,String fluid) {
        var pipe=pump.getBlockPos().east();var tank=pipe.east();
        BeltGameTest.place(h,ModItems.PIPE.get(),relative(h,pipe),Direction.NORTH);
        BeltGameTest.place(h,ModItems.STORAGE_TANK.get(),relative(h,tank),Direction.NORTH);
        h.assertTrue(FluidAccess.segmentAt(h.getLevel(),tank).insert(fluid,amount)==amount,"Реальный tank/pipe source заполнен");
        return tank;
    }
    private static void moveHead(Fixture f,double distance) {
        var train=f.state.trains().get(f.train);
        f.h.assertTrue(f.state.commitMotion(train,f.state.graph().revision(),new MotionUpdate(Optional.of(new Cursor(train.head().orElseThrow().traversal(),distance)),train.headIndex(),train.tape(),0,train.status())),"Изменена настоящая координата owner на текущей rail occurrence");
    }
    @GameTest(template="rail48wide",batch="railway_loading_temporary_fluid",timeoutTicks=300)
    public static void actualTemporaryPumpWorksWithoutStationAndNoScheduleRefuses(GameTestHelper h) {
        var f=new Fixture(h,StockKind.FLUID,true);var stop=f.state.stations().get(f.station).stop();
        RailwaySavedData.get(h.getLevel()).removeStop(f.station);
        f.state.dispatcher().setSchedule(f.train,new TrainSchedule(List.of(new TrainSchedule.Stop(Optional.empty(),Optional.of(new TrainSchedule.RailTarget(stop,false)),true,
                List.of(new TrainSchedule.Wait(TrainSchedule.Type.TIME,TrainSchedule.Join.AND,999999,Optional.empty())))),0));
        PumpBlockEntity[] pump={null};long[] activity={0};
        h.startSequence().thenWaitUntil(f::assertArrived).thenExecute(()->{
            h.assertTrue(f.state.dispatcher().runtime(f.train).stoppedStation().isEmpty(),"Temporary owner не имеет станции");
            pump[0]=f.pump();activity[0]=f.state.dispatcher().runtime(f.train).activityTick().orElseThrow();
        }).thenIdle(10).thenExecute(()->{
            h.assertTrue(f.state.dispatcher().runtime(f.train).activityTick().orElseThrow()==activity[0],"Пустая помпа и selection не сбрасывают inactivity");
            RailPumpDock.selection(h.getLevel());pump[0].box().insert("water",600);
        }).thenIdle(30).thenExecute(()->{
            h.assertTrue(f.car.fluid().orElseThrow().amount()==600,"Fresh temporary pump реально передала600 без станции");
            h.assertTrue(f.state.dispatcher().runtime(f.train).activityTick().orElseThrow()>activity[0],"Только actual transfer сбросил inactivity");
            f.state.dispatcher().setSchedule(f.train,new TrainSchedule(List.of(),0));pump[0].box().insert("water",600);
        }).thenIdle(30).thenExecute(()->{
            h.assertTrue(f.state.trains().get(f.train).status()==Status.NO_SCHEDULE,"Настоящий AUTO без расписания");
            h.assertTrue(f.car.fluid().orElseThrow().amount()==600&&pump[0].box().amount()==600,"AUTO NO_SCHEDULE не передает при той же позе");
            h.assertTrue(RailPumpDock.selection(h.getLevel()).stream().noneMatch(d->d.car().equals(f.car.id())),"NO_SCHEDULE отсутствует и в preview");
        }).thenSucceed();
    }
    @GameTest(template="rail48wide",batch="railway_loading_boundary",timeoutTicks=160)
    public static void actualPumpBoundaryMatchesPreviewAndWrongWorldHeight(GameTestHelper h) {
        var f=new Fixture(h,StockKind.FLUID,false);var pump=f.pump();double[] before={0};
        var sequence=h.startSequence().thenIdle(5);
        for(double offset:new double[]{0,.0625,.0625+1e-7,.25}) {
            sequence.thenExecute(()->{
                moveHead(f,1+offset);before[0]=f.car.fluid().orElseThrow().amount();pump.box().extract(pump.box().amount());pump.box().insert("water",600);
                boolean selected=RailPumpDock.selection(h.getLevel()).stream().anyMatch(d->d.car().equals(f.car.id()));
                h.assertTrue(selected==(offset<=.0625),"Preview на реальном owner offset="+offset);
            }).thenIdle(30).thenExecute(()->h.assertTrue(f.car.fluid().orElseThrow().amount()-before[0]==(offset<=.0625?600:0),"Actual transfer на реальном owner offset="+offset));
        }
        sequence.thenExecute(()->{
            moveHead(f,1);var at=pump.getBlockPos();h.getLevel().setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
            BeltGameTest.place(h,ModItems.PUMP.get(),relative(h,at.above()),Direction.WEST);
            var high=(PumpBlockEntity)h.getLevel().getBlockEntity(at.above());high.box().insert("water",600);before[0]=f.car.fluid().orElseThrow().amount();
            TestPower.powerAt(h,relative(h,at.above(3)));
        }).thenIdle(20).thenExecute(()->{
            h.assertTrue(RailPumpDock.selection(h.getLevel()).stream().noneMatch(d->d.car().equals(f.car.id())),"Реальный port Y+1 не выбран");
            h.assertTrue(f.car.fluid().orElseThrow().amount()==before[0],"Реальный port Y+1 не передает");
        }).thenSucceed();
    }
    @GameTest(template="rail48wide",batch="railway_loading_curve",timeoutTicks=100)
    public static void partialCurveKeepsStraightConnectorAndRejectsCardinalCurveLeadIn(GameTestHelper h) {
        var f=new Fixture(h,StockKind.FLUID,false);var old=f.state.trains().get(f.train);
        var curve=new Piece(UUID.randomUUID(),f.rails.get(11).origin(),Shape.CURVE,0,false);
        var removed=f.rails.subList(11,f.rails.size()).stream().map(Piece::id).collect(java.util.stream.Collectors.toSet());
        var all=new ArrayList<>(f.state.rails().stream().filter(p->!removed.contains(p.id())).toList());all.add(curve);f.state.replaceRails(all);
        f.rails.add(curve);var tape=new ArrayList<>(old.tape().subList(0,11));tape.add(new Traversal(curve.id(),true));
        var current=f.state.trains().get(f.train);
        h.assertTrue(f.state.commitMotion(current,f.state.graph().revision(),new MotionUpdate(Optional.of(new Cursor(tape.get(10),1)),10,tape,0,Status.MANUAL_CONTROL_STOP)),"Owner частично на curve");
        RailwayManager.reconcileViews(h.getLevel());var pump=f.pump();pump.box().insert("water",600);
        h.assertTrue(RailDockGeometry.connectorFrame(f.state,f.train,f.car.id(),1).isEmpty(),"Передний connector находится на curve");
        h.assertTrue(RailDockGeometry.connectorFrame(f.state,f.train,f.car.id(),0).isPresent(),"Средний connector остался straight");
        h.startSequence().thenIdle(30).thenExecute(()->{
            h.assertTrue(f.car.fluid().orElseThrow().amount()==600,"Частичная curve не запретила реальный straight connector");
            var t=f.state.trains().get(f.train);
            h.assertTrue(f.state.commitMotion(t,f.state.graph().revision(),new MotionUpdate(Optional.of(new Cursor(tape.get(11),.05)),11,tape,0,Status.MANUAL_CONTROL_STOP)),"Owner в cardinal lead-in curve");
            h.assertTrue(f.pose().tangent().x()==0&&RailDockGeometry.connectorFrame(f.state,f.train,f.car.id(),0).isEmpty(),"Cardinal tangent внутри CURVE не допускается");
            pump.box().insert("water",600);
        }).thenIdle(30).thenExecute(()->h.assertTrue(f.car.fluid().orElseThrow().amount()==600,"Curve connector не переносит жидкость")).thenSucceed();
    }
    @GameTest(template="rail48wide",batch="railway_loading_fluid",timeoutTicks=240)
    public static void actualPumpTankWagonAndBackConserve25000EveryTick(GameTestHelper h) {
        var f=new Fixture(h,StockKind.FLUID,false);PumpBlockEntity[] pump={f.pump()};var tank=sourceTank(h,pump[0],25000,"water");
        String[] violation={null};double[] previous={0};
        h.onEachTick(()->{
            if(f.closed)return;
            double wagon=f.car.fluid().orElseThrow().amount();double total=wagon+pump[0].box().amount()+FluidAccess.segmentAt(h.getLevel(),tank).amount();
            if(Math.abs(total-25000)>1e-6&&violation[0]==null)violation[0]="Сумма car+pump+pipe+tank="+total;
            if(Math.abs(wagon-previous[0])>600.000001&&violation[0]==null)violation[0]="Больше600 за Minecraft tick: "+(wagon-previous[0]);
            previous[0]=wagon;
        });
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.car.fluid().orElseThrow().amount()==25000,"Цистерна действительно заполнилась25000"))
                .thenExecute(()->{
                    h.assertTrue(violation[0]==null,"Непрерывная сохранность загрузки: "+violation[0]);
                    var old=pump[0];h.assertTrue(old.box().amount()==0,"Перед сменой направления buffer пуст");
                    var origin=old.getBlockPos().west();h.getLevel().setBlockAndUpdate(old.getBlockPos(),Blocks.AIR.defaultBlockState());
                    BeltGameTest.place(h,ModItems.PUMP.get(),relative(h,origin),Direction.EAST);
                    pump[0]=(PumpBlockEntity)h.getLevel().getBlockEntity(origin);
                }).thenWaitUntil(()->h.assertTrue(f.car.fluid().orElseThrow().amount()==0&&pump[0].box().amount()==0,"Настоящая обратная помпа опустошила вагон иbuffer"))
                .thenExecute(()->{
                    h.assertTrue(violation[0]==null,"Непрерывная сохранность обоих направлений: "+violation[0]);
                    h.assertTrue(Math.abs(FluidAccess.segmentAt(h.getLevel(),tank).amount()-25000)<1e-6,"Все25000 вернулись в исходный tank/pipe segment");
                }).thenSucceed();
    }

    @GameTest(template="rail48wide",batch="railway_loading_fluid",timeoutTicks=80)
    public static void actualHalfPowerLimitsEveryTickAndRepeatedTransfer(GameTestHelper h) {
        var f=new Fixture(h,StockKind.FLUID,false);var pump=f.pump(1,0,dev.minetorio.core.fluid.FluidData.PUMP_POWER_J/2);
        var tank=sourceTank(h,pump,10000,"water");double[] previous={0},maximum={0};String[] violation={null};
        h.onEachTick(()->{
            if(f.closed)return;double wagon=f.car.fluid().orElseThrow().amount(),delta=wagon-previous[0];previous[0]=wagon;maximum[0]=Math.max(maximum[0],delta);
            if(delta>300.000001&&violation[0]==null)violation[0]="Half-power transfer="+delta;
            double total=wagon+pump.box().amount()+FluidAccess.segmentAt(h.getLevel(),tank).amount();
            if(Math.abs(total-10000)>1e-6&&violation[0]==null)violation[0]="Half-power total="+total;
        });
        h.runAfterDelay(30,()->{
            h.assertTrue(Math.abs(pump.satisfaction()-.5)<1e-9&&maximum[0]>299.999,"Реальная power grid выдала0.5 и observed transfer300");
            h.assertTrue(violation[0]==null,"Все ticks соблюдали лимит: "+violation[0]);
            double first=RailPumpDock.transfer(h.getLevel(),pump),second=RailPumpDock.transfer(h.getLevel(),pump);
            h.assertTrue(first+second<=300&&second==0,"Повторный вызов того же tick не получает второй бюджет");h.succeed();
        });
    }

    @GameTest(template="rail48wide",batch="railway_loading_fluid",timeoutTicks=80)
    public static void actualNoPowerNoMixAndInvalidFluidNeverExtract(GameTestHelper h) {
        var f=new Fixture(h,StockKind.FLUID,false);var pump=f.pump(1,0,0);pump.box().insert("water",600);
        h.runAfterDelay(10,()->{
            h.assertTrue(pump.satisfaction()==0&&f.car.fluid().orElseThrow().amount()==0&&pump.box().amount()==600,"Без power перенос0");
            h.assertTrue(RailPumpDock.selection(h.getLevel()).stream().noneMatch(d->d.car().equals(f.car.id())),"Power0 не занимает quota");
            pump.box().extract(600);pump.box().insert("crude_oil",600);f.car.insertFluid("water",100);
            TestPower.powerAt(h,relative(h,pump.getBlockPos().above(2)));
        });
        h.runAfterDelay(20,()->{
            h.assertTrue(pump.satisfaction()>.99&&pump.box().amount()==600&&f.car.fluid().orElseThrow().amount()==100,"Несовместимые реальные жидкости не смешаны и не списаны");
            pump.box().extract(600);pump.box().insert("",600);
        });
        h.runAfterDelay(30,()->{
            h.assertTrue(pump.box().fluid().isEmpty()&&pump.box().amount()==600&&f.car.fluid().orElseThrow().amount()==100,"Пустой source id отвергнут до extract");
            pump.box().extract(600);pump.box().insert("unknown",600);
        });
        h.runAfterDelay(40,()->{
            h.assertTrue(pump.box().amount()==600&&f.car.fluid().orElseThrow().amount()==100,"Неизвестный source id отвергнут до extract");
            for(double invalid:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
                h.assertTrue(RailPumpDock.transferAmount("water",invalid,null,0,25000,600)==0,"Invalid source amount");
                h.assertTrue(RailPumpDock.transferAmount("water",600,null,0,25000,invalid)==0,"Invalid rate");
                h.assertTrue(RailPumpDock.transferAmount("water",600,"water",invalid,25000,600)==0,"Invalid target amount");
            }
            h.succeed();
        });
    }

    @GameTest(template="rail48wide",batch="railway_loading_fluid_reload",timeoutTicks=100)
    public static void pumpLifecycleReloadKeepsFluidAndRejectsStaleSupplier(GameTestHelper h) {
        var f=new Fixture(h,StockKind.FLUID,false);f.car.insertFluid("water",25000);var original=f.pump();original.box().insert("water",600);
        PumpBlockEntity[] restored={null};
        h.startSequence().thenIdle(10).thenExecute(()->{
            h.assertTrue(original.satisfaction()>.99&&original.box().amount()==600,"Полная цистерна не потребляет source");
            var registries=h.getLevel().registryAccess();var tag=original.saveWithoutMetadata(registries);
            var restoredOwner=RailwayNbt.read(RailwayNbt.write(f.state,registries),registries);
            h.assertTrue(restoredOwner.cars().get(f.car.id()).fluid().orElseThrow().amount()==25000,"NBT сохраняет полную цистерну");
            original.onChunkUnloaded();
            h.assertTrue(RailPumpDock.transfer(h.getLevel(),original)==0&&!FluidNetworkManager.get(h.getLevel()).loadedPumps().contains(original),"Lifecycle unload удаляет supplier до следующего transfer");
            var pos=original.getBlockPos();h.getLevel().removeBlockEntity(pos);
            restored[0]=new PumpBlockEntity(pos,h.getLevel().getBlockState(pos));restored[0].loadWithComponents(tag,registries);
            h.getLevel().setBlockEntity(restored[0]);restored[0].onLoad();
            h.assertTrue(restored[0].box().amount()+f.car.fluid().orElseThrow().amount()==25600,"NBT reload не теряет car+pump volume");
            h.assertTrue(RailPumpDock.transfer(h.getLevel(),original)==0,"Старый BE не получил новое соединение после замены");
            f.car.extractFluid(600);
        }).thenIdle(30).thenExecute(()->{
            h.assertTrue(restored[0].satisfaction()>.99&&restored[0].box().amount()==0&&f.car.fluid().orElseThrow().amount()==25000,"Новый BE повторно подключился, сохраненные600 перешли в owner");
            h.assertTrue(RailPumpDock.transfer(h.getLevel(),original)==0,"Захваченный старый supplier остается недействительным");
        }).thenSucceed();
    }

    @GameTest(template="rail48wide",batch="railway_loading_dual_port",timeoutTicks=100)
    public static void twoWagonsAtOppositePumpPortsShareOneBudget(GameTestHelper h) {
        var f=new Fixture(h,StockKind.FLUID,false);var shifted=f.rails.stream().map(p->new Piece(UUID.randomUUID(),new Point(p.origin().x()+4,p.origin().y(),p.origin().z()),p.shape(),p.quarterTurns(),p.mirrored())).toList();
        var all=new ArrayList<>(f.state.rails());all.addAll(shifted);f.state.replaceRails(all);f.rails.addAll(shifted);
        var source=f.state.createCar(UUID.randomUUID(),StockKind.FLUID,true);source.insertFluid("water",25000);
        var tape=shifted.stream().map(p->new Traversal(p.id(),true)).toList();
        f.state.createTrain(new Train(UUID.randomUUID(),List.of(source.id()),Optional.of(new Cursor(tape.get(10),1)),10,tape,0,Mode.MANUAL,Status.MANUAL_CONTROL_STOP,0,0,0));
        RailwayManager.reconcileViews(h.getLevel());var pump=f.pump();String[] violation={null};double[] previous={0,25000};
        h.onEachTick(()->{
            if(f.closed)return;double targetAmount=f.car.fluid().orElseThrow().amount(),sourceAmount=source.fluid().orElseThrow().amount();
            if(Math.abs(targetAmount+sourceAmount+pump.box().amount()-25000)>1e-6&&violation[0]==null)violation[0]="Dual port conservation";
            double moved=Math.abs(targetAmount-previous[0])+Math.abs(sourceAmount-previous[1]);
            if(moved>600.000001&&violation[0]==null)violation[0]="Два порта израсходовали "+moved+" за tick";
            previous[0]=targetAmount;previous[1]=sourceAmount;
        });
        h.runAfterDelay(65,()->{
            var selection=RailPumpDock.selection(h.getLevel()).stream().filter(d->d.controller().equals(pump.getBlockPos())).toList();
            h.assertTrue(selection.size()==2&&selection.stream().map(RailPumpDock.Dock::car).distinct().count()==2,"Одна помпа физически подключена обоими портами к разным вагонам");
            h.assertTrue(f.car.fluid().orElseThrow().amount()>0&&source.fluid().orElseThrow().amount()<25000,"Оба направления реально передавали жидкость");
            h.assertTrue(violation[0]==null,"Единый budget и непрерывная сохранность: "+violation[0]);h.succeed();
        });
    }

    @GameTest(template="rail48wide",batch="railway_loading_quota",timeoutTicks=120)
    public static void poweredStickyQuotaThreeAcrossBothSidesAndDuplicateConnector(GameTestHelper h) {
        var f=new Fixture(h,StockKind.FLUID,false,0,false);List<PumpBlockEntity> pumps=new ArrayList<>();List<BlockPos> rightPower=new ArrayList<>();
        PumpBlockEntity center=null;
        for(int side:new int[]{-1,1})for(int connector=-1;connector<=1;connector++) {
            var pump=f.pump(side,connector);pumps.add(pump);pump.box().insert("water",600);
            if(side==1){rightPower.add(pump.getBlockPos().above(2));if(connector==0)center=pump;}
        }
        var duplicateAt=center.getBlockPos().south();
        BeltGameTest.place(h,ModItems.PUMP.get(),relative(h,duplicateAt),Direction.WEST);
        var duplicate=(PumpBlockEntity)h.getLevel().getBlockEntity(duplicateAt);duplicate.box().insert("water",600);pumps.add(duplicate);
        TestPower.powerAt(h,relative(h,duplicateAt.above(2)));rightPower.add(duplicateAt.above(2));
        String[] violation={null};List<Set<BlockPos>> poweredAfter=new ArrayList<>();
        h.onEachTick(()->{
            if(f.closed)return;
            double total=f.car.fluid().orElseThrow().amount()+pumps.stream().mapToDouble(p->p.box().amount()).sum();
            if(Math.abs(total-4200)>1e-6&&violation[0]==null)violation[0]="Quota total="+total;
            var selected=RailPumpDock.selection(h.getLevel()).stream().filter(d->d.car().equals(f.car.id())).toList();
            if(selected.size()>3&&violation[0]==null)violation[0]="Больше трех подключений";
            if(selected.stream().map(d->d.side()+":"+d.connector()).distinct().count()!=selected.size()&&violation[0]==null)violation[0]="Duplicate connector получил второй канал";
        });
        h.runAfterDelay(15,()->{
            var selected=RailPumpDock.selection(h.getLevel()).stream().filter(d->d.car().equals(f.car.id())).toList();
            h.assertTrue(selected.size()==3&&f.car.fluid().orElseThrow().amount()==1800,"Три из семи геометрически допустимых помп передали ровно3x600");
            h.assertTrue(selected.stream().anyMatch(d->d.side()==1)&&selected.stream().anyMatch(d->d.side()==-1),"Cap3 действительно общий для обоих бортов");
            var data=RailwaySavedData.get(h.getLevel());data.setDirty(false);var before=f.car.fluid();
            h.assertTrue(selected.equals(RailPumpDock.selection(h.getLevel()).stream().filter(d->d.car().equals(f.car.id())).toList())&&!data.isDirty()&&before.equals(f.car.fluid()),"Preview не меняет claims, owner илиdirty");
            rightPower.forEach(pos->h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState()));
        });
        h.runAfterDelay(30,()->{
            h.assertTrue(pumps.stream().filter(p->rightPower.contains(p.getBlockPos().above(2))).allMatch(p->p.satisfaction()==0),"Фактическая сеть перестала питать правый борт");
            var selected=RailPumpDock.selection(h.getLevel()).stream().filter(d->d.car().equals(f.car.id())).toList();
            h.assertTrue(selected.size()==3&&selected.stream().allMatch(d->d.side()==-1),"Power0 освободил quota для остальных подходящих помп");
            poweredAfter.add(selected.stream().map(RailPumpDock.Dock::controller).collect(java.util.stream.Collectors.toSet()));
            TestPower.powerAt(h,relative(h,rightPower.getFirst()));
        });
        h.runAfterDelay(45,()->{
            var selected=RailPumpDock.selection(h.getLevel()).stream().filter(d->d.car().equals(f.car.id())).map(RailPumpDock.Dock::controller).collect(java.util.stream.Collectors.toSet());
            h.assertTrue(selected.equals(poweredAfter.getFirst()),"Восстановление питания не вытесняет три сохраненных валидных подключения");
            h.assertTrue(violation[0]==null,"Непрерывная сохранность quota: "+violation[0]);h.succeed();
        });
    }
}
