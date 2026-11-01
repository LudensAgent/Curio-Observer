import http from 'node:http';
import { itemData, text, windowKind } from './items.js';
import { installMineflayerActionPacing } from './pacing.js';
export { installMineflayerActionPacing } from './pacing.js';

export const STREAM_PATH = '/api/observer/stream';
const frame = (event, data) => {
  let json = JSON.stringify(data);
  // NBT-rich books/shulkers can exceed the receiver's aggregate event budget even
  // when each tag fits. Keep every slot/count and omit optional detail for that frame.
  if (json.length > 262_144) json = JSON.stringify(data, (key, value) => key === 'nbt' ? null : value);
  if (json.length > 262_144) json = JSON.stringify(data, (key, value) => key === 'nbt' || key === 'name' ? null : value);
  return `event: ${event}\ndata: ${json}\n\n`;
};
const installations = new WeakMap();

/**
 * Observe the actual Mineflayer bot after its inventory plugin is initialized (normally first spawn).
 * @param {object} bot The existing Mineflayer bot; see docs/mineflayer-protocol.md for its required fields.
 * @param {{heartbeatMs?: number, stateIntervalMs?: number, actionPacing?:boolean|{intervalMs?:number}}} [options]
 * @returns {{handler: Function, listen: Function, openInventory: Function, closeInventory: Function, dispose: Function}}
 */
