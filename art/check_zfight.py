"""Z-fighting detector for Minetorio's JSON models.

    python -I art/check_zfight.py          -> table of findings; exit code 1 if any

Two faces z-fight when they lie in the same plane (within 0.001 px), face the same way and overlap
with area > 0. Faces facing opposite ways never show together (back faces are culled), so they are
not reported. An overlap whose front (the space just in front of it) lies wholly inside a third element is
covered and not reported either.

Checks:
  * every model under models/{block,item} (parents resolved), pairs of faces of different elements;
  * "composites": models drawn together by a block entity renderer or a multipart blockstate,
    placed with the renderer's offsets (table COMPOSITES below, comments point at the Java lines);
    only pairs of faces from different parts are checked there.

Element rotations (origin/axis/angle, no rescale) are applied, so rotated faces are planes in 3D.
The JUnit test dev.minetorio.client.ModelZFightTest runs the same check.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets")
PLANE_EPS = 0.001
AREA_EPS = 1e-4

# --------------------------------------------------------------------------- vector helpers


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def mul(a, k):
    return (a[0] * k, a[1] * k, a[2] * k)


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def norm(a):
    n = math.sqrt(dot(a, a))
    return (a[0] / n, a[1] / n, a[2] / n)


def xf(p, tr, vector=False, inverse=False):
    """Applies one transform to a point (or a direction if vector): ("t", (dx, dy, dz), None) translates,
    (axis, degrees, origin) rotates about the axis ("x"/"y"/"z") through origin, right-handed like
    FaceBakery and PoseStack's Axis.XP/YP."""
    axis, a, origin = tr
    if axis == "t":
        return p if vector else (sub(p, a) if inverse else add(p, a))
    ang = math.radians(-a if inverse else a)
    c, s = math.cos(ang), math.sin(ang)
    o = (0, 0, 0) if vector else origin
    x, y, z = sub(p, o)
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        z, x = z * c - x * s, z * s + x * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return add((x, y, z), o)


# --------------------------------------------------------------------------- model loading


def load_json(rl):
    ns, path = rl.split(":") if ":" in rl else ("minecraft", rl)
    p = os.path.join(ASSETS, ns, "models", path + ".json")
    if not os.path.exists(p):
        return None
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def resolve_elements(rl):
    """Elements of a model: its own, or those of the nearest parent that has any."""
    while rl:
        j = load_json(rl)
        if j is None:
            return []
        if "elements" in j:
            return j["elements"]
        rl = j.get("parent")
    return []


FACE_CORNERS = {
    # corners of each face of the box f..t, as (x, y, z) picks: 0 = from, 1 = to
    "north": ((0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)),
    "south": ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)),
    "west": ((0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)),
    "east": ((1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)),
    "down": ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)),
    "up": ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)),
}
NORMAL = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0),
          "west": (-1, 0, 0)}


class Solid:
    """An element in model space: its rendered faces as 3D polygons and a point-in-volume test."""

    def __init__(self, label, f, t, transforms, faces):
        self.label = label
        self.f, self.t = f, t
        self.transforms = transforms   # list of (axis, deg, origin) applied in order
        self.faces = []                # (name, normal, corners)
        for name in faces:
            pts = [tuple(f[i] if pick[i] == 0 else t[i] for i in range(3)) for pick in FACE_CORNERS[name]]
            n = NORMAL[name]
            for tr in transforms:
                pts = [xf(p, tr) for p in pts]
                n = xf(n, tr, vector=True)
            self.faces.append((name, n, pts))

    def moved(self, label, transforms):
        return Solid(label, self.f, self.t, self.transforms + transforms, [n for n, _, _ in self.faces])

    def contains(self, p, eps=1e-4):
        for tr in reversed(self.transforms):
            p = xf(p, tr, inverse=True)
        return all(self.f[i] - eps <= p[i] <= self.t[i] + eps for i in range(3))


def solids_of(model_rl, label=None):
    out = []
    for i, el in enumerate(resolve_elements(model_rl)):
        r = el.get("rotation")
        tr = [(r["axis"], r["angle"], tuple(r["origin"]))] if r and r.get("angle") else []
        faces = list(el.get("faces", {}).keys())
        out.append(Solid(f"{model_rl if label is None else label}#{i}", tuple(el["from"]), tuple(el["to"]), tr, faces))
    return out


