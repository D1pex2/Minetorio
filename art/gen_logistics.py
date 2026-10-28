"""Logistic network (stage 4): logistic chest, logistic robot and personal roboport art. Uses the model
helpers of gen_robots.py.

    python art/gen_logistics.py

References (wiki.factorio.com, Factorio 1.1 icons and entity art; kept outside the repository):
  Logistic chests (passive provider red, active provider purple, storage yellow, requester blue, buffer
  green): a squat dark olive steel box on a darker base band with corner brackets; the upper part is a
  coloured plate with four bolts and a round porthole in the middle - a brass rim round a cream dial
  with four dark curved vanes; the front carries a coloured louvred panel (horizontal slats) in a dark
  frame; small handles on the sides.
  Logistic robot: the same flying drone as the construction robot - a ducted fan facing UP on top -
  but white/silver: a round silver body under the duct ring, two dark straps crossing over the top,
  two side fins pointing down and out with red tips, a tail fin at the back with a red tip, and a thin
  antenna with a white ball at the back left.
  Personal roboport (equipment icon): a brass ring round a cream dial over a dark machine block with
  blue pipes and red cables.

Outputs (in src/main/resources/assets/minetorio/):
  textures/block/logistic_chest_*.png, logistic_robot_*.png; textures/item/personal_roboport.png
  models/block/logistic_chest.json (template), <mode>_chest.json (passive_provider ... buffer)
  models/block/logistic_robot.json, logistic_robot_rotor.json (LogisticRobotRenderer; same frame as the
                                        construction robot: centered on x = z = 8, north = front)
  models/item/<mode>_chest.json, logistic_robot.json, personal_roboport.json
  blockstates/<mode>_chest.json
And art/logistics-preview.png (via render_models.py).
"""
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gen_robots import (ASSETS, FACES, MB, MI, Model, P, TEX, dump, noisy, ring_y, wear, rivet,  # noqa: E402
                        DARK, RED, STEEL, WELL, BRONZE, rotor as construction_rotor)

ITEM_TEX = os.path.join(ASSETS, "textures", "item")

OLIVE = P("#8a8458", "#6f6a45", "#5a5638", "#46432c", "#33311f", "#22200f")
CREAM = P("#efe8cf", "#d9d0b2", "#bdb293", "#958b6c")
SILVER = P("#ffffff", "#e9ebee", "#d0d4d9", "#b3b8be", "#8e949b", "#666b71")
# Factorio's chest colours (the louvres and top plate), light to dark
COLOURS = {
    "passive_provider": P("#f0745e", "#d94435", "#b02a20", "#7d1912"),
    "active_provider": P("#c58ae8", "#9b4fc9", "#74309e", "#4f1c6e"),
    "storage": P("#f2d35a", "#d6aa1c", "#a87f0e", "#765708"),
    "requester": P("#7fd3ef", "#3fa6cc", "#26799c", "#165370"),
    "buffer": P("#7fe58f", "#34b552", "#20863a", "#145a26"),
}


