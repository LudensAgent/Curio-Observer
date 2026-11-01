# Mineflayer observer contract, version 1

[简体中文](mineflayer-protocol.zh-CN.md)

Curio Observer is a Minecraft 1.20.1 Fabric client mod for spectating a Mineflayer bot. Its camera uses Minecraft's spectator view; private inventories and interaction windows arrive through the bot's telemetry stream. This document is the public integration contract. The bundled [adapter](../integrations/mineflayer/README.md) implements it without depending on another repository.

## Minimum implementation

An exporter must run in, or receive events from, the **actual observed bot**. Ordinary spectator clients do not receive that bot's private window slots. A Minecraft server mod is unnecessary when the bot supplies this feed. RCON remains an optional fallback for player inventory/vitals and does not replace the window feed.

Implement these requirements for basic container support:

1. Serve a UTF-8 HTTP **GET** stream with `Content-Type: text/event-stream`.
2. Start each connection with `hello`, `bot_status`, and a fresh full `state`. If a window is open, follow with `window_open` and `cursor`.
3. Publish window open/close and every changed slot/cursor while the bot operates. Preserve slot indices and `null` empty slots; never compact the slot array.
4. Send a comment heartbeat at least every 15 seconds. The client treats a silent connection as stale after 45 seconds and reconnects.
5. On every reconnect send the complete current snapshot again. This v1 contract has no durable replay cursor or `Last-Event-ID` requirement.
6. Send `bot_status` `offline` or `dead` when applicable, and clear the active window. A later online state requires fresh data.

One stream represents one bot. `hello.bot` must exactly match the observed Minecraft player's username; the mod does not overlay another bot's inventory on the camera target. `hello.protocol` must be `1`, and `mcVersion` must be `1.20.1`.

The default path is `/api/observer/stream`, with URL `http://127.0.0.1:3000/api/observer/stream`. Configure `feedEnabled` and `feedUrl` in the mod's K settings or `config/curio-observer.json`.

```text
event: hello
data: {"protocol":1,"bot":"Curio","mcVersion":"1.20.1","agentVersion":"my-bot/1.0"}

event: bot_status
data: {"status":"online"}

: heartbeat

```

Each event ends with a blank line. `data` is a JSON object. Unknown event names are ignored, allowing producers to keep unrelated agent events on an existing stream. Limits: 262,144 characters per JSON event, 256 slots, and 65,536 characters per item's optional SNBT tag. Large custom inventories or book-heavy snapshots must remain within these limits; do not truncate a JSON frame.

## What Mineflayer must expose

| Source | Required information |
|---|---|
| Identity | `bot.username`, Minecraft `bot.version` |
| Player inventory | `bot.inventory.slots`, `bot.quickBarSlot`; health/food/experience for full vitals |
| Active window | `bot.currentWindow`, with `id`, `type`, `title`, `slots`, `inventoryStart`, `selectedItem` |
| Lifecycle | `windowOpen`, `windowClose`, inventory/window `updateSlot`, `spawn`, `death`, `end`/`kicked` |
| Cursor and in-place mutations | `_client` `set_slot`/`window_items`, or equivalent authoritative cursor events; completed `acceptClick` updates |
| Workstation attributes | `craft_progress_bar` (also called `window_property`) |
| Villager trading | `trade_list`, outgoing `select_trade`, and `bot.registry.items` for numeric item IDs |
| Option selection | Outgoing `enchant_item`, `name_item`, plus equivalent events for future workstations |
| 2×2 crafting | An explicit start/end signal around `bot.craft()` or a custom inventory operation |

These are data requirements, not remote-control APIs. The mod does not need access to the bot's planner, pathfinding, credentials, or operation functions. A producer can publish equivalent data from another implementation.

## Item and player state

An empty slot is JSON `null`. An item has the following shape:

```json
{
  "id": "minecraft:enchanted_book",
  "count": 1,
  "damage": 0,
  "maxDamage": 0,
  "enchanted": true,
  "name": null,
  "nbt": "{StoredEnchantments:[{id:\"minecraft:mending\",lvl:1s}]}"
}
```

