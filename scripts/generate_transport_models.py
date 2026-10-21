"""Reproducible code-native Minecraft models; positions use block units for vehicles.
Detailed geometry, vanilla materials, separated decal faces, no flat item sprites.
"""
import json,math,os
from pathlib import Path
ROOT=Path(os.environ.get('PALLETS_PLUS_RESOURCE_DIR',Path(__file__).resolve().parents[1]/'build/generated/transport'))
AS=ROOT/'assets/caszual_additions'
T={'steel':'minecraft:block/polished_deepslate','dark':'minecraft:block/gray_concrete','rubber':'minecraft:block/black_concrete','edge':'minecraft:block/iron_block','yellow':'minecraft:block/yellow_concrete','light':'minecraft:block/sea_lantern','red':'minecraft:block/red_concrete','glass':'minecraft:block/light_blue_stained_glass','wood':'minecraft:block/stripped_spruce_log','seat':'minecraft:block/cyan_wool','copper':'minecraft:block/cut_copper','particle':'minecraft:block/polished_deepslate'}
def write(path,obj):
 p=ROOT/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,separators=(',',':'))+'\n')
def cube(a,b,tex='steel'):
 assert all(a[i]<b[i] for i in range(3)),(a,b)
 return {'from':[round(v,4) for v in a],'to':[round(v,4) for v in b],'faces':{f:{'texture':'#'+tex,'uv':[0,0,16,16]} for f in ['north','south','east','west','up','down']}}
def model(name,els,display=None):
 obj={'ambientocclusion':True,'textures':T,'elements':els}
 if display:obj['display']=display
 write(f'assets/caszual_additions/models/block/transport/{name}.json',obj)
