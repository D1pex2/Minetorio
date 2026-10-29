"""Procedural oil machinery from Factorio 1.1 wiki entity references.

References are kept in .superpowers/reference/factorio, never packaged. The pumpjack's
green walking beam, refinery's two fractionation towers, chemical plant's yellow tanks
and steel furnace's firebox/chimney follow those images. JSON parts fit Minecraft's
model bounds; generated Java records the exact placement shared by real and ghost renders.
Run python art/gen_oil.py; inspect art/oil-preview.png and the external comparison sheet.
"""
import copy
import itertools
import json
import math
from pathlib import Path
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_radar as gr
from gen_energy import Machine, item
from PIL import Image, ImageDraw
gr.EPS = 0.08  # Collars must remain distinct even in the scaled inventory model.

ROOT = Path(__file__).resolve().parent.parent
A = ROOT / 'src/main/resources/assets/minetorio'
TEX = A / 'textures/block'
MB = A / 'models/block'
MI = A / 'models/item'
BS = A / 'blockstates'
PALETTES = {
    'oil_steel': ['#b8b5a3','#999b8e','#777f78','#555f5c','#343d3b','#222b29'],
    'oil_dark': ['#72726b','#555954','#404741','#2a332e','#1b241f','#111916'],
    'oil_green': ['#abc66b','#7ca347','#5b8731','#3d6124','#2a451a','#1b2e13'],
    'oil_yellow': ['#ffe09a','#d4ae53','#b68b35','#896125','#60471c','#403315'],
    'oil_rust': ['#d49d6c','#ad7549','#8a5533','#623b25','#42271a','#2c1c13'],
}

def textures():
    for n, colors in PALETTES.items():
        pal = gr.P(*colors)
        im = gr.noisy(pal, 330 + len(n))
        gr.bevel(im, pal)
        gr.rivets(im, pal)
        im.save(TEX / (n + '.png'))
    im = Image.new('RGBA', (16,16), '#1f231d')
    d = ImageDraw.Draw(im)
    for y in range(2,15,3): d.line((2,y,13,y),fill='#888d80')
    im.save(TEX/'oil_grate.png')
    im = Image.new('RGBA',(16,16),'#352515'); d=ImageDraw.Draw(im)
    for x in range(2,14,3):
        d.rectangle((x,4,x+1,13),fill='#ee9c22')
        d.rectangle((x,8,x+1,13),fill='#ffdc75')
    im.save(TEX/'oil_fire.png')
    im=Image.new('RGBA',(16,16),'#1e1a16');d=ImageDraw.Draw(im)
    d.ellipse((1,2,14,14),fill='#151217',outline='#52402c')
    d.arc((3,4,13,13),180,290,fill='#77634d')
    im.save(TEX/'oil_source.png')

def model():
    return Machine({n:n for n in (*PALETTES,'oil_grate','oil_fire')},'oil_steel')

def base(m,half):
    m.box([-half,0,-half],[half,2,half],'oil_dark')
    for x in (-half+1,half-3):
        m.box([x,2,-half+1],[x+2,3,half-1],'oil_steel')
    for z in (-half+1,half-3):
        m.box([-half+3,2,z],[half-3,3,z+2],'oil_steel')

def tank(m,x,z,r,y0,y1,tex):
    m.octagon_y(x,z,r*2,y0,y1,tex)
    m.octagon_y(x,z,r*2+1.2,y0+1,y0+2,'oil_dark')
    m.octagon_y(x,z,r*2+1,y1-2,y1-1,'oil_dark')
    m.octagon_y(x,z,r*1.55,y1,y1+2,tex)

def pipe(m,a,b,r=1.2,tex='oil_steel'):
    # Orthogonal sections: each joint has a short flange with different extents.
    lo=[min(a[i],b[i])-r for i in range(3)]; hi=[max(a[i],b[i])+r for i in range(3)]
    m.box(lo,hi,tex)

def pumpjack():
    m=model();base(m,23)
    # A-frame: stepped sloping legs and paired cross rails, open beneath the beam.
    for z in (-7,7):
        for side in (-1,1):
            for i in range(5):
                x=side*(11-i*1.7)
                m.box([x-1.3,3+i*4,z-1.3],[x+1.3,7+i*4,z+1.3],'oil_green')
        m.box([-6,20,z-1.6],[6,22,z+1.6],'oil_green')
    m.box([7,3,-17],[19,12,-7],'oil_dark')
    m.box([8,12,-16],[18,15,-8],'oil_green')
    m.box([9,6,-17.2],[17,10,-17.02],'oil_grate')
    pipe(m,(-17,3,0),(-17,24,0),.65)
    pipe(m,(-17,4,0),(16,4,0),1.8)
    pipe(m,(16,4,0),(16,4,-23),1.8)
    for z in (-10,10):
        m.box([-6,4,z],[8,7,z+1.2],'oil_green')
    return m

