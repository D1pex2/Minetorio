"""Construction robots: roboport and construction robot textures and models, and the ghost block's
particle-only model. Self-contained (does not import the other generators).

    python art/gen_robots.py

References (wiki.factorio.com, Factorio 1.1 entity art; kept outside the repository):
  Roboport: a 4x4 building, tall (here 4 blocks). A square concrete plinth with chamfered corners; on it
  a massive boxy stone-grey body with panel seams filling most of the height, and in the middle of its roof a large round hatch: a bronze/beige rim around
  a grey iris door with curved blades. At each corner a blue-grey steel charging pylon - a tall post
  with a bracket arm leaning out, white highlights and red cables running down its inside. Across the
  front (south in the reference image, the side facing the camera) a machinery bay: dark drums and a
  yellow-trimmed housing. A small antenna mast with a dish stands at one back corner.
  Construction robot: a small flying drone hanging under a ducted fan. The round turbine ring (dark
  gunmetal, radial blades inside) is on TOP with its opening facing up - what Factorio's camera sees;
  a cream/beige hood wraps the back and the sides of the ring and runs down into a bulky left pod with
  a red lamp; dark gunmetal underside with two grey claw arms dangling below.

Outputs (in src/main/resources/assets/minetorio/):
  textures/block/roboport_*.png, construction_robot_*.png
  models/block/roboport.json           controller: particle only (drawn by RoboportRenderer)
  models/block/roboport_IJK.json       the machine cut into 8 pieces (x half I, z half J, lower/upper K),
                                       piece px = machine px - offset (see RoboportRenderer.offset):
                                       offset = (-16 + 16 I, 40 K, -16 + 16 J); the machine (64 px tall)
                                       is authored around the footprint center at the bottom, north = front
  models/block/roboport_blade.json, roboport_antenna.json, roboport_charge_glow.json, roboport_lamp_{green,red}.json
                                       the moving and lit parts RoboportRenderer draws over the pieces (see the
                                       Roboport section: iris blades, antenna head, lit charging points and lamps)
  models/block/construction_robot.json the robot, ~11 px wide, centered on x = z = 8, y 0..10, north = front
  models/block/construction_robot_rotor.json  its fan rotor, same frame, spun about x = z = 8 by the renderer
  models/block/ghost.json              particle only (GhostRenderer draws the target)
  models/item/roboport.json            the whole roboport scaled into one block
  models/item/construction_robot.json  the robot model with item transforms
And art/robots-preview.png (via render_models.py).

No two faces may share a plane, face the same way and overlap (z-fighting): python -I art/check_zfight.py.
"""
import copy
import json
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "minetorio")
TEX = os.path.join(ASSETS, "textures", "block")
MB = os.path.join(ASSETS, "models", "block")
MI = os.path.join(ASSETS, "models", "item")
NS = "minetorio:block/"
FACES = ("north", "south", "east", "west", "up", "down")
EPS = 0.02


# =====================================================================================
# Textures
# =====================================================================================
def C(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def P(*hs):
    return [C(h) for h in hs]


CONCRETE = P("#b4b3ab", "#9d9c94", "#8a8982", "#77766f", "#5f5e58", "#46453f")
STONE = P("#c2c1ba", "#a9a8a1", "#94938c", "#807f78", "#6a6963", "#504f4a")
BRONZE = P("#e2c99a", "#c9a874", "#ad8a58", "#8c6c40", "#654b2a")
IRIS = P("#c4c6c3", "#a9aba8", "#8f918e", "#757774", "#575956", "#3a3b3a")
PYLON = P("#e3e8ec", "#a9b7c6", "#8798ab", "#6a7c91", "#516276", "#3a4757")
RED = P("#f0573f", "#cf3226", "#a3231c", "#741812")
DARK = P("#6c6f72", "#55585b", "#43464a", "#33363a", "#25272a", "#18191b")
YELLOW = P("#f6d055", "#e0b02c", "#b9881b", "#86600f")
CREAM = P("#f6eedb", "#e7dcc0", "#d3c5a2", "#b9a983", "#988864", "#706346")
STEEL = P("#c9ced1", "#a8aeb2", "#8a9095", "#6c7276", "#4f5357")


def noisy(pal, seed, lo=1, hi=3):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), pal[rnd.choices(range(lo, hi + 1), weights=(0.15, 0.7, 0.15)[: hi - lo + 1])[0]])
    return im


def bevel(im, pal, light=0, dark=4):
    for i in range(16):
        im.putpixel((i, 0), pal[light]); im.putpixel((0, i), pal[light])
        im.putpixel((i, 15), pal[dark]); im.putpixel((15, i), pal[dark])


GLOW = P("#ffffff", "#e6f6ff", "#b4e4ff", "#6cc4ff", "#2f8fe6", "#1b5aa8")
GREEN = P("#d8ffb0", "#7cf05a", "#35b52a", "#1d6a1c", "#1b2a1c")
WELL = P("#2a2b2e", "#1e1f22", "#151618", "#0d0e10", "#080809")


def wear(im, pal, seed, n=10):
    """Scattered scuffs: a few pixels one shade darker (dirt) or lighter (worn paint)."""
    rnd = random.Random(seed)
    for _ in range(n):
        x, y = rnd.randrange(16), rnd.randrange(16)
        c = im.getpixel((x, y))
        if c in pal:
            i = pal.index(c)
            i = min(len(pal) - 1, i + 1) if rnd.random() < 0.65 else max(0, i - 1)
            im.putpixel((x, y), pal[i])


def rivet(im, pal, x, y):
    """A rivet head: a light pixel with its shadow below right."""
    im.putpixel((x, y), pal[0])
    if x + 1 < 16 and y + 1 < 16:
        im.putpixel((x + 1, y + 1), pal[-1])


def streak(im, pal, x, y0, y1):
    """A grime run down from a seam or rivet: one shade darker."""
    for y in range(y0, y1):
        c = im.getpixel((x, y))
        if c in pal:
            im.putpixel((x, y), pal[min(len(pal) - 1, pal.index(c) + 1)])


def gradient(im, pal, top, bottom):
    """Light from above: the top rows a shade lighter (top < 0), the bottom rows darker."""
    for y in range(16):
        d = top if y < 4 else bottom if y > 11 else 0
        if not d:
            continue
        for x in range(16):
            c = im.getpixel((x, y))
            if c in pal:
                im.putpixel((x, y), pal[max(0, min(len(pal) - 1, pal.index(c) + d))])


