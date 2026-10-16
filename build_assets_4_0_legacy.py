#!/usr/bin/env python3
"""Generates Java 1.21.11 item models, textures and editable Blockbench source."""
import json, math, base64, io, uuid, zipfile, pathlib
from PIL import Image, ImageDraw, ImageFont
ROOT = pathlib.Path(__file__).parent
PACK = ROOT/'resource-pack'
TEXDIR = PACK/'assets/solarius/textures/item'
MODELDIR = PACK/'assets/solarius/models/item'
ITEMDIR = PACK/'assets/solarius/items'
BBDIR = ROOT/'models/blockbench'
for d in [TEXDIR,MODELDIR,ITEMDIR,BBDIR]: d.mkdir(parents=True,exist_ok=True)
# Inspired by the user's crimson-and-amber sword screenshot: original geometry and hand-painted pixels.
COLORS={
 'bone':(221,151,57),'bright':(255,213,82),'shadow':(93,48,31),
 'aura':(255,245,153),'grip':(78,38,39),'steel':(232,153,37),
 'ember':(250,111,37),'ruby':(143,37,49),'crimson':(188,44,44),
 'edge':(255,182,40),'ivory':(244,227,203),'joint':(120,78,47),
 'gold_dark':(156,98,34),'glow':(255,252,216)}
texsources={}
for name,color in COLORS.items():
 im=Image.new('RGBA',(16,16)); pix=im.load()
 for y in range(16):
  for x in range(16):
   wave=((x*7+y*5+x*y*3)%13)-6
   grain=wave*2
   edge=(4 if x in (0,15) or y in (0,15) else 0)
   pix[x,y]=tuple(max(0,min(255,c + grain + (5 if x<y else -5)-edge)) for c in color)+(255,)
 dst=TEXDIR/(name+'.png'); im.save(dst)
 texsources[name]='data:image/png;base64,'+base64.b64encode(dst.read_bytes()).decode()

def box(e, name, x1,y1,z1,x2,y2,z2, tex, group):
 # Java item model elements must stay in the supported -16 to 32 coordinate range.
 assert all(-16<=v<=32 for v in (x1,y1,z1,x2,y2,z2)),(name,(x1,y1,z1,x2,y2,z2))
 assert x1<x2 and y1<y2 and z1<z2, name
 e.append(dict(name=name, from_=[x1,y1,z1],to=[x2,y2,z2],tex=tex,group=group))

