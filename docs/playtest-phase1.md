# Minetorio phase 1: playtest checklist

**Status: not played yet.** This checklist was written by the agent that built the Task 18 visuals
overnight. It could not do a manual playthrough because nobody could watch the client window. What
it did check automatically:

- `./gradlew test` passes (163 unit tests, including `LangCompletenessTest`).
- `./gradlew runGameTestServer` reports "All 100 required tests passed".
- `./gradlew runClient` loads to the title screen with no model, blockstate or texture warnings in the log.
- Every model was rendered offline into `art/models-preview.png`, and the textures into `art/blocks-preview.png`.

Tick the boxes as you go. Under **Findings** at the end, write down anything that looks wrong:
what you saw, where, and a screenshot (F2 saves to `run/screenshots/`).

## 0. Launch

```bash
export JAVA_HOME="/c/Users/kirill/.jdks/jdk-21.0.12.1+1"; export PATH="$JAVA_HOME/bin:$PATH"
./gradlew runClient
```

Create a new world: **Survival**, cheats **on** (for `/time set day` and `/gamemode` while inspecting).
Keys: **R** hand crafting, **Y** research.

To see every mechanic running at once, use the demo showcase first: `/minetorio demo` or the saved world `demo/Minetorio-Demo.zip` (see `docs/demo-world.md`).

- [ ] The log (`run/logs/latest.log`) has no `Missing textures in model` or `Unable to load model` lines, and no `minetorio` errors.
- [ ] Creative inventory, Minetorio tab: every one of the 34 items has an icon and a name, with no purple-black squares and no raw keys like `item.minetorio.x`. Machines show as small 3D models. Ores, plates, gear, cable, circuit and science packs show as flat icons. Check again with the language set to Russian.
- [ ] Hold each machine item in your hand (first and third person) and throw one on the ground. It should be scaled down, not clip into the camera, and not float far away.

## 1. What every machine should look like

These are blocky, Create-style models: plates with seams, pipes, gears and rivets in vanilla colours.
Look at each one from about 5 blocks away and check that it reads as its Factorio counterpart.
Then check that the moving part **moves only while the machine works** and stops when it doesn't.

| Machine | Size | Look for | Animation (only while working) |
|---|---|---|---|
| Stone furnace | 2×2 | Stepped stone mound, smoke hole on top, arched mouth with a grate on the side facing you | Mouth shows flickering fire and the block gives off light while smelting. Back to a dark grate when idle |
| Burner mining drill | 2×2 | Rusty brown box, dark corner posts, chimney, fire door at the back, output chute at the front | Toothed rotor on top spins while mining. Stops with no fuel or when the output is blocked |
| Electric mining drill | 3×3 | Steel body, yellow corner feet, hazard stripe and chute at the front, vents on the sides | Central drill bit and its 8-arm head spin while mining. Stops with no power |
| Assembling machine 1 / 2 | 3×3 | Panel housing, dark corner pillars, window on the front, big steel cogwheel on the roof. Tier colour: AM1 yellow, AM2 blue | Cogwheel turns while crafting. Stops with no power, no ingredients or a full output |
| Lab | 3×3 | Panel body with red, green and blue lights, red and green pack holders on the corners, brass ring, glass dome | Dome turns pale cyan to glowing, pulsing and slowly rotating, at full brightness while researching |
| Boiler | 3×2 | Horizontal tank, firebox with door on the side facing you, chimney, water pipe stubs left and right, steam outlet at the far side | Fire flickers behind the door only while burning |
| Steam engine | 3×5 | Long bed with rails and a steam pipe, copper cylinder with brass bands, slide bars, crank pedestal, big flywheel on the right side | Flywheel turns, crank swings and the piston slides back and forth under load. Stops when there's no steam or no consumers |
| Offshore pump | 1×2 | Pump on a dark plate: steel motor box with a yellow band, round pump housing, intake grate facing the water, flanged outlet facing you | none |
| Pipe | 1 | Thick steel pipe with collars at joints and flanges at block edges. Joins straight, corner, T, cross and vertical pieces | none |
| Small electric pole | 1 | Wooden post on a dark footing, crossbar with two ceramic insulators, insulator cap on top | Copper wires attach at the top cap and sag between poles |
| Inserters (burner, basic, long, fast) | 1 | Dark base plate, tier-coloured housing (rust, yellow, red, blue), steel turret ring, two-segment steel arm, tier-coloured hand with two gripper fingers | Arm swings from pickup to drop through the right side. The held item hangs under the hand |
| Transport belts (yellow, red, blue) | 1 | Dark belt with raised tier-coloured side rails. Curves have rails on the outer edges | Chevrons scroll in the travel direction, faster for each tier (red twice yellow, blue three times). Curves animate too and join seamlessly with straights |
| Underground belts | 1 | Tier-coloured hood with a dark mouth facing the belt, arrow chevrons on the roof. Entrance and exit are mirror images | Belt part scrolls |
| Splitters | 2×1 | Two belts under a tier-coloured box with motor grilles front and back, arrows on top, dark side housings | Belts scroll |

