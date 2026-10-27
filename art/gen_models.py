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
        # each bar is EPS thinner than the previous: crossing bars never share a cap plane
        for i, ang in enumerate(x_bars):
            kw = {"rot": ("y", ang, [cx, y0, cz])} if ang else {}
            e = 0.5 + EPS * i
            self.box([cx - L, y0 + e, cz - width / 2], [cx + L, y1 - e, cz + width / 2], tex_teeth,
                     split=False, **kw)
        for i, ang in enumerate(z_bars, len(x_bars)):
            kw = {"rot": ("y", ang, [cx, y0, cz])} if ang else {}
            e = 0.5 + EPS * i
            self.box([cx - width / 2, y0 + e, cz - L], [cx + width / 2, y1 - e, cz + L], tex_teeth,
                     split=False, **kw)
        return self

    # 16-gon parts. Step k (0..15) is a box built at a quarter turn q and turned by a residual angle of
    # -22.5, 0, 22.5 or 45 degrees (the angles a model element allows), so the 16 boxes cover every
    # 22.5 degree step whatever the sign convention. Neighbouring steps overlap at their corners, so odd
    # steps are EPS thinner along the axis and their caps never share a plane with the even ones.
    @staticmethod
    def _step(k):
        q, r = divmod(k + 1, 4)
        return q % 4, (r - 1) * 22.5

    @staticmethod
    def _quarter(q, a, b):
        """(a, b) offsets from the axis turned by q quarter turns."""
        for _ in range(q):
            a, b = -b, a
        return a, b

    def _poly(self, axis, c1, c2, r0, r1, w, s0, s1, tex, k, rot_origin):
        """Box between radii r0..r1, w wide, s0..s1 along the axis, in 16-gon step k. Plane coords (c1, c2):
        axis y -> (x, z), axis x -> (y, z)."""
        q, res = self._step(k)
        # corners of the step-0 box (offset toward -c2) turned by q
        pts = [self._quarter(q, a, b) for a in (-w / 2, w / 2) for b in (-r1, -r0)]
        a0, a1 = min(p[0] for p in pts), max(p[0] for p in pts)
        b0, b1 = min(p[1] for p in pts), max(p[1] for p in pts)
        kw = {"rot": (axis, res, rot_origin)} if res else {}
        if axis == "y":
            self.box([c1 + a0, s0, c2 + b0], [c1 + a1, s1, c2 + b1], tex, split=False, **kw)
        else:
            self.box([s0, c1 + a0, c2 + b0], [s1, c1 + a1, c2 + b1], tex, split=False, **kw)

    def ring_y(self, cx, cz, R, t, y0, y1, tex, steps=range(16)):
        """Flat 16-gon ring about a vertical axis: outer radius R, wall t, y0..y1."""
        L = 2 * R * math.tan(math.pi / 16)
        for k in steps:
            e = EPS * (k % 2)
            self._poly("y", cx, cz, R - t, R, L, y0 + e, y1 - e, tex, k, [cx, y0, cz])
        return self

    def ring_x(self, cy, cz, R, t, x0, x1, tex):
        """Upright 16-gon ring about an axis along x through (cy, cz): outer radius R, wall t, x0..x1."""
        L = 2 * R * math.tan(math.pi / 16)
        for k in range(16):
            e = EPS * (k % 2)
            self._poly("x", cy, cz, R - t, R, L, x0 + e, x1 - e, tex, k, [x0, cy, cz])
        return self

    def teeth_y(self, cx, cz, r0, r1, w, y0, y1, tex):
        """16 gear teeth (radial boxes) about a vertical axis."""
        for k in range(16):
            self._poly("y", cx, cz, r0, r1, w, y0, y1, tex, k, [cx, y0, cz])
        return self

    def spokes_y(self, cx, cz, r, w, y0, y1, tex):
        """8 spokes about a vertical axis: four bars through the axis, each EPS thinner than the last."""
        for i, (long_x, ang) in enumerate(((True, 0), (False, 0), (True, 45), (True, -45))):
            kw = {"rot": ("y", ang, [cx, y0, cz])} if ang else {}
            e = EPS * i
            if long_x:
                self.box([cx - r, y0 + e, cz - w / 2], [cx + r, y1 - e, cz + w / 2], tex, split=False, **kw)
            else:
                self.box([cx - w / 2, y0 + e, cz - r], [cx + w / 2, y1 - e, cz + r], tex, split=False, **kw)
        return self

    def spokes_x(self, cy, cz, r, w, x0, x1, tex):
        """8 spokes in the y-z plane about an axis along x."""
        for i, (long_y, ang) in enumerate(((True, 0), (False, 0), (True, 45), (True, -45))):
            kw = {"rot": ("x", ang, [x0, cy, cz])} if ang else {}
            e = EPS * i
            if long_y:
                self.box([x0 + e, cy - r, cz - w / 2], [x1 - e, cy + r, cz + w / 2], tex, split=False, **kw)
            else:
                self.box([x0 + e, cy - w / 2, cz - r], [x1 - e, cy + w / 2, cz + r], tex, split=False, **kw)
        return self

    # Solid round prisms: a regular 16-gon of apothem a is exactly the union of 8 strips (2a long, one
    # 16-gon side wide) through the axis at 22.5 degree steps: long along one plane axis turned by 0, 22.5,
    # -22.5, 45 and long along the other turned by the same. Each strip is EPS shorter along the prism's
    # axis than the previous, so their caps never share a plane.
    PRISM16 = ((0, 0), (0, 22.5), (0, -22.5), (0, 45), (1, 0), (1, 22.5), (1, -22.5), (1, 45))

    def prism16_y(self, cx, cz, a, y0, y1, tex, **kw):
        """Upright 16-gon prism with apothem a (= half its width across flats)."""
        s = a * math.tan(math.pi / 16)
        for i, (along_z, ang) in enumerate(self.PRISM16):
            e = EPS * i
            r = {"rot": ("y", ang, [cx, y0, cz])} if ang else {}
            w, d = (s, a) if along_z else (a, s)
            self.box([cx - w, y0 + e, cz - d], [cx + w, y1 - e, cz + d], tex, split=False, **r, **kw)
        return self

    def prism16_z(self, cx, cy, a, z0, z1, tex, **kw):
        """Horizontal 16-gon prism along z through (cx, cy), apothem a. Long prisms are cut into equal
        lengths of at most 16 px (texel density); the cut faces inside are dropped."""
        s = a * math.tan(math.pi / 16)
        n = max(1, math.ceil((z1 - z0) / 16 - 1e-6))
        cuts = [z0 + (z1 - z0) * k / n for k in range(n + 1)]
        for i, (along_y, ang) in enumerate(self.PRISM16):
            e = EPS * i
            r = {"rot": ("z", ang, [cx, cy, z0])} if ang else {}
            w, h = (s, a) if along_y else (a, s)
            for k in range(n):
                za = cuts[k] + (e if k == 0 else 0)
                zb = cuts[k + 1] - (e if k == n - 1 else 0)
                skip = tuple(f for f, inner in (("north", k > 0), ("south", k < n - 1)) if inner)
                self.box([cx - w, cy - h, za], [cx + w, cy + h, zb], tex, split=False, skip=skip, **r, **kw)
        return self

    def chamfer(self, x0, z0, x1, z1, y0, y1, tex, c=4, **kw):
        """Box with its four vertical edges bevelled by c: a cross of two boxes plus turned corner pieces
        (EPS shorter each, so their caps don't share a plane)."""
        self.box([x0, y0, z0 + c], [x1, y1, z1 - c], tex, **kw)
        self.box([x0 + c, y0 + EPS, z0], [x1 - c, y1 - EPS, z1], tex, **kw)
        s = c * math.sqrt(2)
        for (x, z) in ((x0 + c, z0 + c), (x1 - c, z0 + c), (x0 + c, z1 - c), (x1 - c, z1 - c)):
            self.cbox(x, z, s, s, y0 + 2 * EPS, y1 - 2 * EPS, tex, split=False, rot=("y", 45, [x, y0, z]), **kw)
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