def item(name,ref):write(f'assets/caszual_additions/items/{name}.json',{'model':{'type':'minecraft:model','model':'caszual_additions:block/transport/'+ref}})
DISPLAY={'gui':{'rotation':[25,225,0],'translation':[0,0,0],'scale':[.68,.68,.68]},'ground':{'scale':[.35,.35,.35]},'fixed':{'rotation':[0,90,0],'scale':[.55,.55,.55]},'thirdperson_righthand':{'rotation':[75,45,0],'scale':[.35,.35,.35]},'firstperson_righthand':{'rotation':[0,45,0],'scale':[.45,.45,.45]}}
names=['conveyor_hold','conveyor_logic','conveyor_straight','conveyor_left','conveyor_right','conveyor_t_junction','conveyor_up','conveyor_down','pallet_rail','pallet_rail_index','pallet_rail_left','pallet_rail_right','drone_parking_bay']
for name in names:
 variants={};full=[]
 for part in range(9):
  col,row=part%3,part//3;els=[]
  rise=1 if name.endswith('_up') else -1 if name.endswith('_down') else 0
  def add(a,b,t='steel'):els.append(cube(a,b,t))
  if name.endswith('_left') or name.endswith('_right'):
   rail=name.startswith('pallet_rail');right=name.endswith('_right')
   def band(inner,outer,bottom,top,texture):
    # Analytic circle edges, quarter-pixel strips. One continuous UV field
    # prevents a whole iron/wood texture repeating on every tiny slice.
    step=.25;last=None
    for iz in range(64):
     z=iz*step;global_z=row*16+z+step*.5;distance=48-global_z
     if distance>=outer:continue
     lo=math.sqrt(max(0,inner*inner-distance*distance));hi=math.sqrt(outer*outer-distance*distance)
     if right:lo,hi=48-hi,48-lo
     lo=max(0,lo-col*16);hi=min(16,hi-col*16)
     if hi-lo<.0001:continue
     e=cube([lo,bottom,z],[hi,top,z+step],texture)
     for face in ('up','down'):
      e['faces'][face]['uv']=[(col*16+lo)/3,(row*16+z)/3,(col*16+hi)/3,(row*16+z+step)/3]
     if last is not None and last['from'][0]==e['from'][0] and last['to'][0]==e['to'][0] and last['to'][2]==e['from'][2]:
      last['to'][2]=e['to'][2]
      for face in ('up','down'):last['faces'][face]['uv'][3]=e['faces'][face]['uv'][3]
     else:els.append(e);last=e
   def radial(theta,radius,length,width,bottom,top,texture,tangent=0):
    # Solid sleepers/cleats use vanilla-supported element rotations.
    if part!=4:return
    angle=math.radians(theta);cx=radius*math.cos(angle)+tangent*math.sin(angle);cz=48-radius*math.sin(angle)+tangent*math.cos(angle)
    if right:cx=48-cx
    rotation=-theta if right else theta
    w,d=length,width
    if rotation>45:rotation-=90;w,d=d,w
    if rotation<-45:rotation+=90;w,d=d,w
    cx-=16;cz-=16
    if min(cx-w/2,cz-d/2)<-16 or max(cx+w/2,cz+d/2)>32:
     radial(theta,radius-length/4,length/2,width,bottom,top,texture,tangent)
     radial(theta,radius+length/4,length/2,width,bottom,top,texture,tangent)
     return
    e=cube([cx-w/2,bottom,cz-d/2],[cx+w/2,top,cz+d/2],texture)
    if rotation:e['rotation']={'origin':[round(cx,4),0,round(cz,4)],'axis':'y','angle':rotation,'rescale':False}
    els.append(e)
   if rail:
    # Gauge matches straight rails exactly, including the asymmetric
    # 10/37-pixel centers after a clockwise/counterclockwise rotation.
    radii=(11,38) if right else (10,37)
    for theta in (0,22.5,45,67.5,90):
     tangent=-1.5 if theta==0 else 1.5 if theta==90 else 0
     radial(theta,24,34,2.4,0,1.2,'wood',tangent)
     for radius in radii:
      radial(theta,radius,5,2.6,1.2,1.65,'dark',tangent)
    for radius in radii:
     band(radius-1,radius+1,1.2,3,'steel')
     band(radius-1.6,radius+1.6,3,3.7,'edge')
   else:
    # A continuous curved belt avoids the old serrated roller fan.
    band(0,48,0,1.4,'steel')
    band(2,46,1.4,3.7,'rubber')
    band(0,2,1.4,4.4,'steel');band(46,48,1.4,4.4,'steel')
    band(.5,1.5,4.4,4.65,'yellow');band(46.5,47.5,4.4,4.65,'yellow')
    for theta in (0,22.5,45,67.5,90):
     tangent=-.7 if theta==0 else .7 if theta==90 else 0
     radial(theta,24,42,1,3.7,4,'dark',tangent)
    # Full-width end rollers match the straight deck's material and height.
    radial(0,24,44,1.2,2.5,4,'edge',-1)
    radial(90,24,44,1.2,2.5,4,'edge',1)
  elif name=='conveyor_t_junction':
   # Three-arm reversible-layout MERGE: the south side is deliberately
   # closed in the NORTH-facing model. One inlet arrives from the stem
   # (north) and another from either wing; which side is OUT is selectable.
   add([0,0,0],[16,2.2,16],'steel')
   add([.2,2.2,.2],[15.8,3.6,15.8],'dark')
   for j in range(8):
    z=j*2
    add([.6,3.62,z+.1],[15.4,3.95,z+1.4],'edge')
   if (col,row) in ((1,0),(0,1),(2,1)):
    add([1,4.02,1],[15,4.28,3],'yellow')
   if part==4:
    # Clear T engraving: long crossbar and northbound stem.
    add([.8,4.01,6],[15.2,4.33,8],'yellow')
    add([7,4.01,.8],[9,4.33,14.3],'yellow')
    add([6,4.4,6],[10,4.6,10],'steel')
  elif name=='drone_parking_bay':
   add([0,0,0],[16,2.5,16],'steel');add([.2,2.5,.2],[15.8,3,15.8],'dark')
   if col in (0,2):
    x=1 if col==0 else 13;add([x,3.05,1],[x+2,3.3,15],'yellow')
   if row in (0,2):
    z=1 if row==0 else 13;add([1,3.05,z],[15,3.3,z+2],'yellow')
   if part==4:
    for x in (3,11):add([x,3.05,3],[x+2,3.4,13],'edge')
    add([5,3.05,7],[11,3.4,9],'edge')
   if part in (0,2,6,8):add([6,3.1,6],[10,3.8,10],'light')
  elif name.startswith('pallet_rail'):
   for z in (1,9):add([0,0,z],[16,1.2,z+3],'wood')
   if col in (0,2):
    x=9 if col==0 else 4
    add([x,1.2,0],[x+2,3,16],'steel');add([x-.6,3,0],[x+2.6,3.7,16],'edge')
    for z in (3,11):add([x-1.5,1.2,z],[x+3.5,2.1,z+1],'dark')
   if part==4:
    add([7,1.3,3],[9,1.7,12],'yellow');add([5,1.3,3],[11,1.7,5],'yellow')
  else:
   # Each tile carries eight separate transverse rollers on recessed bearings.
   for j in range(8):
    z=j*2;h=4+rise*((2-row)*16+16-z)/3
    add([0,h-2.4,z],[16,h-1.8,z+1.8],'dark')
    lo=2 if col==0 else 0;hi=14 if col==2 else 16
    add([lo,h-1.5,z+.1],[hi,h,z+1.5],'edge')
    if col in (0,2):
     x=0 if col==0 else 14
     add([x,h-3,z],[x+2,h+.4,z+2],'steel')
     if j%2==0:add([x+.2,h+.45,z+.15],[x+1.8,h+.7,z+1.4],'yellow')
   if part==4:
    # A recessed direction plate with a raised three-dimensional arrow.
    add([5,4+rise*8-1,5],[11,4+rise*8-.25,11],'dark')
  if name=='pallet_rail_index' and part==4:
   add([2,1.8,5],[14,2.8,11],'steel')
   add([3,2.8,6],[13,3.0,10],'dark')
   for x in (4,7,10):add([x,3.0,6],[x+1.2,3.3,10],'yellow')
   add([6,3.3,7],[10,3.6,9],'red')
  if not name.startswith('pallet_rail') and name!='drone_parking_bay' and part==4 and name!='conveyor_logic':
   y=4+rise*8+.15
   add([7,y,6],[9,y+.15,11],'yellow')
   add([5,y,5],[11,y+.15,7],'yellow')
   add([6,y,4],[10,y+.15,5],'yellow')
   add([7,y,3],[9,y+.15,4],'yellow')
  if name=='conveyor_hold':
   if row==0:
    for x in ([.3] if col==0 else [13.7] if col==2 else []):
     add([x,3,3],[x+2,11,7],'steel');add([x+.4,8,2.8],[x+1.6,10,3],'red')
    add([0,3.1,4],[16,4,6],'yellow')
  if name=='conveyor_logic' and part==4:
   add([5,4.1,5],[11,4.4,11],'steel')
   for a,b in [([7,4.4,4],[9,4.7,12]),([4,4.4,7],[12,4.7,9]),([3,4.4,6],[5,4.7,10]),([11,4.4,6],[13,4.7,10]),([6,4.4,3],[10,4.7,5])]:add(a,b,'yellow')
  model(f'{name}_{part}',els)
  if name=='conveyor_t_junction':
   for outlet in range(3):
    chosen=list(els)
    if part==4:
     # Green outlet pointer, distinct from the yellow merge arrows.
     if outlet==0:
      chosen+=[cube([7,4.6,2],[9,4.85,9],'light'),cube([5,4.62,2],[11,4.87,4],'light')]
     elif outlet==1:
      chosen+=[cube([7,4.6,7],[14,4.85,9],'light'),cube([12,4.62,5],[14,4.87,11],'light')]
     else:
      chosen+=[cube([2,4.6,7],[9,4.85,9],'light'),cube([2,4.62,5],[4,4.87,11],'light')]
    model(f'{name}_{part}_out{outlet}',chosen)
  if name=='conveyor_hold':
   raised=list(els)
   if row==0:
    raised=els[:-1]+[cube([0,7,4],[16,9,6],'yellow')]
    for x in range(1,16,4):raised.append(cube([x,7.3,3.96],[x+2,8.7,4],'rubber'))
   model(f'{name}_{part}_raised',raised)
  for e in els:
   q=json.loads(json.dumps(e));q['from']=[e['from'][0]+(col-1)*16,e['from'][1],e['from'][2]+(row-1)*16];q['to']=[e['to'][0]+(col-1)*16,e['to'][1],e['to'][2]+(row-1)*16]
   if 'rotation' in q:q['rotation']['origin']=[q['rotation']['origin'][0]+(col-1)*16,q['rotation']['origin'][1],q['rotation']['origin'][2]+(row-1)*16]
   full.append(q)
  for facing,rot in [('north',0),('east',90),('south',180),('west',270)]:
   if name=='conveyor_hold':
    for power in (False,True):variants[f'facing={facing},part={part},powered={str(power).lower()}']={'model':f'caszual_additions:block/transport/{name}_{part}'+('_raised' if power else ''),'y':rot}
   elif name=='conveyor_t_junction':
    for outlet in range(3):variants[f'facing={facing},part={part},outlet={outlet}']={'model':f'caszual_additions:block/transport/{name}_{part}_out{outlet}','y':rot}
   else:variants[f'facing={facing},part={part}']={'model':f'caszual_additions:block/transport/{name}_{part}','y':rot}
 write(f'assets/caszual_additions/blockstates/{name}.json',{'variants':variants})
 # A reduced whole-module inventory model keeps every element inside vanilla bounds.
 for e in full:
  for key in ('from','to'):e[key]=[(e[key][0]-8)/3+8,e[key][1]/3+5,(e[key][2]-8)/3+8]
  if 'rotation' in e:
   o=e['rotation']['origin'];e['rotation']['origin']=[(o[0]-8)/3+8,o[1]/3+5,(o[2]-8)/3+8]
 model(name+'_item',full,DISPLAY);item(name,name+'_item')

