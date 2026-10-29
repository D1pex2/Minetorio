"""The Minetorio world ("Nauvis"): original 16x16 pixel art in a Factorio-like palette, plus the block and
item models and blockstates, the fish's 32x32 entity texture and art/world-preview.png.

Original art (no Factorio texture is copied): ground tiles are muted, earthy and fine-grained like
Factorio's Nauvis tiles seen up close - olive-to-dark greens, khaki dirt, warm sand, rust-red desert;
cliffs are layered brown rock; trees have slender dark trunks and round, clumpy crowns; rocks are
grey-brown boulders with a lit top. Every ground texture tiles seamlessly; blockstates pick one of four
y-rotations per position so the ground does not repeat visibly.

Deterministic; run from anywhere: python art/gen_world.py
"""
import json
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "minetorio")
TEX = os.path.join(ASSETS, "textures", "block")
ITEM_TEX = os.path.join(ASSETS, "textures", "item")
ENTITY_TEX = os.path.join(ASSETS, "textures", "entity")
MODELS = os.path.join(ASSETS, "models", "block")
ITEM_MODELS = os.path.join(ASSETS, "models", "item")
STATES = os.path.join(ASSETS, "blockstates")
M = "minetorio:"


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def hexc(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


def wrap_noise(rnd, cells=4):
    """Tileable smooth value noise on 16x16 (a cells x cells random grid, bilinear with smoothstep)."""
    g = [[rnd.random() for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            fx, fy = x * cells / 16, y * cells / 16
            ix, iy = int(fx), int(fy)
            tx, ty = fx - ix, fy - iy
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = g[iy % cells][ix % cells]
            b = g[iy % cells][(ix + 1) % cells]
            c = g[(iy + 1) % cells][ix % cells]
            d = g[(iy + 1) % cells][(ix + 1) % cells]
            out[y][x] = (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty
    return out


def new(w=16, h=16):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def put(im, x, y, c, a=255):
    im.putpixel((x % im.width, y % im.height), tuple(c) + (a,))


# --- ground tiles ---------------------------------------------------------------------------------------
# (dark, mid, light, accent) and a texture style
GROUND = {
    "grass_1": ((0x34501C), (0x46661F), (0x5A7C2A), (0x6E8C34), "grass"),
    "grass_2": ((0x4E5E22), (0x61722A), (0x758634), (0x8A9440), "grass"),
    "grass_3": ((0x5C5826), (0x726C30), (0x87803C), (0x9A8E4A), "grass"),
    "grass_4": ((0x6E6234), (0x84763E), (0x988A4C), (0xA89A5C), "dry_grass"),
    "dry_dirt": ((0x6E5A3E), (0x84704E), (0x98845E), (0xAC9A72), "dirt"),
    "dirt_1": ((0x58432E), (0x6C543A), (0x7E6446), (0x947A58), "dirt"),
    "dirt_2": ((0x3F3022), (0x4F3D2C), (0x5E4A36), (0x725C46), "dirt"),
    "sand_1": ((0xA48858), (0xB89C68), (0xC8AE7A), (0xD8C292), "sand"),
    "sand_2": ((0x947A4E), (0xA88C5E), (0xBA9E6E), (0xCCB284), "sand"),
    "red_desert_0": ((0x7E4A2C), (0x955A36), (0xA86A42), (0xBC8058), "sand"),
    "red_desert_1": ((0x6A3E26), (0x7E4A2E), (0x925A38), (0xA46C4A), "dirt"),
}


def ground_tile(name):
    dark, mid, light, accent, style = [hexc(v) if isinstance(v, int) else v for v in GROUND[name]]
    rnd = random.Random(sum(ord(c) * 31 ** i for i, c in enumerate(name)))
    im = new()
    n1 = wrap_noise(rnd, 4)
    n2 = wrap_noise(rnd, 8)
    for y in range(16):
        for x in range(16):
            v = n1[y][x] * 0.55 + n2[y][x] * 0.3 + rnd.random() * 0.25
            c = dark if v < 0.38 else mid if v < 0.62 else light
            put(im, x, y, c)
    if style in ("grass", "dry_grass"):
        # short blades: a light pixel with a dark one below (lit from the top-left)
        for _ in range(26 if style == "grass" else 18):
            x, y = rnd.randrange(16), rnd.randrange(16)
            put(im, x, y, accent if rnd.random() < 0.5 else light)
            put(im, x, y + 1, dark)
        if style == "dry_grass":
            for _ in range(10):  # bare earth showing through
                put(im, rnd.randrange(16), rnd.randrange(16), shade(hexc(0x7A6444), 1.0))
    elif style == "dirt":
        for _ in range(9):  # pebbles: lit top-left, shadow bottom-right
            x, y = rnd.randrange(16), rnd.randrange(16)
            put(im, x, y, accent)
            put(im, x + 1, y + 1, shade(dark, 0.85))
        for _ in range(6):
            put(im, rnd.randrange(16), rnd.randrange(16), shade(dark, 0.9))
    else:  # sand: wind ripples, faint diagonal light and dark streaks
        for k in range(4):
            y0 = rnd.randrange(16)
            x0 = rnd.randrange(16)
            for i in range(rnd.randrange(4, 8)):
                put(im, x0 + i, y0 + (i // 3), light if k % 2 == 0 else accent)
                put(im, x0 + i, y0 + (i // 3) + 1, dark)
        for _ in range(5):
            put(im, rnd.randrange(16), rnd.randrange(16), accent)
    return im


# --- rock-like textures ---------------------------------------------------------------------------------

def cliff_rock():
    """Rough brown cliff face: wavy strata (their boundaries wander with a noise), lit ledges above
    darker undercuts, a few chipped facets; tileable."""
    rnd = random.Random(77)
    pal = [hexc(0x3A3028), hexc(0x4E4236), hexc(0x645546), hexc(0x7A6A57), hexc(0x92806A)]
    im = new()
    n = wrap_noise(rnd, 4)
    n2 = wrap_noise(rnd, 8)
    wave = [round(2 * math.sin((x / 16) * 2 * math.pi + 0.7) + 1.5 * math.sin((x / 16) * 4 * math.pi)) for x in range(16)]
    for y in range(16):
        for x in range(16):
            yy = (y + wave[x]) % 16
            phase = yy % 5  # 5-pixel strata
            v = n[y][x] * 0.6 + n2[y][x] * 0.4
            base = 2 + (1 if v > 0.6 else 0) - (1 if v < 0.32 else 0)
            if phase == 0:
                base = min(4, base + 1)  # lit ledge
            elif phase == 4:
                base = max(0, base - 1)  # undercut shadow
            put(im, x, y, pal[base])
    for _ in range(5):  # chipped facets: a light pixel over a dark one, off the grid
        x, y = rnd.randrange(16), rnd.randrange(16)
        put(im, x, y, pal[4])
        put(im, x + 1, y + 1, pal[0])
    return im


def stone_texture(seed, pal, lumps=True):
    """A boulder surface: mottled, with a few lit chips and dark pits; tileable."""
    rnd = random.Random(seed)
    im = new()
    n = wrap_noise(rnd, 4)
    n2 = wrap_noise(rnd, 8)
    for y in range(16):
        for x in range(16):
            v = n[y][x] * 0.6 + n2[y][x] * 0.4
            i = 0 if v < 0.3 else 1 if v < 0.5 else 2 if v < 0.68 else 3
            put(im, x, y, pal[i])
    if lumps:
        for _ in range(7):
            x, y = rnd.randrange(16), rnd.randrange(16)
            put(im, x, y, pal[4])
            put(im, x + 1, y, pal[3])
            put(im, x, y + 1, pal[0])
    return im


ROCK_PAL = [hexc(0x4E4740), hexc(0x655D54), hexc(0x7A7166), hexc(0x90867A), hexc(0xA89E90)]
SAND_ROCK_PAL = [hexc(0x7A6448), hexc(0x917A5A), hexc(0xA88F6C), hexc(0xBCA580), hexc(0xD2BC96)]
HUGE_ROCK_PAL = [hexc(0x45403C), hexc(0x5A544E), hexc(0x6E6760), hexc(0x847C72), hexc(0x9C9488)]


# --- trees ----------------------------------------------------------------------------------------------

def bark(seed, pal):
    rnd = random.Random(seed)
    im = new()
    for x in range(16):
        col = pal[1 + (x * 7 + seed) % 2]
        for y in range(16):
            put(im, x, y, col)
    for _ in range(14):  # long vertical furrows and lit ridges
        x = rnd.randrange(16)
        y = rnd.randrange(16)
        ln = rnd.randrange(3, 7)
        c = pal[0] if rnd.random() < 0.6 else pal[3]
        for i in range(ln):
            put(im, x, y + i, c)
    return im


TRUNK_PAL = [hexc(0x2A1E16), hexc(0x3E2D20), hexc(0x4A3626), hexc(0x5E4632)]
DRY_PAL = [hexc(0x4A4038), hexc(0x635850), hexc(0x766A60), hexc(0x92867A)]


def bark_top(pal):
    im = new()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            put(im, x, y, pal[3] if d < 2 else pal[2] if d < 4.5 else pal[1] if d < 6.5 else pal[0])
    return im


LEAVES = {
    "green": [hexc(0x20381A), hexc(0x2E4E1E), hexc(0x3E6626), hexc(0x547E30), hexc(0x6C963C)],
    "olive": [hexc(0x38381A), hexc(0x4C4E20), hexc(0x62662A), hexc(0x7A7E34), hexc(0x949640)],
    "red": [hexc(0x3A1E14), hexc(0x56281A), hexc(0x723822), hexc(0x8E4C2C), hexc(0xA86638)],
}


def leaves(kind):
    """Clumps of leaves: round blobs lit top-left, with a few see-through gaps (cutout)."""
    pal = LEAVES[kind]
    rnd = random.Random({"green": 5, "olive": 6, "red": 7}[kind])
    im = new()
    for y in range(16):
        for x in range(16):
            put(im, x, y, pal[1])
    for _ in range(11):
        cx, cy = rnd.randrange(16), rnd.randrange(16)
        r = rnd.choice([2, 2.5, 3])
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                d = math.hypot(dx, dy)
                if d > r:
                    continue
                lit = (-dx - dy) / (2 * r) + 0.5  # top-left lit
                i = 2 + (1 if lit > 0.6 else 0) + (1 if lit > 0.85 and d < r - 0.7 else 0) - (1 if lit < 0.25 else 0)
                put(im, cx + dx, cy + dy, pal[max(0, min(4, i))])
    for _ in range(9):  # gaps
        x, y = rnd.randrange(16), rnd.randrange(16)
        im.putpixel((x, y), (0, 0, 0, 0))
        put(im, x + 1, y, pal[0])
    return im


def branches():
    """Bare twigs for the dry tree's crown (a cross model)."""
    rnd = random.Random(11)
    im = new()
    pal = DRY_PAL

    def line(x0, y0, x1, y1, c):
        n = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(n + 1):
            x = round(x0 + (x1 - x0) * i / n)
            y = round(y0 + (y1 - y0) * i / n)
            if 0 <= x < 16 and 0 <= y < 16:
                im.putpixel((x, y), pal[c] + (255,))

    line(8, 15, 8, 6, 1)
    line(7, 15, 7, 9, 0)
    line(8, 10, 3, 4, 2)
    line(8, 8, 13, 2, 2)
    line(5, 7, 4, 1, 3)
    line(11, 5, 14, 6, 1)
    line(8, 6, 9, 0, 3)
    line(3, 4, 1, 3, 1)
    return im


# --- the crash site -------------------------------------------------------------------------------------
HULL = [hexc(0x4E5458), hexc(0x737A80), hexc(0x9AA2A8), hexc(0xBEC4C8), hexc(0xD8DCDE)]
ORANGE = [hexc(0x8A4416), hexc(0xC4682A), hexc(0xE0904A)]
SCORCH = [hexc(0x221E1C), hexc(0x3A3430)]


def hull(side):
    rnd = random.Random(21 if side else 22)
    im = new()
    for y in range(16):
        for x in range(16):
            put(im, x, y, HULL[3] if (x + y) % 7 else HULL[2])
    for i in range(16):  # panel seams
        put(im, i, 7, HULL[1])
        put(im, 7, i, HULL[1])
        put(im, i, 8, HULL[4])
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13), (5, 10), (10, 5)]:  # rivets
        put(im, x, y, HULL[1])
    if side:  # Factorio-ish orange hazard stripe along the side
        for x in range(16):
            for y in (11, 12):
                put(im, x, y, ORANGE[1] if (x // 2) % 2 == 0 else HULL[0])
            put(im, x, 13, ORANGE[0])
    for _ in range(1):  # a scorch mark
        cx, cy = rnd.randrange(16), rnd.randrange(16)
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                if dx * dx + dy * dy <= 4 and rnd.random() < 0.6:
                    put(im, cx + dx, cy + dy, mix(HULL[1], SCORCH[1], 0.5) if dx * dx + dy * dy > 1 else SCORCH[1])
    return im


def debris():
    rnd = random.Random(31)
    im = new()
    for y in range(16):
        for x in range(16):
            put(im, x, y, HULL[2] if rnd.random() < 0.7 else HULL[1])
    for i in range(16):
        put(im, i, (i * 5) // 7, HULL[0])
        put(im, i, 3 + (i * 3) // 5, ORANGE[1] if i % 3 else ORANGE[0])
    for _ in range(10):
        put(im, rnd.randrange(16), rnd.randrange(16), SCORCH[rnd.randrange(2)])
    return im


def chest_face(front):
    im = new()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            put(im, x, y, HULL[0] if edge else HULL[1] if (x + y) % 5 else HULL[2])
    for x in range(1, 15):
        put(im, x, 4, ORANGE[1])
        put(im, x, 5, ORANGE[0])
    if front:  # a door with a latch
        for y in range(7, 14):
            put(im, 4, y, HULL[0])
            put(im, 11, y, HULL[0])
        for x in range(4, 12):
            put(im, x, 7, HULL[0])
            put(im, x, 13, HULL[0])
        put(im, 7, 10, ORANGE[2])
        put(im, 8, 10, ORANGE[2])
        put(im, 7, 11, ORANGE[0])
        put(im, 8, 11, ORANGE[0])
    return im


def chest_top():
    im = chest_face(False)
    for x in range(3, 13):  # a hatch
        put(im, x, 9, HULL[0])
        put(im, x, 13, HULL[0])
    for y in range(9, 14):
        put(im, 3, y, HULL[0])
        put(im, 12, y, HULL[0])
    return im


# --- items ----------------------------------------------------------------------------------------------

def wood_item():
    """Factorio's wood icon look: two short logs, lit end grain."""
    im = new()
    for (ox, oy) in [(1, 6), (5, 2)]:
        for i in range(10):  # a log lying diagonally
            for w in range(-2, 3):
                x, y = ox + i, oy + i // 2 + w + 3
                c = TRUNK_PAL[2] if w < 0 else TRUNK_PAL[1] if w < 2 else TRUNK_PAL[0]
                if w == -2:
                    c = TRUNK_PAL[3]
                if 0 <= x < 16 and 0 <= y < 16:
                    put(im, x, y, c)
        # end grain at the lower right end
        ex, ey = ox + 10, oy + 5 + 3
        for dy in range(-2, 3):
            for dx in range(-1, 2):
                if 0 <= ex + dx < 16 and 0 <= ey + dy < 16:
                    put(im, ex + dx, ey + dy, hexc(0xC89A62) if abs(dy) < 2 else hexc(0x9A7044))
        if 0 <= ex < 16 and 0 <= ey < 16:
            put(im, ex, ey, hexc(0x7A5432))
    return im


FISH_PAL = [hexc(0x1E2A34), hexc(0x34505E), hexc(0x4E7284), hexc(0x7A9CAC), hexc(0xB4CCD6), hexc(0xE4ECEE)]
FIN = [hexc(0x3C5662), hexc(0x5E7C88)]


def raw_fish_item():
    """A fish lying left to right: dark blue back, silver belly, forked tail, an eye."""
    im = new()
    for x in range(1, 13):
        t = (x - 1) / 11
        half = 3.2 * math.sin(math.pi * min(1, t * 1.15)) + 0.6
        for y in range(16):
            d = y - 8
            if abs(d + 0.5) > half:
                continue
            rel = (d + half) / (2 * half)
            c = FISH_PAL[1] if rel < 0.25 else FISH_PAL[2] if rel < 0.45 else FISH_PAL[3] if rel < 0.7 else FISH_PAL[4]
            put(im, x, y, c)
        top = int(8 - half)
        if 0 <= top < 16:
            put(im, x, top, FISH_PAL[0])
    for i in range(4):  # forked tail
        put(im, 13 + i // 2, 7 - i, FIN[0])
        put(im, 13 + i // 2, 8 + i, FIN[0])
        put(im, 13, 7, FIN[1])
        put(im, 13, 8, FIN[1])
    put(im, 3, 7, FISH_PAL[5])
    put(im, 3, 6, FISH_PAL[0])
    for x in (6, 8, 10):  # scale glints
        put(im, x, 9, FISH_PAL[5])
    return im


def fish_entity():
    """32x32 texture for NauvisFishRenderer's boxes (vanilla box UV layout)."""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    rnd = random.Random(41)

    def box(u, v, w, h, d, back, side, belly, front):
        def fill(x0, y0, ww, hh, f):
            for y in range(hh):
                for x in range(ww):
                    put(im, x0 + x, y0 + y, f(x, y, ww, hh))
        fill(u + d, v, w, d, lambda x, y, ww, hh: back)            # top
        fill(u + d + w, v, w, d, lambda x, y, ww, hh: belly)       # bottom
        for x0 in (u, u + d + w):                                   # sides
            fill(x0, v + d, d, h, side)
        fill(u + d, v + d, w, h, lambda x, y, ww, hh: front)        # north
        fill(u + d + w + d, v + d, w, h, lambda x, y, ww, hh: front)  # south

    def body_side(x, y, w, h):
        rel = y / max(1, h - 1)
        c = FISH_PAL[1] if rel < 0.2 else FISH_PAL[2] if rel < 0.45 else FISH_PAL[3] if rel < 0.75 else FISH_PAL[4]
        if rnd.random() < 0.12:
            c = FISH_PAL[5] if rel > 0.3 else FISH_PAL[0]
        return c

    box(0, 0, 3, 4, 8, FISH_PAL[1], body_side, FISH_PAL[4], FISH_PAL[2])

    def head_side(x, y, w, h):
        if x == 0 and y == 1:
            return FISH_PAL[5]  # eye highlight
        if x == 0 and y == 0:
            return FISH_PAL[0]
        return body_side(x, y, w, h)

    box(0, 12, 2, 3, 2, FISH_PAL[1], head_side, FISH_PAL[4], FISH_PAL[3])
    # fins are flat (width 0): only their side faces show; tail at (14, 12) d=4 h=5
    for (u, v, h, d) in [(14, 12, 5, 4), (22, 0, 2, 4), (22, 6, 1, 3)]:
        for x0 in (u, u + d):
            for y in range(h):
                for x in range(d):
                    fork = (u, v) == (14, 12) and x >= 2 and y == h // 2
                    if not fork:
                        put(im, x0 + x, v + d + y, FIN[1] if (x + y) % 2 else FIN[0])
    return im


# --- models ---------------------------------------------------------------------------------------------

def cube_all_rotated(name, tex=None, render_type=None):
    model = {"parent": "minecraft:block/cube_all", "textures": {"all": M + "block/" + (tex or name)}}
    if render_type:
        model["render_type"] = render_type
    write_json(os.path.join(MODELS, name + ".json"), model)
    write_json(os.path.join(STATES, name + ".json"),
               {"variants": {"": [{"model": M + "block/" + name, "y": r} if r else {"model": M + "block/" + name}
                                  for r in (0, 90, 180, 270)]}})


def single_state(name, model=None):
    write_json(os.path.join(STATES, name + ".json"), {"variants": {"": {"model": M + "block/" + (model or name)}}})


def el(f, t, tex, faces=("north", "south", "east", "west", "up", "down"), uv=None):
    out = {"from": f, "to": t, "faces": {}}
    for face in faces:
        fd = {"texture": tex}
        if uv:
            fd["uv"] = uv(face, f, t)
        out["faces"][face] = fd
    return out


def side_uv(face, f, t):
    """UVs that follow the element's position (like vanilla's auto UV, clamped to 0..16)."""
    if face in ("up", "down"):
        return [f[0], f[2], t[0], t[2]]
    if face in ("north", "south"):
        return [f[0], 16 - t[1], t[0], 16 - f[1]]
    return [f[2], 16 - t[1], t[2], 16 - f[1]]


def trunk_model(name, tex, top, w):
    lo, hi = 8 - w / 2, 8 + w / 2
    write_json(os.path.join(MODELS, name + ".json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": M + "block/" + tex, "bark": M + "block/" + tex, "top": M + "block/" + top},
        "elements": [{"from": [lo, 0, lo], "to": [hi, 16, hi], "faces": {
            "north": {"texture": "#bark", "uv": [lo, 0, hi, 16]}, "south": {"texture": "#bark", "uv": [lo, 0, hi, 16]},
            "east": {"texture": "#bark", "uv": [lo, 0, hi, 16]}, "west": {"texture": "#bark", "uv": [lo, 0, hi, 16]},
            "up": {"texture": "#top", "uv": [lo, lo, hi, hi]}, "down": {"texture": "#top", "uv": [lo, lo, hi, hi]}}}],
    })
    single_state(name)


def dry_branches_model():
    """Solid, forked limbs read as a dead tree from every angle, including a low first-person view."""
    els = [el([6.5, 0, 6.5], [9.5, 13, 9.5], "#bark", uv=side_uv)]
    for axis, angle, origin, start, end in [
        ("z", 45, [8, 3, 8], [6.8, 3, 6.8], [9.2, 16, 9.2]),
        ("z", -45, [8, 5, 8], [7, 5, 7], [9, 16, 9]),
        ("x", 45, [8, 7, 8], [7.2, 7, 7.2], [8.8, 15, 8.8]),
    ]:
        part = el(start, end, "#bark", uv=side_uv)
        part["rotation"] = {"angle": angle, "axis": axis, "origin": origin}
        els.append(part)
    write_json(os.path.join(MODELS, "dry_tree_branches.json"), {
        "parent": "minecraft:block/block", "textures": {
            "bark": M + "block/dry_tree_bark", "particle": M + "block/dry_tree_bark"}, "elements": els})
    single_state("dry_tree_branches")


def rock_model(name, tex, parts):
    # only the base has a bottom face: the others stand on it or on the ground beside it
    els = [el(f, t, "#rock", uv=side_uv) if i == 0 else el(f, t, "#rock", ("north", "south", "east", "west", "up"), uv=side_uv)
           for i, (f, t) in enumerate(parts)]
    write_json(os.path.join(MODELS, name + ".json"),
               {"parent": "minecraft:block/block", "textures": {"particle": M + "block/" + tex, "rock": M + "block/" + tex},
                "elements": els})
    write_json(os.path.join(STATES, name + ".json"),
               {"variants": {"": [{"model": M + "block/" + name, "y": r} if r else {"model": M + "block/" + name}
                                  for r in (0, 90, 180, 270)]}})


# boulders: a wide base, a narrower top and a side knob; no two faces share a plane
BIG_ROCK = [([2, 0, 2], [14, 6.5, 13]), ([3.5, 6.5, 3], [12.5, 10.5, 11.5]), ([9, 0, 10.5], [14.5, 5, 14.5]),
            ([1, 0, 4], [2.5, 4, 10]), ([5, 10.5, 4.5], [10, 11.5, 9.5])]
SAND_ROCK = [([1.5, 0, 2.5], [13.5, 5, 13.5]), ([3, 5, 4], [11, 8.5, 11]), ([10, 0, 1], [14.5, 3.5, 6])]


def main():
    for d in (TEX, ITEM_TEX, ENTITY_TEX, MODELS, ITEM_MODELS, STATES):
        os.makedirs(d, exist_ok=True)
    textures = {}
    for name in GROUND:
        textures[name] = ground_tile(name)
        cube_all_rotated(name)
        write_json(os.path.join(ITEM_MODELS, name + ".json"), {"parent": M + "block/" + name})
    textures["cliff_rock"] = cliff_rock()
    cube_all_rotated("cliff_rock")
    textures["huge_rock"] = stone_texture(91, HUGE_ROCK_PAL)
    cube_all_rotated("huge_rock")
    textures["big_rock"] = stone_texture(92, ROCK_PAL)
    rock_model("big_rock", "big_rock", BIG_ROCK)
    textures["sand_rock"] = stone_texture(93, SAND_ROCK_PAL)
    rock_model("sand_rock", "sand_rock", SAND_ROCK)

    textures["tree_bark"] = bark(3, TRUNK_PAL)
    textures["tree_bark_top"] = bark_top(TRUNK_PAL)
    textures["dry_tree_bark"] = bark(4, DRY_PAL)
    textures["dry_tree_bark_top"] = bark_top(DRY_PAL)
    trunk_model("tree_trunk", "tree_bark", "tree_bark_top", 6)
    trunk_model("dry_tree_trunk", "dry_tree_bark", "dry_tree_bark_top", 5)
    for kind in LEAVES:
        textures["tree_leaves_" + kind] = leaves(kind)
        cube_all_rotated("tree_leaves_" + kind, render_type="minecraft:cutout_mipped")
    textures["dry_tree_branches"] = branches()
    dry_branches_model()

    textures["ship_wreck_hull_side"] = hull(True)
    textures["ship_wreck_hull_top"] = hull(False)
    write_json(os.path.join(MODELS, "ship_wreck_hull.json"),
               {"parent": "minecraft:block/cube_bottom_top", "textures": {
                   "side": M + "block/ship_wreck_hull_side", "top": M + "block/ship_wreck_hull_top",
                   "bottom": M + "block/ship_wreck_hull_top"}})
    single_state("ship_wreck_hull")
    textures["ship_wreck_debris"] = debris()
    write_json(os.path.join(MODELS, "ship_wreck_debris.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": M + "block/ship_wreck_debris", "d": M + "block/ship_wreck_debris"},
        "elements": [
            el([1, 0, 3], [14, 1.5, 13], "#d", uv=side_uv),
            {**el([3, 1.5, 4], [12, 3, 7], "#d", uv=side_uv), "rotation": {"angle": 22.5, "axis": "x", "origin": [8, 1.5, 4]}},
            {**el([4, 0.5, 9], [10, 5.5, 10.5], "#d", uv=side_uv), "rotation": {"angle": -22.5, "axis": "y", "origin": [7, 0, 9]}},
        ]})
    write_json(os.path.join(STATES, "ship_wreck_debris.json"),
               {"variants": {"": [{"model": M + "block/ship_wreck_debris", "y": r} if r else {"model": M + "block/ship_wreck_debris"}
                                  for r in (0, 90, 180, 270)]}})
    textures["crash_site_chest_front"] = chest_face(True)
    textures["crash_site_chest_side"] = chest_face(False)
    textures["crash_site_chest_top"] = chest_top()
    write_json(os.path.join(MODELS, "crash_site_chest.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": M + "block/crash_site_chest_side", "front": M + "block/crash_site_chest_front",
                     "side": M + "block/crash_site_chest_side", "top": M + "block/crash_site_chest_top"},
        "elements": [{"from": [1, 0, 1], "to": [15, 13, 15], "faces": {
            "north": {"texture": "#front", "uv": [1, 3, 15, 16]}, "south": {"texture": "#side", "uv": [1, 3, 15, 16]},
            "east": {"texture": "#side", "uv": [1, 3, 15, 16]}, "west": {"texture": "#side", "uv": [1, 3, 15, 16]},
            "up": {"texture": "#top", "uv": [1, 1, 15, 15]}, "down": {"texture": "#side", "uv": [1, 1, 15, 15]}}}]})
    write_json(os.path.join(STATES, "crash_site_chest.json"), {"variants": {
        f"facing={f}": ({"model": M + "block/crash_site_chest", "y": r} if r else {"model": M + "block/crash_site_chest"})
        for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})

    for name, im in textures.items():
        im.save(os.path.join(TEX, name + ".png"))

    wood_item().save(os.path.join(ITEM_TEX, "wood.png"))
    write_json(os.path.join(ITEM_MODELS, "wood.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": M + "item/wood"}})
    raw_fish_item().save(os.path.join(ITEM_TEX, "raw_fish.png"))
    write_json(os.path.join(ITEM_MODELS, "raw_fish.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": M + "item/raw_fish"}})
    fish_entity().save(os.path.join(ENTITY_TEX, "fish.png"))

    preview(textures)


def preview(textures):
    """art/world-preview.png: every texture at 4x, plus 3x3 ground patches to check tiling."""
    names = list(textures)
    cols = 8
    cell = 16 * 4 + 8
    rows = (len(names) + cols - 1) // cols
    tile_rows = (len(GROUND) + 3) // 4
    sheet = Image.new("RGBA", (cols * cell, rows * cell + tile_rows * (48 * 3 + 8) + 80 + 8), (40, 42, 46, 255))
    for i, n in enumerate(names):
        im = textures[n].resize((64, 64), Image.NEAREST)
        sheet.alpha_composite(im, ((i % cols) * cell + 4, (i // cols) * cell + 4))
    y0 = rows * cell
    for i, n in enumerate(GROUND):
        t = textures[n].resize((48, 48), Image.NEAREST)
        for dy in range(3):
            for dx in range(3):
                sheet.alpha_composite(t.rotate(90 * ((dx * 7 + dy * 3) % 4)),
                                      ((i % 4) * (48 * 3 + 8) + dx * 48 + 4, y0 + (i // 4) * (48 * 3 + 8) + dy * 48 + 4))
    yi = y0 + tile_rows * (48 * 3 + 8)
    for i, im in enumerate([wood_item(), raw_fish_item()]):
        sheet.alpha_composite(im.resize((64, 64), Image.NEAREST), (4 + i * 72, yi + 8))
    sheet.alpha_composite(fish_entity().resize((64, 64), Image.NEAREST), (4 + 2 * 72, yi + 8))
    sheet.save(os.path.join(HERE, "world-preview.png"))


if __name__ == "__main__":
    main()