def item_from(name, *models, offsets=None, particle=None, hand_boost=1.4, render_type=None):
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
    if render_type:
        j = {"render_type": render_type, **j}
    if particle:
        j["textures"]["particle"] = NS + particle
    save_item(name, j)


def item_scaled(name, parts, k, particle=None):
    """Item model of a machine too long for -16..32: every part placed as its renderer draws it
    (parts: (model, dx, dy, dz)), then the whole scaled by k about the middle of its bounding box onto
    the block's center; display_for fits the result into a slot. Element rotations keep their angles; their origins scale with the rest."""
    els, tex = [], {}
    for m, dx, dy, dz in parts:
        for el in m.elements:
            e = json.loads(json.dumps(el))
            e["from"] = [e["from"][0] + dx, e["from"][1] + dy, e["from"][2] + dz]
            e["to"] = [e["to"][0] + dx, e["to"][1] + dy, e["to"][2] + dz]
            if "rotation" in e:
                o = e["rotation"]["origin"]
                e["rotation"]["origin"] = [o[0] + dx, o[1] + dy, o[2] + dz]
            els.append(e)
        tex.update(m.textures)
    lo = [min(e["from"][i] for e in els) for i in range(3)]
    hi = [max(e["to"][i] for e in els) for i in range(3)]
    c = [(lo[i] + hi[i]) / 2 for i in range(3)]
    target = [8, 8, 8]

    def sc(p):
        return [r4((p[i] - c[i]) * k + target[i]) for i in range(3)]

    for e in els:
        e["from"], e["to"] = sc(e["from"]), sc(e["to"])
        if "rotation" in e:
            e["rotation"]["origin"] = sc(e["rotation"]["origin"])
        for v in e["from"] + e["to"]:
            assert -16 <= v <= 32, e
    j = {"parent": "minecraft:block/block", "ambientocclusion": False,
         "textures": {kk: (v if ":" in v else NS + v) for kk, v in tex.items()},
         "display": display_for(els), "elements": els}
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
# Assembling machines (3x3, body + spinning gear; footprint center at 8, 8). Factorio look: a chunky
# square housing with bevelled corners and pipe stubs, the roof a round opening in which a big
# spoked gear with a cross arm turns while crafting.
# =====================================================================================
ASM_COLLAR_R = 24      # static ring around the opening (outer radius)
ASM_GEAR_R = 19        # gear rim (outer radius); teeth reach 21.5, inside the collar's 22


