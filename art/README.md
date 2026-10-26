# Minetorio placeholder art

All 35 item icons and 14 block textures are PROCEDURAL (Python + Pillow, 16x16, limited palette,
dark outline, transparent items). Generator: C:/Users/kirill/AppData/Local/Temp/claude/minetorio-art/gen.py
(copy it into the repo tools dir if you want it reproducible). No fal calls were made: the fal MCP was
not connected and hand-drawn pixels beat downscaled diffusion output at 16x16.

- item/*.png: iron_ore, copper_ore, iron_plate, copper_plate, stone_brick, iron_gear_wheel, copper_cable,
  electronic_circuit, automation_science_pack, logistic_science_pack, scanner, burner_inserter, inserter,
  long_handed_inserter, fast_inserter, {,fast_,express_}transport_belt, {,fast_,express_}underground_belt,
  {,fast_,express_}splitter, burner_mining_drill, electric_mining_drill, stone_furnace, assembling_machine_1/2,
  lab, offshore_pump, pipe, boiler, steam_engine, small_electric_pole.
- block/: machine_metal, machine_metal_dark, furnace_stone, furnace_front_lit, drill_side, assembler_side,
  lab_side, boiler_side, engine_side, pipe, pole_wood (tiling 16x16, noise + panel edges).
- block/belt_yellow (16 frames, scroll 1px/frame), belt_red (8 frames, 2px/frame), belt_blue (8 frames,
  3px/frame); chevron period is 8px so all loop seamlessly. All .png.mcmeta use {"animation":{"frametime":1}};
  speed tiers come from the px-per-frame step, not frametime. Chevrons point toward the top of the image;
  rotate the face UVs per belt direction in the model.
- preview.png: contact sheet at x8 (belts show frame 0).

Human touch-up suggested: belt item icons (dotted chevrons are busy), iron_ore vs copper_ore (similar
silhouette; iron should be browner/paler), iron_gear_wheel (stray top tooth), steam_engine, offshore_pump.