# =====================================================================================
# Textures
# =====================================================================================
def textures():
    t = {}
    im = noisy(OLIVE, 401, 1, 3)
    for i in range(16):
        im.putpixel((i, 0), OLIVE[0]); im.putpixel((i, 15), OLIVE[5])
    wear(im, OLIVE, 402, 14)
    t["logistic_chest_frame"] = im
    im = noisy(OLIVE, 403, 3, 5)
    for x in (1, 14):
        for y in (2, 13):
            rivet(im, OLIVE, x, y)
    t["logistic_chest_base"] = im
    # the porthole dial: cream with four dark curved vanes round a hub (whole texture on the top face)
    im = Image.new("RGBA", (16, 16), CREAM[1])
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            a = (math.degrees(math.atan2(dy, dx)) + r * 9) % 90
            c = CREAM[1] if r > 2 else DARK[2]
            if 2 <= r < 7.2 and a < 16:
                c = DARK[3]
            elif r >= 7.2:
                c = CREAM[3]
            elif c == CREAM[1] and (x + y) % 5 == 0:
                c = CREAM[0]
            im.putpixel((x, y), c)
    t["logistic_chest_dial"] = im
    im = noisy(BRONZE, 404, 1, 3)
    for i in range(16):
        im.putpixel((i, 0), BRONZE[0]); im.putpixel((i, 15), BRONZE[4])
    t["logistic_chest_rim"] = im
    for name, pal in COLOURS.items():
        # top plate: painted steel, lit edge, four bolts
        im = noisy(pal, sum(map(ord, name)), 0, 2)
        for i in range(16):
            im.putpixel((i, 0), pal[0]); im.putpixel((0, i), pal[0])
            im.putpixel((i, 15), pal[3]); im.putpixel((15, i), pal[3])
        for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12)):
            im.putpixel((x, y), OLIVE[4]); im.putpixel((x + 1, y + 1), pal[3])
        t[f"logistic_chest_{name}_plate"] = im
        # louvres: horizontal slats, each lit on top and shadowed below, in a dark frame
        im = Image.new("RGBA", (16, 16), OLIVE[4])
        rnd = random.Random(sum(map(ord, name)) + 7)
        for y in range(1, 15):
            k = (y - 1) % 3
            for x in range(1, 15):
                c = pal[0] if k == 0 else pal[1] if k == 1 else pal[3]
                if k == 1 and rnd.random() < 0.12:
                    c = pal[2]
                im.putpixel((x, y), c)
        t[f"logistic_chest_{name}_louver"] = im

    # logistic robot
    im = noisy(SILVER, 411, 1, 3)
    for i in range(16):
        im.putpixel((i, 0), SILVER[0]); im.putpixel((i, 15), SILVER[4])
    wear(im, SILVER, 412, 10)
    t["logistic_robot_shell"] = im
    im = noisy(DARK, 413, 2, 4)
    for i in range(16):
        im.putpixel((i, 0), DARK[1])
    t["logistic_robot_dark"] = im
    im = noisy(RED, 414, 0, 2)
    for i in range(16):
        im.putpixel((i, 15), RED[3])
    t["logistic_robot_red"] = im
    im = noisy(SILVER, 415, 2, 4)
    for i in range(16):
        im.putpixel((i, 0), SILVER[0]); im.putpixel((i, 1), SILVER[1]); im.putpixel((i, 15), SILVER[5])
    t["logistic_robot_rim"] = im
    im = noisy(DARK, 416, 1, 3)
    for i in range(16):
        im.putpixel((i, 0), DARK[0]); im.putpixel((i, 15), DARK[4])
    t["logistic_robot_rotor"] = im
    im = Image.new("RGBA", (16, 16), SILVER[1])
    for y in range(16):
        for x in range(16):
            if math.hypot(x - 6, y - 6) < 4:
                im.putpixel((x, y), SILVER[0])
    t["logistic_robot_light"] = im
    os.makedirs(TEX, exist_ok=True)
    for name, im in t.items():
        im.save(os.path.join(TEX, name + ".png"))
    personal_roboport_icon()


