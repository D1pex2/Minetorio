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
            var = tex.get(fc) if isinstance(tex, dict) else tex   # a face missing from the dict is left out
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

    def _poly(self, axis, c1, c2, r0, r1, w, s0, s1, tex, k, rot_origin, **box_kw):
        """Box between radii r0..r1, w wide, s0..s1 along the axis, in 16-gon step k. Plane coords (c1, c2):
        axis y -> (x, z), axis x -> (y, z)."""
        q, res = self._step(k)
        # corners of the step-0 box (offset toward -c2) turned by q
        pts = [self._quarter(q, a, b) for a in (-w / 2, w / 2) for b in (-r1, -r0)]
        a0, a1 = min(p[0] for p in pts), max(p[0] for p in pts)
        b0, b1 = min(p[1] for p in pts), max(p[1] for p in pts)
        kw = {"rot": (axis, res, rot_origin)} if res else {}
        if axis == "y":
            self.box([c1 + a0, s0, c2 + b0], [c1 + a1, s1, c2 + b1], tex, split=False, **kw, **box_kw)
        else:
            self.box([s0, c1 + a0, c2 + b0], [s1, c1 + a1, c2 + b1], tex, split=False, **kw, **box_kw)

    def ring_y(self, cx, cz, R, t, y0, y1, tex, steps=range(16), **kw):
        """Flat 16-gon ring about a vertical axis: outer radius R, wall t, y0..y1."""
        L = 2 * R * math.tan(math.pi / 16)
        for k in steps:
            e = EPS * (k % 2)
            self._poly("y", cx, cz, R - t, R, L, y0 + e, y1 - e, tex, k, [cx, y0, cz], **kw)
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

    def prism16_x(self, cy, cz, a, x0, x1, tex, **kw):
        """Disc / prism along x through (cy, cz), apothem a (an upright gear face, a round end cap)."""
        s = a * math.tan(math.pi / 16)
        for i, (along_z, ang) in enumerate(self.PRISM16):
            e = EPS * i
            r = {"rot": ("x", ang, [x0, cy, cz])} if ang else {}
            h, d = (s, a) if along_z else (a, s)
            self.box([x0 + e, cy - h, cz - d], [x1 - e, cy + h, cz + d], tex, split=False, **r, **kw)
        return self

    def teeth_x(self, cy, cz, r0, r1, w, x0, x1, tex):
        """16 gear teeth about an axis along x; neighbours overlap near the root, so odd ones are EPS thinner."""
        for k in range(16):
            e = EPS * (k % 2)
            self._poly("x", cy, cz, r0, r1, w, x0 + e, x1 - e, tex, k, [x0, cy, cz])
        return self

    def gear_x(self, cy, cz, r, x0, x1, tex):
        """Upright toothed gear about an axis along x: a solid 16-sided disc, 16 teeth, a dark hub."""
        self.prism16_x(cy, cz, r, x0, x1, tex["disc"])
        self.teeth_x(cy, cz, r - 0.5, r + 2, 2, x0 + 0.25, x1 - 0.25, tex["teeth"])
        self.prism16_x(cy, cz, max(1.5, r * 0.35), x0 - 0.5, x1 + 0.5, tex["hub"])
        return self

    def dome(self, cx, cz, R, y0, n, tex):
        """Hollow hemisphere of radius R standing on y0 (for glass): n stacked 16-sided rings (apothem at mid
        height), each wall reaching just inside the next ring, so the dome is closed but one glass layer
        thick; the top ring is a solid cap. Every face shows the whole texture (one lattice cell per face)."""
        h = R / n
        a = [math.sqrt(R * R - ((i + 0.5) * h) ** 2) for i in range(n)]
        for i in range(n):
            y0i, y1i = y0 + i * h, y0 + (i + 1) * h
            if i == n - 1:
                self.prism16_y(cx, cz, a[i], y0i, y1i, tex, fit=FACES)
            else:
                self.ring_y(cx, cz, a[i], max(1.0, a[i] - a[i + 1] + 0.75), y0i, y1i, tex, fit=FACES)
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
# Machines, each modelled from its Factorio wiki image (checked side by side in art/render_compare.py).
# =====================================================================================

# =====================================================================================
# Assembling machines (3x3, 2 high; footprint center at 8, 8), after the Factorio 1.1 sprites: a sloped,
# riveted skirt (grey-green tier 1, blue tier 2) under a housing whose recessed copper-brown deck is
# crowded with machinery. The static body is one model; the parts that move while crafting are their
# own models, authored where they rest, and AssemblerRenderer moves them:
#   tier 1 (crank train): a pair of brass cogs over the back gearbox, a push rod driven by the first
#     cog, a train of three upright steel gears, a carriage running along the work table's rails;
#   tier 2 (robot arm): the same cogs, an arm on a turret swinging over the work table, its press head
#     plunging at each end of the swing, a fan turning in a ring;
#   both: the status lamp (its lit lens is drawn only while working).
# =====================================================================================
ASM_DECK = 18          # top of the housing (the deck)
ASM_COG_Y, ASM_COG_X, ASM_COG_R = 25, (0, 10.5), 4.5          # back cogs, axis along z
ASM_COG_Z = (-7.5, -6)
ASM_GEAR_Y, ASM_GEAR_X, ASM_GEAR_R = 24.5, (9, 16.5, 24), 3   # tier-1 gear train, axis along z
ASM_GEAR_Z = (2, 3.5)
ASM_ARM_PIVOT = (10, 10)                                       # tier-2 turret axis (x, z)
ASM_FAN = (20, 20)                                             # tier-2 fan axis (x, z)
ASM_LAMP = (-7, 22, -4, 25, 25, 26.5)                          # lamp lens box x0 z0 x1 z1 y0 y1


ASM_MESH_DZ = 0.1      # a meshing neighbour sits this much further south, so no two cogs share a face plane


def cog_z(m, cx, cy, r, z0, z1, disc, teeth, hub, half_pitch=False):
    """Upright cog about an axis along z: an octagonal disc, 8 teeth (4 bars, each EPS thinner), a hub.
    half_pitch: turned 22.5 degrees (half a tooth), the pose of a cog meshing with an unturned one."""
    s = r * 0.83
    if half_pitch:   # the octagon as two squares at +-22.5
        m.box([cx - r, cy - r, z0], [cx + r, cy + r, z1], disc, split=False, rot=("z", 22.5, [cx, cy, z0]))
        m.box([cx - s, cy - s, z0 + EPS], [cx + s, cy + s, z1 - EPS], disc, split=False,
              rot=("z", -22.5, [cx, cy, z0]))
        bars = ((0, 22.5), (0, -22.5), (1, 22.5), (1, -22.5))
    else:
        m.octagon_axis("z", cy, cx, 2 * r, z0, z1, disc, split=False)
        bars = ((0, 0), (1, 0), (0, 45), (0, -45))
    L = r + 1.5
    for i, (along_y, ang) in enumerate(bars):
        e = 0.25 + EPS * i
        kw = {"rot": ("z", ang, [cx, cy, z0])} if ang else {}
        if along_y:
            m.box([cx - 1, cy - L, z0 + e], [cx + 1, cy + L, z1 - e], teeth, split=False, **kw)
        else:
            m.box([cx - L, cy - 1, z0 + e], [cx + L, cy + 1, z1 - e], teeth, split=False, **kw)
    m.octagon_axis("z", cy, cx, max(2.0, r * 0.7), z0 - 0.5, z1 + 0.5, hub, split=False)
    return m


ASM_TEX = {"dark": "dark", "frame": "dark_panel", "steel": "steel", "steelp": "steel_panel", "copper": "copper",
           "brass": "brass", "rust": "rust_panel", "rusty": "rust", "vent": "vent", "hub": "gear_hub",
           "deck": "asm_deck", "plate": "asm_plate", "gauge": "asm_gauge", "rail": "asm_rail", "table": "asm_table",
           "ctrl": "asm_ctrl", "arm": "asm_arm", "lens": "asm_lamp", "pipe_h": "pipe_h", "pipe_v": "pipe_v",
           "pipe_end": "pipe_end"}


