"""Verify the distributed addon does not embed or replace warehouse classes."""
import json,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
jar=next(p for p in (root/'build/libs').glob('*.jar') if not p.name.endswith('-sources.jar'))
with zipfile.ZipFile(jar) as z:
    names=z.namelist()
    meta=json.loads(z.read('fabric.mod.json'))
    assert meta['id']=='caszual_pallets_plus'
    assert 'caszual_additions' in meta['depends']
    assert not any(n.startswith('com/caszgamermd/caszualadditions/') for n in names)
    assert not any(n.endswith('.jar') for n in names)
    assert not any('/checks/' in n for n in names), 'Test-only mod leaked into release'
    assert 'com/caszgamermd/palletsplus/mixin/MovingPalletTransportMixin.class' in names
    assert 'assets/caszual_additions/blockstates/conveyor_left.json' in names
    assert 'assets/caszual_additions/blockstates/conveyor_t_junction.json' in names
    assert 'assets/caszual_pallets_plus/items/conveyor_t_junction.json' in names
    assert 'data/caszual_additions/recipe/conveyor_t_junction.json' in names
    recipe=json.loads(z.read('data/caszual_additions/recipe/conveyor_t_junction.json'))
    assert recipe['result']['id']=='caszual_pallets_plus:conveyor_t_junction'
    assert 'assets/caszual_additions/blockstates/pallet_rail_right.json' in names
    tag=json.loads(z.read('data/caszual_additions/tags/item/caszual_content.json'))
    assert tag['replace'] is False and len(tag['values'])==23
print('Addon dependency, warehouse isolation, transport assets and shared discovery tag passed.')
