"""Create-style multi-element block/item models for Minetorio.

Writes JSON models into src/main/resources/assets/minetorio/models/{block,item}. Run after editing:

    python art/gen_models.py

Conventions (see client/machine/MachineRender): models drawn by a block entity renderer (R19) are
authored in the north orientation around the footprint's center block, i.e. the footprint center is
at x = z = 8 and every coordinate stays inside -16..32. Models on a controller's blockstate
(stone furnace, burner drill, offshore pump, splitter) are authored relative to the controller
block: width runs toward +x (east = right of north), depth toward -z (north = forward).

Boxes larger than a block are split into equal parts no longer than 16 px, so 16x16 textures keep
roughly vanilla texel density and plates show Create-like seams. Faces inside a split box are
dropped.

No two faces may share a plane, face the same way and overlap (z-fighting): overlays stand EPS
proud of the surface they decorate, copies of a box (octagons, staggered bars) are inset by EPS,
and faces covered by a flush overlay are left out. `python -I art/check_zfight.py` checks it.
"""
import json
import math
import os

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "minetorio")
MB = os.path.join(ASSETS, "models", "block")
MI = os.path.join(ASSETS, "models", "item")
NS = "minetorio:block/"

FACES = ("north", "south", "east", "west", "up", "down")
AXIS = {"x": 0, "y": 1, "z": 2}
EPS = 0.02   # offset that keeps nearly coplanar faces apart (invisible at 1/16 block per unit)


def r4(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def face_dims(face, f, t):
    """(u size, v size) of a face of the box f..t."""
    dx, dy, dz = (t[i] - f[i] for i in range(3))
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy),
            "up": (dx, dz), "down": (dx, dz)}[face]


def crop_uv(face, f, t):
    du, dv = face_dims(face, f, t)
    du, dv = min(du, 16), min(dv, 16)
    return [r4(8 - du / 2), r4(8 - dv / 2), r4(8 + du / 2), r4(8 + dv / 2)]