Things to check closely:

- [ ] Every rotation (north, east, south, west) of each directional block and machine looks right. Pipe stubs line up with the real fluid ports: boiler water left and right, boiler steam forward, engine at both ends, pump outlet toward the shore.
- [ ] Belt chevrons point and move the same way the items travel, on straights and on both curve directions.
- [ ] Belt ramps (R25): a belt one block higher in front (same direction) turns the belt below into an ascending ramp; a belt one block higher behind it, feeding into it, turns it into a descending ramp. No extra item. Items climb and descend, lying on the slope; you can walk up a ramp. Expected limits:
  - Side-feeding onto a ramp doesn't work, ascending or descending. A belt pointing at a ramp's side just stops.
  - No ramp forms if the belt in front feeds a chest or machine (the cell in front holds a block entity), or if the cell above a would-be descending ramp is not open (solid block, chest, another belt). A plain block in front of an ascending ramp, under the upper belt, is fine.
  - Curves, underground belts and splitters are never ramps.
- [ ] No faces flicker (z-fighting) on machine fronts: windows, chutes, the fire door and the furnace mouth.
- [ ] Multiblock machines don't vanish when the controller block is off-screen. They render over their whole footprint.
- [ ] Placing a multiblock machine: it is centered on the block you aim at (where a normal block would go), on that block's layer. For even sizes the aimed block is the left-front one of the middle 2×2 (2×2 furnace and burner drill: the aimed block is their near-left corner; boiler: centered across, extending away from you). Splitters are the exception: the aimed block is their left column, so they line up with the belt you click. While holding a machine, a translucent box shows the footprint: green if it fits, red if something is in the way (and then right-click places nothing and keeps the item).
- [ ] Breaking particles show machine-coloured fragments, not missing-texture squares.

### Протяжка лент (drag placement)

- [ ] Взять конвейер (любой: жёлтый, красный, синий) и коротко кликнуть ПКМ по земле, не сдвигаясь: ставится ровно одна лента, как раньше.
- [ ] Зажать ПКМ на земле и смотреть вперёд вдоль линии: от первой ленты до клетки под прицелом тянется полупрозрачный зелёный «призрак» лент со стрелками (красная клетка = занято, туда ничего не встанет).
- [ ] Не отпуская, бежать вперёд: ленты, уходящие из зоны досягаемости за спиной, ставятся сами; отпустить кнопку — ставится остаток. В выживании каждая лента забирает один предмет (когда кончается стопка в руке, берётся из инвентаря); в творческом ничего не тратится. Кончились ленты — линия обрывается.
- [ ] Повернуть на 90° и пойти в сторону (на 2+ клетки от линии): на углу встаёт поворот (кривая), линия продолжается по новой оси. Сразу после клика тоже можно повернуть — первая лента развернётся.
- [ ] Протянуть через сундук, машину и поперечную ленту: эти клетки пропускаются, ничего не ломается. Красной лентой по жёлтой, смотрящей в ту же сторону: жёлтая заменяется (fast-replace), жёлтая возвращается в инвентарь.
- [ ] Протянуть по ступеньке в один блок вверх и вниз: на перепадах получаются наклонные ленты (рампы), как при ручной установке, и предметы по ним едут.
- [ ] То же с малым столбом ЛЭП: столбы ставятся через каждые 7 клеток (максимум длины провода), провода соединяют их; на отпускании лишний столб не ставится.
- [ ] Лента может встать в клетку, где стоит сам игрок (он просто оказывается на ней).

### Призраки вручную (ghost placement)

Клавиша — **Левый Ctrl** (Управление → Minetorio → «Ставить призрак (держать при ПКМ)»; та же клавиша, что и бег — это нормально: Ctrl читается только в момент ПКМ с блоком Minetorio в руке, бег работает как прежде).

