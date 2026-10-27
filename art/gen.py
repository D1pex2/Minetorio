import random, json, os, math
from PIL import Image, ImageDraw, ImageFont

ROOT = "C:/Users/kirill/Documents/Projects/Minetorio/art"
OL = (30, 26, 24, 255)
def C(h, a=255):
    h = h.lstrip('#'); return (int(h[0:2],16), int(h[2:4],16), int(h[4:6],16), a)
IRON=[C('#e8eef0'),C('#b8c2c6'),C('#7e8a90'),C('#4a5358')]
COP=[C('#f2b08a'),C('#d9774a'),C('#a8502e'),C('#6e301a')]
YEL=[C('#ffe36a'),C('#f0b81c'),C('#b8860b'),C('#6e4f08')]
GRY=[C('#c8c8c8'),C('#9a9a9a'),C('#6b6b6b'),C('#3c3c3c')]
RUST=[C('#c97a4a'),C('#9a5230'),C('#6e3a22'),C('#43220f')]
STONE=[C('#a6a6a6'),C('#8a8a8a'),C('#6e6e6e'),C('#505050')]

class Cv:
    def __init__(s): s.im=Image.new('RGBA',(16,16),(0,0,0,0)); s.d=ImageDraw.Draw(s.im)
    def p(s,x,y,c):
        if 0<=x<16 and 0<=y<16: s.im.putpixel((x,y),c)
    def r(s,x0,y0,x1,y1,c): s.d.rectangle([x0,y0,x1,y1],fill=c)
    def box(s,x0,y0,x1,y1,pal,out=OL):
        s.r(x0,y0,x1,y1,out); s.r(x0+1,y0+1,x1-1,y1-1,pal[1])
        s.r(x0+1,y0+1,x1-1,y0+1,pal[0]); s.r(x0+1,y0+1,x0+1,y1-1,pal[0])
        s.r(x0+1,y1-1,x1-1,y1-1,pal[2]); s.r(x1-1,y0+1,x1-1,y1-1,pal[2])
    def l(s,x0,y0,x1,y1,c): s.d.line([x0,y0,x1,y1],fill=c)
    def e(s,x0,y0,x1,y1,c,o=None): s.d.ellipse([x0,y0,x1,y1],fill=c,outline=o)
def outline(cv):
    im=cv.im; src=im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x,y))[3]==0:
                for dx,dy in((1,0),(-1,0),(0,1),(0,-1)):
                    nx,ny=x+dx,y+dy
                    if 0<=nx<16 and 0<=ny<16 and src.getpixel((nx,ny))[3]>0:
                        im.putpixel((x,y),OL);break
    return cv

items={}
def item(name):
    def f(fn):
        cv=Cv(); fn(cv); items[name]=cv.im; return fn
    return f

def ore(name,rock,pal,fleck,seed,chunky):
    """Ore chunk: iron = angular blue-grey rock with silver crystals; copper = rounded brown rock
    with orange nuggets and green verdigris flecks (different silhouettes, not only hues)."""
    @item(name)
    def _(c):
        rnd=random.Random(seed)
        if chunky:
            pts=[(2,9),(4,4),(8,2),(12,4),(14,8),(12,13),(6,14),(2,12)]
            c.d.polygon(pts,fill=rock[1],outline=OL)
            c.d.polygon([(4,5),(8,3),(11,5),(7,7)],fill=rock[0])
            c.d.polygon([(7,12),(12,12),(13,9),(10,10)],fill=rock[2])
        else:
            c.e(1,3,14,14,rock[1],OL); c.e(3,4,9,8,rock[0]); c.e(8,9,13,13,rock[2])
        for (x,y) in [(5,6),(9,5),(10,9),(6,10),(8,8)]:
            c.p(x,y,pal[0]); c.p(x+1,y,pal[1]); c.p(x,y+1,pal[1]); c.p(x+1,y+1,pal[2])
        for _i in range(4):
            x,y=rnd.randint(4,11),rnd.randint(5,12)
            if c.im.getpixel((x,y))[3]: c.p(x,y,fleck)