class Model:
    def __init__(self, textures, particle=None, parent=None):
        self.textures = dict(textures)
        if particle:
            self.textures["particle"] = particle
        self.parent = parent
        self.elements = []
        self.display = None
        self.render_type = None

    # ------------------------------------------------------------------ boxes
    def box(self, f, t, tex, skip=(), only=None, uv=None, uvrot=None, fit=(), rot=None, split=True,
            light=None, cull=None, shade=True):
        """Adds a box. tex: texture variable (without '#') or {face: var}; uv / uvrot: {face: ...};
        fit: faces that show the whole texture; rot: (axis, angle, origin)."""
        f = [float(v) for v in f]
        t = [float(v) for v in t]
        faces = [x for x in (only or FACES) if x not in skip]
        parts = [(f, t)]
        if split:
            for a in range(3):
                if rot and AXIS[rot[0]] != a:
                    continue
                new = []
                for (pf, pt) in parts:
                    n = max(1, math.ceil((pt[a] - pf[a]) / 16 - 1e-6))
                    step = (pt[a] - pf[a]) / n
                    for i in range(n):
                        a0, a1 = list(pf), list(pt)
                        a0[a] = pf[a] + step * i
                        a1[a] = pf[a] + step * (i + 1)
                        new.append((a0, a1))
                parts = new
        for (pf, pt) in parts:
            fl = []
            for fc in faces:
                # drop faces inside the split box
                a = {"north": 2, "south": 2, "east": 0, "west": 0, "up": 1, "down": 1}[fc]
                lo = fc in ("north", "west", "down")
                if lo and pf[a] > f[a] + 1e-6:
                    continue
                if not lo and pt[a] < t[a] - 1e-6:
                    continue
                fl.append(fc)
            if fl:
                self._element(pf, pt, tex, fl, uv, uvrot, fit, rot, light, cull, shade)
        return self

    def _element(self, f, t, tex, faces, uv, uvrot, fit, rot, light, cull, shade):
        for v in list(f) + list(t):
            assert -16 <= v <= 32, (f, t)
        el = {"from": [r4(v) for v in f], "to": [r4(v) for v in t]}
        if rot:
            axis, angle, origin = rot
            el["rotation"] = {"angle": angle, "axis": axis, "origin": [r4(v) for v in origin]}
        if not shade:
            el["shade"] = False
        if light:
            el["neoforge_data"] = {"block_light": light, "sky_light": light}
        fs = {}
        for fc in faces:
            var = tex[fc] if isinstance(tex, dict) else tex
            if var is None:
                continue
            if uv and fc in uv:
                u = uv[fc]
            elif fc in fit:
                u = [0, 0, 16, 16]
            else:
                u = crop_uv(fc, f, t)
            d = {"uv": u, "texture": "#" + var}
            if uvrot and fc in uvrot:
                d["rotation"] = uvrot[fc]
            if cull and fc in cull:
                d["cullface"] = fc
            fs[fc] = d
        if fs:
            el["faces"] = fs
            self.elements.append(el)

    def cbox(self, cx, cz, w, d, y0, y1, tex, **kw):
        """Box centered on (cx, cz) in plan."""
        return self.box([cx - w / 2, y0, cz - d / 2], [cx + w / 2, y1, cz + d / 2], tex, **kw)

    def octagon_y(self, cx, cz, size, y0, y1, tex, **kw):
        """Vertical octagonal prism (square + square turned 45 degrees about the y axis)."""
        self.cbox(cx, cz, size, size, y0, y1, tex, split=False, **kw)
        s = size * 0.83
        # the turned copy is EPS shorter at both ends so its caps don't share the square's planes
        self.cbox(cx, cz, s, s, y0 + EPS, y1 - EPS, tex, split=False, rot=("y", 45, [cx, y0, cz]), **kw)
        return self

    def octagon_axis(self, axis, c1, c2, size, a0, a1, tex, **kw):
        """Horizontal octagonal prism along x or z; (c1, c2) = (y, other horizontal) of its axis."""
        h = size / 2
        s = size * 0.83 / 2
        if axis == "x":
            self.box([a0, c1 - h, c2 - h], [a1, c1 + h, c2 + h], tex, **kw)
            self.box([a0 + EPS, c1 - s, c2 - s], [a1 - EPS, c1 + s, c2 + s], tex, rot=("x", 45, [a0, c1, c2]), **kw)
        else:
            self.box([c2 - h, c1 - h, a0], [c2 + h, c1 + h, a1], tex, **kw)
            self.box([c2 - s, c1 - s, a0 + EPS], [c2 + s, c1 + s, a1 - EPS], tex, rot=("z", 45, [c2, c1, a0]), **kw)
        return self

    def gear_y(self, cx, cz, radius, y0, y1, tex_disc, tex_teeth, tooth=3, bars=8, width=3):
        """Create-style cogwheel lying flat: octagonal disc plus radial tooth bars (8 bars = 16 teeth)."""
        self.octagon_y(cx, cz, radius * 2, y0, y1, tex_disc)
        L = radius + tooth
        x_bars = [0, 22.5, -22.5, 45, -45][: max(1, bars - 3)]
        z_bars = [0, 22.5, -22.5][: max(0, bars - 5)]
        for ang in x_bars:
            kw = {"rot": ("y", ang, [cx, y0, cz])} if ang else {}
            self.box([cx - L, y0 + 0.5, cz - width / 2], [cx + L, y1 - 0.5, cz + width / 2], tex_teeth,
                     split=False, **kw)
        for ang in z_bars:
            kw = {"rot": ("y", ang, [cx, y0, cz])} if ang else {}
            self.box([cx - width / 2, y0 + 0.5, cz - L], [cx + width / 2, y1 - 0.5, cz + L], tex_teeth,
                     split=False, **kw)
        return self

    def flange_z(self, cx, cy, z0, z1, tex="flange", size=10):
        return self.box([cx - size / 2, cy - size / 2, z0], [cx + size / 2, cy + size / 2, z1], tex, split=False)

    def flange_x(self, cy, cz, x0, x1, tex="flange", size=10):
        return self.box([x0, cy - size / 2, cz - size / 2], [x1, cy + size / 2, cz + size / 2], tex, split=False)

    def pipe_z(self, cx, cy, z0, z1, size=7, skip=()):
        """skip: ends covered by a flush flange."""
        h = size / 2
        return self.box([cx - h, cy - h, z0], [cx + h, cy + h, z1],
                        {"east": "pipe_h", "west": "pipe_h", "up": "pipe_v", "down": "pipe_v",
                         "north": "pipe_end", "south": "pipe_end"}, skip=skip, split=False)

    def pipe_x(self, cy, cz, x0, x1, size=7, skip=()):
        h = size / 2
        return self.box([x0, cy - h, cz - h], [x1, cy + h, cz + h],
                        {"north": "pipe_h", "south": "pipe_h", "up": "pipe_h", "down": "pipe_h",
                         "east": "pipe_end", "west": "pipe_end"},
                        uvrot={"up": 90, "down": 90}, skip=skip, split=False)

    def pipe_y(self, cx, cz, y0, y1, size=7):
        h = size / 2
        return self.box([cx - h, y0, cz - h], [cx + h, y1, cz + h],
                        {"north": "pipe_v", "south": "pipe_v", "east": "pipe_v", "west": "pipe_v",
                         "up": "pipe_end", "down": "pipe_end"}, split=False)

    def shifted(self, dx, dy, dz):
        out = []
        for el in self.elements:
            e = json.loads(json.dumps(el))
            e["from"] = [r4(e["from"][0] + dx), r4(e["from"][1] + dy), r4(e["from"][2] + dz)]
            e["to"] = [r4(e["to"][0] + dx), r4(e["to"][1] + dy), r4(e["to"][2] + dz)]
            if "rotation" in e:
                o = e["rotation"]["origin"]
                e["rotation"]["origin"] = [r4(o[0] + dx), r4(o[1] + dy), r4(o[2] + dz)]
            for v in e["from"] + e["to"]:
                assert -16 <= v <= 32, e
            out.append(e)
        return out

    # ------------------------------------------------------------------ output
    def json(self):
        j = {}
        if self.parent:
            j["parent"] = self.parent
        if self.render_type:
            j["render_type"] = self.render_type
        j["ambientocclusion"] = False
        j["textures"] = {k: (v if ":" in v or v.startswith("#") else NS + v) for k, v in self.textures.items()}
        if self.display:
            j["display"] = self.display
        if self.elements or not self.parent:
            j["elements"] = self.elements
        return j


def dump(obj, path):
    """Compact JSON: one element per line."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if "elements" in obj:
        head = {k: v for k, v in obj.items() if k != "elements"}
        s = json.dumps(head, indent=2)[:-2]
        s += ',\n  "elements": [\n'
        s += ",\n".join("    " + json.dumps(e, separators=(", ", ": ")) for e in obj["elements"])
        s += "\n  ]\n}\n"
    else:
        s = json.dumps(obj, indent=2) + "\n"
    with open(path, "w", newline="\n") as f:
        f.write(s)


written = {}


def save_block(name, m):
    written["block/" + name] = m
    dump(m.json(), os.path.join(MB, name + ".json"))


def save_item(name, obj):
    written["item/" + name] = obj
    dump(obj, os.path.join(MI, name + ".json"))


# ---------------------------------------------------------------------- item display transforms
BASE_DISPLAY = {
    "gui": ([30, 225, 0], [0, 0, 0], 0.625),
    "ground": ([0, 0, 0], [0, 3, 0], 0.25),
    "fixed": ([0, 0, 0], [0, 0, 0], 0.5),
    "thirdperson_righthand": ([75, 45, 0], [0, 2.5, 0], 0.375),
    "firstperson_righthand": ([0, 45, 0], [0, 0, 0], 0.40),
    "firstperson_lefthand": ([0, 225, 0], [0, 0, 0], 0.40),
}


def rot_xyz(deg):
    x, y, z = (math.radians(v) for v in deg)
    rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return rx @ ry @ rz


def display_for(elements, hand_boost=1.4):
    """Display transforms that fit the model's bounding box into one block, centered."""
    lo = np.array([min(e["from"][i] for e in elements) for i in range(3)], float)
    hi = np.array([max(e["to"][i] for e in elements) for i in range(3)], float)
    size = max(hi - lo)
    c = (lo + hi) / 2 - 8
    k = min(1.0, 16 / size)
    out = {}
    for ctx, (rot, tr, s) in BASE_DISPLAY.items():
        sc = s * k * (hand_boost if "person" in ctx and k < 1 else 1)
        t = np.array(tr, float) - sc * (rot_xyz(rot) @ c)
        out[ctx] = {"rotation": rot, "translation": [r4(np.clip(v, -80, 80)) for v in t],
                    "scale": [r4(sc)] * 3}
    return out


