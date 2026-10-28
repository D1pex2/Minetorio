"""Construction robots: roboport and construction robot textures and models, and the ghost block's
particle-only model. Self-contained (does not import the other generators).

    python art/gen_robots.py

References (wiki.factorio.com, Factorio 1.1 entity art; kept outside the repository):
  Roboport: a 4x4 building. A square concrete plinth with chamfered corners; on it a lower stone-grey
  body with panel seams, and in the middle of its roof a large round hatch: a bronze/beige rim around
  a grey iris door with curved blades. At each corner a blue-grey steel charging pylon - a tall post
  with a bracket arm leaning out, white highlights and red cables running down its inside. Across the
  front (south in the reference image, the side facing the camera) a machinery bay: dark drums and a
  yellow-trimmed housing. A small antenna mast with a dish stands at one back corner.
  Construction robot: a small flying drone. A cream/beige shell wraps the top and the sides of a big
  dark turbine intake (round, radial blades) that faces forward; a side pod on each side (cream, the
  left one with a red lamp); dark gunmetal underside with two grey claw arms hanging below.

Outputs (in src/main/resources/assets/minetorio/):
  textures/block/roboport_*.png, construction_robot_*.png
  models/block/roboport.json           controller: particle only (drawn by RoboportRenderer)
  models/block/roboport_IJK.json       the machine cut into 8 pieces (x half I, z half J, lower/upper K),
                                       piece px = machine px - offset (see RoboportRenderer.offset):
                                       offset = (-16 + 16 I, 0 if K == 0 else 24, -16 + 16 J); the machine
                                       is authored around the footprint center at the bottom, north = front
  models/block/construction_robot.json the robot, ~8 px, centered on x = z = 8, y 0..10, north = front
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


def textures():
    t = {}
    # plinth: concrete slabs with a seam cross
    im = noisy(CONCRETE, 201); bevel(im, CONCRETE, 0, 5)
    for i in range(1, 15):
        im.putpixel((i, 8), CONCRETE[4]); im.putpixel((8, i), CONCRETE[4])
    t["roboport_base"] = im
    # body: stone-grey panels, horizontal seams and rivets
    im = noisy(STONE, 202); bevel(im, STONE, 0, 5)
    for x in range(1, 15):
        im.putpixel((x, 5), STONE[5]); im.putpixel((x, 6), STONE[1])
        im.putpixel((x, 11), STONE[5]); im.putpixel((x, 12), STONE[1])
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        im.putpixel((x, y), STONE[0])
    t["roboport_body"] = im
    # roof: plain stone with a dark rim line
    im = noisy(STONE, 203, 1, 3)
    t["roboport_roof"] = im
    # hatch iris: four curved blades meeting in the middle (Factorio's iris door)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            a = (math.degrees(math.atan2(dy, dx)) + 360 + r * 9) % 90
            shade = 1 + int(a / 30)
            if a < 6:
                shade = 5
            im.putpixel((x, y), IRIS[min(shade, 5)])
    t["roboport_iris"] = im
    t["roboport_bronze"] = noisy(BRONZE, 204)
    # pylon: blue-grey steel with a white highlight edge and a lattice notch
    im = noisy(PYLON, 205, 2, 4)
    for y in range(16):
        im.putpixel((1, y), PYLON[0]); im.putpixel((2, y), PYLON[1]); im.putpixel((14, y), PYLON[5])
    for y in (4, 11):
        for x in range(3, 14):
            im.putpixel((x, y), PYLON[5])
    t["roboport_pylon"] = im
    im = noisy(RED, 206, 1, 2)
    for y in range(0, 16, 4):
        for x in range(16):
            im.putpixel((x, y), RED[3])
    t["roboport_cable"] = im
    # machinery: dark plates with vent slots
    im = noisy(DARK, 207); bevel(im, DARK, 0, 5)
    for y in (4, 7, 10):
        for x in range(3, 13):
            im.putpixel((x, y), DARK[5]); im.putpixel((x, y + 1), DARK[1])
    t["roboport_machinery"] = im
    # drum: dark ribs
    im = noisy(DARK, 208, 1, 3)
    for x in range(0, 16, 3):
        for y in range(16):
            im.putpixel((x, y), DARK[0])
    t["roboport_drum"] = im
    # yellow trim with dark hazard stripes
    im = noisy(YELLOW, 209, 0, 2)
    for y in range(16):
        for x in range(16):
            if (x + y) % 8 < 2:
                im.putpixel((x, y), DARK[4])
    t["roboport_yellow"] = im

    # robot
    im = noisy(CREAM, 211, 1, 3)
    for x in range(16):
        im.putpixel((x, 0), CREAM[0]); im.putpixel((x, 15), CREAM[4])
    for (x, y) in ((3, 4), (12, 4), (3, 11), (12, 11)):
        im.putpixel((x, y), CREAM[5])
    t["construction_robot_shell"] = im
    t["construction_robot_dark"] = noisy(DARK, 212, 2, 4)
    # turbine intake: dark disc with 12 radial blades and a hub
    im = Image.new("RGBA", (16, 16), DARK[5])
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            a = math.degrees(math.atan2(dy, dx)) % 30
            if r < 2.2:
                c = DARK[1]
            elif r < 7.2:
                c = STEEL[2] if a < 12 else DARK[4]
            else:
                c = DARK[3]
            im.putpixel((x, y), c)
    t["construction_robot_fan"] = im
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

    def chamfer(self, x0, z0, x1, z1, y0, y1, tex, c=4):
        self.box([x0, y0, z0 + c], [x1, y1, z1 - c], tex)
        self.box([x0 + c, y0 + EPS, z0], [x1 - c, y1 - EPS, z1], tex)
        s = c * math.sqrt(2)
        for (x, z) in ((x0 + c, z0 + c), (x1 - c, z0 + c), (x0 + c, z1 - c), (x1 - c, z1 - c)):
            self.cbox(x, z, s, s, y0 + 2 * EPS, y1 - 2 * EPS, tex, rot=("y", 45, [x, y0, z]))
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
# Roboport (machine px: x, z in -32..32 around the footprint center, y up from the floor; north = front)
# =====================================================================================
def roboport():
    m = Model({"base": "roboport_base", "body": "roboport_body", "roof": "roboport_roof", "iris": "roboport_iris",
               "bronze": "roboport_bronze", "pylon": "roboport_pylon", "cable": "roboport_cable",
               "mach": "roboport_machinery", "drum": "roboport_drum", "yellow": "roboport_yellow"}, "roboport_body")
    # plinth: nearly the whole 4x4, chamfered corners
    m.chamfer(-31, -31, 31, 31, 0, 4, {"north": "base", "south": "base", "east": "base", "west": "base",
                                      "up": "base", "down": "base"}, c=6)
    # body: stone-grey block, chamfered, with a roof slab
    body = {"north": "body", "south": "body", "east": "body", "west": "body", "up": "roof", "down": "body"}
    m.chamfer(-22, -20, 22, 24, 4, 20, body, c=7)
    m.chamfer(-20, -18, 20, 22, 20, 22, {f: "roof" for f in FACES}, c=6)
    # roof hatch: bronze rim ring and the iris door in it
    m.prism16_y(0, 2, 13, 22, 24, "bronze")
    m.prism16_y(0, 2, 10.5, 23.9, 24.6, {"north": "bronze", "south": "bronze", "east": "bronze", "west": "bronze",
                                         "up": "iris", "down": "bronze"})
    # front machinery bay (north): dark housing with a yellow trim band and two drums lying across
    m.box([-16, 4, -28], [16, 14, -20.5], {"north": "mach", "south": "mach", "east": "mach", "west": "mach",
                                            "up": "roof", "down": "mach"}, fit=("north",))
    m.box([-16.5, 12, -28.5], [16.5, 14.5, -20.2], "yellow")
    for cx in (-9, 9):
        m.octagon_x(9, -24.8, 7, cx - 6, cx + 6, {"north": "drum", "south": "drum", "up": "drum", "down": "drum",
                                                   "east": "mach", "west": "mach"})
    # four corner charging pylons: foot, tall post leaning out, bracket head, red cable on the inside
    for sx, sz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        cx, cz = 24.5 * sx, 24.5 * sz
        m.cbox(cx, cz, 9, 9, 4, 7, "mach")
        # Minecraft only allows 0/±22.5/±45° element rotations, so the post leans out along x only
        m.box([cx - 2.5, 6, cz - 2.5], [cx + 2.5, 30, cz + 2.5], "pylon", rot=("z", -22.5 * sx, [cx, 6, cz]))
        # bracket head pointing outward diagonally, white-lit pylon steel
        hx, hz = cx + 3.5 * sx, cz + 3.5 * sz
        m.box([hx - 4, 28, hz - 4], [hx + 4, 32, hz + 4], "pylon", rot=("y", 45, [hx, 28, hz]))
        # red cable down the inner face
        m.box([cx - 3.2 * sx - 0.6, 7, cz - 0.6 - 3.2 * sz], [cx - 3.2 * sx + 0.6, 26, cz + 0.6 - 3.2 * sz], "cable",
              rot=("z", -22.5 * sx, [cx, 6, cz]))
    # antenna mast with a small dish at the back-left corner of the roof
    ax, az = -14, 15
    m.cbox(ax, az, 2.5, 2.5, 22, 34, "pylon")
    m.box([ax - 4, 32, az - 0.75], [ax + 4, 37, az + 0.75], {f: ("iris" if f in ("north", "south") else "pylon")
                                                             for f in FACES}, rot=("x", 22.5, [ax, 32, az]))
    return m


def fits(b, d):
    return all(-16 <= v - d[a] <= 32 for a in range(3) for v in (b.f[a], b.t[a]))


def piece_of(b):
    """The piece holding the box's center, or failing that any piece the box fits in."""
    c = b.center()
    home = (0 if c[0] < 0 else 1, 0 if c[2] < 0 else 1, 0 if c[1] < 24 else 1)
    for p in [home] + [(i, j, k) for i in range(2) for j in range(2) for k in range(2)]:
        if fits(b, offset(*p)):
            return p
    raise AssertionError(f"box fits no piece: {b.f} {b.t}")


