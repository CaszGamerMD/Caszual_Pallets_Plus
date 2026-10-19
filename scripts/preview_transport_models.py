"""Orthographic geometry QA from the actual model elements (flat material swatches)."""
import json,math
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1]
COLORS={'steel':'#555e68','dark':'#394149','rubber':'#1c2024','edge':'#bfc7cb','yellow':'#dfb044','light':'#98efe8','red':'#bb4b45','glass':'#648d9c','wood':'#72513c','seat':'#357582','copper':'#b86c44'}
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',22)
small=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',14)
canvas=Image.new('RGB',(1500,1470),'#141b24');draw=ImageDraw.Draw(canvas)
entries=[('pallet_engine','Train engine'),('pallet_car','Three-pallet flatbed'),('passenger_car','Passenger carriage'),('pallet_drone','Pallet drone'),('conveyor_straight_item','Straight conveyor'),('conveyor_left_item','Left conveyor'),('pallet_rail_left_item','Left corner rail'),('drone_parking_bay_item','Drone parking bay'),('conveyor_right_item','Right corner conveyor'),('pallet_rail_right_item','Right corner rail'),('train_station_post','Train station post'),('pallet_transit_computer','Pallet transit computer')]
for idx,(name,label) in enumerate(entries):
 col,row=idx%4,idx//4;x0,y0=col*375,row*470+50
 model=json.loads((ROOT/f'src/main/resources/assets/caszual_additions/models/block/transport/{name}.json').read_text())
 faces=[];points=[]
 def project(p):
  x,y,z=p;return ((x-z)*.707,(x+z)*.3535-y*.866)
 for element in model['elements']:
  a,b=element['from'],element['to'];x,y,z=a;X,Y,Z=b
  if name in ('train_station_post','pallet_transit_computer'):x,X,z,Z=16-X,16-x,16-Z,16-z
  coords={'up':[(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],'south':[(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)],'east':[(X,y,z),(X,y,Z),(X,Y,Z),(X,Y,z)]}
  for face,verts in coords.items():
   color=COLORS[element['faces'][face]['texture'][1:]];rgb=tuple(int(color[i:i+2],16) for i in (1,3,5));shade={'up':1.12,'south':.8,'east':.98}[face];rgb=tuple(min(255,int(v*shade)) for v in rgb)
   if 'rotation' in element:
    rot=element['rotation'];ox,oy,oz=rot['origin'];a=math.radians(rot['angle']);co,si=math.cos(a),math.sin(a)
    verts=[(ox+(vx-ox)*co+(vz-oz)*si,vy,oz-(vx-ox)*si+(vz-oz)*co) for vx,vy,vz in verts]
   proj=[project(v) for v in verts];points+=proj
   depth=sum(v[0]+v[2]+v[1]*.6 for v in verts)/4
   faces.append((depth,proj,rgb))
 minx,maxx=min(x for x,y in points),max(x for x,y in points);miny,maxy=min(y for x,y in points),max(y for x,y in points)
 scale=min(320/(maxx-minx),330/(maxy-miny));ox=x0+187.5-(minx+maxx)*scale/2;oy=y0+220-(miny+maxy)*scale/2
 draw.rounded_rectangle((x0+8,y0+8,x0+367,y0+455),12,fill='#202a36')
 for _,vertices,c in sorted(faces):draw.polygon([(ox+x*scale,oy+y*scale) for x,y in vertices],fill=c)
 draw.text((x0+22,y0+400),label,fill='#edf1f5',font=font)
 draw.text((x0+22,y0+433),f'{len(model["elements"])} model elements',fill='#94a7ba',font=small)
draw.text((22,18),'PALLET TRANSPORT / MODEL GEOMETRY REVIEW',fill='#edf1f5',font=font)
draw.text((22,1450),'Rendered from repository geometry. Flat swatches approximate materials; this is not an in-game screenshot.',fill='#94a7ba',font=small)
canvas.save(ROOT/'docs/transport/model-geometry-preview.png')
