# Minetorio — этап 1: план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Первая играбельная версия: от кирки и ванильной руды до автоматизированной красной и зелёной науки: залежи, буры, конвейеры, манипуляторы, печи, сборщики, пар и электросеть, лаборатории, ручной крафт с очередью.

**Architecture:** Вся игровая логика (ленты, манипуляторы, крафт, энергия, жидкости, залежи, исследования, очередь крафта) живёт в чистом Java-пакете `dev.minetorio.core` без импортов Minecraft и покрывается JUnit-тестами. Слой Minecraft (`dev.minetorio.content.*`) — тонкая обёртка: блоки, block entity, GUI, рендер, пакеты, сохранение; он проверяется NeoForge GameTest'ами. Многоблочные машины: блок-контроллер с block entity в начале координат плюс блоки-части, которые ссылаются на контроллер.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.x (последняя стабильная из MDK), ModDevGradle, JUnit 5, NeoForge GameTest.

**Spec:** `docs/superpowers/specs/2026-10-26-minetorio-design.md`

## Global Constraints

- Minecraft **1.21.1**, NeoForge **21.1.x**, Java **21**, сборка через ModDevGradle (шаблон `NeoForgeMDKs/MDK-1.21.1-ModDevGradle`).
- Mod id `minetorio`, корневой пакет `dev.minetorio`.
- Пакет `dev.minetorio.core` не импортирует `net.minecraft.*` и `net.neoforged.*`, это проверяет тест (задача 1).
- 20 тиков в секунду. Числа Factorio 1.1 (60 UPS) пересчитываются по секундам: `ticks = round(seconds * 20)`, `1 kW = 50 J/tick`.
- Энергия хранится в `long` джоулях, жидкости в `double` единицах.
- Никаких кусак, атак и загрязнения.
- Мод работает в одиночке и на выделенном сервере: в общем коде нет ссылок на клиентские классы (рендер, экраны, клавиши только в `dev.minetorio.client`).
- Ванильные предметы остаются для обычной игры. Предметы мода крафтятся **только** через ручной крафт мода и машины, без рецептов верстака.
- Залежи генерируются только в Верхнем мире (Overworld).
- Перевод: все строки интерфейса в `en_us.json` и `ru_ru.json`.
- **Отклонение от спецификации (решение плана):** синяя лента (express) в Factorio требует смазку из нефти. Её блоки и логика делаются в этапе 1, но рецепт и исследование появятся в этапе 2. В этапе 1 синяя лента доступна только в творческом режиме.

## Review Focus

1. **Сохранение и выгрузка чанков.** Предметы на лентах, прогресс машин, топливо, жидкости и очередь крафта переживают выход из мира и выгрузку чанка без потерь. Тесты на круговое NBT-сохранение — задачи 10, 11, 14, 15, 17.
2. **Ломание многоблочной машины.** Сломал любую часть: исчезают все части, выпадает ровно один предмет машины плюс её содержимое. Тест — задача 10.
3. **Сохранение предметов.** Если цель манипулятора, ленты или бура полная, предмет ждёт и не пропадает и не дублируется. Тесты — задачи 5, 12, 13.
4. **Энергия на краях.** Генераторов нет, потребителей нет, или машина вне зоны столбов: деления на ноль нет, машины стоят, а не крутятся бесплатно. Тест — задачи 3, 14.
5. **Мультиплеер.** У каждого игрока своя очередь крафта, исследования общие на мир. Тест — задача 17.

---

## Структура файлов

```
build.gradle, settings.gradle, gradle.properties, gradlew*      (из MDK)
src/main/java/dev/minetorio/
  Minetorio.java                         @Mod, регистрация всего
  core/                                  ЧИСТАЯ ЛОГИКА, без Minecraft
    Units.java                           тики, джоули
    item/ItemStackLike.java, Inventory.java
    recipe/Ingredient.java, Recipe.java, RecipeCategory.java, RecipeBook.java
    data/Phase1Data.java                 все рецепты, топливо, дерево технологий этапа 1
    machine/CraftingMachine.java, Burner.java
    power/PowerNetwork.java, PowerConsumer.java, PowerProducer.java, PoleGraph.java
    fluid/FluidBox.java, FluidFlow.java, SteamPlant.java
    belt/BeltTier.java, BeltLane.java, LaneItem.java, LaneSink.java, BeltShape.java,
         SplitterLogic.java, UndergroundLink.java
    inserter/InserterType.java, InserterLogic.java, PickupSource.java, DropTarget.java
    deposit/OreType.java, Deposit.java, DepositGenerator.java, DepositLedger.java
    research/Technology.java, TechTree.java, ResearchState.java, LabLogic.java
    handcraft/HandcraftQueue.java
  content/                               СЛОЙ MINECRAFT
    registry/ModBlocks.java, ModItems.java, ModBlockEntities.java, ModMenus.java,
             ModAttachments.java, ModCreativeTab.java, ModPayloads.java
    multiblock/MultiblockShape.java, MultiblockControllerBlock.java, MultiblockPartBlock.java,
               MultiblockPartBlockEntity.java
    belt/…  inserter/…  deposit/…  machine/…  power/…  research/…  handcraft/…
    nbt/CoreNbt.java                     перевод core-состояния в CompoundTag и обратно
  client/                                рендер, экраны, клавиши (только клиент)
  gametest/                              GameTest'ы (main source set, чтобы видел runGameTestServer)
src/main/resources/
  assets/minetorio/{blockstates,models,textures,lang}/
  data/minetorio/structure/empty16.nbt   пустой шаблон 16×8×16 для GameTest
src/test/java/dev/minetorio/core/…       JUnit-тесты чистой логики
```

Предметы в core — строковые id (`"minetorio:iron_plate"`, `"minecraft:coal"`). Слой Minecraft переводит их через `BuiltInRegistries.ITEM`.

---

### Task 0: Тулчейн и скелет мода

**Files:**
- Create: весь MDK (`build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`)
- Create: `src/main/java/dev/minetorio/Minetorio.java`
- Create: `src/main/java/dev/minetorio/gametest/SmokeGameTest.java`
- Create: `src/main/resources/data/minetorio/structure/empty16.nbt`
- Create: `src/test/java/dev/minetorio/core/SmokeTest.java`
- Modify: `.gitignore`

**Interfaces:**
- Produces: собирающийся мод `minetorio`, команды `./gradlew test`, `./gradlew runGameTestServer`, `./gradlew runClient`; GameTest-шаблон `minetorio:empty16`.

- [ ] **Step 1: Установить JDK 21**

Run: `winget install EclipseAdoptium.Temurin.21.JDK` (на машине Java нет). Затем `java -version`.
Expected: `openjdk version "21.…"`

- [ ] **Step 2: Развернуть MDK 1.21.1 ModDevGradle в корень репо** (без `.git` шаблона). В `gradle.properties`: `mod_id=minetorio`, `mod_name=Minetorio`, `mod_group_id=dev.minetorio`, `mod_version=0.1.0`, `minecraft_version=1.21.1`, `neo_version` — последняя 21.1.x из MDK. В `build.gradle` подключить JUnit 5 (`testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'`, `test { useJUnitPlatform() }`) и включить GameTest-неймспейс `minetorio` в run-конфигурации `gameTestServer` (`systemProperty 'neoforge.enabledGameTestNamespaces', 'minetorio'`).

- [ ] **Step 3: Пустой GameTest-шаблон.** Записать `empty16.nbt`: структура размера `[16,8,16]`, пустые `palette`, `blocks`, `entities`, `DataVersion` 3955. Скрипт генерации положить в `tools/MakeEmptyStructure.java` (одноразовый, через `NbtIo.writeCompressed`), чтобы шаблон можно было пересоздать.

- [ ] **Step 4: Написать smoke-тесты**

```java
// SmokeTest.java
@Test void junitRuns() { assertEquals(4, 2 + 2); }
// SmokeGameTest.java
@GameTestHolder("minetorio") @PrefixGameTestTemplate(false)
public class SmokeGameTest {
  @GameTest(template = "empty16") public static void serverBoots(GameTestHelper h) { h.succeed(); }
}
```

- [ ] **Step 5: Прогнать**

Run: `./gradlew test` → `BUILD SUCCESSFUL`. Run: `./gradlew runGameTestServer` → `All 1 required tests passed`.

- [ ] **Step 6: Проверить клиент вручную.** `./gradlew runClient`: игра запускается, мод `Minetorio` есть в списке модов.

- [ ] **Step 7: Commit** — `chore: NeoForge 1.21.1 mod skeleton with JUnit and GameTest`

---