ore('iron_ore',[C('#9aa6b4'),C('#6e7a88'),C('#4c5662')],[C('#f2f6fa'),C('#c8d4de'),C('#8e9cab')],C('#3a4450'),1,True)
ore('copper_ore',[C('#a08068'),C('#7a5a44'),C('#56402e')],[C('#ffc08a'),C('#e8743a'),C('#9a4a28')],C('#4fbf8a'),2,False)

def plate(name,pal):
    @item(name)
    def _(c):
        c.box(1,8,12,13,pal); c.box(4,4,15,9,pal); c.box(2,2,11,6,pal) if False else None
        c.box(3,2,13,7,pal)
        c.r(5,3,11,3,pal[0])
plate('iron_plate',IRON); plate('copper_plate',COP)

@item('stone_brick')
def _(c):
    c.r(1,3,14,13,OL); c.r(2,4,13,12,STONE[1])
    for y in (7,10): c.r(2,y,13,y,STONE[3])
    for x,y0,y1 in((7,4,6),(4,8,9),(10,8,9),(7,11,12)): c.r(x,y0,x,y1,STONE[3])
    c.r(2,4,13,4,STONE[0]); c.r(2,8,3,8,STONE[0]); c.r(5,8,9,8,STONE[0]); c.r(11,8,13,8,STONE[0])

def gear(c,cx,cy,r,pal,hole=2):
    import math
    for a in range(0,360,45):
        x=cx+round(math.cos(math.radians(a))*(r)); y=cy+round(math.sin(math.radians(a))*(r))
        c.r(x-1,y-1,x,y,OL)
    c.e(cx-r+1,cy-r+1,cx+r-1,cy+r-1,pal[1],OL)
    for a in range(0,360,45):
        x=cx+round(math.cos(math.radians(a))*(r)); y=cy+round(math.sin(math.radians(a))*(r))
        c.r(x-1,y-1,x-1,y-1,pal[0]); c.p(x,y,pal[1]); c.p(x-1,y,pal[1]); c.p(x,y-1,pal[1])
    c.e(cx-hole,cy-hole,cx+hole-1,cy+hole-1,(0,0,0,0),OL)
@item('iron_gear_wheel')
def _(c):
    """Symmetric 8-tooth gear (the old icon had a stray tooth)."""
    cx=cy=7.5
    for y in range(16):
        for x in range(16):
            dx,dy=x-cx,y-cy; r=math.hypot(dx,dy); a=math.atan2(dy,dx)
            tooth=math.cos(a*8)>0.3
            if r<=4.6 or (r<=6.9 and tooth) or r<=5.4:
                col=IRON[1]
                if r<1.6: col=(0,0,0,0)
                elif r<2.6: col=IRON[3]
                elif dx+dy<-3: col=IRON[0]
                elif dx+dy>4: col=IRON[2]
                c.p(x,y,col)
    outline(c)

@item('copper_cable')
def _(c):
    pts=[(2,11),(3,9),(5,8),(7,9),(8,11),(10,12),(12,10),(13,7),(11,4),(8,3),(5,4)]
    for i in range(len(pts)-1): c.l(*pts[i],*pts[i+1],OL)
    cv2=Cv()
    for i in range(len(pts)-1): cv2.l(*pts[i],*pts[i+1],COP[1])
    for y in range(16):
        for x in range(16):
            q=cv2.im.getpixel((x,y))
            if q[3]: c.p(x,y,COP[1] if (x+y)%3 else COP[0])
    c.r(1,11,2,12,C('#2a2a2a')); c.r(5,3,5,3,C('#2a2a2a'))
    outline(c)

@item('electronic_circuit')
def _(c):
    G=[C('#6fd16a'),C('#3f9a3c'),C('#2a6a2a'),C('#1a3f1a')]
    c.box(1,1,14,14,G)
    for y in (4,8,11): c.r(3,y,12,y,G[0] if False else C('#c9e8a0'))
    c.r(6,4,6,11,C('#c9e8a0')); c.r(10,3,10,9,C('#c9e8a0'))
    c.r(4,5,7,7,C('#2e2e2e')); c.r(8,9,11,11,C('#2e2e2e'))
    for x in (4,5,6,7): c.p(x,4,C('#d8d8d8')); c.p(x,8,C('#d8d8d8'))
    c.p(12,3,YEL[0]); c.p(3,12,YEL[0]); c.p(12,12,COP[0])

