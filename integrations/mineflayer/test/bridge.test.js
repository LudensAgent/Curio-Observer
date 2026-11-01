import test from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import http from 'node:http';
import { setTimeout as delay } from 'node:timers/promises';
import { installMineflayerObserver } from '../index.js';
import { itemData, snbt } from '../items.js';

const bookNbt = { type: 'compound', value: {
  StoredEnchantments: { type: 'list', value: { type: 'compound', value: [
    { id: { type: 'string', value: 'minecraft:mending' }, lvl: { type: 'short', value: 1 } },
  ] } },
  display: { type: 'compound', value: { Name: { type: 'string', value: '{"text":"修补之书"}' } } },
} };
const item = (name, count = 1) => ({ name, count });
function window(id, type, count) {
  return Object.assign(new EventEmitter(), { id, type, title: { text: type },
    slots: Array(count).fill(null), inventoryStart: count - 36, selectedItem: null,
    acceptClick() {},
  });
}
function bot() {
  return Object.assign(new EventEmitter(), {
    username: 'Curio', version: '1.20.1', entity: { metadata: [] }, health: 20, food: 20,
    foodSaturation: 5, oxygenLevel: 20, experience: { level: 12, progress: 0.5 }, quickBarSlot: 0,
    inventory: window(0, 'minecraft:inventory', 46), currentWindow: null,
    _client: Object.assign(new EventEmitter(), { write() {} }),
    registry: { items: { 1: { name: 'emerald', stackSize: 64 }, 2: { name: 'enchanted_book', stackSize: 1 } } },
    async craft() {},
  });
}
async function connect(server) {
  const events = [];
  let text = '';
  const request = http.get({ host: '127.0.0.1', port: server.address().port, path: '/api/observer/stream' });
  const response = await new Promise((resolve, reject) => { request.on('response', resolve); request.on('error', reject); });
  response.setEncoding('utf8');
  response.on('data', (chunk) => {
    text += chunk;
    let end;
    while ((end = text.indexOf('\n\n')) >= 0) {
      const value = text.slice(0, end);
      text = text.slice(end + 2);
      if (value.startsWith(':')) continue;
      const lines = value.split('\n');
      events.push({ event: lines[0].slice(7), data: JSON.parse(lines[1].slice(6)) });
    }
  });
  const until = async (predicate) => {
    for (let i = 0; i < 200; i++) {
      const found = events.find(predicate);
      if (found) return found.data;
      await delay(5);
    }
    assert.fail(`Expected event did not arrive. Received: ${JSON.stringify(events)}`);
  };
  await until((event) => event.event === 'hello');
  return { events, until, close: () => request.destroy() };
}
async function setup(t, source = bot(), options = {}) {
  const observer = installMineflayerObserver(source, { stateIntervalMs: 50, ...options });
  const server = await observer.listen({ port: 0 });
  const stream = await connect(server);
  t.after(async () => { stream.close(); await observer.dispose(); });
  return { source, observer, server, ...stream };
}

test('optional action pacing emits cursor approach and exact batch transfer through real SSE', async (t) => {
  const source = bot();
  source.inventory.slots[9] = item('stone', 3);
  source.clickWindow = async (slot) => {
    const target = source.inventory;
    if (slot === -999) target.selectedItem = null;
    else { target.selectedItem = target.slots[slot]; target.slots[slot] = null; target.emit('updateSlot', slot); }
  };
  const original = source.clickWindow;
  const stream = await setup(t, source, { actionPacing: { intervalMs: 5 } });
  await source.clickWindow(9, 0, 0);
  await source.clickWindow(-999, 0, 0);
  const drop = await stream.until(({ event, data }) => event === 'inventory_transfer' && data.kind === 'drop');
  assert.equal(drop.item.count, 3);
  assert.equal(drop.slot, -999);
  const pointers = stream.events.filter(({ event }) => event === 'pointer');
  assert.equal(pointers[0].data.item, null);
  assert.equal(pointers[1].data.item.count, 3);
  assert.equal(pointers[1].data.durationMs, 5);
  assert.ok(stream.events.find(({ event, data }) => event === 'window_open' && data.kind === 'inventory'));
  await stream.observer.dispose();
  assert.equal(source.clickWindow, original);
});