def assembler_body(tier):
    house, trim = ("beige", "dark_panel") if tier == 1 else ("slate", "tier_blue_panel")
    m = Model({"frame": "dark_panel", "body": house + "_panel", "vent": house + "_vent", "plain": house,
               "trim": trim, "roof": "dark", "collar": house + "_panel", "window": "window",
               "pipe_h": "pipe_h", "pipe_v": "pipe_v", "pipe_end": "pipe_end", "flange": "flange"},
              house + "_panel")
    m.chamfer(-16, -16, 32, 32, 0, 2, "frame", c=6)
    m.chamfer(-14, -14, 30, 30, 2, 18, {"north": "body", "south": "vent", "east": "body", "west": "body",
                                        "up": "roof", "down": "frame"}, c=5)
    # trim band under the roof edge (tier colour on tier 2), its top is the roof deck
    m.chamfer(-14.5, -14.5, 30.5, 30.5, 18, 21, {"north": "trim", "south": "trim", "east": "trim", "west": "trim",
                                                 "up": "roof", "down": "trim"}, c=5)
    # the opening's collar
    m.ring_y(8, 8, ASM_COLLAR_R, 2, 21, 23.5, "collar")
    # front window, pipe stubs on the sides
    m.box([1, 6, -14.5], [15, 16, -14], "window", fit=("north",), skip=("south",), split=False)
    m.pipe_x(9, 8, -16, -14, size=6, skip=("west",))
    m.flange_x(9, 8, -16, -15.5, size=9)
    m.pipe_x(9, 8, 30, 32, size=6, skip=("east",))
    m.flange_x(9, 8, 31.5, 32, size=9)
    return m


