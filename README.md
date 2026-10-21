# Caszual Pallets Plus

Fabric 26.2 / Java 25 transport addon. **Requires Caszual Additions 0.1.1 or later**, installed as a separate mod on both client and server.

## Ownership

This repository owns trains and coupling, three-pallet flatbeds, passenger cars, rails, station posts, conveyors and logic/hold controls, drones and chunk loading, drone bays, pallet pressure plates and the transit computer.

Caszual Additions continues to own pallets, cargo storage, racks, warehouse computers, scanners, storage Golems, transfer points, wrappers and all other warehouse features. None of those classes are copied into this addon. The optional MovingPallet integration uses the warehouse's existing cargo and navigation, adding transport delivery modes without overwriting warehouse modes.

## Installation and existing worlds

Install the regular Caszual Additions JAR alongside `caszual-pallets-plus-0.4.0.jar`. Do not install the old combined `pallet-transport` preview of Additions with this addon; it registers the same transport blocks and entities.

Back up the world before changing mods. Finish active Golem train/conveyor loading jobs before replacing the old combined preview. Existing track, conveyor, vehicle, cargo and transit-data identifiers retain their `caszual_additions:` namespace intentionally, so the extracted addon can read those world objects. They are now registered by Pallets Plus. Pallets Plus has its own creative tab and contributes transport items to the shared Caszual content tag.

Removing Pallets Plus while keeping Additions preserves the warehouse implementation, but transport blocks/entities require this addon to remain installed. Remove or unload transport cargo before removing the addon from a world.

## Development

Work on `feature/long-distance-chunk-corridor`. Run `gradle build` with Java 25, Gradle 9.7.0 and Python 3. Models, recipes, translations and tags are generated deterministically by `scripts/generate_transport_models.py` into `build/generated/transport` during the build. Edit this generator for transport model changes.

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