test('SNBT preserves numeric types, signed arrays, long precision and literal strings', () => {
  const value = snbt({ type: 'compound', value: {
    byte: { type: 'byte', value: -1 }, short: { type: 'short', value: 2 }, int: { type: 'int', value: 3 },
    long: { type: 'long', value: [0x12345678, -1] }, big: { type: 'long', value: -9223372036854775808n },
    float: { type: 'float', value: 0.5 }, double: { type: 'double', value: 0.25 },
    bytes: { type: 'byteArray', value: Buffer.from([255, 127]) }, ints: { type: 'intArray', value: [1, -2] },
    longs: { type: 'longArray', value: [[-1, -1], 2n] },
    name: { type: 'string', value: 'a"b\\c\n中文' },
  } });
  assert.equal(value, '{"byte":-1b,"short":2s,"int":3,"long":1311768469162688511L,"big":-9223372036854775808L,"float":0.5f,"double":0.25d,"bytes":[B;-1b,127b],"ints":[I;1,-2],"longs":[L;-1L,2L],"name":"a\\"b\\\\c\n中文"}');
  assert.match(itemData({ name: 'enchanted_book', count: 1, nbt: bookNbt }).nbt, /"lvl":1s/);
  assert.equal(itemData({ name: 'enchanted_book', count: 1, nbt: bookNbt }).name, '修补之书');
});

test('a real SSE connection receives identity, vitals, open container, cursor, changes and close', async (t) => {
  const { source, until } = await setup(t);
  const initial = await until((event) => event.event === 'state');
  assert.equal(initial.hotbar.length, 9);
  assert.equal(initial.main.length, 27);
  const chest = window(1, 'minecraft:generic_9x3', 63);
  chest.slots[0] = item('diamond', 8);
  chest.selectedItem = item('emerald', 2);
  source.currentWindow = chest;
  source.emit('windowOpen', chest);
  const open = await until((event) => event.event === 'window_open');
  assert.equal(open.kind, 'chest');
  assert.equal(open.slots[0].count, 8);
  assert.equal((await until((event) => event.event === 'cursor')).item.count, 2);
  chest.slots[0] = null;
  chest.selectedItem.count = 10;
  chest.emit('updateSlot', 0);
  assert.equal((await until((event) => event.event === 'slot')).item, null);
  await until((event) => event.event === 'cursor' && event.data.item.count === 10);
  source.currentWindow = null;
  source.emit('windowClose', chest);
  assert.equal((await until((event) => event.event === 'window_close')).windowId, 1);
});

test('reconnect replays current slots, cursor and early workstation properties', async (t) => {
  const { source, server } = await setup(t);
  const furnace = window(2, 'minecraft:furnace', 39);
  source.currentWindow = furnace;
  source._client.emit('craft_progress_bar', { windowId: 2, property: 3, value: 200 });
  source._client.emit('craft_progress_bar', { windowId: 2, property: 2, value: 31 });
  source.emit('windowOpen', furnace);
  furnace.slots[0] = item('iron_ore', 4);
  furnace.selectedItem = item('coal');
  const reconnect = await connect(server);
  t.after(reconnect.close);
  const open = await reconnect.until((event) => event.event === 'window_open');
  assert.equal(open.furnace.cookTimeTotal, 200);
  assert.equal(open.furnace.cookTime, 31);
  assert.equal(open.slots[0].id, 'minecraft:iron_ore');
  assert.equal((await reconnect.until((event) => event.event === 'cursor')).item.id, 'minecraft:coal');
});

