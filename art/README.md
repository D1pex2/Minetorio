# Minetorio art

Everything here is procedural (Python 3 + Pillow + numpy) and deterministic. Re-run a script after
editing it. No fal or other generated images are used: hand-placed pixels read better than
downscaled diffusion output at 16×16.

| Script | Output |
|---|---|
| `gen.py` | Flat 16×16 item icons in `art/item/`. Copies the 11 material/tool icons the mod uses into `textures/item/` and writes `preview.png`. Its old `art/block/` tiles are kept for reference only. |
| `gen_blocks.py` | All block textures in `src/main/resources/assets/minetorio/textures/block/` (plus `.mcmeta` for animated strips) and `blocks-preview.png`. |
| `gen_models.py` | All block and item JSON models in `models/{block,item}/`: Create-style multi-element machines, belts, pipes, poles and inserters, plus fitted item display transforms for the multiblocks. |
| `gen_ore_fields.py` | Ore field blocks (iron, copper, coal, stone): fresh and `_depleted` (paler) textures after the Factorio ore tiles, their `cube_all` models, the blockstates (`depleted` × four random y-rotations) and `ore-fields-preview.png`. |
| `gen_belt_slopes.py` | Belt ramps (R25): `template_belt_{ascending,descending}` and the per-tier `*transport_belt_{ascending,descending}` models (the belt plate and rails turned 45°), plus the three `*transport_belt` blockstates (facing × shape × slope). |
| `render_models.py` | `models-preview.png`: an offline isometric render of every model (two views each) for checking shapes without the game, plus the multi-part machines (steam engine, mining drills) assembled as their block entity renderers draw them. |
| `gen_radar.py` | The radar (after the Factorio 1.1 radar): its own `radar_*` textures, `radar`, `radar_base`, `radar_dish` block models, the item model and `radar-preview.png`. Self-contained; the dish turns in `RadarRenderer`. |
| `gen_robots.py` | Construction robots (after the Factorio 1.1 roboport and construction robot): `roboport_*` and `construction_robot_*` textures, the 4×4×4 roboport cut into eight `roboport_IJK` piece models (it is bigger than one block model may be; `RoboportRenderer` puts them back together), the roboport's moving and lit parts (iris blades, antenna head, charging-point glow, status lamps) that `RoboportRenderer` animates over the pieces, the robot model, the item models, the ghost's particle model, the `roboport`/`ghost` blockstates and `robots-preview.png`. Self-contained. |
| `gen_energy.py` | Solar panel, accumulator, medium/big electric pole and substation (after the Factorio 1.1 entities): `energy_*` textures, each machine authored around its footprint center and cut into 32 px `<name>_p<k>` piece models (drawn k × 2 blocks up by `EnergyRender`), the accumulator's lit lamp, particle-only controller models, item models, blockstates and `energy-preview.png`. Uses `gen_radar.py`'s helpers. |

| `render_compare.py REF_DIR OUT.png` | Side-by-side sheet: each machine's Factorio wiki reference image next to a render of the machine as its renderer assembles it, seen from Factorio's angle. Every machine model is made from its reference and checked on this sheet. The references are Factorio's art: keep them and the sheet outside the repository. |

Order: `python art/gen_blocks.py && python art/gen_models.py && python art/gen_belt_slopes.py && python art/render_models.py`

`python -I art/check_zfight.py` and the `ModelZFightTest` / `ModelElementLimitsTest` unit tests check every model: no z-fighting faces, element rotations only 0, ±22.5 or ±45° (anything else does not load) and `from`/`to` inside −16..32.

## Notes

- **Palette:** vanilla-like steel, dark steel, brass, copper, rust and stone. Tier colours are yellow, red and blue, with green for the lab's science holders and the stack inserter, purple for the filter inserter.
- **Animated textures:**
  - `fire` (8 frames, frametime 2): furnace mouth and boiler door.
  - `lab_glow` (8 frames, pulsing): shown only while researching. Idle, the lab uses `lab_glass`.
  - `asm_lamp_glow` (8 frames, pulsing): the assemblers' status lamp, drawn at full brightness only while crafting (tier 1 blinks it twice per work loop). Idle, the lens is the dull `asm_lamp`.
  - Belts: straights and curves scroll at Factorio speed at 20 tps: 1.5, 3 and 4.5 px per tick for yellow, red and blue. The chevron period is 16 px, so tiles join seamlessly and the fast tiers don't alias.
  - Curves map the same pattern onto a quarter circle around the inner corner, matching `BeltGeometry`.
- **Model geometry:**
  - Boxes bigger than a block are split into equal parts of 16 px or less, so textures keep roughly vanilla texel density and show plate seams.
  - Round parts are octagons: a box plus a copy turned 45°.
  - Cogwheels are discs with 8 tooth bars at 22.5° steps.
  - Upright cogs (assemblers) are octagons with 4 tooth bars (8 teeth). A cog meshing with another is authored half a tooth (22.5°) turned and 0.1 px further along its axis (`ASM_MESH_DZ`), so neighbours never share a face plane while they turn.
  - Assembler moving parts (cogs, gears, push rod, carriage, arm, press head, fan, lamp) are separate models authored in their rest pose; `AssemblerRenderer.assemble` moves them through one work loop (Factorio's timing: 32 frames at 3 × crafting speed per tick), so phase 0 is what ghosts and the item show.
