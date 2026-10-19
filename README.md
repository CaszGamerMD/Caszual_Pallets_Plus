# Caszual Pallets Plus

Fabric 26.2 / Java 25 transport addon. **Requires Caszual Additions 0.1.1 or later**, installed as a separate mod on both client and server.

## Ownership

This repository owns trains and coupling, three-pallet flatbeds, passenger cars, rails, station posts, conveyors and logic/hold controls, drones and chunk loading, drone bays, pallet pressure plates and the transit computer.

Caszual Additions continues to own pallets, cargo storage, racks, warehouse computers, scanners, storage Golems, transfer points, wrappers and all other warehouse features. None of those classes are copied into this addon. The optional MovingPallet integration uses the warehouse's existing cargo and navigation, adding transport delivery modes without overwriting warehouse modes.

## Installation and existing worlds

Install the regular Caszual Additions JAR alongside `caszual-pallets-plus-0.1.0.jar`. Do not install the old combined `pallet-transport` preview of Additions with this addon; it registers the same transport blocks and entities.

Back up the world before changing mods. Finish active Golem train/conveyor loading jobs before replacing the old combined preview. Existing track, conveyor, vehicle, cargo and transit-data identifiers retain their `caszual_additions:` namespace intentionally, so the extracted addon can read those world objects. They are now registered by Pallets Plus. Pallets Plus has its own creative tab and contributes transport items to the shared Caszual content tag.

Removing Pallets Plus while keeping Additions preserves the warehouse implementation, but transport blocks/entities require this addon to remain installed. Remove or unload transport cargo before removing the addon from a world.

## Development

Work on `feature/transport-extraction`. Run `gradle build` with Java 25, Gradle 9.7.0 and Python 3. Models, recipes, translations and tags are generated deterministically by `scripts/generate_transport_models.py` into `build/generated/transport` during the build. Edit this generator for transport model changes.

The pinned, unmodified Additions JAR in `libs` is a **compile/dev dependency only**, never bundled in the addon. Its source commit and checksum are recorded in `libs/README.md`. This lets both private repositories build without a broad cross-repository access token. Update the pinned dependency deliberately when warehouse APIs change. Runtime integration targets the existing MovingPallet fields/methods and fails loudly if an incompatible Additions build changes them.

CI builds the addon, runs transport path/save tests, checks asset geometry and packaging isolation, then starts a dedicated server with both mods to exercise mixin application and registration. Client rendering and actual Golem/train/drone gameplay still need in-game testing.

See [transport usage](docs/transport.md) for controls and placement rules inherited from the original transport preview.
