"""Radar: textures, block/item models and a preview. Self-contained (does not import gen_models.py or
gen_blocks.py).

    python art/gen_radar.py

Reference: the Factorio 1.1 radar (wiki.factorio.com/Radar, entity animation). A 3x3 machine: four
arched truss legs at the corners (dark steel with brass lattice), a square deck with a brass trim
band, a ribbed cabinet on one side and a round motor drum on the other, a round turret in the middle
and on it a tall crescent dish (concave, dark face, brass edges) on a mast, with a feed horn in front,
a boom with a counterweight drum behind and a guy wire from the dish top to the boom. The dish and
everything on the mast turns while the radar is powered.

Outputs (in src/main/resources/assets/minetorio/):
  textures/block/radar_*.png        own textures (palette as gen_blocks.py: vanilla-like steel/brass)
  models/block/radar.json           controller: particle only (drawn by RadarRenderer)
  models/block/radar_base.json      static base, authored around the footprint's center block (x = z = 8),
                                    north orientation, y 0..24
  models/block/radar_dish.json      the turning part, drawn one block up (y = world y - 16), turning about
                                    the vertical axis through x = z = 8
  models/item/radar.json            base + dish scaled into one block
And art/radar-preview.png (via render_models.py, when it is importable).

No two faces may share a plane, face the same way and overlap (z-fighting): python -I art/check_zfight.py.
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

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


STEEL = P("#e2e5e6", "#c3c8ca", "#a6acaf", "#8a9094", "#6c7276", "#4c5154")
DARK = P("#7a8085", "#62686d", "#4f5459", "#3f4347", "#303336", "#222426")
BRASS = P("#fbe08a", "#e9bb48", "#c9952c", "#9a6c1c", "#674510")


def noisy(pal, seed, lo=1, hi=3):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), pal[rnd.choices(range(lo, hi + 1), weights=(0.1, 0.8, 0.1)[: hi - lo + 1])[0]])
    return im


def bevel(im, pal, light=0, dark=4):
    for i in range(16):
        im.putpixel((i, 0), pal[light]); im.putpixel((0, i), pal[light])
        im.putpixel((i, 15), pal[dark]); im.putpixel((15, i), pal[dark])


def rivets(im, pal):
    for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12)):
        im.putpixel((x, y), pal[0]); im.putpixel((x + 1, y), pal[2])
        im.putpixel((x, y + 1), pal[2]); im.putpixel((x + 1, y + 1), pal[4])


def textures():
    t = {}
    im = noisy(DARK, 101); bevel(im, DARK); rivets(im, DARK)
    t["radar_frame"] = im
    # deck: dark plate with a cross seam
    im = noisy(DARK, 102); bevel(im, DARK)
    for i in range(1, 15):
        im.putpixel((i, 7), DARK[4]); im.putpixel((i, 8), DARK[1])
    t["radar_deck"] = im
    # truss: dark frame with a brass X lattice (the arched legs and edge beams)
    im = Image.new("RGBA", (16, 16), DARK[4])
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 1], fill=BRASS[2]); d.rectangle([0, 14, 15, 15], fill=BRASS[3])
    for k in range(16):
        for x in (k, 15 - k):
            if 2 <= k <= 13:
                im.putpixel((x, k), BRASS[1] if x == k else BRASS[2])
    for x in (0, 15):
        for y in range(16):
            im.putpixel((x, y), DARK[2])
    t["radar_truss"] = im
    im = noisy(BRASS, 103)
    for x in range(16):
        im.putpixel((x, 4), BRASS[0]); im.putpixel((x, 11), BRASS[3])
    t["radar_brass"] = im
    t["radar_steel"] = noisy(STEEL, 104)
    t["radar_dark"] = noisy(DARK, 105, 2, 4)
    # dish face: dark grey reflector with lighter panel seams (Factorio's dish reads dark inside)
    im = noisy(DARK, 106, 1, 3)
    for y in (3, 8, 13):
        for x in range(16):
            im.putpixel((x, y), DARK[0])
    for x in (5, 10):
        for y in range(16):
            im.putpixel((x, y), DARK[3])
    t["radar_dish"] = im
    # dish back: steel ribs
    im = noisy(STEEL, 107, 2, 4)
    for x in (2, 7, 12):
        for y in range(16):
            im.putpixel((x, y), STEEL[5]); im.putpixel((x + 1, y), STEEL[1])
    t["radar_dish_back"] = im
    # vent cabinet: ribbed
    im = noisy(DARK, 108); bevel(im, DARK)
    for y in (3, 6, 9, 12):
        for x in range(2, 14):
            im.putpixel((x, y), DARK[5]); im.putpixel((x, y + 1), DARK[1])
    t["radar_vent"] = im
    os.makedirs(TEX, exist_ok=True)
    for name, im in t.items():
        im.save(os.path.join(TEX, name + ".png"))
    return t


# =====================================================================================
# Models (a minimal copy of gen_models.py's Model: boxes, octagons and 16-gon prisms)
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


class Model:
    def __init__(self, textures, particle):
        self.textures = dict(textures, particle=particle)
        self.elements = []

    def box(self, f, t, tex, only=None, rot=None, fit=()):
        for v in list(f) + list(t):
            assert -16 <= v <= 32, (f, t)
        el = {"from": [r4(v) for v in f], "to": [r4(v) for v in t]}
        if rot:
            axis, angle, origin = rot
            el["rotation"] = {"angle": angle, "axis": axis, "origin": [r4(v) for v in origin]}
        faces = {}
        for fc in only or FACES:
            var = tex[fc] if isinstance(tex, dict) else tex
            faces[fc] = {"uv": [0, 0, 16, 16] if fc in fit else crop_uv(fc, f, t), "texture": "#" + var}
        el["faces"] = faces
        self.elements.append(el)
        return self

    def cbox(self, cx, cz, w, d, y0, y1, tex, **kw):
        return self.box([cx - w / 2, y0, cz - d / 2], [cx + w / 2, y1, cz + d / 2], tex, **kw)

    def octagon_y(self, cx, cz, size, y0, y1, tex):
        self.cbox(cx, cz, size, size, y0, y1, tex)
        s = size * 0.83
        self.cbox(cx, cz, s, s, y0 + EPS, y1 - EPS, tex, rot=("y", 45, [cx, y0, cz]))
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

    def json(self):
        return {"ambientocclusion": False,
                "textures": {k: (v if ":" in v else NS + v) for k, v in self.textures.items()},
                "elements": self.elements}


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


def base_model():
    m = Model({"frame": "radar_frame", "deck": "radar_deck", "truss": "radar_truss", "brass": "radar_brass",
               "steel": "radar_steel", "dark": "radar_dark", "vent": "radar_vent"}, "radar_frame")
    # four corner legs: foot pad, post and two arched struts leaning in toward the deck
    for sx, sz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        cx = 8 + sx * 20.5
        cz = 8 + sz * 20.5
        m.cbox(cx, cz, 6, 6, 0, 2, "frame")
        m.cbox(cx, cz, 4, 4, 2, 9, "truss")
        m.cbox(cx, cz, 5, 5, 9, 10.5, "brass")
        # struts: one in the x-plane, one in the z-plane, tilted 22.5 degrees toward the center
        m.box([cx - 1.25, 2, cz - 1.25], [cx + 1.25, 14, cz + 1.25], "truss",
              rot=("z", 22.5 * sx, [cx, 2, cz]))
        m.box([cx - 1.25 + EPS, 2 + EPS, cz - 1.25 + EPS], [cx + 1.25 - EPS, 14 - EPS, cz + 1.25 - EPS], "truss",
              rot=("x", -22.5 * sz, [cx, 2, cz]))
    # edge beams joining the posts (truss lattice)
    m.box([-12, 6, -14], [28, 9, -12], "truss")
    m.box([-12, 6.02, 28], [28, 8.98, 30], "truss")
    m.box([-14, 6.04, -12], [-12, 8.96, 28], "truss")
    m.box([28, 6.06, -12], [30, 8.94, 28], "truss")
    # deck with a brass trim band
    m.chamfer(-12, -12, 28, 28, 9, 14, {"north": "frame", "south": "frame", "east": "frame", "west": "frame",
                                       "up": "deck", "down": "frame"}, c=5)
    m.chamfer(-12.5, -12.5, 28.5, 28.5, 11, 12.5, "brass", c=5)
    # side cabinet (west) and motor drum (east), as in the reference
    m.box([-15, 2, -4], [-11, 12, 18], {f: ("vent" if f == "west" else "frame") for f in FACES}, fit=("west",))
    m.octagon_y(23, 15, 7, 14, 20, {"north": "dark", "south": "dark", "east": "dark", "west": "dark",
                                    "up": "steel", "down": "dark"})
    m.octagon_y(23, 15, 8, 16.5, 17.5, "brass")
    # turret: wide ring base, brass band, steel drum
    m.prism16_y(8, 8, 10, 14, 18, "dark")
    m.prism16_y(8, 8, 10.6, 15.5, 16.5, "brass")
    m.prism16_y(8, 8, 7, 18, 24, "steel")
    return m


def dish_model():
    m = Model({"face": "radar_dish", "back": "radar_dish_back", "brass": "radar_brass", "steel": "radar_steel",
               "dark": "radar_dark", "frame": "radar_frame"}, "radar_dish")
    # mast and yoke on the turret (turret top = y 8 here)
    m.octagon_y(8, 8, 4, 8, 14, "steel")
    m.cbox(8, 8, 6, 6, 14, 17, "frame")
    # boom to the back with a counterweight drum
    m.box([7, 14.5, 11], [9, 16.5, 23], "frame")
    m.octagon_y(8, 24, 6, 11, 17, {"north": "dark", "south": "dark", "east": "dark", "west": "dark",
                                   "up": "brass", "down": "dark"})
    # the crescent dish, concave toward north (-z): a center panel and two wings each side, curving forward;
    # each pair shorter than the one inside it so the outline is a half-moon
    dish = {"north": "face", "south": "back", "east": "brass", "west": "brass", "up": "brass", "down": "brass"}
    m.box([4, 10, 3], [12, 32, 4.5], dish)
    W1, W2 = 7, 5
    a1 = math.radians(22.5)
    for side in (1, -1):
        # inner wing from the center panel's edge, turned 22.5 degrees forward
        ox = 8 + side * 4
        x0, x1 = (ox, ox + W1) if side > 0 else (ox - W1, ox)
        m.box([x0, 12, 3], [x1, 30.5, 4.5], dish, rot=("y", 22.5 * side, [ox, 12, 3.75]))
        # outer wing from the inner wing's end, turned 45 degrees forward
        ex = ox + side * W1 * math.cos(a1)
        ez = 3.75 - W1 * math.sin(a1)
        x0, x1 = (ex, ex + W2) if side > 0 else (ex - W2, ex)
        m.box([x0, 15, ez - 0.75], [x1, 27, ez + 0.75], dish, rot=("y", 45 * side, [ex, 15, ez]))
    # feed horn in front of the dish
    m.box([7.5, 20.5, -6], [8.5, 21.5, 3], "steel")
    m.box([6.75, 19.75, -8], [9.25, 22.25, -6], "brass")
    # guy wire from the dish top down to the boom end
    m.box([7.75, 30.5, 4.5], [8.25, 31, 30.5], "steel", rot=("x", 45, [8, 30.75, 4.5]))
    return m


def item(base, dish, k=0.62):
    """Base + dish (one block up) scaled by k about their bounding box center onto the block's center."""
    els = json.loads(json.dumps(base.elements))
    for el in json.loads(json.dumps(dish.elements)):
        el["from"][1] += 16
        el["to"][1] += 16
        if "rotation" in el:
            el["rotation"]["origin"][1] += 16
        els.append(el)
    lo = [min(min(e["from"][i], e["to"][i]) for e in els) for i in range(3)]
    hi = [max(max(e["from"][i], e["to"][i]) for e in els) for i in range(3)]
    c = [(lo[i] + hi[i]) / 2 for i in range(3)]

    def sc(p):
        return [r4((p[i] - c[i]) * k + 8) for i in range(3)]

    for e in els:
        e["from"], e["to"] = sc(e["from"]), sc(e["to"])
        if "rotation" in e:
            e["rotation"]["origin"] = sc(e["rotation"]["origin"])
        for v in e["from"] + e["to"]:
            assert -16 <= v <= 32, e
    tex = dict(base.textures)
    tex.update(dish.textures)
    tex["particle"] = "radar_frame"
    display = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25] * 3},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5] * 3},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
    }
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {kk: (v if ":" in v else NS + v) for kk, v in tex.items()},
            "display": display, "elements": els}


def preview():
    try:
        import sys
        sys.dont_write_bytecode = True
        sys.path.insert(0, HERE)
        import render_models as rm
    except Exception as e:   # render_models.py is optional here
        print("no preview:", e)
        return
    whole = rm.placed("minetorio:block/radar_base") + rm.placed("minetorio:block/radar_dish", dy=16)
    ext = rm.bounds(whole)
    views = [rm.render(whole, None, yaw, extent=ext, size=320) for yaw in (225, 45, 135)]
    sheet = Image.new("RGB", (320 * len(views), 320))
    for i, v in enumerate(views):
        sheet.paste(v, (320 * i, 0))
    sheet.save(os.path.join(HERE, "radar-preview.png"))


def main():
    textures()
    base, dish = base_model(), dish_model()
    dump(base.json(), os.path.join(MB, "radar_base.json"))
    dump(dish.json(), os.path.join(MB, "radar_dish.json"))
    dump({"ambientocclusion": False, "textures": {"particle": NS + "radar_frame"}, "elements": []},
         os.path.join(MB, "radar.json"))
    dump(item(base, dish), os.path.join(MI, "radar.json"))
    preview()


if __name__ == "__main__":
    main()