def roboport_textures(t):
    """The roboport's 16x16 textures, after the Factorio 1.1 roboport: stone-grey plating with seams,
    rivets and grime, a brass collar, blue-steel pylons with white edges and red cables, dark machinery,
    the iris blades and the dark hatch well, and the glowing charging points and status lamps."""
    # plinth: concrete slabs with a seam cross, cracks and scuffs
    im = noisy(CONCRETE, 201); bevel(im, CONCRETE, 1, 5)
    for i in range(1, 15):
        im.putpixel((i, 8), CONCRETE[4]); im.putpixel((8, i), CONCRETE[4])
    for (x, y) in ((3, 3), (4, 4), (4, 5), (12, 11), (11, 12), (13, 3)):
        im.putpixel((x, y), CONCRETE[4])
    wear(im, CONCRETE, 301, 14)
    t["roboport_base"] = im
    # body: stone-grey armour plates in a staggered bond, seams with a lit lower lip, rivets along the
    # seams and grime running down from them
    im = noisy(STONE, 202); bevel(im, STONE, 0, 5)
    for x in range(1, 15):
        for y in (5, 11):
            im.putpixel((x, y), STONE[5]); im.putpixel((x, y + 1), STONE[1])
    for y in range(1, 5):
        im.putpixel((6, y), STONE[5]); im.putpixel((7, y), STONE[1])
    for y in range(7, 11):
        im.putpixel((11, y), STONE[5]); im.putpixel((12, y), STONE[1])
    for y in range(13, 15):
        im.putpixel((4, y), STONE[5]); im.putpixel((5, y), STONE[1])
    for (x, y) in ((2, 3), (10, 3), (13, 2), (3, 8), (8, 8), (14, 8), (2, 13), (9, 13)):
        rivet(im, STONE, x, y)
    streak(im, STONE, 3, 10, 11); streak(im, STONE, 9, 14, 15); streak(im, STONE, 8, 1, 4)
    wear(im, STONE, 302, 12)
    t["roboport_body"] = im
    # roof: tread plate (short raised bars) inside a darker border
    im = noisy(STONE, 203, 2, 3); bevel(im, STONE, 1, 5)
    for y in range(1, 15):
        for x in range(1, 14):
            if (x + 2 * y) % 6 == 0 and (x // 3 + y // 3) % 2 == 0:
                im.putpixel((x, y), STONE[1]); im.putpixel((x + 1, y), STONE[4])
    wear(im, STONE, 303, 10)
    t["roboport_roof"] = im
    # hatch iris (the item's closed door): eight curved blades meeting in the middle
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            a = (math.degrees(math.atan2(dy, dx)) + 360 + r * 11) % 45
            shade = 1 + int(a / 15)
            if a < 4:
                shade = 5
            im.putpixel((x, y), IRIS[min(shade, 5)])
    t["roboport_iris"] = im
    # one iris blade (u along the blade, v across): pale grey plate with a curved shading band, its
    # leading edge dark (the seam the reference shows between closed blades), its trailing edge lit
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            band = (y + x * 0.35) % 16
            im.putpixel((x, y), IRIS[2] if 5 <= band < 11 else IRIS[1])
    for x in range(16):
        im.putpixel((x, 0), IRIS[5]); im.putpixel((x, 1), IRIS[4]); im.putpixel((x, 15), IRIS[0])
    wear(im, IRIS, 304, 8)
    t["roboport_blade"] = im
    # the hatch well under the iris: dark, a cross-shaped robot cradle and four red lamps
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            im.putpixel((x, y), WELL[min(4, int(r / 2.2))] if r < 9 else WELL[0])
    for i in range(4, 12):
        for (x, y) in ((i, 7), (i, 8), (7, i), (8, i)):
            im.putpixel((x, y), IRIS[3])
    for (x, y) in ((7, 7), (8, 8)):
        im.putpixel((x, y), IRIS[1])
    for (x, y) in ((3, 3), (11, 3), (3, 12), (11, 12)):
        im.putpixel((x, y), RED[0]); im.putpixel((x + 1, y), RED[2])
    t["roboport_well"] = im
    # brass collar: brushed diagonally, a row of bolt heads, a dark lower seam and grime
    rnd = random.Random(204)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), BRONZE[2] if rnd.random() < 0.3 else BRONZE[1])
            if (x - y) % 9 == 0 and y < 10:
                im.putpixel((x, y), BRONZE[0])
    for x in range(16):
        im.putpixel((x, 0), BRONZE[0]); im.putpixel((x, 14), BRONZE[3]); im.putpixel((x, 15), BRONZE[4])
    for x in (1, 5, 9, 13):
        rivet(im, BRONZE, x, 11)
    wear(im, BRONZE, 305, 14)
    t["roboport_bronze"] = im
    # silver hatch lip: bright steel lit from above, a groove and a row of bolts
    im = noisy(STEEL, 214, 0, 2)
    gradient(im, STEEL, -1, 1)
    for x in range(16):
        im.putpixel((x, 4), STEEL[4]); im.putpixel((x, 5), STEEL[0])
    for x in (2, 6, 10, 14):
        rivet(im, STEEL, x, 9)
    t["roboport_rim"] = im
    # pylon: blue-grey steel lattice: a white lit edge, a dark shadow edge, cross bracing and rivets
    im = noisy(PYLON, 205, 2, 4)
    for y in range(16):
        for x in (y, 15 - y):
            if 2 <= x <= 13:
                im.putpixel((x, y), PYLON[5])
    for y in (0, 8):
        for x in range(2, 14):
            im.putpixel((x, y), PYLON[1])
    for y in range(16):
        im.putpixel((0, y), PYLON[0]); im.putpixel((1, y), PYLON[1])
        im.putpixel((14, y), PYLON[4]); im.putpixel((15, y), PYLON[5])
    for (x, y) in ((3, 2), (11, 2), (3, 10), (11, 10)):
        rivet(im, PYLON, x, y)
    t["roboport_pylon"] = im
    # white painted steel (pylon tips and edges), scuffed
    im = noisy(CREAM, 215, 0, 2)
    gradient(im, CREAM, -1, 1)
    for y in range(16):
        im.putpixel((15, y), CREAM[3])
    wear(im, CREAM, 306, 10)
    t["roboport_white"] = im
    # red cable: a twisted pair (diagonal bands), darker at the edges
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), RED[(x + y) // 2 % 2 + (2 if x in (0, 15) else 0)])
    t["roboport_cable"] = im
    # machinery: dark plates with rivets, a pair of vent slots and grime
    im = noisy(DARK, 207); bevel(im, DARK, 0, 5)
    for y in (6, 9):
        for x in range(3, 13):
            im.putpixel((x, y), DARK[5]); im.putpixel((x, y + 1), DARK[1])
    for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(im, DARK, x, y)
    streak(im, DARK, 5, 11, 15)
    wear(im, DARK, 307, 10)
    t["roboport_machinery"] = im
    # vent grille: framed horizontal louvres
    im = Image.new("RGBA", (16, 16), DARK[2])
    bevel(im, DARK, 1, 5)
    for y in range(2, 14, 3):
        for x in range(2, 14):
            im.putpixel((x, y), DARK[0]); im.putpixel((x, y + 1), DARK[4]); im.putpixel((x, y + 2), DARK[5])
    t["roboport_vent"] = im
    # drum: dark ribbed tank with a highlight down one side
    im = noisy(DARK, 208, 1, 3)
    for y in range(16):
        im.putpixel((3, y), DARK[0]); im.putpixel((4, y), DARK[1])
    for y in (2, 7, 12):
        for x in range(16):
            im.putpixel((x, y), DARK[4]); im.putpixel((x, y + 1), DARK[1])
    t["roboport_drum"] = im
    # yellow housing: hazard stripes, lit top edge, dark bottom edge
    im = noisy(YELLOW, 209, 0, 2)
    for y in range(16):
        for x in range(16):
            if (x + y) % 8 < 2:
                im.putpixel((x, y), DARK[4])
    for i in range(16):
        im.putpixel((i, 0), YELLOW[0]); im.putpixel((i, 15), YELLOW[3])
    t["roboport_yellow"] = im
    # silver pipe with flange bands
    im = noisy(STEEL, 216, 1, 3)
    for y in range(16):
        im.putpixel((4, y), STEEL[0]); im.putpixel((5, y), STEEL[0]); im.putpixel((12, y), STEEL[4])
    for y in (0, 1, 8, 9):
        for x in range(16):
            im.putpixel((x, y), STEEL[3] if y % 2 else STEEL[1])
    t["roboport_pipe"] = im
    # charging point, idle: dark blue glass in a white rim
    im = Image.new("RGBA", (16, 16), GLOW[5])
    bevel(im, CREAM, 0, 3)
    for y in range(4, 12):
        for x in range(4, 12):
            im.putpixel((x, y), C("#24426a"))
    im.putpixel((5, 5), GLOW[3])
    t["roboport_charge_off"] = im
    # charging point, lit: white-hot core, cyan and blue halo
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), GLOW[min(5, int(math.hypot(x - 7.5, y - 7.5) / 1.7))])
    t["roboport_charge_glow"] = im
    # status lamps: dark lens, lit green, lit red
    for name, pal in (("roboport_lamp_off", P("#2c3a2c", "#1f291f", "#141a14", "#0c100c")),
                      ("roboport_lamp_green", GREEN), ("roboport_lamp_red", RED)):
        im = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                im.putpixel((x, y), pal[min(len(pal) - 1, int(math.hypot(x - 6, y - 6) / 3))])
        t[name] = im
    # antenna prong: white and dark spiral bands (the reference's drill-like striped rods)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), CREAM[1] if (x + y) % 4 < 2 else DARK[3])
    t["roboport_antenna"] = im
    # antenna head: grey housing with a round lens (the reference's eye)
    im = noisy(STEEL, 217, 1, 3); bevel(im, STEEL, 0, 4)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r < 5:
                im.putpixel((x, y), DARK[5] if r < 2.5 else DARK[2] if r < 4 else STEEL[0])
    im.putpixel((6, 6), GLOW[2])
    t["roboport_antenna_head"] = im