def flask(name,liq,liq2):
    @item(name)
    def _(c):
        c.r(6,1,9,4,OL); c.r(7,1,8,4,C('#cfe4ee')); c.r(7,2,8,3,C('#9fc2d4'))
        c.r(3,6,12,14,OL); c.r(5,5,10,6,OL); c.r(6,5,9,6,C('#cfe4ee'))
        c.r(4,7,11,13,liq); c.r(4,10,11,13,liq2)
        c.r(4,7,11,7,C('#cfe4ee')); c.p(5,8,C('#ffffff')); c.p(5,9,C('#ffffff'))
        c.r(2,14,13,14,OL)
        c.p(10,11,liq2); c.p(8,9,C('#ffffff'))
flask('automation_science_pack',C('#e04a3a'),C('#a82a22'))
flask('logistic_science_pack',C('#5fd04a'),C('#2f9a2c'))

@item('scanner')
def _(c):
    c.box(3,2,12,14,YEL); c.box(5,4,10,8,GRY); c.r(6,5,9,7,C('#2b4a3a'))
    c.p(7,6,C('#6dff9c')); c.p(8,5,C('#6dff9c'))
    c.r(5,10,6,11,OL); c.p(5,10,C('#e04a3a')); c.p(9,10,C('#5fd04a')); c.p(10,12,GRY[0])
    c.r(7,0,8,1,OL); c.p(7,0,GRY[0])

def inserter(name,arm_pal,base_pal,long=False,fast=False,burner=False):
    @item(name)
    def _(c):
        c.box(1,10,14,14,base_pal)
        if burner: c.r(2,11,4,13,C('#201510')); c.p(3,12,C('#ff7a1a'))
        else: c.r(11,11,12,12,C('#2e2e2e')); c.p(11,11,C('#e04a3a'))
        top=1 if long else 3
        c.r(6,top,8,11,OL); c.r(7,top+1,7,10,arm_pal[1]); c.p(7,top+1,arm_pal[0])
        c.r(4,top,6,top+2,OL); c.r(10,top,12,top+2,OL) if False else None
        c.r(4,top,5,top+2,OL); c.p(4,top+1,arm_pal[1]); c.p(5,top+1,arm_pal[0])
        c.r(8,top,10,top,OL)
        c.r(7,10,7,10,arm_pal[2]) if False else None
        if fast: c.r(2,11,9,11,arm_pal[0]) if False else c.p(9,12,YEL[0])
inserter('burner_inserter',IRON,RUST,burner=True)
inserter('inserter',YEL,GRY)
inserter('long_handed_inserter',[C('#ff9a8a'),C('#e04a3a'),C('#a82a22'),C('#6e1a14')],GRY,long=True)
inserter('fast_inserter',[C('#8ac8ff'),C('#3a8ae0'),C('#2a5aa8'),C('#1a3a6e')],GRY,fast=True)

BELTP={'':YEL,'fast_':[C('#ff9a8a'),C('#e04a3a'),C('#a82a22'),C('#6e1a14')],'express_':[C('#8ac8ff'),C('#3a8ae0'),C('#2a5aa8'),C('#1a3a6e')]}
def chev(c,pal,y):
    for i in range(4):
        c.p(7-i,y+i,pal[0]);c.p(8+i,y+i,pal[0]) if False else None
    for i in range(4):
        c.p(2+i,y+i-0 if False else y+3-i,pal[0]) if False else None
def belt_top(c,pal):
    c.r(0,3,15,12,OL); c.r(0,4,15,11,GRY[2])
    c.r(0,4,15,4,GRY[1]); c.r(0,11,15,11,GRY[3])
    for x in (2,10):
        c.p(x+0,6,pal[1]);c.p(x+1,5,pal[1]);c.p(x+2,6,pal[1]) if False else None
    for x in (1,6,11):
        for i in range(3):
            c.p(x+i,5+i if i<2 else 7,pal[1]) if False else None
    for x in (1,6,11):
        c.p(x,7,pal[1]);c.p(x+1,6,pal[0]);c.p(x+2,5,pal[1]);c.p(x+1,8,pal[1]);c.p(x+2,9,pal[0]);c.p(x+3,8,pal[1]) if False else None
        c.p(x,5,pal[0]);c.p(x+1,6,pal[1]);c.p(x+2,7,pal[0]);c.p(x+1,8,pal[1]);c.p(x,9,pal[0])