- [ ] С любым блоком Minetorio в руке (хоть один предмет, в выживании) зажать Ctrl и навести на землю: синий полупрозрачный бокс показывает, куда встанет призрак (красный — нельзя). ПКМ ставит синий призрак, предмет не тратится. Без Ctrl ставится настоящий блок, как раньше.
- [ ] Призрак ленты смотрит туда же, куда смотрела бы настоящая; на ступеньке в один блок призраки лент становятся рампой. Ctrl+ПКМ по призраку ставит его заново (например, с новым направлением).
- [ ] Сборочный автомат/печь призраком центрируются на клетке под прицелом, как настоящие; насос на берегу сам разворачивается к воде. Пока стоит призрак машины, в её площадь нельзя поставить ни другой призрак, ни настоящий блок (превью машины красное, клик ничего не ставит). Настоящая такая же машина ровно на место призрака ставится и заменяет его.
- [ ] Призрак выглядит ровно как построенная машина, только синий и полупрозрачный: тот же размер, место и поворот. Поставить призрак электробура, сборочного автомата, лаборатории, радара, паровой машины, бойлера, робопорта, печи и бура на угле по всем 4 сторонам и достроить: модель не меняет ни размер, ни положение (раньше призрак был больше и выше). Столб — все 3 блока высотой, манипулятор — с рукой, ленты (повороты, рампы), трубы и сундуки — как раньше. Нет мерцания на стыке с землёй и соседними блоками.
- [ ] Ctrl + зажатая ПКМ с лентой или столбом: линия призраков (синее превью вместо зелёного), с поворотами, рампами и шагом столбов 7 клеток; предметы не нужны и не тратятся.
- [ ] ПКМ по призраку подходящим предметом строит его (тратится один предмет), машина — целиком. Роботы из рабочей зоны робопорта строят ручные призраки из сундука.
- [ ] Удерживание Ctrl+ПКМ на месте ставит призрак повторно раз в 4 тика; спам ограничен сервером (~10 в секунду).

**Предмет-призрак** (как «призрачный курсор» Factorio):

- [ ] В меню ручного крафта (R) нажать колёсиком (или Ctrl+ЛКМ) по рецепту ленты: в руке появляется «Призрак: Транспортный конвейер» — иконка ленты, полупрозрачная и голубая. Для неизученного рецепта (сборочный автомат в начале игры) и для не-блоков (шестерня) ничего не выдаётся.
- [ ] В выживании без лент в инвентаре нажать колёсиком (pick-block) по поставленной ленте/машине: в руку приходит её предмет-призрак. Если такие предметы есть — работает обычный pick-block (выбирается слот с ними).
- [ ] С предметом-призраком в руке ПКМ (без Ctrl) ставит призрак, зажатая ПКМ тянет линию призраков; предмет-призрак не тратится (стак 1). Достроить призрак всё равно нужно настоящим предметом (рукой или роботами).
- [ ] Выброшенный (Q) предмет-призрак просто исчезает. Положенный в сундук — манипулятор его не берёт.

### Ore fields

- [ ] In a new world, 48 blocks from spawn lie the four starting fields (iron east, copper west, coal south, stone north): round patches of ground blocks with ore lumps, each block at its own height on slopes. Iron lumps are blue-silver, copper orange, coal black, stone beige, on brown ground (compare `art/ore-fields-preview.png`). Neighbouring blocks look turned, not like a grid.
- [ ] Walking further (new chunks), more fields appear on grass, sand, gravel, stone and the like; never on water, lava, leaves, under tree trunks or inside villages.
- [ ] Right-click a field block with an empty hand, or start breaking it: the action bar says the ore and how much is left in that block.
- [ ] Breaking a field block (any pickaxe; bare hands work but slowly) drops exactly one ore and the block stays. Silk touch and fortune change nothing. In creative mode breaking turns the block into dirt at once.
- [ ] Below 10 % of its initial amount a block turns paler; at 0 it becomes dirt.
- [ ] Pistons don't move field blocks; TNT and creepers don't destroy them. Field blocks have no item (pick-block gives nothing).

### Radar and map (demo showcase)

- [ ] In the demo showcase, the radar stop sits on the south side of the walkway, between assembly (6) and research (7), with two signs (radar; M for the map, minimap top right) in your client's language.
- [ ] The radar dish turns (it is powered from the steam plant through the pole next to it). Right-click it: the action bar shows the explored chunk count and the seconds until the next chunk.
- [ ] The minimap in the top right corner shows the showcase area; over a few minutes the explored area grows chunk by chunk beyond the 7×7 chunks around the radar.
- [ ] Press **M**: the full map opens and shows the same explored area.
- [ ] Break the radar's pole: the dish stops and the status says there is no power.

### Alt mode and area highlights