# Chassis models authored in world units, divided by three for block model bounds.
def vehicle(name,length):
 els=[]
 def add(x,y,z,w,h,d,t='steel'):
  a=[8+x*16/3,y*16/3,8+z*16/3];b=[8+(x+w)*16/3,(y+h)*16/3,8+(z+d)*16/3];els.append(cube(a,b,t))
 if name!='pallet_drone':
  add(-1.15,.38,-length/2,2.3,.22,length)
  add(-1.06,.60,-length/2+.06,2.12,.10,length-.12,'dark')
  for x in (-1.18,1.08):
   add(x,.52,-length/2,.10,.13,length,'edge')
   for z in [(-length/2+.25)+i*.6 for i in range(int(length/.6))]:add(x-.008,.54,z,.116,.06,.24,'yellow')
  for z in (-length/2+.75,length/2-.75):
   add(-.9,.23,z-.35,1.8,.15,.7,'dark')
   for x in (-1.14,.88):
    add(x,.04,z-.32,.26,.64,.64,'rubber');add(x-.025,.16,z-.20,.31,.40,.40,'edge');add(x-.04,.27,z-.08,.34,.17,.17,'dark')
   add(-1,.27,z-.07,2,.13,.14,'edge')
  for z in (-length/2-.28,length/2):
   add(-.18,.36,z,.36,.16,.28,'dark');add(-.28,.40,z+.08,.56,.12,.1,'edge')
 if name=='pallet_engine':
  add(-.92,.70,-1.72,1.84,.95,2.05,'steel')
  add(-.84,1.65,-1.64,1.68,.12,1.89,'dark')
  for x in (-.945,.925):
   for z in [-1.5+i*.15 for i in range(10)]:add(x,.92,z,.02,.45,.06,'rubber')
   add(x,1.44,-1.58,.022,.08,1.66,'yellow')
  # Hollow driver cab with a visible seat and dashboard.
  for x in (-.93,.83):add(x,.70,.48,.10,.70,1.25,'steel')
  add(-.93,.70,1.63,1.86,1.65,.10,'steel')
  add(-.83,.70,.48,1.66,.45,.15,'dark')
  add(-.38,.78,.88,.76,.20,.62,'dark')
  add(-.35,.98,.91,.70,.12,.56,'seat')
  add(-.35,1.1,1.4,.70,.55,.09,'seat')
  add(-.58,1.16,.51,1.16,.10,.20,'edge')
  add(-.20,1.26,.55,.40,.05,.12,'light')
  for x in (-.93,.83):add(x,1.4,.48,.10,.95,.10,'edge')
  add(-.79,1.46,.46,1.58,.65,.035,'glass')
  for x in (-.955,.925):add(x,1.44,.68,.03,.66,.78,'glass')
  add(-1.03,2.35,.34,2.06,.17,1.53,'dark')
  add(-.24,1.77,-.68,.48,.45,.38,'steel');add(-.30,2.22,-.74,.60,.08,.50,'rubber')
  for x in (-.72,.46):
   add(x,.94,-1.80,.26,.26,.08,'dark');add(x+.04,.98,-1.83,.18,.18,.035,'light')
   add(x,.82,1.75,.20,.16,.04,'red')
  add(-1.02,.77,-1.89,2.04,.14,.12,'yellow')
  for x in (-1.08,1.03):
   add(x,.7,.26,.05,.83,.05,'edge');add(x,.7,1.65,.05,.83,.05,'edge');add(x,1.49,.26,.05,.05,1.44,'edge')
 elif name=='pallet_car':
  # Three full-size pallet pockets with independent corner locks.
  for center in (-2.2,0,2.2):
   for x in (-1.04,.94):
    for z in (center-1,center+.9):add(x,.71,z,.10,.17,.10,'yellow')
  for z in (-3.30,3.20):
   add(-1.12,.70,z,2.24,.10,.10,'edge')
   for x in (-1.12,1.04):add(x,.70,z,.08,.60,.10,'steel')
 elif name=='passenger_car':
  for x in (-1.02,.93):
   add(x,.70,-2.1,.09,.52,4.2,'steel');add(x,1.22,-2.1,.09,.08,4.2,'yellow')
   for z in (-2.05,-.72,.67,1.96):add(x,1.3,z,.09,1.15,.09,'steel')
   for z in (-1.91,-.59,.80):add(x+.015,1.38,z,.06,.91,1.12,'glass')
  add(-1.12,2.45,-2.2,2.24,.18,4.4,'dark');add(-.91,2.63,-2.05,1.82,.10,4.1,'steel')
  # Eight individual seats: two columns, four rows.
  for z in (-1.5,-.5,.5,1.5):
   for x in (-.55,.55):
    add(x-.35,.72,z-.30,.70,.22,.60,'dark')
    add(x-.33,.94,z-.28,.66,.15,.56,'seat')
    add(x-.34,1.09,z+.24,.68,.63,.08,'seat')
    for a in (-.38,.32):add(x+a,1.06,z-.18,.06,.18,.42,'edge')
  for z in (-2.2,2.12):
   add(-.86,.72,z,.33,.75,.08,'steel');add(.53,.72,z,.33,.75,.08,'steel')
  for x in (-.42,.36):add(x,.72,-2.17,.06,1.65,.06,'edge')
 elif name=='pallet_drone':
  add(-.65,2.10,-.65,1.30,.29,1.30,'steel');add(-.48,2.39,-.48,.96,.10,.96,'dark')
  add(-.27,2.50,-.27,.54,.06,.54,'yellow')
  for x in (-1.08,1.08):
   for z in (-1.08,1.08):
    add(min(x,0)-.09,2.16,min(z,0)-.09,abs(x)+.18,.12,.18,'steel')
    add(x-.09,2.16,min(z,0)-.09,.18,.12,abs(z)+.18,'steel')
    add(x-.20,2.20,z-.20,.40,.25,.40,'dark')
    # Open square duct protects four animated rotor blades.
    for dx,dz,w,d in [(-.55,-.55,1.10,.06),(-.55,.49,1.10,.06),(-.55,-.49,.06,.98),(.49,-.49,.06,.98)]:add(x+dx,2.35,z+dz,w,.16,d,'steel')
  for x in (-1.03,.95):
   for z in (-.82,.74):add(x,.17,z,.08,1.99,.08,'edge')
   add(x-.10,.03,-1.04,.28,.14,2.08,'dark')
   add(x-.05,.20,-.88,.18,.12,1.76,'yellow')
  add(-.26,2.12,-.76,.52,.16,.10,'rubber');add(-.10,2.15,-.79,.20,.10,.04,'light')
 model(name,els,DISPLAY)
 write(f'assets/caszual_additions/blockstates/{name}_model.json',{'variants':{'':{'model':f'caszual_additions:block/transport/{name}'}}});item(name,name)
 return els