for pre,pal in BELTP.items():
    @item(pre+'transport_belt')
    def _(c,pal=pal): belt_top(c,pal)
    @item(pre+'underground_belt')
    def _(c,pal=pal):
        belt_top(c,pal); c.r(8,3,15,12,OL); c.r(9,4,14,11,GRY[3]); c.r(10,5,13,10,C('#14110f'))
        c.r(9,4,14,4,pal[1]); c.r(9,11,14,11,pal[2])
    @item(pre+'splitter')
    def _(c,pal=pal):
        c.r(0,0,15,15,OL); c.r(1,1,14,14,GRY[2]); c.r(1,1,14,1,GRY[1]); c.r(1,14,14,14,GRY[3])
        c.r(7,0,8,15,OL); c.r(7,1,8,14,pal[1]); c.r(7,1,7,14,pal[0])
        for x in (3,11):
            c.p(x,6,pal[0]);c.p(x+1,7,pal[1]);c.p(x,8,pal[0]) if False else None
            c.p(x-1,7,pal[0]);c.p(x,6,pal[1]);c.p(x+1,7,pal[0]);c.p(x,8,pal[1]) if False else None
            c.p(x-1,9,pal[0]);c.p(x,8,pal[1]);c.p(x+1,9,pal[0])

@item('burner_mining_drill')
def _(c):
    c.box(2,6,13,14,RUST); c.r(4,1,7,6,OL); c.r(5,1,6,6,GRY[2]); c.p(5,1,GRY[1])
    c.r(8,7,11,11,OL); c.r(9,8,10,10,C('#201510')); c.p(9,9,C('#ff7a1a'))
    c.r(3,12,12,12,RUST[3]); c.r(5,14,10,15,OL); c.r(6,14,9,14,GRY[1])
    c.p(3,7,RUST[0]);c.p(4,7,RUST[0])
@item('electric_mining_drill')
def _(c):
    c.box(1,3,14,13,YEL); c.r(3,5,12,10,OL); c.r(4,6,11,9,GRY[3])
    gear(c,7,7,3,IRON,1) if False else None
    c.e(5,5,10,10,GRY[1],OL); c.r(7,7,8,8,OL)
    c.r(6,14,9,15,OL); c.r(7,14,8,14,GRY[1]); c.p(2,4,YEL[0]); c.p(13,12,YEL[3])
@item('stone_furnace')
def _(c):
    c.box(1,2,14,14,STONE);
    for y in (5,9): c.r(2,y,13,y,STONE[3])
    for x,y0,y1 in((6,3,4),(10,3,4),(4,6,8),(8,6,8),(12,6,8)): c.r(x,y0,x,y1,STONE[3])
    c.r(4,10,11,13,OL); c.r(5,11,10,13,C('#2a1a10')); c.r(6,12,9,13,C('#ff7a1a')); c.p(7,12,C('#ffd04a'))
@item('assembling_machine_1')
def _(c):
    c.box(1,1,14,14,[C('#b0b8bc'),C('#8a949a'),C('#5a666c'),C('#3a444a')])
    c.r(3,3,12,8,OL); c.r(4,4,11,7,C('#1e2a30')); c.e(5,4,10,7,GRY[1],OL) if False else None
    gear(c,7,5,2,IRON,0) if False else None
    c.r(5,5,10,6,C('#6a8a9a')); c.p(6,5,C('#c8e0ea'))
    c.r(3,10,12,12,YEL[1]); c.r(3,10,12,10,YEL[0]); c.r(3,12,12,12,YEL[2])
@item('assembling_machine_2')
def _(c):
    c.box(1,1,14,14,[C('#8ac8ff'),C('#3a8ae0'),C('#2a5aa8'),C('#1a3a6e')])
    c.r(3,3,12,8,OL); c.r(4,4,11,7,C('#101a28')); c.r(5,5,10,6,C('#5aa0d8')); c.p(6,5,C('#d8f0ff'))
    c.r(3,10,12,12,GRY[1]); c.r(3,10,12,10,GRY[0]); c.r(3,12,12,12,GRY[2]); c.p(11,11,C('#5fd04a'))