def personal_roboport_icon():
    """16x16 item icon after the personal roboport equipment icon: brass ring and cream dial over a dark
    machine block, a blue pipe on the right and red cables."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.putpixel
    for y in range(7, 15):  # machine block
        for x in range(3, 13):
            px((x, y), DARK[2] if (x + y) % 4 else DARK[3])
    for x in range(3, 13):
        px((x, 14), DARK[4]); px((x, 7), DARK[1])
    for y in range(4, 14):  # blue pipe
        px((13, y), (90, 150, 200, 255)); px((14, y), (50, 100, 150, 255))
    px((13, 3), (150, 200, 235, 255))
    for y in range(9, 13):  # red cable
        px((2, y), RED[1])
    px((2, 8), RED[0])
    for y in range(16):  # brass ring and dial
        for x in range(16):
            r = math.hypot(x - 7.5, y - 6)
            if 3.6 <= r < 5.2:
                px((x, y), BRONZE[1] if y < 6 else BRONZE[2])
            elif r < 3.6:
                a = (math.degrees(math.atan2(y - 6, x - 7.5)) + 360) % 90
                px((x, y), DARK[3] if a < 20 and r > 1 else CREAM[1] if r > 1 else DARK[2])
    os.makedirs(ITEM_TEX, exist_ok=True)
    im.save(os.path.join(ITEM_TEX, "personal_roboport.png"))


# =====================================================================================
# Logistic chest (1x1 block, the same on all four sides: no facing)
# =====================================================================================
PLATE_TOP = 13.0
DIAL_TOP = 13.6


def chest_template():
    m = Model({"frame": "logistic_chest_frame", "base": "logistic_chest_base", "dial": "logistic_chest_dial",
               "rim": "logistic_chest_rim", "plate": "logistic_chest_passive_provider_plate",
               "louver": "logistic_chest_passive_provider_louver"}, "logistic_chest_frame")
    # base band with corner brackets
    m.box([1, 0, 1], [15, 2.5, 15], "base")
    # body: dark frame
    m.box([1.6, 2.5, 1.6], [14.4, 11, 14.4], "frame")
    # louvred panels on all four sides (they stand 0.4 px proud of the body)
    m.box([3.5, 3.5, 1.2], [12.5, 10, 1.6], "louver", only=("north", "east", "west", "up", "down"))
    m.box([3.5, 3.5, 14.4], [12.5, 10, 14.8], "louver", only=("south", "east", "west", "up", "down"))
    m.box([1.2, 3.5, 3.5], [1.6, 10, 12.5], "louver", only=("west", "north", "south", "up", "down"))
    m.box([14.4, 3.5, 3.5], [14.8, 10, 12.5], "louver", only=("east", "north", "south", "up", "down"))
    # corner posts
    for x in (1, 13.5):
        for z in (1, 13.5):
            m.box([x, 2.5, z], [x + 1.5, 11.2, z + 1.5], "frame")
    # the coloured top: a slightly wider cap
    m.box([1.2, 11.2, 1.2], [14.8, PLATE_TOP, 14.8], "plate")
    # porthole: brass rim round the dial
    ring_y(m, 8, 8, 4.0, 5.2, PLATE_TOP, DIAL_TOP + 0.6, "rim", "rim")
    m.box([4.2, PLATE_TOP, 4.2], [11.8, DIAL_TOP, 11.8], {f: ("dial" if f == "up" else "frame") for f in FACES},
          fit=("up",), only=("up",))
    # side handles
    m.box([0.4, 8, 6.5], [1.2, 9, 9.5], "frame")
    m.box([14.8, 8, 6.5], [15.6, 9, 9.5], "frame")
    return m


def write_chests():
    t = chest_template()
    dump(dict(t.json(), parent="minecraft:block/block"), os.path.join(MB, "logistic_chest.json"))
    bs = os.path.join(ASSETS, "blockstates")
    for name in COLOURS:
        cid = name + "_chest"
        dump({"parent": "minetorio:block/logistic_chest",
              "textures": {"plate": f"minetorio:block/logistic_chest_{name}_plate",
                           "louver": f"minetorio:block/logistic_chest_{name}_louver",
                           "particle": f"minetorio:block/logistic_chest_{name}_plate"}},
             os.path.join(MB, cid + ".json"))
        dump({"parent": "minetorio:block/" + cid}, os.path.join(MI, cid + ".json"))
        dump({"variants": {"": {"model": "minetorio:block/" + cid}}}, os.path.join(bs, cid + ".json"))
        # loot table: the chest drops itself (its contents drop from LogisticChestBlock.onRemove)
        dump({"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
              "entries": [{"type": "minecraft:item", "name": "minetorio:" + cid}],
              "conditions": [{"condition": "minecraft:survives_explosion"}]}],
              "random_sequence": "minetorio:blocks/" + cid},
             os.path.join(ASSETS, "..", "..", "data", "minetorio", "loot_table", "blocks", cid + ".json"))


# =====================================================================================
# Logistic robot (block px, centered on x = z = 8, north = front; LogisticRobotRenderer)
# =====================================================================================
RING_IN, RING_OUT, RING_Y = 2.9, 3.9, (6.2, 9.2)
FLOOR_TOP = 6.6


def robot():
    m = Model({"shell": "logistic_robot_shell", "dark": "logistic_robot_dark", "red": "logistic_robot_red",
               "rim": "logistic_robot_rim", "duct": "construction_robot_duct", "fan": "construction_robot_fan",
               "light": "logistic_robot_light"}, "logistic_robot_shell")
    # the duct on top, opening up, silver outside, black inside
    ring_y(m, 8, 8, RING_IN, RING_OUT, RING_Y[0], RING_Y[1], "rim", "duct")
    m.box([5.2, 5.6, 5.2], [10.8, FLOOR_TOP, 10.8], {f: ("fan" if f == "up" else "duct") for f in FACES}, fit=("up",))
    # two dark straps crossing over the ring (at different heights: their faces never share a plane)
    m.box([3.6, 9.2, 7.6], [12.4, 9.55, 8.4], "dark")
    m.box([7.55, 9.25, 3.6], [8.45, 9.6, 12.4], "dark")
    # the round silver body under the ring, and its belly
    m.octagon_y(8, 8, 7.4, 2.4, 6.2, "shell")
    m.octagon_y(8, 8, 4.6, 1.4, 2.4, "shell")
    # side fins pointing down and out, red tips
    for side in (-1, 1):
        x0 = 8 + side * 3.4
        xs = sorted((x0, x0 + side * 1.2))
        m.box([xs[0], 0.6, 7.2], [xs[1], 4.6, 8.8], "shell", rot=("z", 22.5 * side, [x0, 4.6, 8]))
        m.box([xs[0] + 0.05, -0.6, 7.25], [xs[1] - 0.05, 0.6, 8.75], "red", rot=("z", 22.5 * side, [x0, 4.6, 8]))
    # tail fin at the back pointing down, red tip
    m.box([7.4, 0.2, 11.2], [8.6, 3.6, 12.4], "shell", rot=("x", 22.5, [8, 3.6, 11.2]))
    m.box([7.45, -1.0, 11.25], [8.55, 0.2, 12.35], "red", rot=("x", 22.5, [8, 3.6, 11.2]))
    # antenna at the back left with a white ball
    m.box([4.6, 9.2, 10.6], [5.0, 13.2, 11.0], "dark")
    m.box([4.3, 13.2, 10.3], [5.3, 14.2, 11.3], "light")
    return m


def rotor():
    """The fan rotor (as the construction robot's), with the logistic robot's dark blades."""
    m = construction_rotor()
    m.textures = dict(m.textures, blade="minetorio:block/logistic_robot_rotor", hub="minetorio:block/logistic_robot_rim",
                      particle="minetorio:block/logistic_robot_rotor")
    return m


def write_robot(m):
    dump(m.json(), os.path.join(MB, "logistic_robot.json"))
    dump(rotor().json(), os.path.join(MB, "logistic_robot_rotor.json"))
    item = Model(dict(m.textures, blade="logistic_robot_rotor"), "logistic_robot_shell")
    item.boxes = m.boxes + rotor().boxes
    for box in item.boxes:
        if box.tex == "hub":
            box.tex = "rim"
    dump(dict(item.json(), display={
        "gui": {"rotation": [35, 210, 0], "translation": [0, 1.5, 0], "scale": [1.6, 1.6, 1.6]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [1.0, 1.0, 1.0]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.6, 1.6, 1.6]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.9, 0.9, 0.9]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [1.1, 1.1, 1.1]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 3, 0], "scale": [1.1, 1.1, 1.1]},
    }), os.path.join(MI, "logistic_robot.json"))
    dump({"parent": "minecraft:item/generated", "textures": {"layer0": "minetorio:item/personal_roboport"}},
         os.path.join(MI, "personal_roboport.json"))


def main():
    textures()
    write_chests()
    write_robot(robot())
    try:
        from render_models import bounds, placed, render
        sheet = Image.new("RGB", (6 * 256, 256), (40, 42, 48))
        for i, name in enumerate(COLOURS):
            els = placed(f"minetorio:block/{name}_chest")
            sheet.paste(render(els, {}, yaw=200, pitch=-35, size=256, extent=bounds(els)), (256 * i, 0))
        r = placed("minetorio:block/logistic_robot") + placed("minetorio:block/logistic_robot_rotor")
        sheet.paste(render(r, {}, yaw=200, pitch=-40, size=256, extent=bounds(r)), (5 * 256, 0))
        sheet.save(os.path.join(HERE, "logistics-preview.png"))
    except ImportError as e:
        print("no preview:", e)


if __name__ == "__main__":
    main()