def item_from(name, *models, offsets=None, particle=None, hand_boost=1.4):
    """Item model with all elements of the given block models (shifted by offsets) + fitted display."""
    els = []
    tex = {}
    for i, m in enumerate(models):
        dx, dy, dz = offsets[i] if offsets else (0, 0, 0)
        els += m.shifted(dx, dy, dz)
        tex.update(m.textures)
    j = {"parent": "minecraft:block/block", "ambientocclusion": False,
         "textures": {k: (v if ":" in v else NS + v) for k, v in tex.items()},
         "display": display_for(els, hand_boost), "elements": els}
    if particle:
        j["textures"]["particle"] = NS + particle
    save_item(name, j)


def item_parent(name, parent):
    save_item(name, {"parent": "minetorio:block/" + parent})


def item_flat(name):
    save_item(name, {"parent": "minecraft:item/generated", "textures": {"layer0": "minetorio:item/" + name}})


def empty(name, particle):
    """Controller block model of an R19 machine: nothing but the break particle."""
    m = Model({}, particle)
    save_block(name, m)


TIER_COLOR = {"": "tier_yellow", "fast_": "tier_red", "express_": "tier_blue"}

# =====================================================================================
# Assembling machines (3x3, body + spinning gear; footprint center at 8, 8)
# =====================================================================================


def assembler_body(accent):
    m = Model({"frame": "dark_panel", "body": "assembler_panel", "top": "steel_panel", "accent": accent + "_panel",
               "pillar": "dark_panel", "window": "window", "vent": "vent", "steel": "steel", "dark": "dark"},
              "assembler_panel")
    m.box([-16, 0, -16], [32, 3, 32], "frame")
    m.box([-14, 3, -14], [30, 20, 30], {"north": "body", "south": "body", "east": "body", "west": "body",
                                         "up": "top", "down": "frame"})
    for x in (-16, 25):
        for z in (-16, 25):
            m.box([x, 3, z], [x + 7, 24, z + 7], "pillar")
            m.box([x + 1, 24, z + 1], [x + 6, 25, z + 6], "accent")
    # accent band under the roof (tier color) and the roof deck
    m.box([-15, 20, -15], [31, 22, 31], "accent")
    m.box([-13, 22, -13], [29, 23, 29], {f: "dark" for f in FACES})
    # front: window + two side vents; back: vent row
    m.box([1, 7, -15], [15, 18, -14], "window", fit=("north",), skip=("south",), split=False)
    m.box([-10, 6, -15], [-2, 14, -14], "vent", fit=("north",), skip=("south",), split=False)
    m.box([18, 6, -15], [26, 14, -14], "vent", fit=("north",), skip=("south",), split=False)
    m.box([-6, 6, 30], [22, 14, 31], "vent", fit=("south",), skip=("north",))
    # side hatches
    for x0, x1, fc in ((-15, -14, "west"), (30, 31, "east")):
        m.box([x0, 6, -2], [x1, 16, 18], "accent", only=(fc, "up", "down", "north", "south"))
    return m


def assembler_gear():
    m = Model({"disc": "steel_panel", "teeth": "steel", "hub": "gear_hub", "shaft": "brass"}, "steel")
    m.octagon_y(8, 8, 8, 23, 26, "shaft")
    m.gear_y(8, 8, 14, 26, 29, "disc", "teeth", tooth=4, bars=8, width=4)
    m.octagon_y(8, 8, 9, 29, 31, "hub")
    for ang in (0, 45, -45):
        kw = {"rot": ("y", ang, [8, 29, 8])} if ang else {}
        m.box([-3, 29, 7], [19, 30, 9], "shaft", split=False, **kw)
    m.box([7, 29, -3], [9, 30, 19], "shaft", split=False)
    return m


gear = assembler_gear()
save_block("assembling_machine_gear", gear)
for tier, accent in ((1, "tier_yellow"), (2, "tier_blue")):
    body = assembler_body(accent)
    save_block(f"assembling_machine_{tier}_body", body)
    empty(f"assembling_machine_{tier}", "assembler_panel")
    item_from(f"assembling_machine_{tier}", body, gear)

# =====================================================================================
# Electric mining drill (3x3, body + spinning head)
# =====================================================================================
edb = Model({"frame": "dark_panel", "body": "steel_panel", "dark": "dark", "hazard": "hazard", "vent": "vent",
             "accent": "tier_yellow_panel", "steel": "steel", "chute": "dark_panel"}, "steel_panel")
edb.box([-16, 0, -16], [32, 3, 32], "frame")
for x in (-16, 26):
    for z in (-16, 26):
        edb.box([x, 3, z], [x + 6, 8, z + 6], "accent")
edb.box([-13, 3, -13], [29, 13, 29], {"north": "body", "south": "body", "east": "vent", "west": "vent",
                                      "up": "body", "down": "frame"})