for name,length in [('pallet_engine',3.6),('pallet_car',6.8),('passenger_car',4.4),('pallet_drone',3.3)]:vehicle(name,length)
els=[cube([5,0,6],[11,12,10],'steel'),cube([5.5,5,5.7],[10.5,11.5,6],'dark'),cube([6,7,5.5],[10,11,5.7],'light'),cube([6,2,5.7],[7,3,6],'yellow'),cube([9,2,5.7],[10,3,6],'red'),cube([6,12,7],[7,16,8],'edge')]
model('transport_controller',els,DISPLAY);item('transport_controller','transport_controller')
# Offset ratchet crank, steel socket and yellow rubber grip.
crank=[cube([6,1,6],[10,4,10],'edge'),cube([6.6,4,6.6],[9.4,9,9.4],'steel'),cube([8,8,7],[14,10,9],'edge'),cube([12,10,6.5],[14,15,9.5],'rubber'),cube([11.8,10,6.3],[14.2,11,9.7],'yellow'),cube([11.8,14,6.3],[14.2,15,9.7],'yellow')]
model('conveyor_crank',crank,DISPLAY);item('conveyor_crank','conveyor_crank')
# Portable bay-pairing tool: bright radio mast and two miniature pads.
bay_link=[cube([4,1,5],[12,3,11],'steel'),cube([5,3,6],[7,5,10],'yellow'),
          cube([9,3,6],[11,5,10],'light'),cube([7,3,7],[9,12,9],'edge'),
          cube([6.5,11,6.5],[9.5,13,9.5],'light'),cube([7,13,7],[9,16,9],'yellow')]
