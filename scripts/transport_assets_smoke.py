import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'build/generated/transport'
assets=root/'assets/caszual_additions'
models=assets/'models/block/transport'
for p in models.glob('*.json'):
    m=json.loads(p.read_text())
    for element in m['elements']:
        assert all(a<b for a,b in zip(element['from'],element['to'])),p
        assert all(-16<=v<=32 for k in ('from','to') for v in element[k]),p
        for face in element['faces'].values():
            assert face['texture'][1:] in m['textures'],p
            assert all(0<=u<=16 for u in face.get('uv',[0,0,16,16])),p
        if 'rotation' in element:
            assert element['rotation']['angle'] in (-45,-22.5,0,22.5,45),p
            assert element['rotation']['axis']=='y',p
names=['conveyor_hold','conveyor_logic','conveyor_straight','conveyor_left','conveyor_right','conveyor_t_junction','conveyor_up','conveyor_down','pallet_rail','pallet_rail_index','pallet_rail_left','pallet_rail_right','drone_parking_bay']
recipes=set()
for name in names+['train_station_post','pallet_transit_computer','pallet_engine','pallet_car','passenger_car','pallet_drone' ,'transport_controller','conveyor_crank','parking_bay_link']:
    item=json.loads((assets/f'items/{name}.json').read_text())
    assert (assets/('models/'+item['model']['model'].split(':')[1]+'.json')).exists(),name
    recipe=json.loads((root/f'data/caszual_additions/recipe/{name}.json').read_text())
    signature=json.dumps([recipe['pattern'],recipe['key']],sort_keys=True)
    assert signature not in recipes,name
    recipes.add(signature)
for name in names:
    states=json.loads((assets/f'blockstates/{name}.json').read_text())['variants']
    assert len(states)==(72 if name=="conveyor_hold" else 108 if name=="conveyor_t_junction" else 72 if name=="drone_parking_bay" else 36),name
    for variant in states.values():assert (assets/('models/'+variant['model'].split(':')[1]+'.json')).exists(),name
print('Transport assets: model bounds, 36-state modules, item references and unique recipes passed.')

native=json.loads((assets.parent/'caszual_pallets_plus/items/conveyor_t_junction.json').read_text())
assert native['model']['model']=='caszual_additions:block/transport/conveyor_t_junction_item'
t_recipe=json.loads((root/'data/caszual_additions/recipe/conveyor_t_junction.json').read_text())
assert t_recipe['result']['id']=='caszual_pallets_plus:conveyor_t_junction'
node_item=json.loads((assets.parent/'caszual_pallets_plus/items/pallet_transfer_node.json').read_text())
assert node_item['model']['model']=='caszual_additions:block/transport/drone_parking_bay_item'
node_recipe=json.loads((root/'data/caszual_additions/recipe/drone_parking_bay.json').read_text())
assert node_recipe['result']['id']=='caszual_pallets_plus:pallet_transfer_node'
node_states=json.loads((assets/'blockstates/drone_parking_bay.json').read_text())['variants']
assert 'facing=north,part=4,outlet=0' in node_states and 'facing=north,part=4,outlet=1' in node_states
for role in (0,1):
  part=json.loads((models/f'drone_parking_bay_4_role{role}.json').read_text())
  assert len(part['elements'])>=4