def blockstate_y(deg):
    """Blockstate "y" rotation (clockwise seen from above) about the block center."""
    return ("y", -deg, (8, 8, 8))


def quad_solid(label, quads):
    """A procedural part (no volume): list of (normal, corners)."""
    s = Solid.__new__(Solid)
    s.label, s.f, s.t, s.transforms = label, (0, 0, 0), (0, 0, 0), []
    s.faces = [(f"q{i}", n, pts) for i, (n, pts) in enumerate(quads)]
    s.contains = lambda p, eps=1e-4: False
    s.moved = lambda lbl, trs: s
    return s


# --------------------------------------------------------------------------- polygon overlap


def plane_basis(n):
    a = (1, 0, 0) if abs(n[0]) < 0.9 else (0, 1, 0)
    u = norm(cross(n, a))
    return u, cross(n, u)


def to2d(pts, u, v):
    poly = [(dot(p, u), dot(p, v)) for p in pts]
    if area2(poly) < 0:
        poly.reverse()
    return poly


def area2(poly):
    s = 0.0
    for i in range(len(poly)):
        x0, y0 = poly[i]
        x1, y1 = poly[(i + 1) % len(poly)]
        s += x0 * y1 - x1 * y0
    return s / 2


def clip(subject, clipper):
    """Sutherland-Hodgman: intersection of two convex CCW polygons."""
    out = subject
    for i in range(len(clipper)):
        if not out:
            break
        a, b = clipper[i], clipper[(i + 1) % len(clipper)]
        inp, out = out, []

        def side(p):
            return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])

        for j in range(len(inp)):
            p, q = inp[j], inp[(j + 1) % len(inp)]
            sp, sq = side(p), side(q)
            if sp >= 0:
                out.append(p)
            if (sp >= 0) != (sq >= 0):
                k = sp / (sp - sq)
                out.append((p[0] + (q[0] - p[0]) * k, p[1] + (q[1] - p[1]) * k))
    return out


def overlap(face_a, face_b):
    """(area, 3D points of the overlap polygon) of two coplanar same-facing faces, or None."""
    _, na, pa = face_a
    _, nb, pb = face_b
    if dot(na, nb) < 1 - 1e-6:
        return None
    if abs(dot(na, pa[0]) - dot(na, pb[0])) > PLANE_EPS:
        return None
    u, v = plane_basis(na)
    poly = clip(to2d(pa, u, v), to2d(pb, u, v))
    if len(poly) < 3:
        return None
    a = area2(poly)
    if a <= AREA_EPS:
        return None
    d = dot(na, pa[0])
    pts = [add(add(mul(u, x), mul(v, y)), mul(na, d)) for x, y in poly]
    return a, pts, d


def hidden(pts, n, others):
    """The space just in front of the overlap is filled by one other element, so neither face shows."""
    probe = list(pts) + [mul(add(add(pts[0], pts[1]), pts[2]), 1 / 3)]
    for s in others:
        if all(s.contains(add(p, mul(n, 0.005))) for p in probe):
            return True
    return False


def pairs(group_a, group_b, everything):
    """Findings between faces of solids in group_a and group_b (same group: distinct solids only)."""
    found = []
    same = group_a is group_b
    for i, sa in enumerate(group_a):
        for j, sb in enumerate(group_b):
            if same and j <= i:
                continue
            for fa in sa.faces:
                for fb in sb.faces:
                    o = overlap(fa, fb)
                    if not o:
                        continue
                    area, pts, d = o
                    others = [s for s in everything if s is not sa and s is not sb]
                    if hidden(pts, fa[1], others):
                        continue
                    found.append((f"{sa.label}.{fa[0]}", f"{sb.label}.{fb[0]}", fa[1], d, area))
    return found


# --------------------------------------------------------------------------- composites