def assembler_shell(tier):
    """Skirt, housing, the deck's raised rim and what both tiers share: back gearbox with the cogs' axle
    blocks, the light chevron plate, the corner post with its cable, a copper pipe column."""
    m = Model({**ASM_TEX, "skirt": f"asm{tier}_skirt", "body": f"asm{tier}_body"}, f"asm{tier}_skirt")
    side = {"north": "skirt", "south": "skirt", "east": "skirt", "west": "skirt", "up": "skirt", "down": "frame"}
    for i, (y0, y1) in enumerate(((0, 3), (3, 6), (6, 9))):          # sloped skirt: three steps
        d = 1.5 * i
        m.chamfer(-16 + d, -16 + d, 32 - d, 32 - d, y0, y1, side, c=5)
    m.chamfer(-12.5, -12.5, 28.5, 28.5, 9, ASM_DECK,
              {"north": "body", "south": "body", "east": "body", "west": "body", "up": "deck", "down": "frame"}, c=4)
    # raised rim round the deck (the dark frame of the recess); the corner bevels get short turned bars
    top = ASM_DECK + 1.5
    m.box([-8.5, ASM_DECK, -12.5], [24.5, top, -10.5], "frame", skip=("down",))
    m.box([-8.5, ASM_DECK, 26.5], [24.5, top, 28.5], "frame", skip=("down",))
    m.box([-12.5, ASM_DECK, -8.5], [-10.5, top, 24.5], "frame", skip=("down",))
    m.box([26.5, ASM_DECK, -8.5], [28.5, top, 24.5], "frame", skip=("down",))
    for (x, z, a) in ((-10.5, -10.5, 45), (26.5, -10.5, -45), (-10.5, 26.5, -45), (26.5, 26.5, 45)):
        m.cbox(x, z, 5.6, 2, ASM_DECK + EPS, top - EPS, "frame", split=False, skip=("down",),
               rot=("y", a, [x, ASM_DECK, z]))
    # back gearbox (rusty) under the cogs, the cogs' axle blocks on it
    m.box([-9, ASM_DECK, -10], [16, ASM_DECK + 5, -4], {"north": "rust", "south": "rust", "east": "rust",
                                                       "west": "rust", "up": "rusty"}, skip=("down",))
    for cx in ASM_COG_X:
        m.cbox(cx, -8.75, 3, 1.5, ASM_DECK + 5, ASM_COG_Y + 1, "frame", split=False, skip=("down",))
    # light chevron plate in the back right corner
    m.box([17, ASM_DECK, -10], [26, ASM_DECK + 2, 2], {"north": "frame", "south": "frame", "east": "frame",
                                                      "west": "frame", "up": "plate"}, fit=("up",), skip=("down",))
    # corner post with a brace and a cable hanging to the gearbox
    m.box([-10.5, ASM_DECK, -10.5], [-8.5, ASM_DECK + 12, -8.5], "frame", split=False, skip=("down",))
    m.box([-10.25, ASM_DECK + 6, -10], [-9, ASM_DECK + 7, -2], "dark", split=False,
          rot=("x", -45, [-9.6, ASM_DECK + 6.5, -10]))
    m.box([-8.5, ASM_DECK + 10, -9.75], [-5, ASM_DECK + 10.75, -9], "dark", split=False)
    m.box([-5.75, ASM_DECK + 5, -9.75 - EPS], [-5, ASM_DECK + 10, -9 + EPS], "dark", split=False)
    # copper pipe column on the east side, going down into the housing
    pz, ps = (13.5, 3) if tier == 1 else (13, 4)
    m.pipe_y(24, pz, ASM_DECK, ASM_DECK + 9, size=ps)
    m.cbox(24, pz, 5, 5, ASM_DECK + 9, ASM_DECK + 10, "brass", split=False)
    return m


def lamp_housing(m, base_y):
    x0, z0, x1, z1, y0, y1 = ASM_LAMP
    if base_y < y0:
        m.box([x0 - 1, base_y, z0 - 1], [x1 + 1, y0, z1 + 1], "frame", split=False, skip=("down",))
    m.box([x0, y0, z0], [x1, y1, z1], {"north": "frame", "south": "frame", "east": "frame", "west": "frame",
                                       "up": "lens"}, fit=("up",), split=False, skip=("down",))


def assembler_body(tier):
    m = assembler_shell(tier)
    D = ASM_DECK
    if tier == 1:
        # west: pressure gauge, a copper cylinder lying along x, the motor box with the lamp
        m.octagon_y(-5.5, -0.5, 6, D, D + 4, {"north": "frame", "south": "frame", "east": "frame", "west": "frame",
                                             "up": "gauge"})
        m.prism16_x(D + 2.5, 13, 2.5, -10, 0, "copper")
        m.box([0, D + 0.25, 10.75], [1, D + 4.75, 15.25], "frame", split=False, skip=("down",))
        m.box([-10, D, 17], [-1, D + 7, 26], {"north": "rust", "south": "vent", "east": "vent", "west": "rust",
                                             "up": "frame"}, fit=("south", "east"), split=False, skip=("down",))
        lamp_housing(m, D + 7)
        # push-rod guide: a block the rod slides through
        m.box([-2.5, D, 0], [2.5, D + 5, 3], "frame", split=False, skip=("down",))
        # gear train's bearing wall behind the three gears
        m.box([5, D, -1], [26.5, D + 7, 1.5], {"north": "rust", "south": "rust", "east": "rust", "west": "rust",
                                               "up": "frame"}, skip=("down",))
        # front: the work table's two guide rails
        for z in (15, 21):
            m.box([1, D, z], [26, D + 2, z + 2], {"north": "rail", "south": "rail", "east": "frame", "west": "frame",
                                                  "up": "steel"}, skip=("down",))
        # east: stepped rust plates
        m.box([18, D, 4], [26, D + 1.5, 12], "rust", skip=("down",))
        m.box([20, D + 1.5, 6], [25, D + 3, 11], "rusty", split=False, skip=("down",))
    else:
        # the robot arm's turret base and the work table it serves
        m.octagon_y(*ASM_ARM_PIVOT, 9, D, D + 3, {"north": "frame", "south": "frame", "east": "frame",
                                                 "west": "frame", "up": "hub"})
        m.box([-10.5, D, -2], [-2, D + 1, 20], {"north": "frame", "south": "frame", "east": "frame", "west": "frame",
                                                "up": "table"}, skip=("down",))
        # west: the control box with the lamp
        m.box([-10, D, 21], [-1, D + 7, 26], {"north": "ctrl", "south": "ctrl", "east": "ctrl", "west": "ctrl",
                                             "up": "frame"}, fit=("north", "south", "east", "west"), split=False,
              skip=("down",))
        lamp_housing(m, D + 7)
        # south-east: the fan's ring housing
        m.ring_y(*ASM_FAN, 5.5, 1.5, D, D + 3, "frame")
        m.octagon_y(*ASM_FAN, 2.5, D, D + 1, "dark")
        # east: copper pipe run and a gauge
        m.pipe_z(24, D + 2.5, 15, 26.5, size=4)
        m.octagon_y(21, 6, 5, D, D + 3.5, {"north": "frame", "south": "frame", "east": "frame", "west": "frame",
                                           "up": "gauge"})
    return m


# --- moving parts (AssemblerRenderer moves them; each is authored in its rest pose) -----------------------

def assembler_cog(b=False):
    """Back cog: the first (b False, drives the push rod) or its meshing neighbour (half a tooth turned,
    ASM_MESH_DZ further south)."""
    m = Model(ASM_TEX, "brass")
    dz = ASM_MESH_DZ if b else 0
    cog_z(m, ASM_COG_X[1 if b else 0], ASM_COG_Y, ASM_COG_R, ASM_COG_Z[0] + dz, ASM_COG_Z[1] + dz,
          "brass", "brass", "hub", half_pitch=b)
    return m


def assembler_gear(b=False):
    """Tier-1 gear train: gear A (drawn at the first and third place) or the middle gear B meshing with both."""
    m = Model(ASM_TEX, "steel")
    dz = ASM_MESH_DZ if b else 0
    cog_z(m, ASM_GEAR_X[1 if b else 0], ASM_GEAR_Y, ASM_GEAR_R, ASM_GEAR_Z[0] + dz, ASM_GEAR_Z[1] + dz,
          "steelp", "steel", "hub", half_pitch=b)
    return m


def assembler_rod():
    """Push rod along z from the first cog's crank to the crosshead (slides +-2.5 px in z)."""
    m = Model(ASM_TEX, "steel")
    m.box([-0.75, ASM_DECK + 2.25, -6], [0.75, ASM_DECK + 3.75, 7], "steel", split=False)
    m.box([-2, ASM_DECK + 1.5, 6], [2, ASM_DECK + 4.5, 9], {"north": "frame", "south": "frame", "east": "frame",
                                                           "west": "frame", "up": "brass", "down": "frame"}, split=False)
    return m


def assembler_slider():
    """Carriage on the work table's rails (runs +-7 px along x) with a tool post."""
    m = Model(ASM_TEX, "brass")
    m.box([10, ASM_DECK + 2, 14], [15, ASM_DECK + 4, 24], {"north": "frame", "south": "frame", "east": "frame",
                                                          "west": "frame", "up": "brass", "down": "frame"}, split=False)
    m.box([11.5, ASM_DECK + 4, 17], [13.5, ASM_DECK + 7, 21], "frame", split=False, skip=("down",))
    m.box([12, ASM_DECK + 7, 18.5], [13, ASM_DECK + 9, 19.5], "steel", split=False, skip=("down",))
    return m