def make_hand(side):
    # Two separate boss-sized forearm and hand constructions, no torso/ribs/head.
    # Finger sections are separate Blockbench groups and can be individually rigged later.
    e=[]; mirror=side=='left'
    def B(name,x1,y1,z1,x2,y2,z2,tex='bone',group='Palm'):
        if mirror: x1,x2=16-x2,16-x1
        box(e,name,x1,y1,z1,x2,y2,z2,tex,group)
    # Jagged disconnected radius / ulna bones, floating gilded energy inside.
    for j,(xx,ww) in enumerate([(3.2,3.2),(9.4,3.2)]):
        grp='Radius' if j==0 else 'Ulna'
        B(grp+' major shaft',xx,20,6.7,xx+ww,29.4,9.6,'bone',grp)
        B(grp+' knobbly distal',xx-.9,18.3,6.2,xx+ww+.5,21.3,10,'steel',grp)
        B(grp+' angular elbow head',xx-1.1,28.5,5.7,xx+ww+.9,31.7,10.5,'bone',grp)
        B(grp+' worn tip top',xx-.45,30.8,6.8,xx+ww+.45,32,9.6,'ivory',grp)
        B(grp+' gilded raised stripe',xx+.95,22,5.9,xx+1.65,29,6.85,'bright',grp)
        B(grp+' inner shadow groove',xx+1.3,22.3,9.6,xx+1.9,27.7,10.3,'joint',grp)
        for k,yy in enumerate([23,26.6]):
            B(grp+f' armored ridge {k+1}',xx-.4,yy,6.15,xx+ww+.35,yy+.75,10,'gold_dark',grp)
            B(grp+f' glowing rivet {k+1}',xx+.8,yy+.11,5.9,xx+1.65,yy+.62,6.45,'glow',grp)
    B('Between-bones solar light',7.25,22,7.45,8.8,28.8,8.4,'aura','Wrist')
    B('Gapped wrist bridge',4.2,17.3,7.0,11.7,19.4,9.65,'joint','Wrist')
    B('Cuff lower rim',2.9,17.4,5.8,13.1,19.1,10.4,'gold_dark','Wrist')
    B('Cuff top plate',3.3,18.7,5.5,12.7,20.4,10.65,'steel','Wrist')
    B('Cuff bevel inner',4.1,18.9,5.1,12,20,5.8,'bright','Wrist')
    for xx in [4.2,6.6,9.0,11.3]:
        B(f'Wrist rivet {xx}',xx,19.1,4.8,xx+.6,19.8,5.3,'glow','Wrist')
    # Bones in the palm remain visible through the armor gaps.
    for i,(xx,w) in enumerate([(3.1,2.0),(5.8,2.0),(8.5,2.0),(11.1,1.65)]):
        B(f'Palm metacarpal {i+1}',xx,10.6,7.4,xx+w,17.7,9.3,'bone','Palm')
        B(f'Palm dark between bones {i+1}',xx+.65,12.3,9.3,xx+1.04,17.1,9.75,'shadow','Palm')
        B(f'Palm ridge {i+1}',xx+.3,14,6.7,xx+w-.15,16.2,7.45,'bright','Palm')
    B('Palm heel shell',3.0,15.7,7.1,13.1,17.95,10.2,'gold_dark','Palm')
    B('Palm knuckle plate',2.3,9.6,6.45,13.35,11.5,10.35,'gold_dark','Palm')
    B('Palm knuckle gold rim',2.6,9.8,5.8,13.1,11.25,6.5,'bright','Palm')
    B('Sacred dorsal crest outer',6.05,12,5.8,10.45,16.6,6.8,'shadow','Palm')
    B('Sacred dorsal crest bronze',6.5,12.5,5.25,10,16.05,5.9,'edge','Palm')
    B('Sacred dorsal crest gemstone',7.2,13.25,4.65,9.35,15.45,5.3,'glow','Palm')
    # Four gripping fingers with distinguishable 3-phalange silhouettes and knuckle jewels.
    finger_data=[(2.15,2.25,1.1),(4.97,2.25,.1),(7.87,2.25,.05),(10.78,2.2,1.45)]
    for i,(xx,w,shorten) in enumerate(finger_data):
        g=f'Finger {i+1}'
        B(f'{g} gilded knuckle',xx-.35,9.0,6.7,xx+w+.35,11.4,10.05,'steel',g)
        B(f'{g} inner knuckle joint',xx+.45,9.2,6.02,xx+w-.4,10.8,6.75,'ruby',g)
        B(f'{g} proximal phalanx',xx,5.7+shorten,7.0,xx+w,9.7,9.15,'bone',g)
        B(f'{g} proximal highlight',xx+.45,6.2+shorten,6.65,xx+w-.45,9.15,7.05,'ivory',g)
        B(f'{g} mid-joint ball',xx-.2,5.0+shorten,6.65,xx+w+.2,6.25+shorten,9.4,'joint',g)
        B(f'{g} joint glint',xx+.35,5.2+shorten,6.28,xx+w-.3,6.0+shorten,6.67,'edge',g)
        B(f'{g} middle phalanx',xx+.15,2.4+shorten,6.55,xx+w-.15,5.15+shorten,8.8,'bone',g)
        B(f'{g} middle raised bone',xx+.47,2.65+shorten,6.16,xx+w-.5,4.3+shorten,6.6,'ivory',g)
        B(f'{g} distal joint',xx+.26,1.85+shorten,6.1,xx+w-.26,2.95+shorten,8.75,'steel',g)
        B(f'{g} claw talon curl',xx+.42,0+shorten,5.4,xx+w-.42,2.15+shorten,7.15,'bone',g)
        B(f'{g} talon gold cutting point',xx+.7,-.95+shorten,5.0,xx+w-.7,.85+shorten,5.65,'bright',g)
        B(f'{g} knuckle spike',xx+.6,10.65,6.7,xx+w-.6,12.25,7.3,'gold_dark',g)
    # Opposed articulated thumb reaching toward the sword grip.
    for name,x1,y1,z1,x2,y2,z2,tex in [
      ('Thumb saddle',-.2,11.4,6.1,3.5,15.8,10.3,'bone'),
      ('Thumb armor plate',-.3,12.3,5.55,2.5,15.3,6.3,'steel'),
      ('Thumb proximal',-.7,8.5,6.2,2.8,12.0,9.1,'bone'),
      ('Thumb first joint',-.9,7.6,5.9,2.0,9.3,9.4,'joint'),
      ('Thumb thumbprint light',-.75,8.2,5.35,1.1,9.4,6.1,'ivory'),
      ('Thumb middle segment',.3,5.9,5.1,3.25,8.0,8.0,'bone'),
      ('Thumb hook tip',1.7,4.6,4.5,4.55,6.7,6.6,'bright'),
      ('Thumb dark gap',1.3,5.9,7.6,2.1,7,8.4,'shadow')]:
        B(name,x1,y1,z1,x2,y2,z2,tex,'Thumb')
    # Floating solar fragments and spike ornaments imply a supernatural boss hand.
    for i,(xx,yy) in enumerate([(1.2,20.2),(13.2,21.1),(1.4,27.8),(13.1,28.4)]):
        B(f'Floating gold bone fragment {i}',xx,yy,7.1,xx+1.0,yy+1.6,8.3,'bright','Aura shards')
    B('Solar forearm rune glow',7.0,26.1,6.0,9.0,28.6,7.0,'aura','Aura shards')
    B('Solar forearm rune center',7.5,26.75,5.65,8.5,28,6.1,'glow','Aura shards')
    return e

