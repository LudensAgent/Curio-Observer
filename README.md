# Curio Observer

<p>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="MIT License"></a>
  <a href="https://github.com/LudensAgent/Curio-Observer/releases"><img src="https://img.shields.io/github/v/release/LudensAgent/Curio-Observer?cacheSeconds=86400" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/minecraft-1.20.1-62B47A.svg" alt="Minecraft Version">
  <img src="https://img.shields.io/badge/fabric_loader-0.18.5+-DBBE5B.svg" alt="Fabric Loader">
  <img src="https://img.shields.io/badge/java-17+-ED8B00.svg" alt="Java Version">
  <a href="https://github.com/LudensAgent/Curio-Observer/actions/workflows/build.yml"><img src="https://github.com/LudensAgent/Curio-Observer/actions/workflows/build.yml/badge.svg" alt="CI"></a>
  <a href="https://github.com/LudensAgent/Curio-Observer/releases"><img src="https://img.shields.io/github/downloads/LudensAgent/Curio-Observer/total?cacheSeconds=86400" alt="GitHub downloads"></a>
</p>

English | [简体中文](README.zh-CN.md)

A client-side spectator HUD mod for watching Mineflayer bots in Minecraft 1.20.1 (Fabric).
It mirrors the observed bot's inventory, crafting, containers and trading, and provides
first-person hands, dark-area brightening and an adaptive broadcast camera.