### Task 1: Единицы, рецепты и данные этапа 1

**Files:**
- Create: `core/Units.java`, `core/item/Inventory.java`, `core/recipe/*`, `core/data/Phase1Data.java`
- Test: `src/test/java/dev/minetorio/core/data/Phase1DataTest.java`, `src/test/java/dev/minetorio/core/CorePurityTest.java`

**Interfaces:**
- Produces:
  - `Units.ticks(double seconds) -> int` (`Math.round(s*20)`), `Units.kw(double kw) -> long` (J/tick), `Units.mj(double mj) -> long` (J)
  - `interface Inventory { int count(String item); int remove(String item, int n); int add(String item, int n); }`: `remove` и `add` возвращают сколько реально убрано/добавлено.
  - `record Ingredient(String item, int count)`
  - `enum RecipeCategory { CRAFTING, SMELTING }`
  - `record Recipe(String id, List<Ingredient> inputs, List<Ingredient> outputs, int ticks, RecipeCategory category)`
  - `final class RecipeBook { Optional<Recipe> get(String id); List<Recipe> producing(String item); Optional<Recipe> smeltingFor(String inputItem); Collection<Recipe> all(); }`
  - `Phase1Data.recipes() -> RecipeBook`, `Phase1Data.fuelValues() -> Map<String,Long>` (J), `Phase1Data.startRecipes() -> Set<String>` (`Phase1Data.techTree()` добавляет задача 7)

**Данные (Factorio 1.1, id рецепта = id результата без неймспейса):**

Плавка (SMELTING, 3.2 s): `minetorio:iron_ore`→`iron_plate`; `minecraft:raw_iron`→`iron_plate` (бутстрап); `minetorio:copper_ore`→`copper_plate`; `minecraft:raw_copper`→`copper_plate` (бутстрап); 2 `minecraft:cobblestone`→`stone_brick`.

Крафт (CRAFTING), формат «входы → выход, время»:
| id | входы | выход | s |
|---|---|---|---|
| iron_gear_wheel | 2 iron_plate | 1 | 0.5 |
| copper_cable | 1 copper_plate | 2 | 0.5 |
| electronic_circuit | 1 iron_plate, 3 copper_cable | 1 | 0.5 |
| pipe | 1 iron_plate | 1 | 0.5 |
| stone_furnace | 5 minecraft:cobblestone | 1 | 0.5 |
| burner_mining_drill | 3 iron_gear_wheel, 1 stone_furnace, 3 iron_plate | 1 | 2 |
| electric_mining_drill | 3 electronic_circuit, 5 iron_gear_wheel, 10 iron_plate | 1 | 2 |
| transport_belt | 1 iron_gear_wheel, 1 iron_plate | 2 | 0.5 |
| underground_belt | 10 iron_plate, 5 transport_belt | 2 | 1 |
| splitter | 5 electronic_circuit, 5 iron_plate, 4 transport_belt | 1 | 1 |
| fast_transport_belt | 5 iron_gear_wheel, 1 transport_belt | 1 | 0.5 |
| fast_underground_belt | 40 iron_gear_wheel, 2 underground_belt | 2 | 2 |
| fast_splitter | 1 splitter, 10 electronic_circuit, 10 iron_gear_wheel | 1 | 2 |
| burner_inserter | 1 iron_gear_wheel, 1 iron_plate | 1 | 0.5 |
| inserter | 1 electronic_circuit, 1 iron_gear_wheel, 1 iron_plate | 1 | 0.5 |
| long_handed_inserter | 1 inserter, 1 iron_gear_wheel, 1 iron_plate | 1 | 0.5 |
| fast_inserter | 2 electronic_circuit, 2 iron_plate, 1 inserter | 1 | 0.5 |
| assembling_machine_1 | 3 electronic_circuit, 5 iron_gear_wheel, 9 iron_plate | 1 | 0.5 |
| assembling_machine_2 | 2 iron_plate, 3 electronic_circuit, 5 iron_gear_wheel, 1 assembling_machine_1 | 1 | 0.5 |
| lab | 10 electronic_circuit, 10 iron_gear_wheel, 4 transport_belt | 1 | 2 |
| offshore_pump | 2 electronic_circuit, 1 pipe, 1 iron_gear_wheel | 1 | 0.5 |
| boiler | 1 stone_furnace, 4 pipe | 1 | 0.5 |
| steam_engine | 8 iron_gear_wheel, 5 pipe, 10 iron_plate | 1 | 0.5 |
| small_electric_pole | 1 minecraft:stick, 2 copper_cable | 2 | 0.5 |
| automation_science_pack | 1 copper_plate, 1 iron_gear_wheel | 1 | 5 |
| logistic_science_pack | 1 inserter, 1 transport_belt | 1 | 6 |
| scanner | 5 electronic_circuit, 10 iron_plate, 5 copper_cable | 1 | 2 |

Экспресс-ленты (`express_transport_belt`, `express_underground_belt`, `express_splitter`) в этапе 1 без рецептов (см. Global Constraints). Рецепт сканера — решение плана (в Factorio его нет).

Топливо (`fuelValues`): `minecraft:coal` 4 MJ, `minecraft:charcoal` 4 MJ, `minecraft:coal_block` 36 MJ, любые `minecraft:*_log` 2 MJ, `minecraft:*_planks` 0.5 MJ, `minecraft:stick` 0.25 MJ. Логи и доски перечислить явно по ванильным id.

`startRecipes()`: всё, кроме `assembling_machine_1/2`, `long_handed_inserter`, `fast_inserter`, `underground_belt`, `splitter`, `logistic_science_pack`, `fast_*`, `express_*`. Плавка открыта всегда.

- [ ] **Step 1: Тесты**

```java
@Test void everyIngredientIsProducibleOrBase() // каждый вход любого рецепта либо производится другим рецептом, либо входит в BASE = {minetorio:iron_ore, minetorio:copper_ore, minecraft:raw_iron, minecraft:raw_copper, minecraft:coal, minecraft:cobblestone, minecraft:stick}
@Test void redScienceTicks() { assertEquals(100, book.get("automation_science_pack").get().ticks()); }
@Test void ironPlateSmeltsFromBootstrapRawIron() { assertEquals("minetorio:iron_plate", book.smeltingFor("minecraft:raw_iron").get().outputs().get(0).item()); }
@Test void beltRecipeMakesTwo() { assertEquals(2, book.get("transport_belt").get().outputs().get(0).count()); }
@Test void coalIs4MJ() { assertEquals(4_000_000L, Phase1Data.fuelValues().get("minecraft:coal")); }
@Test void unitsConvert() { assertEquals(64, Units.ticks(3.2)); assertEquals(4_500L, Units.kw(90)); }
@Test void assemblerLockedAtStart() { assertFalse(Phase1Data.startRecipes().contains("assembling_machine_1")); }
// CorePurityTest: обходит src/main/java/dev/minetorio/core/**/*.java, ни одна строка не начинается с "import net.minecraft" или "import net.neoforged"
```

- [ ] **Step 2: Run** `./gradlew test` → FAIL (классов нет).
- [ ] **Step 3: Реализовать** классы и таблицы выше. `Phase1Data.techTree()` добавляется в задаче 7.
- [ ] **Step 4: Run** `./gradlew test` → PASS.
- [ ] **Step 5: Commit** — `feat(core): units, recipes and phase 1 data`

---

### Task 2: Крафтящая машина и горелка (core)

**Files:**
- Create: `core/machine/CraftingMachine.java`, `core/machine/Burner.java`
- Test: `src/test/java/dev/minetorio/core/machine/CraftingMachineTest.java`, `BurnerTest.java`

**Interfaces:**
- Consumes: `Recipe`, `RecipeBook`, `Units` (задача 1).
- Produces:
  - `CraftingMachine(double speed, long powerPerTickJ)`: сборщик-1 `(0.5, Units.kw(75))`, сборщик-2 `(0.75, Units.kw(150))`, каменная печь `(1.0, Units.kw(90))`.
  - `static CraftingMachine smelter(double speed, long powerPerTickJ, RecipeBook book)`: рецепт выбирается по первому вставленному входу и меняется, только когда входы и выходы пусты.
  - `void setRecipe(@Nullable Recipe r)`: сбрасывает прогресс, возвращает входы вызывающему через `Map<String,Integer> drainInputs()`.
  - `int insertInput(String item, int count, boolean automated)`: принимает только входы рецепта. Если `automated`, то только пока запас < `2 × нужно на крафт`; вручную до 64.
  - `int extractOutput(String item, int max)`, `Map<String,Integer> inputs()`, `Map<String,Integer> outputs()`
  - `long powerDemand()`: `powerPerTickJ`, если машина может работать (есть входы и место в выходе), иначе 0.
  - `void tick(double satisfaction)`: прогресс += `satisfaction * speed / recipe.ticks()`. Входы списываются в начале крафта, выходы добавляются при прогрессе ≥ 1. Если выход не влезает (лимит 64 на предмет), машина ждёт.
  - `double progress()`
  - `Burner(long powerPerTickJ, Map<String,Long> fuelValues)`: `int insertFuel(String item, int count, boolean automated)` (автоматически, пока в слоте < 5 шт.), `double draw()` возвращает долю 0..1 полученной на этот тик энергии, сжигая топливо по мере нужды. `long remainingJ()`, `Map<String,Integer> fuel()`.