def beam():
    m=model()
    m.box([-20,-1.6,-2],[17,1.6,2],'oil_green')
    m.box([-21,-5,-3],[-17,5,3],'oil_green')
    m.box([12,-4,-3.5],[18,-1.7,3.5],'oil_dark')
    return m

def refinery():
    m=model();base(m,39)
    tank(m,8,5,14,3,39,'oil_rust')
    tank(m,-25,-12,5.5,3,42,'oil_steel')
    # Fractionation rings and walkway around the large tower.
    for y in (12,22,32):
        m.octagon_y(8,5,31,y,y+1.4,'oil_dark')
    for x,z in ((-30,28),(-14,29),(27,-27),(28,29)):
        tank(m,x,z,4.5,3,14,'oil_steel')
    m.box([-34,12,21],[-10,14,37],'oil_dark')
    for z in (-31,31): pipe(m,(-34,7,z),(34,7,z),1.5)
    pipe(m,(-25,30,-12),(-25,30,25),1.1)
    pipe(m,(-25,30,25),(8,30,25),1.1)
    pipe(m,(8,30,25),(8,36,25),1.1)
    # Two inputs at rear, three fractions on front, matching the actual ports.
    for x in (-16,16): pipe(m,(x,5,30),(x,5,39),2)
    for x in (-32,0,32): pipe(m,(x,5,-39),(x,5,-29),2)
    for y in range(8,38,5):
        m.box([-32,y,-15],[-30,y+1,-8],'oil_steel')
    return m

def chemical():
    m=model();base(m,23)
    tank(m,-9,-4,7,3,21,'oil_yellow')
    tank(m,9,-4,6,3,27,'oil_steel')
    tank(m,-8,12,5,3,13,'oil_yellow')
    tank(m,10,12,5,3,13,'oil_steel')
    pipe(m,(-9,24,-4),(9,24,-4),1.8,'oil_yellow')
    pipe(m,(-19,7,-16),(18,7,-16),1.5)
    pipe(m,(-19,7,-16),(-19,17,-16),1.5)
    for x in (-16,16):
        pipe(m,(x,4,-23),(x,4,-17),1.8)
        pipe(m,(x,4,17),(x,4,23),1.8)
    m.box([-20,4,0],[-17,15,9],'oil_dark')
    return m

def furnace():
    m=model();base(m,15)
    m.box([-13,3,-12],[11,15,12],'oil_dark')
    m.box([-11,15,-10],[9,19,10],'oil_steel')
    # Inset opening and brass rim; lit overlay is a separate render part.
    m.box([-9,5,-12.2],[7,12,-12.01],'oil_rust')
    m.box([-7,6,-12.4],[5,11,-12.21],'oil_grate')
    tank(m,9,7,3.5,15,29,'oil_rust')
    pipe(m,(-10,20,5),(-10,25,5),1.4)
    pipe(m,(-10,25,5),(1,25,5),1.4)
    pipe(m,(1,25,5),(1,20,5),1.4)
    return m

def write_parts(name,m):
    buckets={}
    for e in m.elements:
        cuts=[]
        for axis in range(3):
            lo,hi=e['from'][axis],e['to'][axis]
            inner=[8+32*k for k in range(-4,6) if lo<8+32*k<hi]
            cuts.append(list(zip([lo]+inner,inner+[hi])))
        for spans in itertools.product(*cuts):
            f=[s[0] for s in spans];t=[s[1] for s in spans]
            cell=tuple(math.floor((f[i]+24+1e-6)/32) for i in range(3))
            offset=[32*c for c in cell]
            el=copy.deepcopy(e)
            for axis,(negative,positive) in enumerate((('west','east'),('down','up'),('north','south'))):
                if f[axis] > e['from'][axis]+1e-6: el['faces'].pop(negative,None)
                if t[axis] < e['to'][axis]-1e-6: el['faces'].pop(positive,None)
            el['from']=[round(f[i]-offset[i]+8,4) for i in range(3)]
            el['to']=[round(t[i]-offset[i]+8,4) for i in range(3)]
            if 'rotation' in el:
                el['rotation']['origin']=[round(e['rotation']['origin'][i]-offset[i]+8,4) for i in range(3)]
            assert all(-16<=v<=32 for v in el['from']+el['to'])
            buckets.setdefault(tuple(offset),[]).append(el)
    parts=[]
    for i,(off,els) in enumerate(sorted(buckets.items())):
        part=f'{name}_p{i}'
        gr.dump({'textures':{k:'minetorio:block/'+v for k,v in m.textures.items()},'elements':els},str(MB/(part+'.json')))
        parts.append((part,*off))
    gr.dump({'textures':{'particle':'minetorio:block/oil_steel'},'elements':[]},str(MB/(name+'.json')))
    extra=[]
    if name=='pumpjack':
        extra=copy.deepcopy(beam().elements)
        for e in extra:
            e['from'][1]+=26; e['to'][1]+=26
    gr.dump(item(m,extra),str(MI/(name+'.json')))
    variants={'facing='+d:{'model':'minetorio:block/'+name} for d in ('north','east','south','west')}
    if name=='steel_furnace': variants={k+',lit='+lit:v for k,v in variants.items() for lit in ('false','true')}
    gr.dump({'variants':variants},str(BS/(name+'.json')))
    return parts

