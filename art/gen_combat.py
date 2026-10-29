"""Авторские модели первого боя и синтезированные звуки. Ассеты Factorio не используются.

Геометрия: оригинальные координаты автора этого скрипта, материалы - ссылки на Minecraft.
Звук: детерминированный шум и затухающие осцилляторы. Для OGG нужен soundfile.
Запуск из корня: python art/gen_combat.py (soundfile должен быть доступен в Python).
"""
from pathlib import Path
import json
import math
import random
import soundfile

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
ASSETS = ROOT / "assets/minetorio"


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def box(a, b, texture="metal"):
    return {"from": a, "to": b, "faces": {side: {"texture": "#"+texture} for side in
            ("up", "down", "north", "south", "east", "west")}}


def model(path, pieces, textures, display=None):
    data = {"textures": textures, "elements": pieces}
    if display:
        data["display"] = display
    write(ASSETS / "models" / (path+".json"), data)


metal = {"metal": "minecraft:block/iron_block", "dark": "minecraft:block/deepslate_tiles",
         "grip": "minecraft:block/dark_oak_planks", "brass": "minecraft:block/gold_block"}
display = {
    "gui": {"rotation": [28, 135, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
    "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 1, 0], "scale": [0.8, 0.8, 0.8]},
    "ground": {"scale": [0.6, 0.6, 0.6]}}
model("item/pistol", [box([6.5,8,2],[9.5,11,13]), box([7,8.5,1],[9,10.5,3],"dark"),
      box([7,2,10],[9,8,13],"grip"), box([6.5,7,6],[9.5,8,11],"dark"),
      box([7.5,11,3],[8.5,11.7,4],"dark"), box([7.5,11,11],[8.5,11.7,12],"dark")],metal,display)
model("item/submachine_gun", [box([6,7,3],[10,11,12],"dark"),box([7,8,0],[9,10,5]),
      box([7,2,7],[9,7,9],"dark"),box([7,2,10],[9,7,12],"grip"),box([6,7,12],[10,10,16],"grip"),
      box([7.5,11,3],[8.5,12,4]), box([7.5,11,10],[8.5,12,11])],metal,display)
model("item/firearm_magazine",[box([4,2,6],[12,12,10],"dark"),
      *[box([x,12,6.5],[x+1.5,15,9.5],"brass") for x in [4,6.2,8.4,10.5]]],metal,display)
chitin = {"shell":"minecraft:block/green_terracotta", "dark":"minecraft:block/brown_terracotta",
          "fang":"minecraft:block/bone_block_side", "eye":"minecraft:block/red_concrete"}
model("block/small_biter_body",[box([3,3,5],[13,8,13],"shell"),box([4,4,2],[12,9,7],"dark"),
      box([3,1,1],[5,4,5],"fang"),box([11,1,1],[13,4,5],"fang"),
      box([3,7,1.95],[5,8,3],"eye"),box([11,7,1.95],[13,8,3],"eye"),
      box([5,6,11],[11,10,15],"shell")],chitin)
model("block/small_biter_leg",[box([7,0,6.5],[15,2,9.5],"dark"), box([13.05,-2,6.55],[14.95,1,9.45],"fang")],chitin)
nest = {"hide":"minecraft:block/brown_terracotta", "ridge":"minecraft:block/red_terracotta",
        "mouth":"minecraft:block/black_concrete", "fang":"minecraft:block/bone_block_side"}
model("block/biter_spawner",[box([-2,0,-1],[18,2,17],"hide"),box([0,2,1],[16,7,15],"hide"),
      box([3,7,3],[13,10,13],"ridge"),box([5,8,5],[11,11,11],"mouth"),
      *[box([x,6,z],[x+2,12,z+2],"fang") for x,z in [(2,2),(12,2),(2,12),(12,12)]]],nest)

for name, freq, duration, seed in [("pistol_shot", 105, .24, 291), ("submachine_shot", 135, .17, 292)]:
    rate = 44100
    rng = random.Random(seed)
    data = []
    last = 0
    for i in range(int(rate*duration)):
        t = i/rate
        noise = rng.uniform(-1,1)
        last = last*.65 + noise*.35
        value = .7*noise*math.exp(-t*65) + .45*last*math.exp(-t*18)
        value += .5*math.sin(2*math.pi*freq*t)*math.exp(-t*28)
        data.append(max(-1,min(1,value)))
    out = ASSETS / "sounds/combat" / (name+".ogg")
    out.parent.mkdir(parents=True, exist_ok=True)
    soundfile.write(str(out),data,rate,format="OGG",subtype="VORBIS")

sounds_path = ASSETS / "sounds.json"
sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
for name in ["pistol_shot","submachine_shot"]:
    sounds[name] = {"subtitle":"subtitles.minetorio."+name,"sounds":["minetorio:combat/"+name]}
write(sounds_path,sounds)
for lang, translations in {
    "ru_ru": {"item.minetorio.pistol":"Пистолет","item.minetorio.submachine_gun":"Автомат",
              "item.minetorio.firearm_magazine":"Магазин с патронами","entity.minetorio.small_biter":"Маленький кусака",
              "entity.minetorio.biter_spawner":"Гнездо кусак","minetorio.handcraft.group.military":"Военное дело",
              "technology.minetorio.military":"Военное дело", "minetorio.combat.loaded":"В оружии: %s / 10",
              "subtitles.minetorio.pistol_shot":"Выстрел пистолета","subtitles.minetorio.submachine_shot":"Выстрел автомата",
              "death.attack.minetorio.bullet":"%1$s погиб от выстрела %2$s", "death.attack.minetorio.bite":"%1$s съеден кусаками",
              "minetorio.combat.single":"Для стрельбы возьмите одно оружие из стопки"},
    "en_us": {"item.minetorio.pistol":"Pistol","item.minetorio.submachine_gun":"Submachine gun",
              "item.minetorio.firearm_magazine":"Firearm magazine","entity.minetorio.small_biter":"Small biter",
              "entity.minetorio.biter_spawner":"Biter spawner","minetorio.handcraft.group.military":"Combat",
              "technology.minetorio.military":"Military", "minetorio.combat.loaded":"Loaded: %s / 10",
              "subtitles.minetorio.pistol_shot":"Pistol fires","subtitles.minetorio.submachine_shot":"Submachine gun fires",
              "death.attack.minetorio.bullet":"%1$s was shot by %2$s", "death.attack.minetorio.bite":"%1$s was eaten by biters",
              "minetorio.combat.single":"Hold a single gun to fire"}
}.items():
    path = ASSETS / "lang" / (lang+".json")
    values=json.loads(path.read_text(encoding="utf-8"));values.update(translations);write(path,values)

for name in ["bullet","bite"]:
    write(ROOT/"data/minetorio/damage_type"/(name+".json"), {"message_id":"minetorio."+name,"scaling":"never","exhaustion":0})
write(ROOT/"data/minecraft/tags/damage_type/bypasses_cooldown.json", {"replace":False,"values":["minetorio:bullet","minetorio:bite"]})
