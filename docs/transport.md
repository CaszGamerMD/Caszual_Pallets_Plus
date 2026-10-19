# Caszual Pallets Plus — transport guide

Target: Fabric 26.2, Java 25. Install this addon alongside regular Caszual Additions. See the repository README for migration instructions.

## Equipment

- **Conveyors:** 3×3 module kits, three blocks wide, straight, left/right turn, and one-block rise/fall over three blocks. Place module centers three blocks apart. Face the direction of entry; a turn changes the exit by 90 degrees. Ramp exits connect one block higher/lower.
- **Warehouse rail:** separate three-wide rail modules, straight and left/right turns. These vehicles do not use vanilla minecart rails. Allow generous clearance for long cars around corners.
- **Train:** an engine, a flatbed holding three full pallets in a row, and a passenger carriage. Cars retain their cargo and couplings across saves.
- **Drone:** carries one pallet beneath a four-rotor frame. Deploy it on a complete 3×3 parking bay. Link a second bay in the same dimension; there is no 128-block limit.
- **Transport Controller:** selects pallets/carriers, transfers cargo, couples cars, and links drone destinations. Available with the equipment in the Caszual Pallets Plus creative tab and Fun Barrel tag; each item has its own recipe.

## Loading and unloading

1. Wrap a ground pallet using the existing pallet wrapper.
2. Click the pallet with the Transport Controller, then click a conveyor module or a stopped freight car/drone.
3. Empty-hand click a carrier to pause/start. Wait a moment after stopping.
4. Select the carrier with the controller, then click supported ground with a clear 2×2 footprint and three blocks of headroom to unload the first pallet. The destination must be within 12 blocks.
5. To hand off between transport types, select the loaded carrier and then another stopped carrier with a free slot. Two train cars instead use the coupling action.
6. To load a train with your Golems, finish lifting a pallet with your four-worker scanner crew, stop the whole train, then click a pallet flatbed with the **Warehouse Scanner**. The crew walks beside the first free slot, animates the pallet onto the car, and only then hands over its complete inventory. Leave a supported three-block-wide aisle alongside the flatbed. If the train moves, unloads or fills up, the crew waits with its cargo; scanning another destination retargets the order.

All 216 inventory slots, four wood selections/plastic tint, filter settings, and wrapping are retained per pallet. A destroyed carrier drops its pallet decks and contents once. Breaking any part of a guideway removes the module as one unit.

## Trains

Place an engine and cars on a continuous straight rail stretch. Leave six blocks between an engine and its first flatbed center, or nine between long flatbeds when coupling. Click the two cars with the Transport Controller, in either order. The track direction determines front/rear; both must face the same way, be stopped, have free couplers and sit on continuous straight rail within ten blocks. Failed attempts keep the first selection, so adjust the cars and retry. Repeat from front to back. Empty-hand click the engine to start or pause. Empty-hand click a passenger car to board; use the normal dismount key to leave.

Pause the engine before loading or uncoupling. Sneak-click a stopped car with an empty hand to uncouple; sneak-click an empty, uncoupled vehicle again to pick it up. Redstone alongside any rail cell stops the engine. Place a **Train Station Post** two blocks to either side of a rail module center, at rail height or one block higher. The engine stops at that module center and stays stopped until you click the engine or post with an empty hand. Use a straight track module for stations.

## Drone trips

Deploy on the home bay and load a pallet. For a nearby bay, select the drone with the controller, then click the destination bay. For a distant destination, click the destination bay first, then travel back and click the stopped drone; the controller remembers that bay even after it unloads. Empty-hand click the drone to depart. It climbs seven blocks above the higher bay, travels horizontally, and lands. It stops at arrival for unloading/loading; another click sends it back. The flight corridor must be clear. While in transit, the drone maintains expiring simulation tickets around itself, its forward route and its destination. Saved active flights restore tickets after server restart. Tickets expire three seconds after docking or pausing; no vanilla `/forceload` entries are changed. Drones wait for chunks to finish loading and retain their cargo when obstructed. They do not route around buildings or automatically load/unload warehouse racks/PTPs.

