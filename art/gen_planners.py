"""Item icons of the deconstruction and upgrade planners (16x16).

Modelled on Factorio 1.1's selection-tool icons: a planner sheet in the tool's colour (red for
deconstruction, green for upgrade, blue is the blueprint's) with a light border and a white symbol
(a cross / an up arrow). Run from the repository root: python art/gen_planners.py
"""
from PIL import Image

OUT = "src/main/resources/assets/minetorio/textures/item/"


def sheet(fill, dark, light):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(1, 15):
        for x in range(2, 14):
            corner = (x in (2, 13)) and (y in (1, 14))
            if corner:
                continue
            edge = x in (2, 13) or y in (1, 14)
            px[x, y] = dark if edge else fill
    # light top-left bevel
    for x in range(3, 13):
        px[x, 2] = light
    for y in range(2, 14):
        px[3, y] = light
    # paper corner fold
    px[12, 2] = dark
    px[12, 3] = dark
    px[11, 2] = dark
    return img


def deconstruction():
    img = sheet((205, 60, 40, 255), (110, 25, 20, 255), (235, 110, 80, 255))
    px = img.load()
    white = (250, 245, 240, 255)
    shade = (140, 35, 25, 255)
    for i in range(7):
        a, b = 5 + i, 4 + i
        px[a, b] = white
        px[a, 4 + 6 - i + 0] = white
        if i < 6:
            px[a + 1, b] = shade if px[a + 1, b] != white else white
    for i in range(7):  # second diagonal thickness
        px[5 + i, 5 + i] = white if 5 + i <= 11 else px[5 + i, 5 + i]
        px[5 + i, 10 - i] = white
    return img


def upgrade():
    img = sheet((60, 160, 70, 255), (25, 80, 35, 255), (120, 210, 120, 255))
    px = img.load()
    white = (245, 250, 240, 255)
    # arrow head
    for row, half in enumerate([0, 1, 2, 3]):
        y = 4 + row
        for x in range(8 - half, 8 + half + 1):
            px[x, y] = white
            if x + 1 <= 13 and half:
                pass
    for row, half in enumerate([0, 1, 2, 3]):
        y = 4 + row
        for x in range(7 - half, 7 + half + 1):
            px[x + 1, y] = white
    # shaft
    for y in range(8, 13):
        for x in (7, 8):
            px[x, y] = white
    return img


if __name__ == "__main__":
    deconstruction().save(OUT + "deconstruction_planner.png")
    upgrade().save(OUT + "upgrade_planner.png")
    big = Image.new("RGBA", (16 * 2 * 8, 16 * 8))
    big.paste(deconstruction().resize((128, 128), Image.NEAREST), (0, 0))
    big.paste(upgrade().resize((128, 128), Image.NEAREST), (128, 0))
    big.save("art/planners-preview.png")