def assembler_gear(tier):
    hub = "brass" if tier == 1 else "tier_blue"
    m = Model({"rim": "steel_panel", "teeth": "steel", "spoke": "steel", "hub": "gear_hub", "boss": hub,
               "arm": "steel_panel", "weight": hub}, "steel")
    m.ring_y(8, 8, ASM_GEAR_R, 2.5, 21.5, 24, "rim")
    m.teeth_y(8, 8, ASM_GEAR_R - 0.5, ASM_GEAR_R + 2.5, 2.5, 21.75, 23.75, "teeth")
    m.spokes_y(8, 8, ASM_GEAR_R - 1, 3.5, 21.75, 23.5, "spoke")
    m.octagon_y(8, 8, 9, 21.5, 26, {"north": "boss", "south": "boss", "east": "boss", "west": "boss",
                                    "up": "hub", "down": "boss"})
    # the cross arm over the gear with weights at its ends
    m.box([-8, 26, 6.5], [24, 27.5, 9.5], "arm", split=False)
    for x in (-9, 25):
        m.cbox(x, 8, 4, 5, 24.5, 28, "weight", split=False)
    return m


for tier in (1, 2):
    body = assembler_body(tier)
    gear = assembler_gear(tier)
    save_block(f"assembling_machine_{tier}_body", body)
    save_block(f"assembling_machine_{tier}_gear", gear)
    empty(f"assembling_machine_{tier}", "beige_panel" if tier == 1 else "slate_panel")
    item_from(f"assembling_machine_{tier}", body, gear)

# =====================================================================================
# Electric mining drill (3x3, ~3 blocks tall). body: housing with side motors; head (spins): a big
# cog on the deck and the drill shaft; tower: an A-frame derrick over the shaft, drawn one block up
# (DrillRenderer), so its y = world y - 16.
# =====================================================================================
edb = Model({"frame": "dark_panel", "body": "steel_panel", "deck": "dark_panel", "hazard": "hazard", "vent": "vent",
             "accent": "tier_yellow_panel", "steel": "steel", "chute": "dark_panel", "dark": "dark"}, "steel_panel")
edb.chamfer(-16, -16, 32, 32, 0, 3, "frame", c=6)
edb.chamfer(-13, -13, 29, 29, 3, 14, {"north": "body", "south": "body", "east": "body", "west": "body",
                                      "up": "deck", "down": "frame"}, c=4)
edb.chamfer(-13.5, -13.5, 29.5, 29.5, 11, 13.5, "accent", c=4)
edb.box([-9, 3, -13.5], [25, 5, -13], "hazard", only=("north", "up", "east", "west"), split=False)
edb.octagon_y(8, 8, 30, 14, 15, "steel")
# output chute on the front edge
edb.box([2, 4, -15.5], [14, 10.5, -13.5], "chute", split=False)
edb.box([3, 4, -16], [13, 9, -15.5], "dark", only=("north",), split=False)
# side motors
for x0, x1, out in ((-16, -13.5, "west"), (29.5, 32, "east")):
    edb.box([x0, 3, -1], [x1, 12, 17], {f: ("vent" if f == out else "frame") for f in FACES}, fit=(out,), split=False)
save_block("electric_mining_drill_body", edb)

edh = Model({"disc": "steel_panel", "teeth": "dark", "bit": "drill_bit", "collar": "tier_yellow", "hub": "gear_hub"},
            "drill_bit")
edh.octagon_y(8, 8, 22, 15.25, 17.25, {"north": "disc", "south": "disc", "east": "disc", "west": "disc",
                                       "up": "disc", "down": "disc"})
edh.teeth_y(8, 8, 10.5, 14, 3, 15.5, 17, "teeth")
edh.octagon_y(8, 8, 7, 17.25, 32, "bit")
for y in (21, 27):
    edh.octagon_y(8, 8, 10, y, y + 1.5, "collar")
save_block("electric_mining_drill_head", edh)

edt = Model({"leg": "tier_yellow_panel", "bar": "dark_panel", "beam": "dark_panel", "cap": "tier_yellow",
             "casing": "dark", "pulley": "steel"}, "tier_yellow_panel")