def make_sword():
    # Pixel-art flameblade silhouette based on the user's screenshot, recreated as original geometry.
    e=[]
    def B(name,x1,y1,z1,x2,y2,z2,tex='gold_dark',group='Blade'):
        box(e,name,x1,y1,z1,x2,y2,z2,tex,group)
    B('Obsidian wine leather grip',7.0,-12.9,6.65,9.0,-.2,9.4,'grip','Handle')
    for i,y in enumerate([-11.4,-8.7,-6,-3.3]):
        B(f'Brass grip diamond wrap {i+1}',6.65,y,6.25,9.35,y+0.9,9.8,'ruby','Handle')
        B(f'Gold binding {i+1}',7.0,y+.12,5.95,9.0,y+.55,6.32,'edge','Handle')
    B('Broad pommel dark base',5.75,-15.6,6.15,10.25,-12.4,9.85,'ruby','Pommel')
    B('Pommel amber inset',6.5,-15,5.7,9.5,-13.0,6.25,'edge','Pommel')
    B('Pommel sunrise highlight',7.35,-14.65,5.46,8.65,-13.3,5.8,'glow','Pommel')
    B('Guard root dark metal',4.0,-.5,5.75,12,4.3,10.3,'ruby','Crossguard')
    B('Wide left wing dark red',-.6,1.0,6.1,6.1,4.8,10.2,'ruby','Crossguard')
    B('Wide right wing dark red',9.9,1.0,6.1,16.6,4.8,10.2,'ruby','Crossguard')
    B('Left sweeping upper wing',.4,4.0,6.65,5.6,7.3,9.6,'crimson','Crossguard')
    B('Right sweeping upper wing',10.4,4.0,6.65,15.6,7.3,9.6,'crimson','Crossguard')
    B('Left gold wing edge',-.6,4.15,5.85,5.0,5.0,6.55,'edge','Crossguard')
    B('Right gold wing edge',11.0,4.15,5.85,16.6,5.0,6.55,'edge','Crossguard')
    B('Left curled guard fluke',.0,6.7,7.0,2.1,9.1,9.2,'ember','Crossguard')
    B('Right curled guard fluke',13.9,6.7,7.0,16,9.1,9.2,'ember','Crossguard')
    B('Sun crystal dark diamond frame',5.3,1.3,4.7,10.7,8.0,6.3,'ruby','Sun gem')
    B('Crystal beveled outer amber',5.9,2.0,4.25,10.1,7.3,5.0,'edge','Sun gem')
    B('Crystal faceted orange',6.45,2.8,3.8,9.55,6.8,4.28,'ember','Sun gem')
    B('Crystal gold inner reflection',7.12,3.35,3.42,8.88,6.35,3.9,'bright','Sun gem')
    B('Crystal center sun spark',7.6,4.03,3.14,8.4,5.65,3.47,'glow','Sun gem')
    # The broad red blade is edged with stepped orange and gold bands.
    B('Blade scarlet core base',5.4,7.1,6.65,10.6,25.8,9.4,'crimson','Blade')
    B('Blade scarlet center raised',6.15,9.1,6.05,9.85,27.9,10,'ruby','Blade')
    B('Left fiery bevel',4.5,8.4,6.95,5.5,27,9.1,'ember','Blade')
    B('Right fiery bevel',10.5,8.4,6.95,11.5,27,9.1,'ember','Blade')
    B('Left gilded cutting edge',3.45,10.25,7.34,4.55,25.65,8.7,'edge','Blade')
    B('Right gilded cutting edge',11.45,10.25,7.34,12.55,25.65,8.7,'edge','Blade')
    B('Left stepped lower edge',4.1,8.0,7.15,5.0,12.2,9,'bright','Blade')
    B('Right stepped lower edge',11.0,8,7.15,11.9,12.2,9,'bright','Blade')
    B('Left stepped upper edge',4.2,24.7,7.2,5.1,28.1,9,'edge','Blade')
    B('Right stepped upper edge',10.9,24.7,7.2,11.8,28.1,9,'edge','Blade')
    # Irregular, stepped solar-flame wings make the silhouette close to the
    # reference: broad crimson blade, jagged molten-orange shoulders and gold trim.
    for i,(yy,wing) in enumerate([(12.3,1.6),(16.5,1.95),(21.1,1.35),(25.0,1.6)]):
        B(f'Left flame wing {i}',3.35-wing,yy,7.45,4.28,yy+2.0,8.75,'ember','Flame edges')
        B(f'Left wing gilded spark {i}',3.25-wing,yy+1.3,7.15,3.70-wing,yy+2.5,9.0,'bright','Flame edges')
        B(f'Right flame wing {i}',11.72,yy,7.45,12.65+wing,yy+2.0,8.75,'ember','Flame edges')
        B(f'Right wing gilded spark {i}',12.3+wing,yy+1.3,7.15,12.8+wing,yy+2.5,9.0,'bright','Flame edges')
    B('Crimson inner facet left',5.23,13.1,6.22,6.16,25.7,7.05,'ruby','Blade')
    B('Crimson inner facet right',9.85,13.1,6.22,10.8,25.7,7.05,'ruby','Blade')
    B('Blade scarlet tip',5.2,26.0,7,10.8,29.55,9.5,'crimson','Blade tip')
    B('Blade ember tip',6.2,29.1,7.1,9.8,31.2,9.35,'ember','Blade tip')
    B('Blade fine cutting point',7.2,30.9,7.5,8.8,31.95,9.0,'edge','Blade tip')
    B('Blade left serrated shoulder',3.35,12.65,6.9,4.4,17.6,9.1,'edge','Blade')
    B('Blade right serrated shoulder',11.6,12.65,6.9,12.65,17.6,9.1,'edge','Blade')
    # Oversized ivory white runic inscription, pixel broken, within blood-red frame.
    B('Runic ivory central track',6.6,11.2,5.65,9.5,25.8,6.28,'ivory','Runes')
    B('Runic warm white highlight',7.25,11.3,5.38,8.6,25.5,5.66,'glow','Runes')
    for i,y in enumerate([12.5,15.5,18.5,21.5,24.3]):
        B(f'Rune carved horizontal notch {i}',6.05,y,5.15,10.0,y+.75,5.62,'ivory','Runes')
        if i % 2 == 0:
            B(f'Rune ruby gap {i}',7.35,y+.78,5.16,8.62,y+1.38,5.67,'ruby','Runes')
    B('Runic tilted stone inlay left',5.75,14.1,5.68,6.65,16.5,6.2,'ivory','Runes')
    B('Runic tilted stone inlay right',9.5,19.2,5.68,10.25,22.0,6.2,'ivory','Runes')
    # Blade spines/power flares add perceived thickness and boss-quality silhouette.
    for i,y in enumerate([11.2,16.0,20.8,25.4]):
        B(f'Backbone polished rib {i}',6.35,y,9.35,9.65,y+.85,10.0,'edge','Back spine')
        B(f'Ruby burning flank left {i}',4.1,y+.5,6.4,5.3,y+1.6,7.0,'ruby','Blade')
        B(f'Ruby burning flank right {i}',10.7,y+.5,6.4,11.9,y+1.6,7,'ruby','Blade')
    return e

