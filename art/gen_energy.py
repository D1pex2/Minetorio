"""Solar panel, accumulator, medium/big electric pole and substation: textures, block/item models and
a preview. Uses gen_radar.py's texture helpers and Model (imported, nothing of it runs).

    python art/gen_energy.py

References (Factorio 1.1 wiki entity images, kept outside the repository):
  Solar_panel_entity.png         3x3: four long blue cell panels side by side running north-south, tilted
                                 (north end raised) on a light steel frame with short legs.
  Accumulator_entity_anim.gif    2x2: four grey-blue storage cylinders on a grated steel base, a brass
                                 cross of terminals between their domed tops and thin insulator rods
                                 rising at the corners.
  Medium_electric_pole_entity    1x1: a tall square rust-brown steel lattice mast with a small head of
                                 short arms carrying white insulators.
  Big_electric_pole_entity       2x2: a dark blue-grey lattice tower, its four legs splayed at the base,
                                 a narrower lattice column and an X of lattice cross arms with insulators.
  Substation_entity              2x2: a low dark steel box with coils on its sides and red/green cables on
                                 top, a short lattice mast in the middle and three insulators at its top.

Heights (blocks; the small pole is 3, scaled from the sprites): medium 4, big 5, substation 3,
accumulator 2, solar panel 1.

Every machine is authored in machine px: origin at the footprint's center on the ground, north
orientation (model -z = the machine's front), y up. A machine taller than one block model may be
(-16..32) is cut into pieces of 32 px: piece k holds the elements that fit model y -16..32 after
moving down 32 k px; model px = machine px + (8, -32 k, 8). The renderers draw piece k 2 k blocks up
from the footprint center (MultiblockPoleRenderer, SolarPanelRenderer, AccumulatorRenderer). The
accumulator's lit lamp (accumulator_lamp_on) is authored around model (8, 0, 8) and drawn at
ACC_LAMP_POS, over the dull lamps of the body.

Outputs (src/main/resources/assets/minetorio/): textures/block/energy_*.png, models/block/<machine>.json
(particle only), models/block/<machine>_p<k>.json, models/block/accumulator_lamp_on.json,
models/item/<machine>.json, blockstates/<machine>.json; and art/energy-preview.png.
"""
import json
import math
import os
import random
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_radar as gr  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

ASSETS = gr.ASSETS
TEX, MB, MI, NS = gr.TEX, gr.MB, gr.MI, gr.NS
BS = os.path.join(ASSETS, "blockstates")
EPS = gr.EPS
FACES = gr.FACES
C, P, noisy, bevel, rivets = gr.C, gr.P, gr.noisy, gr.bevel, gr.rivets
STEEL, DARK, BRASS = gr.STEEL, gr.DARK, gr.BRASS
RUST = P("#a8735a", "#8a5a44", "#714734", "#5a3828", "#43291d", "#2e1c14")
BLUE_STEEL = P("#9aa8b8", "#7d8b9c", "#636f7f", "#4d5764", "#3a424d", "#282e36")
CELL = P("#9fd4ff", "#5aa7e6", "#3b82c4", "#2a619a", "#1c4472")
CERAMIC = P("#ffffff", "#e8ecee", "#cfd5d8", "#aab2b6")
COPPER = P("#f7b98a", "#e08a52", "#b8652f", "#86451d")

# Accumulator lamps: five along the base's front (machine px x, y, z of the lamp center at the front face).
ACC_LAMPS = 5
ACC_LAMP_POS = [(-8 + 4 * i, 2, -15) for i in range(ACC_LAMPS)]