model('parking_bay_link',bay_link,DISPLAY);item('parking_bay_link','parking_bay_link')
# Control furniture shares the same steel, hazard stripe and indicator palette.
station=[cube([2,0,2],[14,2,14]),cube([3,2,3],[13,3,13],'dark'),cube([6,3,6],[10,24,10],'edge'),cube([2,17,5],[14,29,11]),cube([3,18,4.8],[13,28,5],'dark'),cube([4,23,4.6],[12,27,4.8],'red'),cube([4,19,4.6],[12,21,4.8],'yellow'),cube([1.5,28,4.5],[14.5,29.5,11.5],'dark')]
for x in (3,11):
 for z in (3,11):station.append(cube([x,.01,z],[x+1,2.2,z+1],'edge'))
computer=[cube([1,0,1],[15,2,15]),cube([2,2,3],[14,7,14],'dark'),cube([3,7,6],[13,9,13]),cube([1,9,6],[15,16,14]),cube([2,10,5.8],[14,15,6],'rubber'),cube([2.5,10.5,5.6],[13.5,14.5,5.8],'seat'),cube([1,6,1],[15,7,6],'edge'),cube([2,7,2],[11,7.3,5],'rubber'),cube([12,7,2],[14,7.4,4],'yellow')]
for x in range(3,13,2):
 for z in (2,4):computer.append(cube([x,7.3,z],[x+1,7.5,z+.6],'dark'))