def make_sun():
 e=[]
 def B(*args): box(e,*args)
 B('Core',5,5,5,11,11,11,'aura','Core')
 B('Core top',6,11,6,10,14,10,'bright','Core')
 B('Core bottom',6,2,6,10,5,10,'bright','Core')
 B('Core left',2,6,6,5,10,10,'bright','Core')
 B('Core right',11,6,6,14,10,10,'bright','Core')
 B('Core front',6,6,2,10,10,5,'ember','Core')
 B('Core back',6,6,11,10,10,14,'ember','Core')
 for i,(x,y,z) in enumerate([(8,15,8),(8,1,8),(15,8,8),(1,8,8),(8,8,15),(8,8,1)]):
  B(f'Sun ray {i+1}',x-.7,y-.7,z-.7,x+.7,y+.7,z+.7,'aura','Solar Rays')
 return e

# Sunforged IV custom geometry: a readable CRIMSON flameblade and massive
# free-floating skeletal gripping hands. Every cube is editable in Blockbench.
def make_hand(side):
    e=[]; mirror = side=='left'
    def B(name,x1,y1,z1,x2,y2,z2,tex='bone',group='Hand',rot=None):
        if mirror: x1,x2=16-x2,16-x1
        box(e,name,x1,y1,z1,x2,y2,z2,tex,group)
        if rot is not None:
            axis,angle,org=rot
            if mirror and axis=='z': angle=-angle
            e[-1]['rotation']={'origin':([16-org[0],org[1],org[2]] if mirror else list(org)), 'axis':axis,'angle':angle,'rescale':True}
    # Anatomical palm: skeletal metacarpal cage is deliberately open and
    # colored spaces reveal the air behind it (not a solid golden mitten).
    B('Metacarpal arch upper',2.6,17.1,7.1,13.4,19.0,10.3,'gold_dark','Palm cage')
    B('Palm lower arch',2.5,10.9,7.6,13.4,12.35,10.4,'gold_dark','Palm cage')
    B('Palm inner heel',3.2,15.2,8.5,12.8,17.2,10.2,'steel','Palm cage')
    for i,x in enumerate([3.4,6.15,8.9,11.6]):
        B(f'Metacarpal {i+1} gilded shaft',x,11.7,7.7,x+1.2,18.1,9.15,'bone',f'Metacarpal {i+1}')
        B(f'Metacarpal {i+1} ivory highlight',x+.23,13.0,7.2,x+.8,17.1,7.8,'ivory',f'Metacarpal {i+1}')
        B(f'Metacarpal {i+1} tendon groove',x+.43,12.35,9.15,x+.7,16.55,9.5,'shadow',f'Metacarpal {i+1}')
        B(f'Metacarpal {i+1} knuckle node',x-.45,10.3,7.0,x+1.65,12.0,9.95,'steel',f'Metacarpal {i+1}')
        B(f'Metacarpal {i+1} ember spot',x+.27,10.57,6.66,x+.88,11.45,7.1,'glow',f'Metacarpal {i+1}')
    # Sun emblem on dorsal side; rotated diamond-shaped jewel and knuckle filigree.
    B('Dorsal hexagon dark',5.85,14.25,6.35,10.15,17.9,7.25,'shadow','Dorsal gem',('z',45,(8,16.1,7.0)))
    B('Dorsal ruby bezel',6.6,14.8,5.91,9.45,17.36,6.42,'ruby','Dorsal gem',('z',45,(8,16.1,6.1)))
    B('Dorsal sun jewel',7.05,15.15,5.41,8.95,16.95,5.96,'bright','Dorsal gem',('z',45,(8,16.1,5.7)))
    B('Dorsal central flare',7.62,15.66,5.13,8.38,16.37,5.47,'glow','Dorsal gem',('z',45,(8,16.1,5.25)))
    # Each finger has 3 physically separated phalanges. The upper finger bone
    # sits behind the mid phalanx; the hooked talon advances toward the camera.
    # This silhouettes as a gripping claw instead of 4 rigid parallel rods.
    for i,(x,width,short) in enumerate([(2.0,2.25,1.0),(4.78,2.35,0.05),(7.68,2.35,0.15),(10.60,2.2,1.25)]):
        name=f'{["Index","Middle","Ring","Little"][i]} finger'
        B(f'{name} pearl knuckle',x-.26,9.52,7.5,x+width+.22,11.44,10.25,'bone',name)
        B(f'{name} gold knuckle armor',x,9.9,6.8,x+width,10.7,7.51,'edge',name)
        B(f'{name} proximal phalanx',x+.25,6.15+short,7.65,x+width-.16,9.8,9.52,'ivory',name)
        B(f'{name} proximal gilded line',x+.72,7.3+short,7.13,x+width-.64,9.15,7.68,'bright',name)
        B(f'{name} flexor ball',x+.07,5.56+short,7.2,x+width-.02,6.6+short,10.0,'joint',name)
        B(f'{name} knuckle cuff',x-.15,5.62+short,6.75,x+width+.14,6.22+short,9.65,'gold_dark',name)
        B(f'{name} middle bent segment',x+.23,3.42+short,5.55,x+width-.25,5.9+short,7.5,'bone',name,('x',-22.5,(x+width/2,5.55+short,7.2)))
        B(f'{name} middle bone white ridge',x+.65,4+short,5.03,x+width-.7,5.45+short,5.75,'ivory',name)
        B(f'{name} distal joint',x+.38,2.89+short,4.8,x+width-.34,3.6+short,6.85,'gold_dark',name)
        B(f'{name} hooked claw',x+.58,1.12+short,4.0,x+width-.55,3.07+short,5.92,'bone',name,('x',-22.5,(x+width/2,2.9+short,5.8)))
        B(f'{name} sharpened tip',x+.8,.65+short,3.55,x+width-.78,1.88+short,4.38,'bright',name)
        B(f'{name} inlaid bright jewel',x+.74,10.45,6.45,x+width-.74,11.2,6.81,'glow',name)
        B(f'{name} knuckle curling thorn',x+.87,11.6,7.25,x+width-.82,13.3,7.91,'gold_dark',name,('z',22.5,(x+width/2,11.9,7.4)))
    # Opposable thumb crosses in front of the blade grip, visibly gripping.
    B('Thumb carpometacarpal',-.10,12.5,7.7,3.72,17.25,10.32,'bone','Opposable thumb')
    B('Thumb protective flange',-.7,14.6,6.95,2.15,16.15,9.7,'steel','Opposable thumb')
    B('Thumb long proximal bone',-.70,9.8,6.75,2.42,14.0,8.95,'ivory','Opposable thumb',('z',-22.5,(1.25,11,8)))
    B('Thumb joint skull',-.25,8.7,6.3,2.15,10.35,8.8,'joint','Opposable thumb')
    B('Thumb wrapping middle',1.05,6.91,4.95,4.15,9.35,7.52,'bone','Opposable thumb',('z',-22.5,(2.9,8.2,6)))
    B('Thumb middle gold edge',1.8,7.1,4.45,3.5,8.3,5.06,'edge','Opposable thumb')
    B('Thumb final hook',3.65,6.0,4.11,5.55,7.3,6.0,'bright','Opposable thumb')
    B('Thumb ivory claw',4.45,5.72,3.7,5.97,6.36,4.35,'ivory','Opposable thumb')
    # 2 independent ulna/radius forearm bones, open dark separation in between.
    for i,(x,width) in enumerate([(4.0,2.7),(9.35,2.65)]):
        g=f'{["Radius","Ulna"][i]}'
        B(f'{g} open shaft',x,19.05,7.7,x+width,29.6,10.27,'bone',g)
        B(f'{g} ivory central crest',x+.5,20.3,7.12,x+width-.46,28.6,7.85,'ivory',g)
        B(f'{g} inner shadow groove',x+.6,21,10.2,x+1.3,28.4,10.72,'joint',g)
        B(f'{g} elbow socket',x-1.05,28.55,7.2,x+width+.95,31.5,10.85,'steel',g)
        B(f'{g} bone white skullcap',x-.25,30.6,7.3,x+width+.15,32,10.35,'ivory',g)
        for j,yy in enumerate([22.15,25.75]):
            B(f'{g} plated ring {j+1}',x-.5,yy,7.0,x+width+.55,yy+1.0,10.8,'gold_dark',g)
            B(f'{g} gilded shining ridge {j+1}',x-.2,yy+.14,6.67,x+width+.2,yy+.55,7.07,'bright',g)
            B(f'{g} sun rivet {j+1}',x+.9,yy+.2,6.35,x+1.45,yy+.8,6.7,'glow',g)
    B('Wrist socket transverse',3.0,18.3,7.3,13.05,20.5,10.6,'gold_dark','Wrist halo')
    B('Wrist cuff top highlight',3.6,19.2,6.4,12.5,20.1,7.42,'bright','Wrist halo')
    B('Wrist cuff gold boss',6.7,19,5.8,9.3,20.9,6.4,'ruby','Wrist halo')
    B('Wrist cuff jewel',7.2,19.3,5.33,8.85,20.6,5.85,'glow','Wrist halo')
    B('Floating prana link',7.4,21.5,7.8,8.65,28.2,9.6,'aura','Solar energy')
    for i,(x,y,z) in enumerate([(1.8,20.2,7.2),(13.4,20.7,7.2),(1.2,27.5,8.1),(13.7,28,8.2),(.5,22.9,9.1),(15.1,24.1,9.1)]):
        B(f'Free-floating sun splinter {i+1}',x,y,z,x+.58,y+1.66,z+.65,'bright','Solar energy',('z',22.5 if i%2 else -22.5,(x+.29,y+.85,z+.25)))
    B('Floating solar rune ring upper',6.95,28.1,5.9,9.15,29.18,6.5,'edge','Solar energy')
    B('Floating solar rune center',7.45,26.9,5.45,8.55,28.0,6.0,'glow','Solar energy')
    return e