def textures():
    t = {}
    roboport_textures(t)

    # robot
    im = noisy(CREAM, 211, 1, 3)
    for x in range(16):
        im.putpixel((x, 0), CREAM[0]); im.putpixel((x, 15), CREAM[4])
    for (x, y) in ((3, 4), (12, 4), (3, 11), (12, 11)):
        im.putpixel((x, y), CREAM[5])
    t["construction_robot_shell"] = im
    t["construction_robot_dark"] = noisy(DARK, 212, 2, 4)
    # stator on the duct floor (seen between the spinning rotor blades): 12 dark radial vanes on a black floor
    im = Image.new("RGBA", (16, 16), WELL[2])
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            a = math.degrees(math.atan2(dy, dx)) % 30
            if r < 2.2:
                c = DARK[3]
            elif r < 7.6:
                c = DARK[3] if a < 9 else WELL[1] if a < 20 else WELL[3]
            else:
                c = WELL[0]
            im.putpixel((x, y), c)
    t["construction_robot_fan"] = im
    # duct ring: gunmetal with a bright worn top lip (rows 0-1) and grime toward the bottom
    im = noisy(DARK, 214, 1, 3)
    for x in range(16):
        im.putpixel((x, 0), STEEL[1]); im.putpixel((x, 1), DARK[0]); im.putpixel((x, 15), DARK[5])
    wear(im, DARK, 215, 12)
    t["construction_robot_rim"] = im
    t["construction_robot_duct"] = noisy(WELL, 216, 1, 3)
    # rotor blades: light steel, a lit leading edge
    im = noisy(STEEL, 217, 0, 2)
    for x in range(16):
        im.putpixel((x, 0), STEEL[0]); im.putpixel((x, 15), STEEL[3])
    t["construction_robot_rotor"] = im
    im = Image.new("RGBA", (16, 16), RED[1])
    for y in range(16):
        for x in range(16):
            if math.hypot(x - 6, y - 6) < 4:
                im.putpixel((x, y), RED[0])
    t["construction_robot_light"] = im
    t["construction_robot_arm"] = noisy(STEEL, 213, 1, 3)
    os.makedirs(TEX, exist_ok=True)
    for name, im in t.items():
        im.save(os.path.join(TEX, name + ".png"))
    return t


# =====================================================================================
# Models: elements are kept as boxes until written, so the roboport can be cut into pieces
# =====================================================================================
def r4(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def crop_uv(face, f, t):
    dx, dy, dz = (t[i] - f[i] for i in range(3))
    du, dv = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy),
              "up": (dx, dz), "down": (dx, dz)}[face]
    du, dv = min(du, 16), min(dv, 16)
    return [r4(8 - du / 2), r4(8 - dv / 2), r4(8 + du / 2), r4(8 + dv / 2)]


