# SeedMap

A seed-based world map inside Minecraft. Client-side Fabric mod for Minecraft 26.3.

Press **M** to open the map. It predicts biomes and structure locations from the world seed, including unexplored regions.

## Features

- Overworld, Nether and End maps with pan and zoom.
- Structure icons, toggleable types, biome and structure search.
- Default-on clutter hiding without marker clustering: crowded icons are hidden at real positions instead of replaced with cluster counts.
- Optional legacy clustering and experimental white icon outlines (off by default).
- Strict, unsmoothed 2-physical-pixel silhouette border: outlines follow pixel staircases and corners instead of rounding them off.
- 3D item/model outlines use Minecraft 26.3's already rendered GUI item atlas (including oversized picture-in-picture models and resource packs), with only two asynchronous capture readbacks allowed at a time. Captured silhouettes are cached and invalidated by resource pack reloads. Note: GUI/atlas GPU-backend compatibility still requires an Ingame test.
- Find amethyst geodes, show slime chunks and explore biome Y layers.
- Search within configurable radii and place waypoints from the results.
- HUD direction arrow and in-world waypoint beam.
- Optional translucent Freecam camera arrow, distinct from your real player marker (supports detached camera entities and WI Freecam).
- Coordinates, distance measurement, chunk grid, spawn marker and PNG export.
- Background tile generation and on-disk cache.
- Per-server seed storage. No server mod required.
- Optional Mod Menu integration: Mods → SeedMap → Configure opens the existing settings screen.

## Install and use

Requires Minecraft 26.3, Fabric Loader and Fabric API. Install the SeedMap jar in the `mods` folder. Press M while in a world.

In singleplayer the world seed is read automatically. In multiplayer enter the correct seed in the map sidebar and press Apply. All three dimensions use that seed.

Use the sidebar to filter structure icons, search for structures or biomes and select results. Right-click a marker to set a waypoint, copy coordinates, share the location or use a permitted teleport command. Shift-drag to measure distances. Use Export to save a PNG.

## Building

Requires JDK 25. Run `./gradlew build` (Windows: `gradlew.bat build`). The jar is written to `build/libs`.

## Accuracy

SeedMap predicts ungenerated areas using Minecraft's world-generation logic. A predicted structure is not guaranteed to exist, since additional generation checks may reject it. On multiplayer servers, custom terrain generation and unknown datapacks can make predictions inaccurate.

Settings are saved in `config/seedmap.json`. Cached tiles are stored in `seedmap/tiles` and can be deleted.

## Credits and license

Copyright (C) 2026 Fayber. This project is distributed under [GPL-3.0-or-later](LICENSE).

Based on [SeedScout](https://github.com/CJH3139/SeedScout) by roych, licensed under Apache-2.0. The original license is preserved in [THIRD_PARTY_LICENSES/SeedScout-LICENSE](THIRD_PARTY_LICENSES/SeedScout-LICENSE). The underlying world-generation, map rendering and search systems started from that project. SeedMap is independently maintained and not an official SeedScout release.
