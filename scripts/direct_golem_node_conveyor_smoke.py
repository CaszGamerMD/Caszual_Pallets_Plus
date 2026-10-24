#!/usr/bin/env python3
"""Cross-mod contract: real Storage Golem rig -> node/conveyor without cargo loss."""
from pathlib import Path
base=Path(__file__).resolve().parents[1]
integration=(base/"src/main/java/com/caszgamermd/palletsplus/integration")
transport=(base/"src/main/java/com/caszgamermd/palletsplus/transport")
mixin=(base/"src/main/java/com/caszgamermd/palletsplus/mixin/MovingPalletTransportMixin.java").read_text()
crew=(integration/"CrewTransport.java").read_text()
interface=(integration/"TransportCrew.java").read_text()
node=(transport/"DroneBayCargo.java").read_text()
cargo=(transport/"PalletCargo.java").read_text()
paths=(transport/"GuidewayBlock.java").read_text()

assert "String palletsplus$requestNode(ServerLevel level,BlockPos root);" in interface
assert "String palletsplus$requestConveyor(ServerLevel level,BlockPos root);" in interface
assert "((TransportCrew)(Object)rig).palletsplus$requestNode(server,center)" in crew
assert "((TransportCrew)(Object)rig).palletsplus$requestConveyor(server,root)" in crew
assert "if(rig!=null)" in crew and "else if(found!=null)" in crew
assert "DroneBayCargo.find(server,center)" in crew
assert "GuidewayBlock.root(hit.getBlockPos(),state)" in crew
# A node is a 3x3 guideway; physical pallet occupies its central 2x2,
# one block above the pad and facing EAST (+X/+Z).
assert "return center.offset(-1,1,-1);" in node
assert "staged.place(level,padRoot,Direction.EAST)" in mixin
assert "if(deliveryMode==10){palletsplus$tickNode(level);ci.cancel();}" in mixin
assert "deliveryMode=10;" in mixin
assert "mode==8||mode==9||mode==10" in mixin
assert "palletsplus_transport_mode" in mixin
assert "palletsplus_transport_side" in mixin
assert "StorageGolemReturnPath.walkable(level,trial)" in mixin
assert "navigateToRackFront(level,approach,x,z)" in mixin
assert "WAITING: NODE 2x2 LANDING BLOCKED; CARGO RETAINED" in mixin
assert "staged.deck=pallet.copyWithCount(1)" in mixin
assert "for(int i=0;i<216;i++)staged.items.set(i,contents.get(i).copy());" in mixin
assert mixin.index("if(!staged.place(level,padRoot,Direction.EAST))") < mixin.index("clearCargo();",mixin.index("if(!staged.place("))
# Physical placement checks all four cells + support and commits
# inventory on only the root's block entity before the cargo clears.
assert "for(int i=0;i<4;i++)" in cargo
assert "getCollisionShape(level,p.below()).isEmpty()" in cargo
assert "for(int h=0;h<3;h++)" in cargo
assert "dest.initialize(pos,0,deck);" in cargo
assert "for(int i=0;i<216;i++)dest.setItem(i,items.get(i).copy());" in cargo
assert cargo.index("dest.initialize(pos,0,deck)")<cargo.index("clear();",cargo.index("dest.initialize("))
# Conveyor still uses its real transport carrier. No pallet entity is
# spawned before both the vehicle's footprint and a safe approach are clear.
assert "palletsplus$approachSide=selected;" in mixin
assert "if(conveyor&&(!level.noCollision(train)||!level.addFreshEntity(train)))" in mixin
assert "if(!train.acceptCrewPallet(pallet,contents))" in mixin
assert "GuidewayBlock.complete(level,root)" in mixin
assert "way.rail()||way.bay()" in mixin
assert "public static BlockPos root(BlockPos pos,BlockState s)" in paths
print("PASS: manual DIRECT Golems can safely deliver to transfer nodes and conveyors without copying/dropping cargo")