class Box:
    def __init__(self, f, t, tex, only=None, rot=None, fit=()):
        self.f, self.t, self.tex, self.only, self.rot, self.fit = list(f), list(t), tex, only, rot, tuple(fit)

    def shifted(self, d):
        b = copy.deepcopy(self)
        b.f = [b.f[i] - d[i] for i in range(3)]
        b.t = [b.t[i] - d[i] for i in range(3)]
        if b.rot:
            axis, angle, origin = b.rot
            b.rot = (axis, angle, [origin[i] - d[i] for i in range(3)])
        return b

    def scaled(self, k, about):
        b = copy.deepcopy(self)
        b.f = [about[i] + (b.f[i] - about[i]) * k for i in range(3)]
        b.t = [about[i] + (b.t[i] - about[i]) * k for i in range(3)]
        if b.rot:
            axis, angle, origin = b.rot
            b.rot = (axis, angle, [about[i] + (origin[i] - about[i]) * k for i in range(3)])
        return b

    def center(self):
        return [(self.f[i] + self.t[i]) / 2 for i in range(3)]

    def element(self):
        for v in self.f + self.t:
            assert -16 <= v <= 32, (self.f, self.t)
        el = {"from": [r4(v) for v in self.f], "to": [r4(v) for v in self.t]}
        if self.rot:
            axis, angle, origin = self.rot
            el["rotation"] = {"angle": angle, "axis": axis, "origin": [r4(v) for v in origin]}
        faces = {}
        for fc in self.only or FACES:
            var = self.tex[fc] if isinstance(self.tex, dict) else self.tex
            faces[fc] = {"uv": [0, 0, 16, 16] if fc in self.fit else crop_uv(fc, self.f, self.t), "texture": "#" + var}
        el["faces"] = faces
        return el


class Model:
    def __init__(self, textures, particle):
        self.textures = dict(textures, particle=particle)
        self.boxes = []

    def box(self, f, t, tex, only=None, rot=None, fit=()):
        self.boxes.append(Box(f, t, tex, only, rot, fit))
        return self

    def cbox(self, cx, cz, w, d, y0, y1, tex, **kw):
        return self.box([cx - w / 2, y0, cz - d / 2], [cx + w / 2, y1, cz + d / 2], tex, **kw)

    def octagon_y(self, cx, cz, size, y0, y1, tex):
        self.cbox(cx, cz, size, size, y0, y1, tex)
        s = size * 0.83
        self.cbox(cx, cz, s, s, y0 + EPS, y1 - EPS, tex, rot=("y", 45, [cx, y0, cz]))
        return self

    def octagon_z(self, cx, cy, size, z0, z1, tex, fit=()):
        """An octagonal prism along z (a disc facing north/south); faces in fit show the whole texture."""
        self.box([cx - size / 2, cy - size / 2, z0], [cx + size / 2, cy + size / 2, z1], tex, fit=fit)
        s = size * 0.83
        self.box([cx - s / 2, cy - s / 2, z0 + EPS], [cx + s / 2, cy + s / 2, z1 - EPS], tex, rot=("z", 45, [cx, cy, z0]),
                 fit=fit)
        return self

    def octagon_x(self, cy, cz, size, x0, x1, tex):
        self.box([x0, cy - size / 2, cz - size / 2], [x1, cy + size / 2, cz + size / 2], tex)
        s = size * 0.83
        self.box([x0 + EPS, cy - s / 2, cz - s / 2], [x1 - EPS, cy + s / 2, cz + s / 2], tex, rot=("x", 45, [x0, cy, cz]))
        return self

    PRISM16 = ((0, 0), (0, 22.5), (0, -22.5), (0, 45), (1, 0), (1, 22.5), (1, -22.5), (1, 45))

    def prism16_y(self, cx, cz, a, y0, y1, tex):
        """Upright 16-gon prism of apothem a: 8 strips through the axis, each EPS shorter than the last."""
        s = a * math.tan(math.pi / 16)
        for i, (along_z, ang) in enumerate(self.PRISM16):
            e = EPS * i
            w, d = (s, a) if along_z else (a, s)
            rot = ("y", ang, [cx, y0, cz]) if ang else None
            self.box([cx - w, y0 + e, cz - d], [cx + w, y1 - e, cz + d], tex, rot=rot)
        return self

    def chamfer(self, x0, z0, x1, z1, y0, y1, tex, c=4, top=True, bottom=True):
        """A box with its four vertical edges cut off at 45 degrees; top/bottom False leave out those caps
        (for a storey stacked on another)."""
        only = tuple(f for f in FACES if (top or f != "up") and (bottom or f != "down"))
        self.box([x0, y0, z0 + c], [x1, y1, z1 - c], tex, only=only)
        self.box([x0 + c, y0 + EPS, z0], [x1 - c, y1 - EPS, z1], tex, only=only)
        s = c * math.sqrt(2)
        for (x, z) in ((x0 + c, z0 + c), (x1 - c, z0 + c), (x0 + c, z1 - c), (x1 - c, z1 - c)):
            self.cbox(x, z, s, s, y0 + 2 * EPS, y1 - 2 * EPS, tex, rot=("y", 45, [x, y0, z]), only=only)
        return self

    def json(self, boxes=None):
        return {"ambientocclusion": False,
                "textures": {k: (v if ":" in v else NS + v) for k, v in self.textures.items()},
                "elements": [b.element() for b in (self.boxes if boxes is None else boxes)]}