def make_sword():
    e=[]
    def B(name,x1,y1,z1,x2,y2,z2,tex='gold_dark',group='Blade',rot=None):
        box(e,name,x1,y1,z1,x2,y2,z2,tex,group)
        if rot is not None:
            axis,angle,origin=rot
            e[-1]['rotation']={'origin':list(origin),'axis':axis,'angle':angle,'rescale':True}
    # Screenshot proportions: short wine-red handle, oversized forward wings,
    # big orange gem diamond, wide fiery blade with white runic core.
    B('Dark cherry grip',7.0,-12.85,7.0,9.0,-1.35,9.4,'grip','Hilt')
    B('Grip crimson front ridge',7.44,-11.7,6.45,8.6,-2.1,7.1,'ruby','Hilt')
    for i,y in enumerate([-11.15,-8.85,-6.55,-4.3]):
        B(f'Burgundy leather grip band {i}',6.67,y,6.68,9.36,y+.72,9.7,'ruby','Hilt')
        B(f'Grip golden stud {i}',7.65,y+.1,6.3,8.35,y+.54,6.7,'edge','Hilt')
    B('Pommel oxidized crimson',5.85,-15.65,6.15,10.2,-12.2,10.0,'ruby','Pommel')
    B('Pommel scarlet flanged horns',4.65,-15.1,6.6,6.2,-12.3,9.6,'crimson','Pommel')
    B('Pommel scarlet flanged horns right',9.8,-15.1,6.6,11.35,-12.3,9.6,'crimson','Pommel')
    B('Pommel brass central seal',7.1,-15.28,5.6,8.9,-13.4,6.3,'edge','Pommel')
    B('Pommel diamond',7.56,-14.8,5.12,8.43,-13.8,5.68,'glow','Pommel',('z',45,(8,-14.3,5.4)))
    B('Guard center forged red',4.25,-.55,6.1,11.75,4.55,10.5,'ruby','Sun guard')
    B('Guard central scarlet face',5.3,.22,5.7,10.7,4.25,6.18,'crimson','Sun guard')
    # Flared wing guard nearly as large as blade shoulders; stacked flame feathers.
    for i,sg in enumerate([-1,1]):
        if sg==-1:
            B('Left guard wing burgundy',-.65,1.7,6.3,5.45,5.2,10.2,'ruby','Sun guard')
            B('Left guard flame rise',.1,4.58,6.25,3.3,8.4,9.63,'crimson','Sun guard')
            B('Left guard golden edge',-.85,4.5,5.69,4.75,5.22,6.3,'edge','Sun guard')
            B('Left guard horn hot tip',-.75,7.95,6.2,1.0,9.95,9.2,'ember','Sun guard')
            B('Left guard inner bronze curled wing',2.8,5.65,5.9,5.9,8.25,6.6,'ember','Sun guard')
        else:
            B('Right guard wing burgundy',10.55,1.7,6.3,16.65,5.2,10.2,'ruby','Sun guard')
            B('Right guard flame rise',12.7,4.58,6.25,15.9,8.4,9.63,'crimson','Sun guard')
            B('Right guard golden edge',11.25,4.5,5.69,16.85,5.22,6.3,'edge','Sun guard')
            B('Right guard horn hot tip',15.0,7.95,6.2,16.75,9.95,9.2,'ember','Sun guard')
            B('Right guard inner bronze curled wing',10.1,5.65,5.9,13.2,8.25,6.6,'ember','Sun guard')
    # Stand-out faceted rotated jewel, visually diamond rather than flat rectangle.
    B('Sun guard dark diamond bezel',5.55,1.2,5.26,10.45,7.7,6.45,'ruby','Crimson sun gem',('z',45,(8,4.45,5.85)))
    B('Sun guard metallic bronze bezel',6.05,1.85,4.83,9.95,7.0,5.4,'steel','Crimson sun gem',('z',45,(8,4.43,5.12)))
    B('Amber crystal big face',6.6,2.5,4.28,9.4,6.45,4.85,'ember','Crimson sun gem',('z',45,(8,4.47,4.56)))
    B('Yellow inner gem core',7.05,3.02,3.74,8.94,5.96,4.3,'bright','Crimson sun gem',('z',45,(8,4.5,4.03)))
    B('White center glimmer',7.65,3.8,3.36,8.33,5.02,3.79,'glow','Crimson sun gem',('z',45,(8,4.5,3.5)))
    # Broad flameblade. It's intentionally thin in Z (an actual sword), not a square rod.
    B('Blade deep red core volume',5.15,7.3,7.05,10.86,28.0,9.72,'ruby','Blade core')
    B('Blade front crimson plateau',5.70,8.8,6.25,10.3,29.4,7.1,'crimson','Blade core')
    B('Blade right inner ember',10.25,9.55,6.45,11.65,28.4,8.9,'ember','Blade core')
    B('Blade left inner ember',4.35,9.55,6.45,5.75,28.4,8.9,'ember','Blade core')
    B('Blade gold outside left',3.7,11.0,7.1,4.65,29.2,8.8,'edge','Gold cutting edges')
    B('Blade gold outside right',11.35,11.0,7.1,12.3,29.2,8.8,'edge','Gold cutting edges')
    for i,(yy,spread) in enumerate([(9.7,1.7),(13.25,2.55),(17.45,1.7),(22.6,2.8),(27.05,1.4)]):
        B(f'Left jagged red fire tooth {i}',3.1-spread,yy,7.35,4.75,yy+2.8,9.0,'crimson','Crimson flame serrations')
        B(f'Left orange lick {i}',2.75-spread,yy+.8,7.0,3.55,yy+2.85,8.78,'ember','Crimson flame serrations')
        B(f'Left top golden cutting lip {i}',2.55-spread,yy+1.75,6.75,3.08-spread,yy+3.0,9.0,'edge','Gold cutting edges')
        B(f'Right jagged red fire tooth {i}',11.25,yy,7.35,12.9+spread,yy+2.8,9.0,'crimson','Crimson flame serrations')
        B(f'Right orange lick {i}',12.45,yy+.8,7.0,13.25+spread,yy+2.85,8.78,'ember','Crimson flame serrations')
        B(f'Right top golden cutting lip {i}',12.92+spread,yy+1.75,6.75,13.45+spread,yy+3.0,9.0,'edge','Gold cutting edges')
    B('Blade front gold lower shoulder L',4.05,8.0,6.18,5.05,15.0,7.05,'bright','Gold cutting edges')
    B('Blade front gold lower shoulder R',10.95,8.0,6.18,11.95,15.0,7.05,'bright','Gold cutting edges')
    # A luminous pale rune stripe: broken ivory diamonds on a burgundy line.
    B('Rune wine red channel',6.15,10.3,5.7,9.85,28.2,6.34,'ruby','Ancient script')
    B('Rune ivory channel long',6.75,11.35,5.1,9.4,27.55,5.7,'ivory','Ancient script')
    B('Rune warm pearly reflection',7.65,11.55,4.78,8.62,27.2,5.14,'glow','Ancient script')
    for i, yy in enumerate([12.0,15.15,18.3,21.5,24.7]):
        B(f'Ancient script ivory cross {i}',6.0,yy,4.73,10.18,yy+.72,5.22,'ivory','Ancient script')
        B(f'Ancient script ruby missing glyph {i}',7.22,yy+.75,4.52,8.6,yy+1.45,5.23,'ruby','Ancient script')
        B(f'Rune golden tick {i}',6.15,yy+1.35,5.45,6.55,yy+1.95,6.06,'edge','Ancient script')
    for i,yy in enumerate([11.35,15.45,19.6,24.3]):
        B(f'Crimson blade facet left {i}',5.28,yy,5.7,6.0,yy+2.55,6.3,'ruby','Blade details')
        B(f'Crimson blade facet right {i}',10.0,yy,5.7,10.72,yy+2.55,6.3,'ruby','Blade details')
        B(f'Gold trailing ridge {i}',6.0,yy,9.45,10.0,yy+.65,10.16,'gold_dark','Blade details')
    B('Wide crimson flameblade shoulder tip',5.4,28.4,6.3,10.65,31.1,9.48,'crimson','Blade crown')
    B('Orange solar point',6.18,30.15,6.7,9.82,31.7,9.0,'ember','Blade crown')
    B('Golden cutting diamond tip',7.28,30.7,7.0,8.72,32,8.8,'edge','Blade crown')
    return e

