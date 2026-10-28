"""Fluid handling: pipe caps, the underground pipe, the storage tank and the pump. Block/item models and
blockstates. Self-contained (reuses existing block textures: steel, dark_panel, flange, pipe_*, window,
tier_blue, boiler_bronze, fluid_port).

    python art/gen_fluid.py

References (Factorio wiki entity images, kept outside the repository):
  Pipe_to_ground_entity.png  a pipe stub with a bolted flange that bends down into the ground through a
                             dark collar; here the stub is the open end (FACING) and the collar and elbow
                             point along the tunnel.
  Storage_tank_entity.png    a squat, rounded-square riveted tank with a ribbed band at the top and the
                             bottom, a little window on the front, a hub on the roof with a pipe to a
                             corner, and pipe connections with valve wheels at two opposite corners.
  Pump_entity_anim.gif       a straight pipe run (flanged joints, blue windows) with the pump block
                             sitting on it: two cylinders side by side with blue windows, bronze bellows
                             at both ends, a dark housing below.

Outputs (src/main/resources/assets/minetorio/):
  models/block/pipe_cap.json           closed pipe side (north), drawn by blockstates/pipe.json per side
  models/block/pipe_to_ground.json     open end north, tunnel south
  models/block/storage_tank.json       controller: particle only (StorageTankRenderer draws the body)
  models/block/storage_tank_body.json  around the footprint's center block, north = forward, y 0..30
  models/block/pump.json               on the controller (back cell), the front cell is z -16..0
  models/item/{pipe_to_ground,storage_tank,pump}.json
  blockstates/{pipe,pipe_to_ground,storage_tank,pump}.json

No two faces may share a plane, face the same way and overlap: python -I art/check_zfight.py.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "minetorio")
MB = os.path.join(ASSETS, "models", "block")
MI = os.path.join(ASSETS, "models", "item")
BS = os.path.join(ASSETS, "blockstates")
NS = "minetorio:block/"
FACES = ("north", "south", "east", "west", "up", "down")


def uv(face, f, t):
    """Vanilla-like UVs from the element's extent on the face's plane, wrapped into 0..16."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    if face in ("north", "south"):
        u0, u1, v0, v1 = x0, x1, 16 - y1, 16 - y0
    elif face in ("east", "west"):
        u0, u1, v0, v1 = z0, z1, 16 - y1, 16 - y0
    else:
        u0, u1, v0, v1 = x0, x1, z0, z1
    du, dv = u1 - u0, v1 - v0
    du, dv = min(du, 16), min(dv, 16)
    u0 = u0 % 16
    v0 = v0 % 16
    if u0 + du > 16:
        u0 = 16 - du
    if v0 + dv > 16:
        v0 = 16 - dv
    return [round(u0, 3), round(v0, 3), round(u0 + du, 3), round(v0 + dv, 3)]


