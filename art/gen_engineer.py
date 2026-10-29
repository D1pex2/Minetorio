"""Original Minetorio engineer, classic 64x64 wide-arm Minecraft UV; no borrowed art."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/minetorio'
skin = Image.new('RGBA', (64, 64))
d = ImageDraw.Draw(skin)
dark = '#302b29'
seam = '#554534'
cloth = '#91703f'
light = '#b38d52'
metal = '#666b69'
orange = '#d7a041'

def box(x, y, w, h, dep, base):
    # Top/bottom and right/front/left/back; opaque base layer on every face.
    d.rectangle((x+dep,y,x+dep+w-1,y+dep-1), fill=light)
    d.rectangle((x+dep+w,y,x+dep+2*w-1,y+dep-1), fill=dark)
    for xx,ww,c in [(x,dep,seam),(x+dep,w,base),(x+dep+w,dep,seam),(x+2*dep+w,w,seam)]:
        d.rectangle((xx,y+dep,xx+ww-1,y+dep+h-1),fill=c)
        d.line((xx,y+dep+h-1,xx+ww-1,y+dep+h-1),fill=dark)

box(0,0,8,8,8,cloth)    # head
box(16,16,8,12,4,cloth) # torso
box(40,16,4,12,4,cloth) # right arm
box(32,48,4,12,4,cloth) # left arm
box(0,16,4,12,4,seam)   # right leg
box(16,48,4,12,4,seam)  # left leg
# Helmet: worn orange rim, forehead lamp, dark blue goggles and respirator.
d.rectangle((8,8,15,10),fill=orange)
d.line((8,10,15,10),fill=dark)
d.rectangle((8,11,15,13),fill=dark)
d.rectangle((9,11,10,12),fill='#749e9c')
d.rectangle((13,11,14,12),fill='#749e9c')
d.rectangle((11,13,12,15),fill=metal)
d.line((8,15,15,15),fill=seam)
for x in (0,16,24):
    d.rectangle((x,8,x+7,10),fill=orange)
    d.line((x,11,x+7,11),fill=dark)
d.rectangle((11,8,12,9),fill='#f7e8aa')
d.line((10,0,10,7),fill=orange)
d.line((13,0,13,7),fill=orange)
# Harness, reinforced collar, chest pockets, utility belt and steel buckle.
d.rectangle((20,20,27,21),fill=dark)
for x in (21,26):
    d.line((x,22,x,28),fill=dark)
    d.point((x,23),fill=metal)
d.rectangle((22,23,24,25),fill=light)
d.line((22,25,24,25),fill=seam)
d.rectangle((16,29,39,30),fill=dark)
d.rectangle((23,29,24,30),fill='#b4b8aa')
# Backpack on back of torso (32..39), breathing bottle and small warning patch.
d.rectangle((33,21,38,28),fill=dark)
d.rectangle((34,22,37,27),fill='#796947')
d.line((34,23,37,23),fill=light)
d.rectangle((36,25,37,26),fill=orange)
d.line((32,22,32,28),fill=metal)
# Gloves, cuffs, kneepads and boots.
for x,y in ((44,20),(36,52)):
    d.rectangle((x,y+3,x+3,y+4),fill=light)
    d.rectangle((x,y+8,x+3,y+11),fill=dark)
    d.line((x,y+8,x+3,y+8),fill=metal)
for x,y in ((4,20),(20,52)):
    d.rectangle((x,y+4,x+3,y+6),fill=metal)
    d.rectangle((x,y+8,x+3,y+11),fill=dark)
    d.line((x,y+8,x+3,y+8),fill=orange)
# Complete side/back glove and boot wraps to avoid blank visible seams.
for x,y in ((40,20),(32,52),(0,20),(16,52)):
    d.rectangle((x,y+9,x+15,y+11),fill=dark)

path=ASSETS/'textures/entity/engineer.png'
path.parent.mkdir(parents=True,exist_ok=True)
skin.save(path)
# Front/back plate composed from exact model UVs for offline inspection.
plate=Image.new('RGBA',(64,40),'#212326')
def paste_uv(region,xy): plate.alpha_composite(skin.crop(region),xy)
for back,ox in ((False,8),(True,40)):
    paste_uv((24,8,32,16) if back else (8,8,16,16),(ox+4,2))
    paste_uv((32,20,40,32) if back else (20,20,28,32),(ox+4,10))
    paste_uv((52,20,56,32) if back else (44,20,48,32),(ox,10))
    paste_uv((44,52,48,64) if back else (36,52,40,64),(ox+12,10))
    paste_uv((12,20,16,32) if back else (4,20,8,32),(ox+4,22))
    paste_uv((28,52,32,64) if back else (20,52,24,64),(ox+8,22))
plate.resize((768,480),Image.Resampling.NEAREST).save(ROOT/'art/engineer-preview.png')

def icon(name, draw):
    im=Image.new('RGBA',(16,16));draw(ImageDraw.Draw(im));
    target=ASSETS/f'textures/item/{name}.png';target.parent.mkdir(parents=True,exist_ok=True);im.save(target)
    (ASSETS/f'models/item/{name}.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'minetorio:item/{name}'}},indent=2)+'\n')
def coal(p):
    p.polygon([(2,11),(3,5),(8,2),(13,5),(14,11),(10,14),(4,14)],fill='#25272b',outline='#15171a')
    p.polygon([(4,6),(8,3),(11,6),(7,9)],fill='#505258');p.line((4,11,7,9,11,12),fill='#36393e')
def stone(p):
    p.polygon([(1,11),(4,5),(10,3),(14,8),(14,12),(8,14),(2,13)],fill='#777565',outline='#343c35')
    p.polygon([(4,6),(10,4),(12,8),(7,10),(2,11)],fill='#b4af98');p.line((7,10,8,13),fill='#51584e')
def tool(p):
    p.line((4,14,11,3),fill='#3d3027',width=3);p.line((4,13,10,4),fill='#a67c49')
    p.polygon([(4,3),(8,1),(13,2),(15,5),(11,4),(8,4),(5,6)],fill='#8a9290',outline='#343d3d')
    p.line((5,3,8,2,12,3),fill='#c2c7b5')
icon('coal',coal);icon('stone',stone);icon('engineer_tool',tool)
print('Original engineer skin, offline front/back preview and three item icons generated.')