# =====================================================================================
# Textures
# =====================================================================================
def textures():
    t = {}
    # solar cells: blue cells in a 2x3 grid with light seams and a steel rim on the long sides
    im = Image.new("RGBA", (16, 16), CELL[2])
    rnd = random.Random(201)
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), CELL[rnd.choice((1, 2, 2, 3))])
    for y in (0, 5, 10, 15):
        for x in range(16):
            im.putpixel((x, y), CELL[0])
    for x in (0, 7, 8, 15):
        for y in range(16):
            im.putpixel((x, y), STEEL[1] if x in (0, 15) else CELL[0])
    t["energy_solar_cell"] = im
    im = gr.noisy(STEEL, 202); bevel(im, STEEL)
    t["energy_frame"] = im
    t["energy_steel"] = gr.noisy(STEEL, 203)
    t["energy_dark"] = gr.noisy(DARK, 204, 2, 4)
    # grated base: dark plate with a lighter diagonal grating
    im = gr.noisy(DARK, 205, 2, 4)
    for k in range(-16, 16, 4):
        for x in range(16):
            y = x + k
            if 0 <= y < 16:
                im.putpixel((x, y), DARK[1])
    bevel(im, DARK, 1, 5)
    t["energy_grate"] = im
    # accumulator cylinder: vertically shaded blue-grey steel with two bands
    im = Image.new("RGBA", (16, 16))
    for x in range(16):
        shade = [1, 1, 0, 0, 1, 1, 2, 2, 2, 2, 3, 3, 3, 4, 4, 5][x]
        for y in range(16):
            im.putpixel((x, y), BLUE_STEEL[shade])
    for y in (3, 12):
        for x in range(16):
            im.putpixel((x, y), BLUE_STEEL[4])
    t["energy_cylinder"] = im
    im = gr.noisy(BLUE_STEEL, 206, 1, 3)
    d = ImageDraw.Draw(im)
    d.ellipse([3, 3, 12, 12], outline=BLUE_STEEL[4])
    t["energy_cylinder_top"] = im
    im = Image.new("RGBA", (16, 16), (40, 52, 60, 255))
    t["energy_lamp"] = im
    im = Image.new("RGBA", (16, 16), (150, 235, 255, 255))
    for x in range(16):
        im.putpixel((x, 0), (230, 255, 255, 255))
    t["energy_lamp_on"] = im
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), CERAMIC[2] if y % 4 == 3 else CERAMIC[1 if x < 8 else 0])
    t["energy_insulator"] = im
    t["energy_copper"] = gr.noisy(COPPER, 207)
    t["energy_brass"] = gr.noisy(BRASS, 208)
    im = gr.noisy(RUST, 209, 1, 3)
    for x in range(16):
        im.putpixel((x, 0), RUST[0]); im.putpixel((x, 15), RUST[4])
    t["energy_rust"] = im
    im = gr.noisy(BLUE_STEEL, 210, 2, 4)
    for x in range(16):
        im.putpixel((x, 0), BLUE_STEEL[0]); im.putpixel((x, 15), BLUE_STEEL[5])
    t["energy_tower"] = im
    # substation body: dark panel with vent slots
    im = gr.noisy(DARK, 211); bevel(im, DARK)
    for y in (4, 7, 10):
        for x in range(3, 13):
            im.putpixel((x, y), DARK[5]); im.putpixel((x, y + 1), DARK[1])
    rivets(im, DARK)
    t["energy_sub_body"] = im
    im = gr.noisy(DARK, 212); bevel(im, DARK)
    t["energy_sub_top"] = im
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), COPPER[1 + (y + x // 8) % 2] if y % 2 == 0 else COPPER[3])
    t["energy_coil"] = im
    t["energy_cable_red"] = gr.noisy(P("#ff7a6a", "#d23a2a", "#a8261a", "#701510"), 213)
    t["energy_cable_green"] = gr.noisy(P("#8cf08a", "#3fb83c", "#2a8a28", "#1a5c18"), 214)
    t["energy_concrete"] = gr.noisy(P("#c8c4bc", "#aeaaa2", "#96928a", "#7c7870"), 215)
    os.makedirs(TEX, exist_ok=True)
    for name, img in t.items():
        img.save(os.path.join(TEX, name + ".png"))
    return t


# =====================================================================================
# Machines in machine px (no -16..32 limit; pieces are cut afterwards)
# =====================================================================================
class Machine(gr.Model):
    def box(self, f, t, tex, only=None, rot=None, fit=()):
        el = {"from": [gr.r4(v) for v in f], "to": [gr.r4(v) for v in t]}
        if rot:
            axis, angle, origin = rot
            el["rotation"] = {"angle": angle, "axis": axis, "origin": [gr.r4(v) for v in origin]}
        faces = {}
        for fc in only or FACES:
            var = tex[fc] if isinstance(tex, dict) else tex
            faces[fc] = {"uv": [0, 0, 16, 16] if fc in fit else gr.crop_uv(fc, f, t), "texture": "#" + var}
        el["faces"] = faces
        self.elements.append(el)
        return self

    def vbox(self, cx, cz, w, d, y0, y1, tex, **kw):
        """Upright box split at every 32 px so each part fits a piece."""
        cuts = [y0] + [c for c in range(32, 200, 32) if y0 < c < y1] + [y1]
        for a, b in zip(cuts, cuts[1:]):
            self.cbox(cx, cz, w, d, a, b, tex, **kw)
        return self

    def lattice_face(self, side, half, y0, y1, bar, tex, depth=0.8):
        """Two crossing braces (+-22.5 degrees) on one face of a square mast of half-width `half` (outer),
        between y0 and y1; `side` n/s/e/w. The second brace sits 0.05 px further in, so the two never share
        a plane."""
        h = y1 - y0
        L = h / math.cos(math.radians(22.5))
        yc = (y0 + y1) / 2
        for k, ang in enumerate((22.5, -22.5)):
            inset = 0.1 + 0.05 * k
            if side in ("n", "s"):
                s = -1 if side == "n" else 1
                z0 = s * (half - inset)
                z1 = s * (half - inset - depth)
                self.box([-bar / 2, yc - L / 2, min(z0, z1)], [bar / 2, yc + L / 2, max(z0, z1)], tex,
                         rot=("z", ang, [0, yc, 0]))
            else:
                s = -1 if side == "w" else 1
                x0 = s * (half - inset)
                x1 = s * (half - inset - depth)
                self.box([min(x0, x1), yc - L / 2, -bar / 2], [max(x0, x1), yc + L / 2, bar / 2], tex,
                         rot=("x", ang, [0, yc, 0]))
        return self

    def ring(self, half, y, thick, tex, depth=0.9, step=0, post=1.5):
        """Horizontal bars round a square mast of outer half-width `half` at height y (inside the posts'
        outer faces, 0.2 px in)."""
        e = EPS * step
        a = half - 0.2
        b = half - post  # bars end at the corner posts
        self.box([-b, y + e, -a], [b, y + thick - e, -a + depth], tex)
        self.box([-b, y + e + EPS, a - depth], [b, y + thick - e - EPS, a], tex)
        self.box([-a, y + e + 2 * EPS, -b], [-a + depth, y + thick - e - 2 * EPS, b], tex)
        self.box([a - depth, y + e + 3 * EPS, -b], [a, y + thick - e - 3 * EPS, b], tex)
        return self

    def mast(self, half, post, y0, y1, rings, tex):
        """Square lattice mast: four corner posts (outer half-width `half`), rings at `rings`, X braces
        between consecutive rings on all four faces."""
        c = half - post / 2
        for sx in (-1, 1):
            for sz in (-1, 1):
                self.vbox(sx * c, sz * c, post, post, y0, y1, tex)
        for i, y in enumerate(rings):
            self.ring(half, y, 1.0, tex, post=post)
        for a, b in zip(rings, rings[1:]):
            for side in ("n", "s", "e", "w"):
                self.lattice_face(side, half, a + 1.0, b, 0.8, tex)
        return self

    def insulator(self, x, z, y0, rot=None):
        self.box([x - 0.9, y0, z - 0.9], [x + 0.9, y0 + 4, z + 0.9], {**{f: "ins" for f in FACES}}, rot=rot)
        self.box([x - 1.2, y0 + 4, z - 1.2], [x + 1.2, y0 + 5, z + 1.2], "copper", rot=rot)
        return self


def solar():
    m = Machine({"cell": "energy_solar_cell", "frame": "energy_frame", "dark": "energy_dark", "steel": "energy_steel"},
                "energy_solar_cell")
    # two base rails across the footprint (east-west), front and back
    for z in (-19, 17):
        m.box([-23, 0, z], [23, 1.5, z + 2], "frame")
    # four panels running north-south, tilted 22.5 degrees (north end up) about their middles
    for xc in (-16.5, -5.5, 5.5, 16.5):
        o = [xc, 10, 0]
        for k, (z0, z1) in enumerate(((-21, -7), (-7, 7), (7, 21))):
            m.box([xc - 5, 9.25, z0], [xc + 5, 10.75, z1],
                  {"up": "cell", "down": "dark", "north": "frame", "south": "frame", "east": "frame", "west": "frame"},
                  rot=("x", 22.5, o), fit=("up",))
        # legs: a tall one at the back, a short foot at the front, both under the panel's middle
        m.box([xc - 0.75, 1.5, -18.5], [xc + 0.75, 17, -17], "steel")
        m.box([xc - 0.75, 1.5, 17.25], [xc + 0.75, 3.5, 18.75], "steel")
        m.box([xc - 0.6, 1.5, -0.6], [xc + 0.6, 9.5, 0.6], "steel")
    return m


def accumulator():
    m = Machine({"grate": "energy_grate", "cyl": "energy_cylinder", "top": "energy_cylinder_top", "brass": "energy_brass",
                 "copper": "energy_copper", "ins": "energy_insulator", "lamp": "energy_lamp", "dark": "energy_dark"},
                "energy_cylinder")
    # grated base with the charge lamps on its front
    m.box([-15, 0, -15], [15, 4, 15], {**{f: "grate" for f in FACES}, "up": "dark"})
    for (x, y, z) in ACC_LAMP_POS:
        m.box([x - 1.2, y - 0.8, z - 0.3], [x + 1.2, y + 0.8, z], "lamp")
    # four storage cylinders with domed tops
    cyl = {"north": "cyl", "south": "cyl", "east": "cyl", "west": "cyl", "up": "top", "down": "top"}
    for sx in (-1, 1):
        for sz in (-1, 1):
            cx, cz = 7.5 * sx, 7.5 * sz
            m.octagon_y(cx, cz, 12.5, 4, 23, cyl)
            m.octagon_y(cx, cz, 9.5, 23, 25.5, cyl)
            # thin insulator rods rising at the outer corners
            rx, rz = cx + 3 * sx, cz + 3 * sz
            m.box([rx - 0.5, 23, rz - 0.5], [rx + 0.5, 29.5, rz + 0.5], "brass")
            m.box([rx - 0.9, 29.5, rz - 0.9], [rx + 0.9, 31.5, rz + 0.9], "ins")
    # brass terminal cross between the domes and a central terminal block
    m.box([-8, 25.5, -1], [8, 27, 1], "brass")
    m.box([-1, 25.52, -8], [1, 26.98, 8], "brass")
    m.box([-2.5, 25.6, -2.5], [2.5, 29, 2.5], {**{f: "copper" for f in FACES}})
    return m


def accumulator_lamp_on():
    m = gr.Model({"on": "energy_lamp_on"}, "energy_lamp_on")
    # lamp center at model (8, 0, 8) = the dull lamp's front-face center; 0.1 px bigger every way
    m.box([8 - 1.3, -0.9, 8 - 0.4], [8 + 1.3, 0.9, 8 + 0.1], "on")
    return m


def medium_pole():
    m = Machine({"rust": "energy_rust", "concrete": "energy_concrete", "ins": "energy_insulator",
                 "copper": "energy_copper", "dark": "energy_dark"}, "energy_rust")
    m.cbox(0, 0, 10, 10, 0, 2, "concrete")
    m.mast(4, 1.5, 2, 53, [2, 18, 34, 50], "rust")
    # head: cap plate, a cross of short arms and insulators (wires attach at the middle one, y 61)
    m.cbox(0, 0, 9, 9, 52, 54, "dark")
    m.box([-7, 54, -1], [7, 56, 1], "rust")
    m.box([-1, 54.02, -5], [1, 55.98, 5], "rust")
    for x in (-6, 6):
        m.insulator(x, 0, 56)
    m.insulator(0, 0, 56)
    m.insulator(0, 4, 56)
    m.insulator(0, -4, 56)
    return m


def big_pole():
    m = Machine({"tower": "energy_tower", "concrete": "energy_concrete", "ins": "energy_insulator",
                 "copper": "energy_copper", "dark": "energy_dark"}, "energy_tower")
    # splayed base: at each corner a foot and two legs leaning 22.5 degrees inward, one in each face plane
    for sx in (-1, 1):
        for sz in (-1, 1):
            cx, cz = 12.5 * sx, 12.5 * sz
            m.cbox(cx, cz, 5, 5, 0, 1.5, "concrete")
            m.box([cx - 1.25, 1.5, cz - 1.25], [cx + 1.25, 23.5, cz + 1.25], "tower", rot=("z", 22.5 * sx, [cx, 1.5, cz]))
            m.box([cx - 1.25 + EPS, 1.5 + EPS, cz - 1.25 + EPS], [cx + 1.25 - EPS, 23.5 - EPS, cz + 1.25 - EPS], "tower",
                  rot=("x", -22.5 * sz, [cx, 1.5, cz]))
    # a brace round the legs at y 10
    m.box([-9, 10, -12.9], [9, 11.4, -12.0], "tower")
    m.box([-9, 10.02, 12.0], [9, 11.38, 12.9], "tower")
    m.box([-12.9, 10.04, -9], [-12.0, 11.36, 9], "tower")
    m.box([12.0, 10.06, -9], [12.9, 11.34, 9], "tower")
    # lattice column
    m.mast(5.5, 2, 19, 69, [19, 35, 51, 67], "tower")
    # head: a cap and two crossing arms (an X in plan) with insulators at their four ends
    m.cbox(0, 0, 12, 12, 68, 70, "dark")
    m.box([-17, 70, -1], [17, 72.5, 1], "tower", rot=("y", 45, [0, 70, 0]))
    m.box([-17, 70.05, -0.95], [17, 72.45, 0.95], "tower", rot=("y", -45, [0, 70, 0]))
    d = 15 / math.sqrt(2)
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.insulator(sx * d, sz * d, 72.5)
    m.cbox(0, 0, 3, 3, 72.5, 75, "dark")
    m.box([-1.3, 75, -1.3], [1.3, 76, 1.3], "copper")
    return m


def substation():
    m = Machine({"body": "energy_sub_body", "top": "energy_sub_top", "dark": "energy_dark", "tower": "energy_tower",
                 "coil": "energy_coil", "red": "energy_cable_red", "green": "energy_cable_green",
                 "ins": "energy_insulator", "copper": "energy_copper", "cyl": "energy_cylinder"}, "energy_sub_body")
    m.box([-15, 0, -15], [15, 2, 15], "dark")
    m.chamfer(-14, -14, 14, 14, 2, 14, {"north": "body", "south": "body", "east": "body", "west": "body",
                                       "up": "top", "down": "dark"}, c=3)
    # coils on the east and west sides
    for sx in (-1, 1):
        for zc in (-6, 6):
            m.octagon_y(13 * sx, zc, 6, 3, 12, {"north": "coil", "south": "coil", "east": "coil", "west": "coil",
                                                "up": "cyl", "down": "cyl"})
    # cables over the top
    m.box([-10, 14, -6.5], [10, 15.5, -5], "red")
    m.box([-10, 14, 5], [10, 15.5, 6.5], "green")
    m.box([-6.5, 14.02, -10], [-5, 15.4, 10], "red")
    m.box([5, 14.02, -10], [6.5, 15.4, 10], "green")
    # lattice mast and its three insulators (the outer two leaning out; wires attach at the middle one, y 45)
    m.mast(4, 1.5, 14, 40, [14, 27, 39], "tower")
    m.cbox(0, 0, 9, 9, 39.5, 41, "dark")
    m.insulator(0, 0, 41)
    m.insulator(-3, 0, 41, rot=("z", 22.5, [-3, 41, 0]))
    m.insulator(3, 0, 41, rot=("z", -22.5, [3, 41, 0]))
    return m


# =====================================================================================
# Pieces, item models, blockstates
# =====================================================================================
def piece_of(el):
    y0, y1 = el["from"][1], el["to"][1]
    for k in range(0, 8):
        if y0 >= 32 * k - 16 and y1 <= 32 * k + 32:
            return k
    raise AssertionError(f"element fits no piece: {el}")


def pieces(machine):
    out = {}
    for el in machine.elements:
        k = piece_of(el)
        e = json.loads(json.dumps(el))
        for key in ("from", "to"):
            e[key] = [gr.r4(e[key][0] + 8), gr.r4(e[key][1] - 32 * k), gr.r4(e[key][2] + 8)]
            for v in e[key]:
                assert -16 <= v <= 32, (machine, el)
        if "rotation" in e:
            o = e["rotation"]["origin"]
            e["rotation"]["origin"] = [gr.r4(o[0] + 8), gr.r4(o[1] - 32 * k), gr.r4(o[2] + 8)]
        out.setdefault(k, []).append(e)
    n = max(out) + 1
    models = []
    for k in range(n):
        mdl = gr.Model(machine.textures, machine.textures["particle"])
        mdl.elements = out.get(k, [])
        models.append(mdl)
    return models


DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625] * 3},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25] * 3},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5] * 3},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
}


