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
PURPLE = P("#d6a8ff", "#a866e8", "#8a3ac8", "#5e2490", "#361257")
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
blocks["tier_green_panel"] = panel(GREEN, 18)  # stack inserter housing
blocks["tier_purple"] = noisy(PURPLE, 19)  # filter inserter
blocks["tier_purple_panel"] = panel(PURPLE, 20)

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

# ---------------------------------------------------------------- stone furnace (bricks: furnace_stone below)

# furnace mouth (unlit): dark opening with a grate
im = new(C("#1d1714"))
rect(im, 0, 0, 15, 0, STONE[5]); rect(im, 0, 15, 15, 15, STONE[5])
for x in (2, 6, 10, 14):
    rect(im, x, 9, x, 14, DARK[1])
rect(im, 1, 9, 14, 9, DARK[0])
for x, y in ((4, 12), (8, 13), (12, 12)):
    px(im, x, y, C("#3a2a22"))
blocks["furnace_mouth"] = im


# (the fire itself is furnace_fire, in the burner/steam machine section below)

# ---------------------------------------------------------------- engine
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

# ---------------------------------------------------------------- Factorio reference look (polish P6)
# Colours picked from the wiki's entity images (see art/README.md: every machine is checked side by
# side against its reference in art/render_compare.py).


def lattice(base, line, seed, glass_hi):
    """Geodesic dome glass, one lattice cell per texture (each dome face shows the whole texture): bright
    opaque struts along the edges and one diagonal, so neighbouring faces form triangles, over see-through
    glass (base / glass_hi carry their alpha) with a few highlight pixels."""
    rnd = random.Random(seed)
    im = new(base)
    for y in range(16):
        for x in range(16):
            if rnd.random() < 0.12:
                px(im, x, y, glass_hi)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 1, 14, 15) or x == y:
                px(im, x, y, line)
    return im


# Factorio 1.1 lab: brass struts over smoky grey glass (idle) / glass lit blue from inside (researching)
blocks["lab_dome_glass"] = lattice(C("#2a3238", 165), C("#c9a464"), 90, C("#7d8f99", 175))


def dome_glow_frame(t):
    level = 0.5 + 0.5 * math.sin(t / 8 * 2 * math.pi)
    lo, hi = (30, 70, 190), (90, 165, 255)
    glass = tuple(int(lo[i] + (hi[i] - lo[i]) * level) for i in range(3)) + (180,)
    sparkle = tuple(min(255, c + 60) for c in glass[:3]) + (200,)
    return lattice(glass, C("#f0cf86"), 90, sparkle)


strip = new(h=16 * 8)
for t in range(8):
    strip.paste(dome_glow_frame(t), (0, 16 * t))
blocks["lab_dome_glow"] = strip
anim["lab_dome_glow"] = {"animation": {"frametime": 3}}

# assembling machines (after the Factorio 1.1 sprites): a sloped skirt of riveted, ribbed panels with
# grime at the foot (grey-green tier 1, blue tier 2), a darker housing with plate seams and a recessed
# copper-brown deck crowded with machinery (rails, a gauge, a control box, a lamp, a light chevron plate).
GREYGREEN = P("#b4bdb0", "#959f92", "#7b8578", "#646d62", "#4c544b", "#363c36")
# the housing above the skirt is darker than the skirt in both tiers
HOUSING = {"asm1": P("#7b8578", "#646d62", "#565e54", "#4c544b", "#3e453d", "#2f342e"),
           "asm2": P("#778598", "#5a677a", "#4c586a", "#3f4a5a", "#323b48", "#252c36")}
