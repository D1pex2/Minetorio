package dev.minetorio.gametest;

import dev.minetorio.content.belt.LaneStacks;
import dev.minetorio.content.combat.CombatRegistry;
import dev.minetorio.content.rail.RailItemKeys;
import dev.minetorio.content.rail.RailwayNbt;
import dev.minetorio.content.rail.RailwaySavedData;
import dev.minetorio.core.data.RailData.StockKind;
import dev.minetorio.core.rail.RailwayState;
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
        state.replaceRails(List.of(a, b, c));
        Traversal first = new Traversal(a.id(), true);
        Traversal turn = new Traversal(c.id(), true);
        h.assertTrue(state.graph().next(first).size() == 2, "Развилка должна иметь два выхода");
        state.createTrain(new UUID(0, 20), List.of(loco.id(), cargo.id(), fluid.id()), Optional.of(new Cursor(first, 0.75)),
                List.of(first, turn), 0.5, Mode.AUTOMATIC, Status.ON_THE_PATH);
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
}
