# Caszual Pallets Plus

Fabric 26.2 / Java 25 transport addon. **Requires Caszual Additions 0.1.1 or later**, installed as a separate mod on both client and server.

## Ownership

This repository owns trains and coupling, three-pallet flatbeds, passenger cars, rails, station posts, conveyors and logic/hold controls, drones and chunk loading, drone bays, pallet pressure plates and the transit computer.

Caszual Additions continues to own pallets, cargo storage, racks, warehouse computers, scanners, storage Golems, transfer points, wrappers and all other warehouse features. None of those classes are copied into this addon. The optional MovingPallet integration uses the warehouse's existing cargo and navigation, adding transport delivery modes without overwriting warehouse modes.

## Installation and existing worlds

Install the regular Caszual Additions JAR alongside `caszual-pallets-plus-0.5.1.jar`. Do not install the old combined `pallet-transport` preview of Additions with this addon; it registers the same transport blocks and entities.

Back up the world before changing mods. Finish active Golem train/conveyor loading jobs before replacing the old combined preview. Existing track, conveyor, vehicle, cargo and transit-data identifiers retain their `caszual_additions:` namespace intentionally, so the extracted addon can read those world objects. They are now registered by Pallets Plus. Pallets Plus has its own creative tab and contributes transport items to the shared Caszual content tag.

Removing Pallets Plus while keeping Additions preserves the warehouse implementation, but transport blocks/entities require this addon to remain installed. Remove or unload transport cargo before removing the addon from a world.

## Development

Work directly on `main`. Run `gradle build` with Java 25, Gradle 9.7.0 and Python 3. Models, recipes, translations and tags are generated deterministically by `scripts/generate_transport_models.py` into `build/generated/transport` during the build. Edit this generator for transport model changes.

The pinned, unmodified Additions JAR in `libs` is a **compile/dev dependency only**, never bundled in the addon. Its source commit and checksum are recorded in `libs/README.md`. This lets both private repositories build without a broad cross-repository access token. Update the pinned dependency deliberately when warehouse APIs change. Runtime integration targets the existing MovingPallet fields/methods and fails loudly if an incompatible Additions build changes them.

CI builds the addon, runs transport path/save tests, checks asset geometry and packaging isolation, then starts a dedicated server with both mods to exercise mixin application and registration. Client rendering and actual Golem/train/drone gameplay still need in-game testing.

See [transport usage](docs/transport.md) for controls and placement rules inherited from the original transport preview.

## Speeds, seats and unloading (0.2.0)

- Engine: accelerates at 3 m/s² to 30 m/s in ten seconds on a clear track. Speed resets on a stop or obstruction. One engine supports up to ten trailing pallet/passenger cars, including when joining existing groups.
- Conveyors: 8 m/s. Drones: 45 m/s, with a continuous 96-block chunk-loading lookahead. Speeds assume normal 20 TPS. Collision, module ends, station stops, hold bars and indexing limits are checked in substeps no longer than 0.1 block.
- Empty-hand click boards the engine (one driver) or passenger car (eight seats). Use the Transport Controller while seated to start/pause the engine, or sneak-use it on the engine from outside. Sneak to dismount. Station posts can still release stopped engines.
- **Warehouse Rail — Car Indexer:** a straight 3×3 module. The engine stops at its centre. Empty-hand click the rail, or give it a fresh redstone pulse, to advance at 2 m/s until the next car centre reaches that point. Distances include each pair's actual half-lengths and coupler gap, so mixed car lengths line up correctly. Each subsequent pulse brings the next car. Constant power does not repeat. After the last car, start the engine with the controller to depart. Lay enough ordinary track ahead for the entire train. Interrupted steps retain their remaining distance across saves.
- **Conveyor Direction Crank:** use on an empty conveyor to set its input direction to your horizontal facing. Sneak-use rotates clockwise. All nine tiles rotate together, including corner exits, ramps and hold bars; logic route settings are retained. Occupied conveyors reject changes to avoid moving a pallet off its path.

Back up existing worlds before trying the new movement behavior. Compilation, automated geometry/motion tests and dedicated-server startup are CI gates; multiplayer seating and end-to-end travel require in-game validation.

## Long-distance transport continuity (0.2.1 preview)