for n, pal, seed in (("asm1", GREYGREEN, 100), ("asm2", BLUE, 104)):
    rnd = random.Random(seed)
    im = noisy(pal, seed, 2, 3, (0.6, 0.4))
    # two ribbed panels per face, each with a raised frame and a darker sunken middle
    for x0 in (0, 8):
        rect(im, x0 + 2, 3, x0 + 5, 12, pal[3])
        rect(im, x0 + 2, 3, x0 + 5, 3, pal[4]); rect(im, x0 + 2, 3, x0 + 2, 12, pal[4])
        rect(im, x0 + 3, 12, x0 + 5, 12, pal[1]); rect(im, x0 + 5, 4, x0 + 5, 12, pal[2])
        for y in (5, 8, 11):                      # pressed ribs
            px(im, x0 + 3, y, pal[2]); px(im, x0 + 4, y, pal[2])
        rect(im, x0, 0, x0, 15, pal[-1]); rect(im, x0 + 1, 0, x0 + 1, 15, pal[1])   # seam
        rect(im, x0 + 7, 0, x0 + 7, 15, pal[-2])
        for y in (1, 13):                         # rivets top and bottom
            px(im, x0 + 3, y, pal[0]); px(im, x0 + 4, y, pal[-2])
    rect(im, 0, 0, 15, 0, pal[0]); rect(im, 0, 15, 15, 15, pal[-1])
    for x in range(16):                           # grime and rust at the foot
        if rnd.random() < 0.55:
            px(im, x, 14, RUST[3] if rnd.random() < 0.4 else pal[-2])
        if rnd.random() < 0.25:
            px(im, x, 13, RUST[2])
    for _ in range(4):                            # scratches
        px(im, rnd.randint(2, 13), rnd.randint(2, 12), pal[0])
    blocks[n + "_skirt"] = im
    h = HOUSING[n]
    im = panel(h, seed + 1)
    rect(im, 1, 7, 14, 7, h[5]); rect(im, 1, 8, 14, 8, h[1])          # plate seam
    for x in (4, 11):
        px(im, x, 6, h[0]); px(im, x, 9, h[0])                         # seam rivets
    for _ in range(5):
        px(im, rnd.randint(2, 13), rnd.randint(2, 13), h[0])
    for k in range(rnd.randint(3, 5)):                                 # a rust streak under a rivet
        px(im, 12, 4 + k, RUST[3 if k else 2])
    blocks[n + "_body"] = im

DECK = P("#a08868", "#86704f", "#6c583c", "#56452e", "#403322", "#2c2318")
rnd = random.Random(108)
im = noisy(DECK, 108, 1, 3)
for (x0, y0, x1, y1) in ((0, 0, 7, 7), (8, 0, 15, 7), (0, 8, 7, 15), (8, 8, 15, 15)):   # four floor plates
    rect(im, x0, y0, x1, y0, DECK[0]); rect(im, x0, y0, x0, y1, DECK[1])
    rect(im, x0, y1, x1, y1, DECK[5]); rect(im, x1, y0, x1, y1, DECK[4])
    px(im, x0 + 2, y0 + 2, DECK[0]); px(im, x1 - 2, y1 - 2, DECK[4])
for _ in range(10):                                                   # oil stains and scuffs
    x, y = rnd.randint(1, 14), rnd.randint(1, 14)
    px(im, x, y, DECK[4] if rnd.random() < 0.6 else RUST[1])
blocks["asm_deck"] = im

# the light chevron plate in the back corner of the deck
PLATE = P("#efe6d6", "#d9cfbc", "#bfb39c", "#9f937c", "#776c58", "#4f4637")
im = panel(PLATE, 140, with_rivets=False)
for i in range(5):
    for (x, y) in ((3 + i, 3 + i), (3 + i, 12 - i)):
        px(im, x + 3, y, PLATE[5]); px(im, x + 4, y, PLATE[4])
rivets(im, PLATE, 1)
blocks["asm_plate"] = im

# pressure gauge: a white dial with a red needle in a dark bezel
im = panel(DARK, 141, with_rivets=False)
ImageDraw.Draw(im).ellipse([2, 2, 13, 13], fill=DARK[5], outline=DARK[0])
ImageDraw.Draw(im).ellipse([3, 3, 12, 12], fill=C("#e8e4d8"))
for (x, y) in ((7, 4), (4, 7), (11, 7), (5, 5), (10, 5)):
    px(im, x, y, DARK[3])
for k in range(4):
    px(im, 7 + k, 8 - k, C("#c23426"))
px(im, 7, 8, DARK[5]); px(im, 8, 8, DARK[5])
blocks["asm_gauge"] = im

# guide rail of the work table: a polished top, a dark groove, a lit lower lip
im = new(DARK[3])
for x in range(16):
    px(im, x, 0, DARK[5]); px(im, x, 1, STEEL[1]); px(im, x, 2, STEEL[2]); px(im, x, 3, STEEL[3])
    for y in (4, 5, 6, 7, 8, 9, 10):
        px(im, x, y, BLACK if y in (6, 7, 8) else DARK[4])
    px(im, x, 11, STEEL[2]); px(im, x, 12, STEEL[3])
    for y in (13, 14):
        px(im, x, y, DARK[2])
    px(im, x, 15, DARK[5])
for x in (2, 9):
    rivet(im, x, 13, STEEL)