def dump(obj, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if obj.get("elements"):
        head = {k: v for k, v in obj.items() if k != "elements"}
        s = json.dumps(head, indent=2)[:-2]
        s += ',\n  "elements": [\n'
        s += ",\n".join("    " + json.dumps(e, separators=(", ", ": ")) for e in obj["elements"])
        s += "\n  ]\n}\n"
    else:
        s = json.dumps(obj, indent=2) + "\n"
    with open(path, "w", newline="\n") as f:
        f.write(s)


# =====================================================================================
# Roboport (machine px: x, z in -32..32 around the footprint center, y 0..64 up from the floor;
# north = front). A 4x4x4 machine after the Factorio 1.1 roboport: a massive stone-grey body, on its
# roof a brass collar round the iris hatch (robots leave and enter there, RoboportBlockEntity.DOCK_HEIGHT),
# a blue-steel charging pylon at each corner with a charging point on top, the machinery bay across the
# front, side cabinets and pipes, and a turning antenna at the back.
#
# Static parts go into the eight pieces; the moving and lit parts are their own small models that
# RoboportRenderer draws over them (each authored around a pivot at model (8, 0, 8)):
#   roboport_blade        one iris blade, drawn 8 times: blade k at BLADE_Y + k * BLADE_STEP, turned
#                         k * 45 + door * BLADE_TWIST degrees about the hatch axis and slid door * BLADE_SLIDE
#                         px out along its own +x (door 0 closed .. 1 open); open, it is hidden under the lip
#                         and the collar
#   roboport_antenna      the antenna head, turning about the mast (pivot ANTENNA)
#   roboport_charge_glow  a lit charging point, drawn over the idle one at CHARGE[i] while robots charge
#   roboport_lamp_green / _red   a lit status lamp, drawn over the dark lens at LAMPS[i] when it blinks on
# All must match RoboportRenderer and the roboport composite in check_zfight.py / ModelZFightTest.
# =====================================================================================
HATCH = (0, 1)          # roof hatch center (x, z)
ROOF_Y = 50             # top of the body shoulder
HOLE_R = 8.5            # apothem of the hatch opening in the collar
COLLAR_R = 18.5         # apothem of the brass collar
COLLAR_TOP = 55.5
LIP = (8, 11.5)         # the silver lip on the collar's inner edge (apothems)
LIP_TOP = 56.5
WELL_TOP = 52           # floor of the hatch well
BLADE_Y, BLADE_STEP, BLADE_T = 52.2, 0.1, 0.3
BLADE_X = (-0.5, 9.5)   # blade along its own x from the hatch axis, closed
BLADE_HALF = 3.6
BLADE_TWIST, BLADE_SLIDE = 60, 8.3
ANTENNA = (14, 58, 21)  # antenna pivot: top of the mast
PYLON_C = 27.5
CORNERS = ((-1, -1), (1, -1), (-1, 1), (1, 1))
CHARGE = [(PYLON_C * sx, 60, PYLON_C * sz) for sx, sz in CORNERS]  # charging point pivots (bottom center)
LAMPS = ([(-16, 17, -28.5), (-6, 17, -28.5), (24.5, 17, -9), (-24.5, 17, 9)]
         + [(PYLON_C * sx + 3.75 * sx, 7, PYLON_C * sz + 3.75 * sz) for sx, sz in CORNERS])
LAMP_HALF, LAMP_H = 0.7, 1.4
EPS8 = [EPS * i for i in range(8)]


def ring_y(m, cx, cz, r_in, r_out, y0, y1, tex, inner):
    """A hollow 16-gon ring about a vertical axis: 16 boxes, one per side, each turned to its side.
    Neighbours overlap at the inner edge, so the 8 orientations stand at 8 heights EPS apart (opposite
    sides share one). inner is the texture of the faces toward the axis."""
    half = r_out * math.tan(math.pi / 16) + 0.05
    rc = (r_in + r_out) / 2
    depth = r_out - r_in
    for i in range(16):
        theta = 22.5 * i
        n = round(theta / 90)
        phi = theta - 90 * n
        if phi == -45:
            n, phi = n - 1, 45
        x = cx + rc * math.cos(math.radians(theta))
        z = cz + rc * math.sin(math.radians(theta))
        radial_x = n % 2 == 0
        w, d = (depth, 2 * half) if radial_x else (2 * half, depth)
        # the face toward the axis: radial direction n * 90 degrees (x toward +x, z toward +z)
        inward = {0: "west", 1: "north", 2: "east", 3: "south"}[n % 4]
        faces = {f: (inner if f == inward else tex) for f in FACES}
        e = EPS8[i % 8]
        rot = ("y", -phi, [x, y0, z]) if phi else None
        m.box([x - w / 2, y0 + e, z - d / 2], [x + w / 2, y1 - e, z + d / 2], faces, rot=rot)
    return m


def roboport():
    m = Model({"base": "roboport_base", "body": "roboport_body", "roof": "roboport_roof", "bronze": "roboport_bronze",
               "rim": "roboport_rim", "well": "roboport_well", "pylon": "roboport_pylon", "white": "roboport_white",
               "cable": "roboport_cable", "mach": "roboport_machinery", "vent": "roboport_vent",
               "drum": "roboport_drum", "yellow": "roboport_yellow", "pipe": "roboport_pipe",
               "charge": "roboport_charge_off", "lamp": "roboport_lamp_off"}, "roboport_body")
    side = {"north": "body", "south": "body", "east": "body", "west": "body", "up": "roof", "down": "body"}
    band = {"north": "mach", "south": "mach", "east": "mach", "west": "mach", "up": "roof", "down": "mach"}
    # plinth: nearly the whole 4x4, chamfered corners
    m.chamfer(-31, -31, 31, 31, 0, 3, "base", c=6)
    # body: a massive stone-grey block in two storeys parted by a dark ledge (the rotated corner columns must
    # each fit one piece), then a shoulder carrying the roof
    m.chamfer(-23, -21, 23, 24, 3, 26, side, c=7, top=False)
    m.chamfer(-24.5, -22.5, 24.5, 25.5, 26, 29.5, band, c=7.5)
    m.chamfer(-23, -21, 23, 24, 29.5, 46, side, c=7, bottom=False)
    m.chamfer(-22, -20.5, 22, 23.5, 46, ROOF_Y, {f: "roof" for f in FACES}, c=6)
    # roof hatch: the brass collar, the silver lip and the dark well under the iris (the blades are drawn by
    # RoboportRenderer)
    hx, hz = HATCH
    ring_y(m, hx, hz, HOLE_R, COLLAR_R, ROOF_Y, COLLAR_TOP, "bronze", "mach")
    ring_y(m, hx, hz, LIP[0], LIP[1], COLLAR_TOP, LIP_TOP, "rim", "rim")
    m.box([hx - 8.6, ROOF_Y, hz - 8.6], [hx + 8.6, WELL_TOP, hz + 8.6],
          {f: ("well" if f == "up" else "mach") for f in FACES}, only=("up",), fit=("up",))
    # roof machinery behind the collar: a dark block and a smaller yellow-topped one, a pipe between them
    m.box([-10, ROOF_Y, 19.6], [-2.5, 54.5, 23], "mach")
    m.box([4.5, ROOF_Y, 20], [9.5, 53, 23], {f: ("yellow" if f == "up" else "mach") for f in FACES})
    m.box([-2.5, 51, 21], [4.5, 52.4, 22.4], "pipe", only=("north", "south", "up", "down"))
    # antenna: a dark base block and the mast; the head turns (roboport_antenna)
    ax, ay, az = ANTENNA
    m.cbox(ax, az, 4, 3, ROOF_Y, 53, "mach")
    m.cbox(ax, az, 2.2, 2.2, 53, ay, "pylon")
    m.cbox(ax, az, 3.4, 3.4, 55, 56, "mach")
    # front machinery bay (north): dark housing under a yellow trim band, a control box with two status lamps,
    # a big upright drum and two pipes on the east half (the reference's left as it faces the camera), two
    # U-pipes on the housing, dark vent panels above it on the body face
    m.box([-20, 3, -27.5], [20, 21, -21], {"north": "mach", "south": "mach", "east": "mach", "west": "mach",
                                           "up": "roof"}, only=("north", "south", "east", "west", "up"))
    m.box([-20.5, 20, -28], [20.5, 22.5, -20.7], "yellow")
    m.box([-18.5, 3, -29.5], [-3.5, 17, -27.5], {f: ("vent" if f == "north" else "mach") for f in FACES},
          only=("north", "east", "west", "up"), fit=("north",))
    m.octagon_y(10, -26.5, 9, 3.05, 25, {"north": "drum", "south": "drum", "east": "drum", "west": "drum",
                                       "up": "mach", "down": "mach"})
    for px in (1.5, 4.3):
        m.cbox(px, -29.4, 1.8, 1.8, 3, 33, "pipe")
    for ux in (-13, -8):
        for dx in (-1.6, 1.6):
            m.cbox(ux + dx, -25, 1.2, 1.2, 22.5, 26.5, "pipe")
        m.box([ux - 2.3, 26.5, -25.6], [ux + 2.3, 27.7, -24.4], "pipe")
    for px in (-12.5, 12.5):
        m.box([px - 8, 30, -22.4], [px + 8, 42, -21], {f: ("vent" if f == "north" else "mach") for f in FACES},
              fit=("north",))
    # side cabinets (vent faces out) with a pipe running along each side wall above them
    for sx, z0, z1 in ((1, -12, 4), (-1, -4, 12)):
        x0, x1 = sorted((23 * sx, 26 * sx))
        out = "east" if sx > 0 else "west"
        m.box([x0, 3, z0], [x1, 17, z1], {f: ("vent" if f == out else "mach") for f in FACES},
              only=(out, "north", "south", "up"), fit=(out,))
        px0, px1 = sorted((23 * sx, 24.6 * sx))
        m.box([px0, 21.4, -13], [px1, 23, 16], "pipe", only=(out, "up", "down", "north", "south"))
    # status lamps (dark lenses; RoboportRenderer lights them)
    for (lx, ly, lz) in LAMPS:
        m.cbox(lx, lz, 2 * LAMP_HALF, 2 * LAMP_HALF, ly, ly + LAMP_H, "lamp")
    # four corner charging pylons: foot, a tall blue-steel post with a white lit edge, an inner rail and a red
    # cable, braces and a cable to the body, a cap, two white bracket fingers leaning out and, between them, a
    # white stem carrying the charging point
    for sx, sz in CORNERS:
        cx, cz = PYLON_C * sx, PYLON_C * sz
        m.cbox(cx, cz, 9, 9, 3, 7, "mach")
        m.cbox(cx, cz, 6, 6, 7, 50, "pylon")
        m.cbox(cx + 3 * sx, cz + 3 * sz, 0.8, 0.8, 8, 49.5, "white")
        ix, iz = cx - 4.6 * sx, cz - 4.6 * sz
        m.cbox(ix, iz, 2.4, 2.4, 7, 47, "pylon")
        m.cbox(cx - 3.2 * sx, cz - 3.2 * sz, 1.1, 1.1, 9, 49, "cable")
        for y in (14, 36):
            bx, bz = cx - 5 * sx, cz - 5 * sz
            m.cbox(bx, bz, 9, 2.4, y, y + 2.4, "pylon", rot=("y", -45 * sx * sz, [bx, y, bz]))
        bx, bz = cx - 6 * sx, cz - 6 * sz
        m.cbox(bx, bz, 8, 1, 31, 32, "cable", rot=("y", -45 * sx * sz, [bx, 31, bz]))
        m.cbox(cx, cz, 8, 8, 50, 53, "pylon", rot=("y", 45, [cx, 50, cz]))
        m.box([cx - 1.3, 52, cz - 1.3], [cx + 1.3, 62, cz + 1.3], "white", rot=("z", -22.5 * sx, [cx, 52, cz]))
        m.box([cx - 1.3, 52.2, cz - 1.3], [cx + 1.3, 61.8, cz + 1.3], "white", rot=("x", 22.5 * sz, [cx, 52, cz]))
        m.cbox(cx, cz, 1.6, 1.6, 53, 60, "white")
        m.cbox(cx, cz, 3, 3, 60, 63, "charge")
    return m


def blade():
    """One iris blade around the hatch-axis pivot (8, 0, 8), closed, pointing along +x."""
    m = Model({"blade": "roboport_blade"}, "roboport_blade")
    m.box([8 + BLADE_X[0], 0, 8 - BLADE_HALF], [8 + BLADE_X[1], BLADE_T, 8 + BLADE_HALF], "blade")
    return m


def antenna():
    """The antenna head around the mast-top pivot (8, 0, 8): a housing with the lens (the reference's
    eye) facing north, a cap and four striped prongs, each tilted 22.5 degrees up, with spiked tips."""
    m = Model({"head": "roboport_antenna_head", "rod": "roboport_antenna", "mach": "roboport_machinery",
               "white": "roboport_white"}, "roboport_antenna_head")
    m.box([6, 0, 6.5], [10, 3.5, 9.5], "head", fit=("north", "south"))
    m.box([7, 3.5, 7], [9, 4.3, 9], "mach")
    m.box([10, 1.4, 7.5], [16, 2.4, 8.5], "rod", rot=("z", 22.5, [10, 1.9, 8]))
    m.box([15, 1.1, 7.2], [16.6, 2.7, 8.8], "white", rot=("z", 22.5, [10, 1.9, 8]))
    m.box([0, 1.4, 7.5], [6, 2.4, 8.5], "rod", rot=("z", -22.5, [6, 1.9, 8]))
    m.box([-0.6, 1.1, 7.2], [1, 2.7, 8.8], "white", rot=("z", -22.5, [6, 1.9, 8]))
    m.box([7.5, 1.4, 9.5], [8.5, 2.4, 15.5], "rod", rot=("x", -22.5, [8, 1.9, 9.5]))
    m.box([7.2, 1.1, 14.5], [8.8, 2.7, 16.1], "white", rot=("x", -22.5, [8, 1.9, 9.5]))
    m.box([7.5, 1.4, 0.5], [8.5, 2.4, 6.5], "rod", rot=("x", 22.5, [8, 1.9, 6.5]))
    m.box([7.2, 1.1, -0.1], [8.8, 2.7, 1.5], "white", rot=("x", 22.5, [8, 1.9, 6.5]))
    return m


def glow(tex, half, y0, y1):
    """A lit cap around a pivot (8, 0, 8), drawn over the idle part (slightly bigger, so no face is shared)."""
    m = Model({"glow": tex}, tex)
    m.box([8 - half, y0, 8 - half], [8 + half, y1, 8 + half], "glow", fit=FACES)
    return m


ANIMATED = {
    "roboport_blade": blade,
    "roboport_antenna": antenna,
    "roboport_charge_glow": lambda: glow("roboport_charge_glow", 1.9, -0.4, 3.4),
    "roboport_lamp_green": lambda: glow("roboport_lamp_green", LAMP_HALF + 0.1, -0.1, LAMP_H + 0.1),
    "roboport_lamp_red": lambda: glow("roboport_lamp_red", LAMP_HALF + 0.1, -0.1, LAMP_H + 0.1),
}


def blade_pose(k, door):
    """(yaw degrees, slide px, pivot) of blade k at door 0..1 (RoboportRenderer.blade)."""
    hx, hz = HATCH
    return 45 * k + door * BLADE_TWIST, door * BLADE_SLIDE, (hx, BLADE_Y + k * BLADE_STEP, hz)


CUT_Y = 28  # lower pieces hold y < CUT_Y, upper pieces the rest


def fits(b, d):
    return all(-16 <= v - d[a] <= 32 for a in range(3) for v in (b.f[a], b.t[a]))


def piece_of(b):
    """The piece holding the box's center, or failing that any piece the box fits in."""
    c = b.center()
    home = (0 if c[0] < 0 else 1, 0 if c[2] < 0 else 1, 0 if c[1] < CUT_Y else 1)
    for p in [home] + [(i, j, k) for i in range(2) for j in range(2) for k in range(2)]:
        if fits(b, offset(*p)):
            return p
    raise AssertionError(f"box fits no piece: {b.f} {b.t}")


def offset(i, j, k):
    """Must match RoboportRenderer.offset: pieces cover x -32..16 / -16..32, y -16..32 / 24..72, z like x."""
    return (-16 + 16 * i, 40 * k, -16 + 16 * j)


CUT_FACES = {0: ("east", "west"), 1: ("up", "down"), 2: ("south", "north")}


def fits_any(b):
    return any(fits(b, offset(i, j, k)) for i in range(2) for j in range(2) for k in range(2))


def split(boxes):
    """Cuts unrotated boxes that fit no piece at x = 0, then z = 0, then y = CUT_Y. The cut faces are
    interior (the other half is right there) and are dropped."""
    out = []
    todo = list(boxes)
    while todo:
        b = todo.pop()
        cut = None
        if not b.rot and not fits_any(b):
            for axis, at in ((0, 0), (2, 0), (1, CUT_Y)):
                if b.f[axis] < at < b.t[axis]:
                    cut = (axis, at)
                    break
        if cut is None:
            out.append(b)
            continue
        axis, at = cut
        lo, hi = copy.deepcopy(b), copy.deepcopy(b)
        lo.t[axis] = at
        hi.f[axis] = at
        hi_face, lo_face = CUT_FACES[axis]
        lo.only = tuple(f for f in (b.only or FACES) if f != hi_face)
        hi.only = tuple(f for f in (b.only or FACES) if f != lo_face)
        todo += [lo, hi]
    return out


def write_roboport(m):
    pieces = {}
    for b in split(m.boxes):
        pieces.setdefault(piece_of(b), []).append(b)
    for i in range(2):
        for j in range(2):
            for k in range(2):
                d = offset(i, j, k)
                boxes = [b.shifted(d) for b in pieces.get((i, j, k), [])]
                obj = m.json(boxes)
                if not boxes:
                    obj.pop("elements")
                dump(obj, os.path.join(MB, f"roboport_{i}{j}{k}.json"))
    animated = {name: make() for name, make in ANIMATED.items()}
    for name, am in animated.items():
        dump(am.json(), os.path.join(MB, name + ".json"))
    dump({"textures": {"particle": NS + "roboport_body"}}, os.path.join(MB, "roboport.json"))
    # item: the whole machine at rest scaled 1/4 into one block (64 px -> 16 px), centered: the static parts,
    # the closed iris as one disc and the antenna head; a full cube now, so the display transforms are vanilla
    # block's (minecraft:block/block)
    ant = animated["roboport_antenna"]
    item = Model(dict(m.textures, iris="roboport_iris", **ant.textures), "roboport_body")
    item.textures.pop("particle")
    item.textures["particle"] = NS + "roboport_body"
    hx, hz = HATCH
    disc = Box([hx - HOLE_R - 0.6, BLADE_Y, hz - HOLE_R - 0.6], [hx + HOLE_R + 0.6, BLADE_Y + 0.5, hz + HOLE_R + 0.6],
               "iris", only=("up",), fit=("up",))
    ax, ay, az = ANTENNA
    machine = m.boxes + [disc] + [b.shifted((8 - ax, -ay, 8 - az)) for b in ant.boxes]
    k = 0.25
    item.boxes = [b.shifted((-8 / k, 0, -8 / k)).scaled(k, (0, 0, 0)) for b in machine]
    obj = item.json()
    obj["display"] = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    }
    dump(obj, os.path.join(MI, "roboport.json"))