- Active trains and conveyors now use the same temporary, expiring simulation-ticket approach as drones. Train engines refresh a 96-block corridor ahead and a 96-block corridor behind the engine, covering ten coupled cars. A conveyor pallet refreshes 48 blocks ahead along its current travel direction. Drone lookahead and destination loading remain in place.
- Paths are sampled every 16 blocks with a one-chunk ticket radius for ground transport (two for drones). This is a short local corridor that follows changing headings, **not** a permanent loader for an entire rail or conveyor network.
- Active trip positions and short target headings are persisted using the existing transport SavedData; moving engines also preserve the exact positions of up to ten trailers so a train around a bend stays loaded. Active trips can restore their ticket corridor after a restart. Missing chunks cause the vehicle to wait safely and retry when loaded; confirmed broken or incompatible guideways instead stop the vehicle and release tickets.
- Ticket renewal stops on parking, stations, indexer stops and paused conveyors. Expiring tickets leave no permanent vanilla `/forceload` changes. Busy networks consume additional server resources while transports move. Mobs and other nearby activity may tick inside loaded chunks.
- Existing train, conveyor, drone and pallet IDs are unchanged. Back up existing saves, then replace the previous addon JAR; do not install both versions.

Automated tests cover corridor limits, negative chunk coordinates and server-record activation/release. Client-side render checks and long-distance multiplayer/world-reload gameplay should still be performed before considering this a stable release.

## Conveyor ramp support collision fix (0.2.2)

- A pallet on a downhill conveyor no longer collides with its own ramp deck (whose lowered geometry extends below the nominal block) or ordinary full-height support blocks placed directly below the conveyor.
- The collision exception applies only to the active current/previous/next conveyor modules and their immediate support blocks; overhead walls, machinery, and protruding support shapes such as fences remain solid barriers.
- Route progress and the previous module are saved so restarting during a transition does not make the pallet collide with the ramp it just left.
- Dedicated-server integration now runs two downhill paths with solid blocks below the ramp, including a separate obstructed path to confirm genuine cargo obstacles still stop it safely.

**Note:** This is a transport collision fix, not a change to vanilla block collisions or the warehouse's own pallet collision system. Existing conveyor blocks and pallet cargo need no replacement; back up your world before updating.

## T-junction merging conveyors (0.3.0)

Craft or place **Pallet Conveyor — T-Junction (2 IN / 1 OUT)**. Each complete 3×3 module has a stem, left wing and right wing; the fourth side is closed. Set the intended outlet by empty-hand right-clicking any cell or by using the Conveyor Direction Crank. The center marker updates to show the selected output. The other two sides are automatically the incoming lanes. Sneak-use the crank to rotate the whole three-arm assembly before loading pallets. Switching or rotating is blocked while a pallet occupies the junction.

Pallets from straight conveyor sections or curves entering either inlet merge into the same output; a carrier approaching from the exit or missing fourth side cannot enter. The chosen outlet is stored in block states across saves, and the inbound direction is saved with each traveling pallet. Curves that touch diagonally at their tips can also pass cargo directly between the two corners when their input/output direction agrees; ambiguous double-tip junctions deliberately stop rather than choosing an unintended lane.

The merge has no separate power requirement. At high traffic, pallets safely wait for the transport in front to clear; this is not a priority scheduler. New assets and a recipe are included. The addon continues to require Caszual Additions without duplicating its warehouse/pallet system.

## Drone Parking Bay network (0.4.0 preview)

Empty-hand click a complete 3x3 Drone Parking Bay to change INPUT/OUTPUT. Craft the **Parking Bay Link** tool: click OUTPUT bay, then INPUT bay to create a one-way route. To connect a bay to a warehouse, click the Rack Computer with the same tool, then click that bay (both within 128 blocks). Sneak-use the tool to clear its selection. Links, roles, owner and warehouse position persist in the world, and INPUT/OUTPUT bay links are shown on the Pallet Transit Computer.

A drone placed at an OUTPUT bay adopts its linked INPUT route. It takes off as soon as it physically lifts a wrapped pallet staged beside the OUTPUT bay; no player start click is necessary. It flies over terrain with a bounded vertical climb/descent, aiming 20–40 blocks above the sampled ground. A blocked landing bay makes it hover instead of colliding or dropping cargo. The descent is limited to 0.12 blocks/tick and auto-deliveries return the empty drone to the OUTPUT bay after physically placing their shipment at the receiver.

A linked warehouse receives a one-time redstone notification pulse on an empty OUTPUT bay (requesting an Output Aisle pallet) and on a delivered INPUT pallet (requesting a four-Golem remote pickup into its warehouse rack/PTP). Input remote dispatch uses the existing Caszual Additions dispatcher when the owner who linked the warehouse is online and the warehouse can spare a full crew and an empty exchange pallet. No virtual pallet cloning or teleporting is used. If a warehouse has no compatible automated output-aisle crew handoff, it must physically stage a wrapped pallet next to the OUTPUT bay; the request remains visible on the Transit Computer until staged. Golems, supplies, connected warehouses, unload clearances and the world path all still need gameplay testing with the installed Additions build.

## Bay Link fix (0.4.2)