blocks["asm_rail"] = im

# work table under the arm: dark plate with hazard edges and a worn middle
im = panel(DARK, 142, with_rivets=False)
for y in range(16):
    for x in (0, 1, 14, 15):
        px(im, x, y, YELLOW[1] if ((x + y) // 2) % 2 == 0 else DARK[5])
rnd = random.Random(143)
for _ in range(8):
    px(im, rnd.randint(4, 11), rnd.randint(2, 13), DARK[0])
blocks["asm_table"] = im

# control box: off-white casing, a vent and a row of status lights
im = panel(STEEL, 144)
rect(im, 3, 3, 12, 6, DARK[4])
for x, c in ((4, C("#5fd04a")), (6, C("#f2c12e")), (8, C("#e8513f")), (10, C("#55a2f2"))):
    px(im, x, 4, c); px(im, x, 5, DARK[5])
for y in (9, 11):
    rect(im, 3, y, 12, y, DARK[3]); rect(im, 3, y + 1, 12, y + 1, STEEL[1])
blocks["asm_ctrl"] = im

# the robot arm of tier 2: dark steel with a lit edge and bolts
im = noisy(DARK, 145, 2, 4)
rect(im, 0, 0, 15, 0, DARK[0]); rect(im, 0, 1, 15, 1, DARK[1]); rect(im, 0, 15, 15, 15, DARK[5])
for x in (2, 7, 12):
    rivet(im, x, 6, STEEL)
rect(im, 0, 10, 15, 10, DARK[5]); rect(im, 0, 11, 15, 11, DARK[2])
blocks["asm_arm"] = im

# status lamp: a dull lens (idle) and a pulsing lit one (working)
LAMP = P("#fff1b0", "#ffd75a", "#f2a91e", "#b8730e", "#6e4408")


def lamp(level):
    im = new(DARK[5])
    ImageDraw.Draw(im).ellipse([1, 1, 14, 14], fill=DARK[3])
    ramp = [LAMP[4], LAMP[3], LAMP[2], LAMP[1], LAMP[0]]
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r < 5.8:
                k = max(0, min(4, round(level * 4 - r / 3)))
                px(im, x, y, ramp[k])
    px(im, 5, 5, LAMP[0] if level > 0.3 else LAMP[2])
    return im


blocks["asm_lamp"] = lamp(0.2)
strip = new(h=16 * 8)
for t in range(8):
    strip.paste(lamp(0.65 + 0.35 * math.cos(2 * math.pi * t / 8)), (0, 16 * t))
blocks["asm_lamp_glow"] = strip
anim["asm_lamp_glow"] = {"animation": {"frametime": 2}}

# steam engine tanks: weathered steel with rust streaks and a riveted seam
# (the 1.1 sprite: grey steel with a blue cast, rust round the rivets and seams)
TANK = P("#a4aaa7", "#8e9491", "#7a807d", "#666b69", "#515654", "#3b3f3e")
rnd = random.Random(110)
im = noisy(TANK, 111, 1, 2, (0.55, 0.45))
for _ in range(5):
    x, y = rnd.randint(0, 15), rnd.randint(0, 8)
    for k in range(rnd.randint(3, 7)):
        px(im, x, min(15, y + k), RUST[2] if k < 2 else RUST[1])
rect(im, 0, 3, 15, 3, TANK[4])
for x in range(1, 16, 3):
    px(im, x, 4, TANK[0])
blocks["engine_tank"] = im

# boiler: dark bronze drum with vertical ribs
BRONZE = P("#b8a678", "#978656", "#7a6b42", "#5e5232", "#433a24", "#2c2618")
im = noisy(BRONZE, 120, 1, 3)
for x in (3, 8, 13):
    rect(im, x, 0, x, 15, BRONZE[5]); rect(im, x + 1, 0, x + 1, 15, BRONZE[0])
rect(im, 0, 0, 15, 0, BRONZE[4]); rect(im, 0, 15, 15, 15, BRONZE[5])
blocks["boiler_bronze"] = im

# fluid connection: Factorio's blue pipe end
im = panel(BLUE, 125, with_rivets=False)
ImageDraw.Draw(im).ellipse([3, 3, 12, 12], fill=BLUE[3], outline=BLUE[0])
ImageDraw.Draw(im).ellipse([6, 6, 9, 9], fill=C("#0c1a2c"))
blocks["fluid_port"] = im

# stone furnace bricks: Factorio's weathered grey-brown stone
SAND = P("#b3a48c", "#9a8b73", "#84765f", "#6e624f", "#574d3e", "#3b342a")
rnd = random.Random(130)
im = new(SAND[5])
for ri, (y0, y1) in enumerate(((0, 4), (5, 9), (10, 14))):
    x = -3 if ri % 2 else 0
    while x < 16:
        w = rnd.choice((5, 6))
        x0, x1 = max(x, 0), min(x + w - 1, 15)
        base = rnd.choice((1, 2, 2, 3))
        rect(im, x0, y0, x1, y1 - 1, SAND[base])
        rect(im, x0, y0, x1, y0, SAND[base - 1])
        for _ in range(2):
            px(im, rnd.randint(x0, x1), rnd.randint(y0 + 1, max(y0 + 1, y1 - 2)), SAND[base + 1])
        x += w + 1
blocks["furnace_stone"] = im
# the ledges of the stepped pyramid: the same bricks in sunlight
# output chute with Factorio's yellow drop arrow (pointing to the top of the texture = the machine's front)
im = panel(DARK, 135, with_rivets=False)
for i in range(6):
    rect(im, 7 - i, 3 + i, 8 + i, 3 + i, YELLOW[1])
rect(im, 6, 9, 9, 12, YELLOW[1])
rect(im, 7, 4, 8, 11, YELLOW[0])
blocks["chute_arrow"] = im
blocks["furnace_ledge"] = Image.eval(blocks["furnace_stone"].convert("RGB"), lambda v: min(255, int(v * 1.12) + 12)).convert("RGBA")

# ---------------------------------------------------------------- burner/steam machine rework (working animations)
# Each machine checked against its Factorio 1.1 wiki image and animation (art/render_compare.py). Animated strips
# with irregular frame times flicker like fire instead of pulsing evenly.
EMBER = P("#fff6c0", "#ffd65a", "#ff9a2a", "#e05a14", "#9c2c0c", "#4a160a")


def strip_of(frames):
    im = new(h=16 * len(frames))
    for k, f in enumerate(frames):
        im.paste(f, (0, 16 * k))
    return im


def flicker_meta(times):
    return {"animation": {"frames": [{"index": i, "time": t} for i, t in enumerate(times)]}}


def furnace_fire_frame(t):
    """The stone furnace's fire seen through its mouth: tongues over a bed of embers (Factorio's 48-frame fire,
    cut to 16 frames). Hottest at the bottom, the tongues' heights wander from frame to frame."""
    rnd = random.Random(300 + t)
    im = new(EMBER[5])
    for x in range(16):
        h = 7 + 3.5 * math.sin(x * 0.9 + t * 0.8) + 2.5 * math.sin(x * 2.3 - t * 1.9) + rnd.uniform(-1.5, 1.5)
        top = 15 - max(3, int(h))
        for y in range(max(top, 0), 16):
            d = (y - top) / max(15 - top, 1)
            k = 0 if d > 0.82 else 1 if d > 0.6 else 2 if d > 0.35 else 3 if d > 0.15 else 4
            px(im, x, y, EMBER[k])
    for x in range(16):                       # glowing coal bed
        px(im, x, 15, EMBER[1 if rnd.random() < 0.5 else 2])
        if rnd.random() < 0.4:
            px(im, x, 14, EMBER[0])
    for _ in range(3):                        # sparks
        px(im, rnd.randint(1, 14), rnd.randint(0, 5), EMBER[1])
    return im


FLICKER16 = [2, 1, 3, 1, 2, 2, 1, 3, 2, 1, 1, 2, 3, 1, 2, 1]
blocks["furnace_fire"] = strip_of([furnace_fire_frame(t) for t in range(16)])
anim["furnace_fire"] = flicker_meta(FLICKER16)


def vent_glow_frame(t):
    """The furnace's top vent: a grate over glowing coals that brighten and dim."""
    rnd = random.Random(320 + t)
    lvl = 0.6 + 0.4 * math.sin(t * 2.1) * math.sin(t * 0.7 + 1)
    im = new()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5) / 10.6
            k = max(0, min(5, int(5 - (1 - r) * 4.2 * lvl - rnd.uniform(0, 1.2))))
            px(im, x, y, EMBER[k])
    for v in (4, 8, 12):                      # the grate bars
        rect(im, v, 0, v, 15, DARK[4]); rect(im, 0, v, 15, v, DARK[4])
    rect(im, 0, 0, 15, 0, SAND[5]); rect(im, 0, 0, 0, 15, SAND[5])
    rect(im, 0, 15, 15, 15, SAND[4]); rect(im, 15, 0, 15, 15, SAND[4])
    return im


blocks["furnace_vent_glow"] = strip_of([vent_glow_frame(t) for t in range(8)])
anim["furnace_vent_glow"] = flicker_meta([3, 2, 4, 2, 3, 2, 3, 4])


def lit_bricks(level):
    """The furnace's bricks lit yellow by the fire (Factorio's lit front): the brick pattern washed toward
    warm yellow by `level` (0..1)."""
    src = blocks["furnace_stone"]
    im = new()
    warm = (255, 205, 70)
    for y in range(16):
        for x in range(16):
            r, g, b, a = src.getpixel((x, y))
            k = level * (0.55 + 0.3 * (y / 15))   # brighter toward the fire below
            px(im, x, y, (int(r + (warm[0] - r) * k), int(g + (warm[1] - g) * k), int(b + (warm[2] - b) * k * 0.6), 255))
    return im


blocks["furnace_stone_lit"] = strip_of([lit_bricks(0.68 + 0.14 * math.sin(t * 1.9) + 0.1 * math.sin(t * 4.3))
                                        for t in range(8)])
anim["furnace_stone_lit"] = flicker_meta([3, 2, 4, 2, 3, 2, 3, 4])

# polished piston rod / valve stem: a bright highlight down its length
im = new(STEEL[2])
for y in range(16):
    for x, k in ((0, 4), (1, 3), (2, 2), (3, 1), (4, 0), (5, 0), (6, 1), (7, 2), (8, 2), (9, 3), (10, 3), (11, 4),
                 (12, 4), (13, 5), (14, 4), (15, 5)):
        px(im, x, y, STEEL[k])
for y in (0, 15):
    rect(im, 0, y, 15, y, DARK[3])
blocks["piston_rod"] = im

# electric drill rails: Factorio's toothed rack columns (dark steel, light teeth)
im = noisy(DARK, 330, 2, 4)
rect(im, 0, 0, 1, 15, DARK[5]); rect(im, 14, 0, 15, 15, DARK[5])
rect(im, 2, 0, 2, 15, DARK[1])
for y in range(0, 16, 3):
    rect(im, 4, y, 11, y, STEEL[3]); rect(im, 4, y + 1, 11, y + 1, DARK[5])
    px(im, 4, y, STEEL[2]); px(im, 11, y + 1, BLACK)
blocks["edr_rail"] = im

# electric drill status lamp: dark green lens (idle) and a pulsing lit one (mining)
GREENLAMP = P("#e6ffd6", "#9cf57a", "#4fd63a", "#2c9a22", "#164f12")


def green_lamp(level):
    im = new(DARK[5])
    ImageDraw.Draw(im).ellipse([1, 1, 14, 14], fill=DARK[3])
    ramp = list(reversed(GREENLAMP))
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r < 5.8:
                px(im, x, y, ramp[max(0, min(4, round(level * 4 - r / 3)))])
    px(im, 5, 5, GREENLAMP[0] if level > 0.3 else GREENLAMP[2])
    return im


blocks["edr_lamp_glow"] = strip_of([green_lamp(0.7 + 0.3 * math.cos(2 * math.pi * t / 8)) for t in range(8)])
anim["edr_lamp_glow"] = {"animation": {"frametime": 3}}

# the electric drill's motor head: copper-pink casing with a dark band and bolts (the reference's pink head)
MOTOR = P("#f3c2a8", "#dc9a7c", "#c07a5c", "#9a5a42", "#6c3a2a", "#43231a")
im = noisy(MOTOR, 340, 1, 3)
rect(im, 0, 6, 15, 8, DARK[3]); rect(im, 0, 6, 15, 6, DARK[1])
for x in (2, 7, 12):
    rivet(im, x, 2, STEEL); rivet(im, x, 11, STEEL)
rect(im, 0, 0, 15, 0, MOTOR[0]); rect(im, 0, 15, 15, 15, MOTOR[5])
blocks["edr_motor"] = im

# rubber hose: rusty brown with ribs
HOSE = P("#b0705a", "#8e553f", "#70402e", "#53301f", "#352015")
im = new(HOSE[2])
for y in range(16):
    for x in range(16):
        px(im, x, y, HOSE[[3, 2, 1, 1, 2, 2, 3, 4][x // 2]] if y % 4 else HOSE[4])
blocks["hose"] = im

# lab: the core inside the dome (dark steel with science slots) and its pulsing lit version (researching)
im = panel(DARK, 350, with_rivets=False)
for x0, c in ((2, RED[2]), (6, GREEN[2]), (10, BLUE[2])):
    rect(im, x0, 4, x0 + 3, 11, DARK[5]); rect(im, x0 + 1, 5, x0 + 2, 10, c)
blocks["lab_core"] = im
LABGLOW = P("#e8f7ff", "#a8dcff", "#5aa8ff", "#2f6ee0", "#1a3a8a", "#0c1a44")


def lab_core_frame(t):
    lvl = 0.5 + 0.5 * math.sin(2 * math.pi * t / 12)
    im = new()
    for y in range(16):
        for x in range(16):
            r = abs(x - 7.5) / 8
            k = max(0, min(5, int(round(4.6 - lvl * 3.4 - (1 - r) * 1.6 + (0.6 if (y + t) % 6 == 0 else 0)))))
            px(im, x, y, LABGLOW[k])
    for x0 in (2, 6, 10):
        rect(im, x0 + 1, 5, x0 + 2, 10, LABGLOW[0] if lvl > 0.5 else LABGLOW[1])
    return im


blocks["lab_core_glow"] = strip_of([lab_core_frame(t) for t in range(12)])
anim["lab_core_glow"] = {"animation": {"frametime": 3}}      # one pulse per 36 ticks
# the lab's entrance: a dark doorway in a steel frame
im = panel(STEEL, 352, with_rivets=False)
rect(im, 3, 4, 12, 15, DARK[5]); rect(im, 4, 3, 11, 3, DARK[4])
rect(im, 4, 5, 11, 15, BLACK)
for y in (8, 12):
    rect(im, 5, y, 10, y, DARK[4])
blocks["lab_door"] = im

# boiler: the grated fire box front (dark grilles in a bronze frame) and a pressure gauge
im = new(BRONZE[3])
rect(im, 0, 0, 15, 0, BRONZE[1]); rect(im, 0, 15, 15, 15, BRONZE[5])
for x0 in (1, 8):
    rect(im, x0, 2, x0 + 6, 13, DARK[5])
    for y in range(3, 13, 2):
        rect(im, x0 + 1, y, x0 + 5, y, DARK[2])
blocks["boiler_grille"] = im

# steam engine flywheel rim: ribbed steel (Factorio's striped drum)
im = new()
for y in range(16):
    for x in range(16):
        px(im, x, y, STEEL[[1, 2, 3, 4, 5, 4, 3, 2][x % 8]] if 1 <= y <= 14 else DARK[3])
blocks["engine_flywheel"] = im

# offshore pump: the glass tubes on top with still water (idle) and water flowing through them (pumping);
# Factorio's 32-frame loop at 1/4 speed (128 ticks): bubbles run along the tube twice per loop
WATER = P("#c8f0ff", "#7fd4f2", "#3aa8d8", "#1f7cb0", "#155276", "#0c3048")


def pump_glass(t=None):
    im = new(WATER[3])
    for y in range(16):
        for x in range(16):
            k = 3 if 4 <= y <= 11 else 4
            if y == 5:
                k = 2
            px(im, x, y, WATER[k])
    if t is not None:
        rnd = random.Random(360)
        for b in range(6):
            bx = (rnd.randint(0, 15) + t) % 16
            by = rnd.choice((5, 7, 8, 10))
            px(im, bx, by, WATER[0]); px(im, (bx + 15) % 16, by, WATER[1])
        for x in range(16):
            if (x + t * 2) % 8 < 2:
                px(im, x, 6, WATER[1])
    for x in (3, 12):                          # steel straps round the tube
        rect(im, x, 0, x, 15, STEEL[3]); rect(im, x + 1, 0, x + 1, 15, STEEL[5])
    rect(im, 0, 0, 15, 1, STEEL[2]); rect(im, 0, 14, 15, 15, STEEL[4])
    return im


blocks["pump_glass"] = pump_glass()
blocks["pump_flow"] = strip_of([pump_glass(t) for t in range(16)])
anim["pump_flow"] = {"animation": {"frametime": 1}}
# corrugated bronze of the pump's elbows
im = new()
for y in range(16):
    for x in range(16):
        px(im, x, y, BRONZE[[0, 1, 2, 3, 4, 3][y % 6]])
blocks["pump_ribs"] = im

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
