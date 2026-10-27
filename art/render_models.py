"""Software renderer for Minetorio's JSON models: an isometric contact sheet for eyeballing shapes.

    python art/render_models.py            -> art/models-preview.png

Resolves parents and texture variables, applies element rotations, z-buffers every face with its
texture (first animation frame) and vanilla-like directional shading. Two views per model: from the
front-left (north-west, the side a machine faces) and from the back-right.
"""
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets")
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}
NORMAL = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0),
          "west": (-1, 0, 0)}
_tex_cache = {}


def load_json(rl):
    ns, path = rl.split(":") if ":" in rl else ("minecraft", rl)
    p = os.path.join(ASSETS, ns, "models", path + ".json")
    if not os.path.exists(p):
        return None
    with open(p) as f:
        return json.load(f)


def resolve(rl):
    """(elements, textures) of a model with its parent chain."""
    chain = []
    while rl:
        j = load_json(rl)
        if j is None:
            break
        chain.append(j)
        rl = j.get("parent")
    tex, els = {}, None
    for j in reversed(chain):
        tex.update(j.get("textures", {}))
        if "elements" in j:
            els = j["elements"]
    return els or [], tex


def texture(ref, tex):
    seen = 0
    while ref.startswith("#") and seen < 10:
        ref = tex.get(ref[1:], "missing")
        seen += 1
    if ref in _tex_cache:
        return _tex_cache[ref]
    ns, path = ref.split(":") if ":" in ref else ("minecraft", ref)
    p = os.path.join(ASSETS, ns, "textures", path + ".png")
    if os.path.exists(p):
        im = Image.open(p).convert("RGBA")
        im = im.crop((0, 0, im.width, im.width))
        arr = np.asarray(im.resize((16, 16), Image.NEAREST), dtype=np.float32)
    elif "black" in ref:
        arr = np.zeros((16, 16, 4), np.float32); arr[..., 3] = 255; arr[..., :3] = 20
    else:  # missing texture: magenta/black like the game
        arr = np.zeros((16, 16, 4), np.float32); arr[..., 3] = 255
        arr[:8, :8, 0] = arr[:8, :8, 2] = 255; arr[8:, 8:, 0] = arr[8:, 8:, 2] = 255
        print("  missing texture", ref)
    _tex_cache[ref] = arr
    return arr