`nbt` is optional **SNBT for the item's tag compound**, not the whole `{id,Count,tag}` ItemStack. Preserve numeric suffixes, lists, arrays, display names, potion data and actual enchantments. Do not replace an enchanted book's NBT with an `enchanted: true` flag. Legacy items without `nbt` still render their basic fields; `damage`, `maxDamage`, `enchanted` and `name` default when absent. The bundled adapter omits tags exceeding the per-item limit while retaining basic item fields. When the encoded JSON of an entire snapshot exceeds the event limit, it omits all optional NBT for that frame, then optional names if still necessary, keeping every slot, item ID and count intact.

`state` fields:

| Field | Meaning |
|---|---|
| `health`, `absorption`, optional `maxHealth` | Health values; default maximum is 20 |
| `food`, `saturation`, `oxygen` | Food/saturation and oxygen in the 0–20 HUD scale |
| `xp: {level, progress}` | Progress is 0–1 |
| `armor` | Exactly 4 items, head/chest/legs/feet |
| `main` | Exactly 27 items, main inventory |
| `hotbar` | Exactly 9 items |
| `offhand` | Item or null |
| `selected` | Hotbar index 0–8 |

While a container is open, Mineflayer's active window may have newer player slots than `bot.inventory`. Read its 36 appended player slots using `inventoryStart`. Minecraft 1.20 smithing has **four** workstation slots before the player inventory; some older prismarine-windows versions report an offset of three.

## Window events

`window_open` provides `{windowId, kind, title, slots}` plus optional full workstation states described below. `slots` uses the actual **window slot numbering**, including the appended player inventory. These are not player NBT `Inventory` slot numbers.

| Event | JSON fields |
|---|---|
| `window_open` | `windowId`, `kind`, `title`, full `slots`, optional `furnace/enchanting/anvil/merchant/brewing` |
| `slot` | `windowId`, `slot`, `item` |
| `cursor` | `item`; mouse-carried stack or null |
| `window_close` | `windowId` |
| `bot_status` | `status`: `online`, `offline`, or `dead` |

Open the new window before sending its slot/property changes. Ignore updates for an old window after closing it. Cursor is scoped to the active window and is cleared on close. Send close even if the window was opened and closed quickly; periodic latest-state polling alone loses such interactions.

Supported `kind` values: `inventory`, `crafting_table`, `chest`, `large_chest`, `generic`, `generic_3x3`, `shulker_box`, `hopper`, `furnace`, `blast_furnace`, `smoker`, `brewing_stand`, `enchanting_table`, `anvil`, `smithing`, `grindstone`, `stonecutter`, `loom`, `cartography_table`, `beacon`, `merchant`. Unknown kinds use a generic slot layout. Barrels and ender chests use a chest layout; droppers and dispensers use `generic_3x3`.

Important layouts:

| Window | Slot numbering |
|---|---|
| Player inventory | result 0; 2×2 inputs 1–4; armor 5–8; main 9–35; hotbar 36–44; offhand 45 |
| Workbench | result 0; 3×3 inputs 1–9; main 10–36; hotbar 37–45 |
| Grindstone / anvil | inputs 0–1; result 2; player inventory starts at 3 |
| Smithing 1.20.1 | template 0; equipment 1; addition 2; result 3; player inventory starts at 4 |
| Merchant | inputs 0–1; result 2; player inventory starts at 3 |
| Chest | container 0–26 or 0–53; player inventory follows |

The 2×2 player inventory has no ordinary Mineflayer `windowOpen`. Bracket the actual operation explicitly (`openInventory()`/`closeInventory()`), as the bundled `bot.craft()` wrapper does. An ordinary human pressing E without touching slots is a client-only screen change and is not inferred from this data.

## Workstations and trading

These fields can be embedded in `window_open`. Later events use the same field names at the top level alongside `windowId`, for example `{"windowId":2,"burnTime":80,"fuelTime":1600,"cookTime":40,"cookTimeTotal":200}` for `furnace`. Each event is a **full known property snapshot**, with `null` for values not received yet.

| Event | Payload after `windowId` | Mineflayer source |
|---|---|---|
| `furnace` | `burnTime`, `fuelTime`, `cookTime`, `cookTimeTotal` in game ticks | property IDs 0, 1, 2, 3 |
| `brewing` | `brewTime` remaining ticks, `fuel` remaining operations | properties 0, 1 |
| `anvil` | `cost` in levels or null, `name` string or null | property 0 and outgoing `name_item` |
| `enchanting` | `options` exactly 3 `{cost,enchant,level}` records, `chosen` 0–2 or null | properties 0–2 costs, 4–6 enchantment IDs, 7–9 levels; outgoing `enchant_item` |
| `merchant` | `offers`, `selected`, `level`, `experience`, `regular`, `canRestock` | `trade_list` and outgoing `select_trade` |

