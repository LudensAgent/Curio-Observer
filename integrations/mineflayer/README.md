# Mineflayer integration

This dependency-free ESM adapter exposes the **existing bot's** inventory and current interaction window to Curio Observer. It targets Minecraft **1.20.1** and Node **22+**. A second spectator bot cannot supply another player's private slots. No Minecraft server plugin or RCON is required for this feed.

The published data contract is [English](../../docs/mineflayer-protocol.md) / [简体中文](../../docs/mineflayer-protocol.zh-CN.md). Integrations in other languages can implement that contract directly.

## Connect an existing bot

Copy this directory into your application, or install it as a local package with `npm install /path/to/Curio-Observer/integrations/mineflayer`. The adapter does not create or log in a bot. Its default mode only observes; action pacing is explicitly opt-in.

```js
import { installMineflayerObserver } from '@curio-observer/mineflayer';

// `bot` is the Mineflayer instance your application already created.
// First spawn ensures its inventory plugin and identity are ready.
let observer;
bot.once('spawn', async () => {
  observer = installMineflayerObserver(bot);
  await observer.listen({ port: 3000 });
});

// During application shutdown or before replacing this bot:
// await observer?.dispose();
```

In the mod's **K** settings, enable **Mineflayer feed** and set the stream URL to `http://127.0.0.1:3000/api/observer/stream`. Spectate the bot with the matching Minecraft username. The mod retains its local camera, brightness, and panel settings.

If the bot has already spawned, install immediately. Recreating a bot requires disposing the previous observer and installing one for the new instance. A dimension change or ordinary respawn on the same bot does not require reinstalling.

`listen()` binds to `127.0.0.1` by default. For a bot on another machine, supply the intended interface as `host` and configure the mod with that machine's reachable URL. The adapter's HTTP endpoint itself has no authentication; expose it through the application's authenticated proxy or a private connection when needed. It sends only telemetry and accepts no control commands.

## Existing HTTP application

```js
import http from 'node:http';
import { installMineflayerObserver } from '@curio-observer/mineflayer';

const observer = installMineflayerObserver(bot);
const server = http.createServer((request, response) => {
  if (observer.handler(request, response)) return;
  response.writeHead(404).end();
});
server.listen(3000, '127.0.0.1');
```

`handler()` returns `false` for paths other than `/api/observer/stream`. The caller owns this externally created server; `dispose()` only closes servers created with `observer.listen()`.

## API

- `installMineflayerObserver(bot, { heartbeatMs?, stateIntervalMs?, actionPacing? })`: installs event listeners and observational wrappers. The inventory plugin, `bot.username`, and `bot.inventory.slots` must already exist. A duplicate installation is rejected.
- `observer.listen({ port = 3000, host = '127.0.0.1' })`: starts an HTTP server and resolves to its Node `Server`.
- `observer.handler(request, response)`: mounts the SSE stream in an existing server.
- `observer.openInventory()` / `observer.closeInventory()`: brackets custom 2×2 inventory operations that bypass `bot.craft()`.
- `observer.cancelPendingActions()`: cancels paced clicks not yet sent, including queued clicks. It cannot undo an action already sent to the server.
- `await observer.dispose()`: restores wrapped methods, removes listeners, ends stream clients and closes owned servers. Call before replacing the bot.

By default the adapter wraps `bot.craft()`, `bot.trade()`, the active window's `acceptClick()`, and `_client.write()` to observe otherwise invisible changes. It preserves their arguments/results and adds **no delays**. Your controller remains responsible for serializing inventory operations on a single bot. The package exports TypeScript declarations for the minimum observer and optional pacing source interfaces.

## Optional real action pacing

```js
const observer = installMineflayerObserver(bot, {
  actionPacing: { intervalMs: 220 }, // true also selects 220 ms; omitted/false preserves existing speed
});
await observer.listen();
```

This waits before each actual inventory click, then lets Mineflayer apply the click and send its normal packet. It serializes clicks without delaying incoming packets or fabricating intermediate inventories. `pointer` approaches the target with the actual carried item; `inventory_transfer` describes the actual single-item or stack quantity moved. Right-click placement remains one item, while left-click placement moves the permitted stack in one operation. Slot and cursor updates remain authoritative and immediate. Ordinary player-inventory actions automatically show an inventory HUD and close it 650 ms after the last completed transfer when the cursor is empty; explicit `openInventory()`/`closeInventory()` retains manual control.

The supported API is Mineflayer **4.39.0 / Minecraft 1.20.1**. Its `transfer`, `putAway`, `moveSlotItem`, `putSelectedItemRange`, and returned container `deposit`/`withdraw` functions capture internal clicks, so pacing replaces these with equivalent public-click sequences. Crafting, trading, furnace, brewing, enchanting and anvil APIs then use those paced operations. Network writes are never postponed. Controllers must still await each high-level operation before starting the next. Custom plugins that retain original function references or send their own packets bypass pacing.

With pacing enabled, `bot.toss(type, metadata, 3)` first assembles three matching items into an empty inventory slot and drops that stack with one genuine outside left-click. It does **not** manufacture three one-item drop events. Full stacks can drop directly; amounts larger than a stack become real stack-sized batches. If assembly requires a spare slot and there is none, the operation rejects before dropping anything. Inventory space failures retain the cursor rather than silently dropping its contents. `bot.tossStack(item)` keeps its original whole-stack semantics.

For controllers without SSE, use `installMineflayerActionPacing(bot, { intervalMs, onPointer?, onTransfer? })` from the same package; it returns `{ intervalMs, cancelPending(), dispose() }`. Do not install it twice or combine it with the observer's `actionPacing` option. Call `cancelPending()` on cancellation, let the active operation settle, then dispose; changing/closing the real window cancels delayed clicks automatically. `intervalMs` is an integer from 0 to 2000. The acceptance bot defaults to 220 ms; the passive adapter remains unchanged unless enabled.

## Coverage

Window open/close, all slots, cursor, grindstone and smithing results, 2×2/workbench crafting, furnace timing, brewing, enchantment offers, anvil naming/cost, and villager offers/selection are captured. Typed item NBT is preserved, including enchanted books and custom item names. If NBT-heavy snapshots exceed the protocol's JSON budget, the adapter omits optional NBT for that frame (and names if still necessary), preserving every item, count and slot. The adapter listens for early workstation properties that arrive before Mineflayer emits `windowOpen` and reconciles trade uses/disabled state after Mineflayer completes a trade.

Cutting recipes, loom pattern choices, beacon choices, cartography/map previews, and non-container editors such as signs/books are not exported as dedicated UI state yet. The current client renders their available container slots, not a complete replica of every vanilla widget. See the protocol's capability matrix before implementing an exporter.

## Validation

```sh
node --test integrations/mineflayer/test/*.test.js
```

Tests use real HTTP SSE connections and fake Mineflayer event sources: reconnect replay, window lifecycle, properties arriving before open, cursor, 2×2 crafting, trade prices and full item NBT. They do not claim a live Minecraft server or graphical game-session test.

After `npm ci` in `integrations/mineflayer-test`, its `test/pacing.test.js` also checks the pinned Mineflayer inventory plugin and Prismarine window implementation: packet timing, exact counts, batch drops, container closures, cancellation and restoration. No real server connection is made by these automated tests.