edb.box([-13.5, 3, -14], [29.5, 5, -13], "hazard", only=("north", "up"))
edb.box([-9, 13, -9], [25, 16, 25], "dark")
edb.octagon_y(8, 8, 20, 16, 19, "steel")
# output chute on the front edge
edb.box([2, 4, -15.5], [14, 11, -13], "chute", split=False)
edb.box([3, 4, -16], [13, 9, -15.5], "dark", only=("north",), split=False)
# side motors
for x0, x1 in ((-16, -13), (29, 32)):
    edb.box([x0, 3, 0], [x1, 11, 16], "frame", split=False)
save_block("electric_mining_drill_body", edb)

edh = Model({"bit": "drill_bit", "arm": "steel", "tip": "tier_yellow", "hub": "gear_hub", "dark": "dark"}, "drill_bit")
edh.octagon_y(8, 8, 9, 19, 30, "bit")
# crossing arms: each EPS thinner than the previous so their tops and bottoms never share a plane
for i, ang in enumerate((0, 45, -45)):
    kw = {"rot": ("y", ang, [8, 22, 8])} if ang else {}
    edh.box([-6, 22 + EPS * i, 6], [22, 25 - EPS * i, 10], "arm", split=False, **kw)
edh.box([6, 22 + EPS * 3, -6], [10, 25 - EPS * 3, 22], "arm", split=False)
for (x, z) in ((-7, 8), (23, 8), (8, -7), (8, 23)):
    edh.cbox(x, z, 4 + 2 * EPS, 4 + 2 * EPS, 21, 26, "tip", split=False)   # proud of the arm sides
edh.octagon_y(8, 8, 6, 30, 32, "hub")
save_block("electric_mining_drill_head", edh)
empty("electric_mining_drill", "steel_panel")
item_from("electric_mining_drill", edb, edh)

# =====================================================================================
# Burner mining drill (2x2 on the controller's blockstate; head drawn by the BER on the center)
# footprint: x 0..32, z -16..16; front (output) = north
# =====================================================================================
bmd = Model({"body": "rust_panel", "frame": "dark_panel", "dark": "dark", "mouth": "furnace_mouth",
             "chute": "dark_panel", "steel": "steel"}, "rust_panel")
bmd.box([0, 0, -16], [32, 3, 16], "frame")
bmd.box([2, 3, -14], [30, 17, 14], "body")
bmd.box([1, 3, -15], [6, 18, -10], "frame", split=False)
bmd.box([26, 3, -15], [31, 18, -10], "frame", split=False)
bmd.box([1, 3, 10], [6, 18, 15], "frame", split=False)
bmd.box([26, 3, 10], [31, 18, 15], "frame", split=False)
bmd.box([10, 4, 14], [22, 12, 15], "mouth", only=("south",), fit=("south",), split=False)
bmd.box([9, 3, 14], [23, 13, 15.5], "dark", skip=("south",), split=False)
bmd.box([11, 4, -15.5], [21, 11, -14], "chute", split=False)
bmd.box([12, 5, -16], [20, 9, -15.5], "dark", only=("north",), split=False)
bmd.box([5, 17, -9], [27, 19, 9], "frame")
# chimney
bmd.octagon_y(24, 10, 5, 17, 31 + 2.5 * EPS, "dark")   # soot core stands proud of the steel collar
bmd.octagon_y(24, 10, 7, 29, 31, "steel")
save_block("burner_mining_drill", bmd)

bmh = Model({"disc": "steel", "teeth": "rust", "hub": "gear_hub", "bit": "drill_bit"}, "steel")
bmh.octagon_y(8, 8, 6, 19, 22, "bit")
bmh.gear_y(8, 8, 8, 22, 24, "disc", "teeth", tooth=3, bars=8, width=3)
bmh.octagon_y(8, 8, 6, 24, 27, "hub")
save_block("burner_mining_drill_head", bmh)
# item: body + head moved from the center model frame (8, 8) to the footprint center (16, 0)
item_from("burner_mining_drill", bmd, bmh, offsets=[(0, 0, 0), (8, 0, -8)])

# =====================================================================================
# Stone furnace (2x2x2 on the controller's blockstate; x 0..32, z -16..16; mouth on the south side)
# =====================================================================================


def furnace(lit):
    m = Model({"stone": "furnace_stone", "dark": "dark", "mouth": "fire" if lit else "furnace_mouth",
               "steel": "steel"}, "furnace_stone")
    m.box([0, 0, -16], [32, 4, 16], "stone")
    m.box([1, 4, -15], [31, 15, 15], "stone")
    m.box([3, 15, -13], [29, 22, 13], "stone")
    m.box([6, 22, -10], [26, 27, 10], "stone")
    m.box([10, 27, -6], [22, 29, 6], "stone")
    m.box([12, 29, -4], [20, 29.5, 4], "dark", only=("up",), split=False)
    # arched mouth on the south face
    m.box([8, 4, 15], [11, 15, 17], "stone", split=False)
    m.box([21, 4, 15], [24, 15, 17], "stone", split=False)
    m.box([8 - EPS, 13, 15], [24 + EPS, 16, 17.5], "stone", split=False)   # proud of the jambs
    m.box([11, 4, 15], [21, 13, 15.5], "mouth", only=("south",), fit=("south",), split=False,
          light=15 if lit else None, shade=not lit)
    m.box([11, 4, 15.5], [21, 4.5, 17], "steel", only=("up",), split=False)
    return m


fur = furnace(False)
save_block("stone_furnace", fur)
save_block("stone_furnace_lit", furnace(True))
item_from("stone_furnace", fur)

# =====================================================================================
# Lab (3x3, body + dome that glows while researching)
# =====================================================================================
lb = Model({"frame": "dark_panel", "panel": "lab_panel", "deck": "steel_panel", "brass": "brass", "steel": "steel",
            "red": "tier_red", "green": "tier_green", "dark": "dark"}, "lab_panel")