def roboport_elements(door=0.0, charging=0, lamps=()):
    """The roboport as RoboportRenderer draws it, as element dicts in machine px (for previews and
    render_compare.py): the pieces, the 8 iris blades at door 0..1, the antenna head (unturned),
    the first `charging` charging points lit and the lamps whose indices are in `lamps` lit green."""
    from render_models import placed
    B = "minetorio:block/"
    els = []
    for i in range(2):
        for j in range(2):
            for k in range(2):
                d = offset(i, j, k)
                els += placed(f"{B}roboport_{i}{j}{k}", d[0], d[1], d[2])
    for k in range(8):
        yaw, slide, (px, py, pz) = blade_pose(k, door)
        for e in placed(B + "roboport_blade", px - 8 + slide, py, pz - 8):
            e["rotation"] = {"axis": "y", "angle": yaw, "origin": [px, py, pz]}
            els.append(e)
    ax, ay, az = ANTENNA
    head = placed(B + "roboport_antenna", ax - 8, ay, az - 8)
    els += head
    for i, (cx, cy, cz) in enumerate(CHARGE[:charging]):
        els += placed(B + "roboport_charge_glow", cx - 8, cy, cz - 8)
    for i in lamps:
        lx, ly, lz = LAMPS[i]
        els += placed(B + "roboport_lamp_green", lx - 8, ly, lz - 8)
    return els