- [ ] **Step 1: Тесты**

```java
@Test void assembler1MakesGearIn20Ticks() // speed 0.5, рецепт 10 тиков: после 19 tick(1.0) outputs пусто, после 20-го — 1 шестерня
@Test void halfPowerHalfSpeed() // tick(0.5) × 40 = одна шестерня, × 39 = ноль
@Test void automatedInsertCapsAtTwoCrafts() // gear: insertInput(iron_plate, 10, true) == 4
@Test void rejectsNonIngredient()
@Test void blocksWhenOutputFull() // outputs = 64 шестерни → прогресс не растёт, powerDemand()==0
@Test void smelterPicksRecipeFromInput() // smelter: вставили raw_iron → через 64 тика iron_plate
@Test void smelterRejectsOtherOreWhileBusy()
@Test void burnerCoalLasts() // Burner(kw(90)): 1 уголь (4 MJ) = 4_000_000/4_500 = 888 тиков с draw()==1.0, на 889-м < 1
@Test void burnerNoFuelZero() { assertEquals(0.0, burner.draw()); }
```

- [ ] **Step 2: Run** `./gradlew test --tests "dev.minetorio.core.machine.*"` → FAIL.
- [ ] **Step 3: Реализовать.**
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat(core): crafting machine and burner`

---

### Task 3: Электросеть (core)

**Files:**
- Create: `core/power/PowerNetwork.java`, `PowerConsumer.java`, `PowerProducer.java`, `PoleGraph.java`
- Test: `src/test/java/dev/minetorio/core/power/PowerNetworkTest.java`, `PoleGraphTest.java`

**Interfaces:**
- Produces:
  - `interface PowerConsumer { long demand(); void supply(double satisfaction); }`
  - `interface PowerProducer { long maxOutput(); void produce(long joules); }`
  - `PowerNetwork { void add(PowerConsumer); void add(PowerProducer); void remove(Object); void tick(); double satisfaction(); }`. Правило: `demand = Σ demand()`, `capacity = Σ maxOutput()`; `satisfaction = demand == 0 ? 1 : min(1, capacity / demand)`; каждый producer получает `produce(round(maxOutput * min(1, demand / capacity)))` (при `capacity == 0` не вызывается); каждый consumer получает `supply(satisfaction)`, но если `capacity == 0`, то `supply(0)`.
  - `record PolePos(int x, int y, int z)`; `PoleGraph(double wireReach, int supplyRadius)`: малый столб `(7.5, 2)` (зона 5×5). Методы: `void addPole(PolePos)`, `void removePole(PolePos)`, `Set<PolePos> neighbors(PolePos)` (евклидово расстояние ≤ reach, авто-соединение, как в Factorio), `int componentOf(PolePos)`, `OptionalInt componentCovering(int x, int y, int z)` (клетка в зоне снабжения столба: |dx|≤r, |dz|≤r, |dy|≤r).

- [ ] **Step 1: Тесты**

```java
@Test void enoughPowerFullSatisfaction()
@Test void halfPower() // demand 100, capacity 50 → 0.5
@Test void noProducersZero() // satisfaction 0 и supply(0), без исключений (Review Focus 4)
@Test void noConsumersOne()
@Test void producersShareLoad() // два двигателя по 45000, demand 45000 → каждый produce(22500)
@Test void polesWithinReachConnect() // (0,0,0) и (7,0,0) — одна компонента; (0,0,0) и (8,0,0) — разные
@Test void removingMiddlePoleSplitsNetwork()
@Test void coverage() // столб (0,0,0): (2,0,-2) покрыт, (3,0,0) нет
```

- [ ] **Step 2: Run** → FAIL. **Step 3:** реализовать. **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat(core): power network and pole graph`

---

### Task 4: Конвейеры (core)

**Files:**
- Create: `core/belt/BeltTier.java`, `LaneItem.java`, `LaneSink.java`, `BeltLane.java`, `BeltShape.java`, `SplitterLogic.java`, `UndergroundLink.java`
- Test: `src/test/java/dev/minetorio/core/belt/*Test.java`

**Interfaces:**
- Produces:
  - `enum BeltTier { YELLOW(0.09375, 4), RED(0.1875, 6), BLUE(0.28125, 8); double speed() /* блоков за тик */; int undergroundMaxGap(); }` (Factorio 1.1: 1.875/3.75/5.625 тайлов в секунду)
  - `record LaneItem(String item, double pos)`, где `pos` в `[0, length]`, 0 — вход полосы.
  - `interface LaneSink { boolean offer(String item, double overflow); }`: `overflow` — насколько предмет зашёл за конец; при `true` предмет передан.
  - `BeltLane(double length)`, `static final double SPACING = 0.25`. Методы: `boolean canInsertAt(double pos)` (до соседей ≥ SPACING), `boolean insertAt(String item, double pos)`, `void tick(double speed, @Nullable LaneSink next)` (двигает от переднего к заднему; передний упирается в конец, если `next` отказал; остальные сжимаются до SPACING), `List<LaneItem> items()` (передний первым), `Optional<LaneItem> takeFirstMatching(Predicate<String>)`, `boolean isEmpty()`.
  - `enum BeltShape { STRAIGHT, CURVE_LEFT, CURVE_RIGHT; static BeltShape of(boolean backInput, boolean leftInput, boolean rightInput); double laneLength(boolean innerLane); }`: изгиб, только если нет входа сзади и ровно один боковой. Длины: прямая 1.0/1.0, изгиб — внутренняя 0.5, внешняя 1.0.
  - `SplitterLogic`: `SplitterLogic()`, `int route(int laneIndex, boolean leftFree, boolean rightFree)` возвращает 0 (левый выход), 1 (правый) или −1 (ждать). Для каждой полосы чередует выходы; если один заблокирован, шлёт в другой.
  - `UndergroundLink`: `static OptionalInt findEntranceDistance(List<Boolean> isMatchingEntranceBehind, int maxGap)`: расстояние до ближайшего непарного входа того же уровня и направления позади (≤ maxGap+1), иначе пусто.

- [ ] **Step 1: Тесты**

```java
@Test void yellowItemCrossesOneBlockIn11Ticks() // insertAt(0) → после 10 tick pos<1, после 11 предмет предложен sink'у
@Test void spacingRespected() // insertAt(0.1) при предмете на 0.0 → false
@Test void blockedLaneCompresses() // sink всегда false, вставлять по 1 предмету в 0 каждые 3 тика → на полосе максимум 4 предмета, интервалы ≥ 0.25 (Review Focus 3: ни один не потерян — сумма в полосе + принятых = вставленных)
@Test void throughputYellowLane() // бесконечная подача, sink всегда true: за 200 тиков (10 s) принято 75±1 предметов (7.5/s на полосу)
@Test void shapeCurveOnlyWithSingleSideInput() // of(false,true,false)==CURVE_LEFT; of(true,true,false)==STRAIGHT; of(false,true,true)==STRAIGHT
@Test void splitterAlternates() // route(0,true,true) ×4 → 0,1,0,1
@Test void splitterFallsBack() // route(0,false,true)==1; route(0,false,false)==-1
@Test void undergroundPairsWithinRange() // YELLOW: вход на расстоянии 5 найден, 6 — нет
```

