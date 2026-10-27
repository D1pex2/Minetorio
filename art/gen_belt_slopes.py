"""Belt ramps (R25): models for ascending/descending transport belts and the belt blockstates.

Writes into src/main/resources/assets/minetorio:
  models/block/template_belt_{ascending,descending}.json
  models/block/{,fast_,express_}transport_belt_{ascending,descending}.json
  blockstates/{,fast_,express_}transport_belt.json   (facing x shape x slope)

Run after editing:  python art/gen_belt_slopes.py   (then python -I art/check_zfight.py)

Geometry (north-facing, travel toward -z). A ramp rises (ascending) or falls (descending) one full
block over its one-block run, so its surface is the flat belt's top (y = 2 px) at the low edge and
y = 18 px (the next belt's top, one block up) at the high edge. The plate is the flat belt's 2 px
plate turned 45 degrees about x through the surface centre (8, 10, 8); its length along the slope is
16*sqrt(2) so its top spans exactly z = 0..16 and consecutive ramps touch end to end. Rotated
elements are written at their real size (no "rescale"), so the z-fight checkers see the true faces.
The belt texture maps one tile (16 px) onto the slope just like on a flat belt: lanes stay 1.0 long,
so the animated texture keeps pace with the items. The rails are the same prisms 1 px taller than
the plate, inset 0.02 px from the flat rails' planes (x = 0, 1, 15, 16) because a ramp's rail ends
reach into the neighbouring flat belt's rails.
"""
import json
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "minetorio")
MB = os.path.join(ASSETS, "models", "block")
BS = os.path.join(ASSETS, "blockstates")
NS = "minetorio:block/"

TIER_COLOR = {"": "tier_yellow", "fast_": "tier_red", "express_": "tier_blue"}
EPS = 0.02
HALF = 8 * math.sqrt(2)          # half the plate length along the slope
ORIGIN = [8, 10, 8]              # centre of the ramp surface
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
SHAPES = ("straight", "curve_left", "curve_right")
SLOPES = ("flat", "ascending", "descending")


def r4(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def element(x0, x1, y0, y1, angle, faces):
    return {
        "from": [r4(x0), r4(y0), r4(8 - HALF)],
        "to": [r4(x1), r4(y1), r4(8 + HALF)],
        "rotation": {"angle": angle, "axis": "x", "origin": ORIGIN},
        "faces": faces,
    }


def ramp_template(angle):
    """angle +45: the north (front) end goes up (ascending); -45: it goes down (descending)."""
    plate_faces = {
        "up": {"uv": [1, 0, 15, 16], "texture": "#belt"},
        "down": {"uv": [1, 0, 15, 16], "texture": "#side"},
        "north": {"uv": [1, 7, 15, 9], "texture": "#side"},
        "south": {"uv": [1, 7, 15, 9], "texture": "#side"},
    }

    def rail_faces():
        return {
            "north": {"uv": [7.5, 6.5, 8.5, 9.5], "texture": "#rail"},
            "south": {"uv": [7.5, 6.5, 8.5, 9.5], "texture": "#rail"},
            "east": {"uv": [0, 6.5, 16, 9.5], "texture": "#rail"},
            "west": {"uv": [0, 6.5, 16, 9.5], "texture": "#rail"},
            "up": {"uv": [7.5, 0, 8.5, 16], "texture": "#rail"},
            "down": {"uv": [7.5, 0, 8.5, 16], "texture": "#rail"},
        }

    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": "#belt", "side": "minetorio:block/dark"},
        "elements": [
            # the plate reaches under the rails (1 - EPS .. 15 + EPS) so no gap shows beside it
            element(1 - EPS, 15 + EPS, 8, 10, angle, plate_faces),
            element(EPS, 1 - EPS, 8, 11, angle, rail_faces()),
            element(15 + EPS, 16 - EPS, 8, 11, angle, rail_faces()),
        ],
    }


def dump(obj, path):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def blockstate(pre):
    variants = {}
    for facing, y in FACINGS.items():
        for shape in SHAPES:
            for slope in SLOPES:
                # curves are never ramps (BeltBlock.withShape); those states fall back to the curve
                if shape == "straight" and slope != "flat":
                    model = f"{NS}{pre}transport_belt_{slope}"
                elif shape == "straight":
                    model = f"{NS}{pre}transport_belt"
                else:
                    model = f"{NS}{pre}transport_belt_{shape}"
                v = {"model": model}
                if y:
                    v["y"] = y
                variants[f"facing={facing},shape={shape},slope={slope}"] = v
    return {"variants": variants}


def main():
    dump(ramp_template(45), os.path.join(MB, "template_belt_ascending.json"))
    dump(ramp_template(-45), os.path.join(MB, "template_belt_descending.json"))
    for pre, rail in TIER_COLOR.items():
        for slope in ("ascending", "descending"):
            dump({
                "parent": f"minetorio:block/template_belt_{slope}",
                "ambientocclusion": False,
                "textures": {"belt": NS + pre + "transport_belt", "rail": NS + rail},
            }, os.path.join(MB, f"{pre}transport_belt_{slope}.json"))
        dump(blockstate(pre), os.path.join(BS, f"{pre}transport_belt.json"))


if __name__ == "__main__":
    main()