def item(machine, extra=()):
    """The whole machine scaled about its bounding box center onto the block's center to fit 15 px."""
    els = json.loads(json.dumps(machine.elements)) + [json.loads(json.dumps(e)) for e in extra]
    lo = [min(min(e["from"][i], e["to"][i]) for e in els) for i in range(3)]
    hi = [max(max(e["from"][i], e["to"][i]) for e in els) for i in range(3)]
    c = [(lo[i] + hi[i]) / 2 for i in range(3)]
    k = 15 / max(hi[i] - lo[i] for i in range(3))

    def sc(p):
        return [gr.r4((p[i] - c[i]) * k + 8) for i in range(3)]

    for e in els:
        e["from"], e["to"] = sc(e["from"]), sc(e["to"])
        if "rotation" in e:
            e["rotation"]["origin"] = sc(e["rotation"]["origin"])
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {kk: (v if ":" in v else NS + v) for kk, v in machine.textures.items()},
            "display": DISPLAY, "elements": els}


def blockstate(name):
    variants = {f"facing={f}": {"model": NS + name} for f in ("north", "south", "east", "west")}
    gr.dump({"variants": variants}, os.path.join(BS, name + ".json"))


MACHINES = {
    "solar_panel": solar,
    "accumulator": accumulator,
    "medium_electric_pole": medium_pole,
    "big_electric_pole": big_pole,
    "substation": substation,
}


