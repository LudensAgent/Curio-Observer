import test from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { createRequire } from 'node:module';
import { setTimeout as delay } from 'node:timers/promises';
import { installMineflayerActionPacing } from '../../mineflayer/index.js';

const require = createRequire(import.meta.url);
const registry = require('prismarine-registry')('1.20.1');
const Item = require('prismarine-item')(registry);
const windows = require('prismarine-windows')(registry);
const injectInventory = require('mineflayer/lib/plugins/inventory');
const injectSimpleInventory = require('mineflayer/lib/plugins/simple_inventory');
const stone = registry.itemsByName.stone.id;

// Real pinned Mineflayer inventory and Prismarine window implementations, with a
// recording transport. No Minecraft connection or substitute click algorithm.
function fixture(t, intervalMs = 0) {
  const packets = [];
  const events = [];
  const bot = Object.assign(new EventEmitter(), { username: 'Paced', version: '1.20.1', registry,
    supportFeature: (name) => registry.supportFeature(name), QUICK_BAR_START: 36,
    _client: Object.assign(new EventEmitter(), { write(name, data) { packets.push({ name, data, time: performance.now() }); } }),
  });
  injectInventory(bot, { hideErrors: true });
  injectSimpleInventory(bot);
  const original = bot.clickWindow;
  const pacing = installMineflayerActionPacing(bot, { intervalMs,
    onPointer: (data) => events.push({ event: 'pointer', data }),
    onTransfer: (data) => events.push({ event: 'inventory_transfer', data }),
  });
  t.after(() => pacing.dispose());
  return { bot, packets, events, pacing, original };
}

test('real clicks are serial and delayed before packets; pointers use the true cursor', async (t) => {
  const { bot, packets, events } = fixture(t, 25);
  bot.inventory.updateSlot(9, new Item(stone, 16));
  const started = performance.now();
  await Promise.all([bot.clickWindow(9, 0, 0), bot.clickWindow(10, 0, 0)]);
  const clicks = packets.filter((packet) => packet.name === 'window_click');
  assert.equal(clicks.length, 2);
  assert.ok(clicks[0].time - started >= 20);
  assert.ok(clicks[1].time - clicks[0].time >= 20);
  assert.equal(bot.inventory.slots[10].count, 16);
  assert.equal(events[0].data.item, null);
  assert.equal(events[2].data.item.count, 16);
  assert.deepEqual(events.filter((event) => event.event === 'inventory_transfer').map(({ data }) => [data.kind, data.item.count]), [['take', 16], ['put', 16]]);
});

test('transfer distinguishes one-item placement and whole-stack placement, preserving counts', async (t) => {
  const { bot, packets, events } = fixture(t);
  const chest = windows.createWindow(1, 'minecraft:generic_9x3', 'Chest');
  bot.currentWindow = chest;
  chest.updateSlot(27, new Item(stone, 64));
  await bot.transfer({ window: chest, itemType: stone, count: 1, sourceStart: 27, sourceEnd: 28, destStart: 0, destEnd: 1 });
  assert.equal(chest.slots[0].count, 1);
  assert.equal(chest.slots[27].count, 63);
  assert.equal(chest.selectedItem, null);
  assert.ok(packets.some(({ data }) => data.slot === 0 && data.mouseButton === 1));
  const before = packets.length;
  await bot.transfer({ window: chest, itemType: stone, count: 63, sourceStart: 27, sourceEnd: 28, destStart: 0, destEnd: 1 });
  assert.equal(chest.slots[0].count, 64);
  assert.equal(packets.slice(before).filter(({ name }) => name === 'window_click').length, 2);
  assert.deepEqual(events.filter(({ event, data }) => event === 'inventory_transfer' && data.slot === 0).map(({ data }) => data.item.count), [1, 63]);
});

test('public toss(3) sends exactly one real batch drop and keeps the other 61 items', async (t) => {
  const { bot, packets, events } = fixture(t);
  bot.inventory.updateSlot(9, new Item(stone, 64));
  await bot.toss(stone, null, 3);
  const outside = packets.filter(({ name, data }) => name === 'window_click' && data.slot === -999);
  assert.equal(outside.length, 1);
  assert.equal(outside[0].data.mouseButton, 0);
  assert.equal(bot.inventory.count(stone), 61);
  assert.equal(bot.inventory.selectedItem, null);
  assert.deepEqual(events.filter(({ event, data }) => event === 'inventory_transfer' && data.kind === 'drop').map(({ data }) => data.item.count), [3]);
});

test('batch toss without a spare slot rejects before any click or partial drop', async (t) => {
  const { bot, packets } = fixture(t);
  for (let slot = 9; slot < 45; slot++) bot.inventory.updateSlot(slot, new Item(stone, 64));
  await assert.rejects(bot.toss(stone, null, 3), /empty inventory slot/);
  assert.equal(packets.length, 0);
  assert.equal(bot.inventory.count(stone), 36 * 64);
});

test('returned window deposit/withdraw and putAway route through paced clicks', async (t) => {
  const { bot, packets, events } = fixture(t);
  const chest = windows.createWindow(1, 'minecraft:generic_9x3', 'Chest');
  chest.updateSlot(27, new Item(stone, 8));
  bot.activateBlock = () => queueMicrotask(() => { bot.currentWindow = chest; bot.emit('windowOpen', chest); });
  const opened = await bot.openBlock({});
  await opened.deposit(stone, null, 8);
  assert.equal(chest.slots[0].count, 8);
  await opened.withdraw(stone, null, 3);
  assert.equal(chest.slots[0].count, 5);
  await bot.putAway(0);
  assert.equal(chest.slots[0], null);
  assert.equal(chest.count(stone), 8);
  assert.equal(events.filter(({ event }) => event === 'pointer').length, packets.filter(({ name }) => name === 'window_click').length);
});

test('stop or a changed window cancels a delayed click without writing a packet', async (t) => {
  const { bot, packets, pacing } = fixture(t, 40);
  bot.inventory.updateSlot(9, new Item(stone, 4));
  const first = bot.clickWindow(9, 0, 0);
  const rejected = assert.rejects(first, /abort|cancel/i);
  await delay(5);
  pacing.cancelPending();
  await rejected;
  assert.equal(packets.length, 0);
  const second = bot.clickWindow(9, 0, 0);
  const changed = assert.rejects(second, /window changed/);
  await delay(5);
  bot.currentWindow = windows.createWindow(2, 'minecraft:generic_9x3', 'Other');
  await changed;
  assert.equal(packets.length, 0);
});

test('dispose restores original functions and does not leave per-window wrappers installed', async (t) => {
  const { bot, pacing, original } = fixture(t);
  assert.notEqual(bot.clickWindow, original);
  pacing.dispose();
  assert.equal(bot.clickWindow, original);
  assert.equal(bot.listenerCount('windowClose'), 0);
  const again = installMineflayerActionPacing(bot, { intervalMs: 0 });
  again.dispose();
});