LEG_LEN, LEG_TILT = 30, 22.5
for z0, z1 in ((-8, -5), (21, 24)):
    for i, (xf, ang) in enumerate(((-5, -LEG_TILT), (21, LEG_TILT))):
        e = EPS * i   # the second leg is EPS slimmer in z where the two cross at the top
        edt.box([xf - 1.5, -2, z0 + e], [xf + 1.5, -2 + LEG_LEN, z1 - e], "leg", split=False,
                rot=("z", ang, [xf, -2, (z0 + z1) / 2]))
    # cross bar half way up
    edt.box([-1, 11, z0 + 2 * EPS], [17, 12.5, z1 - 2 * EPS], "bar", split=False)
# crown: a beam joining the two A-frames, a pulley and a cap
edt.box([4.5, 25, -9], [11.5, 28, 25], "beam")
edt.octagon_axis("x", 29.5, 8, 4, 6, 10, "pulley", split=False)
# static casing of the upper shaft
edt.octagon_y(8, 8, 8, 16, 25, "casing")
save_block("electric_mining_drill_tower", edt)
empty("electric_mining_drill", "steel_panel")
item_from("electric_mining_drill", edb, edh, edt, offsets=[(0, -16, 0), (0, -16, 0), (0, 0, 0)])

# =====================================================================================
# Burner mining drill (2x2 on the controller's blockstate; head and chimney drawn by the BER on the
# center). footprint: x 0..32, z -16..16; front (output) = north. The chimney model is authored in
# the center frame (8, 8) one block up (y = world y - 16).
# =====================================================================================
bmd = Model({"body": "rust_panel", "frame": "dark_panel", "dark": "dark", "mouth": "furnace_mouth",
             "chute": "dark_panel", "steel": "steel", "band": "dark_panel", "deck": "dark_panel"}, "rust_panel")
bmd.box([0, 0, -16], [32, 3, 16], "frame")
bmd.box([2, 3, -14], [30, 24, 14], {"north": "body", "south": "body", "east": "body", "west": "body",
                                    "up": "deck", "down": "frame"})
for x0 in (1, 26):
    for z0 in (-15, 10):
        bmd.box([x0, 3, z0], [x0 + 5, 26, z0 + 5], "frame", split=False)
bmd.box([1.5, 13, -14.5], [30.5, 15, 14.5], "band")
bmd.box([10, 4, 14.5], [22, 12, 15], "mouth", only=("south",), fit=("south",), split=False)
bmd.box([9, 3, 14], [23, 13, 15.5], "dark", skip=("south",), split=False)
bmd.box([11, 4, -15.5], [21, 11, -14], "chute", split=False)
bmd.box([12, 5, -16], [20, 9, -15.5], "dark", only=("north",), split=False)
bmd.box([6, 24, -10], [26, 25, 10], "steel")
save_block("burner_mining_drill", bmd)

bmh = Model({"disc": "steel", "teeth": "rust", "hub": "gear_hub", "bit": "drill_bit"}, "steel")
bmh.octagon_y(8, 8, 6, 25, 28, "bit")
bmh.gear_y(8, 8, 6, 25.5, 27.5, "disc", "teeth", tooth=2.5, bars=8, width=3)
bmh.octagon_y(8, 8, 5, 28, 31, "hub")
save_block("burner_mining_drill_head", bmh)