Use the Parking Bay Link on an OUTPUT bay, then walk or fly to an INPUT bay and click it. The OUTPUT chunk does **not** need to remain loaded. Its saved role and coordinates are used for the second click. Wrong-way links show precise errors; to change a role, empty-hand right-click a bay. After a successful link the tool clears its pending selection. Breaking a bay also removes its saved role/route and invalidates incoming links; existing complete bays missing saved records are recovered when visited. Cargo and bay block IDs remain unchanged.

## Conveyor pallet recovery (0.4.3)

If a pallet gets bumped off the conveyor, stalls on a slope, or stays stuck after the route is cleared, **right-click its carrier with the Conveyor Crank** to restore the saved 3×3 guideway position and restart it. Clicking the occupied conveyor module with the crank also works when the pallet blocks access to the entity. The cargo and all 216 storage slots stay with the same carrier. Recovery checks the actual destination for obstructions and will not pass through walls or other transports.

For an unrecoverable jam, **sneak + right-click the carrier or its occupied conveyor using the crank** to salvage it onto nearby supported, empty 2×2 ground (up to 8 blocks from the player with 3 blocks of headroom). The original complete pallet, items and filter settings are placed into the world before the empty carrier disappears. If no valid spot is found, both carrier and inventory stay unchanged; clear a platform and retry. No blocks have to be broken, and a saved vehicle from an older version is supported.

**Important unload fix:** Caszual Additions' pallet is four blocks but only its root owns a BlockEntity. Pallets Plus previously rejected every unload after looking for nonexistent BlockEntities in the three satellite cells. This release fixes the common pallet placement path used by conveyors, drones, manual transfers and emergency salvage. Full 216-slot contents and wrapper/filter metadata are preserved in the root.

## Parking Bay Link click-priority fix (0.4.4)

Using the **Parking Bay Link** on an OUTPUT bay selects it; using the same tool on an INPUT bay completes the route. The tool now intercepts block interaction **before** the Drone Parking Bay's empty-hand role toggle, and before the Caszual Additions Rack Computer's always-open terminal GUI. Clicking with the linking tool cannot switch INPUT/OUTPUT roles or erase existing routes. Both main-hand server and client actions are consumed consistently. To intentionally change a bay's INPUT/OUTPUT role, use an actually empty hand. The tool also works on the Rack Computer to bind a bay to its warehouse. Save IDs and existing pallet/drone routes are unchanged.

## Ground pallets on pressure plates (0.4.5)

When a loaded conveyor pallet reaches an adjacent complete 2×2 **Pallet Pressure Plate**, the conveyor centers the shipment and physically places the original pallet one block above the plate, occupying exactly its four tiles. It is no longer a traveling transport entity. Its original 216-slot inventory, wrap and filter data are preserved, and **Caszual Additions storage Golems** can pick it up like an ordinary wrapped ground pallet. The pressure plate stays powered while the ground pallet remains. A pallet already on the plate, an obstructed top, or an incomplete plate causes the incoming carrier to stop **without** consuming its cargo. The plate also detects loaded conveyor carriers already directly above it. Passing train cars and flying drones are excluded to prevent accidental unloading.

## Pallets sit flush on pressure plates (0.4.6)

The 0.4.5 handoff correctly transferred the cargo to a real ground pallet at **one block above** the 2×2 pressure plate, which kept it golem-pickable but visually floated it in mid-air. With this version, the Pallets Plus **client-only** renderer patch detects a real four-cell pressure plate directly underneath a placed wrapped pallet and lowers the complete pallet model, including its runners, wrap, and displayed inventory, so the feet rest precisely on the pressure plate's 1/16-high powered surface (or 2/16 idle surface). Only the matching pressure-plate configuration receives a display offset; ordinary pallets are unchanged. The real pallet inventory/block entities retain their original positions and IDs for existing Caszual Additions warehouse golem pickup.

## Drone INPUT Parking Bay handoff (0.4.7)

A drone carrying a wrapped pallet now descends gently to a safe unloading hover above the destination **INPUT Drone Parking Bay**, deposits its original cargo onto the central 2×2 tile footprint of that bay, and departs toward its OUTPUT bay without intersecting the placed cargo. The physical pallet is a real Caszual Additions wrapped pallet with its original item, filter/wrap metadata and 216 independent inventory slots; the client visual seats it flush on the raised bay surface. It remains physically on the bay until collected rather than being dropped on nearby terrain. An occupied or obstructed INPUT bay is never overwritten and the drone keeps its cargo until it can place it safely.

