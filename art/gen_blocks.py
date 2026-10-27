"""Procedural 16x16 block textures for Minetorio (vanilla-like palette, Create-style panels).

Writes straight into src/main/resources/assets/minetorio/textures/block/ (plus .mcmeta for animated
strips) and a contact sheet into art/blocks-preview.png. Deterministic: re-run after any edit.

    python art/gen_blocks.py
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "minetorio", "textures", "block")


def C(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def P(*hs):
    return [C(h) for h in hs]


# light -> dark ramps
STEEL = P("#e2e5e6", "#c3c8ca", "#a6acaf", "#8a9094", "#6c7276", "#4c5154")
DARK = P("#7a8085", "#62686d", "#4f5459", "#3f4347", "#303336", "#222426")
BRASS = P("#fbe08a", "#e9bb48", "#c9952c", "#9a6c1c", "#674510")
COPPER = P("#f6b08a", "#e0875a", "#bb6236", "#8c4220", "#5c2912")
RUST = P("#d29a64", "#b17544", "#8e5a31", "#6a4022", "#472915")
STONE = P("#a9a9a9", "#959595", "#858585", "#767676", "#666666", "#4f4f4f")
YELLOW = P("#ffe27a", "#f2c12e", "#d29f13", "#9c740b", "#5e4506")
RED = P("#ff9585", "#e8513f", "#c23426", "#8a2018", "#55120c")
GREEN = P("#a6f08a", "#5fd04a", "#3fa336", "#2a7524", "#174513")
BLUE = P("#a4d6ff", "#55a2f2", "#3178cc", "#215192", "#132f57")
WOOD = P("#b7834f", "#9c6b3c", "#83582f", "#6a4524", "#4d3018")
BLACK = C("#141414")
TIERS = {"": YELLOW, "fast_": RED, "express_": BLUE}

blocks = {}       # name -> Image (16 x 16*frames)
anim = {}         # name -> mcmeta dict


def new(fill=(0, 0, 0, 0), h=16):
    return Image.new("RGBA", (16, h), fill)


def px(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c)


def rect(im, x0, y0, x1, y1, c):
    ImageDraw.Draw(im).rectangle([x0, y0, x1, y1], fill=c)


def noisy(pal, seed, lo=1, hi=3, weights=(0.1, 0.8, 0.1)):
    """Seamless-ish plate: pixels from pal[lo..hi] with mild clustering."""
    rnd = random.Random(seed)
    im = new()
    choices = list(range(lo, hi + 1))
    for y in range(16):
        for x in range(16):
            k = rnd.choices(choices, weights=weights[: len(choices)])[0]
            px(im, x, y, pal[k])
    return im


def bevel(im, pal, light=0, dark=4):
    """1 px panel edge: light top/left, dark bottom/right (Create-style plate seams)."""
    for i in range(16):
        px(im, i, 0, pal[light]); px(im, 0, i, pal[light])
        px(im, i, 15, pal[dark]); px(im, 15, i, pal[dark])
    px(im, 0, 15, pal[dark - 1]); px(im, 15, 0, pal[dark - 1])


def rivet(im, x, y, pal):
    px(im, x, y, pal[0]); px(im, x + 1, y, pal[2]); px(im, x, y + 1, pal[2]); px(im, x + 1, y + 1, pal[4])


def rivets(im, pal, inset=2):
    for (x, y) in ((inset, inset), (14 - inset, inset), (inset, 14 - inset), (14 - inset, 14 - inset)):
        rivet(im, x, y, pal)


def panel(pal, seed, with_rivets=True):
    im = noisy(pal, seed)
    bevel(im, pal)
    if with_rivets:
        rivets(im, pal)
    return im


# ---------------------------------------------------------------- metals
blocks["steel"] = noisy(STEEL, 1)
blocks["steel_panel"] = panel(STEEL, 2)
blocks["dark"] = noisy(DARK, 3)
blocks["dark_panel"] = panel(DARK, 4)
blocks["brass"] = noisy(BRASS, 5)
blocks["copper"] = noisy(COPPER, 6)
blocks["rust"] = noisy(RUST, 7)
blocks["rust_panel"] = panel(RUST, 8)
for pre, pal in TIERS.items():
    name = {"": "yellow", "fast_": "red", "express_": "blue"}[pre]
    blocks["tier_" + name] = noisy(pal, 9 + len(pre))
    blocks["tier_" + name + "_panel"] = panel(pal, 10 + len(pre))
blocks["tier_green"] = noisy(GREEN, 17)

# brass/copper bands look better with a sheen row
for n, pal in (("brass", BRASS), ("copper", COPPER)):
    im = blocks[n]
    for x in range(16):
        px(im, x, 4, pal[0]); px(im, x, 11, pal[3])

# vent grille: dark frame, horizontal slots
im = panel(DARK, 11, with_rivets=False)
for y in (3, 6, 9, 12):
    rect(im, 2, y, 13, y, BLACK)
    rect(im, 2, y + 1, 13, y + 1, DARK[1])
blocks["vent"] = im

# hazard stripes
im = new()
for y in range(16):
    for x in range(16):
        px(im, x, y, YELLOW[1] if ((x + y) // 4) % 2 == 0 else DARK[4])
blocks["hazard"] = im

# window: dark glass in a steel frame with a diagonal highlight
im = panel(STEEL, 12, with_rivets=False)
rect(im, 2, 2, 13, 13, DARK[4])
rect(im, 3, 3, 12, 12, C("#2c4654"))
for i in range(4):
    px(im, 4 + i, 8 - i, C("#7fb3c8")); px(im, 5 + i, 8 - i, C("#4f7f94"))
px(im, 10, 4, C("#9fd3e6"))
blocks["window"] = im

# machine panel with a vent window in the middle (assemblers)
im = panel(STEEL, 13)
rect(im, 4, 4, 11, 11, DARK[4])
for y in (5, 7, 9):
    rect(im, 5, y, 10, y, BLACK)
    rect(im, 5, y + 1, 10, y + 1, DARK[2])
blocks["assembler_panel"] = im

# assembler housings: greyish beige (tier 1) and blue-grey slate (tier 2), plain and with a vent
BEIGE = P("#e6dfcf", "#cdc4ae", "#b3a98f", "#978d75", "#79705a", "#56503f")
SLATE = P("#d4dce6", "#b2bdcb", "#93a1b3", "#778598", "#5a677a", "#3f4a5a")
for n, pal, seed in (("beige", BEIGE, 80), ("slate", SLATE, 84)):
    blocks[n] = noisy(pal, seed)
    blocks[n + "_panel"] = panel(pal, seed + 1)
    im = panel(pal, seed + 2, with_rivets=False)
    rect(im, 3, 3, 12, 12, DARK[4])
    for y in (4, 6, 8, 10):
        rect(im, 4, y, 11, y, BLACK)
        rect(im, 4, y + 1, 11, y + 1, DARK[2])
    blocks[n + "_vent"] = im

# lab panel: three science lights
im = panel(STEEL, 14)
rect(im, 3, 9, 12, 12, DARK[4])
for x, col in ((4, C("#e8483a")), (7, C("#5fd04a")), (10, C("#55a2f2"))):
    rect(im, x, 10, x + 1, 11, col)
    px(im, x, 10, (min(col[0] + 70, 255), min(col[1] + 70, 255), min(col[2] + 70, 255), 255))
rect(im, 3, 3, 12, 6, DARK[3])
rect(im, 4, 4, 11, 5, C("#2c4654"))
px(im, 5, 4, C("#9fd3e6"))
blocks["lab_panel"] = im

# gear faces
im = noisy(STEEL, 16, 2, 4)
for i in range(16):
    px(im, i, 7, STEEL[5]); px(im, 7, i, STEEL[5])
rect(im, 6, 6, 9, 9, STEEL[1]); rect(im, 7, 7, 8, 8, DARK[5])
blocks["gear_hub"] = im

# drill bit: helical stripes (shows the spin)
im = new()
for y in range(16):
    for x in range(16):
        k = (x + y * 2) % 8
        px(im, x, y, STEEL[1] if k < 2 else STEEL[3] if k < 5 else DARK[2])
blocks["drill_bit"] = im

# ---------------------------------------------------------------- stone furnace
rnd = random.Random(20)
im = new(STONE[2])
rows = [(0, 4), (5, 9), (10, 15)]
for ri, (y0, y1) in enumerate(rows):
    x = -3 if ri % 2 else 0
    while x < 16:
        w = rnd.choice((5, 6, 7))
        x1 = min(x + w - 1, 15)
        x0 = max(x, 0)
        base = rnd.choice((1, 2, 2, 3))
        rect(im, x0, y0, x1, y1 - 1, STONE[base])
        rect(im, x0, y0, x1, y0, STONE[base - 1])            # lit top edge
        rect(im, x0, y1 - 1, x1, y1 - 1, STONE[base + 1])     # shaded bottom
        for _ in range(2):
            px(im, rnd.randint(x0, x1), rnd.randint(y0 + 1, y1 - 2), STONE[base + 1])
        if x1 < 15:
            rect(im, x1 + 1 if x1 + 1 < 16 else 15, y0, x1 + 1 if x1 + 1 < 16 else 15, y1 - 1, STONE[5])
        x += w + 1
    rect(im, 0, y1, 15, y1, STONE[5]) if y1 < 15 else None
blocks["furnace_stone"] = im

# furnace mouth (unlit): dark opening with a grate
im = new(C("#1d1714"))
rect(im, 0, 0, 15, 0, STONE[5]); rect(im, 0, 15, 15, 15, STONE[5])
for x in (2, 6, 10, 14):
    rect(im, x, 9, x, 14, DARK[1])
rect(im, 1, 9, 14, 9, DARK[0])
for x, y in ((4, 12), (8, 13), (12, 12)):
    px(im, x, y, C("#3a2a22"))
blocks["furnace_mouth"] = im


def fire_frame(t, seed=31):
    rnd = random.Random(seed + t * 7)
    im = new(C("#1d1714"))
    heights = [6 + int(4 * (0.5 + 0.5 * math.sin(x * 1.3 + t * 1.7))) + rnd.randint(-1, 1) for x in range(16)]
    for x in range(16):
        top = 15 - heights[x]
        for y in range(max(top, 1), 15):
            d = (y - top) / max(heights[x], 1)
            col = C("#fff2a0") if d > 0.75 else C("#ffc23a") if d > 0.45 else C("#ff7a1a") if d > 0.2 else C("#c23a12")
            px(im, x, y, col)
    for x in (2, 6, 10, 14):
        rect(im, x, 12, x, 14, DARK[2])
    rect(im, 1, 12, 14, 12, DARK[1])
    rect(im, 0, 0, 15, 0, STONE[5]); rect(im, 0, 15, 15, 15, STONE[5])
    return im


strip = new(h=16 * 8)
for t in range(8):
    strip.paste(fire_frame(t), (0, 16 * t))
blocks["fire"] = strip
anim["fire"] = {"animation": {"frametime": 2}}

# ---------------------------------------------------------------- boiler / engine
im = noisy(DARK, 40, 0, 2, (0.3, 0.45, 0.25))
for y in (0, 15):
    rect(im, 0, y, 15, y, DARK[4])
for y in (3, 12):
    for x in range(1, 16, 3):
        rivet(im, x, y, DARK)
rect(im, 0, 7, 15, 8, DARK[3])
rect(im, 0, 7, 15, 7, DARK[1])
blocks["boiler_shell"] = im

# engine cylinder: copper with brass straps
im = blocks["copper"].copy()
for x in (0, 1, 14, 15):
    for y in range(16):
        px(im, x, y, BRASS[2] if x in (1, 14) else BRASS[3])
blocks["cylinder"] = im

# ---------------------------------------------------------------- pipes
def pipe_tex(vertical):
    im = new()
    shade = {4: STEEL[4], 5: STEEL[3], 6: STEEL[1], 7: STEEL[0], 8: STEEL[1], 9: STEEL[2], 10: STEEL[3], 11: STEEL[4]}
    for a in range(16):
        k = shade.get(a, STEEL[2] if 3 <= a <= 12 else STEEL[4])
        for b in range(16):
            col = k
            if b in (0, 15):
                col = STEEL[5]
            if vertical:
                px(im, a, b, col)
            else:
                px(im, b, a, col)
    return im


blocks["pipe_h"] = pipe_tex(False)
blocks["pipe_v"] = pipe_tex(True)
im = panel(STEEL, 41, with_rivets=False)
for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12)):
    rivet(im, x, y, STEEL)
blocks["flange"] = im
im = new(STEEL[3])
ImageDraw.Draw(im).ellipse([2, 2, 13, 13], fill=STEEL[2], outline=STEEL[5])
ImageDraw.Draw(im).ellipse([5, 5, 10, 10], fill=C("#1b2a33"))
px(im, 6, 6, C("#3a5a6a"))
blocks["pipe_end"] = im

# ---------------------------------------------------------------- pole
rnd = random.Random(50)
im = new()
for x in range(16):
    base = WOOD[1 + (x * 7 % 3)]
    for y in range(16):
        px(im, x, y, base)
for _ in range(10):
    x, y = rnd.randint(0, 15), rnd.randint(0, 13)
    px(im, x, y, WOOD[3]); px(im, x, y + 1, WOOD[3])
blocks["pole_wood"] = im
im = new(WOOD[2])
ImageDraw.Draw(im).ellipse([1, 1, 14, 14], outline=WOOD[0])
ImageDraw.Draw(im).ellipse([4, 4, 11, 11], outline=WOOD[3])
px(im, 7, 7, WOOD[4])
blocks["pole_wood_top"] = im
im = new()
INS = P("#f2f2e8", "#d8dccf", "#b8c0b0", "#8e988a")
for y in range(16):
    for x in range(16):
        px(im, x, y, INS[1] if y % 3 else INS[3])
for y in range(0, 16, 3):
    rect(im, 0, y + 1, 15, y + 1, INS[0])
blocks["insulator"] = im

# ---------------------------------------------------------------- lab dome
im = new(C("#3c8fa8"))
for y in range(16):
    for x in range(16):
        if (x + y) % 7 == 0:
            px(im, x, y, C("#5fb1c9"))
rect(im, 3, 2, 5, 4, C("#b8eef8"))
px(im, 3, 2, C("#ffffff"))
blocks["lab_glass"] = im


def glow_frame(t):
    level = 0.5 + 0.5 * math.sin(t / 8 * 2 * math.pi)
    lo = (40, 150, 190)
    hi = (190, 245, 255)
    base = tuple(int(lo[i] + (hi[i] - lo[i]) * level) for i in range(3)) + (255,)
    core = tuple(min(255, c + 50) for c in base[:3]) + (255,)
    im = new(base)
    for y in range(16):
        for x in range(16):
            if (x + y + t) % 7 == 0:
                px(im, x, y, core)
    rect(im, 3, 2, 5, 4, C("#ffffff"))
    return im


strip = new(h=16 * 8)
for t in range(8):
    strip.paste(glow_frame(t), (0, 16 * t))
blocks["lab_glow"] = strip
anim["lab_glow"] = {"animation": {"frametime": 3}}

# ---------------------------------------------------------------- belts
# Travel is toward the top of the image (model north). Chevrons point up; the pattern period is
# 16 px so straight tiles and quarter-circle curves (one period along the arc) join seamlessly and
# all three tiers stay alias-free. Speed = Factorio speed at 20 tps: 1.5 / 3 / 4.5 px per tick.
BELT_BASE = P("#5a5d60", "#4a4d50", "#3c3e41", "#2e3032")
BELT_TIERS = {"": (YELLOW, 1.5, 32), "fast_": (RED, 3.0, 16), "express_": (BLUE, 4.5, 32)}


def chevron(s, c, pal, bend=0.9):
    """Pattern at along-coordinate s (px, increasing in travel direction) and across c (0..16)."""
    d = abs(c - 8)
    if d > 5.5:
        return slat(s)
    ph = (s + d * bend) % 16  # chevron apex leads (points forward)
    if 0 <= ph < 1.6:
        return pal[1]
    if 1.6 <= ph < 2.6:
        return pal[2]
    return slat(s)


def slat(s):
    """Belt surface: one dark joint per 16 px (finer joints would alias at the faster tiers)."""
    ph = s % 16
    if 8 <= ph < 9:
        return BELT_BASE[3]
    if 9 <= ph < 10:
        return BELT_BASE[0]
    return BELT_BASE[1]


def straight_frame(pal, shift):
    im = new()
    for v in range(16):
        for x in range(16):
            s = (15.5 - v) - shift
            col = chevron(s, x + 0.5, pal)
            if x in (0, 15):
                col = pal[3]
            elif x in (1, 14):
                col = BELT_BASE[3]
            px(im, x, v, col)
    return im


def curve_frame(pal, shift):
    """curve_left (facing north): pivot at the top-left corner, entry on the left edge, exit on top."""
    im = new()
    for v in range(16):
        for x in range(16):
            fx, fz = (x + 0.5) / 16, (v + 0.5) / 16
            r = math.hypot(fx, fz)
            theta = math.atan2(fx, fz)  # 0 at the left edge (entry), pi/2 at the top edge (exit)
            c = r * 16
            if c > 16.0:
                col = pal[3]
            elif c > 15 or c < 1:
                col = pal[3] if (c > 15.5 or c < 0.5) else BELT_BASE[3]
            else:
                s = theta / (math.pi / 2) * 16 - shift
                col = chevron(s, c, pal, bend=0.0)
            px(im, x, v, col)
    return im


for pre, (pal, speed, frames) in BELT_TIERS.items():
    for kind, fn in (("", straight_frame), ("_curve", curve_frame)):
        strip = new(h=16 * frames)
        for k in range(frames):
            strip.paste(fn(pal, (k * speed) % 16), (0, 16 * k))
        name = pre + "transport_belt" + kind
        blocks[name] = strip
        anim[name] = {"animation": {"frametime": 1}}

# underground hood / splitter panels: tier panel with a chevron decal
for pre, pal in TIERS.items():
    im = panel(pal, 60 + len(pre), with_rivets=False)
    for i in range(5):
        for (x, y) in ((7 - i, 4 + i), (8 + i, 4 + i)):
            px(im, x, y, DARK[4]); px(im, x, y + 1, DARK[4])
            px(im, x, y + 4, DARK[4]); px(im, x, y + 5, DARK[4])
    blocks[pre + "belt_arrow"] = im
    im = panel(pal, 70 + len(pre))
    rect(im, 3, 3, 12, 12, DARK[3])
    rect(im, 4, 4, 11, 11, DARK[4])
    for x in (5, 7, 9):
        rect(im, x, 5, x, 10, DARK[2])
    blocks[pre + "belt_motor"] = im

# ---------------------------------------------------------------- write
os.makedirs(OUT, exist_ok=True)
for n, im in blocks.items():
    assert im.width == 16 and im.height % 16 == 0, n
    im.save(os.path.join(OUT, n + ".png"))
    meta = os.path.join(OUT, n + ".png.mcmeta")
    if n in anim:
        with open(meta, "w") as f:
            json.dump(anim[n], f)
    elif os.path.exists(meta):
        os.remove(meta)

S = 6
cw, ch = 16 * S + 8, 16 * S + 18
cols = 10
rows = (len(blocks) + cols - 1) // cols
sheet = Image.new("RGBA", (cols * cw, rows * ch), (58, 58, 66, 255))
d = ImageDraw.Draw(sheet)
font = ImageFont.load_default()
for i, (n, im) in enumerate(blocks.items()):
    x, y = (i % cols) * cw + 4, (i // cols) * ch + 2
    sheet.alpha_composite(im.crop((0, 0, 16, 16)).resize((16 * S, 16 * S), Image.NEAREST), (x, y))
    d.text((x, y + 16 * S + 2), n[:17], fill=(235, 235, 235, 255), font=font)
sheet.save(os.path.join(HERE, "blocks-preview.png"))
print(len(blocks), "textures")