def assembler_arm():
    """Robot arm on the turret (turns about the turret axis): shoulder, twin beams reaching west, a hydraulic
    cylinder under them, the wrist."""
    m = Model(ASM_TEX, "asm_arm")
    px_, pz = ASM_ARM_PIVOT
    D = ASM_DECK
    m.cbox(px_, pz, 7, 7, D + 3, D + 11, {"north": "arm", "south": "arm", "east": "arm", "west": "arm", "up": "hub",
                                           "down": "frame"}, split=False)
    for z0 in (pz - 3.25, pz + 0.75):
        m.box([-9, D + 8, z0], [px_ - 3.5, D + 11 - EPS, z0 + 2.5], "arm", split=False)
    # hydraulic cylinder from the shoulder's foot up to the beams (steel sleeve, copper piston)
    m.box([-1, D + 4.5, pz - 0.75], [px_ - 3.5, D + 6, pz + 0.75], "steel", split=False,
          rot=("z", -22.5, [px_ - 3.5, D + 4.5, pz]))
    m.box([-5, D + 4.75, pz - 0.5], [-1, D + 5.75, pz + 0.5], "copper", split=False,
          rot=("z", -22.5, [px_ - 3.5, D + 4.5, pz]))
    m.box([-14, D + 6.5, pz - 3], [-9, D + 11.5, pz + 3], {"north": "arm", "south": "arm", "east": "arm", "west": "arm",
                                                          "up": "hub", "down": "frame"}, split=False)
    return m


def assembler_head():
    """The arm's press head under the wrist (turns with the arm, plunges 2 px at each end of the swing)."""
    m = Model(ASM_TEX, "steel")
    D = ASM_DECK
    m.cbox(-11.5, ASM_ARM_PIVOT[1], 1.5, 1.5, D + 4, D + 6.5, "steel", split=False, skip=("up",))
    m.cbox(-11.5, ASM_ARM_PIVOT[1], 5, 5, D + 3, D + 4, {"north": "frame", "south": "frame", "east": "frame",
                                                       "west": "frame", "up": "frame", "down": "steelp"}, split=False)
    return m


def assembler_fan():
    m = Model(ASM_TEX, "steel")
    fx, fz = ASM_FAN
    D = ASM_DECK
    m.box([fx - 3.75, D + 1.5, fz - 1], [fx + 3.75, D + 2, fz + 1], "steelp", split=False)
    m.box([fx - 1, D + 1.5 + EPS, fz - 3.75], [fx + 1, D + 2 - EPS, fz + 3.75], "steelp", split=False)
    m.box([fx - 3.5, D + 1.5 + 2 * EPS, fz - 0.9], [fx + 3.5, D + 2 - 2 * EPS, fz + 0.9], "steel", split=False,
          rot=("y", 45, [fx, D, fz]))
    m.box([fx - 3.5, D + 1.5 + 3 * EPS, fz - 0.9], [fx + 3.5, D + 2 - 3 * EPS, fz + 0.9], "steel", split=False,
          rot=("y", -45, [fx, D, fz]))
    m.octagon_y(fx, fz, 2.6, D + 1.25, D + 2.5, {"north": "dark", "south": "dark", "east": "dark", "west": "dark",
                                               "up": "hub", "down": "dark"})
    return m


def assembler_lamp():
    """Lit lens, a hair larger than the dull one it covers; drawn at full brightness while working."""
    m = Model({"glow": "asm_lamp_glow"}, "asm_lamp_glow")
    x0, z0, x1, z1, y0, y1 = ASM_LAMP
    m.box([x0 - EPS, y0, z0 - EPS], [x1 + EPS, y1 + EPS, z1 + EPS], "glow", fit=FACES, split=False, skip=("down",))
    return m


def asm_copy(m, dx):
    c = Model(m.textures)
    c.elements = m.shifted(dx, 0, 0)
    return c


asm_moving = {"cog": assembler_cog(), "cog_b": assembler_cog(True), "gear": assembler_gear(),
              "gear_b": assembler_gear(True), "rod": assembler_rod(),
              "slider": assembler_slider(), "arm": assembler_arm(), "head": assembler_head(),
              "fan": assembler_fan(), "lamp": assembler_lamp()}
for part, model in asm_moving.items():
    save_block(f"assembling_machine_{part}", model)
for tier in (1, 2):
    body = assembler_body(tier)
    save_block(f"assembling_machine_{tier}_body", body)
    empty(f"assembling_machine_{tier}", f"asm{tier}_skirt")
    # the item shows the machine at rest (as a ghost does): body plus every moving part in its rest pose
    extra = [asm_moving["cog"], asm_moving["cog_b"]]
    if tier == 1:
        extra += [asm_moving["gear"], asm_moving["gear_b"], asm_copy(asm_moving["gear"], ASM_GEAR_X[2] - ASM_GEAR_X[0])]
        extra += [asm_moving["rod"], asm_moving["slider"]]
    else:
        extra += [asm_moving["arm"], asm_moving["head"], asm_moving["fan"]]
    item_from(f"assembling_machine_{tier}", body, *extra)

# =====================================================================================
# Electric mining drill (3x3, 3 high). Factorio: a dark base, two tall upright rails on the left and
# right with a mechanism carriage between them, a yellow output chute at the front and a status light.
#   body:     base, deck, chute, the rails' lower half;
#   tower:    the rails' upper half with hazard caps, drawn one block up (y = world y - 16);
#   carriage: motor housing riding up and down between the rails while mining (DrillRenderer);
#   head:     drill shaft from the deck into the carriage, spinning while mining.
# =====================================================================================
EDR_RAILS = ((-14, -8), (24, 30))     # rail x ranges; rails run z -12..28
EDR_LAMP = (20, -10, 9.5)              # status lens (x, z, top)
edb = Model({"frame": "dark_panel", "body": "dark_panel", "deck": "dark", "rail": "edr_rail", "trim": "tier_yellow_panel",
             "hazard": "hazard", "chute": "tier_yellow_panel", "arrow": "chute_arrow", "dark": "dark",
             "green": "tier_green", "steel": "steel", "gearbox": "steel_panel", "vent": "vent"}, "dark_panel")
edb.chamfer(-16, -16, 32, 32, 0, 3, "frame", c=5)
edb.chamfer(-14, -14, 30, 30, 3, 7, {"north": "body", "south": "body", "east": "body", "west": "body",
                                    "up": "deck", "down": "frame"}, c=4)
# the rails are open truss frames: three corner/mid posts, a bottom and a top beam, a Warren zigzag of
# 45 degree diagonals between them; every gap is real geometry (nothing covers it)
EDR_POSTS = ((-12, -9), (6.5, 9.5), (25, 28))
EDR_BAYS = (((-9, 6.5)), ((9.5, 25)))


def edr_diag(m, x0, x1, bay, yc, run, th, angle, tex):
    """A diagonal brace across a bay (run = horizontal extent, th = thickness), turned about the x axis."""
    zc = (bay[0] + bay[1]) / 2
    ln = run / math.cos(math.radians(abs(angle)))
    cx = (x0 + x1) / 2
    m.box([cx - 1.5, yc - th / 2, zc - ln / 2], [cx + 1.5, yc + th / 2, zc + ln / 2], tex,
          rot=("x", angle, (cx, yc, zc)), split=False)


for x0, x1 in EDR_RAILS:
    for pz0, pz1 in EDR_POSTS:
        edb.box([x0, 7, pz0], [x1, 32, pz1], "rail", skip=("up",), split=False)
    for bz0, bz1 in EDR_BAYS:
        for y0, y1 in ((7, 10), (29, 32)):
            edb.box([x0, y0, bz0], [x1, y1, bz1], "rail", skip=("north", "south"), split=False)
    for i, bay in enumerate(EDR_BAYS):
        edr_diag(edb, x0, x1, bay, 19.5, 17.5 if i == 0 else 15.5, 2, 45 if i == 0 else -45, "rail")
    # a yellow-black band low on each rail's front post
    edb.box([x0 - EPS, 12, -12.5], [x1 + EPS, 16, -11.5], "hazard", split=False)
# output chute: a yellow spout on the front edge, the drop arrow on top, a dark mouth
edb.box([2, 3, -15.5], [14, 8, -10], {"north": "chute", "south": "chute", "east": "chute", "west": "chute",
                                     "up": "arrow", "down": "chute"}, fit=("up",), split=False)
edb.box([3.5, 3.5, -16], [12.5, 7, -15.5], "dark", only=("north", "east", "west", "up"), split=False)
# status light on the deck, front right: a dull green lens in a dark housing (the lit lens is its own model)
edb.cbox(20, -10, 4, 4, 7, 8, "dark", split=False, skip=("down",))
edb.cbox(*EDR_LAMP[:2], 2, 2, 8, EDR_LAMP[2], "green", split=False, skip=("down",))
# the reference's yellow base frame: lips along the front and the sides of the base
for f, t in (([-15, 3, -15], [1, 4.5, -14]), ([15, 3, -15], [31, 4.5, -14]),
             ([-15, 3, -14], [-14, 4.5, 10]), ([30, 3, -14], [31, 4.5, 10])):
    edb.box(f, t, "trim", skip=("down",), split=False)
