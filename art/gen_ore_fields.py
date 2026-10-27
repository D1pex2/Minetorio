"""Ore field blocks: textures, block models and blockstates, plus art/ore-fields-preview.png.

Look after the Factorio ore tiles (wiki.factorio.com: Iron ore, Copper ore, Coal, Stone): chunky
lumps with a light top-left face and a dark lower edge, packed on brown ground. Iron is blue-silver,
copper salmon-orange, coal black with grey glints, stone khaki-beige. Each texture tiles seamlessly
(lumps wrap around the edges). The `_depleted` variant (shown below 10 % of the initial amount)
keeps only a few, paler lumps. Blockstates pick one of four y-rotations per position, so a field
does not look like a grid.

Deterministic; run from anywhere: python art/gen_ore_fields.py
"""
import json
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "minetorio")
TEX = os.path.join(ASSETS, "textures", "block")
MODELS = os.path.join(ASSETS, "models", "block")
STATES = os.path.join(ASSETS, "blockstates")

# brown ground, darkest to lightest
GROUND = [(74, 58, 40), (92, 72, 50), (108, 86, 60), (124, 100, 70)]

# lump palettes: outline, shadow, mid, light, glint
ORES = {
    "iron":   [(46, 54, 64), (82, 98, 114), (128, 148, 166), (176, 194, 210), (226, 236, 244)],
    "copper": [(72, 34, 20), (128, 60, 34), (186, 96, 58), (226, 136, 92), (250, 192, 152)],
    "coal":   [(10, 10, 12), (26, 26, 28), (44, 44, 47), (68, 68, 72), (128, 128, 134)],
    "stone":  [(84, 70, 46), (128, 110, 76), (168, 148, 104), (204, 186, 138), (234, 222, 178)],
}

SEEDS = {"iron": 11, "copper": 23, "coal": 37, "stone": 41}


def ground(rnd):
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            r = rnd.random()
            c = GROUND[1] if r < 0.45 else GROUND[2] if r < 0.8 else GROUND[0] if r < 0.92 else GROUND[3]
            im.putpixel((x, y), c + (255,))
    return im


# lump shapes: rows of (dx, width), drawn from the top; the lighting follows the shape
SHAPES = [
    [(1, 2), (0, 4), (0, 4), (1, 2)],          # round 4×4
    [(1, 3), (0, 5), (0, 5), (0, 5), (1, 3)],  # big 5×5
    [(1, 3), (0, 5), (0, 5), (1, 3)],          # wide 5×4
    [(0, 3), (0, 3), (1, 2)],                  # small 3×3
    [(1, 2), (0, 3), (0, 4), (1, 3)],          # leaning 4×4
]


def lump(im, x0, y0, shape, pal):
    """Draws a lump with its top-left corner at (x0, y0), wrapping around the tile."""
    cells = set()
    for row, (dx, w) in enumerate(shape):
        for i in range(w):
            cells.add((dx + i, row))
    h = len(shape)
    for (cx, cy) in cells:
        below = (cx, cy + 1) not in cells
        right = (cx + 1, cy) not in cells
        above = (cx, cy - 1) not in cells
        left = (cx - 1, cy) not in cells
        if below and cy == h - 1:
            c = pal[0]                      # dark lower rim
        elif below or right:
            c = pal[1]
        elif above or left:
            c = pal[3]
        else:
            c = pal[2]
        im.putpixel(((x0 + cx) % 16, (y0 + cy) % 16), c + (255,))
    # glint near the top-left
    gx = min(c[0] for c in cells if c[1] == 1) + 1 if h > 2 else shape[0][0]
    im.putpixel(((x0 + gx) % 16, (y0 + 1) % 16), pal[4] + (255,))
    # shadow cast on the ground below the lump
    for (cx, cy) in cells:
        if (cx, cy + 1) not in cells:
            p = ((x0 + cx) % 16, (y0 + cy + 1) % 16)
            if im.getpixel(p)[:3] in GROUND:
                im.putpixel(p, GROUND[0] + (255,))


def place_lumps(rnd, count):
    """Non-overlapping (on a wrapped 16×16 torus) lump positions and shapes."""
    taken = set()
    out = []
    tries = 0
    while len(out) < count and tries < 2000:
        tries += 1
        shape = SHAPES[rnd.randrange(len(SHAPES))]
        x0, y0 = rnd.randrange(16), rnd.randrange(16)
        cells = {((x0 + dx + i) % 16, (y0 + row) % 16) for row, (dx, w) in enumerate(shape) for i in range(w)}
        halo = {((x + ox) % 16, (y + oy) % 16) for (x, y) in cells for ox in (-1, 0, 1) for oy in (-1, 0, 1)}
        if halo & taken:
            continue
        taken |= cells
        out.append((x0, y0, shape))
    return out


def pale(pal, ground_c=GROUND[2], t=0.45):
    """Mixes a palette toward the ground and grey: a worked-out look."""
    out = []
    for c in pal:
        g = sum(c) / 3
        out.append(tuple(int(round((c[i] * 0.6 + g * 0.4) * (1 - t) + ground_c[i] * t)) for i in range(3)))
    return out


def texture(name, depleted):
    rnd = random.Random(SEEDS[name] * (2 if depleted else 1))
    im = ground(rnd)
    lumps = place_lumps(rnd, 4 if depleted else 12)
    pal = pale(ORES[name]) if depleted else ORES[name]
    for (x0, y0, shape) in lumps:
        lump(im, x0, y0, shape, pal)
    return im


def write_json(path, data):
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    for d in (TEX, MODELS, STATES):
        os.makedirs(d, exist_ok=True)
    tiles = {}
    for name in ORES:
        block = f"{name}_ore_field"
        variants = {}
        for depleted in (False, True):
            tex = block + ("_depleted" if depleted else "")
            im = texture(name, depleted)
            tiles[tex] = im
            im.save(os.path.join(TEX, tex + ".png"))
            write_json(os.path.join(MODELS, tex + ".json"),
                       {"parent": "minecraft:block/cube_all", "textures": {"all": f"minetorio:block/{tex}"}})
            variants[f"depleted={'true' if depleted else 'false'}"] = [
                {"model": f"minetorio:block/{tex}", "y": r} if r else {"model": f"minetorio:block/{tex}"}
                for r in (0, 90, 180, 270)]
        write_json(os.path.join(STATES, block + ".json"), {"variants": variants})

    # preview: each texture tiled 3×3 at 6×, fresh on top, depleted below
    s, n = 6, 3
    cell = 16 * s * n
    prev = Image.new("RGBA", (len(ORES) * (cell + 8) + 8, 2 * (cell + 8) + 8), (40, 40, 40, 255))
    for i, name in enumerate(ORES):
        for j, suffix in enumerate(("", "_depleted")):
            big = tiles[f"{name}_ore_field{suffix}"].resize((16 * s, 16 * s), Image.NEAREST)
            for tx in range(n):
                for ty in range(n):
                    prev.paste(big, (8 + i * (cell + 8) + tx * 16 * s, 8 + j * (cell + 8) + ty * 16 * s))
    prev.save(os.path.join(HERE, "ore-fields-preview.png"))
    print("ore fields:", ", ".join(sorted(tiles)))


if __name__ == "__main__":
    main()