lb.textures["green"] = "tier_green"
lb.box([-15, 0, -15], [31, 3, 31], "frame")
lb.cbox(8, 8, 36, 36, EPS, 3 - EPS, "frame", split=False, rot=("y", 45, [8, 0, 8]))
lb.box([-11, 3, -11], [27, 13, 27], {"north": "panel", "south": "panel", "east": "panel", "west": "panel",
                                     "up": "deck", "down": "frame"})
lb.box([-9, 13, -9], [25, 15, 25], "steel")
lb.octagon_y(8, 8, 22, 15, 17, "brass")
# science pack holders on the corners
for i, (x, z) in enumerate(((-13, -13), (29, -13), (-13, 29), (29, 29))):
    lb.cbox(x, z, 6, 6, 3, 12, "frame", split=False)
    lb.cbox(x, z, 4, 4, 12, 16, "red" if i % 2 == 0 else "green", split=False)
    lb.cbox(x, z, 2, 2, 16, 17, "steel", split=False)
save_block("lab_body", lb)


def lab_dome(tex):
    m = Model({"glass": tex, "brass": "brass"}, tex)
    m.octagon_y(8, 8, 20, 17, 23, "glass")
    m.octagon_y(8, 8, 14, 23, 27, "glass")
    m.octagon_y(8, 8, 7, 27, 29, "glass")
    m.octagon_y(8, 8, 21, 20, 21, "brass")
    m.cbox(8, 8, 2, 2, 29, 31, "brass", split=False)
    return m


save_block("lab_dome", lab_dome("lab_glow"))
idle_dome = lab_dome("lab_glass")
save_block("lab_dome_idle", idle_dome)
empty("lab", "lab_panel")
item_from("lab", lb, idle_dome)

# =====================================================================================
# Boiler (3x2: x -16..32, z -8..24; steam out north at x 0..16, water ports west/east of the
# near row z 8..24, fire door south)
# =====================================================================================
bb = Model({"frame": "dark_panel", "shell": "boiler_shell", "cap": "dark_panel", "box": "rust_panel",
            "dark": "dark", "mouth": "furnace_mouth", "steel": "steel", "brass": "brass",
            "pipe_h": "pipe_h", "pipe_v": "pipe_v", "pipe_end": "pipe_end", "flange": "flange"}, "boiler_shell")
bb.box([-15, 0, -7], [31, 3, 23], "frame")
# horizontal tank along x
bb.octagon_axis("x", 13, 6, 18, -14, 30, "shell")
for x0, x1 in ((-15, -13), (29, 31)):
    bb.octagon_axis("x", 13, 6, 14, x0, x1, "cap", split=False)
for x in (-6, 8, 22):
    bb.box([x - 1, 3, -2], [x + 1, 5, 14], "steel", split=False)
# firebox with door (south)
bb.box([-6, 3, 13], [22, 14, 23], "box")
bb.box([1, 4, 23], [15, 11, 23.5], "mouth", only=("south",), fit=("south",), split=False)
bb.box([0, 3, 23], [16, 12, 23.5], "dark", only=("east", "west", "up"), split=False)
# chimney on the firebox
bb.octagon_y(18, 19, 5, 14, 31 + 2.5 * EPS, "dark")
bb.octagon_y(18, 19, 7, 29, 31, "steel")
# safety valve on the tank
bb.cbox(-4, 6, 3, 3, 22, 26, "brass", split=False)
bb.cbox(-4, 6, 5, 2, 26, 27, "brass", split=False)
# water ports (west/east of the near row, centered at z 16, y 8) and steam port (north, x 8)
bb.pipe_x(8, 16, -16, -6, skip=("west",))
bb.flange_x(8, 16, -16, -15)
bb.pipe_x(8, 16, 22, 32, skip=("east",))
bb.flange_x(8, 16, 31, 32)
bb.pipe_z(8, 12, -8, -1, skip=("north",))
bb.flange_z(8, 12, -8, -7)
save_block("boiler_body", bb)

bf = Model({"fire": "fire"}, "fire")
bf.box([1.5, 4.5, 23.5], [14.5, 10.5, 23.6], "fire", only=("south",), fit=("south",), split=False, shade=False)
save_block("boiler_fire", bf)
empty("boiler", "boiler_shell")
item_from("boiler", bb)

# =====================================================================================
# Steam engine (3x5). body: one half, drawn twice (z -16..24 at dz -1 and +1.5 blocks); center:
# cylinder and slide (once, at the center); piston: slides along z; flywheel: turns about the x axis
# at y 16, z 8 (the renderer's pivot).
# =====================================================================================
eb = Model({"frame": "dark_panel", "rail": "steel", "dark": "dark", "pipe_h": "pipe_h", "pipe_v": "pipe_v",
            "pipe_end": "pipe_end", "flange": "flange", "plate": "steel_panel"}, "dark_panel")
eb.box([-14, 0, -16], [30, 3, 24], "frame")
eb.box([-12, 3, -16], [-8, 6, 24], "rail")
eb.box([24, 3, -16], [28, 6, 24], "rail")
eb.box([-8, 3, -14], [24, 4, 22], "plate")
# steam pipe along the bed, flanged at both ends of the half
eb.pipe_z(8, 8, -16, 24, skip=("north", "south"))
eb.flange_z(8, 8, -16, -15)
eb.flange_z(8, 8, 23, 24)
save_block("steam_engine_body", eb)

ec = Model({"cyl": "cylinder", "brass": "brass", "dark": "dark", "steel": "steel", "plate": "dark_panel"},
           "cylinder")
# cylinder (front), valve chest, slide bars, crank bearing
# the front ring stands EPS back from z -16 (the item model has the body's flange there) and the
# cylinder sits behind the ring's face
ec.octagon_axis("z", 14, 4, 14, -16 + 4 * EPS, 2, "cyl")
ec.octagon_axis("z", 14, 4, 16, -16 + EPS, -14, "brass", split=False)
ec.octagon_axis("z", 14, 4, 16, 1, 3, "brass", split=False)
ec.box([-1, 21, -12], [9, 24, 0], "plate", split=False)
ec.box([3, 24, -8], [5, 27, -6], "brass", split=False)
for y0 in (10, 17):
    ec.box([1, y0, 3], [7, y0 + 1, 12], "steel", split=False)