for x,y,t in [(4,11,'yellow'),(7,13,'yellow'),(11,12,'light'),(9,11,'red')]:computer.append(cube([x,y,5.4],[x+.7,y+.7,5.6],t))
for x in range(3,13,2):computer.append(cube([x,3,2.8],[x+.6,5.5,3],'steel'))
for name,els in [('train_station_post',station),('pallet_transit_computer',computer)]:
 model(name,els,DISPLAY);item(name,name);write(f'assets/caszual_additions/blockstates/{name}.json',{'variants':{'':{'model':f'caszual_additions:block/transport/{name}'}}})
platevariants={}
for part in range(4):
 for power in (False,True):
  h=1 if power else 2
  els=[cube([0,0,0],[16,h,16],'steel'),cube([.2,h,.2],[15.8,h+.1,15.8],'dark')]
  for z in (3,7,11):els.append(cube([1,h+.1,z],[15,h+.2,z+.6],'edge'))
  x=0 if part%2==0 else 14;z=0 if part//2==0 else 14
  els.extend([cube([x,h+.1,0],[x+2,h+.3,16],'yellow'),cube([0,h+.1,z],[16,h+.3,z+2],'yellow'),cube([7,h+.1,7],[9,h+.4,9],'red' if power else 'light')])
  name=f'pallet_pressure_plate_{part}_{str(power).lower()}';model(name,els,DISPLAY);platevariants[f'part={part},powered={str(power).lower()}']={'model':'caszual_additions:block/transport/'+name}