![Curio Observer workbench HUD with the observed bot's character, crafting grid, result and inventory](docs/images/workbench-observer.png)

*In-game workbench preview: the observed bot's character beside a 3×3 crafting grid, output slot,
inventory and hotbar.*

The mod runs on the observer client. A small bridge runs **inside the observed bot's process**
and exports its window events over HTTP SSE. No Minecraft server plugin or mod is needed.
Container overlays are read-only and never send inventory operations. Optional automatic observation
uses `/spectate <bot>` and, when needed, `/gamemode spectator`; the observer needs permission for those commands.

## Features

- **Spectator HUD**: `vitals` (health / armor / hunger / air / XP), `inventory` (armor / inventory / offhand / main hand), `view` (visual helper status), `hands` (first-person held items with swing, eating and use animations). Health bars respect the target's maximum health.
- **Dark-area brightening**: replaces the lightmap gamma so caves and dark spots are easier to see.
- **Auto camera**: adaptive third-/first-person broadcast camera while spectating.
- **Optional RCON channel**: when no matching bot feed is available, a server with `enable-rcon=true` can supply the spectated player's full inventory and hunger/XP.
- **Live container HUD**: inventory/workbench crafting, chest/barrel/ender chest/shulker storage, hopper/dispenser slots, grindstone/smithing results, anvil rename/cost, enchanting options, furnace/brewing progress and villager trade offers.
- **Character and item animation**: inventory and workbench layouts show the observed player's skin, equipment and held items. Changed slots pulse; carried items and transfer/drop animations retain actual stack counts.
- **Automatic observation and bilingual settings**: configure a target and reattachment interval, switch between English and Chinese, and adjust HUD scale, panel visibility and brightness from **K** settings.
- **Documented bot integration**: a standalone Mineflayer bridge, protocol examples, reconnect snapshots and real item NBT. The feed only applies to the matching spectated player.
- **Interactive acceptance bot**: 22 real Minecraft scenarios cover inventory, crafting, storage, workstations, trading and batch drops, with terminal controls and optional action pacing.

## Connect your Mineflayer bot

Copy [`integrations/mineflayer`](integrations/mineflayer) into your bot project, or install that directory as a local package. It has no runtime dependencies and does not require another Curio repository. Use Node.js 22+.

Install once after the actual observed bot's first spawn, when its inventory plugin is ready:

```js
import { installMineflayerObserver } from './integrations/mineflayer/index.js';

let observer;
bot.once('spawn', async () => {
  observer = installMineflayerObserver(bot);
  await observer.listen({ port: 3000 });
});

// Before replacing the bot instance or shutting down:
// await observer?.dispose();
```

For a readable player-like action pace, install the bridge with `{ actionPacing: { intervalMs: 220 } }`.
This spaces actual bot clicks so crafting, single-item placement and stack transfers remain visible;
await bot operations sequentially. Partial tosses first assemble the requested amount and then drop
that batch, with the HUD retaining each operation's real quantity. Pacing is opt-in for ordinary
integrations and enabled by default in the acceptance bot. The HUD does not queue old inventory snapshots.

### Start observing

Join the bot's Minecraft server with your observer client, enter spectator mode, and spectate that bot.
Press **K** and configure the feed URL for the integration above (the mod defaults to
`http://127.0.0.1:3000/api/observer/stream`).
The bridge defaults to loopback; for a remote bot configure its listener and the mod URL as
described in the adapter guide. The mod accepts HTTP/HTTPS and reconnects automatically.
A newly created spectator bot cannot supply another bot's private windows.

In **K** settings, set a **default target** such as `Alex` and enable **automatic observation**
to attach on a configurable interval. Once that player appears in the server's player list, it
first requests spectator mode when necessary, then spectates the target after the mode change is confirmed.
This requires server command permission. The default target is empty and automatic observation is off until configured.
An already attached target is not repeatedly reselected; disabling automatic observation stops
automatic commands. The mod's language can follow Minecraft or be set explicitly to English or Chinese.

Reattachment after a dimension change uses the same automatic-observation setting and interval.
Keep it enabled to resume watching the configured target when the server detaches the camera.

### Feed protocol

The minimum producer contract is **identity/state, window open/close, complete indexed slots,
cursor-held item and changes**. Workstation properties and trade offers enable the corresponding
specialized HUD. Backpacks' 2×2 crafting needs explicit start/end signals because it does not
emit a normal window-open event. Item tags can be supplied as SNBT to preserve enchantments,
potions and custom names.

See the **[integration protocol](docs/mineflayer-protocol.md)** ([中文](docs/mineflayer-protocol.zh-CN.md))
for required fields, payload examples, slot numbering, lifecycle, limits and capability coverage.
The **[Mineflayer adapter guide](integrations/mineflayer/README.md)** lists the concrete bot APIs it uses.
Only a read-only event stream is exposed to the mod; it does not receive a bot control API.
Existing Curio v1 window feeds remain compatible; unrelated thought/task/dashboard events are ignored.

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

The installable output is `build/libs/curio-observer-0.4.0.jar`; the `-sources.jar` is for development.
`build` runs unit tests and release-bytecode compatibility checks. To run both explicitly:

```bash
./gradlew test releaseMixinTest
```

Test the standalone bridge with Node.js 22+ (no dependency installation required):

```sh
node --test integrations/mineflayer/test/*.test.js
```

Test the acceptance bot after installing its dependencies:

```sh
npm ci --prefix integrations/mineflayer-test
node --test integrations/mineflayer-test/test/*.test.js
```

Automated checks cover protocol parsing, HTTP reconnects, player isolation, item NBT, layouts and
bot event forwarding, plus acceptance-bot sequencing, cancellation and paced inventory actions.
These tests do not connect to a Minecraft server. In-game validation should exercise chest open/close,
both crafting modes, grindstone results, trading, feed loss and a change of spectated target;
automated tests do not replace rendering validation.

## Configuration

On first launch the mod creates `config/curio-observer.json`. The bot feed is enabled by default;
RCON is optional and disabled by default:

```jsonc
{
  "feedEnabled": true,
  "feedUrl": "http://127.0.0.1:3000/api/observer/stream",
  "defaultTarget": "",
  "autoObserve": false,
  "autoObserveSeconds": 5,
  "language": "auto",
  "hudEnabled": true,
  "hudScale": 1.0,
  "panels": { "containers": { "visible": true } },
  "autoCamera": false,
  "brighten": false,
  "gamma": 3.0,
  "rconEnabled": false,
  "rconHost": "127.0.0.1",
  "rconPort": 25575,
  "rconPassword": "",
  "rconPollSeconds": 1
}
```

> RCON requires `enable-rcon=true` and `rcon.password` in the server's `server.properties`,
> plus the matching password in this mod's config.

HUD, auto camera and brightening have independent switches. In **K** settings, HUD scale accepts
0.5–3.0 and gamma accepts 0–15; gamma 1 matches vanilla Bright. Containers automatically scale down
to fit the screen. The JSON also supports `infoScale` for text cards (default 0.7),
`broadcastDistance` (default 3.0 blocks) and `broadcastSmoothing` (default 0.18 seconds).

Panels are `vitals`, `inventory`, `containers`, `view`, and `hands`; optional panel `x`/`y` values
are screen fractions from 0 to 1. When a container is visible, the regular inventory panel yields
its space. Disconnects clear container/cursor state and fall back to RCON or client-visible data.
RCON is not needed with a complete bot feed and vanilla RCON commands cannot expose active windows.
The automatic-observation interval accepts 1–300 seconds, defaulting to 5. The target must be a
Minecraft username of 1–16 letters, digits or underscores. The same scheduler handles reattachment;
there is no separate always-on follow loop when automatic observation is disabled.
Language values are `auto`, `en_us`, and `zh_cn`. The override changes settings, HUD labels and common
container captions immediately; choose **Done** to save it. Minecraft-rendered item and enchantment
names keep the game's language, and custom window titles keep the text supplied by the bot.

## Interactive acceptance bot

[`integrations/mineflayer-test`](integrations/mineflayer-test/README.md) contains a real Minecraft
test client, not a simulated telemetry feed. It connects as a Mineflayer bot and performs timed
container, crafting and workstation interactions while the observer watches. Its terminal supports
`list`, `next`, `next <id>`, `run`, `pause`, `stop`, `status` and `quit`.

```sh
npm ci --prefix integrations/mineflayer-test
node integrations/mineflayer-test/main.js --config integrations/mineflayer-test/config.example.json --prepare
```

The example uses offline `Alex` on `127.0.0.1:25565`, with an explicitly selected test area centered
at `(1000, 200, 1000)`. Preparation requires operator permission and places facilities only in air
inside the bounded area. Read the test-client guide before using `--prepare` on a world you care about.
Without `--prepare`, the client uses existing nearby facilities and skips missing prerequisites.
It stays connected after the sequence for inspection; `--run` starts the sequence automatically.

Use `--scenarios crafting_table,grindstone,villager` to select a smaller set, or `next crafting_table`
in the terminal to inspect the workbench shown above. Actual clicks are spaced by 220 ms by default;
`--action-interval-ms` controls that pace independently of the scene's `--dwell-ms` display time.
A scenario marked `passed` confirms the bot operation completed; visual HUD checks are still manual.

## Known limitations

- Without a matching bot feed, private windows cannot be displayed. Without a feed or RCON, inventory/hunger/XP are limited to the observer client's data.
- Stonecutter recipe lists, loom pattern menus, beacon choices and cartography map previews are not yet synchronized. Their slots and outputs are shown, not the complete vanilla interface.
- Horse inventories, lecterns, book/sign editors and custom mod screens do not have dedicated layouts. Unknown windows use a generic slot grid, which cannot reproduce special controls.
- A bot has no physical mouse or GUI scroll position. Carried-item animation follows slot changes or optional pointer events without drawing a mouse cursor; the trade list follows the selected offer.
- Full item detail requires the optional NBT field. The standalone adapter supplies item NBT; MinecraftLLM's existing feed does not supply tag SNBT. Feeds with only an enchantment flag use a simplified glint and cannot reproduce missing potion, enchantment or custom-tag details.
- The bridge observes existing actions; it does not add crafting/trading abilities missing from the bot or its Mineflayer/prismarine-windows version.

## License

Licensed under the [MIT License](LICENSE).