# =====================================================================================
# Construction robot (block px, centered on x = z = 8, north = front; ConstructionRobotRenderer)
# After the reference sprite: the ducted fan faces UP - a dark gunmetal ring on top of the robot with the rotor
# inside it (seen from Factorio's camera as the round intake) - and the body hangs under it: a cream hood wraps
# the back and the right of the ring, a bulky cream pod the left with the red lamp at its front bottom corner,
# the gunmetal belly below, and two grey claw arms dangling from it. The rotor (hub and 8 blades) is its own
# model, construction_robot_rotor, authored in the same frame and turned by the renderer about the vertical
# axis x = z = 8; only the stator below it (radial vanes on the duct floor) is part of the body.
# =====================================================================================
RING_IN, RING_OUT, RING_Y = 2.9, 3.9, (6.2, 9.2)   # duct ring (16-gon apothems, heights)
FLOOR_TOP = 6.6                                    # top of the stator disc inside the ring
ROTOR_Y, ROTOR_STEP, ROTOR_T = 7.0, 0.05, 0.35     # lowest blade bar, stagger, thickness
ROTOR_R, ROTOR_W = 2.75, 0.7                       # blade tip radius and blade width


def robot():
    m = Model({"shell": "construction_robot_shell", "dark": "construction_robot_dark", "fan": "construction_robot_fan",
               "light": "construction_robot_light", "arm": "construction_robot_arm", "rim": "construction_robot_rim",
               "duct": "construction_robot_duct"}, "construction_robot_shell")
    # the duct: a 16-gon ring on top, opening up, gunmetal outside, black inside
    ring_y(m, 8, 8, RING_IN, RING_OUT, RING_Y[0], RING_Y[1], "rim", "duct")
    # the stator on the duct floor: radial vanes (whole texture) under the spinning rotor
    m.box([5.2, 5.6, 5.2], [10.8, FLOOR_TOP, 10.8], {f: ("fan" if f == "up" else "duct") for f in FACES}, fit=("up",))
    # the body hanging under the ring: gunmetal octagonal housing and a smaller belly
    m.octagon_y(8, 8, 6.6, 2.6, 6.2, "dark")
    m.octagon_y(8, 8, 4.4, 1.6, 2.6, "dark")
    # the cream hood: up over the back edge of the ring (a lip above it) and a narrow cowl down the left side
    m.box([4.6, 3.6, 11.2], [11.4, 9.8, 12.5], "shell")
    m.box([3.5, 4.0, 7.0], [4.8, 8.6, 11.2], "shell")
    # the bulky pod wrapping the right of the ring (the reference's left, seen from the front), with the red lamp
    # at its front bottom corner
    m.box([11.2, 3.0, 5.6], [13.6, 9.4, 12.0], "shell")
    m.box([13.6, 3.4, 5.8], [13.9, 4.6, 7.0], "light", fit=("east",))
    m.box([12.2, 3.4, 5.3], [13.4, 4.6, 5.6], "light", fit=("north",))
    # two claw arms dangling below the belly, reaching forward
    for x in (6.4, 9.6):
        m.box([x - 0.45, 0.4, 6.6], [x + 0.45, 2.8, 7.4], "arm", rot=("x", -22.5, [x, 2.8, 7]))
        m.box([x - 0.6, 0, 4.8], [x + 0.6, 0.7, 6.6], "arm")
    return m


