# Minetorio art

Everything here is procedural (Python 3 + Pillow + numpy) and deterministic. Re-run a script after
editing it. No fal or other generated images are used: hand-placed pixels read better than
downscaled diffusion output at 16×16.

| Script | Output |
|---|---|
| `gen.py` | Flat 16×16 item icons in `art/item/`. Copies the 11 material/tool icons the mod uses into `textures/item/` and writes `preview.png`. Its old `art/block/` tiles are kept for reference only. |
| `gen_blocks.py` | All block textures in `src/main/resources/assets/minetorio/textures/block/` (plus `.mcmeta` for animated strips) and `blocks-preview.png`. |
| `gen_models.py` | All block and item JSON models in `models/{block,item}/`: Create-style multi-element machines, belts, pipes, poles and inserters, plus fitted item display transforms for the multiblocks. |
| `render_models.py` | `models-preview.png`: an offline isometric render of every model (two views each) for checking shapes without the game. |

Order: `python art/gen_blocks.py && python art/gen_models.py && python art/render_models.py`

## Notes

- **Palette:** vanilla-like steel, dark steel, brass, copper, rust and stone. Tier colours are yellow, red and blue, with green for the lab's science holders.
- **Animated textures:**
  - `fire` (8 frames, frametime 2): furnace mouth and boiler door.
  - `lab_glow` (8 frames, pulsing): shown only while researching. Idle, the lab uses `lab_glass`.
  - Belts: straights and curves scroll at Factorio speed at 20 tps: 1.5, 3 and 4.5 px per tick for yellow, red and blue. The chevron period is 16 px, so tiles join seamlessly and the fast tiers don't alias.
  - Curves map the same pattern onto a quarter circle around the inner corner, matching `BeltGeometry`.
- **Model geometry:**
  - Boxes bigger than a block are split into equal parts of 16 px or less, so textures keep roughly vanilla texel density and show plate seams.
  - Round parts are octagons: a box plus a copy turned 45°.
  - Cogwheels are discs with 8 tooth bars at 22.5° steps.