def inserter_arm(reach, arm, facing_deg=0):
    """Joint, arm and hand boxes of InserterRenderer.render (client/inserter/InserterRenderer.java:97-111, the
    claw fingers left out), in model pixels, for facing north (heading toward the pickup = north at arm 0)."""
    SHOULDER_Y, ELBOW_RISE, ELBOW_RISE_PER_REACH, ELBOW_AT, HAND_RISE, HAND_REACH = 5.5 / 16, 0.40, 0.06, 0.1, 0.2, 0.85
    a = math.pi * arm
    c, s = -math.cos(a), math.sin(a)
    h = (0 * c + 1 * s, -1 * c + 0 * s)   # facing north (0,-1), right = east (1,0)
    radius = HAND_REACH * reach
    shoulder = (0.5, SHOULDER_Y, 0.5)
    elbow = (0.5 + h[0] * radius * ELBOW_AT, SHOULDER_Y + ELBOW_RISE + ELBOW_RISE_PER_REACH * reach,
             0.5 + h[1] * radius * ELBOW_AT)
    hand = (0.5 + h[0] * radius, SHOULDER_Y + HAND_RISE, 0.5 + h[1] * radius)
    side = (-h[1], 0, h[0])
    quads = []
    for a0, b0, half in ((shoulder, add(shoulder, (0, 0.1, 0)), 0.065),
                         (add(shoulder, (0, 0.05, 0)), elbow, 0.035),
                         (sub(elbow, (0, 0.045, 0)), add(elbow, (0, 0.045, 0)), 0.045),
                         (elbow, add(hand, (0, 0.03, 0)), 0.024),
                         (sub(hand, (0, 0.06, 0)), add(hand, (0, 0.03, 0)), 0.05)):
        u = norm(sub(b0, a0))
        sv = norm(side)
        vv = norm(cross(u, sv))
        sv = norm(cross(vv, u))
        corners = []
        for i in range(8):
            base = b0 if i & 4 else a0
            base = add(base, mul(sv, half if i & 2 else -half))
            base = add(base, mul(vv, half if i & 1 else -half))
            corners.append(mul(base, 16))
        for idx, n in (((2, 3, 7, 6), sv), ((0, 4, 5, 1), mul(sv, -1)), ((1, 5, 7, 3), vv),
                       ((0, 2, 6, 4), mul(vv, -1)), ((4, 6, 7, 5), u), ((0, 1, 3, 2), mul(u, -1))):
            quads.append((n, [corners[k] for k in idx]))
    return quad_solid(f"inserter_arm(reach={reach},arm={arm})", quads)


SPIN = (0, 11.25, 22.5, 33.75, 45, 67.5)   # sampled angles of parts that turn


def spin_y(deg):
    """BER turn about the footprint center's vertical axis (model x = z = 8)."""
    return [("y", deg, (8, 0, 8))] if deg else []


def M(name):
    return "minetorio:block/" + name


