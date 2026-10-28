"""Side-by-side check of every machine against its Factorio reference image.

    python art/render_compare.py REF_DIR OUT.png

REF_DIR holds the reference crops from the Factorio wiki (lab.png, steam_engine.png, ...). They are
Factorio's art, so neither they nor the sheet belong in this repository: keep both in a scratch folder.
Each machine is drawn as its block entity renderer assembles it (render_models.placed), seen from above
at about Factorio's angle, from the side that the reference shows facing the camera.
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

from render_models import bounds, placed, render

B = "minetorio:block/"
I = "minetorio:item/"
T = "minetorio:block/"

# InserterRenderer's arm (client/inserter/InserterRenderer.java), in blocks
SHOULDER_Y, ELBOW_RISE, ELBOW_RISE_PER_REACH, ELBOW_AT, HAND_RISE, HAND_REACH = 5.5 / 16, 0.40, 0.06, 0.1, 0.2, 0.85
SHOULDER_HALF, UPPER_HALF, ELBOW_HALF, FORE_HALF, HAND_HALF = 0.065, 0.035, 0.045, 0.024, 0.05
STACK_HAND_HALF = 0.085


def segment(a, b, half, tex):
    """A square bar from a to b (block units) in the y-z plane, as a model element turned about x."""
    a = [v * 16 for v in a]
    b = [v * 16 for v in b]
    h = half * 16
    dy, dz = b[1] - a[1], b[2] - a[2]
    length = math.hypot(dy, dz)
    # built along -z from a, then turned about x through a so (0, 0, -L) lands on (dy, dz)
    angle = math.degrees(math.atan2(dy / length, -dz / length))
    faces = {f: {"texture": tex} for f in ("north", "south", "east", "west", "up", "down")}
    return {"from": [a[0] - h, a[1] - h, a[2] - length], "to": [a[0] + h, a[1] + h, a[2]],
            "rotation": {"axis": "x", "angle": angle, "origin": a}, "faces": faces}


def inserter(name, reach, tier_tex, hand_half=HAND_HALF):
    """The base and the arm reaching toward the pickup side (north, arm progress 0)."""
    radius = HAND_REACH * reach
    shoulder = (0.5, SHOULDER_Y, 0.5)
    elbow = (0.5, SHOULDER_Y + ELBOW_RISE + ELBOW_RISE_PER_REACH * reach, 0.5 - radius * ELBOW_AT)
    hand = (0.5, SHOULDER_Y + HAND_RISE, 0.5 - radius)
    up = lambda p, d: (p[0], p[1] + d, p[2])
    joint = T + "dark_panel"
    els = placed(B + name)
    els += [segment(up(shoulder, 0.1), shoulder, SHOULDER_HALF, joint),
            segment(up(shoulder, 0.05), elbow, UPPER_HALF, tier_tex),
            segment(up(elbow, 0.045), up(elbow, -0.045), ELBOW_HALF, joint),
            segment(elbow, up(hand, 0.03), FORE_HALF, tier_tex),
            segment(up(hand, 0.03), up(hand, -0.06), hand_half, joint)]
    return els


def engine():
    els = (placed(B + "steam_engine_body") + placed(B + "steam_engine_front", dz=-24)
           + placed(B + "steam_engine_rear", dz=24))
    for model, py, pz in (("steam_engine_gear", 15, 10), ("steam_engine_gear_small", 12, -6),
                          ("steam_engine_gear_small", 21, 25)):
        els += placed(B + model, dy=py - 8, dz=pz - 8)
    return els


# (reference file, label, elements, camera yaw: 180 = from the south, 0 = from the north, 90 = from the west,
#  270 = from the east)
MACHINES = [
    ("lab.png", "lab", lambda: placed(B + "lab_body") + placed(B + "lab_dome_idle"), 180),
    ("assembling_machine_1.png", "assembling machine 1",
     lambda: placed(B + "assembling_machine_1_body") + placed(B + "assembling_machine_1_gear"), 180),
    ("assembling_machine_2.png", "assembling machine 2",
     lambda: placed(B + "assembling_machine_2_body") + placed(B + "assembling_machine_2_gear"), 180),
    ("steam_engine.png", "steam engine (gears east)", engine, 270),
    ("electric_mining_drill.png", "electric mining drill (front)",
     lambda: placed(B + "electric_mining_drill_body") + placed(B + "electric_mining_drill_head")
     + placed(B + "electric_mining_drill_carriage") + placed(B + "electric_mining_drill_tower", dy=16), 0),
    ("burner_mining_drill.png", "burner mining drill (front)",
     lambda: placed(B + "burner_mining_drill") + placed(B + "burner_mining_drill_head", 8, 0, -8), 0),
    ("boiler.png", "boiler (burning)", lambda: placed(B + "boiler_body") + placed(B + "boiler_fire"), 180),
    ("stone_furnace.png", "stone furnace (lit)", lambda: placed(B + "stone_furnace_lit"), 180),
    ("inserter.png", "inserter (in world, reaching north)", lambda: inserter("inserter", 1, T + "tier_yellow"), 90),
    ("long_handed_inserter.png", "long-handed inserter (in world)",
     lambda: inserter("long_handed_inserter", 2, T + "tier_red"), 90),
    ("filter_inserter.png", "filter inserter (in world)", lambda: inserter("filter_inserter", 1, T + "tier_purple"), 90),
    ("stack_inserter.png", "stack inserter (in world, wide hand)",
     lambda: inserter("stack_inserter", 1, T + "tier_green", STACK_HAND_HALF), 90),
    ("offshore_pump.png", "offshore pump (water north)", lambda: placed(B + "offshore_pump"), 90),
    ("small_electric_pole.png", "small electric pole (3 high)",
     lambda: placed(B + "small_electric_pole") + placed(B + "small_electric_pole_middle", dy=16)
     + placed(B + "small_electric_pole_top", dy=32), 180),
    ("roboport.png", "roboport (machinery front)",
     lambda: [e for i in (0, 1) for j in (0, 1) for k in (0, 1)
              for e in placed(B + f"roboport_{i}{j}{k}", -8 + 16 * i, 40 * k, -8 + 16 * j)], 0),
    ("construction_robot_crop.png", "construction robot", lambda: placed(B + "construction_robot"), 20),
]

if __name__ == "__main__":
    ref_dir, out = sys.argv[1], sys.argv[2]
    S, cols = 240, 3
    font = ImageFont.load_default()
    rows = (len(MACHINES) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (2 * S + 16), rows * (S + 18)), (40, 42, 48))
    d = ImageDraw.Draw(sheet)
    for i, (ref, label, load, yaw) in enumerate(MACHINES):
        x, y = (i % cols) * (2 * S + 16), (i // cols) * (S + 18)
        sheet.paste(Image.open(os.path.join(ref_dir, ref)).convert("RGB").resize((S, S)), (x, y))
        els = load()
        sheet.paste(render(els, {}, yaw=yaw, pitch=-45, size=S, extent=bounds(els)), (x + S + 2, y))
        d.text((x + 4, y + S + 3), label, fill=(230, 230, 230), font=font)
    sheet.save(out)
    print("wrote", out)
