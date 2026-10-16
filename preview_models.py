"""Draw an accurate *orthographic front* preview of Java cuboids (not an in-game screenshot)."""
from PIL import Image,ImageDraw,ImageFont
from pathlib import Path
import json,math
root=Path(__file__).parent
models=root/'resource-pack/assets/solarius/models/item'
colors={
'bone':(221,151,57),'bright':(255,213,82),'shadow':(93,48,31),
'aura':(255,245,153),'grip':(78,38,39),'steel':(232,153,37),
'ember':(250,111,37),'ruby':(143,37,49),'crimson':(188,44,44),
'edge':(255,182,40),'ivory':(244,227,203),'joint':(120,78,47),
'gold_dark':(156,98,34),'glow':(255,252,216)}
W,H=1500,850
im=Image.new('RGB',(W,H),(12,15,29));d=ImageDraw.Draw(im)
for y in range(H):
 f=y/H
 d.line([(0,y),(W,y)],fill=(int(15+10*f),int(18+9*f),int(32+4*f)))
font='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
reg='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
title=ImageFont.truetype(font,53);label=ImageFont.truetype(font,24);sub=ImageFont.truetype(reg,23)
d.text((53,30),'SOLARIUS 4.0   |   SUNFORGED',font=title,fill=(255,205,93))
d.text((55,103),'New boss-style hand armor, curved finger bones and rune-etched crimson flameblade',font=sub,fill=(218,211,188))

def shade(c,f):return tuple(max(0,min(255,int(x*f))) for x in c)

def model(name,cx,cy,scale):
 j=json.loads((models/(name+'.json')).read_text())
 elements=sorted(j['elements'],key=lambda e:e['from'][2]+e['to'][2],reverse=True)
 for e in elements:
  x,y,z=e['from'];X,Y,Z=e['to']
  col=colors[e['faces']['north']['texture'][1:]]
  points=[(x,y),(X,y),(X,Y),(x,Y)]
  if 'rotation' in e:
   rot=e['rotation']
   if rot['axis']=='z':
    theta=math.radians(rot['angle']);ox,oy,_=rot['origin']
    points=[(ox+(a-ox)*math.cos(theta)-(b-oy)*math.sin(theta),oy+(a-ox)*math.sin(theta)+(b-oy)*math.cos(theta)) for a,b in points]
  screen=[(round(cx+(a-8)*scale),round(cy-(b-8)*scale)) for a,b in points]
  back=[(a+int(scale*.22),b-int(scale*.21)) for a,b in screen]
  d.polygon(back,fill=shade(col,.57))
  d.polygon(screen,fill=col)
  d.line(screen+[screen[0]],fill=shade(col,.57),width=1)

panels=[(56,175,475,742),(539,175,958,742),(1022,175,1441,742)]
for r in panels:
 d.rounded_rectangle(r,fill=(29,32,47),outline=(153,107,52),width=2,radius=22)
model('skeletal_hand_left',265,540,13.3)
model('skeletal_hand_right',749,540,13.3)
model('solar_sword',1227,480,10.6)
for text,cx in [('LEFT SKELETAL HAND',265),('RIGHT SKELETAL HAND',749),('SUNFORGED RUNE SWORD',1227)]:
 w=d.textbbox((0,0),text,font=label)[2]
 d.text((cx-w/2,758),text,font=label,fill=(255,212,121))
d.text((55,817),'Editable Blockbench .bbmodel assets  •  front-facing cuboid render, not an in-game screenshot  •  Minecraft Java 1.21.11',font=ImageFont.truetype(reg,17),fill=(174,174,180))
out=root/'Solarius-3D-Models-Preview.png';im.save(out)
print(out)