@item('lab')
def _(c):
    c.box(1,6,14,14,[C('#9aa4aa'),C('#7a868c'),C('#50606a'),C('#323c42')])
    c.e(3,0,12,9,C('#5aa8c8'),OL); c.r(5,2,8,4,C('#a8e0f0')); c.p(5,2,C('#ffffff'))
    c.r(2,8,13,9,OL); c.r(3,8,12,8,GRY[1])
    c.r(3,11,5,12,C('#e04a3a')); c.r(7,11,9,12,C('#5fd04a')); c.r(11,11,12,12,YEL[1])
@item('offshore_pump')
def _(c):
    c.box(2,2,9,10,[C('#8ab4d0'),C('#5a88a8'),C('#3a6080'),C('#223c50')])
    c.r(9,4,14,7,OL); c.r(9,5,13,6,IRON[1]); c.r(9,5,13,5,IRON[0])
    c.r(0,12,15,15,C('#2a6ab0')); c.r(0,12,15,12,C('#5a9ae0'))
    for x in (2,6,11): c.p(x,14,C('#8ac0f0'))
    c.r(4,10,6,12,OL); c.p(5,11,GRY[1]); c.p(3,4,C('#c8e0f0'))
@item('pipe')
def _(c):
    c.r(0,5,15,11,OL); c.r(0,6,15,10,IRON[1]); c.r(0,6,15,6,IRON[0]); c.r(0,10,15,10,IRON[2])
    c.r(0,8,15,8,IRON[1])
    for x in (3,12): c.r(x,4,x+1,12,OL); c.r(x,5,x,11,IRON[0]); c.r(x+1,5,x+1,11,IRON[2])
@item('boiler')
def _(c):
    c.box(0,3,15,12,[C('#d0a070'),C('#a07040'),C('#704828'),C('#43220f')])
    for x in (4,11): c.r(x,3,x,12,RUST[3])
    c.r(2,12,4,15,OL); c.r(11,12,13,15,OL)
    c.r(6,6,9,11,OL); c.r(7,7,8,10,C('#201510')); c.p(7,9,C('#ff7a1a')); c.p(8,10,C('#ffd04a'))
    c.r(6,0,9,3,OL); c.r(7,0,8,3,GRY[1])
@item('steam_engine')
def _(c):
    c.box(0,4,15,13,IRON); c.r(1,4,14,4,OL);
    c.r(2,2,5,4,OL); c.r(3,2,4,3,GRY[1])
    c.e(9,5,14,12,GRY[1],OL); c.e(10,6,13,11,GRY[2]); c.p(11,8,GRY[0])
    c.r(2,7,7,9,OL); c.r(3,8,6,8,COP[1]); c.r(3,7,6,7,COP[0])
    c.r(1,14,14,15,RUST[2])
@item('small_electric_pole')
def _(c):
    c.r(6,3,9,15,OL); c.r(7,4,8,14,C('#8a5a32')); c.r(7,4,7,14,C('#b07a46'))
    c.r(2,2,13,4,OL); c.r(3,3,12,3,C('#8a5a32')); c.r(3,3,5,3,C('#b07a46'))
    for x in (2,13): c.p(x,0,OL); c.p(x,1,COP[1]);
    c.p(2,1,C('#e8e8e8')); c.p(13,1,C('#e8e8e8'))

# ---------- blocks ----------
blocks={}
def noise(base,var,seed,hl=None):
    rnd=random.Random(seed); im=Image.new('RGBA',(16,16))
    for y in range(16):
        for x in range(16):
            k=rnd.choice([0,0,1,-1,2]); b=base[min(max(1+k,0),len(base)-1)] if False else None
            im.putpixel((x,y),base[1 if rnd.random()<.6 else rnd.choice([0,2])])
    return im
def metal(pal,seed,rivets=True):
    im=noise(pal,0,seed); d=ImageDraw.Draw(im)
    d.rectangle([0,0,15,15],outline=pal[3]); d.rectangle([1,1,14,14],outline=pal[0] if False else pal[1])
    d.line([1,1,14,1],fill=pal[0]); d.line([1,1,1,14],fill=pal[0])
    d.line([2,14,14,14],fill=pal[2]); d.line([14,2,14,14],fill=pal[2])
    if rivets:
        for x,y in((3,3),(12,3),(3,12),(12,12)): im.putpixel((x,y),pal[3]); im.putpixel((x+1 if x<8 else x-1,y),pal[0])
    return im