## Conveyor control and pressure plates

Pallets remain attached to their carrier through straight sections, turns and ramps. At a missing route end they wait with all cargo retained; pause and unload with the controller. They never fall off as loose items.

- **Hold conveyor:** powered redstone at any module cell raises the hold bar and stops the pallet at the module center. Removing power releases it.
- **Logic conveyor:** empty-hand click cycles the LOW state; sneak-click cycles HIGH. Each can be Straight, Left, Right or Hold. Chat shows both choices. Supply redstone at any module cell. A selected moving route is latched until the pallet exits, so changing power mid-turn cannot jump the pallet onto another path. A Hold selection waits at the entry until a moving state is selected. Connect the output tracks to the selected exits, with matching entry orientation.
- **Pallet pressure plate:** one kit occupies 2×2 cells extending south/east from the placement cell. A ground pallet immediately above it, or a loaded carrier over it, outputs signal strength 15. Players and loose item entities do not activate it. Updates every four ticks. To detect conveyor traffic, put it under the conveyor deck; wire its output to a hold/logic module elsewhere.

## Pallet transit computer

Right-click with an empty hand to open the current dimension's map. Arrow buttons pan, +/− zoom, Terminal returns home, and Fit pallets shows the recorded fleet. Gold icons represent individual pallets (three separate icons on a full flatbed); hover for the carrier, slot, stack count, status and coordinates. Gray icons are explicitly last-known unloaded carriers. Bays are blue, station posts red. Large networks paginate 128 carriers per page; Next page shows the rest.

The terrain layer samples loaded chunks and remembers up to 65,536 samples. Uncharted areas remain dark, including distant chunks never viewed at that zoom. Opening the computer does not generate the whole dimension. Coordinates still place remote pallet markers correctly. The map uses surface height (including the Nether roof) and is not a cave map. The computer refreshes once per second and requires the player to remain within ten blocks. Only pallets loaded into transport carriers are tracked, not every ground pallet in the world.

## Validation

`TransportPathTest` checks rotated module joins, quarter-circle geometry, and ramp elevation continuity. `scripts/transport_assets_smoke.py` checks model coordinates, state/model references, item models, and distinct recipes. `scripts/preview_transport_models.py` renders the actual repository geometry with flat material swatches for inspection; it is not an in-game screenshot. Interactive client validation is still required for boarding, multiplayer motion, and full transport journeys.

## Conveyor crew delivery and automatic drone handling (preview 4)

With the crew carrying a pallet, click any cell of a conveyor module with the Warehouse Scanner. The crew approaches from the side, waits for the deck to clear, and animates the pallet onto the rollers. A conveyor carrier is spawned only at the handoff; the crew keeps the inventory if the module is broken, occupied or blocked. Leave a supported three-block aisle beside it. The loaded pallet begins following the conveyor, obeying its hold bar and logic settings.

An empty drone collects a **wrapped ground pallet** when you click it to depart from a complete bay. Loading areas are three blocks from the bay's center in each cardinal direction, at bay height (or one block above/below). Place the near corner of the pallet there; leave its 2x2 footprint and three-block headroom clear. The bay's facing direction is checked first, then its sides and back. A loaded drone automatically puts the pallet down in a clear loading area at the destination. It waits with its cargo if every area is blocked, retaining chunk tickets until unloading succeeds. It does not immediately collect the pallet it just unloaded: click again when ready for the return trip. Empty return trips are allowed. These dock transfers use actual ground pallets, not dropped items; all cargo and pallet settings are retained. Controller loading/unloading remains available.

## Corner model rebuild (preview 5)

Left/right rails use continuous quarter-pixel curve edges, solid angled sleepers and a gauge matched to the straight rail ends. The square backing has been removed from rail corners. Conveyor corners use a curved rubber transfer belt with steel edges, yellow safety trim and end rollers. Texture coordinates continue across curved strips instead of repeating an entire texture on every sliver. Existing corner blocks update automatically; routes and pallet movement are unchanged.