ec.box([-2, 4, 1 + 2.5 * EPS], [10, 10, 14], "plate", split=False)   # behind the rear ring's face
ec.box([18, 4, 3], [26, 13, 13], "plate", split=False)
ec.box([19, 13, 5], [25, 19, 11], "dark", split=False)
save_block("steam_engine_center", ec)

ep = Model({"rod": "steel", "head": "brass"}, "steel")
ep.box([3, 13, 2], [5, 15, 9], "rod", split=False)
ep.box([1.5, 11, 6], [6.5, 17, 10], "head", split=False)
save_block("steam_engine_piston", ep)

ef = Model({"rim": "dark", "spoke": "steel", "hub": "gear_hub", "crank": "brass"}, "dark")
# flywheel on the east side, axle along x through (y 16, z 8)
R = 13
# rim bars cross each other: each is EPS thinner (in x) than the previous so their sides never share a plane
bars = [("y", a) for a in (0, 22.5, -22.5, 45, -45)] + [("z", a) for a in (0, 22.5, -22.5)]
for i, (long_axis, ang) in enumerate(bars):
    kw = {"rot": ("x", ang, [27, 16, 8])} if ang else {}
    x0, x1 = 27 + EPS * i, 31 - EPS * i
    if long_axis == "y":
        ef.box([x0, 16 - R, 8 - 2.5], [x1, 16 + R, 8 + 2.5], "rim", split=False, **kw)
    else:
        ef.box([x0, 16 - 2.5, 8 - R], [x1, 16 + 2.5, 8 + R], "rim", split=False, **kw)
# spoke plates stop short of the hub's outer face
ef.box([26, 16 - 8, 8 - 8], [32 - EPS, 16 + 8, 8 + 8], "spoke", split=False)
ef.box([26 + EPS, 16 - 6, 8 - 6], [32 - 2 * EPS, 16 + 6, 8 + 6], "spoke", split=False, rot=("x", 45, [27, 16, 8]))
ef.box([25.5, 13, 5], [32, 19, 11], "hub", split=False)
ef.box([9, 14.5, 6.5], [26, 17.5, 9.5], "spoke", split=False)
ef.box([10, 14, 6], [13, 22, 10], "crank", split=False)
save_block("steam_engine_flywheel", ef)
empty("steam_engine", "dark_panel")
item_from("steam_engine", eb, ec, ep, ef)

# =====================================================================================
# Offshore pump (1x2 on the controller's blockstate: x 0..16, z -16..16; water beyond z -16;
# outlet on the south face of the controller at y 8)
# =====================================================================================
op = Model({"frame": "dark_panel", "body": "steel_panel", "accent": "tier_yellow_panel", "dark": "dark",
            "pipe_h": "pipe_h", "pipe_v": "pipe_v", "pipe_end": "pipe_end", "flange": "flange", "grate": "vent"},
           "steel_panel")
op.box([1, 0, -16 + EPS], [15, 2, 14], "frame")   # behind the intake grate's face
op.box([3, 2, 1], [13, 13, 13], "body")
op.box([2.5, 9, 0.5], [13.5, 11, 13.5], "accent", split=False)
op.box([4, 13, 3], [12, 15, 11], "dark", split=False)
op.octagon_y(8, -8, 12, 2, 12, "body")
op.octagon_y(8, -8, 13, 12, 14, "accent")
op.pipe_z(8, 7, -8, 1)
op.box([4, 1, -16], [12, 9, -14], "grate", fit=("north",), split=False)
op.pipe_z(8, 8, 13, 16, size=8, skip=("south",))
op.flange_z(8, 8, 15, 16)
save_block("offshore_pump", op)
item_from("offshore_pump", op)

# =====================================================================================
# Pipe (multipart: collar + arms with a flange at the block edge)
# =====================================================================================
pc = Model({"collar": "flange"}, "pipe_h")
pc.box([4, 4, 4], [12, 12, 12], "collar", split=False)
save_block("pipe_core", pc)
ps = Model({"pipe_h": "pipe_h", "pipe_v": "pipe_v", "pipe_end": "pipe_end", "flange": "flange"}, "pipe_h")
ps.box([4.5, 4.5, 1], [11.5, 11.5, 4], {"east": "pipe_h", "west": "pipe_h", "up": "pipe_v", "down": "pipe_v"},
       only=("east", "west", "up", "down"), split=False)
ps.box([4, 4, 0], [12, 12, 1], "flange", skip=("south",), cull=("north",), split=False)
save_block("pipe_side", ps)
pu = Model({"pipe_v": "pipe_v", "flange": "flange"}, "pipe_v")
pu.box([4.5, 12, 4.5], [11.5, 15, 11.5], "pipe_v", only=("north", "south", "east", "west"), split=False)
pu.box([4, 15, 4], [12, 16, 12], "flange", skip=("down",), cull=("up",), split=False)
save_block("pipe_up", pu)
pd = Model({"pipe_v": "pipe_v", "flange": "flange"}, "pipe_v")
pd.box([4.5, 1, 4.5], [11.5, 4, 11.5], "pipe_v", only=("north", "south", "east", "west"), split=False)
pd.box([4, 0, 4], [12, 1, 12], "flange", skip=("up",), cull=("down",), split=False)
save_block("pipe_down", pd)
pipe_item = Model({"pipe_h": "pipe_h", "pipe_v": "pipe_v", "pipe_end": "pipe_end", "flange": "flange"}, "pipe_h")
pipe_item.pipe_z(8, 8, 0, 16, skip=("north", "south"))
pipe_item.flange_z(8, 8, 0, 1, size=8)
pipe_item.flange_z(8, 8, 15, 16, size=8)
pipe_item.box([4, 4, 6], [12, 12, 10], "flange", split=False)
pipe_item.parent = "minecraft:block/block"
save_item("pipe", pipe_item.json())