# the grey gearbox behind the chute, a vent on its front
edb.box([2.5, 7, -10.5], [13.5, 11.5, -5], {"north": "vent", "south": "gearbox", "east": "gearbox", "west": "gearbox",
                                            "up": "gearbox"}, fit=("north",), skip=("down",), split=False)
save_block("electric_mining_drill_body", edb)

# the lit status lens, a hair larger than the dull one; DrillRenderer draws it at full brightness while mining
edl = Model({"glow": "edr_lamp_glow"}, "edr_lamp_glow")
edl.cbox(*EDR_LAMP[:2], 2 + 2 * EPS, 2 + 2 * EPS, 8, EDR_LAMP[2] + EPS, "glow", fit=FACES, split=False, skip=("down",))
save_block("electric_mining_drill_lamp", edl)

edt = Model({"rail": "edr_rail", "cap": "dark", "hazard": "hazard", "steel": "steel"}, "dark_panel")
for x0, x1 in EDR_RAILS:
    for pz0, pz1 in EDR_POSTS:
        edt.box([x0, 16, pz0], [x1, 26, pz1], "rail", skip=("down",), split=False)
    for bz0, bz1 in EDR_BAYS:
        edt.box([x0, 23, bz0], [x1, 26, bz1], "rail", skip=("north", "south"), split=False)
    for i, bay in enumerate(EDR_BAYS):
        edr_diag(edt, x0, x1, bay, 19.5, 16.5, 1.5, 22.5 if i == 0 else -22.5, "rail")
    edt.box([x0 - 0.5, 26, -11.5], [x1 + 0.5, 27.5, 28.5], "cap", split=False)
    # yellow-black tip at the front of each rail
    edt.box([x0 - 0.6, 23, -12.5], [x1 + 0.6, 28, -9.5], "hazard", split=False)
save_block("electric_mining_drill_tower", edt)

edc = Model({"beam": "dark_panel", "gbox": "dark", "band": "tier_yellow_panel", "motor": "edr_motor", "drum": "steel",
             "end": "dark", "cyl": "steel", "ram": "piston_rod", "hose": "hose"}, "dark_panel")
EDR_CAR_Y = 16        # carriage bottom at rest; it rides up to EDR_CAR_Y + DrillRenderer.CARRIAGE_TRAVEL px
# an open frame between the rails: front and back cross beams, a spine and the gearbox around the shaft
for z0, z1 in ((-6, -2), (18, 22)):
    edc.box([-8, EDR_CAR_Y, z0], [24, EDR_CAR_Y + 4, z1], "beam")
edc.box([-7.5, EDR_CAR_Y + 1, -6.5], [23.5, EDR_CAR_Y + 3, -6], "band", only=("north", "up", "east", "west"), split=False)
edc.box([5, EDR_CAR_Y + 0.5, -2], [11, EDR_CAR_Y + 3.5, 18], "beam", split=False)
edc.octagon_y(8, 8, 11, EDR_CAR_Y, EDR_CAR_Y + 5, "gbox")
# the reference's copper-pink motor head on the gearbox, a dark lid
edc.octagon_y(8, 8, 8, EDR_CAR_Y + 5, EDR_CAR_Y + 10, "motor")
edc.octagon_y(8, 8, 5, EDR_CAR_Y + 10, EDR_CAR_Y + 11, "gbox")
# the two hydraulic rams reaching out from the motor to the rails, rising outward (the reference's V)
C0 = EDR_CAR_Y + 6
edc.box([12, C0, 6.5], [21, C0 + 3, 9.5], "cyl", split=False, rot=("z", 22.5, [12, C0, 8]))
edc.box([-5, C0, 6.5], [4, C0 + 3, 9.5], "cyl", split=False, rot=("z", -22.5, [4, C0, 8]))
edc.box([20, C0 + 0.75, 7.25], [23, C0 + 2.25, 8.75], "ram", split=False, rot=("z", 22.5, [12, C0, 8]))
edc.box([-7, C0 + 0.75, 7.25], [-4, C0 + 2.25, 8.75], "ram", split=False, rot=("z", -22.5, [4, C0, 8]))
# a rubber hose from the motor down to the front beam
edc.box([1, EDR_CAR_Y + 4, -4.5], [3, EDR_CAR_Y + 6, 4], "hose", split=False, rot=("x", -22.5, [2, EDR_CAR_Y + 4, -4.5]))
# motor drum on the back beam
edc.prism16_x(EDR_CAR_Y + 7.5, 20, 3.5, -4, 20, "drum")
for x0, x1 in ((-5, -4), (20, 21)):
    edc.prism16_x(EDR_CAR_Y + 7.5, 20, 2.75, x0, x1, "end")
save_block("electric_mining_drill_carriage", edc)

edh = Model({"bit": "drill_bit", "collar": "tier_yellow", "disc": "steel_panel", "teeth": "dark", "hub": "gear_hub"},
            "drill_bit")
# the drill head on the deck: a big toothed cutter disc, the auger shaft up into the carriage, yellow collars
edh.gear_y(8, 8, 9, 7.25, 9.25, "disc", "teeth", tooth=3, bars=8, width=3)
edh.octagon_y(8, 8, 7, 7, EDR_CAR_Y + 4.75, "bit")
for y in (10.5, 14):
    edh.octagon_y(8, 8, 9, y, y + 1.5, "collar")
save_block("electric_mining_drill_head", edh)
empty("electric_mining_drill", "dark_panel")
item_from("electric_mining_drill", edb, edh, edc, edt, offsets=[(0, -14, 0), (0, -14, 0), (0, -14, 0), (0, 2, 0)])

# =====================================================================================
# Burner mining drill (2x2, 2 high, on the controller's blockstate: x 0..32, z -16..16, front = north;
# the spinning head is drawn by the BER on the footprint center, its model in the center frame (8, 8)).
# Factorio 1.1 (sprite and its 32-frame animation): a dark iron mechanism box held up on splayed legs like
# a derrick, a rusty funnel down the front to the output, a chimney at the back; while it mines the drill
# mechanism turns, a piston beside the box pumps up and down and (here) the fire glows through the fire
# door. Moving parts, drawn by DrillRenderer in the center frame (footprint center at 8, 8 = controller
# 16, 0) and authored in their rest pose:
#   head:   gear on the deck, the drill mast and the pick across its top, turning about the vertical axis;
#   piston: the rod in its housing on the box's west side, rising BMD_PISTON_STROKE px once per head turn;
#   fire:   the fire behind the fire door at the back, drawn at full brightness while mining.
# =====================================================================================
BMD_TO_CENTER = (-8, 0, 8)     # controller frame -> center frame
BMD_PISTON = (7.25, -7)        # piston axis (x, z), controller frame
bmd = Model({"body": "dark_panel", "frame": "dark_panel", "dark": "dark", "leg": "dark", "steel": "steel",
             "chute": "rust_panel", "arrow": "chute_arrow", "band": "rust", "mouth": "furnace_mouth", "pipe": "dark",
             "collar": "steel", "brass": "brass"},
            "rust_panel")
# four long legs, leaning in from the corners (front and back A-frames)
for z in (-12, 12):
    for i, (xf, ang) in enumerate(((2.5, -22.5), (29.5, 22.5))):
        e = EPS * i
        bmd.box([xf - 2, 0, z - 2 + e], [xf + 2, 24, z + 2 - e], "leg", split=False, rot=("z", ang, [xf, 0, z]))
        bmd.cbox(xf, z, 4.5, 6, 0, 1.5, "frame", split=False)
    # brace between the legs of an A-frame
    bmd.box([5, 7, z - 1 + 2 * EPS], [27, 8.5, z + 1 - 2 * EPS], "frame", split=False)
# the mechanism box: steel housing with rusty bands, a fire door at the back
bmd.box([9, 6, -9], [23, 22, 9], {"north": "body", "south": "body", "east": "body", "west": "body",
                                  "up": "frame", "down": "frame"})
for y0 in (9, 18):
    bmd.box([8.5, y0, -9.5], [23.5, y0 + 1.5, 9.5], "band", split=False)
bmd.box([12, 11, 9], [20, 16, 9.5], "mouth", only=("south",), fit=("south",), split=False)
# orange output chute out of the front with the drop arrow on top
bmd.box([11, 3, -15.5], [21, 9, -9], {"north": "chute", "south": "chute", "east": "chute", "west": "chute",
                                     "up": "arrow", "down": "chute"}, fit=("up",), split=False)
bmd.box([12.5, 4, -16], [19.5, 8, -15.5], "dark", only=("north",), split=False)
# chimney at the back corner of the deck (clear of the turning pick)
bmd.octagon_y(21, 6, 4, 22, 31, "pipe")
bmd.octagon_y(21, 6, 5.5, 28.5, 30.5, "collar")
# the piston's housing on the box's west side: a cylinder on two brackets, a brass gland on top
px_, pz_ = BMD_PISTON
bmd.octagon_y(px_, pz_, 3.5, 12, 22, "frame")
bmd.octagon_y(px_, pz_, 4.25, 21, 22.5, "brass")
for y0 in (13, 19):
    bmd.box([px_ + 1.5, y0, pz_ - 0.75], [9, y0 + 1.5, pz_ + 0.75], "dark", split=False, skip=("east",))