def offset(i, j, k):
    """Must match RoboportRenderer.offset: pieces cover x -32..16 / -16..32, y -16..32 / 8..56, z like x."""
    return (-16 + 16 * i, 0 if k == 0 else 24, -16 + 16 * j)


CUT_FACES = {0: ("east", "west"), 1: ("up", "down"), 2: ("south", "north")}


def fits_any(b):
    return any(fits(b, offset(i, j, k)) for i in range(2) for j in range(2) for k in range(2))


def split(boxes):
    """Cuts unrotated boxes that fit no piece at x = 0, then z = 0, then y = 24. The cut faces are
    interior (the other half is right there) and are dropped."""
    out = []
    todo = list(boxes)
    while todo:
        b = todo.pop()
        cut = None
        if not b.rot and not fits_any(b):
            for axis, at in ((0, 0), (2, 0), (1, 24)):
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
    dump({"textures": {"particle": NS + "roboport_body"}}, os.path.join(MB, "roboport.json"))
    # item: the whole machine scaled 1/4 into one block (64 px -> 16 px), centered
    item = Model(dict(m.textures), "roboport_body")
    item.textures = m.textures
    k = 0.25
    item.boxes = [b.shifted((-8 / k, 0, -8 / k)).scaled(k, (0, 0, 0)) for b in m.boxes]
    obj = item.json()
    obj["display"] = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [1.0, 1.0, 1.0]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
    }
    dump(obj, os.path.join(MI, "roboport.json"))