test('villager offers preserve enchanted-book NBT, actual prices and outgoing selection', async (t) => {
  const { source, until } = await setup(t);
  const merchant = window(3, 'minecraft:merchant', 39);
  source.currentWindow = merchant;
  source.emit('windowOpen', merchant);
  source._client.emit('trade_list', {
    windowId: 3, villagerLevel: 2, experience: 25, isRegularVillager: true, canRestock: true,
    trades: [{ inputItem1: { present: true, itemId: 1, itemCount: 10 }, inputItem2: { present: false },
      outputItem: { present: true, itemId: 2, itemCount: 1, nbtData: bookNbt },
      tradeDisabled: false, nbTradeUses: 1, maximumNbTradeUses: 12, demand: 2, priceMultiplier: 0.2, specialPrice: -1 }],
  });
  const trade = await until((event) => event.event === 'merchant' && event.data.offers.length === 1);
  assert.equal(trade.offers[0].input1.count, 13);
  assert.equal(trade.offers[0].output.name, '修补之书');
  assert.match(trade.offers[0].output.nbt, /minecraft:mending/);
  source._client.write('select_trade', { slot: 0 });
  assert.equal((await until((event) => event.event === 'merchant' && event.data.selected === 0)).selected, 0);
});

test('grindstone, anvil and enchanting use exact server slots and properties', async (t) => {
  const { source, until } = await setup(t);
  const grindstone = window(4, 'minecraft:grindstone', 39);
  grindstone.slots[0] = { name: 'diamond_sword', count: 1, damage: 10 };
  grindstone.slots[2] = { name: 'diamond_sword', count: 1, damage: 2 };
  source.currentWindow = grindstone;
  source.emit('windowOpen', grindstone);
  const open = await until((event) => event.event === 'window_open' && event.data.kind === 'grindstone');
  assert.equal(open.slots[2].damage, 2);
  const anvil = window(5, 'minecraft:anvil', 39);
  source.currentWindow = anvil;
  source.emit('windowOpen', anvil);
  source._client.emit('craft_progress_bar', { windowId: 5, property: 0, value: 7 });
  source._client.write('name_item', { name: 'My sword' });
  const named = await until((event) => event.event === 'anvil' && event.data.name === 'My sword');
  assert.equal(named.cost, 7);
  const enchanting = window(6, 'minecraft:enchantment', 38);
  source.currentWindow = enchanting;
  source.emit('windowOpen', enchanting);
  source._client.emit('craft_progress_bar', { windowId: 6, property: 0, value: 5 });
  source._client.emit('craft_progress_bar', { windowId: 6, property: 4, value: 17 });
  source._client.emit('craft_progress_bar', { windowId: 6, property: 7, value: 2 });
  source._client.write('enchant_item', { windowId: 6, enchantment: 0 });
  const chosen = await until((event) => event.event === 'enchanting' && event.data.chosen === 0);
  assert.deepEqual(chosen.options[0], { cost: 5, enchant: 17, level: 2 });
});

test('2x2 crafting is bracketed without delays and original method behavior is preserved', async (t) => {
  const source = bot();
  source.craft = async function (recipe, count) {
    assert.equal(this, source);
    this.inventory.slots[1] = item('oak_log');
    this.inventory.emit('updateSlot', 1);
    this.inventory.slots[0] = item('oak_planks', count);
    this.inventory.acceptClick();
    return recipe;
  };
  const original = source.craft;
  const { observer, until } = await setup(t, source);
  assert.equal(await source.craft('recipe', 4), 'recipe');
  const open = await until((event) => event.event === 'window_open');
  assert.equal(open.kind, 'inventory');
  assert.equal((await until((event) => event.event === 'slot' && event.data.slot === 0)).item.count, 4);
  await until((event) => event.event === 'window_close');
  await observer.dispose();
  assert.equal(source.craft, original);
});