bmc = Model({"base": "dark_panel", "pipe": "dark", "collar": "steel", "soot": "dark"}, "dark")
CHIM_X, CHIM_Z = 18, 18   # = controller frame (26, 10)
bmc.cbox(CHIM_X, CHIM_Z, 7, 7, 8, 11, "base", split=False)
bmc.octagon_y(CHIM_X, CHIM_Z, 5, 11, 28 + 2.5 * EPS, "pipe")
bmc.octagon_y(CHIM_X, CHIM_Z, 7, 25, 28, "collar")
bmc.octagon_y(CHIM_X, CHIM_Z, 6, 17, 18.5, "collar")
save_block("burner_mining_drill_chimney", bmc)
# item: body + head and chimney moved from the center frame (8, 8) to the footprint center (16, 0),
# everything 13 px down so the chimney stays inside -16..32
item_from("burner_mining_drill", bmd, bmh, bmc, offsets=[(0, -13, 0), (8, -13, -8), (8, 3, -8)])

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
# Lab (3x3): dark base plate, a dark round pedestal and on it a tall slender glass tube (16-sided,
# one block across, ~1.5 blocks tall) with a cap. Inside the tube stands a fluid column with red and
# green bands (lab_dome / lab_dome_idle), which turns slowly and glows blue at full brightness while
# researching. The tube (lab_shell) is translucent and drawn by LabRenderer in a translucent buffer.
# =====================================================================================
LAB_TUBE_A, LAB_Y0, LAB_Y1 = 8, 7, 30   # tube apothem (half width), bottom and top
lb = Model({"frame": "dark_panel", "panel": "lab_panel", "deck": "dark_panel", "brass": "brass", "steel": "steel",
            "red": "tier_red", "green": "tier_green", "dark": "dark", "vent": "vent", "pedestal": "dark_panel",
            "cap": "steel_panel"}, "lab_panel")
lb.chamfer(-16, -16, 32, 32, 0, 2, "frame", c=6)
lb.chamfer(-12, -12, 28, 28, 2, 4.5, {"north": "panel", "south": "panel", "east": "panel", "west": "panel",
                                    "up": "deck", "down": "frame"}, c=5)
# round pedestal and the brass seat of the tube
lb.prism16_y(8, 8, 11, 4.5, 6.25, "pedestal")
lb.prism16_y(8, 8, 9.5, 6.25, LAB_Y0, "brass")
# cap on the tube
lb.prism16_y(8, 8, 9, LAB_Y1, 31.25, "cap")
lb.prism16_y(8, 8, 3, 31.25, 32, "brass")
# small vents around the pedestal
for (x, z, w, d, fc) in ((8, -6.5, 8, 2, "north"), (8, 22.5, 8, 2, "south"), (-6.5, 8, 2, 8, "west"),
                         (22.5, 8, 2, 8, "east")):
    lb.cbox(x, z, w, d, 4.5, 7.5, {f: ("vent" if f == fc else "dark") for f in FACES}, fit=(fc,), split=False)
# science pack holders on the corners
for i, (x, z) in enumerate(((-8, -8), (24, -8), (-8, 24), (24, 24))):
    lb.cbox(x, z, 5, 5, 4.5, 7.5, "frame", split=False)
    lb.cbox(x, z, 3.5, 3.5, 7.5, 11.5, "red" if i % 2 == 0 else "green", split=False)
    lb.cbox(x, z, 2, 2, 11.5, 12.5, "steel", split=False)
save_block("lab_body", lb)

shell = Model({"glass": "lab_shell"}, "lab_shell")
shell.ring_y(8, 8, LAB_TUBE_A, 0.5, LAB_Y0, LAB_Y1, "glass")
save_block("lab_shell", shell)


def lab_dome(core):
    m = Model({"core": core, "red": "tier_red", "green": "tier_green", "brass": "brass"}, core)
    m.prism16_y(8, 8, 4, LAB_Y0, LAB_Y1, "core")
    m.prism16_y(8, 8, 5.5, 11, 15, "red")
    m.prism16_y(8, 8, 5.5, 19.5, 23.5, "green")
    return m


save_block("lab_dome", lab_dome("lab_glow"))
idle_dome = lab_dome("lab_glass")
save_block("lab_dome_idle", idle_dome)
empty("lab", "lab_panel")
item_from("lab", lb, idle_dome, shell, render_type="minecraft:translucent")

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
# Steam engine (3x5, z along the 5-block length; footprint center at x 8, z 8, so the machine spans
# z -32..48 in the center frame). Parts (SteamMachineRenderer.Engine):
#   body:     front half (center frame z -32..8, drawn at dz -1 block): bed, crankcase, steam port, the
#             long round cylinder with dark bands and brass rivets, valve chest;
#   rear:     back half (z 8..48, drawn at dz +1.5): bed, cylinder cover and gland, crosshead guides,
#             crankshaft pedestals, exhaust port;
#   piston:   rod, crosshead and connecting rod, at dz +1.5 plus the stroke;
#   flywheel: big spoked flywheel on the east side plus crankshaft and crank, turning about x through
#             (y 16, z 8) of the model; drawn at dz +1.5 (pivot at center frame z 32).
# =====================================================================================
ENG_Y = 16          # cylinder and crankshaft axis height
eb = Model({"frame": "dark_panel", "case": "dark", "cyl": "slate_panel", "band": "dark_panel", "rivet": "brass",
            "plate": "dark_panel", "brass": "brass", "pipe_h": "pipe_h", "pipe_v": "pipe_v", "pipe_end": "pipe_end",
            "flange": "flange"}, "slate_panel")
