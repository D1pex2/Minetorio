package dev.minetorio.gametest;

import dev.minetorio.content.belt.LaneStacks;
import dev.minetorio.content.combat.CombatRegistry;
import dev.minetorio.content.rail.RailItemKeys;
import dev.minetorio.content.rail.RailwayNbt;
import dev.minetorio.content.rail.RailwaySavedData;
import dev.minetorio.core.data.RailData.StockKind;
import dev.minetorio.core.rail.RailwayState;
import dev.minetorio.core.rail.TrainFuel;
import dev.minetorio.core.rail.TrainMotion;
import dev.minetorio.core.rail.RailwayState.Mode;
import dev.minetorio.core.rail.RailwayState.Status;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.DimensionDataStorage;
import static dev.minetorio.core.rail.RailGeometry.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class RailwaySaveGameTest {
    private static String loadedKey(GameTestHelper h) {
        ItemStack pistol = new ItemStack(CombatRegistry.PISTOL.get());
        pistol.set(CombatRegistry.ROUNDS.get(), 7);
        pistol.set(DataComponents.MAX_STACK_SIZE, 1);
        return LaneStacks.toLane(pistol, h.getLevel().registryAccess()).orElseThrow();
    }
    private static RailwayState populated(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        RailwayState state = new RailwayState(key -> RailItemKeys.limit(key, registries));
        var loco = state.createCar(new UUID(0, 10), StockKind.LOCOMOTIVE, false);
        loco.fuel().orElseThrow().insert(2, "minetorio:coal", 13, false);
        loco.setActiveFuel("minetorio:coal", new BigDecimal("1234567.890123456789"));
        var cargo = state.createCar(new UUID(0, 11), StockKind.CARGO, true);
        var slots = cargo.cargo().orElseThrow();
        h.assertTrue(slots.insert(0, loadedKey(h), 2, false) == 1, "Заряженный пистолет не должен складываться в 2");
        h.assertTrue(slots.insert(1, loadedKey(h), 1, false) == 1, "Второй пистолет занимает другой слот");
        slots.insert(39, "minetorio:pistol", 5, false);
        slots.setFilter(0, Optional.of("minetorio:pistol"));
        slots.setBar(12);
        cargo.setHp(512.5);
        var fluid = state.createCar(new UUID(0, 12), StockKind.FLUID, false);
        h.assertTrue(fluid.insertFluid("water", 30000) == 25000, "Вместимость цистерны");
        h.assertTrue(fluid.insertFluid("steam", 1) == 0, "Смешивание жидкостей");
        Piece a = new Piece(new UUID(0, 1), new Point(0, 0, 0), Shape.STRAIGHT, 0, false);
        Piece b = new Piece(new UUID(0, 2), new Point(0, 0, -2), Shape.STRAIGHT, 0, false);
        Piece c = new Piece(new UUID(0, 3), new Point(0, 0, -2), Shape.CURVE, 0, false);
        List<Piece> pieces = new ArrayList<>(List.of(a,b,c));
        List<Traversal> tape = new ArrayList<>();
        for (int j=10;j>=1;j--) {
            Piece tail = new Piece(new UUID(1,j),new Point(0,0,2*j),Shape.STRAIGHT,0,false);
            pieces.add(tail); tape.add(new Traversal(tail.id(),true));
        }
        state.replaceRails(pieces);
        Traversal first = new Traversal(a.id(), true);
        Traversal turn = new Traversal(c.id(), true);
        h.assertTrue(state.graph().next(first).size() == 2, "Развилка должна иметь два выхода");
        tape.add(first); tape.add(turn);
        state.createTrain(new RailwayState.Train(new UUID(0, 20), List.of(loco.id(), cargo.id(), fluid.id()), Optional.of(new Cursor(first, 0.75)),
                10, tape, 0.5, Mode.AUTOMATIC, Status.ON_THE_PATH, 0, 0, 0));
        return state;
    }
    @GameTest(template = "empty16")
    public static void stateRoundTrip(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var state = populated(h);
        var written = RailwayNbt.write(state, registries);
        var restored = RailwayNbt.read(written, registries);
        h.assertTrue(restored.cars().size() == 3, "NBT потерял вагоны");
        var cargo = restored.cars().get(new UUID(0, 11));
        h.assertTrue(cargo.reversed() && cargo.hp() == 512.5, "Ориентация/HP потеряны");
        var slots = cargo.cargo().orElseThrow();
        h.assertTrue(slots.get(0).count() == 1 && slots.get(1).count() == 1 && slots.get(39).count() == 5, "Количество оружия потеряно");
        h.assertTrue(slots.bar() == 12 && slots.get(0).filter().orElseThrow().equals("minetorio:pistol"), "Bar/filter потеряны");
        var pistol = LaneStacks.stack(slots.get(0).key(), registries);
        h.assertTrue(pistol.getMaxStackSize() == 1 && pistol.getOrDefault(CombatRegistry.ROUNDS.get(), 0) == 7, "Заряд/лимит потеряны");
        h.assertTrue(slots.insertManual(0, loadedKey(h), 1, false) == 0, "Resolver не восстановлен");
        var newCar = restored.createCar(new UUID(0, 99), StockKind.CARGO, false);
        h.assertTrue(newCar.cargo().orElseThrow().insert(0, loadedKey(h), 2, false) == 1, "Новый вагон потерял resolver");
        var loco = restored.cars().get(new UUID(0, 10));
        h.assertTrue(loco.fuel().orElseThrow().snapshot().size() == 3 && loco.fuel().orElseThrow().get(2).count() == 13, "Топливные слоты потеряны");
        h.assertTrue(loco.remainingJ().equals(new BigDecimal("1234567.890123456789")) && loco.activeFuel().equals("minetorio:coal"), "Дробная энергия потеряна");
        h.assertTrue(restored.cars().get(new UUID(0, 12)).fluid().orElseThrow().amount() == 25000, "Жидкость потеряна");
        h.assertTrue(restored.trains().equals(state.trains()), "UUID/лента/голова/состояние потеряны");
        h.succeed();
    }
    @GameTest(template = "empty16")
    public static void invalidSavedDataNeverCreatesEmptyReplacement(GameTestHelper h) throws Exception {
        var registries = h.getLevel().registryAccess();
        for (boolean future : new boolean[]{true, false}) {
            Path folder = Files.createTempDirectory("minetorio-railway-invalid-");
            CompoundTag invalid = RailwayNbt.write(populated(h), registries);
            if (future) invalid.putInt("schemaVersion", 999);
            else invalid.getList("cars", Tag.TAG_COMPOUND).getCompound(1).getCompound("cargo")
                    .getList("slots", Tag.TAG_COMPOUND).getCompound(0).putInt("count", 2);
            CompoundTag root = new CompoundTag();
            root.put("data", invalid);
            Path file = folder.resolve(RailwaySavedData.NAME + ".dat");
            NbtIo.writeCompressed(root, file);
            byte[] before = Files.readAllBytes(file);
            var storage = new DimensionDataStorage(folder.toFile(), DataFixers.getDataFixer(), registries);
            boolean rejected = false;
            try { RailwaySavedData.get(storage, folder, registries); }
            catch (IllegalStateException expected) {
                rejected = expected.getMessage().contains(file.toString()) && expected.getCause() != null
                        && expected.getCause().getMessage().contains(future ? "schemaVersion" : "slots[0]");
            }
            h.assertTrue(rejected, "Loader вернул пустое состояние после ошибки существующего файла");
            rejected = false;
            try { RailwaySavedData.get(storage, folder, registries); }
            catch (IllegalStateException expected) { rejected = true; }
            h.assertTrue(rejected, "Повторное чтение cache=null создало пустую модель");
            storage.save();
            h.assertTrue(Arrays.equals(before, Files.readAllBytes(file)), "Исходное содержимое поврежденного файла изменилось");
        }
        Path rawFolder = Files.createTempDirectory("minetorio-railway-raw-");
        Path rawFile = rawFolder.resolve(RailwaySavedData.NAME + ".dat");
        byte[] raw = "Это поврежденный NBT, исходные байты нельзя перезаписывать".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(rawFile, raw);
        var rawStorage = new DimensionDataStorage(rawFolder.toFile(), DataFixers.getDataFixer(), registries);
        boolean rejected = false;
        try { RailwaySavedData.get(rawStorage, rawFolder, registries); }
        catch (IllegalStateException expected) { rejected = expected.getMessage().contains(rawFile.toString()); }
        h.assertTrue(rejected, "Файл, не являющийся NBT, заменен пустой моделью");
        rawStorage.save();
        h.assertTrue(Arrays.equals(raw, Files.readAllBytes(rawFile)), "Исходные байты raw-файла изменились");
        h.succeed();
    }
    @GameTest(template = "empty16")
    public static void storageReloadAndDirtyOwnership(GameTestHelper h) throws Exception {
        var registries = h.getLevel().registryAccess();
        Path folder = Files.createTempDirectory("minetorio-railway-storage-");
        var storage = new DimensionDataStorage(folder.toFile(), DataFixers.getDataFixer(), registries);
        var data = RailwaySavedData.get(storage, folder, registries);
        h.assertTrue(data == RailwaySavedData.get(storage, folder, registries), "Два владельца одного storage");
        data.setDirty(false);
        var car = data.state().createCar(UUID.randomUUID(), StockKind.CARGO, false);
        h.assertTrue(data.isDirty(), "Создание вагона не помечает SavedData");
        data.setDirty(false);
        car.cargo().orElseThrow().insert(0, loadedKey(h), 1, true);
        h.assertTrue(!data.isDirty(), "Симуляция помечает SavedData");
        car.cargo().orElseThrow().insert(0, loadedKey(h), 1, false);
        h.assertTrue(data.isDirty(), "Вставка в слот не помечает SavedData");
        CompoundTag root = new CompoundTag();
        root.put("data", data.save(new CompoundTag(), registries));
        NbtIo.writeCompressed(root, folder.resolve(RailwaySavedData.NAME + ".dat"));
        var reloaded = RailwaySavedData.get(new DimensionDataStorage(folder.toFile(), DataFixers.getDataFixer(), registries), folder, registries);
        h.assertTrue(!reloaded.isDirty() && reloaded.state().cars().size() == 1, "Чтение storage/чистый dirty");
        var slots = reloaded.state().cars().get(car.id()).cargo().orElseThrow();
        h.assertTrue(slots.get(0).key().equals(loadedKey(h)), "Storage потерял components");
        slots.extract(0, 1, false);
        h.assertTrue(reloaded.isDirty(), "Восстановленный слот потерял dirty callback");
        // Реальный get(ServerLevel) подтверждает путь хранения каждого измерения.
        var overworld = RailwaySavedData.get(h.getLevel());
        h.assertTrue(overworld == RailwaySavedData.get(h.getLevel()), "ServerLevel создает второго владельца");
        var nether = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        h.assertTrue(nether != null && overworld != RailwaySavedData.get(nether), "Состояния разных измерений объединены");
        h.succeed();
    }
    @GameTest(template = "empty16")
    public static void malformedCargoFailsButStaleRouteKeepsCargo(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        CompoundTag source = RailwayNbt.write(populated(h), registries);
        for (int variant = 0; variant < 9; variant++) {
            CompoundTag broken = source.copy();
            CompoundTag cargo = broken.getList("cars", Tag.TAG_COMPOUND).getCompound(1).getCompound("cargo");
            CompoundTag slot = cargo.getList("slots", Tag.TAG_COMPOUND).getCompound(0);
            switch (variant) {
                case 0 -> slot.putString("key", "minetorio:pistol#{broken");
                case 1 -> slot.putInt("count", 2);
                case 2 -> slot.putString("filter", "minetorio:missing_item");
                case 3 -> cargo.getList("slots", Tag.TAG_COMPOUND).remove(0);
                case 4 -> broken.getList("cars", Tag.TAG_COMPOUND).add(broken.getList("cars", Tag.TAG_COMPOUND).getCompound(1).copy());
                case 5 -> broken.getList("rails", Tag.TAG_COMPOUND).getCompound(0).putInt("x", 1);
                case 6 -> broken.getList("cars", Tag.TAG_COMPOUND).getCompound(2).getCompound("fluid").putDouble("amount", 25001);
                case 7 -> broken.getList("trains", Tag.TAG_COMPOUND).getCompound(0).getList("cars", Tag.TAG_COMPOUND).getCompound(0).putUUID("id", UUID.randomUUID());
                case 8 -> broken.getList("cars", Tag.TAG_COMPOUND).getCompound(0).putString("remainingJ", "4000001");
            }
            boolean rejected = false;
            try { RailwayNbt.read(broken, registries); } catch (IllegalArgumentException expected) { rejected = true; }
            h.assertTrue(rejected, "Поврежденная schema/gruz принята, вариант " + variant);
        }
        source.getList("trains", Tag.TAG_COMPOUND).getCompound(0).getList("tape", Tag.TAG_COMPOUND)
                .getCompound(1).putUUID("piece", UUID.randomUUID());
        var restored = RailwayNbt.read(source, registries);
        var train = restored.trains().values().iterator().next();
        h.assertTrue(train.speed() == 0 && train.status() == Status.PATH_LOST && train.tape().isEmpty(), "Старый путь не останавливает поезд");
        h.assertTrue(restored.cars().get(new UUID(0, 11)).cargo().orElseThrow().get(0).key().equals(loadedKey(h)), "Старый путь удаляет груз");
        h.succeed();
    }
    @GameTest(template = "empty16")
    public static void realComponentStackLimit(GameTestHelper h) {
        ItemStack pistol = new ItemStack(CombatRegistry.PISTOL.get());
        pistol.set(CombatRegistry.ROUNDS.get(), 7);
        pistol.set(DataComponents.MAX_STACK_SIZE, 1);
        String key = LaneStacks.toLane(pistol, h.getLevel().registryAccess()).orElseThrow();
        h.assertTrue(RailItemKeys.limit(key, h.getLevel().registryAccess()) == 1, "Компонент MAX_STACK_SIZE=1 потерян");
        h.assertTrue(RailItemKeys.limit("minetorio:pistol", h.getLevel().registryAccess()) == 5, "Базовый лимит pistol должен быть 5");
        for (String invalid : List.of("minetorio:pistol#{broken", "minetorio:pistol#{unknown:1}",
                "minetorio:missing", "minecraft:air", "minetorio:pistol#{\"minetorio:magazine_rounds\":99}",
                "minetorio:pistol#" + " ".repeat(LaneStacks.MAX_KEY_BYTES))) {
            h.assertTrue(RailItemKeys.limit(invalid, h.getLevel().registryAccess()) == 0, "Недопустимый полный ключ принят");
        }
        var ghost = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof dev.minetorio.content.robot.GhostItem).findFirst().orElseThrow();
        h.assertTrue(RailItemKeys.limit(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ghost).toString(),
                h.getLevel().registryAccess()) == 0, "Ghost принят в груз");
        h.succeed();
    }
    private static CompoundTag schemaOne(CompoundTag source) {
        CompoundTag legacy=source.copy(); legacy.putInt("schemaVersion",1);
        for(Tag item:legacy.getList("trains",Tag.TAG_COMPOUND)) {
            var train=(CompoundTag)item;
            train.remove("headIndex"); train.remove("routeRevision"); train.remove("consistRevision"); train.remove("motionRevision");
            train.putDouble("speed",Math.abs(train.getDouble("speed")));
        }
        return legacy;
    }
    private static CompoundTag loopFixture(GameTestHelper h) {
        var state=new RailwayState(key->RailItemKeys.limit(key,h.getLevel().registryAccess()));
        List<Piece> rails=new ArrayList<>();
        List<Piece> quarter=List.of(new Piece(new UUID(8,1),new Point(0,0,0),Shape.CURVE,0,false),
                new Piece(new UUID(8,2),new Point(4,0,-8),Shape.DIAGONAL,0,false),
                new Piece(new UUID(8,3),new Point(12,0,-12),Shape.CURVE,3,true));
        int[][] shifts={{0,0},{12,-12},{24,0},{12,12}}; long id=100;
        for(int q=0;q<4;q++) for(Piece raw:quarter) {
            Piece p=transform(raw,q,false);
            rails.add(new Piece(new UUID(8,id++),new Point(p.origin().x()+shifts[q][0],0,p.origin().z()+shifts[q][1]),p.shape(),p.quarterTurns(),p.mirrored()));
        }
        state.replaceRails(rails);
        var a=state.createCar(new UUID(0,10),StockKind.LOCOMOTIVE,false);
        var b=state.createCar(new UUID(0,11),StockKind.CARGO,true);
        var c=state.createCar(new UUID(0,12),StockKind.FLUID,false);
        List<Traversal> tape=new ArrayList<>(); Traversal step=new Traversal(rails.getFirst().id(),true);
        for(int i=0;i<36;i++) { tape.add(step); step=state.graph().next(step).getFirst(); }
        state.createTrain(new RailwayState.Train(new UUID(0,20),List.of(a.id(),b.id(),c.id()),Optional.of(new Cursor(tape.get(12),0)),12,tape,-0.1,Mode.MANUAL,Status.MANUAL_CONTROL,4,5,6));
        var root=RailwayNbt.write(state,h.getLevel().registryAccess());
        root.put("cars",RailwayNbt.write(populated(h),h.getLevel().registryAccess()).getList("cars",Tag.TAG_COMPOUND));
        return root;
    }
    @GameTest(template="empty16")
    public static void schemaOneMigrationPreservesCargoAndRejectsAmbiguousOrShortHistory(GameTestHelper h) {
        var registries=h.getLevel().registryAccess(); var original=populated(h);
        CompoundTag source=RailwayNbt.write(original,registries), legacy=schemaOne(source);
        h.assertTrue(!legacy.getList("trains",Tag.TAG_COMPOUND).getCompound(0).contains("headIndex"),"Fixture должен иметь поля schema1");
        var migrated=RailwayNbt.read(legacy,registries);
        h.assertTrue(migrated.trains().equals(original.trains()),"Однозначная schema1 изменила layout");
        h.assertTrue(TrainMotion.carPoses(migrated,new UUID(0,20)).equals(TrainMotion.carPoses(original,new UUID(0,20))),"Миграция изменила позы");
        h.assertTrue(RailwayNbt.write(migrated,registries).getInt("schemaVersion")==2,"Миграция не записывает schema2");
        CompoundTag shortHistory=legacy.copy(); var train=shortHistory.getList("trains",Tag.TAG_COMPOUND).getCompound(0);
        var shortTape=new ListTag(); shortTape.add(train.getList("tape",Tag.TAG_COMPOUND).getCompound(10).copy()); train.put("tape",shortTape);
        CompoundTag ambiguous=loopFixture(h); var ambiguousTrain=ambiguous.getList("trains",Tag.TAG_COMPOUND).getCompound(0);
        var ambiguousHead=ambiguousTrain.getList("tape",Tag.TAG_COMPOUND).getCompound(17).copy(); ambiguousHead.putDouble("distance",0.5); ambiguousTrain.put("head",ambiguousHead);
        for(int candidate:new int[]{5,17}) {
            var unambiguous=ambiguous.copy(); unambiguous.getList("trains",Tag.TAG_COMPOUND).getCompound(0).putInt("headIndex",candidate);
            h.assertTrue(RailwayNbt.read(unambiguous,registries).trains().get(new UUID(0,20)).head().isPresent(),"Оба кандидата должны покрывать все тело, чтобы неоднозначность не маскировалась короткой лентой");
        }
        for(CompoundTag broken:List.of(shortHistory,schemaOne(ambiguous))) {
            var recovered=RailwayNbt.read(broken,registries); var lost=recovered.trains().get(new UUID(0,20));
            h.assertTrue(lost.status()==Status.PATH_LOST && lost.head().isEmpty() && lost.headIndex()==-1 && lost.speed()==0,"Неоднозначная/короткая schema1 изобрела layout");
            var saved=RailwayNbt.write(recovered,registries);
            h.assertTrue(saved.getList("cars",Tag.TAG_COMPOUND).equals(broken.getList("cars",Tag.TAG_COMPOUND)),"Миграция повредила груз/UUID/топливо");
            h.assertTrue(saved.equals(RailwayNbt.write(RailwayNbt.read(saved,registries),registries)),"Повторная миграция нестабильна");
        }
        h.succeed();
    }
    @GameTest(template="empty16")
    public static void schemaTwoSignedLoopResumesIdenticalMotion(GameTestHelper h) {
        var registries=h.getLevel().registryAccess(); var source=loopFixture(h);
        var before=RailwayNbt.read(source,registries); var after=RailwayNbt.read(RailwayNbt.write(before,registries),registries); UUID id=new UUID(0,20);
        h.assertTrue(after.trains().get(id).headIndex()==12 && after.trains().get(id).speed()==-0.1,"Второй проход/знак потерян");
        h.assertTrue(after.trains().get(id).routeRevision()==4 && after.trains().get(id).consistRevision()==5 && after.trains().get(id).motionRevision()==6,"Ревизии потеряны");
        var control=new TrainMotion.Control(-1,false,TrainMotion.Turn.LEFT);
        TrainMotion.step(before,id,control,0); TrainMotion.step(after,id,control,0);
        h.assertTrue(before.trains().equals(after.trains()) && after.trains().get(id).headIndex()==11,"Следующий occurrence после reload расходится");
        h.assertTrue(TrainMotion.carPoses(before,id).equals(TrainMotion.carPoses(after,id)),"После reload движение расходится");
        h.assertTrue(before.cars().get(new UUID(0,10)).remainingJ().equals(after.cars().get(new UUID(0,10)).remainingJ()),"После reload расход расходится");
        for(int variant=0;variant<3;variant++) {
            var malformed=source.copy(); var train=malformed.getList("trains",Tag.TAG_COMPOUND).getCompound(0);
            if(variant==0) train.remove("headIndex"); else if(variant==1) train.putString("headIndex","12"); else train.putInt("headIndex",999);
            var restored=RailwayNbt.read(malformed,registries);
            h.assertTrue(restored.trains().get(id).status()==Status.PATH_LOST,"Неверный index принят за occurrence0");
            h.assertTrue(RailwayNbt.write(restored,registries).getList("cars",Tag.TAG_COMPOUND).equals(source.getList("cars",Tag.TAG_COMPOUND)),"Неверный index уничтожил cargo");
        }
        h.succeed();
    }
    @GameTest(template="empty16")
    public static void decimalFuelAndExhaustedModifierSurviveActualNbt(GameTestHelper h) {
        var registries=h.getLevel().registryAccess(); var state=populated(h); UUID id=new UUID(0,10); var car=state.cars().get(id);
        car.setActiveFuel("minetorio:solid_fuel",new BigDecimal("100"));
        for(int i=0;i<200;i++) TrainFuel.draw(car,new BigDecimal("0.25"));
        state=RailwayNbt.read(RailwayNbt.write(state,registries),registries); car=state.cars().get(id);
        for(int i=0;i<200;i++) TrainFuel.draw(car,new BigDecimal("0.25"));
        h.assertTrue(car.remainingJ().signum()==0 && car.activeFuel().equals("minetorio:solid_fuel"),"Дробные J или last modifier потеряны");
        state=RailwayNbt.read(RailwayNbt.write(state,registries),registries); car=state.cars().get(id);
        TrainFuel.draw(car,BigDecimal.ZERO); var preview=TrainFuel.preview(car,BigDecimal.ONE);
        h.assertTrue(TrainFuel.activeTopSpeed(car)==1.05,"Preview/нулевая выдача меняет last modifier");
        h.assertTrue(preview.equals(TrainFuel.draw(car,BigDecimal.ONE)) && TrainFuel.activeTopSpeed(car)==1,"После reload активация следующего угля неверна");
        h.succeed();
    }
    @GameTest(template="empty16")
    public static void namedFuelKeepsComponentsAndPreviewIsPure(GameTestHelper h) {
        var registries=h.getLevel().registryAccess(); var state=new RailwayState(key->RailItemKeys.limit(key,registries));
        var car=state.createCar(UUID.randomUUID(),StockKind.LOCOMOTIVE,false);
        for(String fuel:List.of("coal","solid_fuel")) {
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minetorio",fuel));
            ItemStack stack=new ItemStack(item); stack.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Топливо с именем"));
            String key=LaneStacks.toLane(stack,registries).orElseThrow();
            car.setActiveFuel("",BigDecimal.ZERO); car.fuel().orElseThrow().extract(0,100,false);
            h.assertTrue(car.fuel().orElseThrow().insert(0,key,2,false)==2,"Топливо с components не принято");
            var slots=car.fuel().orElseThrow().snapshot(); var preview=TrainFuel.preview(car,BigDecimal.ONE);
            h.assertTrue(slots.equals(car.fuel().orElseThrow().snapshot()) && car.activeFuel().isEmpty(),"Preview мутировал компоненты/activeFuel");
            h.assertTrue(preview.equals(TrainFuel.draw(car,BigDecimal.ONE)),"Preview отличается от draw");
            h.assertTrue(car.fuel().orElseThrow().get(0).key().equals(key) && car.fuel().orElseThrow().get(0).count()==1,"Сгорание удалило компоненты остатка");
            var restored=RailwayNbt.read(RailwayNbt.write(state,registries),registries);
            h.assertTrue(restored.cars().get(car.id()).fuel().orElseThrow().get(0).key().equals(key),"NBT потерял имя топлива");
        }
        h.succeed();
    }
}