# =====================================================================================
# Small electric pole (wires attach at the center, y 22)
# =====================================================================================
sp = Model({"wood": "pole_wood", "top": "pole_wood_top", "ins": "insulator", "foot": "dark_panel",
            "copper": "copper"}, "pole_wood")
sp.parent = "minecraft:block/block"
sp.box([5, 0, 5], [11, 2, 11], "foot", split=False)
sp.box([6.5, 2, 6.5], [9.5, 21, 9.5], {"north": "wood", "south": "wood", "east": "wood", "west": "wood",
                                      "up": "top", "down": "top"}, split=False)
sp.box([1.5, 17, 7], [14.5, 19, 9], {f: "wood" for f in FACES}, uvrot={"north": 90, "south": 90, "up": 90, "down": 90},
       split=False)
for x in (2.5, 13.5):
    sp.cbox(x, 8, 2, 2, 19, 22, "ins", split=False)
    sp.cbox(x, 8, 1, 1, 22, 23, "copper", split=False)
sp.cbox(8, 8, 3, 3, 21, 24, "ins", split=False)
sp.cbox(8, 8, 1.5, 1.5, 24, 25, "copper", split=False)
save_block("small_electric_pole", sp)
item_parent("small_electric_pole", "small_electric_pole")

# =====================================================================================
# Inserters (static base; the arm is drawn by InserterRenderer)
# =====================================================================================
ti = Model({"plate": "dark_panel", "steel": "steel", "dark": "dark"}, "#housing")
ti.parent = "minecraft:block/block"
ti.box([1, 0, 1], [15, 2, 15], "plate", split=False, cull=("down",))
ti.box([3, 2, 3], [13, 4, 13], "housing", split=False)
ti.octagon_y(8, 8, 6, 4, 5, "steel")
ti.box([6, 2, 0.5], [10, 3, 3], "dark", split=False)  # marker on the pickup side
ti.box([12.5, 2, 6], [14, 3.5, 10], "dark", split=False)
ti.textures.pop("particle")
ti.textures["particle"] = "#housing"
save_block("template_inserter", ti)
for name, housing in (("burner_inserter", "rust_panel"), ("inserter", "tier_yellow_panel"),
                      ("long_handed_inserter", "tier_red_panel"), ("fast_inserter", "tier_blue_panel")):
    save_item_model = Model({"housing": housing}, None, parent="minetorio:block/template_inserter")
    save_block(name, save_item_model)
    # item: base + a raised arm pose
    im = Model({"plate": "dark_panel", "steel": "steel", "dark": "dark", "housing": housing, "arm": "steel"}, housing)
    im.elements = json.loads(json.dumps(ti.elements))
    im.box([7, 5, 7], [9, 14, 9], "arm", split=False)
    im.box([7 + EPS, 12, 3], [9 - EPS, 14, 9], "arm", split=False, rot=("x", -22.5, [8, 13, 8]))
    im.box([6, 10, 2], [10, 12, 5], housing.replace("_panel", "") if "tier" in housing else "rust", split=False)
    im.textures.setdefault("rust", "rust")
    for t in ("tier_yellow", "tier_red", "tier_blue"):
        im.textures.setdefault(t, t)
    im.parent = "minecraft:block/block"
    save_item(name, im.json())

# =====================================================================================
# Belts
# =====================================================================================
SIDE_TEX = {"side": "minetorio:block/dark"}


def belt_template(kind):
    m = Model({}, None, parent="minecraft:block/block")
    m.textures = {"particle": "#belt"}
    if kind == "straight":
        m.box([1, 0, 0], [15, 2, 16], {"up": "belt", "down": "side", "north": "side", "south": "side"},
              only=("up", "down", "north", "south"), uv={"up": [1, 0, 15, 16]}, cull=("down",), split=False)
        for x0 in (0, 15):
            m.box([x0, 0, 0], [x0 + 1, 3, 16], "rail", split=False, cull=("down",))
    else:
        # the plate fills only what the rails leave free (an L around the corner post), so none of its
        # faces is flush with a rail face; its texture keeps block coordinates
        right = kind == "curve_right"
        ox = 0 if right else 15      # outer rail along z
        ix = 15 if right else 0      # corner post at the inner end of the entry edge
        edge = "east" if right else "west"   # the plate strip's side on the block edge

        def u(x):
            return 16 - x if right else x

        plate = {"up": "curve", "down": "side", "north": "side", "south": "side", "east": "side", "west": "side"}
        m.box([1, 0, 0], [15, 2, 15], plate, only=("up", "down", "north"), uv={"up": [u(1), 0, u(15), 15]},
              cull=("down",), split=False)
        m.box([ix, 0, 1], [ix + 1, 2, 15], plate, only=("up", "down", edge),
              uv={"up": [u(ix), 1, u(ix + 1), 15]}, cull=("down",), split=False)
        m.box([ox, 0, 0], [ox + 1, 3, 15], "rail", skip=("south",), split=False, cull=("down",))
        m.box([0, 0, 15], [16, 3, 16], "rail", split=False, cull=("down",))
        m.box([ix, 0, 0], [ix + 1, 3, 1], "rail", split=False)
    for k, v in SIDE_TEX.items():
        m.textures.setdefault(k, v)
    return m


for kind in ("straight", "curve_left", "curve_right"):
    save_block("template_belt_" + kind, belt_template(kind))

for pre, rail in TIER_COLOR.items():
    belt = NS + pre + "transport_belt"
    curve = NS + pre + "transport_belt_curve"
    save_block(pre + "transport_belt", Model({"belt": belt, "rail": NS + rail}, None,
                                             parent="minetorio:block/template_belt_straight"))
    for side in ("left", "right"):
        save_block(f"{pre}transport_belt_curve_{side}",
                   Model({"belt": belt, "curve": curve, "rail": NS + rail}, None,
                         parent=f"minetorio:block/template_belt_curve_{side}"))
    item_parent(pre + "transport_belt", pre + "transport_belt")