# the fire door's frame round the mouth at the back
bmd.box([11, 10, 9], [21, 11, 10], "band", split=False)
bmd.box([11, 16, 9], [21, 17, 10], "band", split=False)
save_block("burner_mining_drill", bmd)

# piston rod with a crosshead cap (slides up out of the housing)
bmp = Model({"rod": "piston_rod", "cap": "dark_panel"}, "piston_rod")
bmp.cbox(px_, pz_, 1.5, 1.5, 22.25, 29.5, "rod", split=False, skip=("down",))
bmp.cbox(px_, pz_, 2.5, 2.5, 29.5, 31, "cap", split=False)
bmp.elements = bmp.shifted(*BMD_TO_CENTER)
save_block("burner_mining_drill_piston", bmp)
# the fire behind the fire door (glows at full brightness while mining)
bmf = Model({"fire": "furnace_fire"}, "furnace_fire")
bmf.box([12.25, 11.25, 9.6], [19.75, 15.75, 9.6], "fire", only=("south",), fit=("south",), split=False, shade=False)
bmf.elements = bmf.shifted(*BMD_TO_CENTER)
save_block("burner_mining_drill_fire", bmf)

bmh = Model({"disc": "steel_panel", "teeth": "rust", "hub": "gear_hub", "bit": "drill_bit", "pick": "steel_panel",
             "tip": "dark"}, "steel")
# the turning mechanism: a gear on the deck, the drill mast and a pick across its top
bmh.gear_y(8, 8, 5.5, 22.5, 24.5, "disc", "teeth", tooth=2.5, bars=8, width=2.5)
bmh.octagon_y(8, 8, 3.5, 22.25, 28.5, "bit")
bmh.box([3, 26, 7], [13, 27.5, 9], "pick", split=False)
for x0, x1 in ((2, 3), (13, 14)):
    bmh.box([x0, 25, 7.25], [x1, 27.25, 8.75], "tip", split=False)
save_block("burner_mining_drill_head", bmh)
# item: body + head moved from the center frame (8, 8) to the footprint center (16, 0)
item_from("burner_mining_drill", bmd, bmh, bmp, offsets=[(0, 0, 0), (8, 0, -8), (8, 0, -8)])

# =====================================================================================
# Stone furnace (2x2x2 on the controller's blockstate; x 0..32, z -16..16; mouth on the south side).
# Factorio 1.1: a squat truncated pyramid of weathered grey-brown bricks with a square vent on top and the
# fire mouth in a brick arch low on the front. Lit (blockstate lit=true), the fire burns in the mouth, the
# vent's coals glow and the bricks facing the light are washed yellow by it - three animated strips with
# irregular frame times (flicker), the fire and the vent at full brightness. Nothing moves, so the furnace
# needs no block entity renderer; smoke rises from the vent while lit (StoneFurnaceBlock.animateTick).
# =====================================================================================
FUR_COURSES = 6          # 4 px courses, each 1.8 px further in
FUR_TOP = 4 * FUR_COURSES


def furnace(lit):
    m = Model({"stone": "furnace_stone_lit" if lit else "furnace_stone", "back": "furnace_stone",
               "ledge": "furnace_ledge", "mouth": "furnace_fire" if lit else "furnace_mouth",
               "vent": "furnace_vent_glow" if lit else "vent"}, "furnace_stone")
    step = {"north": "back", "south": "stone", "east": "stone", "west": "stone", "up": "ledge", "down": "back"}
    for i in range(FUR_COURSES):
        d = 1 + 1.8 * i
        m.box([d, 4 * i, -16 + d], [32 - d, 4 * (i + 1), 16 - d], step, skip=("down",) if i else ())
    # the vent: a raised rim round a square opening, the grate (glowing coals when lit) just over the top course
    t0, t1 = FUR_TOP, FUR_TOP + 2
    rim = {"north": "back", "south": "stone", "east": "stone", "west": "stone", "up": "ledge"}
    m.box([10, t0, -6], [22, t1, -4], rim, skip=("down",), split=False)
    m.box([10, t0, 4], [22, t1, 6], rim, skip=("down",), split=False)
    m.box([10, t0, -4], [12, t1, 4], rim, skip=("down",), split=False)
    m.box([20, t0, -4], [22, t1, 4], rim, skip=("down",), split=False)
    m.box([12, t0, -4], [20, t0 + 0.1, 4], "vent", only=("up",), fit=("up",), split=False,
          light=15 if lit else None, shade=not lit)
    # the brick arch round the mouth on the front: two pillars and a lintel, the fire just inside
    m.box([10, 0, 11], [12.5, 9, 16], rim, skip=("down",), split=False)
    m.box([19.5, 0, 11], [22, 9, 16], rim, skip=("down",), split=False)
    m.box([9.5, 9, 10.5], [22.5, 11.5, 16], rim, skip=("down",), split=False)
    m.box([12.5, 0, 15.25], [19.5, 9, 15.25], "mouth", only=("south",), fit=("south",), split=False,
          light=15 if lit else None, shade=not lit)
    return m


fur = furnace(False)
save_block("stone_furnace", fur)
save_block("stone_furnace_lit", furnace(True))
item_from("stone_furnace", fur)

# =====================================================================================
# Lab (3x3, 2 high): Factorio's geodesic glass dome - a hemisphere glazed with a brass triangle
# lattice - on a low round base, a doorway at the front. Factorio's 33-frame animation (1/3 speed) keeps
# the dome still and moves the light inside it; here (LabRenderer) the dome stays put, the mechanism
# inside turns while researching - a ring carrying four science pods round the core (lab_rotor) - and the
# core (lab_core_glow) and the glass (lab_dome) pulse blue at full brightness. Idle: dark glass, nothing lit.
# =====================================================================================
LAB_R, LAB_DOME_Y = 20, 4.5
lb = Model({"frame": "dark_panel", "rim": "steel_panel", "dark": "dark", "core": "lab_core", "brass": "brass",
            "red": "tier_red", "green": "tier_green", "door": "lab_door"}, "steel_panel")
lb.prism16_y(8, 8, 23, 0, 2.5, "frame")
lb.prism16_y(8, 8, 21.5, 2.5, LAB_DOME_Y, "rim")
# the equipment seen through the glass: a dark pedestal, a column with science bands and a brass top
lb.prism16_y(8, 8, 9, LAB_DOME_Y, 7, "dark")
lb.prism16_y(8, 8, 4, 7, 17, "core")
for y0, tex in ((9, "red"), (12.5, "green")):
    lb.prism16_y(8, 8, 4.5, y0, y0 + 2, tex)
lb.prism16_y(8, 8, 2.5, 17, 18.5, "brass")
# the doorway at the front, standing out of the glass
lb.box([3, LAB_DOME_Y, -13.5], [13, 13, -8], {"north": "door", "east": "rim", "west": "rim", "up": "rim"},
       fit=("north",), split=False)
save_block("lab_body", lb)

# the turning mechanism: a ring on four spokes round the core, a science pod hanging under each spoke's end
LAB_RING_Y = (11.25, 12.25)
lr = Model({"ring": "brass", "spoke": "dark_panel", "red": "tier_red", "green": "tier_green", "blue": "tier_blue",
            "yellow": "tier_yellow"}, "brass")
lr.ring_y(8, 8, 10.5, 1.5, *LAB_RING_Y, "ring")
lr.spokes_y(8, 8, 9.5, 1, LAB_RING_Y[0] + 0.1, LAB_RING_Y[1] - 0.1, "spoke")
for (x, z), tex in zip(((17.75, 8), (-1.75, 8), (8, 17.75), (8, -1.75)), ("red", "green", "blue", "yellow")):
    lr.cbox(x, z, 2.5, 2.5, 8, LAB_RING_Y[0], tex, split=False)
save_block("lab_rotor", lr)
# the core lit from inside, a hair larger than the column (drawn at full brightness while researching)
lcg = Model({"glow": "lab_core_glow"}, "lab_core_glow")
lcg.prism16_y(8, 8, 4.15, 7.05, 17.15, "glow")
save_block("lab_core_glow", lcg)


def lab_dome(glass):
    """The glass dome: see-through glass with an opaque lattice, drawn by LabRenderer in a translucent buffer."""
    m = Model({"glass": glass, "cap": "steel_panel"}, glass)
    m.dome(8, 8, LAB_R, LAB_DOME_Y, 8, "glass")
    m.prism16_y(8, 8, 3, LAB_DOME_Y + LAB_R, LAB_DOME_Y + LAB_R + 0.75, "cap")
    return m


