# SeedMap - Modrinth page copy

Source of truth for https://modrinth.com/mod/fayber-seedmap.

## description

Explore a seed-based map of biomes and structures inside Minecraft, including unexplored areas.

## body

# SeedMap

A world map generated from your seed, right inside Minecraft. Press **M** to open it and explore the Overworld, Nether or End without visiting every chunk first.

## Features

- Pan and zoom through biome maps, with structure icons and filters.
- Search for biomes, structures and amethyst geodes within a chosen radius.
- Show slime chunks, chunk borders, the spawn marker and biome Y layers.
- Place waypoints from map markers or search results, with an in-world beam and HUD direction arrow.
- Copy coordinates, share locations, measure distances and export the map as a PNG.
- Background map generation, cached tiles and saved seeds for each server.

## Usage

In singleplayer, the world seed is read automatically. On multiplayer servers, enter the seed in the map sidebar and press **Apply**. No server mod is required.

Use the sidebar to choose which structures to show and search for a location. Right-click a marker for waypoints, coordinates and sharing. Shift-drag to measure a distance.

## Notes

- Minecraft 26.3, Fabric Loader and Fabric API. Client-side only.
- Structure locations are predictions. Minecraft may reject a structure during generation, and custom terrain or server datapacks can change the result.
- Settings: `config/seedmap.json`. Cached map tiles: `seedmap/tiles`.

## Credits

Based on [SeedScout](https://github.com/CJH3139/SeedScout) by roych, licensed under Apache-2.0. SeedMap is independently maintained and is not an official SeedScout release. The original license is preserved in the project.

SeedMap is licensed under GPL-3.0-or-later.