# underground belt: input = belt in the south half running into a hood on the north half
#                   output = hood on the south half, belt leaving north


def underground(output):
    m = Model({}, None, parent="minecraft:block/block")
    m.textures = {"particle": "#hood", "side": "minetorio:block/dark", "tunnel": "minecraft:block/black_concrete",
                  "frame": "minetorio:block/dark_panel"}
    b0, b1 = (0, 8) if output else (8, 16)       # belt half
    h0, h1 = (8, 16) if output else (0, 8)       # hood half
    mouth = h0 if output else h1                 # z of the opening (faces the belt half)
    far = h1 if output else h0
    m.box([1, 0, b0], [15, 2, b1], {"up": "belt", "down": "side"}, only=("up", "down"),
          uv={"up": [1, b0, 15, b1]}, cull=("down",), split=False)
    for x0 in (0, 15):
        m.box([x0, 0, b0], [x0 + 1, 3, b1], "rail", split=False, cull=("down",))
    # hood walls (tier color), interior black; walls and roof stand EPS in from the block sides so the
    # lip over the mouth is proud of them
    for x0, x1, inner in ((EPS, 2, "east"), (14, 16 - EPS, "west")):
        m.box([x0, 0, h0], [x1, 9, h1], {f: ("tunnel" if f == inner else "hood") for f in FACES}, split=False)
    zf0, zf1 = (far - 2, far) if far == 16 else (far, far + 2)
    m.box([2, 0, zf0], [14, 9, zf1], {f: ("tunnel" if f in ("north", "south") and f == ("south" if far == 0 else "north")
                                          else "hood") for f in FACES}, split=False)
    m.box([EPS, 9, h0], [16 - EPS, 11, h1], {"up": "arrow", "down": "tunnel", "north": "hood", "south": "hood",
                                              "east": "hood", "west": "hood"},
          uv={"up": [0, h0, 16, h1]}, split=False)
    m.box([2, 0, h0], [14, 0.5, h1], "tunnel", only=("up",), split=False)
    # lip over the mouth and a frame band
    lz0, lz1 = (mouth - 1, mouth + EPS) if not output else (mouth - EPS, mouth + 1)
    m.box([0, 7, lz0], [16, 11 + EPS, lz1], "frame", split=False)
    m.box([3, 11, h0 + 2], [13, 12, h1 - 2], "frame", split=False)
    return m


save_block("template_underground_input", underground(False))
save_block("template_underground_output", underground(True))
for pre, tier in TIER_COLOR.items():
    for kind in ("input", "output"):
        save_block(f"{pre}underground_belt_{kind}",
                   Model({"belt": NS + pre + "transport_belt", "rail": NS + tier, "hood": NS + tier + "_panel",
                          "arrow": NS + pre + "belt_arrow"}, None,
                         parent=f"minetorio:block/template_underground_{kind}"))
    item_parent(pre + "underground_belt", pre + "underground_belt_input")

# splitter: 2 wide (x 0..32), 1 deep


def splitter_template():
    m = Model({}, None, parent="minecraft:block/block")
    m.textures = {"particle": "#hood", "side": "minetorio:block/dark", "frame": "minetorio:block/dark_panel"}
    for x0 in (0, 16):
        m.box([x0 + 1, 0, 0], [x0 + 15, 2, 16], {"up": "belt", "down": "side"}, only=("up", "down"),
              uv={"up": [1, 0, 15, 16]}, cull=None, split=False)
        for rx in (x0, x0 + 15):
            if rx in (0, 31):
                # outer rails run past the end frames: cut there, the frame covers the rail's outer side
                outer = "west" if rx == 0 else "east"
                for z0, z1 in ((0, 4), (4, 12), (12, 16)):
                    skip = [f for f, cut in (("north", z0 > 0), ("south", z1 < 16), (outer, z0 == 4)) if cut]
                    m.box([rx, 0, z0], [rx + 1, 3, z1], "rail", skip=skip, split=False,
                          uv={"up": [7.5, z0, 8.5, z1], "down": [7.5, 16 - z1, 8.5, 16 - z0],
                              "west": [z0, 6.5, z1, 9.5], "east": [16 - z1, 6.5, 16 - z0, 9.5]})
            else:
                m.box([rx, 0, 0], [rx + 1, 3, 16], "rail", split=False)
    # motor housing: its ends are inside the end frames
    m.box([0, 2, 5], [32, 10, 11], {"north": "motor", "south": "motor", "east": "hood", "west": "hood",
                                     "up": "arrow", "down": "side"}, skip=("east", "west"), fit=("north", "south", "up"))
    # end frames: their bottoms are covered by the rails and the belt plates
    m.box([0, 0, 4], [2, 12, 12], "frame", skip=("down",), split=False)
    m.box([30, 0, 4], [32, 12, 12], "frame", skip=("down",), split=False)
    m.box([14, 10, 6], [18, 12, 10], "frame", split=False)
    return m


save_block("template_splitter", splitter_template())
for pre, tier in TIER_COLOR.items():
    s = Model({"belt": NS + pre + "transport_belt", "rail": NS + tier, "hood": NS + tier + "_panel",
               "motor": NS + pre + "belt_motor", "arrow": NS + pre + "belt_arrow"}, None,
              parent="minetorio:block/template_splitter")
    save_block(pre + "splitter", s)
    tmpl = splitter_template()
    j = {"parent": "minetorio:block/" + pre + "splitter", "display": display_for(tmpl.elements, hand_boost=1.2)}
    save_item(pre + "splitter", j)

# flat icons for materials and the scanner
for n in ("iron_ore", "copper_ore", "iron_plate", "copper_plate", "stone_brick", "iron_gear_wheel", "copper_cable",
          "electronic_circuit", "automation_science_pack", "logistic_science_pack", "scanner"):
    item_flat(n)

print(len(written), "models")