ASSETS={
 'skeletal_hand_left':make_hand('left'),
 'skeletal_hand_right':make_hand('right'),
 'solar_sword':make_sword(),
 'sun_orb':make_sun(),
}
# Face orientation and UVs compatible with vanilla cuboid Java item models.
face_names=['north','south','east','west','up','down']
for name,elems in ASSETS.items():
 textures={k:'solarius:item/'+k for k in COLORS}
 textures['particle']='solarius:item/bone'
 elements=[]
 for e in elems:
  from_,to=e['from_'],e['to']
  elements.append({'name':e['name'],'from':from_,'to':to,
                   'faces':{f:{'uv':[0,0,16,16],'texture':'#'+e['tex']} for f in face_names},**({'rotation':e['rotation']} if 'rotation' in e else {})})
 model={'credit':'PlattSMP Solarius 4.0 — original rune flameblade and animated skeletal constructs','ambientocclusion':False,
        'textures':textures,'elements':elements,
        'display':{
            'gui':{'rotation':[25,35,0],'translation':[0,0,0],'scale':[.55,.55,.55]},
            'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.36,.36,.36]},
            'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[.85,.85,.85]},
            'thirdperson_righthand':{'rotation':[0,90,-35],'translation':[0,1.8,0],'scale':[.68,.68,.68]},
            'firstperson_righthand':{'rotation':[0,-90,25],'translation':[1.5,1,0],'scale':[.62,.62,.62]},
            'none':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]}}}
 (MODELDIR/(name+'.json')).write_text(json.dumps(model,indent=2)+'\n')
 (ITEMDIR/(name+'.json')).write_text(json.dumps({'model':{'type':'minecraft:model','model':'solarius:item/'+name}},indent=2)+'\n')
 # Editable Blockbench Java block/item project; the item models are authoritative assets.
 elem_bb=[]
 indices=list(COLORS.keys()); group_to_cubes={}
 for e in elems:
  uid=str(uuid.uuid4()); texidx=indices.index(e['tex']); group_to_cubes.setdefault(e['group'],[]).append(uid)
  elem_bb.append({'name':e['name'],'type':'cube','uuid':uid,'from':e['from_'],'to':e['to'],
                  'origin':[8,8,8],'color':texidx,'autouv':0,
                  'faces':{f:{'uv':[0,0,16,16],'texture':texidx} for f in face_names},**({'origin':e['rotation']['origin'], 'rotation':[e['rotation']['angle'] if ax == e['rotation']['axis'] else 0 for ax in 'xyz']} if 'rotation' in e else {})})
 groups=[]; outliner=[]
 for group,children in group_to_cubes.items():
  gid=str(uuid.uuid4()); groups.append({'name':group,'uuid':gid,'origin':[8,8,8],'color':0,'export':True,'visibility':True})
  outliner.append({'uuid':gid,'isOpen':True,'children':children})
 bb={'meta':{'format_version':'5.0','model_format':'java_block','box_uv':False},'name':name,
     'resolution':{'width':16,'height':16},'elements':elem_bb,'groups':groups,'outliner':outliner,
     'textures':[{'path':'','name':k+'.png','folder':'','namespace':'solarius','id':str(i),
                  'particle':False,'render_mode':'default','visible':True,'mode':'bitmap','saved':True,
                  'uuid':str(uuid.uuid4()),'source':texsources[k]} for i,k in enumerate(indices)]}
 (BBDIR/(name+'.bbmodel')).write_text(json.dumps(bb,separators=(',',':'))+'\n')