eb.box([-6, 0, -16], [22, 3, 24], "frame")
eb.box([-3, 3, -12], [19, 5, 24], "case")
eb.pipe_z(8, 8, -16, -10, size=6, skip=("north",))
eb.flange_z(8, 8, -16, -15.5, size=9)
# the cylinder: 16-sided, 22 across, local z -10..23 (center frame -26..7), under a front cover and bands
eb.prism16_z(8, ENG_Y, 11, -10, 23, "cyl")
for z0, z1, a in ((-11, -8, 12), (6, 8, 11.75), (21, 24, 12)):
    eb.prism16_z(8, ENG_Y, a, z0, z1, "band")
    zm = (z0 + z1) / 2
    for (x, y) in ((8, ENG_Y + a), (8 - a, ENG_Y), (8 + a, ENG_Y)):
        eb.box([x - 0.5, y - 0.5, zm - 0.5], [x + 0.5, y + 0.5, zm + 0.5], "rivet", split=False)
# saddles hugging it, valve chest and valve wheel on top
for z0 in (-6, 14):
    eb.box([-2, 4, z0], [18, 9, z0 + 4], "plate", split=False)
eb.box([3, 26, -4], [13, 29.5, 16], "plate")
eb.cbox(8, 6, 1.5, 1.5, 29.5, 31, "brass", split=False)
eb.cbox(8, 6, 5, 5, 31, 32, "brass", split=False)
save_block("steam_engine_body", eb)

er = Model({"frame": "dark_panel", "case": "dark", "cover": "dark_panel", "steel": "steel", "plate": "dark_panel",
            "post": "dark_panel", "cap": "brass", "pipe_h": "pipe_h", "pipe_v": "pipe_v", "pipe_end": "pipe_end",
            "flange": "flange"}, "dark_panel")
er.box([-6, 0, -16], [22, 3, 24], "frame")
er.box([-3, 3, -16], [19, 5, -12], "case", split=False)
# cylinder back cover and rod gland (the cylinder's last band ends at local z -16)
er.prism16_z(8, ENG_Y, 10.5, -16, -13, "cover")
er.prism16_z(8, ENG_Y, 3, -13, -11, "steel")
# crosshead guides above and below the rod line, on a stand
for y0 in (10.5, 20):
    er.box([4, y0, -11], [12, y0 + 1.5, 3], "steel", split=False)
er.box([4.5, 3, -3], [11.5, 10.5, 1], "plate", split=False)
# crankshaft pedestals (crank pivot at local z 8) either side of the crank
for x0 in (-3, 15):
    er.box([x0, 3, 5], [x0 + 4, 18, 11], "post", split=False)
    er.box([x0 + 0.5, 18, 5.5], [x0 + 3.5, 19, 10.5], "cap", split=False)
# exhaust port at the back end
er.pipe_z(8, 8, 18, 24, size=6, skip=("south",))
er.flange_z(8, 8, 23.5, 24, size=9)
save_block("steam_engine_rear", er)

ep = Model({"rod": "steel", "head": "brass", "conrod": "dark_panel"}, "steel")
ep.box([7, ENG_Y - 1, -13], [9, ENG_Y + 1, -1], "rod", split=False)
ep.box([5.5, ENG_Y - 3.8, -3], [10.5, ENG_Y + 3.8, 2], "head", split=False)
ep.box([7.25, ENG_Y - 0.75, 2], [8.75, ENG_Y + 0.75, 7.3], "conrod", split=False)
save_block("steam_engine_piston", ep)

