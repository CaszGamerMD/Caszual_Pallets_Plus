"""Top-down material review of real corner model geometry, including rotations."""
from PIL import Image,ImageDraw,ImageFont
from pathlib import Path
import math,json
root=Path(__file__).resolve().parents[1]
canvas=Image.new('RGB',(1100,1120),'#141b24');d=ImageDraw.Draw(canvas)
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',24)
small=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',15)
colors={'steel':'#55606b','dark':'#374149','rubber':'#20272c','edge':'#ccd5d8','wood':'#846449','yellow':'#dfb044'}
d.text((24,20),'REBUILT CORNERS / ACTUAL MODEL GEOMETRY',font=font,fill='#edf1f5')
for index,name in enumerate(('pallet_rail_left','pallet_rail_right','conveyor_left','conveyor_right')):
 x0=24+(index%2)*550;y0=75+(index//2)*510
 d.rounded_rectangle((x0,y0,x0+520,y0+490),12,fill='#26303a')
 m=json.loads((root/f'build/generated/transport/assets/caszual_additions/models/block/transport/{name}_item.json').read_text())
 for e in sorted(m['elements'],key=lambda a:a['to'][1]):
  x,y,z=e['from'];X,Y,Z=e['to'];points=[(x,z),(X,z),(X,Z),(x,Z)]
  if 'rotation' in e:
   rot=e['rotation'];ox,_,oz=rot['origin'];a=math.radians(rot['angle']);co,si=math.cos(a),math.sin(a)
   points=[(ox+(vx-ox)*co+(vz-oz)*si,oz-(vx-ox)*si+(vz-oz)*co) for vx,vz in points]
  points=[(x0+68+vx*24,y0+24+vz*24) for vx,vz in points]
  d.polygon(points,fill=colors[e['faces']['up']['texture'][1:]])
 d.text((x0+20,y0+455),name.replace('_',' ').title(),font=font,fill='#edf1f5')
d.text((24,1090),'Repository geometry with flat material swatches; not an in-game screenshot.',font=small,fill='#a9b8c2')
(ROOT if 'ROOT' in globals() else root).joinpath('build/previews').mkdir(parents=True,exist_ok=True)
canvas.save(root/'build/previews/corner-geometry-preview.png')