class Model:
    def __init__(self, textures):
        self.textures = dict(textures)
        self.elements = []

    def box(self, f, t, tex, skip=(), rot=None, split=True):
        """A box; boxes longer than 16 px on x or z are cut into equal parts (texel density)."""
        if split:
            for axis in (0, 2):
                span = t[axis] - f[axis]
                if span > 16.0001:
                    n = int(-(-span // 16))
                    step = span / n
                    for i in range(n):
                        a = list(f)
                        b = list(t)
                        a[axis] = f[axis] + step * i
                        b[axis] = f[axis] + step * (i + 1)
                        self.box(a, b, tex, skip, rot, split)
                    return
        faces = {}
        for face in FACES:
            if face in skip:
                continue
            name = tex[face] if isinstance(tex, dict) else tex
            faces[face] = {"uv": uv(face, f, t), "texture": "#" + name}
        e = {"from": [round(v, 4) for v in f], "to": [round(v, 4) for v in t], "faces": faces}
        if rot:
            e["rotation"] = rot
        self.elements.append(e)

    def json(self, parent=None, display=None):
        out = {}
        if parent:
            out["parent"] = parent
        out["ambientocclusion"] = False
        out["textures"] = self.textures
        out["elements"] = self.elements
        if display:
            out["display"] = display
        return out


def write(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


PIPE_TEX = {"pipe_h": NS + "pipe_h", "pipe_v": NS + "pipe_v", "pipe_end": NS + "pipe_end", "flange": NS + "flange",
            "dark": NS + "dark_panel", "particle": NS + "pipe_h"}
PIPE_Z = {"east": "pipe_h", "west": "pipe_h", "up": "pipe_v", "down": "pipe_v", "north": "pipe_end", "south": "pipe_end"}


# ------------------------------------------------------------------------------------------- pipe cap
def pipe_cap():
    """A blind flange bolted onto the core's north face, smaller than the core so caps on two sides never meet."""
    m = Model(PIPE_TEX)
    m.box([5, 5, 2.6], [11, 11, 4], "flange", skip=("south",))
    m.box([7, 7, 2], [9, 9, 2.6], "dark", skip=("south",))
    return m


# ------------------------------------------------------------------------------------- underground pipe
def pipe_to_ground():
    """Open end (flange, like a pipe arm) at the north face, collar and elbow toward the south (the tunnel)."""
    m = Model(PIPE_TEX)
    m.box([4, 4, 0], [12, 12, 1], "flange")
    m.box([4.5, 4.5, 1], [11.5, 11.5, 6], PIPE_Z, skip=("north", "south"))
    m.box([3, 3, 6], [13, 13, 8], "flange")
    m.box([3.5, 3.5, 8], [12.5, 12.5, 13], "dark", skip=("north",))
    m.box([4.5, 4.5, 13], [11.5, 11.5, 15], "flange", skip=("north",))
    # bolts on the collar
    for (x, y) in ((3.3, 3.3), (11.7, 3.3), (3.3, 11.7), (11.7, 11.7)):
        m.box([x - 0.5, y - 0.5, 5.4], [x + 0.5, y + 0.5, 6], "dark", skip=("south",))
    return m


# ----------------------------------------------------------------------------------------- storage tank
TANK_TEX = {"steel": NS + "steel_panel", "dark": NS + "dark_panel", "rim": NS + "steel", "window": NS + "window",
            "flange": NS + "flange", "pipe_h": NS + "pipe_h", "pipe_v": NS + "pipe_v", "pipe_end": NS + "pipe_end",
            "bronze": NS + "boiler_bronze", "particle": NS + "steel_panel"}


def storage_tank_body():
    """3x3x2 tank around the center block (x, z -16..32), north = forward. Connections: the front-right cell
    (x 16..32, z -16..0) north and east, the back-left cell (x -16..0, z 16..32) south and west."""
    m = Model(TANK_TEX)
    m.box([-15, 0, -15], [31, 1.5, 31], "dark")                                 # plinth
    m.box([-13, 1.5, -10], [29, 24, 26], "steel")                               # body, cross of two boxes:
    m.box([-10, 1.55, -13], [26, 23.95, 29], "steel")                           # rounded square
    m.box([-13.5, 2, -10.5], [29.5, 4, 26.5], "rim")                            # bottom rib band
    m.box([-10.5, 2.05, -13.5], [26.5, 3.95, 29.5], "rim")
    m.box([-13.5, 20, -10.5], [29.5, 22.5, 26.5], "rim")                        # top rib band
    m.box([-10.5, 20.05, -13.5], [26.5, 22.45, 29.5], "rim")
    m.box([-11, 24, -8], [27, 25, 24], "rim", skip=("down",))                   # roof plate
    m.box([4, 25, 4], [12, 27, 12], "dark", skip=("down",))                     # hub
    m.box([5, 27, 5], [11, 28, 11], "bronze", skip=("down",))
    m.box([12, 25.3, 7], [24, 26.7, 9], PIPE_Z | {"north": "pipe_h", "south": "pipe_h", "east": "pipe_end"},
          skip=("west", "down"))                                               # roof pipe to the front-right corner
    m.box([5, 6, -13.6], [11, 12, -13], "window", skip=("south",))              # front window
    m.box([4.4, 5.4, -13.4], [11.6, 6, -13], "dark", skip=("south",))
    # connection stubs: front-right cell north and east, back-left cell south and west
    m.box([20.5, 4.5, -15], [27.5, 11.5, -13], PIPE_Z, skip=("south", "north"))
    m.box([20, 4, -16], [28, 12, -15], "flange")
    m.box([29, 4.5, -11.5], [31, 11.5, -4.5], {"north": "pipe_h", "south": "pipe_h", "up": "pipe_h", "down": "pipe_h",
                                               "east": "pipe_end", "west": "pipe_end"}, skip=("west", "east"))
    m.box([31, 4, -12], [32, 12, -4], "flange")
    m.box([-11.5, 4.5, 29], [-4.5, 11.5, 31], PIPE_Z, skip=("south", "north"))
    m.box([-12, 4, 31], [-4, 12, 32], "flange")
    m.box([-15, 4.5, 20.5], [-13, 11.5, 27.5], {"north": "pipe_h", "south": "pipe_h", "up": "pipe_h", "down": "pipe_h",
                                                "east": "pipe_end", "west": "pipe_end"}, skip=("west", "east"))
    m.box([-16, 4, 20], [-15, 12, 28], "flange")
    # valve wheels on the two connection corners (riser + wheel)
    for (cx, cz) in ((27, -11), (-11, 27)):
        m.box([cx - 1, 12.2, cz - 1], [cx + 1, 16, cz + 1], "dark", skip=("down",))
        m.box([cx - 3, 16, cz - 3], [cx + 3, 17, cz + 3], "bronze")
    return m


# ------------------------------------------------------------------------------------------------- pump
PUMP_TEX = {"pipe_h": NS + "pipe_h", "pipe_v": NS + "pipe_v", "pipe_end": NS + "pipe_end", "flange": NS + "flange",
            "dark": NS + "dark_panel", "steel": NS + "steel_panel", "blue": NS + "tier_blue", "bronze": NS + "boiler_bronze",
            "particle": NS + "steel_panel"}


def pump():
    """Controller = back cell (z 0..16), front cell z -16..0; fluid flows north (toward -z)."""
    m = Model(PUMP_TEX)
    m.box([4.5, 4.5, -15], [11.5, 11.5, 15], PIPE_Z, skip=("north", "south"))   # through pipe
    m.box([4, 4, -16], [12, 12, -15], "flange")
    m.box([4, 4, 15], [12, 12, 16], "flange")
    m.box([4, 4, -13], [12, 12, -12], "flange")
    m.box([4, 4, 12], [12, 12, 13], "flange")
    m.box([3.9, 6, -14], [4.5, 10, -13], "blue", skip=("east",))                # pipe windows (blue)
    m.box([11.5, 6, -14], [12.1, 10, -13], "blue", skip=("west",))
    m.box([3.9, 6, 13], [4.5, 10, 14], "blue", skip=("east",))
    m.box([11.5, 6, 13], [12.1, 10, 14], "blue", skip=("west",))
    m.box([2, 1.5, -9], [14, 13, 7], "dark")                                    # housing
    m.box([3, 1, -7], [13, 1.5, 5], "steel", skip=("up",))                      # feet
    m.box([3, 11, -8], [7.5, 15.5, 6], "steel")                                 # two cylinders
    m.box([8.5, 11.05, -8], [13, 15.45, 6], "steel")
    m.box([2.7, 12, -5], [3, 14.5, 1], "blue", skip=("east",))                  # their blue windows
    m.box([13, 12, -5], [13.3, 14.5, 1], "blue", skip=("west",))
    m.box([3.2, 15.5, -4], [7.3, 15.8, 0], "blue", skip=("down",))
    m.box([8.7, 15.45, -4], [12.8, 15.75, 0], "blue", skip=("down",))
    for z0 in (-10, 6):                                                         # bronze bellows
        m.box([3, 10, z0], [13, 15, z0 + 2], "bronze")
        m.box([3.5, 10.5, z0 - 0.5 if z0 < 0 else z0 + 2], [12.5, 14.5, z0 if z0 < 0 else z0 + 2.5], "bronze",
              skip=("south",) if z0 < 0 else ("north",))
    return m


ITEM_DISPLAY_1 = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}
ITEM_DISPLAY_3 = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -0.8, 0], "scale": [0.2174, 0.2174, 0.2174]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2.6, 0], "scale": [0.087, 0.087, 0.087]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -0.8, 0], "scale": [0.1739, 0.1739, 0.1739]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.28, -0.82], "scale": [0.1826, 0.1826, 0.1826]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -0.9, 0], "scale": [0.1948, 0.1948, 0.1948]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, -0.9, 0], "scale": [0.1948, 0.1948, 0.1948]},
}
ITEM_DISPLAY_2 = {k: dict(v, scale=[s * 0.75 for s in v["scale"]]) for k, v in ITEM_DISPLAY_1.items()}