- [ ] **Step 2: Run** → FAIL. **Step 3:** реализовать. **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat(core): belt lanes, shapes, splitter and underground logic`

---

### Task 5: Манипуляторы (core)

**Files:**
- Create: `core/inserter/InserterType.java`, `InserterLogic.java`, `PickupSource.java`, `DropTarget.java`
- Test: `src/test/java/dev/minetorio/core/inserter/InserterLogicTest.java`

**Interfaces:**
- Produces:
  - `enum InserterType(int cycleTicks, int reach, boolean burner, long powerPerTickJ)`: `BURNER(33,1,true,Units.kw(94.2))`, `BASIC(24,1,false,Units.kw(13.2))`, `LONG(17,2,false,Units.kw(20))`, `FAST(9,1,false,Units.kw(46.6))`. Полный цикл «взять → отнести → вернуться» = `cycleTicks`.
  - `interface PickupSource { Optional<String> peek(Predicate<String> wanted); boolean take(String item); }`
  - `interface DropTarget { boolean wants(String item); boolean accept(String item); }`
  - `InserterLogic(InserterType)`: `void tick(PickupSource src, DropTarget dst, double satisfaction)`, `@Nullable String held()`, `double armProgress()` (0..1 для рендера), `boolean isMoving()`, `long powerDemand()` (powerPerTickJ, пока рука движется, иначе 0). Поведение: пустая рука в позиции забора → `peek(dst::wants)` → `take` → половина цикла к цели → `accept`, иначе ждать с предметом → половина цикла назад. Прогресс за тик = `satisfaction / (cycleTicks / 2)`. Рука держит 1 предмет.

- [ ] **Step 1: Тесты**

```java
@Test void basicMovesOneItemPerCycle() // источник 5 предметов, цель берёт всё: за 24*5 тиков доставлено 5, за 24*5-13 — 4
@Test void waitsHoldingWhenTargetFull() // цель отказывает 100 тиков: held()!=null, источник −1, ничего не потеряно (Review Focus 3); потом цель принимает → доставлен 1
@Test void onlyPicksWanted() // источник [stone, iron_plate], цель хочет iron_plate → берёт iron_plate
@Test void noPowerNoMove() // satisfaction 0 → armProgress не меняется
@Test void idleDrawsNoPower() // пустой источник → powerDemand()==0
```

- [ ] **Step 2: Run** → FAIL. **Step 3:** реализовать. **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat(core): inserter logic`

---

### Task 6: Залежи (core)

**Files:**
- Create: `core/deposit/OreType.java`, `Deposit.java`, `DepositGenerator.java`, `DepositLedger.java`
- Test: `src/test/java/dev/minetorio/core/deposit/*Test.java`

**Interfaces:**
- Produces:
  - `enum OreType(String item, int weight, long baseAmount)`: `IRON("minetorio:iron_ore",40,20_000)`, `COPPER("minetorio:copper_ore",30,20_000)`, `COAL("minecraft:coal",20,15_000)`, `STONE("minecraft:cobblestone",10,10_000)`.
  - `record Deposit(long id, OreType type, int centerX, int centerZ, int radius, long initialAmount) { boolean contains(int x, int z); }`: круг по XZ, высота не важна.
  - `DepositGenerator(long worldSeed, int spawnX, int spawnZ)`, константы `REGION = 64`, `CHANCE = 0.35`, `MIN_RADIUS = 6`, `MAX_RADIUS = 14`, `SPAWN_EXCLUSION = 80`. Детерминированно из `seed` и координат региона: в регионе с вероятностью CHANCE одна залежь со случайным центром (с отступом `MAX_RADIUS` от края региона, чтобы не перекрываться), типом по весам и радиусом. `initialAmount = baseAmount * (1 + dist(center, spawn) / 500)`. Регионы, центр которых ближе `SPAWN_EXCLUSION` к спавну, пусты, кроме **стартовых**: IRON в `spawn+(48,0)`, COPPER `(-48,0)`, COAL `(0,48)`, STONE `(0,-48)`, радиус 10, `baseAmount`, id −1..−4.
    Методы: `Optional<Deposit> depositAt(int x, int z)`, `List<Deposit> inRegion(int rx, int rz)`, `Optional<Deposit> nearest(int x, int z, int maxDistance)`.
  - `DepositLedger { long remaining(Deposit d); long mine(Deposit d, long amount); Map<Long,Long> minedById(); void load(Map<Long,Long>); }`: `mine` возвращает добытое, ≤ remaining, никогда не уходит в минус.

- [ ] **Step 1: Тесты**

```java
@Test void deterministic() // два генератора с одним seed дают одинаковые inRegion для 100 регионов
@Test void startingDepositsExist() // depositAt(spawn+48, spawnZ) — IRON; остальные три на местах
@Test void spawnAreaHasOnlyStartingDeposits()
@Test void richerFarAway() // initialAmount у залежи на 2000 блоках > у залежи на 200
@Test void densityReasonable() // в 400 регионах 25–45% содержат залежь
@Test void ledgerNeverNegative() // remaining 3, mine(5)==3, потом mine(1)==0, remaining==0
@Test void nearestFindsStartingIron() // nearest(spawnX+30, spawnZ, 256) → IRON
```