save_block("lab_dome", lab_dome("lab_dome_glow"))
idle_dome = lab_dome("lab_dome_glass")
save_block("lab_dome_idle", idle_dome)
empty("lab", "lab_dome_glass")
item_from("lab", lb, lr, idle_dome, render_type="minecraft:translucent")

# =====================================================================================
# Boiler (3x2, 2 high: x -16..32, z -8..24; steam out north at x 0..16, water ports west/east of the
# near row z 8..24, fire south). Factorio: a bronze barrel lying front to back with a fire window in
# its front, a tall smokestack at the back left and blue pipe ends left and right.
# =====================================================================================
BOIL_Y = 15     # drum axis height
bb = Model({"frame": "dark_panel", "drum": "boiler_bronze", "band": "dark_panel", "dark": "dark", "steel": "steel",
            "brass": "brass", "copper": "copper", "mouth": "furnace_mouth", "port": "fluid_port",
            "pipe_h": "pipe_h", "pipe_v": "pipe_v", "pipe_end": "pipe_end", "flange": "flange",
            "grille": "boiler_grille"}, "boiler_bronze")
bb.box([-14, 0, -6], [30, 3, 22], "frame")
# the drum along z with dark bands and a dark front cover
bb.prism16_z(8, BOIL_Y, 11, -5, 18, "drum")
for z0, z1 in ((-5.5, -3.5), (6, 8)):
    bb.prism16_z(8, BOIL_Y, 11.5, z0, z1, "band")
bb.prism16_z(8, BOIL_Y, 10.5, 18, 19.5, "band")
# fire window in the front cover (boiler_fire glows in front of it)
bb.box([2, 9, 19.5], [14, 16, 20], "mouth", only=("south", "up", "east", "west"), fit=("south",), split=False)
# saddles and the firebox under the front
for z0 in (-2, 12):
    bb.box([-1, 3, z0], [17, 6, z0 + 4], "band", split=False)
bb.box([0, 3, 18], [16, 7, 22], {f: ("grille" if f == "south" else "dark") for f in FACES}, fit=("south",),
       split=False)
# copper pipes up the front cover either side of the fire window (the reference's copper runs)
for x in (0, 16):
    bb.box([x - 0.75, 6, 19.75], [x + 0.75, 23, 21.25], "copper", split=False)
    bb.box([x - 0.75, 23, 16], [x + 0.75, 24.5, 21.25], "copper", split=False)
# the safety valve on top, behind the manhole (steam puffs from it while the boiler works)
bb.prism16_y(8, 12, 1.5, BOIL_Y + 10.5, BOIL_Y + 13.5, "brass")
bb.prism16_y(8, 12, 2.25, BOIL_Y + 13.5, BOIL_Y + 14.5, "steel")
# manhole on top
bb.prism16_y(8, 4, 4, BOIL_Y + 10.5, BOIL_Y + 12.5, "band")
# smokestack at the back left, piped to the drum
bb.box([-14, 3, -5], [-6, 6, 3], "band", split=False)
bb.octagon_y(-10, -1, 5, 6, 31, "dark")
bb.octagon_y(-10, -1, 6.5, 28, 30.5, "steel")
bb.octagon_y(-10, -1, 6.5, 12, 13.5, "steel")
bb.pipe_x(BOIL_Y + 3, -1, -7.5, -2.5, size=4)
# water: a pipe across the near row with blue ports on both ends; steam: a blue port at the back
bb.pipe_x(8, 16, -13, -2.5, skip=("west",))
bb.pipe_x(8, 16, 18.5, 29, skip=("east",))
bb.box([-16, 3, 11], [-13, 13, 21], "port", fit=FACES, split=False)
bb.box([29, 3, 11], [32, 13, 21], "port", fit=FACES, split=False)
bb.pipe_z(8, 12, -7, -4.5, skip=("north",))
bb.box([3, 7, -8], [13, 17, -7], {f: ("port" if f == "north" else "band") for f in FACES}, fit=("north",), split=False)
# brass pipes along the drum's sides
for x in (-4.5, 20.5):
    bb.pipe_z(x, 10, -3, 16, size=2.5)
save_block("boiler_body", bb)

bf = Model({"fire": "furnace_fire"}, "furnace_fire")
bf.box([2.5, 9.5, 20], [13.5, 15.5, 20.1], "fire", only=("south",), fit=("south",), split=False, shade=False)
save_block("boiler_fire", bf)
empty("boiler", "boiler_bronze")
item_from("boiler", bb)

# =====================================================================================
# Steam engine (3x5, 2 high; z along the 5-block length, footprint center at x 8, z 8, so the machine
# spans z -32..48 in the center frame). Factorio: three upright round tanks in a row along the length,
# each with a domed top and a valve, wrapped in pipes on a dark frame, a cluster of gears on one long
# side (east) and blue fluid connections at both ends. Parts (SteamMachineRenderer.Engine):
#   body:   middle tank, frame z -8..24, the gear mounting plate (drawn at dz 0);
#   front:  front tank, frame z -28..-8, front fluid port (drawn at dz -1.5 blocks: model z = z + 24);
#   rear:   the front's mirror image (drawn at dz +1.5: model z = z - 24);
#   gear / gear_small: gears turning about x, authored around (y 8, z 8) and drawn at their pivots
#           (SteamMachineRenderer.Engine.GEARS); the small ones turn the other way.
# =====================================================================================
ENG_TANK_X, ENG_TANK_A = 6, 11     # tank axis x and apothem
ENG_TANK_Y = (4, 25)               # tank wall bottom/top
ENG_PLATE_X = 26                   # east face of the gear mounting plate
ENG_GEARS = ((None, 15, 10), ("small", 12, -6), ("small", 21, 25))   # (model suffix, pivot y, pivot z)
# crank train (SteamMachineRenderer.Engine): a crank pin ENG_CRANK_R px off the big gear's axis drives a
# connecting rod ENG_ROD_L px long up to a crosshead sliding upright in its guide over the gear, outside the
# gears (x ENG_ROD_X); the flywheel turns at the front end; the three valve stems bob a third of a turn apart
ENG_CRANK_R, ENG_ROD_L, ENG_ROD_X = 5, 10, (30.5, 31.5)
ENG_FLYWHEEL = (14, -23, 9)        # pivot y, z, radius
ENG_TEX = {"frame": "dark_panel", "tank": "engine_tank", "band": "dark_panel", "cap": "steel_panel", "valve": "brass",
           "wheel": "tier_red", "port": "fluid_port", "copper": "copper", "dark": "dark",
           "pipe_h": "pipe_h", "pipe_v": "pipe_v", "pipe_end": "pipe_end", "flange": "flange"}


ENG_YOKE_Y = (ENG_TANK_Y[1] + 3, ENG_TANK_Y[1] + 4.5)   # valve yoke posts (bottom, top); crossbar on top


def engine_tank(m, cz):
    """An upright tank on the frame: wall, two dark hoops, domed top, the valve yoke the stem works through
    (the stem is steam_engine_valve)."""
    y0, y1 = ENG_TANK_Y
    m.prism16_y(ENG_TANK_X, cz, ENG_TANK_A, y0, y1, "tank")
    for yb in (8, 19):
        m.prism16_y(ENG_TANK_X, cz, ENG_TANK_A + 0.5, yb, yb + 1.5, "band")
    m.prism16_y(ENG_TANK_X, cz, ENG_TANK_A - 1.5, y1, y1 + 1.5, "tank")
    m.prism16_y(ENG_TANK_X, cz, ENG_TANK_A - 4, y1 + 1.5, y1 + 3, "cap")
    ya, yb = ENG_YOKE_Y
    for dx in (-2.25, 2.25):
        m.cbox(ENG_TANK_X + dx, cz, 1, 1.5, ya, yb, "valve", split=False, skip=("down",))
    # the crossbar in two halves, leaving the stem's slot between them
    m.box([ENG_TANK_X - 2.75, yb, cz - 0.75], [ENG_TANK_X - 0.6, yb + 0.75, cz + 0.75], "valve", split=False)
    m.box([ENG_TANK_X + 0.6, yb, cz - 0.75], [ENG_TANK_X + 2.75, yb + 0.75, cz + 0.75], "valve", split=False)


def engine_pipes(m, z0, z1, skip):
    """Pipe runs along the engine's length (west low and high, east high), z0..z1 in model z."""
    m.pipe_z(-8, 9, z0, z1, size=4, skip=skip)
    m.pipe_z(-7, 19, z0, z1, size=3, skip=skip)
    m.pipe_z(19, 21, z0, z1, size=3, skip=skip)
    # the manifold over the gear plate
    for y in (17, 22):
        m.pipe_z(24, y, z0, z1, size=3, skip=skip)


