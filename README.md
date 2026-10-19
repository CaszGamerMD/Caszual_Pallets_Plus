# Caszual Pallets Plus

Fabric 26.2 / Java 25 transport addon. **Requires Caszual Additions 0.1.1 or later**, installed as a separate mod on both client and server.

## Ownership

This repository owns trains and coupling, three-pallet flatbeds, passenger cars, rails, station posts, conveyors and logic/hold controls, drones and chunk loading, drone bays, pallet pressure plates and the transit computer.

Caszual Additions continues to own pallets, cargo storage, racks, warehouse computers, scanners, storage Golems, transfer points, wrappers and all other warehouse features. None of those classes are copied into this addon. The optional MovingPallet integration uses the warehouse's existing cargo and navigation, adding transport delivery modes without overwriting warehouse modes.

## Installation and existing worlds

Install the regular Caszual Additions JAR alongside `caszual-pallets-plus-0.2.1.jar`. Do not install the old combined `pallet-transport` preview of Additions with this addon; it registers the same transport blocks and entities.

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
- Active trip positions and short target headings are persisted using the existing transport SavedData; active trips can restore their ticket corridor after a restart. Missing chunks cause the vehicle to wait safely and retry when loaded.
- Ticket renewal stops on parking, stations, indexer stops and paused conveyors. Expiring tickets leave no permanent vanilla `/forceload` changes. Busy networks consume additional server resources while transports move. Mobs and other nearby activity may tick inside loaded chunks.
- Existing train, conveyor, drone and pallet IDs are unchanged. Back up existing saves, then replace the previous addon JAR; do not install both versions.

Automated tests cover corridor limits, negative chunk coordinates and server-record activation/release. Client-side render checks and long-distance multiplayer/world-reload gameplay should still be performed before considering this a stable release.
