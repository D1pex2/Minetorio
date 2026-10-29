"""Writes the Minetorio world generation data (the "Nauvis" world type). Stdlib only, deterministic.

Usage: python tools/gen_worldgen.py   (from anywhere)

Outputs under src/main/resources/data/:
  minetorio/dimension_type/nauvis.json             y -16..128, overworld sky
  minetorio/worldgen/noise/{nauvis_seed,ground_patch}.json
  minetorio/worldgen/noise_settings/nauvis.json    terrain from the minetorio:nauvis_* density functions,
                                                   no caves/aquifers/ore veins, the ground tile surface rules
  minetorio/worldgen/world_preset/minetorio.json   Overworld only, multi-noise biomes (ranges = core NauvisBiome)
  minetorio/worldgen/biome/*.json                  the eight Nauvis biomes
  minetorio/worldgen/{configured,placed}_feature/  cliffs, trees, rocks
  minetorio/loot_table/blocks/*.json, chests/crash_site.json
  minetorio/tags/block/nauvis_ground.json
  minecraft/tags/worldgen/{world_preset/normal,biome/is_overworld}.json, block mineable tags (merged)

Numbers that also live in Java (heights, climate ranges) are checked by NauvisDataTest.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "..", "src", "main", "resources", "data")
M = "minetorio:"

MIN_Y, HEIGHT, SEA_LEVEL = -16, 144, 63
SHORE, DEEP = -0.2, -0.45


def write(rel, obj):
    path = os.path.join(DATA, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def merge_tag(rel, values):
    """Adds values to a tag file, keeping what is there."""
    path = os.path.join(DATA, rel)
    old = []
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            old = json.load(f).get("values", [])
    out = list(old)
    for v in values:
        if v not in out:
            out.append(v)
    write(rel, {"values": out})


def block(name, props=None):
    b = {"Name": name if ":" in name else M + name}
    if props:
        b["Properties"] = props
    return b


# --- dimension type, noises ---------------------------------------------------------------------------

write("minetorio/dimension_type/nauvis.json", {
    "ultrawarm": False, "natural": True, "coordinate_scale": 1.0, "has_skylight": True, "has_ceiling": False,
    "ambient_light": 0.0, "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0,
    "piglin_safe": False, "bed_works": True, "respawn_anchor_works": False, "has_raids": False,
    "min_y": MIN_Y, "height": HEIGHT, "logical_height": HEIGHT,
    "infiniburn": "#minecraft:infiniburn_overworld", "effects": "minecraft:overworld",
})
# only sampled at three fixed points to seed the pure terrain (NauvisDensity.terrain)
write("minetorio/worldgen/noise/nauvis_seed.json", {"firstOctave": -4, "amplitudes": [1.0, 1.0, 1.0]})
# patches of the second ground tile within a biome (~30 blocks)
write("minetorio/worldgen/noise/ground_patch.json", {"firstOctave": -5, "amplitudes": [1.0, 1.0, 0.5]})

SEED = M + "nauvis_seed"


def climate(channel):
    return {"type": M + "nauvis_climate", "channel": channel, "seed_noise": SEED}


FINAL = {
    "type": "minecraft:add",
    "argument1": {"type": "minecraft:cache_2d", "argument": {"type": M + "nauvis_surface", "seed_noise": SEED}},
    "argument2": {"type": "minecraft:y_clamped_gradient", "from_y": MIN_Y, "to_y": MIN_Y + HEIGHT,
                  "from_value": float(-MIN_Y), "to_value": float(-(MIN_Y + HEIGHT))},
}

# --- biomes: (id, temperature, humidity, continentalness, top A, top B, sub, trees/rocks) --------------
LAND = [SHORE, 1.5]
BIOMES = [
    ("nauvis_grassland", [-1.5, 1.5], [0.3, 1.5], LAND, "grass_1", "grass_2", "dirt_1"),
    ("nauvis_meadow", [-1.5, 1.5], [0.05, 0.3], LAND, "grass_2", "grass_3", "dirt_1"),
    ("nauvis_steppe", [-1.5, 1.5], [-0.15, 0.05], LAND, "grass_3", "grass_4", "dirt_1"),
    ("nauvis_dry_plains", [-1.5, 0.25], [-0.4, -0.15], LAND, "dry_dirt", "dirt_1", "dirt_2"),
    ("nauvis_desert", [-1.5, 0.25], [-1.5, -0.4], LAND, "sand_1", "sand_2", "sand_1"),
    ("nauvis_red_desert", [0.25, 1.5], [-1.5, -0.15], LAND, "red_desert_0", "red_desert_1", "red_desert_1"),
    ("nauvis_shallow_water", [-1.5, 1.5], [-1.5, 1.5], [DEEP, SHORE], "sand_1", "grass_2", "dirt_2"),
    ("nauvis_deep_water", [-1.5, 1.5], [-1.5, 1.5], [-1.5, DEEP], "sand_1", "grass_2", "dirt_2"),
]
# under water, lake floors mix these two
LAKE_FLOOR = ("dirt_2", "sand_1")


def biome_rules(top_a, top_b, sub, water):
    floor_a, floor_b = LAKE_FLOOR if water else (sub, sub)
    top = {"type": "minecraft:sequence", "sequence": [
        {"type": "minecraft:condition",
         "if_true": {"type": "minecraft:noise_threshold", "noise": M + "ground_patch", "min_threshold": 0.15, "max_threshold": 1e9},
         "then_run": {"type": "minecraft:block", "result_state": block(top_b)}},
        {"type": "minecraft:block", "result_state": block(top_a)},
    ]}
    under_water = {"type": "minecraft:sequence", "sequence": [
        {"type": "minecraft:condition",
         "if_true": {"type": "minecraft:noise_threshold", "noise": M + "ground_patch", "min_threshold": 0.0, "max_threshold": 1e9},
         "then_run": {"type": "minecraft:block", "result_state": block(floor_b)}},
        {"type": "minecraft:block", "result_state": block(floor_a)},
    ]}
    return {"type": "minecraft:sequence", "sequence": [
        {"type": "minecraft:condition",
         "if_true": {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": False, "secondary_depth_range": 0,
                     "surface_type": "floor"},
         "then_run": {"type": "minecraft:sequence", "sequence": [
             {"type": "minecraft:condition",
              "if_true": {"type": "minecraft:water", "offset": 0, "surface_depth_multiplier": 0, "add_stone_depth": False},
              "then_run": top},
             under_water,
         ]}},
        {"type": "minecraft:condition",
         "if_true": {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": True, "secondary_depth_range": 0,
                     "surface_type": "floor"},
         "then_run": {"type": "minecraft:block", "result_state": block(sub)}},
    ]}


surface = {"type": "minecraft:sequence", "sequence": [
    {"type": "minecraft:condition",
     "if_true": {"type": "minecraft:vertical_gradient", "random_name": M + "bedrock_floor",
                 "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}},
     "then_run": {"type": "minecraft:block", "result_state": block("minecraft:bedrock")}},
]}
for (bid, _t, _h, _c, a, b, sub) in BIOMES:
    surface["sequence"].append({"type": "minecraft:condition",
                                "if_true": {"type": "minecraft:biome", "biome_is": [M + bid]},
                                "then_run": biome_rules(a, b, sub, "water" in bid)})

zero = 0.0
write("minetorio/worldgen/noise_settings/nauvis.json", {
    "sea_level": SEA_LEVEL,
    "disable_mob_generation": True,
    "aquifers_enabled": False,
    "ore_veins_enabled": False,
    "legacy_random_source": False,
    "default_block": block("minecraft:stone"),
    "default_fluid": block("minecraft:water", {"level": "0"}),
    "noise": {"min_y": MIN_Y, "height": HEIGHT, "size_horizontal": 1, "size_vertical": 2},
    "noise_router": {
        "barrier": zero, "fluid_level_floodedness": zero, "fluid_level_spread": zero, "lava": zero,
        "temperature": climate("aux"), "vegetation": climate("moisture"), "continents": climate("water"),
        "erosion": zero, "depth": zero, "ridges": zero,
        "initial_density_without_jaggedness": FINAL, "final_density": FINAL,
        "vein_toggle": zero, "vein_ridged": zero, "vein_gap": zero,
    },
    "spawn_target": [],
    "surface_rule": surface,
})

write("minetorio/worldgen/world_preset/minetorio.json", {"dimensions": {"minecraft:overworld": {
    "type": M + "nauvis",
    "generator": {"type": "minecraft:noise", "settings": M + "nauvis", "biome_source": {"type": "minecraft:multi_noise", "biomes": [
        {"biome": M + bid, "parameters": {"temperature": t, "humidity": h, "continentalness": c,
                                          "erosion": 0.0, "weirdness": 0.0, "depth": 0.0, "offset": 0.0}}
        for (bid, t, h, c, *_rest) in BIOMES]}},
}}})
merge_tag("minecraft/tags/worldgen/world_preset/normal.json", [M + "minetorio"])
merge_tag("minecraft/tags/worldgen/biome/is_overworld.json", [M + b[0] for b in BIOMES])

# --- features -----------------------------------------------------------------------------------------


def configured(name, ftype, config):
    write(f"minetorio/worldgen/configured_feature/{name}.json", {"type": ftype, "config": config})


def placed(name, feature, placement):
    write(f"minetorio/worldgen/placed_feature/{name}.json", {"feature": M + feature, "placement": placement})


def scatter(count=None, rarity=None):
    p = []
    if count:
        p.append({"type": "minecraft:count", "count": count})
    if rarity:
        p.append({"type": "minecraft:rarity_filter", "chance": rarity})
    return p + [{"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
                {"type": "minecraft:biome"}]


configured("cliff_rock", M + "cliff_rock", {})
placed("cliff_rock", "cliff_rock", [])


def tree(leaves, lo, hi, forest, chance):
    return {"trunk": block("tree_trunk"), "leaves": block(leaves), "min_height": lo, "max_height": hi,
            "forest": forest, "chance": chance}


configured("tree_green", M + "nauvis_tree", tree("tree_leaves_green", 3, 5, True, 0.9))
configured("tree_olive", M + "nauvis_tree", tree("tree_leaves_olive", 3, 5, True, 0.8))
configured("tree_red", M + "nauvis_tree", tree("tree_leaves_red", 3, 4, True, 0.7))
configured("dry_tree", M + "nauvis_tree", {"trunk": block("dry_tree_trunk"), "branches": block("dry_tree_branches"),
                                           "min_height": 2, "max_height": 4, "forest": False, "chance": 1.0})
configured("big_rock", M + "nauvis_rock", {"block": block("big_rock"), "radius": 0, "chance": 1.0})
configured("sand_rock", M + "nauvis_rock", {"block": block("sand_rock"), "radius": 0, "chance": 1.0})
configured("huge_rock", M + "nauvis_rock", {"block": block("huge_rock"), "radius": 1, "chance": 1.0})

# one global order for step 9 (vegetal_decoration); each biome lists a subset in this order
VEGETATION = {
    "huge_rock": ("huge_rock", scatter(rarity=24)),
    "big_rock": ("big_rock", scatter(rarity=7)),
    "sand_rock": ("sand_rock", scatter(rarity=4)),
    "trees_green_dense": ("tree_green", scatter(count=14)),
    "trees_green": ("tree_green", scatter(count=5)),
    "trees_olive": ("tree_olive", scatter(count=5)),
    "trees_red": ("tree_red", scatter(count=2)),
    "dry_trees": ("dry_tree", scatter(rarity=2)),
    "dry_trees_sparse": ("dry_tree", scatter(rarity=5)),
}
for name, (feat, placement) in VEGETATION.items():
    placed(name, feat, placement)
ORDER = list(VEGETATION)

BIOME_VEGETATION = {
    "nauvis_grassland": ["huge_rock", "big_rock", "trees_green_dense", "trees_olive", "trees_red"],
    "nauvis_meadow": ["huge_rock", "big_rock", "trees_green", "trees_olive", "trees_red"],
    "nauvis_steppe": ["huge_rock", "big_rock", "trees_olive", "dry_trees_sparse"],
    "nauvis_dry_plains": ["huge_rock", "big_rock", "trees_red", "dry_trees"],
    "nauvis_desert": ["huge_rock", "sand_rock", "dry_trees_sparse"],
    "nauvis_red_desert": ["huge_rock", "big_rock", "sand_rock", "dry_trees_sparse"],
    "nauvis_shallow_water": [],
    "nauvis_deep_water": [],
}

# Factorio's look: a hazy sky, warm grey fog, dark teal water
SKY, FOG, WATER, WATER_FOG = 0xA4B5BC, 0xC4C0AC, 0x2E5866, 0x1C3640
GRASS_TINT = {"nauvis_grassland": 0x56702A, "nauvis_meadow": 0x6B7A33, "nauvis_steppe": 0x857C3C,
              "nauvis_dry_plains": 0x8C7448, "nauvis_desert": 0xB89C64, "nauvis_red_desert": 0x9E6440,
              "nauvis_shallow_water": 0x6B7A33, "nauvis_deep_water": 0x6B7A33}

for (bid, *_rest) in BIOMES:
    steps = [[] for _ in range(11)]
    steps[2] = [M + "cliff_rock"]
    veg = BIOME_VEGETATION[bid]
    assert veg == [v for v in ORDER if v in veg], bid
    steps[9] = [M + v for v in veg]
    water = "water" in bid
    dry = bid in ("nauvis_desert", "nauvis_red_desert", "nauvis_dry_plains")
    write(f"minetorio/worldgen/biome/{bid}.json", {
        "has_precipitation": False,
        "temperature": 1.2 if dry else 0.8,
        "downfall": 0.1 if dry else 0.5,
        "effects": {"sky_color": SKY, "fog_color": FOG, "water_color": WATER, "water_fog_color": WATER_FOG,
                    "grass_color": GRASS_TINT[bid], "foliage_color": GRASS_TINT[bid]},
        "spawners": {"monster": [], "creature": [], "ambient": [], "axolotls": [], "underground_water_creature": [],
                     "water_creature": [], "misc": [],
                     "water_ambient": [{"type": M + "fish", "weight": 10, "minCount": 2, "maxCount": 5}] if water else []},
        "spawn_costs": {},
        "carvers": {},
        "features": steps,
    })

# --- blocks: loot, tags -------------------------------------------------------------------------------
GROUND = ["grass_1", "grass_2", "grass_3", "grass_4", "dry_dirt", "dirt_1", "dirt_2", "sand_1", "sand_2",
          "red_desert_0", "red_desert_1"]
TREE_PARTS = ["tree_trunk", "dry_tree_trunk", "tree_leaves_green", "tree_leaves_olive", "tree_leaves_red", "dry_tree_branches"]
ROCKS = ["big_rock", "sand_rock", "huge_rock"]


def item_entry(name, count=None):
    e = {"type": "minecraft:item", "name": name}
    if count is not None:
        e["functions"] = [{"function": "minecraft:set_count", "count": count}]
    return e


def uniform(lo, hi):
    return {"type": "minecraft:uniform", "min": lo, "max": hi}


def loot(name, pools, kind="minecraft:block"):
    write(f"minetorio/loot_table/{name}.json", {"type": kind, "pools": pools})


for g in GROUND:
    loot(f"blocks/{g}", [{"rolls": 1, "entries": [item_entry(M + g)], "conditions": [{"condition": "minecraft:survives_explosion"}]}])
for t in TREE_PARTS:  # a tree yields 4 wood, whichever part is mined
    loot(f"blocks/{t}", [{"rolls": 1, "entries": [item_entry(M + "wood", 4)]}])
# rocks (Factorio 1.1): big rock 20 stone, sand rock 19-25 stone, huge rock 24-50 stone + 24-50 coal.
# Rocks use the same mod resources as surface ore fields.
loot("blocks/big_rock", [{"rolls": 1, "entries": [item_entry("minetorio:stone", 20)]}])
loot("blocks/sand_rock", [{"rolls": 1, "entries": [item_entry("minetorio:stone", uniform(19, 25))]}])
loot("blocks/huge_rock", [{"rolls": 1, "entries": [item_entry("minetorio:stone", uniform(24, 50))]},
                          {"rolls": 1, "entries": [item_entry("minetorio:coal", uniform(24, 50))]}])
# Factorio 1.1 freeplay starting items that exist in the mod (the pistol and ammo do not)
loot("chests/crash_site", [{"rolls": 1, "entries": [item_entry(M + i, n)]} for i, n in
                           [("iron_plate", 8), ("wood", 1), ("burner_mining_drill", 1), ("stone_furnace", 1)]],
     kind="minecraft:chest")

write("minetorio/tags/block/nauvis_ground.json", {"values": [M + g for g in GROUND]})
merge_tag("minetorio/tags/block/ore_field_replaceable.json", ["#minetorio:nauvis_ground"])
merge_tag("minecraft/tags/block/mineable/shovel.json", [M + g for g in GROUND])
merge_tag("minecraft/tags/block/mineable/axe.json", [M + t for t in TREE_PARTS])
merge_tag("minecraft/tags/block/mineable/pickaxe.json", [M + r for r in ROCKS] + [M + "ship_wreck_hull", M + "ship_wreck_debris",
                                                                                  M + "crash_site_chest"])
print("worldgen data written")
