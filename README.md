# Curio Observer

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

English | [简体中文](README.zh-CN.md)

A pure client-side spectator HUD mod for Minecraft 1.20.1 (Fabric). While spectating another
player, it renders their health / armor / held items / inventory, and provides two visual
helpers: dark-area brightening and an adaptive broadcast camera.

> This mod sends **no packets** to the server and requires **no server-side mod**.

## Features

- **Spectator HUD**: `vitals` (health / armor / hunger / air / XP), `inventory` (armor / inventory / offhand / main hand), `view` (visual helper status), `hands` (first-person held-item rendering).
- **Dark-area brightening**: replaces the lightmap gamma so caves and dark spots are easier to see.
- **Auto camera**: adaptive third-/first-person broadcast camera while spectating.
- **Optional RCON channel**: when the server has `enable-rcon=true`, it can pull the spectated player's full inventory and hunger/XP (otherwise those values show as defaults).

## Keybindings (defaults)

| Key | Action |
|-----|--------|
| `O` | Toggle spectator HUD |
| `B` | Toggle brighten dark areas |
| `V` | Toggle auto camera |
| `K` | Open observer settings |

All keys can be rebound under "Options → Controls → Key Binds", and the toggle state is persisted to `config/curio-observer.json`.

## Requirements

- Minecraft **1.20.1**
- [Fabric Loader](https://fabricmc.net/use/) `>= 0.18.5`
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java **17+**

## Installation

1. Install Fabric Loader (for 1.20.1) and Fabric API.
2. Download `curio-observer-<version>.jar` from [Releases](../../releases) (or from a GitHub Actions build).
3. Place the jar in `.minecraft/mods/` and launch the game.

## Building

Requires Java 17. From the repository root:

```bash
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```

The output is `build/libs/curio-observer-0.3.8.jar`. To run the unit and release-compatibility tests:

```bash
./gradlew test
```

## Configuration

On first launch the mod creates `config/curio-observer.json`. RCON-related options are disabled by default:

```jsonc
{
  "rconEnabled": false,
  "rconHost": "127.0.0.1",
  "rconPort": 25575,
  "rconPassword": "",
  "rconPollSeconds": 1
}
```

> RCON requires `enable-rcon=true` and `rcon.password` in the server's `server.properties`,
> plus the matching password in this mod's config.

## Known limitations

A spectator can only see what the server syncs to it, therefore:

- Health, armor, main/off hand and air (bubbles) display correctly.
- Hunger/XP of a remote spectated player usually show defaults (the server does not sync them to spectators), unless RCON is enabled.
- The full 27-slot inventory and open crafting-table / furnace / enchanting-table UIs are invisible to spectators; the "inventory" panel only shows equipment and held items the client actually receives.

## License

Licensed under the [MIT License](LICENSE).
