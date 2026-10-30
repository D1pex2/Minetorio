package dev.minetorio.content.demo;

import dev.minetorio.content.combat.CombatRegistry;
import dev.minetorio.content.fluid.*;
import dev.minetorio.content.multiblock.MultiblockBlockItem;
import dev.minetorio.content.rail.*;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.world.BasicChests;
import dev.minetorio.core.data.RailData.StockKind;
import dev.minetorio.core.rail.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.RailwayState.*;

/** Настоящий челнок в свободном юго-восточном углу прежней площадки 96x96. */
public final class RailDemoBuilder {
    public record Layout(BlockPos anchor, BlockPos output, BlockPos tank, BlockPos weapons) {}
    private final ServerLevel level;
    private final BlockPos base;
    private final List<Consumer<DemoSavedData>> pending;
    private RailDemoBuilder(ServerLevel level, BlockPos origin, List<Consumer<DemoSavedData>> pending) {
        this.level=level; this.pending=pending; base=layout(origin).anchor();
    }
    public static Layout layout(BlockPos origin) {
        BlockPos p=new BlockPos(Math.floorDiv(origin.getX()+44,2)*2,origin.getY()+1,Math.floorDiv(origin.getZ()+80,2)*2);
        return new Layout(p,p.offset(36,0,3),p.offset(29,0,7),origin.offset(42,1,65));
    }
    public static void build(ServerLevel level,BlockPos origin,List<Consumer<DemoSavedData>> pending) {
        new RailDemoBuilder(level,origin,pending).build(origin);
    }
    /** В заявленной области команда уже удаляет постройки и транспорт. Убираем и их canonical owners. */
    public static void clear(ServerLevel level,BoundingBox area) {
        var data=RailwaySavedData.get(level); var state=data.state();
        var cars=new ArrayList<UUID>();
        for(var train:state.trains().values()) TrainMotion.carPoses(state,train.id()).forEach((id,pose)->{
            var v=pose.position();if(area.isInside(BlockPos.containing(v.x(),v.y(),v.z())))cars.add(id);
        });
        cars.forEach(id->state.removeCar(id,true));
        data.worldStops().values().stream().filter(s->area.isInside(s.position())).map(RailwaySavedData.WorldStop::id).toList().forEach(data::removeStop);
        data.worldSignals().values().stream().filter(s->area.isInside(s.position())).map(RailwaySavedData.WorldSignal::id).toList().forEach(data::removeSignal);
        state.replaceRails(state.rails().stream().filter(p->cells(p).stream().noneMatch(c->area.isInside(RailPlacement.block(c)))).toList());
        data.rebindStops(); RailwayManager.reconcileViews(level);
    }
    private BlockPos p(int x,int z){return base.offset(x,0,z);}
    private void build(BlockPos origin) {
        var data=RailwaySavedData.get(level);var state=data.state();var rails=new ArrayList<Piece>();
        for(int i=0;i<24;i++)rails.add(new Piece(UUID.randomUUID(),new Point(base.getX()+2*i,base.getY(),base.getZ()),Shape.STRAIGHT,1,false));
        var all=new ArrayList<>(state.rails());all.addAll(rails);state.replaceRails(all);
        var front=state.createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,false);
        var cargo=state.createCar(UUID.randomUUID(),StockKind.CARGO,false);
        var fluid=state.createCar(UUID.randomUUID(),StockKind.FLUID,false);
        var rear=state.createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,true);
        // Только начальный запас топлива. Груз и вода попадают в вагоны исключительно через порты.
        front.fuel().orElseThrow().insert(0,"minetorio:coal",100,false);
        rear.fuel().orElseThrow().insert(0,"minetorio:coal",100,false);
        var tape=rails.stream().map(r->new Traversal(r.id(),true)).toList();UUID train=UUID.randomUUID();
        state.createTrain(new Train(train,List.of(front.id(),cargo.id(),fluid.id(),rear.id()),Optional.of(new Cursor(tape.get(13),.5)),13,tape,0,Mode.AUTOMATIC,Status.NO_SCHEDULE,0,0,0));
        stop(data,rails.get(0),false,1,p(1,-1),"Demo Load");
        stop(data,rails.get(23),true,1,p(47,3),"Demo Unload");
        state.dispatcher().setSchedule(train,new TrainSchedule(List.of(schedule("Demo Load"),schedule("Demo Unload")),0));
        RailwayManager.reconcileViews(level);
        // Электросеть с четырьмя обычными солнечными панелями, без тестовых источников энергии.
        place(ModItems.SUBSTATION.get(),p(10,-5),Direction.NORTH);
        place(ModItems.SUBSTATION.get(),p(28,-5),Direction.NORTH);
        for(int x:new int[]{2,6,10,14})place(ModItems.SOLAR_PANEL.get(),p(x,-10),Direction.NORTH);
        source(p(18,3),ModItems.IRON_PLATE.get(),256);
        place(ModItems.FAST_INSERTER.get(),p(18,2),Direction.NORTH);
        chest(p(36,3));DemoSavedData.tag(level.getBlockEntity(p(36,3)),DemoSavedData.SINK_NAME);
        pending.add(d->d.addSink(p(36,3),64));
        place(ModItems.FAST_INSERTER.get(),p(36,2),Direction.SOUTH);
        // Южный борт цистерны: загрузка передним портом помпы, выгрузка задним.
        place(ModItems.PUMP.get(),p(11,3),Direction.NORTH);
        place(ModItems.PUMP.get(),p(29,2),Direction.SOUTH);
        for(int x:new int[]{11,29}) {
            for(int dx=0;dx<=2;dx++)place(ModItems.PIPE.get(),p(x+dx,4),Direction.NORTH);
            place(ModItems.STORAGE_TANK.get(),p(x,7),Direction.NORTH);
        }
        // Вода добывается штатным береговым насосом и идет через бак и трубы.
        BlockPos water=p(7,7);
        level.setBlock(water.below(),Blocks.STONE.defaultBlockState(),3);
        for(Direction side:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST})level.setBlock(water.relative(side),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(water,Blocks.WATER.defaultBlockState(),3);
        place(ModItems.OFFSHORE_PUMP.get(),p(9,7),Direction.WEST);
        place(ModItems.PIPE.get(),p(10,7),Direction.NORTH);
        sign(p(0,-14),"Железная дорога","Челнок AUTO","Груз + вода","Погрузка: юг");
        sign(p(18,5),"Загрузка","Чит-сундук","Манипулятор","Вода из насоса");
        sign(p(36,5),"Выгрузка","Пластины в сундук","Вода в бак","Ожидание 10 с");
        BlockPos weapons=layout(origin).weapons();chest(weapons);
        var handler=level.getCapability(Capabilities.ItemHandler.BLOCK,weapons,null);
        for(var stack:List.of(new ItemStack(CombatRegistry.PISTOL.get()),new ItemStack(CombatRegistry.SUBMACHINE_GUN.get()),new ItemStack(CombatRegistry.FIREARM_MAGAZINE.get(),100)))
            ItemHandlerHelper.insertItemStacked(handler,stack,false);
        sign(weapons.east(2),"Первый бой","Оружие + магазины","ЛКМ: стрельба","Без живых врагов");
        for(int x=1;x<=3;x++)level.setBlock(origin.offset(42+x,1,61),Blocks.TARGET.defaultBlockState(),3);
    }
    private static TrainSchedule.Stop schedule(String name) {
        return new TrainSchedule.Stop(Optional.of(name),Optional.empty(),false,List.of(new TrainSchedule.Wait(TrainSchedule.Type.TIME,TrainSchedule.Join.AND,600,Optional.empty())));
    }
    private void stop(RailwaySavedData data,Piece rail,boolean forward,double distance,BlockPos pos,String name) {
        data.putStop(new RailwaySavedData.WorldStop(UUID.randomUUID(),pos,name,true,OptionalInt.empty(),rail.origin(),rail.shape(),rail.quarterTurns(),rail.mirrored(),forward,distance));
    }
    private void source(BlockPos pos,Item item,int count) {
        chest(pos);DemoSavedData.tag(level.getBlockEntity(pos),DemoSavedData.SOURCE_NAME);
        var handler=level.getCapability(Capabilities.ItemHandler.BLOCK,pos,null);
        ItemHandlerHelper.insertItemStacked(handler,new ItemStack(item,count),false);
        pending.add(d->d.addSource(pos,net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(),count));
    }
    private void chest(BlockPos pos){level.setBlock(pos,BasicChests.IRON.get().defaultBlockState(),3);}
    private void place(Item item,BlockPos pos,Direction facing) {
        BlockPos target=MultiblockBlockItem.targetFor(item,pos,facing);
        var hit=new BlockHitResult(Vec3.atCenterOf(target),Direction.UP,target,false);
        var context=new BlockPlaceContext(level,null,InteractionHand.MAIN_HAND,new ItemStack(item),hit){
            @Override public Direction getHorizontalDirection(){return facing;}
        };
        if(!((BlockItem)item).place(context).consumesAction())throw new IllegalStateException("rail demo: "+item+" at "+pos);
    }
    private void sign(BlockPos pos,String... lines) {
        level.setBlock(pos,Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION,8),3);
        if(level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText text=new SignText();for(int i=0;i<lines.length;i++)text=text.setMessage(i,Component.literal(lines[i]));
            sign.setText(text,true);sign.setText(text,false);sign.setWaxed(true);
        }
    }
}
