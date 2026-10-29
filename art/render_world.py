"""art/world-models-preview.png: offline renders of the Minetorio world objects as they stand in the world
(a green, olive and red tree, a dry tree, rocks, a huge rock, a cliff step, the crash site), using
render_models.render. Run after gen_world.py: python art/render_world.py
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_models as rm  # noqa: E402

M = "minetorio:block/"


def cube(tex, dx, dy, dz, top=None, bottom=None):
    faces = {f: {"texture": tex} for f in ("north", "south", "east", "west")}
    faces["up"] = {"texture": top or tex}
    faces["down"] = {"texture": bottom or tex}
    return {"from": [dx, dy, dz], "to": [dx + 16, dy + 16, dz + 16], "faces": faces}


def ground(tex, w, d, y=-16, x0=0, z0=0):
    return [cube(M + tex, x0 + 16 * i, y, z0 + 16 * k) for i in range(w) for k in range(d)]


def model_at(name, bx, by, bz):
    return rm.placed("minetorio:block/" + name, 16 * bx, 16 * by, 16 * bz)


def tree(leaves, h=4):
    els = ground("grass_1", 5, 5, x0=-32, z0=-32)
    for y in range(h):
        els += model_at("tree_trunk", 0, y, 0)
    top = h - 1
    for dy in range(-2, 2):
        rad = 1 if dy in (1, -2) else 2
        for dx in range(-rad, rad + 1):
            for dz in range(-rad, rad + 1):
                if abs(dx) == rad and abs(dz) == rad:
                    continue
                if dx == 0 and dz == 0 and dy <= 0:
                    continue
                els.append(cube(M + leaves, 16 * dx, 16 * (top + dy), 16 * dz))
    els.append(cube(M + leaves, 0, 16 * (top + 2), 0))
    return els


def dry_tree():
    els = ground("sand_1", 3, 3, x0=-16, z0=-16)
    for y in range(3):
        els += model_at("dry_tree_trunk", 0, y, 0)
    els += model_at("dry_tree_branches", 0, 3, 0) + model_at("dry_tree_branches", 1, 2, 0)
    return els


def rocks():
    els = ground("dirt_1", 3, 2, x0=-16)
    els += model_at("big_rock", -1, 0, 0) + model_at("sand_rock", 1, 0, 0)
    for (x, y, z) in [(0, 0, 1), (1, 0, 1), (0, 1, 1), (0, 0, 1)]:
        pass
    return els


def huge_rock():
    els = ground("grass_3", 4, 4, x0=-16, z0=-16)
    for (x, y, z) in [(0, 0, 0), (1, 0, 0), (0, 0, 1), (1, 0, 1), (0, 1, 0), (1, 1, 1), (-1, 0, 1)]:
        els.append(cube(M + "huge_rock", 16 * x, 16 * y, 16 * z))
    return els


def cliff():
    els = ground("grass_2", 6, 3, x0=-48, z0=-16)
    for x in range(-3, 3):
        for z in range(-1, 2):
            if z >= 0:
                h = 3
                for y in range(h):
                    tex = "cliff_rock" if z == 0 else "dirt_1"
                    els.append(cube(M + tex, 16 * x, 16 * y, 16 * z, top=M + ("cliff_rock" if z == 0 else "grass_2") if y == h - 1 else None))
    return els


def crash_site():
    els = ground("grass_2", 7, 4, x0=-16, z0=-16)
    layers = [".HHHHH|HHHHHH|.HHHH.", "..HHH.|.HHHHH|..HHH.", "......|...HH.|......", "......|....H.|......"]
    for dy, layer in enumerate(layers):
        for dz, row in enumerate(layer.split("|")):
            for dx, ch in enumerate(row):
                if ch == "H":
                    els.append(cube(M + "ship_wreck_hull_side", 16 * (dx - 1), 16 * dy, 16 * (dz - 1),
                                    top=M + "ship_wreck_hull_top"))
    els += model_at("crash_site_chest", -1, 0, 1)
    els += model_at("ship_wreck_debris", -2, 0, -1) + model_at("ship_wreck_debris", 5, 0, 2)
    return els


SCENES = [("tree (green)", lambda: tree("tree_leaves_green")), ("tree (olive)", lambda: tree("tree_leaves_olive", 5)),
          ("tree (red)", lambda: tree("tree_leaves_red", 3)), ("dry tree", dry_tree), ("big rock, sand rock", rocks),
          ("huge rock", huge_rock), ("cliff step (3 high)", cliff), ("crash site", crash_site)]


def main():
    size = 320
    sheet = Image.new("RGB", (size * 4, (size + 20) * 2), (40, 42, 46))
    d = ImageDraw.Draw(sheet)
    for i, (name, fn) in enumerate(SCENES):
        els = fn()
        lo, hi = rm.bounds(els)
        im = rm.render(els, {}, yaw=35, pitch=-28, size=size, extent=(lo, hi))
        x, y = (i % 4) * size, (i // 4) * (size + 20)
        sheet.paste(im, (x, y + 20))
        d.text((x + 6, y + 4), name, fill=(230, 230, 230))
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "world-models-preview.png")
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