# Each composite: (name, [part, ...]); part = (label, model or callable, [transform list per sampled pose]
# [, spin axis]). Model coordinates are pixels in the frame of the model drawn with the identity transform.
# A part that turns is checked whole in its authored pose (the first); in the other sampled poses only its
# faces whose normal is parallel to the spin axis count, since only those stay coplanar while it turns
# (others line up for a single frame at most).
Y, X = (0, 1, 0), (1, 0, 0)
COMPOSITES = [
    # AssemblerRenderer.java:35-43: the tier's body and gear both at translate(-0.5, 0, -0.5) of the footprint
    # center; the gear turns about the vertical center axis (line 42).
    *[(f"assembling_machine_{t}", [("body", M(f"assembling_machine_{t}_body"), [[]]),
                                   ("gear", M(f"assembling_machine_{t}_gear"), [spin_y(a) for a in SPIN], Y)])
      for t in (1, 2)],
    # DrillRenderer.java: electric body at the center, head turning about the vertical axis, the carriage
    # riding 0..CARRIAGE_TRAVEL_PX (4 px) up, the tower one block up (draw(top, 1, ...)).
    ("electric_mining_drill", [("body", M("electric_mining_drill_body"), [[]]),
                               ("head", M("electric_mining_drill_head"), [spin_y(a) for a in SPIN], Y),
                               ("carriage", M("electric_mining_drill_carriage"),
                                [[("t", (0, dy, 0), None)] if dy else [] for dy in (0, 1, 2, 3, 4)]),
                               ("tower", M("electric_mining_drill_tower"), [[("t", (0, 16, 0), None)]])]),
    # Burner drill: body on the controller's blockstate (x 0..32, z -16..16); DrillRenderer.java puts the
    # head at MachineRender.center (2x2, north: 1.0, 0.0 blocks) - 0.5 = (+8, 0, -8) px.
    ("burner_mining_drill", [("body", M("burner_mining_drill"), [[]]),
                             ("head", M("burner_mining_drill_head"),
                              [[("y", a, (8, 0, 8)), ("t", (8, 0, -8), None)] if a else [("t", (8, 0, -8), None)]
                               for a in SPIN], Y)]),
    # LabRenderer.java: body and dome (glowing or idle) at the center, the dome turning.
    *[(f"lab+{d}", [("body", M("lab_body"), [[]]), ("dome", M(d), [spin_y(a) for a in SPIN], Y)])
      for d in ("lab_dome", "lab_dome_idle")],
    # RadarRenderer.java: base at the center, the dish one block up turning about the vertical center axis.
    ("radar", [("base", M("radar_base"), [[]]),
               ("dish", M("radar_dish"), [[("y", a, (8, 0, 8)), ("t", (0, 16, 0), None)] if a else [("t", (0, 16, 0), None)]
                                          for a in SPIN], Y)]),
    # SteamMachineRenderer.java: boiler body and fire, both at dz 0.
    ("boiler", [("body", M("boiler_body"), [[]]), ("fire", M("boiler_fire"), [[]])]),
    # SteamMachineRenderer.Engine: body at dz 0, front at -1.5 blocks, rear at +1.5, gears turning about x
    # through their models' (y 8, z 8), drawn at their pivots (Engine.GEARS).
    ("steam_engine", [("body", M("steam_engine_body"), [[]]),
                      ("front", M("steam_engine_front"), [[("t", (0, 0, -24), None)]]),
                      ("rear", M("steam_engine_rear"), [[("t", (0, 0, 24), None)]]),
                      *[(f"gear@{py},{pz}", M(model),
                         [[("x", a, (0, 8, 8)), ("t", (0, py - 8, pz - 8), None)] if a else
                          [("t", (0, py - 8, pz - 8), None)] for a in SPIN], X)
                        for model, py, pz in (("steam_engine_gear", 15, 10), ("steam_engine_gear_small", 12, -6),
                                              ("steam_engine_gear_small", 21, 25))]]),
    # blockstates/small_electric_pole.json: the middle and top parts' models on the blocks above the lower part's.
    ("small_electric_pole", [("lower", M("small_electric_pole"), [[]]),
                             ("middle", M("small_electric_pole_middle"), [[("t", (0, 16, 0), None)]]),
                             ("top", M("small_electric_pole_top"), [[("t", (0, 32, 0), None)]])]),
    # InserterRenderer.java:97-111: procedural arm over the template_inserter base (shoulder at y 5.5 px).
    *[(f"{n}+arm", [("base", M(n), [[]]),
                    ("arm", lambda r=r: [inserter_arm(r, arm) for arm in (0, 0.25, 0.5, 0.75, 1)], None)])
      for n, r in (("burner_inserter", 1), ("inserter", 1), ("long_handed_inserter", 2), ("fast_inserter", 1))],
    # blockstates/pipe.json: core + four sides (y 0/90/180/270) + up + down.
    ("pipe(all arms)", [("core", M("pipe_core"), [[]]),
                        *[(f"side{y}", M("pipe_side"), [[blockstate_y(y)]]) for y in (0, 90, 180, 270)],
                        ("up", M("pipe_up"), [[]]), ("down", M("pipe_down"), [[]])]),
    # Neighbours: a belt feeding an underground entrance from behind (south, +z), a belt leaving an exit
    # (north, -z), a belt run, a splitter fed by two belts and an inserter beside a belt.
    *[(f"{pre}underground_belt_input+belt", [("ug", M(f"{pre}underground_belt_input"), [[]]),
                                             ("belt", M(f"{pre}transport_belt"), [[("t", (0, 0, 16), None)]])])
      for pre in ("", "fast_", "express_")],
    *[(f"{pre}underground_belt_output+belt", [("ug", M(f"{pre}underground_belt_output"), [[]]),
                                              ("belt", M(f"{pre}transport_belt"), [[("t", (0, 0, -16), None)]])])
      for pre in ("", "fast_", "express_")],
    ("belt+belt", [("a", M("transport_belt"), [[]]), ("b", M("transport_belt"), [[("t", (0, 0, -16), None)]])]),
    ("splitter+belts", [("splitter", M("splitter"), [[]]),
                        ("belt0", M("transport_belt"), [[("t", (0, 0, 16), None)]]),
                        ("belt1", M("transport_belt"), [[("t", (16, 0, 16), None)]])]),
    ("inserter+belt", [("inserter", M("inserter"), [[]]), ("belt", M("transport_belt"), [[("t", (0, 0, -16), None)]])]),
    # Belt ramps (R25, north-facing, travel -z): the flat belt feeding the low end, the flat belt at the high
    # end, the next ramp of a chain, and a flat belt beside the ramp.
    ("ramp_up+flat_bottom", [("ramp", M("transport_belt_ascending"), [[]]),
                             ("flat", M("transport_belt"), [[("t", (0, 0, 16), None)]])]),
    ("ramp_up+flat_top", [("ramp", M("transport_belt_ascending"), [[]]),
                          ("flat", M("transport_belt"), [[("t", (0, 16, -16), None)]])]),
    ("ramp_up+ramp_up", [("a", M("transport_belt_ascending"), [[]]),
                         ("b", M("transport_belt_ascending"), [[("t", (0, 16, -16), None)]])]),
    ("ramp_up+flat_beside", [("ramp", M("transport_belt_ascending"), [[]]),
                             ("flat", M("transport_belt"), [[("t", (16, 0, 0), None)]])]),
    ("ramp_down+flat_top", [("ramp", M("transport_belt_descending"), [[]]),
                            ("flat", M("transport_belt"), [[("t", (0, 16, 16), None)]])]),
    ("ramp_down+flat_bottom", [("ramp", M("transport_belt_descending"), [[]]),
                               ("flat", M("transport_belt"), [[("t", (0, 0, -16), None)]])]),
    ("ramp_down+ramp_down", [("a", M("transport_belt_descending"), [[]]),
                             ("b", M("transport_belt_descending"), [[("t", (0, -16, -16), None)]])]),
    ("ramp_down+flat_beside", [("ramp", M("transport_belt_descending"), [[]]),
                               ("flat", M("transport_belt"), [[("t", (16, 0, 0), None)]])]),
]