blocks['machine_metal']=metal([C('#b8c2c6'),C('#9aa6ac'),C('#7e8a90'),C('#4a5358')],11)
blocks['machine_metal_dark']=metal([C('#6e7a80'),C('#566268'),C('#424c52'),C('#262c30')],12)
im=noise(STONE,0,13); d=ImageDraw.Draw(im)
for y in (0,5,10,15): d.line([0,y,15,y],fill=STONE[3])
for x,y0 in((4,0),(11,0),(0,5),(7,5),(4,10),(11,10)): d.line([x,y0,x,y0+4],fill=STONE[3])
blocks['furnace_stone']=im
im=blocks['furnace_stone'].copy(); d=ImageDraw.Draw(im)
d.rectangle([3,6,12,14],fill=OL); d.rectangle([4,7,11,14],fill=C('#2a1a10'))
d.rectangle([5,10,10,14],fill=C('#e8641a')); d.rectangle([6,11,9,14],fill=C('#ffa82a')); d.rectangle([7,12,8,14],fill=C('#ffe27a'))
blocks['furnace_front_lit']=im
def paneled(base,accent,seed,extra):
    im=metal(base,seed,False); d=ImageDraw.Draw(im); extra(d,im); return im
def drill(d,im):
    d.rectangle([3,3,12,12],fill=OL); d.rectangle([4,4,11,11],fill=YEL[1])
    for i in range(4): d.line([4+i*2,11,6+i*2,4],fill=YEL[2] if False else YEL[3])
    d.line([4,4,11,4],fill=YEL[0])
blocks['drill_side']=paneled([C('#f6d04a'),C('#e0a818'),C('#b8860b'),C('#6e4f08')],0,21,drill)
def asm(d,im):
    d.rectangle([3,3,12,8],fill=OL); d.rectangle([4,4,11,7],fill=C('#1e2a30')); d.rectangle([5,5,10,6],fill=C('#6a8a9a'))
    d.rectangle([3,10,12,12],fill=YEL[1]); d.line([3,10,12,10],fill=YEL[0])
blocks['assembler_side']=paneled([C('#b0b8bc'),C('#8a949a'),C('#5a666c'),C('#3a444a')],0,22,asm)
def lab(d,im):
    d.rectangle([3,3,12,8],fill=OL); d.rectangle([4,4,11,7],fill=C('#5aa8c8')); d.line([5,5,7,5],fill=C('#d8f4ff'))
    for x,c in((4,'#e04a3a'),(7,'#5fd04a'),(10,'#f0b81c')): d.rectangle([x,10,x+1,12],fill=C(c))
blocks['lab_side']=paneled([C('#9aa4aa'),C('#7a868c'),C('#50606a'),C('#323c42')],0,23,lab)
im=noise([C('#d0a070'),C('#a07040'),C('#704828')],0,24); d=ImageDraw.Draw(im)
d.rectangle([0,0,15,15],outline=C('#43220f'))
for x in (4,11): d.line([x,1,x,14],fill=C('#43220f')); d.line([x+1,1,x+1,14],fill=C('#d0a070'))
for x,y in((2,2),(2,13),(13,2),(13,13)): im.putpixel((x,y),C('#43220f'))
blocks['boiler_side']=im
im=metal(IRON[::1] if False else [C('#b8c2c6'),C('#9aa6ac'),C('#7e8a90'),C('#4a5358')],25,False); d=ImageDraw.Draw(im)
d.rectangle([2,4,13,11],fill=OL); d.rectangle([3,5,12,10],fill=COP[1]); d.line([3,5,12,5],fill=COP[0]); d.line([3,10,12,10],fill=COP[2])
for x in (5,8,11): d.line([x,5,x,10],fill=COP[3])
blocks['engine_side']=im
im=Image.new('RGBA',(16,16)); d=ImageDraw.Draw(im)
for y in range(16):
    c=[IRON[2],IRON[1],IRON[0],IRON[1],IRON[1],IRON[1],IRON[2],IRON[3]][y%8] if False else [IRON[3],IRON[2],IRON[1],IRON[0],IRON[0],IRON[1],IRON[2],IRON[3]][y%8]
    d.line([0,y,15,y],fill=c)