Check these once in English and once with the game language set to Russian (the key is listed under Controls → Minetorio as "Alt mode (machine icons)" / «Альт-режим (значки машин)»).

- [ ] Press **Left Alt**: the action bar says alt mode is on; press again: off. It is a toggle, not a hold.
- [ ] With alt mode on, every machine within about 32 blocks shows an icon on a dark rounded square above it, turning to face you: the assembler its recipe's product, the furnace the plate it smelts, drills their ore, the lab the current research (a red pack if the research has no item), the boiler its fuel. Icons behind walls are hidden; far ones are drawn larger so they stay readable.
- [ ] Warnings flash in the icon's lower right corner (or alone, blinking, when the machine has no icon): red ⚡ no power, yellow ⚡ low power, red coal no fuel, red ✖ no ore field, yellow ! output full, yellow ? no recipe / no research. Break a pole and watch the machines it fed start flashing within half a second.
- [ ] Inserters show a yellow arrow over their base from the pickup side to the drop side; a burner inserter stuck mid-swing without fuel shows a small flashing badge.
- [ ] Hold a small electric pole and look at the ground: a pale blue 5×5 square on the terrain shows its supply area, a thin ring its wire reach (7.5), and the supply areas of the poles already placed nearby show faintly, so gaps are easy to see. Looking at a placed pole (empty hand) shows its square and ring.
- [ ] Hold or look at a burner drill (2×2) or an electric drill (5×5): a green square shows the mining area. Hold or look at a radar: a blue outline marks its 7×7-chunk continuous scan area.
- [ ] The highlights follow steps in the terrain, do not flicker against the ground, and are not drawn on top of belts or machines.

## 2. Bootstrap: pickaxe to green science (brief Step 4)

1. [ ] Walk to the starting fields (48 blocks east, west, south and north of spawn). With a pickaxe, mine some iron and copper ore, coal and stone (cobblestone) from them, one ore per break.
2. [ ] Press **R** and hand-craft a stone furnace (5 cobblestone). Place it, add coal, and smelt iron ore into iron plates. **Look:** the furnace mouth lights up and flickers.
3. [ ] Place a burner mining drill on the iron field (it mines the 2×2 field blocks under itself, one block after the other; emptied blocks turn into dirt), then a belt from the drill's chute into a stone furnace (use a burner inserter). Right-click the drill: the status says the ore and how much is left under it; on plain ground it says there is no ore field. **Look:** the drill rotor spins and the belt animates.
4. [ ] Place an offshore pump on a shore facing the water, then pipes to a boiler (fuel it with coal) and a steam engine. Place small electric poles (1 stick + 2 copper cable → 2 poles) and an electric mining drill (3 circuits, 5 gears, 10 iron plates) on a field (it mines the 5×5 field blocks under and around it). **Everything electric must be inside a pole's supply area:** labs, assemblers, electric drills and the non-burner inserters. **Look:** the boiler fire flickers, the engine flywheel turns and its piston slides, wires sag between poles, and the drill head spins.
5. [ ] Hand-craft (R) a lab (10 circuits, 10 gears, 4 transport belts) and 10 automation (red) science packs (1 copper plate + 1 gear each, 5 s). Place the lab in pole coverage, insert the packs, press **Y** and research `automation` (10 red packs; unlocks assembling machine 1 and the long-handed inserter). Assemblers stay locked until this research is done. **Look:** the lab dome glows and pulses while researching.
6. [ ] Craft assembling machines 1 (3 circuits, 5 gears, 9 iron plates) and place them powered.
   1. Automate gears and red science with inserters and belts. **Look:** the assembler gears turn only while crafting.
   2. Research `logistic_science_pack` (75 red packs). It unlocks the green pack recipe (1 inserter + 1 transport belt, 6 s). Automate green science.
   3. Research `logistics` (20 red packs). It is a prerequisite of `logistics_2`, together with `logistic_science_pack`.
   4. Research `logistics_2` (200 red + 200 green packs). It unlocks fast belts, fast undergrounds and fast splitters. Craft red (fast) belts: 5 gears + 1 transport belt.
   5. Optional: research `automation_2` (40 red + 40 green) and check that assembling machine 2 has blue accents.
7. [ ] Save and quit, then reload: everything is still there, belts keep moving and animations resume.
8. [ ] Dedicated server: run `./gradlew runServer` (accept the EULA in `run/eula.txt` on the first start), join with the client, and check that belts, GUIs, research and animations all work.

## Findings

| # | Step | What happened | Severity |
|---|---|---|---|
| | | | |