def main():
    textures()
    machines={'pumpjack':pumpjack(),'oil_refinery':refinery(),'chemical_plant':chemical(),'steel_furnace':furnace()}
    for name in machines:
        for old in MB.glob(name+'_p*.json'):
            old.unlink()  # Only these generator-owned parts; their count can change with geometry.
    # Nested collars, crossed pipes and tank bands must not have coincident outer faces.
    # Small, deterministic manufacturing clearances leave each surface on its own plane.
    for m in machines.values():
        for index,e in enumerate(m.elements):
            for axis in range(3):
                clearance=(0.013,0.017,0.019)[axis]*(index+1)
                e['from'][axis]+=clearance
                e['to'][axis]+=clearance
                if 'rotation' in e: e['rotation']['origin'][axis]+=clearance
    parts={n:write_parts(n,m) for n,m in machines.items()}
    (ROOT/'art/oil-model-parts.json').write_text(json.dumps(parts,indent=2)+'\n')
    bm=beam(); bm.elements=[{**e,'from':[e['from'][0]+8,e['from'][1],e['from'][2]+8],'to':[e['to'][0]+8,e['to'][1],e['to'][2]+8]} for e in bm.elements]
    gr.dump({'textures':{k:'minetorio:block/'+v for k,v in bm.textures.items()},'elements':bm.elements},str(MB/'pumpjack_beam.json'))
    glow=model();glow.box([1,6,-4.45],[13,11,-4.4],'oil_fire')
    gr.dump({'textures':{k:'minetorio:block/'+v for k,v in glow.textures.items()},'elements':glow.elements},str(MB/'steel_furnace_fire.json'))
    gr.dump({'parent':'minecraft:block/cube_all','textures':{'all':'minetorio:block/oil_source'}},str(MB/'oil_source.json'))
    gr.dump({'variants':{'':{'model':'minetorio:block/oil_source'}}},str(BS/'oil_source.json'))
    java=['package dev.minetorio.client.oilrender;','import java.util.List;','import java.util.Map;','/** Generated by art/gen_oil.py. Machine-pixel offsets shared with preview and ghosts. */','public final class OilModelParts {','private OilModelParts() {}','public record Part(String model, int x, int y, int z) {}','public static final Map<String,List<Part>> PARTS = Map.of(']
    entries=[]
    for n,ps in parts.items(): entries.append('"'+n+'",List.of('+','.join(f'new Part("{p}",{x},{y},{z})' for p,x,y,z in ps)+')')
    java.append(',\n'.join(entries)+');\n}')
    path=ROOT/'src/main/java/dev/minetorio/client/oilrender/OilModelParts.java';path.parent.mkdir(parents=True,exist_ok=True);path.write_text('\n'.join(java),encoding='utf8')
    import render_models as rm
    views=[]
    for n,ps in parts.items():
        whole=[]
        for p,x,y,z in ps: whole+=rm.placed('minetorio:block/'+p,dx=x,dy=y,dz=z)
        if n=='pumpjack': whole+=rm.placed('minetorio:block/pumpjack_beam',dy=26)
        views.append(rm.render(whole,None,225,extent=rm.bounds(whole),size=300))
    sheet=Image.new('RGB',(1200,300))
    for i,v in enumerate(views):sheet.paste(v,(i*300,0))
    sheet.save(ROOT/'art/oil-preview.png')
    print('Oil models:',{n:len(p) for n,p in parts.items()})

if __name__=='__main__':main()