for x in (0,1,14,15): d.line([x,0,x,15],fill=IRON[3] if x in(0,15) else IRON[2])
blocks['pipe']=im
im=noise([C('#b07a46'),C('#8a5a32'),C('#6a4020')],0,26); d=ImageDraw.Draw(im)
for y in range(0,16,4): d.line([0,y,15,y],fill=C('#5a3418')) if False else None
rnd=random.Random(27)
for x in range(0,16,3):
    d.line([x,0,x,15],fill=C('#6a4020'))
blocks['pole_wood']=im

# belts: vertical strip, content scrolls downward by k*shift each frame
def belt_frame(pal,off):
    im=Image.new('RGBA',(16,16)); d=ImageDraw.Draw(im)
    d.rectangle([0,0,15,15],fill=GRY[2]); d.line([0,0,0,15],fill=GRY[3]); d.line([15,0,15,15],fill=GRY[3])
    d.line([1,0,1,15],fill=GRY[1]); d.line([14,0,14,15],fill=GRY[1])
    for x in (0,15): pass
    for base in (0,8):
        for i in range(6):  # chevron pointing up (rows decrease as moves up) V shape
            y=(base+off+ (2-abs(i-2.5)//1 if False else 0))
        # chevron: apex at top center
        pts=[]
        for dx in range(0,6):
            pts.append((7-dx,(base+off+dx)%16)); pts.append((8+dx,(base+off+dx)%16))
        for (x,y) in pts:
            if 2<=x<=13:
                im.putpixel((x,y),pal[0]); im.putpixel((x,(y+1)%16),pal[1])
    # subtle plate ridges
    return im
def belt_strip(name,pal,n,shift):
    s=Image.new('RGBA',(16,16*n))
    for k in range(n): s.paste(belt_frame(pal,(k*shift)%8),(0,16*k))
    blocks[name]=s
belt_strip('belt_yellow',YEL,16,1)
belt_strip('belt_red',BELTP['fast_'],8,2)
belt_strip('belt_blue',BELTP['express_'],8,3)

# ---------- write ----------
for n,im in items.items(): assert im.size==(16,16); im.save(f"{ROOT}/item/{n}.png")
# flat icons used by the mod (machines, belts, pipes, poles and inserters use 3D block-item models)
FLAT=('iron_ore','copper_ore','iron_plate','copper_plate','stone_brick','iron_gear_wheel','copper_cable',
      'electronic_circuit','automation_science_pack','logistic_science_pack')
for n in FLAT: items[n].save(f"{ROOT}/../src/main/resources/assets/minetorio/textures/item/{n}.png")
for n,im in blocks.items():
    im.save(f"{ROOT}/block/{n}.png")
    if n.startswith('belt_'):
        json.dump({"animation":{"frametime":1}},open(f"{ROOT}/block/{n}.png.mcmeta","w"))

# preview
S=8; cw=16*S+8; ch=16*S+18
entries=[('item/'+n,im) for n,im in items.items()]+[('block/'+n,(im.crop((0,0,16,16)))) for n,im in blocks.items()]
cols=8; rows=(len(entries)+cols-1)//cols
sheet=Image.new('RGBA',(cols*cw,rows*ch),(58,58,66,255)); d=ImageDraw.Draw(sheet); f=ImageFont.load_default()
for i,(n,im) in enumerate(entries):
    x,y=(i%cols)*cw+4,(i//cols)*ch+2
    bg=Image.new('RGBA',(16*S,16*S),(90,90,100,255))
    for yy in range(0,16*S,S*2):
        for xx in range(0,16*S,S*2): ImageDraw.Draw(bg).rectangle([xx,yy,xx+S-1,yy+S-1],fill=(104,104,114,255))
    bg.alpha_composite(im.resize((16*S,16*S),Image.NEAREST)); sheet.paste(bg,(x,y))
    d.text((x,y+16*S+2),n.split('/')[1][:24],fill=(235,235,235,255),font=f)
sheet.save(f"{ROOT}/preview.png")
print(len(items),len(blocks))