export function installMineflayerObserver(bot, options = {}) {
  if (!bot?.inventory || !Array.isArray(bot.inventory.slots) || typeof bot.on !== 'function'
      || typeof bot.username !== 'string' || !bot.username) {
    throw new TypeError('Install after Mineflayer initializes bot.username, bot.inventory.slots and its events');
  }
  if (installations.has(bot)) throw new Error('This bot already has an observer; dispose it before reinstalling');
  if (bot.version && bot.version !== '1.20.1') throw new Error('This bridge currently targets Minecraft 1.20.1');

  const clients = new Set();
  const servers = new Set();
  const cleanup = [];
  const properties = new WeakMap();
  let window = null;
  let windowCleanup = [];
  let virtualInventory = false;
  let slots = [];
  let cursor = null;
  let status = bot.entity ? 'online' : 'offline';
  let stateTimer;
  let lastState = '';
  let disposed = false;
  let crafting = 0;
  let pacingInventory = false;
  let pacingCloseTimer;
  const pacing = options.actionPacing ? installMineflayerActionPacing(bot, {
    ...(options.actionPacing === true ? {} : options.actionPacing),
    onPointer(data) {
      clearTimeout(pacingCloseTimer);
      if (!window && !bot.currentWindow) { openWindow(bot.inventory, true); pacingInventory = true; }
      publish('pointer', data);
    },
    onTransfer(data) {
      publish('inventory_transfer', data);
      if (pacingInventory) pacingCloseTimer = setTimeout(() => {
        if (pacingInventory && virtualInventory && !bot.inventory.selectedItem) closeWindow();
      }, 650);
    },
  }) : null;
  if (pacing) cleanup.push(() => { clearTimeout(pacingCloseTimer); pacing.dispose(); });

  const write = (response, value) => {
    // A disconnected/slow viewer must not grow the bot's memory indefinitely.
    if (response.destroyed || response.writableLength > 1_048_576) { response.destroy(); clients.delete(response); return; }
    response.write(value);
  };
  const publish = (event, data) => {
    if (disposed) return;
    const value = frame(event, data);
    clients.forEach((response) => write(response, value));
  };
  const listen = (emitter, event, callback, removers = cleanup) => {
    emitter.on(event, callback);
    removers.push(() => emitter.removeListener(event, callback));
  };
  const replace = (owner, name, wrapper, removers = cleanup) => {
    const original = owner[name];
    if (typeof original !== 'function') return;
    const wrapped = wrapper(original);
    owner[name] = wrapped;
    removers.push(() => { if (owner[name] === wrapped) owner[name] = original; });
  };
  const state = () => {
    const inventory = bot.inventory.slots;
    const active = bot.currentWindow;
    const start = active && windowKind(active.type) === 'smithing' && active.slots.length >= 40
      ? 4 : active?.inventoryStart;
    const range = (source, first, count) => Array.from({ length: count }, (_, index) => itemData(source[first + index]));
    const playerSlots = active && Number.isInteger(start) && start + 36 <= active.slots.length
      ? active.slots.slice(start, start + 36) : inventory.slice(9, 45);
    return {
      health: bot.health ?? 20, absorption: bot.absorption ?? bot.entity?.metadata?.[15] ?? 0,
      food: bot.food ?? 20, saturation: bot.foodSaturation ?? 0,
      oxygen: Math.max(0, Math.min(20, Math.round(bot.oxygenLevel ?? 20))),
      xp: { level: bot.experience?.level ?? 0, progress: bot.experience?.progress ?? 0 },
      armor: range(inventory, 5, 4), main: range(playerSlots, 0, 27), hotbar: range(playerSlots, 27, 9),
      offhand: itemData(inventory[45]), selected: Math.max(0, Math.min(8, bot.quickBarSlot ?? 0)),
    };
  };
  const queueState = () => {
    if (stateTimer || disposed) return;
    stateTimer = setTimeout(() => {
      stateTimer = undefined;
      if (status !== 'online') return;
      const next = state();
      const serialized = JSON.stringify(next);
      if (serialized !== lastState) { lastState = serialized; publish('state', next); }
    }, Math.max(50, options.stateIntervalMs ?? 100));
    stateTimer.unref();
  };
  const offersOf = (trades) => trades.map((trade) => {
    const fromSlot = (slot) => {
      if (!slot || slot.present === false) return null;
      if (slot.present !== true) return itemData(slot);
      const definition = bot.registry?.items[slot.itemId];
      return definition ? itemData({ name: definition.name, count: slot.itemCount,
        maxDurability: definition.maxDurability, nbt: slot.nbtData }) : null;
    };
    const input1 = fromSlot(trade.inputItem1);
    if (input1) {
      const base = trade.inputItem1;
      const stackSize = base.stackSize ?? bot.registry?.items[base.itemId ?? base.type]?.stackSize ?? 64;
      const demand = Math.max(0, Math.floor(Math.fround(Math.fround(input1.count * (trade.demand ?? 0)) * (trade.priceMultiplier ?? 0))));
      input1.count = Math.min(stackSize, Math.max(1, input1.count + demand + (trade.specialPrice ?? 0)));
    }
    return { input1, input2: fromSlot(trade.inputItem2), output: fromSlot(trade.outputItem),
      disabled: trade.tradeDisabled, uses: trade.nbTradeUses, maxUses: trade.maximumNbTradeUses };
  });
  const propertyState = (target) => {
    if (properties.has(target)) return properties.get(target);
    const kind = windowKind(target.type);
    const result = {};
    if (['furnace', 'blast_furnace', 'smoker'].includes(kind)) {
      const ticks = (value) => Number.isFinite(value) && value >= 0 ? Math.round(value * 20) : null;
      const remaining = ticks(target.progressSeconds);
      result.furnace = { burnTime: ticks(target.fuelSeconds), fuelTime: target.totalFuel ?? null,
        cookTime: remaining !== null && target.totalProgress != null ? Math.max(0, target.totalProgress - remaining) : null,
        cookTimeTotal: target.totalProgress ?? null };
    }
    if (kind === 'brewing_stand') result.brewing = { brewTime: null, fuel: null };
    if (kind === 'anvil') result.anvil = { cost: null, name: null };
    if (kind === 'merchant') {
      const selected = target.trades?.indexOf(target.selectedTrade) ?? -1;
      result.merchant = { offers: offersOf(target.trades ?? []), selected: selected >= 0 ? selected : null,
        level: null, experience: null, regular: null, canRestock: null };
    }
    if (kind === 'enchanting_table') result.enchanting = {
      options: Array.from({ length: 3 }, (_, i) => {
        const option = target.enchantments?.[i];
        return { cost: option?.level > 0 ? option.level : null, enchant: option?.expected?.enchant >= 0 ? option.expected.enchant : null,
          level: option?.expected?.level > 0 ? option.expected.level : null };
      }), chosen: null,
    };
    properties.set(target, result);
    return result;
  };
  const windowSnapshot = () => ({ windowId: window.id, kind: virtualInventory ? 'inventory' : windowKind(window.type),
    title: text(window.title).slice(0, 256) || (virtualInventory ? 'Inventory' : 'Container'),
    slots: window.slots.map(itemData), ...propertyState(window) });
  const sync = () => {
    if (!window || disposed || status !== 'online') return;
    window.slots.forEach((item, index) => {
      const next = itemData(item);
      const serialized = JSON.stringify(next);
      if (serialized !== slots[index]) {
        slots[index] = serialized;
        publish('slot', { windowId: window.id, slot: index, item: next });
      }
    });
    const next = itemData(window.selectedItem);
    const serialized = JSON.stringify(next);
    if (cursor !== serialized) { cursor = serialized; publish('cursor', { item: next }); }
    const data = propertyState(window);
    if (data.merchant && window.trades) {
      const offers = offersOf(window.trades);
      const selected = window.trades.indexOf(window.selectedTrade);
      const updated = { ...data.merchant, offers, selected: selected >= 0 ? selected : data.merchant.selected };
      if (JSON.stringify(updated) !== JSON.stringify(data.merchant)) {
        data.merchant = updated;
        publish('merchant', { windowId: window.id, ...updated });
      }
    }
    queueState();
  };
  const closeWindow = () => {
    pacingInventory = false;
    clearTimeout(pacingCloseTimer);
    if (!window) return;
    const id = window.id;
    windowCleanup.splice(0).reverse().forEach((remove) => remove());
    properties.delete(window);
    window = null;
    virtualInventory = false;
    cursor = null;
    slots = [];
    publish('window_close', { windowId: id });
  };
  const openWindow = (target, inventory = false) => {
    if (disposed || status !== 'online') return;
    if (window === target && virtualInventory === inventory) return;
    closeWindow();
    window = target;
    virtualInventory = inventory;
    slots = target.slots.map((item) => JSON.stringify(itemData(item)));
    cursor = JSON.stringify(itemData(target.selectedItem));
    listen(target, 'updateSlot', () => sync(), windowCleanup);
    // acceptClick can mutate item counts after updateSlot; observe completion as well.
    replace(target, 'acceptClick', (original) => function (...args) {
      try { return original.apply(this, args); } finally { sync(); }
    }, windowCleanup);
    publish('window_open', windowSnapshot());
    publish('cursor', { item: itemData(target.selectedItem) });
    queueState();
  };
  const hello = () => ({ protocol: 1, bot: bot.username, mcVersion: bot.version ?? '1.20.1', agentVersion: 'curio-mineflayer/0.1.0' });
  const setStatus = (next) => {
    if (next !== 'online') closeWindow();
    status = next;
    if (next === 'online') publish('hello', hello());
    publish('bot_status', { status });
    if (next === 'online') { lastState = ''; queueState(); }
  };

  listen(bot, 'windowOpen', (target) => openWindow(target));
  listen(bot, 'windowClose', (target) => { if (!target || target === window) closeWindow(); });
  listen(bot.inventory, 'updateSlot', queueState);
  ['health', 'experience', 'heldItemChanged', 'breath'].forEach((event) => listen(bot, event, queueState));
  listen(bot, 'entityUpdate', (entity) => { if (entity === bot.entity) queueState(); });
  listen(bot, 'spawn', () => {
    closeWindow();
    setStatus('online');
    if (bot.currentWindow) openWindow(bot.currentWindow);
  });
  listen(bot, 'death', () => setStatus('dead'));
  ['end', 'kicked'].forEach((event) => listen(bot, event, () => setStatus('offline')));

  if (bot._client) {
    ['set_slot', 'window_items'].forEach((event) => listen(bot._client, event, () => queueMicrotask(() => { sync(); queueState(); })));
    const property = ({ windowId, property: id, value }) => {
      const target = bot.currentWindow;
      if (!target || target.id !== windowId || !Number.isInteger(id) || !Number.isInteger(value)) return;
      const data = propertyState(target);
      let event;
      if (data.furnace && id >= 0 && id <= 3 && value >= 0) {
        data.furnace[['burnTime', 'fuelTime', 'cookTime', 'cookTimeTotal'][id]] = value;
        event = 'furnace';
      } else if (data.brewing && (id === 0 || id === 1) && value >= 0) {
        data.brewing[id === 0 ? 'brewTime' : 'fuel'] = id === 1 ? Math.min(20, value) : value;
        event = 'brewing';
      } else if (data.anvil && id === 0) {
        data.anvil.cost = value > 0 ? value : null;
        event = 'anvil';
      } else if (data.enchanting && id >= 0 && id <= 9 && id !== 3) {
        const index = id < 3 ? id : id < 7 ? id - 4 : id - 7;
        const key = id < 3 ? 'cost' : id < 7 ? 'enchant' : 'level';
        data.enchanting.options[index][key] = value >= (key === 'enchant' ? 0 : 1) ? value : null;
        if (id === 0) data.enchanting.chosen = null;
        event = 'enchanting';
      }
      if (event && window === target) publish(event, { windowId, ...data[event] });
    };
    ['craft_progress_bar', 'window_property'].forEach((event) => listen(bot._client, event, property));
    listen(bot._client, 'trade_list', (packet) => {
      const target = bot.currentWindow;
      if (!target || target.id !== packet.windowId || windowKind(target.type) !== 'merchant') return;
      const data = propertyState(target);
      const offers = offersOf(packet.trades);
      const selected = data.merchant.selected;
      data.merchant = { offers, selected: selected !== null && selected < offers.length ? selected : null,
        level: packet.villagerLevel, experience: packet.experience, regular: packet.isRegularVillager, canRestock: packet.canRestock };
      if (window === target) publish('merchant', { windowId: target.id, ...data.merchant });
    });
    replace(bot._client, 'write', (original) => function (name, params, ...args) {
      const result = original.call(this, name, params, ...args);
      const target = bot.currentWindow;
      if (target && target === window) {
        const data = propertyState(target);
        if (name === 'select_trade' && data.merchant && Number.isInteger(params.slot) && params.slot >= 0 && params.slot < data.merchant.offers.length) {
          data.merchant.selected = params.slot;
          publish('merchant', { windowId: target.id, ...data.merchant });
        } else if (name === 'name_item' && data.anvil) {
          data.anvil.name = String(params.name ?? '').slice(0, 50) || null;
          publish('anvil', { windowId: target.id, ...data.anvil });
        } else if (name === 'enchant_item' && data.enchanting && params.windowId === target.id
          && Number.isInteger(params.enchantment) && params.enchantment >= 0 && params.enchantment < 3) {
          data.enchanting.chosen = params.enchantment;
          publish('enchanting', { windowId: target.id, ...data.enchanting });
        }
      }
      return result;
    });
  }

  // The player's 2x2 inventory is always present; bot.craft does not emit windowOpen for it.
  replace(bot, 'craft', (original) => async function (...args) {
    const inventory = args[2] == null;
    crafting++;
    if (inventory) openWindow(bot.inventory, true);
    try { return await original.apply(this, args); }
    finally {
      crafting--;
      sync();
      if (crafting === 0 && virtualInventory) closeWindow();
    }
  });
  // Mineflayer increments trade uses/disabled locally after completion, sometimes
  // without another trade_list packet. Publish those final values as well.
  replace(bot, 'trade', (original) => async function (...args) {
    try { return await original.apply(this, args); } finally { sync(); }
  });
  if (bot.currentWindow) openWindow(bot.currentWindow);

  const heartbeat = setInterval(() => clients.forEach((response) => write(response, ': heartbeat\n\n')),
    Math.max(1_000, Math.min(15_000, options.heartbeatMs ?? 15_000)));
  heartbeat.unref();

  const observer = {
    /** Mount this handler in an existing HTTP server. Returns false for unrelated routes. */
    handler(request, response) {
      if (request.url?.split('?', 1)[0] !== STREAM_PATH) return false;
      if (disposed) { response.writeHead(503).end(); return true; }
      if (request.method !== 'GET') { response.writeHead(405, { Allow: 'GET' }).end(); return true; }
      response.writeHead(200, { 'Content-Type': 'text/event-stream; charset=utf-8', 'Cache-Control': 'no-cache, no-transform',
        Connection: 'keep-alive', 'X-Accel-Buffering': 'no' });
      clients.add(response);
      response.on('close', () => clients.delete(response));
      write(response, frame('hello', hello()));
      write(response, frame('bot_status', { status }));
      if (status === 'online') {
        write(response, frame('state', state()));
        if (window) {
          write(response, frame('window_open', windowSnapshot()));
          write(response, frame('cursor', { item: itemData(window.selectedItem) }));
        }
      }
      return true;
    },
    /** Start a dedicated HTTP server; port 0 is useful in tests. */
    async listen({ port = 3000, host = '127.0.0.1' } = {}) {
      if (disposed) throw new Error('Observer has been disposed');
      const server = http.createServer((request, response) => {
        if (!observer.handler(request, response)) response.writeHead(404).end();
      });
      await new Promise((resolve, reject) => {
        server.once('error', reject);
        server.listen(port, host, () => { server.removeListener('error', reject); resolve(); });
      });
      servers.add(server);
      server.on('close', () => servers.delete(server));
      return server;
    },
    /** Custom inventory/crafting adapters can explicitly bracket their 2x2 operations. */
    openInventory() { pacingInventory = false; clearTimeout(pacingCloseTimer); openWindow(bot.inventory, true); },
    closeInventory() { if (virtualInventory) closeWindow(); },
    /** Stop clicks still awaiting their pacing delay; an already sent server action cannot be undone. */
    cancelPendingActions() { pacing?.cancelPending(); },
    async dispose() {
      if (disposed) return;
      closeWindow();
      publish('bot_status', { status: 'offline' });
      disposed = true;
      clearTimeout(stateTimer);
      clearInterval(heartbeat);
      cleanup.splice(0).reverse().forEach((remove) => remove());
      clients.forEach((response) => response.end());
      clients.clear();
      installations.delete(bot);
      await Promise.all([...servers].map((server) => new Promise((resolve) => {
        server.close(resolve);
        server.closeIdleConnections();
      })));
    },
  };
  installations.set(bot, observer);
  return observer;
}