# =====================================================================================
# Construction robot (block px, centered on x = z = 8, north = front)
# =====================================================================================
def robot():
    m = Model({"shell": "construction_robot_shell", "dark": "construction_robot_dark", "fan": "construction_robot_fan",
               "light": "construction_robot_light", "arm": "construction_robot_arm"}, "construction_robot_shell")
    # turbine: dark octagonal drum along z, its big intake (fan face) at the front
    m.octagon_z(8, 5.5, 7, 3.5, 11, {"north": "fan", "south": "dark", "east": "dark", "west": "dark",
                                    "up": "dark", "down": "dark"}, fit=("north",))
    # cream hood over the rear two thirds, wrapping down both sides; the intake stays open in front
    m.box([4.2, 8.3, 6.5], [11.8, 10, 12.4], "shell")
    m.box([3.9, 3.4, 6.8], [5.2, 8.6, 12], "shell")
    m.box([10.8, 3.4, 6.8], [12.1, 8.6, 12], "shell")
    m.box([5.4, 2.8, 10], [10.6, 8.4, 12.6], "shell")  # rear cowl
    # side pods (thrusters)
    m.box([2.4, 4, 7.4], [3.9, 7.4, 10.8], "shell")
    m.box([12.1, 4, 7.4], [13.6, 7.4, 10.8], "shell")
    m.box([2.1, 4.6, 7.9], [2.4, 5.8, 9.1], "light", only=("west",), fit=("west",))  # red lamp on the left pod
    # gunmetal belly and two claw arms hanging below, reaching forward
    m.box([6, 1.4, 5.5], [10, 2.4, 10.6], "dark")
    for x in (6.3, 9.7):
        m.box([x - 0.5, 0, 6.5], [x + 0.5, 2.4, 7.5], "arm", rot=("x", -22.5, [x, 2.4, 7]))
        m.box([x - 0.6, 0, 5.2], [x + 0.6, 0.8, 7], "arm")
    return m