eng = Model(ENG_TEX, "engine_tank")
eng.box([-14, 0, -8], [30, 4, 24], "frame", skip=("north", "south"))
engine_tank(eng, 8)
engine_pipes(eng, -8, 24, ("north", "south"))
# gear mounting plate on the east side with a crank housing
eng.box([22, 4, -8], [ENG_PLATE_X, 14, 24], "band")
eng.box([19, 4, 4], [22, 10, 20], "dark", split=False)
# risers from the gear plate up to the manifold, with valves
for z in (-3, 19):
    eng.pipe_y(24, z, 14, 23, size=2.5)
    eng.cbox(24, z, 4, 4, 19, 20, "valve", split=False)
# the crosshead's guide over the big gear: two upright bars outside the gears and a bracket back to the plate
gz = ENG_GEARS[0][2]
for z0, z1 in ((gz - 2.5, gz - 1.75), (gz + 1.75, gz + 2.5)):
    eng.box([ENG_ROD_X[1] + 0.1, 17, z0], [32, 31.5, z1], "frame", split=False)
eng.box([ENG_PLATE_X, 31.5, gz - 2.5], [32, 32, gz + 2.5], "frame", split=False)
save_block("steam_engine_body", eng)


def engine_end(rear):
    """Front end (model z = center z + 24) or its mirror, the rear end (model z = center z - 24)."""
    m = Model(ENG_TEX, "engine_tank")

    def Z(a, b):
        return (16 - b, 16 - a) if rear else (a, b)

    def box(f, t, tex, **kw):
        z0, z1 = Z(f[2], t[2])
        m.box([f[0], f[1], z0], [t[0], t[1], z1], tex, **kw)

    inner, outer = ("north", "south") if rear else ("south", "north")
    box([-14, 0, -4], [30, 4, 16], "frame", skip=(inner,))
    engine_tank(m, 8)
    z0, z1 = Z(-3, 16)
    engine_pipes(m, z0, z1, (inner,))
    # the fluid connection: a pipe out of the end at (x 8, y 8) with a blue port
    z0, z1 = Z(-7, 0)
    m.pipe_z(8, 8, z0, z1, size=6, skip=(outer,))
    box([3, 3, -8], [13, 13, -7], {f: ("port" if f == outer else "band") for f in FACES}, fit=(outer,), split=False)
    # end of the gear plate
    box([22, 4, -3], [ENG_PLATE_X, 10, 16], "band", skip=(inner,))
    return m


eng_front, eng_rear = engine_end(False), engine_end(True)
save_block("steam_engine_front", eng_front)
save_block("steam_engine_rear", eng_rear)


def engine_gear(r, x0, x1):
    m = Model({"disc": "steel_panel", "teeth": "dark", "hub": "brass", "pin": "piston_rod"}, "steel_panel")
    m.gear_x(8, 8, r, x0, x1, {"disc": "disc", "teeth": "teeth", "hub": "hub"})
    return m


# the small gears are thinner, so meshing teeth never share a plane with the big gear's
eng_gear = engine_gear(9, ENG_PLATE_X + 0.5, ENG_PLATE_X + 3.5)
# the crank pin on the big gear's face (turns with it), reaching out to the connecting rod
eng_gear.box([ENG_PLATE_X + 4, 8 + ENG_CRANK_R - 0.75, 7.25], [ENG_ROD_X[0], 8 + ENG_CRANK_R + 0.75, 8.75], "pin",
             split=False)
eng_gear_small = engine_gear(5.5, ENG_PLATE_X + 1, ENG_PLATE_X + 3)
save_block("steam_engine_gear", eng_gear)
save_block("steam_engine_gear_small", eng_gear_small)

# connecting rod, authored at rest (crank pin at the top of its circle): from the pin straight up to the crosshead
_pin_y = ENG_GEARS[0][1] + ENG_CRANK_R
eng_rod = Model({"rod": "piston_rod", "end": "dark_panel"}, "piston_rod")
eng_rod.box([ENG_ROD_X[0], _pin_y - 0.75, gz - 0.75], [ENG_ROD_X[1], _pin_y + ENG_ROD_L, gz + 0.75], "rod", split=False)
save_block("steam_engine_rod", eng_rod)
# the crosshead (slides upright in the guide; its middle is the rod's top end)
eng_cross = Model({"end": "dark_panel", "brass": "brass"}, "dark_panel")
eng_cross.box([ENG_ROD_X[0] - 0.1, _pin_y + ENG_ROD_L - 1, gz - 1.75],
              [ENG_ROD_X[1] + 0.1, _pin_y + ENG_ROD_L + 1, gz + 1.75],
              {"north": "end", "south": "end", "east": "brass", "west": "end", "up": "end", "down": "end"}, split=False)
save_block("steam_engine_crosshead", eng_cross)
# the flywheel at the front end, outside the gear plate: a ribbed rim on eight spokes round a brass hub,
# authored about (y 8, z 8) like the gears
eng_fly = Model({"rim": "engine_flywheel", "spoke": "dark_panel", "hub": "brass"}, "engine_flywheel")
_fx0, _fx1 = ENG_PLATE_X + 0.75, ENG_PLATE_X + 3
eng_fly.ring_x(8, 8, ENG_FLYWHEEL[2], 2, _fx0, _fx1, "rim")
eng_fly.spokes_x(8, 8, ENG_FLYWHEEL[2] - 1, 1.5, _fx0 + 0.3, _fx1 - 0.3, "spoke")
eng_fly.prism16_x(8, 8, 2, ENG_PLATE_X, _fx1 + 0.5, "hub")
save_block("steam_engine_flywheel", eng_fly)
# a valve stem with its knob, working up and down through the yoke on a tank (authored on the middle tank)
eng_valve = Model({"rod": "piston_rod", "knob": "brass"}, "piston_rod")
eng_valve.cbox(ENG_TANK_X, 8, 0.8, 0.8, ENG_TANK_Y[1] + 3, ENG_YOKE_Y[1] + 1.25, "rod", split=False, skip=("down",))
eng_valve.cbox(ENG_TANK_X, 8, 2, 2, ENG_YOKE_Y[1] + 1.25, ENG_YOKE_Y[1] + 2.25, "knob", split=False)
save_block("steam_engine_valve", eng_valve)
empty("steam_engine", "engine_tank")
# item: the whole engine as drawn, scaled to 0.55
item_scaled("steam_engine", [(eng, 0, 0, 0), (eng_front, 0, 0, -24), (eng_rear, 0, 0, 24)]
            + [(eng_gear if s is None else eng_gear_small, 0, py - 8, pz - 8)
               for s, py, pz in ENG_GEARS]
            + [(eng_rod, 0, 0, 0), (eng_cross, 0, 0, 0), (eng_fly, 0, ENG_FLYWHEEL[0] - 8, ENG_FLYWHEEL[1] - 8)]
            + [(eng_valve, 0, 0, dz) for dz in (-24, 0, 24)], 0.55)

# =====================================================================================
# Offshore pump (1x2 on the controller's blockstate: x 0..16, z -16..16; water beyond z -16; outlet on
# the south face of the controller at y 8). Factorio 1.1 (sprite and its 32-frame animation at 1/4 speed):
# a bronze housing on a frame with a bullet nose toward the land, two glass tubes of water lying on top,
# two ribbed elbows arching over into the water; while it pumps, water runs through the tubes and the
# little gears on its side turn. The still tubes are in the blockstate model; OffshorePumpRenderer draws
# the gears (offshore_pump_gear / _gear_b, turning about x) and, while pumping, the flowing water over
# the tubes (offshore_pump_flow). Those are authored in the center frame (controller + (0, 0, 8)).
# =====================================================================================
OP_TUBES = (5, 11)                 # tube axes x; they lie along z at y OP_TUBE_Y
OP_TUBE_Y, OP_TUBE_A, OP_TUBE_Z = 10.5, 2, (-6, 5)
OP_GEARS = ((6, -4), (6, 3))        # gear pivots (y, z), controller frame; the second meshes with the first
OP_GEAR_X = 13.5                    # housing's east face
OP_TO_CENTER = (0, 0, 8)
op = Model({"frame": "dark_panel", "body": "boiler_bronze", "dark": "dark_panel", "band": "tier_blue",
            "port": "fluid_port", "elbow": "pump_ribs", "glass": "pump_glass", "vent": "vent", "pipe_h": "pipe_h",
            "pipe_v": "pipe_v", "pipe_end": "pipe_end", "flange": "flange"}, "boiler_bronze")
op.box([1, 0, -12], [15, 1.5, 13], "frame")
# the housing, with vents on the sides and a dark band round its top
op.box([2.5, 1.5, -9], [OP_GEAR_X, 7, 6], {"north": "body", "south": "body", "east": "vent", "west": "vent", "up": "dark",
                                         "down": "dark"}, fit=("east", "west"), split=False)