A merchant offer is `{input1,input2,output,disabled,uses,maxUses}`. Item fields use the complete item format above, including NBT. `input1.count` is the **actual adjusted cost** after demand and special price, not just the base quote. `selected` is the offer index; null means unobserved. The HUD keeps the selected offer visible; it does not know the bot's nonexistent physical mouse-wheel position.

Some property packets arrive before Mineflayer emits `windowOpen`. Cache them against that current window object and include them in its eventual open snapshot. Do not manufacture furnace progress or enchantment options from unrelated inventory changes.

Mineflayer also updates a villager's `trades[].nbTradeUses` and `tradeDisabled` in place after a completed trade, sometimes without another `trade_list` packet. Reconcile those values from the active window after trade completion. The bundled adapter does this and can replay an already-open merchant's existing `trades` when installed late.

## Optional operation presentation

Optional presentation events are `pointer` (`windowId,slot,durationMs,item`) and `inventory_transfer` (`windowId,slot,kind,item`, where kind is `take`, `put`, `drop`, or legacy `craft`/`quick_move`). `slot=-999` denotes outside-window drops for these two events only. `pointer.item` must be the actual carried cursor stack, or null before a pickup. Transfer `item.count` is the actual quantity moved in that one operation: a three-item batch drop is one `drop` event with count 3, not three count-1 events. These events never replace authoritative slot/cursor updates.

Publish the pointer before the operation, then publish its transfer after completion. For a right-click drop from a carried stack of 64, the pointer carries 64, the transfer contains 1, and the remaining cursor contains 63. The pointer must not carry an item that has not been picked up yet.

The bundled adapter emits these events only when explicitly installed with `{ actionPacing: { intervalMs: 220 } }` (or `true` for that default). It delays actual inventory clicks before sending them, rather than replaying stale snapshots after fast actions. Default observation has no timing changes. The optional helper routes public Mineflayer 4.39.0 inventory/container APIs through paced clicks; a partial `bot.toss(...,3)` is assembled and genuinely dropped as one stack, with a clear preflight error if no spare slot is available. See the [adapter API](../integrations/mineflayer/README.md#optional-real-action-pacing) for cancellation, supported methods and standalone usage.

The HUD applies slot/cursor snapshots immediately. It retains at most eight recent transfers for 1.2 seconds, draws each actual dropped stack for up to 450 ms with its exact count, and fades a closed window for 200 ms. New windows and disconnection clear old presentation state. These short traces do not queue or replay historical inventories; fast unpaced operations may still have intermediate slots that are gone before the next rendered frame.

## Coverage and limits

| Feature | Current implementation |
|---|---|
| Storage, crafting, grindstone, smithing | Window lifecycle, exact slots, items/NBT, cursor and result |
| Furnace variants, brewing | Slots and real progress/fuel properties |
| Enchanting, anvil | Offers/costs/selected option and rename state |
| Villager/itinerant trading | Offers, adjusted price, selection, disabled state, level and experience |
| Stonecutter, loom, cartography, beacon | Container slots; dedicated choice lists and previews not yet synchronized |
| Horse/lectern, books/signs/command-block editors | No dedicated renderer/adapter contract yet; generic grids do not reproduce their full UI |
| True mouse position, hover, scrolling, unsubmitted editor text | Not provided by vanilla bot window packets |

All HUDs are read-only. A rendered slot or button does not issue a click, craft or trade. Having a texture/layout does not imply a complete vanilla interface. This scope lets exporters state what they actually provide and extend the protocol deliberately rather than infer private UI state.

## Validation and compatibility

The existing CurioObserver reference v1 feed's window events are accepted; unrelated thoughts/tasks/dashboard events are ignored. The standalone adapter and this mod are self-contained and do not load files from the reference repository.

Run `node --test integrations/mineflayer/test/*.test.js` for the adapter tests and `./gradlew build` for the client tests. Test coverage includes HTTP reconnect replay, window close, target isolation, invalid frames, typed NBT, actual trade pricing and 2×2 operation boundaries. A live Mineflayer + server + game client smoke test remains necessary when validating a particular Mineflayer/prismarine version.