def write_robot(m):
    dump(m.json(), os.path.join(MB, "construction_robot.json"))
    dump({"parent": "minetorio:block/construction_robot", "display": {
        "gui": {"rotation": [25, 210, 0], "translation": [0, 1, 0], "scale": [1.6, 1.6, 1.6]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [1.0, 1.0, 1.0]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.6, 1.6, 1.6]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.9, 0.9, 0.9]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [1.1, 1.1, 1.1]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 3, 0], "scale": [1.1, 1.1, 1.1]},
    }}, os.path.join(MI, "construction_robot.json"))


def write_blockstates():
    bs = os.path.join(ASSETS, "blockstates")
    for name in ("roboport", "ghost"):
        dump({"variants": {"": {"model": "minetorio:block/" + name}}}, os.path.join(bs, name + ".json"))
    dump({"textures": {"particle": "minecraft:block/light_blue_stained_glass"}}, os.path.join(MB, "ghost.json"))


def main():
    textures()
    rp = roboport()
    write_roboport(rp)
    write_robot(robot())
    write_blockstates()
    try:
        sys.path.insert(0, HERE)
        from render_models import bounds, placed, render
        els = []
        for i in range(2):
            for j in range(2):
                for k in range(2):
                    d = offset(i, j, k)
                    els += placed(f"minetorio:block/roboport_{i}{j}{k}", d[0] + 8, d[1], d[2] + 8)
        sheet = Image.new("RGB", (3 * 256, 256), (40, 42, 48))
        sheet.paste(render(els, {}, yaw=180, pitch=-45, size=256, extent=bounds(els)), (0, 0))
        sheet.paste(render(els, {}, yaw=0, pitch=-30, size=256, extent=bounds(els)), (256, 0))
        r = placed("minetorio:block/construction_robot")
        sheet.paste(render(r, {}, yaw=25, pitch=-35, size=256, extent=bounds(r)), (512, 0))
        sheet.save(os.path.join(HERE, "robots-preview.png"))
    except ImportError as e:
        print("no preview:", e)


if __name__ == "__main__":
    main()
