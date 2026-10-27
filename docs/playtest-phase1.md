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
- [ ] Creative inventory, Minetorio tab: every one of the 35 items has an icon and a name, with no purple-black squares and no raw keys like `item.minetorio.x`. Machines show as small 3D models. Ores, plates, gear, cable, circuit, science packs and scanner show as flat icons. Check again with the language set to Russian.
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

## 2. Bootstrap: pickaxe to green science (brief Step 4)

1. [ ] With a pickaxe, mine vanilla iron and copper ore, cobblestone and coal.
2. [ ] Press **R** and hand-craft a stone furnace (5 cobblestone). Place it, add coal, and smelt `raw_iron` into iron plates. **Look:** the furnace mouth lights up and flickers.
3. [ ] Hand-craft a deposit scanner (5 circuits, 10 iron plates, 5 cables; smelt some copper first). Use it to find the starting iron deposit. Place a burner mining drill on it, then a belt from the drill's chute into a stone furnace (use a burner inserter). **Look:** the drill rotor spins and the belt animates.
4. [ ] Place an offshore pump on a shore facing the water, then pipes to a boiler (fuel it with coal) and a steam engine. Place small electric poles (1 stick + 2 copper cable → 2 poles) and an electric mining drill (3 circuits, 5 gears, 10 iron plates). **Everything electric must be inside a pole's supply area:** labs, assemblers, electric drills and the non-burner inserters. **Look:** the boiler fire flickers, the engine flywheel turns and its piston slides, wires sag between poles, and the drill head spins.
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