ef = Model({"rim": "steel_panel", "spoke": "dark_panel", "hub": "gear_hub", "shaft": "steel", "crank": "brass"},
           "steel_panel")
FW_Z, FW_R = 8, 15.5
ef.ring_x(ENG_Y, FW_Z, FW_R, 3, 22, 26.5, "rim")
ef.spokes_x(ENG_Y, FW_Z, FW_R - 2, 2, 23.25, 25.25, "spoke")
ef.box([21.5, ENG_Y - 3.5, FW_Z - 3.5], [27, ENG_Y + 3.5, FW_Z + 3.5], "hub", split=False)
ef.octagon_axis("x", ENG_Y, FW_Z, 3, -3.5, 21.5, "shaft", split=False)
for x0 in (4, 11):
    ef.box([x0, ENG_Y - 5, FW_Z - 2], [x0 + 1, ENG_Y + 2, FW_Z + 2], "crank", split=False)
ef.octagon_axis("x", ENG_Y - 3, FW_Z, 2.5, 5, 11, "shaft", split=False)
save_block("steam_engine_flywheel", ef)
empty("steam_engine", "slate_panel")
# item: the whole engine as drawn, scaled to 0.6
item_scaled("steam_engine", [(eb, 0, 0, -16), (er, 0, 0, 24), (ep, 0, 0, 24), (ef, 0, 0, 24)], 0.6)

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
# Inserters: a round low base with a pivot (the arm - two tier-coloured segments, joints and a claw -
# is drawn by InserterRenderer from the pivot top at y 5.5)
# =====================================================================================
ti = Model({"plate": "dark_panel", "steel": "steel", "dark": "dark"}, "#housing")
ti.parent = "minecraft:block/block"
ti.octagon_y(8, 8, 14, 0, 1.5, "plate", cull=("down",))
ti.octagon_y(8, 8, 11, 1.5, 3.5, "housing")
ti.octagon_y(8, 8, 8, 3.5, 4.5, "dark")
ti.octagon_y(8, 8, 5, 4.5, 5.5, "steel")
ti.box([6.5, 1.5, 0.5], [9.5, 2.5, 2.5], "dark", split=False)  # marker on the pickup side
ti.textures.pop("particle")
ti.textures["particle"] = "#housing"
save_block("template_inserter", ti)
INSERTER_TIERS = (("burner_inserter", "rust_panel", "rust"), ("inserter", "tier_yellow_panel", "tier_yellow"),
                  ("long_handed_inserter", "tier_red_panel", "tier_red"), ("fast_inserter", "tier_blue_panel", "tier_blue"))
for name, housing, arm in INSERTER_TIERS:
    save_block(name, Model({"housing": housing}, None, parent="minetorio:block/template_inserter"))
    # item: base + the arm raised over the pickup side
    long = name.startswith("long")
    up, fore = (11, 13) if long else (9, 10)
    im = Model({"plate": "dark_panel", "steel": "steel", "dark": "dark", "housing": housing, "arm": arm,
                "joint": "dark_panel"}, housing)
    im.elements = json.loads(json.dumps(ti.elements))
    im.cbox(8, 8, 3, 3, 5.5, 7.5, "joint", split=False)
    im.box([7, 6, 7], [9, 6 + up, 9], "arm", split=False, rot=("x", 22.5, [8, 6, 8]))
    ey = 6 + up * math.cos(math.radians(22.5))
    ez = 8 + up * math.sin(math.radians(22.5))
    im.cbox(8, ez, 3, 3, ey - 1.5, ey + 1.5, "joint", split=False)
    im.box([7.25, ey - 0.75, ez - fore], [8.75, ey + 0.75, ez], "arm", split=False, rot=("x", -22.5, [8, ey, ez]))
    hy = ey - fore * math.sin(math.radians(22.5))
    hz = ez - fore * math.cos(math.radians(22.5))
    im.cbox(8, hz, 3, 3, hy - 2.5, hy, "joint", split=False)
    for x0 in (6.75, 8.75):
        im.box([x0, hy - 4.5, hz - 0.5], [x0 + 0.5, hy - 2.5, hz + 0.5], "steel", split=False)
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