def blockstates():
    pipe = {"multipart": [{"apply": {"model": NS + "pipe_core"}}]}
    rots = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}
    for side, rot in rots.items():
        pipe["multipart"].append({"when": {side: "pipe"}, "apply": dict({"model": NS + "pipe_side"}, **rot)})
        pipe["multipart"].append({"when": {side: "cap"}, "apply": dict({"model": NS + "pipe_cap"}, **rot)})
    pipe["multipart"].append({"when": {"up": "pipe"}, "apply": {"model": NS + "pipe_up"}})
    pipe["multipart"].append({"when": {"down": "pipe"}, "apply": {"model": NS + "pipe_down"}})
    pipe["multipart"].append({"when": {"up": "cap"}, "apply": {"model": NS + "pipe_cap", "x": 270}})
    pipe["multipart"].append({"when": {"down": "cap"}, "apply": {"model": NS + "pipe_cap", "x": 90}})
    write(os.path.join(BS, "pipe.json"), pipe)
    six = dict(rots)
    six["up"] = {"x": 270}
    six["down"] = {"x": 90}
    write(os.path.join(BS, "pipe_to_ground.json"),
          {"variants": {f"facing={f}": dict({"model": NS + "pipe_to_ground"}, **r) for f, r in six.items()}})
    write(os.path.join(BS, "storage_tank.json"), {"variants": {"": {"model": NS + "storage_tank"}}})
    write(os.path.join(BS, "pump.json"),
          {"variants": {f"facing={f}": dict({"model": NS + "pump"}, **r) for f, r in rots.items()}})


def main():
    write(os.path.join(MB, "pipe_cap.json"), pipe_cap().json())
    write(os.path.join(MB, "pipe_to_ground.json"), pipe_to_ground().json())
    write(os.path.join(MB, "storage_tank.json"),
          {"ambientocclusion": False, "textures": {"particle": NS + "steel_panel"}, "elements": []})
    write(os.path.join(MB, "storage_tank_body.json"), storage_tank_body().json())
    write(os.path.join(MB, "pump.json"), pump().json())
    write(os.path.join(MI, "pipe_to_ground.json"), {"parent": NS + "pipe_to_ground", "display": ITEM_DISPLAY_1})
    write(os.path.join(MI, "storage_tank.json"), {"parent": NS + "storage_tank_body", "display": ITEM_DISPLAY_3})
    write(os.path.join(MI, "pump.json"), {"parent": NS + "pump", "display": ITEM_DISPLAY_2})
    blockstates()


if __name__ == "__main__":
    main()