write('assets/caszual_additions/blockstates/pallet_pressure_plate.json',{'variants':platevariants})
item('pallet_pressure_plate','pallet_pressure_plate_0_false')
langp=AS/'lang/en_us.json';langp.parent.mkdir(parents=True,exist_ok=True);lang={}
labels={'pallet_rail_index':'Warehouse Rail — Car Indexer','conveyor_crank':'Conveyor Direction Crank','conveyor_hold':'Pallet Conveyor — Redstone Hold Bar','conveyor_logic':'Pallet Conveyor — Logic Switch','train_station_post':'Train Station Post','pallet_pressure_plate':'Pallet Pressure Plate','pallet_transit_computer':'Pallet Transit Computer','conveyor_straight':'Pallet Conveyor — Straight','conveyor_left':'Pallet Conveyor — Left Turn','conveyor_right':'Pallet Conveyor — Right Turn','conveyor_t_junction':'Pallet Conveyor — T-Junction (2 IN / 1 OUT)','conveyor_up':'Pallet Conveyor — Ramp Up','conveyor_down':'Pallet Conveyor — Ramp Down','pallet_rail':'Warehouse Rail — Straight','pallet_rail_left':'Warehouse Rail — Left Turn','pallet_rail_right':'Warehouse Rail — Right Turn','drone_parking_bay':'Drone Parking Bay','pallet_engine':'Pallet Train Engine','pallet_car':'Three-Pallet Flatbed Car','passenger_car':'Passenger Car','pallet_drone':'Pallet Drone','transport_controller':'Pallet Transport Controller','parking_bay_link':'Parking Bay Link'}
for name,label in labels.items():lang[('block.' if name in names+['train_station_post','pallet_pressure_plate','pallet_transit_computer'] else 'item.')+'caszual_additions.'+name]=label
lang['entity.caszual_additions.pallet_transport']='Pallet Transport';langp.write_text(json.dumps(lang,indent=2,ensure_ascii=False)+'\n')
tag=ROOT/'data/caszual_additions/tags/item/caszual_content.json';tag.parent.mkdir(parents=True,exist_ok=True);obj={'replace':False,'values':[]}
for name in labels:
 val='caszual_additions:'+name
 if val not in obj['values']:obj['values'].append(val)
# The original legacy item remains in saves, but the searchable native item
# uses the same detailed model and is the result of new crafting.
write('assets/caszual_pallets_plus/items/conveyor_t_junction.json',
      {'model':{'type':'minecraft:model','model':'caszual_additions:block/transport/conveyor_t_junction_item'}})
tag.write_text(json.dumps(obj,indent=2)+'\n')
# Survival recipes; module kits each place a complete 3x3 footprint.
for name in labels:
 ingredients={'pallet_rail_index':'minecraft:observer','conveyor_crank':'minecraft:lever','conveyor_hold':'minecraft:iron_bars','conveyor_logic':'minecraft:repeater','train_station_post':'minecraft:redstone_torch','pallet_pressure_plate':'minecraft:heavy_weighted_pressure_plate','pallet_transit_computer':'minecraft:map','conveyor_straight':'minecraft:hopper','conveyor_left':'minecraft:amethyst_shard','conveyor_right':'minecraft:quartz','conveyor_t_junction':'minecraft:dropper','conveyor_up':'minecraft:piston','conveyor_down':'minecraft:sticky_piston','pallet_rail':'minecraft:rail','pallet_rail_left':'minecraft:powered_rail','pallet_rail_right':'minecraft:detector_rail','pallet_engine':'minecraft:furnace','pallet_car':'minecraft:chest','passenger_car':'minecraft:oak_stairs','pallet_drone':'minecraft:ender_pearl','drone_parking_bay':'minecraft:lodestone','transport_controller':'minecraft:comparator','parking_bay_link':'minecraft:ender_eye'}
 write(f'data/caszual_additions/recipe/{name}.json',{'type':'minecraft:crafting_shaped','pattern':['III','ISI','IRI'],'key':{'I':'minecraft:iron_ingot','S':ingredients[name],'R':'minecraft:redstone'},'result':{'id':('caszual_pallets_plus:' if name=='conveyor_t_junction' else 'caszual_additions:')+name,'count':1}})
print('Generated 13 modular guideways and transit control furniture, 4 vehicle models, controller, recipes and discovery tags.')

for name in ('train_station_post','pallet_transit_computer'):
 write(f'data/caszual_additions/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'caszual_additions:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