test('disconnect clears windows and dispose releases listeners and wrapped methods', async (t) => {
  const source = bot();
  const originalWrite = source._client.write;
  const { observer, until } = await setup(t, source);
  source.currentWindow = window(7, 'minecraft:hopper', 41);
  const originalClick = source.currentWindow.acceptClick;
  source.emit('windowOpen', source.currentWindow);
  source.emit('end');
  await until((event) => event.event === 'window_close');
  await until((event) => event.event === 'bot_status' && event.data.status === 'offline');
  await observer.dispose();
  assert.equal(source._client.write, originalWrite);
  assert.equal(source.currentWindow.acceptClick, originalClick);
  assert.equal(source.listenerCount('windowOpen'), 0);
  assert.equal(source._client.listenerCount('trade_list'), 0);
  assert.equal(source.inventory.listenerCount('updateSlot'), 0);
});

test('inventory state uses the Minecraft 1.20 smithing player-slot offset', async (t) => {
  const source = bot();
  source.currentWindow = window(8, 'minecraft:smithing', 40);
  source.currentWindow.inventoryStart = 3; // Legacy prismarine-windows metadata.
  source.currentWindow.slots[4] = item('diamond');
  source.currentWindow.slots[31] = item('netherite_ingot');
  const { until } = await setup(t, source);
  const state = await until((event) => event.event === 'state');
  assert.equal(state.main[0].id, 'minecraft:diamond');
  assert.equal(state.hotbar[0].id, 'minecraft:netherite_ingot');
});

test('NBT-heavy initial snapshots stay below the JSON budget without losing slots or counts', async (t) => {
  const source = bot();
  const chest = window(9, 'minecraft:generic_9x6', 90);
  const nbt = { type: 'compound', value: { text: { type: 'string', value: 'x'.repeat(60_000) } } };
  chest.slots = Array.from({ length: 90 }, (_, i) => ({ name: 'written_book', count: i % 16 + 1, nbt }));
  source.currentWindow = chest;
  const { until } = await setup(t, source);
  const open = await until((event) => event.event === 'window_open');
  assert.ok(JSON.stringify(open).length <= 262_144);
  assert.equal(open.slots.length, 90);
  assert.equal(open.slots[70].count, 7);
  assert.equal(open.slots[70].nbt, null);
  const state = await until((event) => event.event === 'state');
  assert.ok(JSON.stringify(state).length <= 262_144);
  assert.equal(state.main.length, 27);
});

test('already-open merchant replays offers and trade completion updates uses without a new packet', async (t) => {
  const source = bot();
  const merchant = window(10, 'minecraft:merchant', 39);
  const trade = { inputItem1: item('emerald', 8), inputItem2: null,
    outputItem: { name: 'enchanted_book', count: 1, nbt: bookNbt },
    tradeDisabled: false, nbTradeUses: 11, maximumNbTradeUses: 12 };
  merchant.trades = [trade];
  merchant.selectedTrade = trade;
  source.currentWindow = merchant;
  source.trade = async () => { trade.nbTradeUses++; trade.tradeDisabled = true; };
  const { until } = await setup(t, source);
  const open = await until((event) => event.event === 'window_open');
  assert.equal(open.merchant.selected, 0);
  assert.match(open.merchant.offers[0].output.nbt, /minecraft:mending/);
  await source.trade(merchant, 0, 1);
  const updated = await until((event) => event.event === 'merchant' && event.data.offers[0].disabled);
  assert.equal(updated.offers[0].uses, 12);
});

test('respawn sends a new window snapshot after its hello instead of orphan slot updates', async (t) => {
  const { source, events, until } = await setup(t);
  const chest = window(11, 'minecraft:generic_9x3', 63);
  source.currentWindow = chest;
  source.emit('windowOpen', chest);
  await until((event) => event.event === 'window_open');
  source.emit('spawn');
  await until((event) => event.event === 'window_close');
  await delay(20);
  const hellos = events.flatMap((event, i) => event.event === 'hello' ? [i] : []);
  assert.equal(hellos.length, 2);
  assert.ok(events.slice(hellos[1] + 1).some((event) => event.event === 'window_open'));
});