op.box([2, 6.5, -9.5], [OP_GEAR_X + 0.25, 8, 6.5], "dark", split=False, skip=("down",))
# the two glass tubes on top on their saddles, dark collars at the ends
for x in OP_TUBES:
    op.prism16_z(x, OP_TUBE_Y, OP_TUBE_A, *OP_TUBE_Z, "glass")
    for z0, z1 in ((OP_TUBE_Z[0] - 1, OP_TUBE_Z[0]), (OP_TUBE_Z[1], OP_TUBE_Z[1] + 1)):
        op.prism16_z(x, OP_TUBE_Y, OP_TUBE_A + 0.6, z0, z1, "dark")
    for z in (-4, 3):
        op.cbox(x, z, 3, 1.5, 8, OP_TUBE_Y - 1, "frame", split=False, skip=("down",))
# the bullet nose toward the land end, the outlet with its blue port
for a_, z0, z1, tex in ((5.5, 6, 9, "body"), (4.75, 9, 11.5, "body"), (3.75, 11.5, 13, "body"), (3, 13, 14, "dark")):
    op.prism16_z(8, 5, a_, z0, z1, tex)
op.pipe_z(8, 8, 14, 15, size=6)
op.box([3, 3, 15], [13, 13, 16], {f: ("port" if f == "south" else "dark") for f in FACES}, fit=("south",), split=False)
# the ribbed elbows: up out of each tube's water end, over and down into the water, with blue rings
for x in OP_TUBES:
    op.prism16_y(x, -8.5, 2.2, OP_TUBE_Y, 14.75, "elbow")
    op.prism16_z(x, 12.5, 1.9, -13.25, -8.5, "elbow")
    op.prism16_y(x, -13.25, 2.2, -6, 14.75, "elbow")
    for y0 in (-1, 6):
        op.prism16_y(x, -13.25, 2.6, y0, y0 + 1.2, "band")
save_block("offshore_pump", op)

# water running through the tubes (drawn over the still tubes while pumping)
opf = Model({"flow": "pump_flow"}, "pump_flow")
for x in OP_TUBES:
    opf.prism16_z(x, OP_TUBE_Y, OP_TUBE_A + 0.1, OP_TUBE_Z[0] + 0.05, OP_TUBE_Z[1] - 0.05, "flow")
opf.elements = opf.shifted(*OP_TO_CENTER)
save_block("offshore_pump_flow", opf)


def pump_gear(b):
    """A little cog on the housing's east side, about (y 8, z 8) like the steam engine's gears: a 16-sided disc,
    8 teeth (4 bars through the axis, each EPS thinner) and a hub. b: the meshing one, authored half a tooth
    (22.5 degrees) turned and a hair thinner, so the two never share a plane."""
    m = Model({"disc": "steel_panel", "teeth": "dark", "hub": "brass"}, "steel_panel")
    x0, x1 = (OP_GEAR_X + 0.35, OP_GEAR_X + 1.15) if b else (OP_GEAR_X + 0.25, OP_GEAR_X + 1.25)
    m.prism16_x(8, 8, 2.5, x0, x1, "disc")
    bars = ((True, 22.5), (True, -22.5), (False, 22.5), (False, -22.5)) if b else \
        ((True, 0), (False, 0), (True, 45), (True, -45))
    for i, (long_y, ang) in enumerate(bars):
        kw = {"rot": ("x", ang, [x0, 8, 8])} if ang else {}
        e = 0.17 + EPS * i
        if long_y:
            m.box([x0 + e, 8 - 4, 8 - 0.6], [x1 - e, 8 + 4, 8 + 0.6], "teeth", split=False, **kw)
        else:
            m.box([x0 + e, 8 - 0.6, 8 - 4], [x1 - e, 8 + 0.6, 8 + 4], "teeth", split=False, **kw)
    m.prism16_x(8, 8, 1, x0 - 0.4, x1 + 0.4, "hub")
    return m


op_gears = (pump_gear(False), pump_gear(True))
save_block("offshore_pump_gear", op_gears[0])
save_block("offshore_pump_gear_b", op_gears[1])
item_from("offshore_pump", op, *op_gears, offsets=[(0, 0, 0)] + [(0, y - 8, z - 8) for y, z in OP_GEARS])

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
# Small electric pole (three blocks tall: part 0 (lower) holds the block entity, parts 1 and 2 are the
# middle and the top). Factorio: a thin wooden pole with a small crossbar and insulators. Wires attach at
# the center insulator's top, top-part y 15 (WireRenderer.ATTACH_Y = 47 / 16 over the lower block).
# =====================================================================================
POLE_TEX = {"wood": "pole_wood", "top": "pole_wood_top", "ins": "insulator", "foot": "dark_panel", "copper": "copper",
            "green": "tier_green"}
POST = {"north": "wood", "south": "wood", "east": "wood", "west": "wood", "up": "top", "down": "top"}
sp = Model(POLE_TEX, "pole_wood")
sp.parent = "minecraft:block/block"
sp.box([5, 0, 5], [11, 1.5, 11], "foot", split=False)
sp.box([6.5, 1.5, 6.5], [9.5, 16, 9.5], POST, skip=("up",), split=False)
save_block("small_electric_pole", sp)
spm = Model(POLE_TEX, "pole_wood")
spm.parent = "minecraft:block/block"
spm.box([6.5, 0, 6.5], [9.5, 16, 9.5], POST, skip=("up", "down"), split=False)
save_block("small_electric_pole_middle", spm)
spt = Model(POLE_TEX, "pole_wood")
spt.parent = "minecraft:block/block"
spt.box([6.5, 0, 6.5], [9.5, 13, 9.5], POST, skip=("down",), split=False)
spt.box([1.5, 10, 7], [14.5, 12, 9], {f: "wood" for f in FACES}, uvrot={"north": 90, "south": 90, "up": 90, "down": 90},
        split=False)
for x, tex in ((2.5, "ins"), (13.5, "green")):
    spt.cbox(x, 8, 2, 2, 12, 14.5, tex, split=False)
    spt.cbox(x, 8, 1, 1, 14.5, 15.25, "copper", split=False)
spt.cbox(8, 8, 2.5, 2.5, 13, 15, "ins", split=False)
spt.cbox(8, 8, 1.5, 1.5, 15, 15.75, "copper", split=False)
save_block("small_electric_pole_top", spt)
# item: the whole pole, 15.75 px down so it fits -16..32
item_from("small_electric_pole", sp, spm, spt, offsets=[(0, -15.75, 0), (0, 0.25, 0), (0, 16.25, 0)], particle="pole_wood")

# =====================================================================================
# Inserters: a round low base with a pivot. The arm - Factorio's short riser and long thin arm with a
# claw, tier coloured - is drawn by InserterRenderer from the pivot top at y 5.5. Tier colours after
# Factorio 1.1: burner grey, inserter yellow, long-handed red, fast blue, filter purple, stack green
# (the stack inserter with a wider hand).
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
INSERTER_TIERS = (("burner_inserter", "dark_panel", "steel"), ("inserter", "tier_yellow_panel", "tier_yellow"),
                  ("long_handed_inserter", "tier_red_panel", "tier_red"), ("fast_inserter", "tier_blue_panel", "tier_blue"),
                  ("filter_inserter", "tier_purple_panel", "tier_purple"), ("stack_inserter", "tier_green_panel", "tier_green"))
for name, housing, arm in INSERTER_TIERS:
    save_block(name, Model({"housing": housing}, None, parent="minetorio:block/template_inserter"))
    # item: base + the riser and the long arm reaching over the pickup side
    fore = 13 if name.startswith("long") else 10
    im = Model({"plate": "dark_panel", "steel": "steel", "dark": "dark", "housing": housing, "arm": arm,
                "joint": "dark_panel"}, housing)
    im.elements = json.loads(json.dumps(ti.elements))
    im.cbox(8, 8, 3, 3, 5.5, 7.5, "joint", split=False)
    ey, ez = 13.5, 8
    im.box([7.1, 7.5, 7.1], [8.9, ey, 8.9], "arm", split=False)
    im.cbox(8, ez, 2.6, 2.6, ey - 1, ey + 1.5, "joint", split=False)
    im.box([7.4, ey - 0.6, ez - fore], [8.6, ey + 0.6, ez - 1.3], "arm", split=False, rot=("x", -22.5, [8, ey, ez]))
    hy = ey - fore * math.sin(math.radians(22.5))
    hz = ez - fore * math.cos(math.radians(22.5))
    # the stack inserter's hand is wider, with four fingers (Factorio's big green claw)
    stack = name.startswith("stack")
    im.cbox(8, hz, 4.4 if stack else 2.6, 2.6, hy - 2, hy + 0.5, "joint", split=False)
    for x0 in ((5.9, 7.2, 8.3, 9.6) if stack else (6.4, 9.1)):
        im.box([x0, hy - 4, hz - 0.5], [x0 + 0.5, hy - 2, hz + 0.5], "steel", split=False)
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

# flat icons for materials
for n in ("iron_ore", "copper_ore", "iron_plate", "copper_plate", "stone_brick", "iron_gear_wheel", "copper_cable",
          "electronic_circuit", "automation_science_pack", "logistic_science_pack"):
    item_flat(n)

print(len(written), "models")
