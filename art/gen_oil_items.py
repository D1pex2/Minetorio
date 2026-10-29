"""Original 32px intermediate icons and data assets for oil stage (no Factorio art bundled)."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parent.parent
A=ROOT/'src/main/resources/assets/minetorio'
COLORS={'water':'#339aca','crude_oil':'#57473b','heavy_oil':'#b05e30','light_oil':'#e8b442','petroleum_gas':'#ab80bb','sulfuric_acid':'#dbdc42','lubricant':'#81ad42'}
IDS=['steel_plate','iron_stick','engine_unit','electric_engine_unit','advanced_circuit','flying_robot_frame','plastic_bar','sulfur','battery','explosives','solid_fuel','chemical_science_pack','production_science_pack','utility_science_pack','empty_barrel']+[f'{f}_barrel' for f in COLORS]

def save(id,im):
    im.save(A/'textures/item'/f'{id}.png')
    (A/'models/item'/f'{id}.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'minetorio:item/{id}'}},indent=2)+'\n')

def icon(id):
    im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
    if id.endswith('_barrel'):
        d.rounded_rectangle((7,4,25,28),radius=4,fill='#758281',outline='#263b3b',width=2)
        d.ellipse((7,3,25,10),fill='#b7bfaf',outline='#354b4a',width=2)
        for y in (12,23):d.rectangle((6,y,26,y+2),fill='#263b3b')
        fluid=id.removesuffix('_barrel')
        if fluid!='empty':d.rectangle((10,15,22,20),fill=COLORS[fluid])
    elif id.endswith('_science_pack'):
        c={'chemical':'#3eaee4','production':'#b361db','utility':'#eed351'}[id.removesuffix('_science_pack')]
        d.polygon([(10,5),(21,5),(21,12),(27,24),(24,28),(7,28),(5,24),(10,12)],fill=c,outline='#233b40')
        d.rectangle((10,3,21,7),fill='#c0cbc7',outline='#273c40')
        d.line((9,23,23,23),fill='#d1f1e8',width=2)
    elif id=='advanced_circuit':
        d.rounded_rectangle((3,7,29,26),radius=2,fill='#ba4033',outline='#432c25',width=2)
        for x in (7,13,19,25):d.line((x,5,x,29),fill='#d7b965',width=2)
        d.rectangle((10,12,22,22),fill='#3c3c35',outline='#9e9b7b',width=2)
    elif id in ('engine_unit','electric_engine_unit'):
        d.rectangle((4,10,26,25),fill='#536b56' if id.startswith('electric') else '#8a774c',outline='#293a32',width=2)
        for x in (7,14,21):d.rectangle((x,4,x+5,15),fill='#b3baaa',outline='#47534c',width=2)
        d.ellipse((18,18,30,29),fill='#45514b',outline='#a0ad9d',width=2)
    elif id=='battery':
        d.rounded_rectangle((8,6,24,29),radius=2,fill='#b8af72',outline='#3f453b',width=2)
        d.rectangle((10,2,14,6),fill='#e27442');d.rectangle((18,2,22,6),fill='#626d69')
        d.line((9,14,23,14),fill='#4c6a58',width=4)
    elif id=='flying_robot_frame':
        for x in (2,21):
            for y in (3,20):d.ellipse((x,y,x+9,y+9),fill='#9ba392',outline='#33413c',width=2)
        d.line((7,8,26,25),fill='#6f7a64',width=4);d.line((26,8,7,25),fill='#6f7a64',width=4)
        d.rectangle((11,11,21,23),fill='#929d72',outline='#35433b',width=2)
    elif id=='steel_plate':
        for y,c in ((13,'#5a6660'),(8,'#9bada2')):d.polygon([(4,y+6),(21,y),(29,y+6),(12,y+13)],fill=c,outline='#303f3b',width=2)
    elif id=='iron_stick':
        d.line((7,27,24,5),fill='#3c4d48',width=6);d.line((7,26,24,4),fill='#a4b4a6',width=3)
    elif id=='plastic_bar':
        for x,y in ((5,14),(12,6)):d.polygon([(x,y),(x+13,y),(x+17,y+8),(x+3,y+10)],fill='#e6e2bd',outline='#767f70',width=2)
    elif id=='sulfur':
        d.polygon([(3,24),(6,14),(12,13),(15,4),(24,9),(28,22),(21,28),(10,27)],fill='#e1ce39',outline='#776e25',width=2)
        d.line((12,13,17,23,24,9),fill='#ffeb77',width=2)
    elif id=='explosives':
        for x,y in ((5,8),(13,4),(21,9)):d.rounded_rectangle((x,y,x+6,y+21),radius=2,fill='#af5b38',outline='#482f27',width=1)
        d.rectangle((4,15,28,20),fill='#9d9c72');d.line((7,7,25,2),fill='#393c2b',width=2)
    else:
        d.polygon([(5,10),(23,5),(28,11),(27,25),(10,28),(4,22)],fill='#484d43',outline='#1c2925',width=2)
        d.line((8,12,23,8,25,11),fill='#89917b',width=2)
    return im

def main():
    for id in IDS:save(id,icon(id))
    # Steel chest is a real 48-slot container; its original metal-panel model is block-rendered.
    for name in ('steel_chest',):
        (A/'models/block'/f'{name}.json').write_text(json.dumps({'parent':'minecraft:block/cube_all','textures':{'all':'minetorio:block/oil_steel'}},indent=2)+'\n')
        (A/'blockstates'/f'{name}.json').write_text(json.dumps({'variants':{'':{'model':f'minetorio:block/{name}'}}},indent=2)+'\n')
        (A/'models/item'/f'{name}.json').write_text(json.dumps({'parent':f'minetorio:block/{name}'},indent=2)+'\n')
    data=ROOT/'src/main/resources/data'
    for name in ('pumpjack','oil_refinery','chemical_plant','steel_furnace','steel_chest'):
        p=data/'minetorio/loot_table/blocks'/f'{name}.json'
        p.write_text(json.dumps({'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'minetorio:{name}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]},indent=2)+'\n')
    tag=data/'minecraft/tags/block/mineable/pickaxe.json'
    obj=json.loads(tag.read_text());obj['values']=list(dict.fromkeys(obj['values']+[f'minetorio:{n}' for n in ('pumpjack','oil_refinery','chemical_plant','steel_furnace','steel_chest')]))
    tag.write_text(json.dumps(obj,indent=2)+'\n')
    sheet=Image.new('RGBA',(32*len(IDS),32))
    for i,id in enumerate(IDS):sheet.paste(icon(id),(32*i,0))
    sheet.save(ROOT/'art/oil-items-preview.png')
    print(f'{len(IDS)} oil intermediate icons generated')

if __name__=='__main__':main()