def rotor():
    """The fan rotor in the robot's frame, turning about x = z = 8: a hub and four bars through it (8 blades),
    each bar ROTOR_STEP higher than the last so the crossing faces never share a plane."""
    m = Model({"blade": "construction_robot_rotor", "hub": "construction_robot_rim"}, "construction_robot_rotor")
    for i, ang in enumerate((0, 45, 90, -45)):
        y0 = ROTOR_Y + i * ROTOR_STEP
        if ang == 90:
            m.box([8 - ROTOR_W / 2, y0, 8 - ROTOR_R], [8 + ROTOR_W / 2, y0 + ROTOR_T, 8 + ROTOR_R], "blade")
        else:
            m.box([8 - ROTOR_R, y0, 8 - ROTOR_W / 2], [8 + ROTOR_R, y0 + ROTOR_T, 8 + ROTOR_W / 2], "blade",
                  rot=("y", ang, [8, y0, 8]) if ang else None)
    m.octagon_y(8, 8, 1.6, FLOOR_TOP, 8.0, "hub")
    return m


def write_robot(m):
    dump(m.json(), os.path.join(MB, "construction_robot.json"))
    dump(rotor().json(), os.path.join(MB, "construction_robot_rotor.json"))
    # the item: body and rotor in one model (the rotor at rest), seen from above so the fan ring shows
    item = Model(dict(m.textures, blade="construction_robot_rotor"), "construction_robot_shell")
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
    }), os.path.join(MI, "construction_robot.json"))


def write_blockstates():
    bs = os.path.join(ASSETS, "blockstates")
    for name in ("roboport", "ghost"):
        dump({"variants": {"": {"model": "minetorio:block/" + name}}}, os.path.join(bs, name + ".json"))
    dump({"textures": {"particle": "minecraft:block/light_blue_stained_glass"}}, os.path.join(MB, "ghost.json"))


def main():
    textures()
    write_roboport(roboport())
    write_robot(robot())
    write_blockstates()
    try:
        sys.path.insert(0, HERE)
        from render_models import bounds, placed, render
        els = roboport_elements()
        sheet = Image.new("RGB", (3 * 256, 256), (40, 42, 48))
        sheet.paste(render(els, {}, yaw=180, pitch=-45, size=256, extent=bounds(els)), (0, 0))
        busy = roboport_elements(door=1, charging=4, lamps=range(len(LAMPS)))
        sheet.paste(render(busy, {}, yaw=0, pitch=-45, size=256, extent=bounds(busy)), (256, 0))
        r = placed("minetorio:block/construction_robot") + placed("minetorio:block/construction_robot_rotor")
        sheet.paste(render(r, {}, yaw=25, pitch=-35, size=256, extent=bounds(r)), (512, 0))
        sheet.save(os.path.join(HERE, "robots-preview.png"))
    except ImportError as e:
        print("no preview:", e)


if __name__ == "__main__":
    main()