def preview(counts):
    try:
        import render_models as rm
    except Exception as e:
        print("no preview:", e)
        return
    views = []
    for name, n in counts.items():
        whole = []
        for k in range(n):
            whole += rm.placed(f"minetorio:block/{name}_p{k}", dy=32 * k)
        ext = rm.bounds(whole)
        views.append(rm.render(whole, None, 225, extent=ext, size=260))
    sheet = Image.new("RGB", (260 * len(views), 260))
    for i, v in enumerate(views):
        sheet.paste(v, (260 * i, 0))
    sheet.save(os.path.join(HERE, "energy-preview.png"))


def main():
    textures()
    counts = {}
    for name, fn in MACHINES.items():
        mach = fn()
        ps = pieces(mach)
        counts[name] = len(ps)
        for k, p in enumerate(ps):
            gr.dump(p.json(), os.path.join(MB, f"{name}_p{k}.json"))
        gr.dump({"ambientocclusion": False, "textures": {"particle": NS + mach.textures["particle"]}, "elements": []},
                os.path.join(MB, name + ".json"))
        gr.dump(item(mach), os.path.join(MI, name + ".json"))
        blockstate(name)
        print(name, "pieces", len(ps), "elements", len(mach.elements))
    gr.dump(accumulator_lamp_on().json(), os.path.join(MB, "accumulator_lamp_on.json"))
    preview(counts)


if __name__ == "__main__":
    main()