def rot_matrix(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def face_frame(face, f, t):
    """Corner where (a, b) = (0, 0) and the a/b edge vectors, matching vanilla's default UV layout."""
    fx, fy, fz = f
    tx, ty, tz = t
    if face == "up":
        return np.array([fx, ty, fz]), np.array([tx - fx, 0, 0]), np.array([0, 0, tz - fz])
    if face == "down":
        return np.array([fx, fy, tz]), np.array([tx - fx, 0, 0]), np.array([0, 0, fz - tz])
    if face == "north":
        return np.array([tx, ty, fz]), np.array([fx - tx, 0, 0]), np.array([0, fy - ty, 0])
    if face == "south":
        return np.array([fx, ty, tz]), np.array([tx - fx, 0, 0]), np.array([0, fy - ty, 0])
    if face == "west":
        return np.array([fx, ty, fz]), np.array([0, 0, tz - fz]), np.array([0, fy - ty, 0])
    return np.array([tx, ty, tz]), np.array([0, 0, fz - tz]), np.array([0, fy - ty, 0])  # east


def render(elements, tex, yaw, pitch=-30, size=260, extent=None):
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    view = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, -sy], [0, 1, 0], [sy, 0, cy]])
    lo, hi = extent if extent else (np.array([-16, -16, -16.]), np.array([32, 32, 32.]))
    center = (lo + hi) / 2
    radius = np.linalg.norm(hi - lo) / 2
    scale = size / (2 * radius * 1.02)
    img = np.zeros((size, size, 3), np.float32) + np.array([62, 64, 72], np.float32)
    zb = np.full((size, size), np.inf, np.float32)

    def project(p):
        q = view @ (p - center)
        return np.array([size / 2 + q[0] * scale, size / 2 - q[1] * scale]), q[2]

    for el in elements:
        f, t = np.array(el["from"], float), np.array(el["to"], float)
        rot = el.get("rotation")
        R = None
        if rot and rot.get("angle"):
            R = rot_matrix(rot["axis"], rot["angle"])
            origin = np.array(rot["origin"], float)
        for face, fd in el.get("faces", {}).items():
            n = np.array(NORMAL[face], float)
            o, ea, eb = face_frame(face, f, t)
            if R is not None:
                o = R @ (o - origin) + origin
                ea, eb, n = R @ ea, R @ eb, R @ n
            if (view @ n)[2] > 0:  # facing away from the camera (camera looks toward +depth)
                continue
            arr = texture(fd.get("texture", "#missing"), tex)
            u0, v0, u1, v1 = fd.get("uv", [0, 0, 16, 16])
            r = fd.get("rotation", 0)
            s0, d0 = project(o)
            sa, da = project(o + ea)
            sb, db = project(o + eb)
            A = np.array([sa - s0, sb - s0]).T
            if abs(np.linalg.det(A)) < 1e-6:
                continue
            Ai = np.linalg.inv(A)
            pts = [s0, sa, sb, sa + sb - s0]
            x0 = max(int(min(p[0] for p in pts)), 0); x1 = min(int(max(p[0] for p in pts)) + 1, size)
            y0 = max(int(min(p[1] for p in pts)), 0); y1 = min(int(max(p[1] for p in pts)) + 1, size)
            if x0 >= x1 or y0 >= y1:
                continue
            xs, ys = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
            rel = np.stack([xs - s0[0], ys - s0[1]], -1) @ Ai.T
            a, b = rel[..., 0], rel[..., 1]
            inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
            depth = d0 + a * (da - d0) + b * (db - d0)
            for _ in range(r // 90):
                a, b = b, 1 - a
            u = u0 + (u1 - u0) * a
            v = v0 + (v1 - v0) * b
            ui = np.clip(np.floor(u).astype(int), 0, 15)
            vi = np.clip(np.floor(v).astype(int), 0, 15)
            col = arr[vi, ui]
            mask = inside & (col[..., 3] > 0) & (depth < zb[y0:y1, x0:x1])
            sh = 1.0 if el.get("shade") is False else SHADE[face] if R is None else 0.85
            zb[y0:y1, x0:x1][mask] = depth[mask]
            img[y0:y1, x0:x1][mask] = col[..., :3][mask] * sh
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def placed(rl, dx=0, dy=0, dz=0, turn=False):
    """Elements of a model with absolute texture refs, shifted and then optionally turned 180 degrees about
    the footprint center (x = z = 8), as a block entity renderer draws a part."""
    els, tex = resolve(rl)
    out = []
    for el in els:
        e = json.loads(json.dumps(el))
        f = [e["from"][0] + dx, e["from"][1] + dy, e["from"][2] + dz]
        t = [e["to"][0] + dx, e["to"][1] + dy, e["to"][2] + dz]
        if e.get("rotation"):
            o = e["rotation"]["origin"]
            e["rotation"]["origin"] = [o[0] + dx, o[1] + dy, o[2] + dz]
        if turn:
            f, t = [16 - t[0], f[1], 16 - t[2]], [16 - f[0], t[1], 16 - f[2]]
            r = e.get("rotation")
            if r:
                o = r["origin"]
                r["origin"] = [16 - o[0], o[1], 16 - o[2]]
                if r["axis"] != "y":
                    r["angle"] = -r["angle"]
        e["from"], e["to"] = f, t
        for fd in e.get("faces", {}).values():
            ref = fd.get("texture", "#missing")
            seen = 0
            while ref.startswith("#") and seen < 10:
                ref = tex.get(ref[1:], "missing")
                seen += 1
            fd["texture"] = ref
        if turn:
            fs = e.get("faces", {})
            swap = {"north": "south", "south": "north", "east": "west", "west": "east"}
            e["faces"] = {swap.get(k, k): v for k, v in fs.items()}
        out.append(e)
    return out


# whole machines as their block entity renderers draw them (in-world size, not the item)
COMPOSITE_ENTRIES = [
    ("steam_engine (in world, 3x5)", lambda: placed("minetorio:block/steam_engine_body", dz=-16)
     + placed("minetorio:block/steam_engine_body", dz=-16, turn=True)
     + placed("minetorio:block/steam_engine_center", dz=-8) + placed("minetorio:block/steam_engine_piston", dz=-8)
     + placed("minetorio:block/steam_engine_flywheel", dz=24)),
    ("electric_mining_drill (in world)", lambda: placed("minetorio:block/electric_mining_drill_body")
     + placed("minetorio:block/electric_mining_drill_head") + placed("minetorio:block/electric_mining_drill_tower", dy=16)),
    ("burner_mining_drill (in world)", lambda: placed("minetorio:block/burner_mining_drill")
     + placed("minetorio:block/burner_mining_drill_head", 8, 0, -8)
     + placed("minetorio:block/burner_mining_drill_chimney", 8, 16, -8)),
]


def bounds(elements):
    lo = np.array([min(e["from"][i] for e in elements) for i in range(3)], float)
    hi = np.array([max(e["to"][i] for e in elements) for i in range(3)], float)
    return lo - 1, hi + 1


ENTRIES = [
    ("minetorio:item/stone_furnace", "stone_furnace"),
    ("minetorio:block/stone_furnace_lit", "stone_furnace (lit)"),
    ("minetorio:item/burner_mining_drill", "burner_mining_drill"),
    ("minetorio:item/electric_mining_drill", "electric_mining_drill"),
    ("minetorio:item/assembling_machine_1", "assembling_machine_1"),
    ("minetorio:item/assembling_machine_2", "assembling_machine_2"),
    ("minetorio:block/assembling_machine_1_gear", "assembling machine gear (spins)"),
    ("minetorio:item/lab", "lab (idle)"),
    ("minetorio:block/lab_dome", "lab dome (researching)"),
    ("minetorio:item/boiler", "boiler"),
    ("minetorio:item/steam_engine", "steam_engine (item: shortened)"),
    ("minetorio:item/offshore_pump", "offshore_pump"),
    ("minetorio:item/pipe", "pipe"),
    ("minetorio:block/small_electric_pole", "small_electric_pole"),
    ("minetorio:item/burner_inserter", "burner_inserter (item pose)"),
    ("minetorio:item/inserter", "inserter (item pose)"),
    ("minetorio:item/long_handed_inserter", "long_handed_inserter"),
    ("minetorio:item/fast_inserter", "fast_inserter"),
    ("minetorio:block/transport_belt", "transport_belt"),
    ("minetorio:block/fast_transport_belt_curve_left", "fast belt curve_left"),
    ("minetorio:block/express_transport_belt_curve_right", "express belt curve_right"),
    ("minetorio:block/underground_belt_input", "underground_belt input"),
    ("minetorio:block/fast_underground_belt_output", "fast_underground output"),
    ("minetorio:block/splitter", "splitter"),
    ("minetorio:block/express_splitter", "express_splitter"),
]

if __name__ == "__main__":
    S = 260
    cols = 4
    font = ImageFont.load_default()
    entries = [(lambda rl=rl: resolve(rl), label) for rl, label in ENTRIES] +               [(lambda f=f: (f(), {}), label) for label, f in COMPOSITE_ENTRIES]
    rows = (len(entries) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (2 * S + 12), rows * (S + 20)), (40, 42, 48))
    d = ImageDraw.Draw(sheet)
    for i, (load, label) in enumerate(entries):
        els, tex = load()
        if not els:
            print("no elements:", label)
            continue
        ext = bounds(els)
        x = (i % cols) * (2 * S + 12)
        y = (i // cols) * (S + 20)
        sheet.paste(render(els, tex, yaw=-135 + 180, size=S, extent=ext), (x, y))  # front-left (north-west)
        sheet.paste(render(els, tex, yaw=45 + 180, size=S, extent=ext), (x + S + 2, y))  # back-right
        d.text((x + 4, y + S + 3), label, fill=(230, 230, 230), font=font)
    out = os.path.join(HERE, "models-preview.png")
    sheet.save(out)
    print("wrote", out)