- [ ] **Step 2: Run** → FAIL. **Step 3:** реализовать. **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat(core): deposit generation and ledger`

---

### Task 7: Исследования (core)

**Files:**
- Create: `core/research/Technology.java`, `TechTree.java`, `ResearchState.java`, `LabLogic.java`
- Modify: `core/data/Phase1Data.java` (добавить `techTree()`)
- Test: `src/test/java/dev/minetorio/core/research/*Test.java`

**Interfaces:**
- Produces:
  - `record Technology(String id, List<String> prerequisites, Map<String,Integer> packsPerUnit, int units, int ticksPerUnit, List<String> unlocksRecipes)`
  - `TechTree(Collection<Technology>)`: `Optional<Technology> get(String id)`, `Collection<Technology> all()`
  - `ResearchState(TechTree, Set<String> startRecipes)`: `boolean isRecipeUnlocked(String recipeId)` (плавка всегда true, это проверяет вызывающий по категории), `boolean isResearched(String techId)`, `boolean canResearch(String techId)`, `void setCurrent(String techId)` (`IllegalStateException`, если нельзя), `Optional<Technology> current()`, `int unitsDone()`, `void completeUnit()` (при `units` достигнутых: technology исследована, рецепты открыты, current очищен), `Set<String> researched()`, `void load(Set<String> researched, @Nullable String current, int unitsDone)`. Смена текущего исследования сохраняет прогресс старого (`Map<String,Integer> partialProgress`), как в Factorio.
  - `LabLogic(double speed, long powerPerTickJ)`: лаборатория `(1.0, Units.kw(60))`. `int insertPack(String item, int count, boolean automated)` (автоматически до 2 штук каждого пакета), `void tick(ResearchState s, double satisfaction)` (если есть current и все пакеты на юнит: в начале юнита списывает по одному набору, прогресс += `satisfaction*speed/ticksPerUnit`, по завершении `s.completeUnit()`), `long powerDemand()`, `Map<String,Integer> packs()`.

**Дерево (Factorio 1.1, R = automation_science_pack, G = logistic_science_pack):**
| id | пререквизиты | пакеты на юнит | юниты | s/юнит | открывает |
|---|---|---|---|---|---|
| automation | — | R | 10 | 10 | assembling_machine_1, long_handed_inserter |
| logistics | — | R | 20 | 15 | underground_belt, splitter |
| fast_inserter | automation | R | 30 | 15 | fast_inserter |
| logistic_science_pack | — | R | 75 | 5 | logistic_science_pack |
| automation_2 | automation, logistic_science_pack | R+G | 40 | 15 | assembling_machine_2 |
| logistics_2 | logistics, logistic_science_pack | R+G | 200 | 30 | fast_transport_belt, fast_underground_belt, fast_splitter |

- [ ] **Step 1: Тесты**

```java
@Test void cannotResearchWithoutPrereqs() // canResearch("automation_2")==false в начале
@Test void completingUnlocksRecipes() // setCurrent("automation"), 10×completeUnit → isRecipeUnlocked("assembling_machine_1")
@Test void switchingKeepsPartialProgress()
@Test void labConsumesPacksPerUnit() // 1 lab, automation: вставлено 2 R → 400 тиков → unitsDone==2, packs пусто
@Test void labWaitsWithoutPacks()
@Test void allTreeRecipesExist() // каждый unlocksRecipes есть в Phase1Data.recipes()
@Test void everyLockedRecipeHasTech() // каждый рецепт не из startRecipes открывается какой-то технологией, кроме express_*
```

- [ ] **Step 2: Run** → FAIL. **Step 3:** реализовать. **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat(core): research tree, state and labs`

---

### Task 8: Ручной крафт с очередью (core)

**Files:**
- Create: `core/handcraft/HandcraftQueue.java`
- Test: `src/test/java/dev/minetorio/core/handcraft/HandcraftQueueTest.java`

**Interfaces:**
- Consumes: `RecipeBook`, `Inventory`, `Predicate<String> isUnlocked` (из `ResearchState::isRecipeUnlocked`).
- Produces:
  - `HandcraftQueue(RecipeBook book)`
  - `int maxCraftable(String recipeId, Inventory inv, Predicate<String> isUnlocked)`: сколько раз можно скрафтить с учётом промежуточных деталей (для кнопок GUI).
  - `boolean enqueue(String recipeId, int times, Inventory inv, Predicate<String> isUnlocked)`: только CRAFTING и только открытые. Недостающие входы, у которых есть открытый CRAFTING-рецепт, раскрываются рекурсивно (сначала используется то, что есть в инвентаре). Все сырьевые входы **сразу списываются** из инвентаря. Если не хватает, ничего не меняется и возвращается `false`.
  - `void cancel(int entryIndex, Inventory inv)`: возвращает в инвентарь всё зарезервированное этой записью (включая её подкрафты).
  - `void tick(Inventory inv)`: скорость 1.0. Шаги выполняются по порядку (подкрафты перед родителем). Промежуточные результаты идут в резерв записи, в инвентарь попадает только итоговый результат.
  - `List<Entry> entries()`, `record Entry(String recipeId, int times, double progress)`.
  - `Map<String,Object> save()` / `static HandcraftQueue load(RecipeBook, Map<String,Object>)`: простая структура (строки, числа, списки), чтобы `CoreNbt` её сериализовал.

- [ ] **Step 1: Тесты**

```java
@Test void craftsGearFromPlates() // 2 iron_plate → enqueue(gear) → plates списаны сразу → 10 тиков → 1 gear
@Test void autoCraftsIntermediates() // инвентарь: 4 iron_plate, 2 copper_plate → enqueue("inserter",1) == true, инвентарь пуст.
// Подкрафты: 2× copper_cable (20 тиков, 4 кабеля), circuit (10), gear (10), inserter (10) → через 49 тиков inserter нет, через 50 — 1 inserter и 1 лишний copper_cable в инвентаре
@Test void failsAtomically() // не хватает → false, инвентарь не тронут
@Test void lockedRecipeRejected()
@Test void smeltingRejected()
@Test void cancelRefundsEverything() // enqueue, 3 тика, cancel → инвентарь как до enqueue
@Test void saveLoadRoundTrip() // в середине крафта save→load → оставшееся время то же
```

- [ ] **Step 2: Run** → FAIL. **Step 3:** реализовать. **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat(core): handcraft queue with intermediate resolution`

---

### Task 9: Жидкости и пар (core)

**Files:**
- Create: `core/fluid/FluidBox.java`, `FluidFlow.java`, `SteamPlant.java`
- Test: `src/test/java/dev/minetorio/core/fluid/*Test.java`

**Interfaces:**
- Produces:
  - `FluidBox(double capacity)`: `@Nullable String fluid()`, `double amount()`, `double insert(String fluid, double amount)`, `double extract(double amount)`. Пустой бокс сбрасывает fluid в null. Жидкости: `"water"`, `"steam"`.
  - `FluidFlow.flow(FluidBox a, FluidBox b)`: если жидкости совместимы (одна или пустой бокс), переносит `0.4 * (a.fill - b.fill) * min(capA, capB)` в сторону меньшего заполнения (`fill = amount/capacity`).
  - Ёмкости: труба 100, насос 100, бойлер вода 200 / пар 200, паровой двигатель 200.
  - `SteamPlant.OFFSHORE_WATER_PER_TICK = 60.0` (1200/s).
  - `SteamPlant.Boiler(Burner burner)`: `FluidBox water()`, `FluidBox steam()`, `void tick()`: если есть вода и место под пар, `burner` (мощность `Units.kw(1800)`) отдаёт долю `f`, конвертирует `3.0 * f` воды в столько же пара.
  - `SteamPlant.Engine implements PowerProducer`: `FluidBox steam()`, `maxOutput() = min(Units.kw(900), (long)(steam.amount() * 30_000))`, `produce(J)` списывает `J / 30_000.0` пара.

- [ ] **Step 1: Тесты**

```java
@Test void flowEqualizes() // 100 и 0 → через 50 вызовов разница < 1
@Test void noMixing() // вода в паровой бокс не течёт
@Test void boilerFeedsTwoEngines() // бойлер с углём и водой 60/тик → два двигателя, demand 2×45000 → satisfaction 1 на 200 тиках (после 20 тиков прогрева)
@Test void engineWithoutSteamProducesNothing() // maxOutput()==0
@Test void boilerStopsWithoutFuel()
```

- [ ] **Step 2: Run** → FAIL. **Step 3:** реализовать. **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat(core): fluid boxes, boiler and steam engine`

---

### Task 10: Регистрация, многоблочные машины, сохранение core-состояния

**Files:**
- Create: `content/registry/*`, `content/multiblock/*`, `content/nbt/CoreNbt.java`
- Create: `src/main/java/dev/minetorio/gametest/MultiblockGameTest.java`
- Modify: `Minetorio.java`
- Create: `assets/minetorio/lang/en_us.json`, `ru_ru.json`

**Interfaces:**
- Produces:
  - `ModItems`: все предметы из таблиц задачи 1 (материалы и машины) + `iron_ore`, `copper_ore`, `iron_plate`, `copper_plate`, `stone_brick`, `scanner`, `express_*`. Предметы машин — `BlockItem`.
  - `ModCreativeTab.MINETORIO` — вкладка со всеми предметами.
  - `record MultiblockShape(int width, int depth, int height)`, размеры (ширина × глубина × высота, ширина поперёк facing): stone_furnace 2×2×2, burner_mining_drill 2×2×2, electric_mining_drill 3×3×2, assembling_machine_1/2 3×3×2, lab 3×3×2, boiler 3×2×2, steam_engine 3×5×2, offshore_pump 1×2×1, splitter (все уровни) 2×1×1.
  - `abstract class MultiblockControllerBlock extends BaseEntityBlock` с `FACING` (горизонтальный), `abstract MultiblockShape shape()`. `getStateForPlacement` возвращает null, если в объёме есть незаменяемые блоки. `onPlace` ставит части. Контроллер в левом переднем нижнем углу относительно facing. Утилита `static List<BlockPos> footprint(BlockPos origin, Direction facing, MultiblockShape)`.
  - `MultiblockPartBlock`: невидимый блок (`RenderShape.INVISIBLE`) с хитбоксом, block entity `MultiblockPartBlockEntity { BlockPos controllerOffset; }`. Клик, ломание и capability делегируются контроллеру. Ломание любой части ломает весь блок. Выпадает 1 предмет машины + содержимое (Review Focus 2).
  - `CoreNbt`: `CompoundTag write(Map<String,Object>)`, `Map<String,Object> read(CompoundTag)`, хелперы `writeCounts(Map<String,Integer>)`/`readCounts`, `writeLane(BeltLane)`/`readLane`, `writeFluid(FluidBox)`/`readFluid`.

- [ ] **Step 1: GameTest'ы** (тестовый блок — `stone_furnace`, его контроллер в этой задаче пока без логики)

```java
@GameTest(template="empty16") placesAllParts // поставить stone_furnace в (2,1,2) facing NORTH → 8 блоков (2×2×2) заняты, 7 из них part с корректным controllerOffset
@GameTest(template="empty16") refusesWhenObstructed // камень в зоне → установка не происходит
@GameTest(template="empty16") breakingPartBreaksAllDropsOne // destroyBlock(part, true) → все 8 блоков воздух, ровно 1 ItemEntity stone_furnace
```
Плюс JUnit `CoreNbtTest`: `BeltLane` с 3 предметами → write → read → `items()` равны.

- [ ] **Step 2: Run** `./gradlew test runGameTestServer` → FAIL.
- [ ] **Step 3: Реализовать** регистрации, многоблок и CoreNbt. Модели пока заглушки: контроллер рендерит однотонный куб на весь объём (JSON-модель с элементом до 32 px покрывает 2–3 блока; для steam_engine 5 блоков — `BlockEntityRenderer` с масштабированной коробкой). Lang-файлы: название каждого предмета на английском и русском.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat: registries, multiblock framework, core NBT bridge`

---

### Task 11: Конвейеры в мире

**Files:**
- Create: `content/belt/BeltBlock.java`, `BeltBlockEntity.java`, `UndergroundBeltBlock.java`, `UndergroundBeltBlockEntity.java`, `SplitterBlock.java`, `SplitterBlockEntity.java`, `BeltGeometry.java`
- Create: `client/belt/BeltRenderer.java` (+ варианты для подземных и разделителя)
- Create: `gametest/BeltGameTest.java`

**Interfaces:**
- Consumes: `BeltTier`, `BeltLane`, `BeltShape`, `SplitterLogic`, `UndergroundLink`, `CoreNbt`, `MultiblockControllerBlock` (для разделителя).
- Produces:
  - Блоки `transport_belt`, `fast_transport_belt`, `express_transport_belt` (+ `*_underground_belt`, `*_splitter`). Высота ленты 2 px, на неё можно наступать. Сущность на ленте едет со скоростью ленты (Factorio). Facing = горизонтальное направление взгляда игрока.
  - `BeltBlockEntity`: `BeltLane lane(LaneSide side)`, `enum LaneSide { LEFT, RIGHT }` относительно направления ленты. Серверный тик: `lane.tick(tier.speed(), sinkTo(front))`. Клиентский тик двигает полосы локально для плавности. Сервер шлёт полное состояние полос при изменении состава (вход/выход предмета).
  - `BeltGeometry.sinkTo(...)`: если впереди лента и её задняя сторона смотрит на нас, полосы стыкуются один к одному (с учётом изгиба). Если впереди лента боком, обе наши полосы вливаются в её **ближнюю** к нам полосу в позиции 0.5 (side-loading). Если впереди контейнер с `IItemHandler`, предмет вставляется в него (лента сама кладёт в сундук). Иначе предмет стоит.
  - `BeltGeometry.farLane(Direction beltFacing, Direction fromSide) -> LaneSide`: для манипуляторов.
  - Подземные: при установке, если `UndergroundLink` находит позади непарный вход того же уровня и направления, блок становится выходом, иначе входом. Вход и выход хранят друг друга. Внутренние полосы имеют длину `distance`. Side-loading во вход заполняет только полосу с той же стороны, что и подающая лента.
  - Разделитель 2×1: две входные и две выходные позиции, по одному `SplitterLogic` на полосу.
  - `BeltRenderer`: рисует `ItemStack` каждого `LaneItem` (масштаб 0.4) с интерполяцией `partialTick`; полосы смещены на ±0.25 от центра.

- [ ] **Step 1: GameTest'ы**

```java
yellowItemTravelsTenBelts // 10 жёлтых лент в ряд → сундук; вставить 1 iron_plate в начало → в сундуке к тику 112 (ceil(10/0.09375)+5), не раньше 100
redFasterThanYellow // то же с красными, к тику 59
sideLoadFillsNearLane // лента A вливается сбоку в ленту B → предметы только в ближней полосе B
curveFormsAutomatically // лента без входа сзади с одним боковым → shape CURVE
undergroundCarriesItems // вход, 4 блока камня, выход → предмет прошёл
splitterAlternates // 4 предмета в одну полосу → по 2 в каждом выходе
fullChestStopsBelt // сундук заполнен → предметы стоят на ленте, ни один не пропал (Review Focus 3)
beltNbtRoundTrip // BE с 3 предметами: saveWithFullMetadata → новый BE.loadWithComponents → те же предметы и позиции (Review Focus 1)
breakingBeltDropsItems // сломать ленту с 3 предметами → 1 лента + 3 предмета на земле
```

- [ ] **Step 2: Run** `./gradlew runGameTestServer` → FAIL. **Step 3:** реализовать. **Step 4: Run** → PASS.
- [ ] **Step 5: Проверка вручную в `runClient`:** предметы плавно едут, изгибы рисуются, нет мерцания при 100 лентах.
- [ ] **Step 6: Commit** — `feat: transport belts, underground belts and splitters`

---

### Task 12: Манипуляторы в мире

**Files:**
- Create: `content/inserter/InserterBlock.java`, `InserterBlockEntity.java`, `InserterEndpoints.java`
- Create: `client/inserter/InserterRenderer.java`
- Create: `gametest/InserterGameTest.java`

**Interfaces:**
- Consumes: `InserterLogic`, `InserterType`, `Burner`, `BeltBlockEntity`/`BeltGeometry.farLane`, `MachineEndpoint` (интерфейс ниже, его реализуют машины в задачах 13–16).
- Produces:
  - Блоки `burner_inserter`, `inserter`, `long_handed_inserter`, `fast_inserter`. `FACING` = направление сброса (взгляд игрока), забор с противоположной стороны на расстоянии `reach`.
  - `interface MachineEndpoint { PickupSource asSource(); DropTarget asTarget(); }`: машина отдаёт только выходы, принимает входы, топливо и пакеты с лимитами `automated=true`.
  - `InserterEndpoints.source(Level, BlockPos)` / `target(Level, BlockPos, Direction from)`: машина (через part → контроллер) → `MachineEndpoint`; лента → забор переднего подходящего предмета с любой полосы, сброс в дальнюю полосу на позицию 0.5 через `canInsertAt`; иначе `IItemHandler` (ванильные сундуки, воронки).
  - Burner-манипулятор: свой `Burner`. Если несёт топливо, а его собственный слот пуст, оставляет 1 штуку себе.
  - Электрические манипуляторы — `PowerConsumer` (подключение в задаче 14; до неё в тестах satisfaction = 1 через тестовый хук `setTestPower(double)`).
  - Рендер: руку из двух сегментов поворачивает `armProgress()`, предмет в руке рисуется.

- [ ] **Step 1: GameTest'ы**

```java
basicChestToChest // сундук → inserter → сундук, 5 предметов за ≤ 5*24+10 тиков
burnerRefuelsItself // burner_inserter без топлива с 1 углём в руке переносит уголь и продолжает работать
beltToChestTakesFromBothLanes
chestToBeltDropsOnFarLane
longHandedReachesTwo
fullTargetNoLoss // целевой сундук полон → предмет в руке, источник −1, сумма предметов сохранена (Review Focus 3)
```

- [ ] **Step 2: Run** → FAIL. **Step 3:** реализовать. **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat: inserters`

---

### Task 13: Залежи, сканер и буры в мире

**Files:**
- Create: `content/deposit/DepositSavedData.java`, `Deposits.java`, `ScannerItem.java`
- Create: `content/machine/BurnerDrillBlock(+Entity).java`, `ElectricDrillBlock(+Entity).java`, `DrillOutput.java`
- Create: `gametest/DrillGameTest.java`

**Interfaces:**
- Consumes: `DepositGenerator`, `DepositLedger`, `Burner`, `MultiblockControllerBlock`, `InserterEndpoints.target` (бур выдаёт предмет так же, как манипулятор кладёт).
- Produces:
  - `Deposits.get(ServerLevel) -> Deposits`: генератор (seed мира, точка спавна) + `DepositSavedData` с `DepositLedger` (имя `minetorio_deposits`). Только Overworld; в других измерениях `depositAt` пуст.
  - `Deposits.depositUnder(int minX, int minZ, int maxX, int maxZ) -> Optional<Deposit>`: первая залежь, содержащая центр зоны, иначе любую клетку зоны.
  - Бур: зона добычи — burner 2×2 (под собой), electric 5×5 (по центру). Скорость добычи burner 0.25/s, electric 0.5/s (время добычи руды 1 s): прогресс за тик = `rate/20 * satisfaction`. При ≥1 вызывает `ledger.mine(d,1)`, кладёт `type.item()` в клетку перед выходной стороной (центр передней грани): лента, машина или контейнер. Если некуда, ждёт с предметом во внутреннем буфере (1 шт.). При `remaining == 0` бур стоит, в GUI/подсказке «Залежь исчерпана». Burner-бур: `Burner(Units.kw(150))`; электрический: `PowerConsumer` на `Units.kw(90)`.
  - `ScannerItem`: правый клик показывает в action bar:
    - залежь под игроком: `«Залежь: железная руда — осталось 12 345»`;
    - иначе ближайшая в радиусе 256: `«Здесь пусто. Ближайшая: медная руда, 57 блоков на СВ»` (8 направлений);
    - иначе `«Залежей в радиусе 256 блоков нет»`.
    Ключи перевода `minetorio.scanner.*` на en и ru.

- [ ] **Step 1: GameTest'ы** (залежь подкладывается через тестовый хук `Deposits.overrideForTest(Deposit, remaining)`, т.к. пустой шаблон стоит не над залежью)

```java
burnerDrillMinesIntoChest // залежь IRON, бур с 1 углём, сундук перед выходом → через 400 тиков в сундуке 5 iron_ore (±1)
depletionStopsDrill // remaining 3 → ровно 3 руды, потом стоит, remaining==0
noDepositNoOutput
drillWaitsWhenBlocked // сундук полон → руда в буфере, ledger списал ровно на 1 больше, чем в сундуке
electricDrillNeedsPower // без сети ничего не добывает (после задачи 14 тест обновляется на реальный столб)
scannerMessages // мок-игрок над залежью → сообщение содержит "iron" и остаток
```

- [ ] **Step 2: Run** → FAIL. **Step 3:** реализовать. **Step 4: Run** → PASS.
- [ ] **Step 5: Проверка вручную:** новый мир, сканер находит 4 стартовые залежи у спавна.
- [ ] **Step 6: Commit** — `feat: deposits, scanner and mining drills`

---

### Task 14: Энергия в мире: насос, трубы, бойлер, двигатель, столбы

**Files:**
- Create: `content/power/PowerGridManager.java`, `ElectricPoleBlock(+Entity).java`, `PipeBlock(+Entity).java`, `OffshorePumpBlock(+Entity).java`, `BoilerBlock(+Entity).java`, `SteamEngineBlock(+Entity).java`, `ElectricConsumerHelper.java`
- Create: `client/power/WireRenderer.java`
- Create: `gametest/PowerGameTest.java`

**Interfaces:**
- Consumes: `PowerNetwork`, `PoleGraph`, `FluidBox`, `FluidFlow`, `SteamPlant`, `Burner`, `MultiblockControllerBlock`.
- Produces:
  - `PowerGridManager.get(ServerLevel)`: живёт в памяти, пересобирается из загруженных BE (без SavedData). `registerPole/unregisterPole(BlockPos)`, `registerConsumer(BlockPos, AABB footprint, PowerConsumer)`, `registerProducer(BlockPos, AABB, PowerProducer)`, `unregister(BlockPos)`. Потребитель/генератор попадает в сеть столба, если любая клетка его footprint в зоне снабжения. На `LevelTickEvent.Post`: если граф изменился, пересобрать компоненты; затем `PowerNetwork.tick()` для каждой. Потребители используют satisfaction прошлого тика.
  - `ElectricConsumerHelper`: общий код для BE электрических машин: регистрация в `onLoad`, снятие в `setRemoved`/`onChunkUnloaded`, `double satisfaction()`. Вне зоны столбов satisfaction 0 (Review Focus 4).
  - `small_electric_pole`: провода рендерятся линиями между соединёнными столбами (`WireRenderer`, клиентская копия соединений из update-тега столба).
  - `offshore_pump` 1×2: ставится только задней частью к блоку воды-источника; даёт 60 воды/тик в свой выход.
  - `pipe`: соединяется со всеми соседними трубами и fluid-портами машин; `FluidFlow.flow` с каждым соседом раз в тик.
  - `boiler` 3×2: вход и выход воды по бокам, выход пара сзади по центру, топливо — через манипулятор или GUI-слот.
  - `steam_engine` 3×5: порты пара на обоих торцах по центру (двигатели ставятся цепочкой, как в Factorio); `PowerProducer`.
  - Fluid-порты машин задаются списком `(локальная клетка, направление)` в каждом контроллере.

- [ ] **Step 1: GameTest'ы**

```java
steamSetupPowersConsumer // вода-источник → насос → бойлер (уголь) → двигатель → столб; тестовый потребитель 900 kW в зоне → через 100 тиков satisfaction 1.0
oneEngineTwoLoads // потребление 1800 kW, 1 двигатель → satisfaction 0.5 ±0.02
noFuelNoPower // бойлер без угля → satisfaction 0, потребитель стоит
consumerOutsidePoleZero // потребитель в 4 блоках от столба → 0 (Review Focus 4)
breakingPoleSplitsGrid
pumpNeedsWater // насос не у воды → не ставится
boilerNbtRoundTrip // вода, пар и топливо переживают save/load (Review Focus 1)
```

- [ ] **Step 2: Run** → FAIL. **Step 3:** реализовать. Обновить `electricDrillNeedsPower` из задачи 13 и электрические манипуляторы из задачи 12 на `ElectricConsumerHelper`; убрать тестовый хук `setTestPower` из продового пути (оставить только в GameTest-утилите).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat: steam power, pipes and electric poles`

---

### Task 15: Каменная печь и сборочные машины

**Files:**
- Create: `content/machine/StoneFurnaceBlock(+Entity).java`, `AssemblerBlock(+Entity).java`, `MachineMenu.java`, `AssemblerMenu.java`
- Create: `client/machine/StoneFurnaceScreen.java`, `AssemblerScreen.java`
- Create: `content/registry/ModPayloads.java` (+ `SelectRecipePayload`)
- Create: `gametest/MachineGameTest.java`

**Interfaces:**
- Consumes: `CraftingMachine`, `Burner`, `ElectricConsumerHelper`, `MachineEndpoint`, `ResearchState` (через `ResearchSavedData.get(server)` из задачи 16; до неё все рецепты считаются открытыми через временную заглушку `RecipeLocks.isUnlocked(server, id) -> true`).
- Produces:
  - `stone_furnace` 2×2: `CraftingMachine.smelter(1.0, Units.kw(90), book)` + `Burner(Units.kw(90))`. GUI: слот топлива, вход, выход, полоса прогресса. Реализует `MachineEndpoint`.
  - `assembling_machine_1/2` 3×3: `CraftingMachine(0.5 / 0.75, ...)`, `PowerConsumer`. GUI: список открытых CRAFTING-рецептов с иконками → клик шлёт `SelectRecipePayload(BlockPos, String recipeId)`. Сервер проверяет дистанцию, открытость и категорию. Слоты входов (по рецепту), выход, прогресс, индикатор питания (зелёный/жёлтый/красный по satisfaction).
  - `RecipeLocks.isUnlocked(MinecraftServer, String recipeId) -> boolean` — единая точка проверки (в задаче 16 подключается к `ResearchState`).

- [ ] **Step 1: GameTest'ы**

```java
furnaceSmeltsRawIron // 1 raw_iron + 1 уголь → через 64 тика 1 iron_plate (бутстрап из спеки)
furnaceSmeltsDepositOre // iron_ore → iron_plate
assemblerMakesGearsAtFullPower // assembler_1 + сеть, рецепт gear, манипулятор подаёт пластины → 1 шестерня за 20 тиков
assemblerHalfPowerHalfSpeed
assemblerNbtRoundTrip // рецепт, входы, прогресс сохраняются (Review Focus 1)
selectRecipePayloadRejectsSmelting
fullFactoryLine // залежь → бур → лента → манипулятор → печь → манипулятор → сборщик(gear) → сундук: шестерни появляются (интеграционный)
```

- [ ] **Step 2: Run** → FAIL. **Step 3:** реализовать. **Step 4: Run** → PASS.
- [ ] **Step 5: Проверка вручную:** GUI сборщика, выбор рецепта, индикатор питания.
- [ ] **Step 6: Commit** — `feat: stone furnace and assembling machines`

---

### Task 16: Лаборатория и исследования в мире

**Files:**
- Create: `content/research/ResearchSavedData.java`, `LabBlock(+Entity).java`, `RecipeLocks.java` (заменить заглушку), `SelectResearchPayload.java`, `ResearchSyncPayload.java`
- Create: `client/research/ResearchScreen.java`, `client/ModKeys.java`
- Create: `gametest/ResearchGameTest.java`

**Interfaces:**
- Consumes: `ResearchState`, `LabLogic`, `TechTree`, `ElectricConsumerHelper`, `MachineEndpoint`.
- Produces:
  - `ResearchSavedData.get(MinecraftServer)`: один `ResearchState` на весь мир, хранится в Overworld (`minetorio_research`). Общий для всех игроков.
  - `lab` 3×3: `LabLogic(1.0, Units.kw(60))`, `PowerConsumer`, `MachineEndpoint` (принимает пакеты).
  - Клавиша **Y** (`key.minetorio.research`) открывает экран дерева технологий: узлы (иконка, название, стоимость), состояние (исследовано / доступно / закрыто), текущее с прогрессом. Клик по доступному шлёт `SelectResearchPayload(String techId)`.
  - `ResearchSyncPayload(Set<String> researched, String current, int unitsDone)` рассылается всем игрокам при изменении и при входе игрока.
  - По завершении исследования: сообщение в чат всем `«Исследование завершено: Автоматизация»` и звук.

- [ ] **Step 1: GameTest'ы**

```java
labResearchesAutomation // setCurrent(automation), лаборатория с питанием и 10 R → через 10*200 + запас тиков automation исследована, assembling_machine_1 открыт
labsShareProgress // 2 лаборатории → примерно вдвое быстрее
assemblerRejectsLockedRecipe // до исследования SelectRecipePayload(assembling_machine_1) отклонён
researchPersists // ResearchSavedData save → load → researched тот же (Review Focus 1)
```

- [ ] **Step 2: Run** → FAIL. **Step 3:** реализовать. **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat: labs, research tree screen and recipe locks`

---

### Task 17: Ручной крафт в мире

**Files:**
- Create: `content/handcraft/HandcraftAttachment.java`, `PlayerInventoryAdapter.java`, `HandcraftPayloads.java` (`Enqueue`, `Cancel`, `QueueSync`), `HandcraftTicker.java`
- Create: `client/handcraft/HandcraftScreen.java`, `client/handcraft/QueueHud.java`
- Modify: `content/registry/ModAttachments.java`, `client/ModKeys.java`
- Create: `gametest/HandcraftGameTest.java`

**Interfaces:**
- Consumes: `HandcraftQueue`, `RecipeLocks`, `Inventory`, `CoreNbt`.
- Produces:
  - `ModAttachments.HANDCRAFT`: `AttachmentType<HandcraftAttachment>` на игроке, сериализуется через `HandcraftQueue.save()/load()` и `CoreNbt`. При смерти очередь отменяется, ингредиенты выпадают вместе с инвентарём.
  - `PlayerInventoryAdapter implements Inventory`: поверх `Player.getInventory()`. Если `add` не влезает, остаток выпадает у ног.
  - `HandcraftTicker`: на `PlayerTickEvent.Post` (сервер) вызывает `queue.tick(adapter)`, при изменении шлёт `QueueSync`.
  - Клавиша **R** (`key.minetorio.handcraft`) открывает экран: сетка открытых CRAFTING-рецептов по группам (логистика, производство, промежуточные, наука). Число на иконке — `maxCraftable`. ЛКМ ×1, ПКМ ×5, Shift+ЛКМ — максимум (как в Factorio). Подсказка показывает входы и время.
  - `QueueHud`: слева внизу иконки очереди с прогрессом первой; клик по иконке в экране отменяет запись.

- [ ] **Step 1: GameTest'ы** (игроки — `helper.makeMockServerPlayerInLevel()`)

```java
handcraftGear // 2 iron_plate в инвентаре → Enqueue(gear,1) → через 10 тиков 1 gear
handcraftResolvesIntermediates // пластины → Enqueue(inserter) → inserter в инвентаре
twoPlayersSeparateQueues // у каждого игрока своя очередь, крафт одного не трогает инвентарь другого (Review Focus 5)
researchSharedBetweenPlayers // игрок A исследовал automation → игрок B может поставить assembling_machine_1 в очередь (Review Focus 5)
queueSurvivesRelog // attachment save → load → оставшиеся записи и прогресс (Review Focus 1)
```

- [ ] **Step 2: Run** → FAIL. **Step 3:** реализовать. **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat: Factorio-style handcrafting with queue`

---

### Task 18: Внешний вид и сквозная проверка

**Files:**
- Modify: `assets/minetorio/textures/**`, `models/**`, `blockstates/**`, `lang/*.json`
- Create: `docs/playtest-phase1.md`

**Interfaces:**
- Consumes: все блоки и предметы.

- [ ] **Step 1: Текстуры и модели.** Стиль как у Create: объёмные многоэлементные модели (трубы, валы, шестерни, корпуса) вместо кубов; текстуры 16×16 в ванильном стиле, узнаваемые формы машин Factorio. Стартовый набор текстур уже лежит в `art/` (генератор `art/gen.py`). У каждого предмета иконка, у каждой машины объёмная модель на весь footprint (через общий BlockEntityRenderer контроллера, правило R19), у лент анимированная текстура полотна, скорость анимации соответствует уровню. Движущиеся части через BER: шестерни сборщика крутятся во время крафта, бур вращается при добыче, поршень и маховик парового двигателя двигаются под нагрузкой, лаборатория светится во время исследования. Анимация зависит от того, работает ли машина (синхронизируется флагом «работает»).
- [ ] **Step 2: Lang.** Все ключи есть в обоих файлах. Добавить JUnit-тест `LangCompletenessTest`: множества ключей `en_us.json` и `ru_ru.json` совпадают, и каждый зарегистрированный предмет (`minetorio:*` из списка в `ModItems`, вынести список id в константу) имеет `item.minetorio.<id>` или `block.minetorio.<id>`.
- [ ] **Step 3: Run** `./gradlew test runGameTestServer` → всё PASS.
- [ ] **Step 4: Ручной плейтест** по `docs/playtest-phase1.md` в `runClient` (новый мир, выживание):
  1. Добыть киркой ванильную железную и медную руду, булыжник, уголь.
  2. R → скрафтить каменную печь, переплавить raw_iron в iron_plate.
  3. Скрафтить сканер, найти стартовую железную залежь, поставить твердотопливный бур, ленту, печь.
  4. Поставить насос, бойлер, двигатель, столбы, электробур.
  5. Скрафтить лабораторию, автоматизировать красную науку, исследовать automation.
  6. Сборщики делают шестерни и красную науку, исследовать logistic_science_pack, автоматизировать зелёную науку, исследовать logistics_2 и получить красные ленты.
  7. Выйти из мира и зайти: всё на месте, ленты едут.
  8. Запустить выделенный сервер (`./gradlew runServer`), зайти клиентом: ленты, GUI и исследования работают.
  Записать в документ, что пройдено и какие баги нашлись.
- [ ] **Step 5: Commit** — `feat: phase 1 art, translations and playtest checklist`

---

### Task 19: Демо-мир со всеми механиками

Карта-витрина, где за пару минут можно обойти и посмотреть все механики этапа 1 в работе.

**Files:**
- Create: `content/demo/DemoCommand.java`, `content/demo/DemoBuilder.java`
- Create: `gametest/DemoGameTest.java`
- Create: `docs/demo-world.md`

**Interfaces:**
- Consumes: все блоки и машины задач 10–17, `Deposits.overrideForTest` (или аналог для демо-залежей), `ResearchSavedData`.
- Produces:
  - Команда `/minetorio demo` (уровень прав 2): строит витрину вокруг игрока на ровной площадке (по умолчанию 96×96, очищает её до камня/травы) и телепортирует игрока ко входу. Работает в любом мире; для лучшего вида — суперплоский творческий мир.
  - Витрина — дорожка с «остановками», у каждой табличка на en/ru (язык по клиенту через ключи перевода):
    1. **Ленты:** жёлтая/красная/синяя рядом (видна разница скоростей), изгиб, боковая подача, подземная лента под стеной, разделитель, сундук-источник с бесконечной подачей (творческий источник, только для демо).
    2. **Манипуляторы:** все четыре вида переносят предметы между сундуками и лентами.
    3. **Добыча:** залежь железа (демо-залежь с большим запасом) + твердотопливный бур + электробур → лента; рядом сканер в рамке и табличка «возьми сканер и кликни по земле».
    4. **Плавка:** бур → лента → манипулятор → каменная печь → манипулятор → сундук.
    5. **Энергия:** вода → насос → бойлер → 2 паровых двигателя → столбы с проводами → питание всей витрины.
    6. **Сборка:** сборщики делают шестерни и красную науку из пластин с ленты.
    7. **Исследования:** лаборатории потребляют красную науку; текущее исследование выставлено (`automation`).
    8. **Ручной крафт:** сундук с материалами и табличка «нажми R».
  - Всё запитано и запущено сразу после постройки: через ~30 секунд игры на каждой остановке видно движение.

- [ ] **Step 1: GameTest** `demoBuildsAndRuns`: выполнить построение в тестовой структуре большего размера (или собрать демо в чистом месте уровня GameTest) → через 600 тиков: в сундуке-выходе плавки есть iron_plate, в выходе сборщика есть шестерни, satisfaction сети витрины ≥ 0.99, прогресс исследования > 0. Если 96×96 не помещается в GameTest-шаблон, тест строит демо по координатам вне шаблона, а проверки делает по ним (документировать).
- [ ] **Step 2: Run** → FAIL. **Step 3:** реализовать. **Step 4: Run** → PASS.
- [ ] **Step 5: Ручная проверка в `runClient`:** новый суперплоский творческий мир → `/minetorio demo` → обойти все остановки, убедиться, что всё движется. Записать в `docs/demo-world.md`, как запустить демо и что на какой остановке.
- [ ] **Step 6: Готовый мир.** Сохранить созданный мир с построенной витриной как `demo/Minetorio-Demo.zip` (только папка сохранения, без чужих файлов; размер < 20 МБ) и описать в `docs/demo-world.md`, как его открыть.
- [ ] **Step 7: Commit** — `feat: demo showcase command and world`