A linked warehouse sees the newly arrived pallet, emits **one** short redstone/request pulse per physical shipment, and requests the existing Caszual Additions four-Golem remote pallet pickup workflow. Its returning crew prioritizes empty INPUT aisle racks, with INPUT PTP fallback if rack placement is unavailable. Remote pickup requires the original warehouse-linking player to be online, four docked Golems, clear worker paths and an empty exchange pallet under the current Additions dispatcher; until all prerequisites are met, the loaded pallet remains safely on its INPUT bay. The current Additions dispatcher exchanges the target with an empty pallet, so automatic clearing of its replacement remains a subsequent Additions integration task; this addon does not delete, teleport or duplicate that replacement.

## Unified Parking Bay logistics (0.4.8)

The former Pallet Pressure Plate is retired: no new crafting recipe, creative-menu entry or tag. Its registered block remains as a legacy placeholder in old saves, but new conveyors no longer deliver to it. **Drone Parking Bays are the single pallet landing/handoff surface**. Every complete 3×3 bay has a central 2×2 physical pallet footprint that holds the real wrapped Caszual Additions pallet and its 216 storage slots.

While a physical pallet occupies a bay, the 3×3 bay continuously emits redstone strength **15** from its POWERED state. Removing the pallet via a warehouse Golem, forklift, conveyor or drone stops the redstone output. Unlike earlier versions this is a presence signal, NOT the momentary warehouse request pulse.

Conveyors can move loaded pallets onto adjacent INPUT or OUTPUT Parking Bays, and empty conveyor carriers can pick up wrapped pallets from an adjacent bay. The warehouse scanner's DIRECT or FORKLIFT mode resolves clicks on a bay into the actual underlying pallet, so Golems and forklifts can lift or place real pallets onto bays. A filled destination is never overwritten.

An INPUT bay attached to a warehouse requests the new **four-Golem, no-replacement** pickup from Caszual Additions **0.1.3**. The crew returns a pallet to an empty INPUT aisle rack (or regular rack fallback), without consuming or leaving any empty pallet on the bay. It can run while the warehouse-linking player is offline. Install matching updated Additions 0.1.3; older Additions will leave the pallet safely on the bay rather than perform an unwanted replacement exchange.

## Parking Bay pickup vs. Scanner Remote (0.4.9)

**Parking Bay INPUT retrieval:** Four warehouse Golems come together, all empty-handed; they take the pallet to an INPUT aisle rack. No replacement pallet is used. **Normal Scanner REMOTE pickup:** Four warehouse Golems are still dispatched; three travel to the target and the fourth carries a new empty pallet to exchange. These are distinct orders. Requires matching Caszual Additions **0.1.3** for automatic INPUT bay pickup. Existing bay pallets remain safe if Additions has not been updated.

## Pallet Transfer Nodes (0.5.0): The former Drone Parking Bay is now named Pallet Transfer Node. Its block registry ID stays unchanged for existing worlds. Empty-hand right-click switches INPUT (glowing I) and OUTPUT (yellow O). The Pallet Transfer Link tool still assigns warehouses and pairs air routes. An OUTPUT node connected to a warehouse requests exactly two Golems to walk to and push an existing staged pallet onto a physically adjacent empty conveyor carrier or stopped train flatbed. INPUT nodes likewise use two Golems to remove a pallet from an adjacent loaded vehicle onto the node. No adjacent eligible train/conveyor means no pusher dispatch. Actual transfers move the original pallet and its 216 slots atomically without duplication. Drones keep their animations, four-Golem INPUT retrieval needs no replacement, and ordinary four-Golem Remote pickup keeps its replacement courier.

An empty, warehouse-linked OUTPUT Pallet Transfer Node with a matching adjacent train/conveyor requests exactly two pushers to retrieve a real pallet from a reachable Output aisle rack. The node does not signal or request a crew without adjacent eligible transport. The loaded team reaches the node and transfers the original 216-slot pallet directly to the transport in one server transaction; no handplaced pallet on the node is required. The four-Golem INPUT and Remote pickup modes remain separate.


## Pallet Transfer Node interaction fix (0.5.1)

With an **empty selected hand**, right-click **any of the nine node tiles** to toggle its persistent mode. The bright **I** indicates INPUT and the yellow **O** indicates OUTPUT. The node confirms the new mode in chat. A real Pallet Transfer Link tool click continues to link nodes to warehouses/air routes without changing mode. The early Fabric right-click callback handles this before other interaction handlers can consume the click; the vanilla empty-hand handler remains a fallback. The mode and all nine visual marker block states remain synced to saved world data.

A genuine adjacent conveyor carrier or stopped train flatbed is still required before any two-Golem transfer crew is requested; otherwise, no push job is dispatched. The drone animation and four-Golem warehouse pallet handling remain separate. Version 0.5.1 modifies only node interaction, tests, metadata and documentation, not transport movement or warehouse worker algorithms.