(PACK/'pack.mcmeta').write_text(json.dumps({'pack':{'min_format':[75,0],'max_format':[75,0],
     'description':'☀ Solarius 4.0 | Crimson-gold flameblade and celestial skeletal hands'}},indent=2)+'\n')
im=Image.new('RGBA',(128,128),(29,22,14,255)); d=ImageDraw.Draw(im)
for r in (49,43,37): d.ellipse([64-r,64-r,64+r,64+r],outline=(230,168,31,255),width=2)
d.ellipse([39,39,89,89],fill=(236,169,26,255),outline=(255,232,104,255),width=4)
for k in range(12):
 a=2*math.pi*k/12; x1,y1=64+52*math.cos(a),64+52*math.sin(a); x2,y2=64+61*math.cos(a),64+61*math.sin(a)
 d.line([x1,y1,x2,y2],fill=(255,209,62),width=5)
d.line([63,31,63,97],fill=(83,46,20),width=7);d.line([63,37,63,78],fill=(255,247,164),width=4)
d.line([47,77,79,77],fill=(83,46,20),width=8); d.line([48,76,78,76],fill=(255,211,79),width=4)
im.save(PACK/'pack.png')

with zipfile.ZipFile(ROOT/'Solarius-ResourcePack-1.21.11.zip','w',zipfile.ZIP_DEFLATED) as z:
 for f in PACK.rglob('*'):
  if f.is_file(): z.write(f,f.relative_to(PACK))
print('Generated:', ', '.join(f'{name}: {len(ele)} cuboids' for name,ele in ASSETS.items()))
print('Pack size:',(ROOT/'Solarius-ResourcePack-1.21.11.zip').stat().st_size)