def part_poses(part):
    label, model, poses = part[:3]
    if callable(model):
        return [[s] for s in model()]
    base = solids_of(model, label)
    return [[s.moved(f"{label}:{s.label.split('#')[-1]}", tr) if tr else s for s in base] for tr in poses]


def check_composite(name, parts):
    found = {}
    expanded = [part_poses(p) for p in parts]
    for i in range(len(parts)):
        for j in range(i + 1, len(parts)):
            for ia, pose_a in enumerate(expanded[i]):
                for ib, pose_b in enumerate(expanded[j]):
                    axes = [p[3] for p, k in ((parts[i], ia), (parts[j], ib)) if k > 0 and len(p) > 3]
                    rest = [s for k, e in enumerate(expanded) if k not in (i, j) for s in e[0]]
                    for a, b, n, d, area in pairs(pose_a, pose_b, pose_a + pose_b + rest):
                        if all(abs(dot(n, ax)) > 1 - 1e-6 for ax in axes):
                            found.setdefault((a, b), (n, d, area))
    return [(name, a, b, n, d, area) for (a, b), (n, d, area) in found.items()]


# --------------------------------------------------------------------------- main


def all_models():
    root = os.path.join(ASSETS, "minetorio", "models")
    for kind in ("block", "item"):
        for fn in sorted(os.listdir(os.path.join(root, kind))):
            if fn.endswith(".json"):
                yield f"minetorio:{kind}/{fn[:-5]}"


def run():
    findings = []
    for rl in all_models():
        sol = solids_of(rl, "")
        for a, b, n, d, area in pairs(sol, sol, sol):
            findings.append((rl.split(":")[1], a, b, n, d, area))
    for name, parts in COMPOSITES:
        findings.extend(check_composite(name, parts))
    return findings


def fmt_n(n):
    return "(" + ",".join(f"{v:+.2f}" for v in n) + ")"


def main():
    findings = run()
    if not findings:
        print("no z-fighting faces")
        return 0
    w = max(len(f[0]) for f in findings)
    print(f"{'model':<{w}}  {'face A':<26} {'face B':<26} {'normal':<18} {'plane':>9} {'area':>9}")
    for model, a, b, n, d, area in findings:
        print(f"{model:<{w}}  {a:<26} {b:<26} {fmt_n(n):<18} {d:9.3f} {area:9.3f}")
    print(f"{len(findings)} z-fighting face pairs")
    return 1


if __name__ == "__main__":
    sys.exit(main())
