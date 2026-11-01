import test from 'node:test';
import assert from 'node:assert/strict';
import { Vec3 } from 'vec3';
import mineflayer from 'mineflayer';
import { createRequire } from 'node:module';
import { EventEmitter } from 'node:events';
import { arenaPlan, ARENA_STATIONS, prepareArena } from '../prepare.js';
import { SCENARIOS } from '../scenarios.js';

const require = createRequire(import.meta.url);
const registry = require('prismarine-registry')('1.20.1');
const windows = require('prismarine-windows')(registry);
const Item = require('prismarine-item')(registry);
const item = (name, slot, count = 1) => Object.assign(new Item(registry.itemsByName[name].id, count), { slot });
const context = () => ({ signal: new AbortController().signal, dwellMs: 0, wait: async () => {}, log() {} });

test('approved arena plan confines every block edit to 24×24 and preserves existing blocks', () => {
  const plan = arenaPlan({ x: 1000, y: 200, z: 1000 }, 'Alex');
  assert.deepEqual(plan.bounds, { minX: 988, maxX: 1011, floorY: 200, minZ: 988, maxZ: 1011 });
  assert.equal(ARENA_STATIONS.length, 20);
  const fills = plan.commands.filter((command) => command.startsWith('fill '));
  assert.deepEqual(fills, ['fill 988 200 988 1011 200 1011 minecraft:stone replace minecraft:air']);
  const blocks = plan.commands.filter((command) => command.startsWith('setblock '));
  for (const command of blocks) {
    const [, x, y, z, , mode] = command.split(' ');
    assert.ok(Number(x) >= 988 && Number(x) <= 1011);
    assert.ok(Number(z) >= 988 && Number(z) <= 1011);
    assert.ok(Number(y) === 200 || Number(y) === 201);
    assert.equal(mode, 'keep');
  }
  assert.ok(plan.commands.every((command) => !/^(clear|kill|gamerule|time|weather)\b/.test(command)));
  assert.ok(plan.commands.every((command) => ('execute in minecraft:overworld run ' + command).length <= 256));
  assert.ok(plan.commands.filter((command) => command.startsWith('data ')).every((command) => command.includes(`tag=${plan.tag}`)));
  for (const command of plan.commands.filter((command) => command.startsWith('give '))) {
    const name = command.split(' ')[2].split('{')[0].replace('minecraft:', '');
    assert.ok(registry.itemsByName[name], name);
  }
});

test('preparation requires explicit valid coordinates and a safe actual player name', () => {
  assert.throws(() => arenaPlan(undefined, 'Alex'), /explicit/);
  assert.throws(() => arenaPlan({ x: 1000, y: 400, z: 1000 }, 'Alex'), /limits/);
  assert.throws(() => arenaPlan({ x: 1000, y: 200, z: 1000 }, '@a'), /username/);
  assert.equal(typeof mineflayer.createBot, 'function'); // imports do not create a connection
});

test('preparation cancellation restores the original position and mode before any block edit', async () => {
  const controller = new AbortController();
  const commands = [];
  let flying = false;
  const bot = {
    username: 'Alex', game: { gameMode: 'survival', dimension: 'overworld' },
    entity: { position: new Vec3(10, 70, 20) },
    creative: { startFlying() { flying = true; }, stopFlying() { flying = false; } },
    chat(command) {
      commands.push(command);
      if (command === '/gamemode creative Alex') this.game.gameMode = 'creative';
      if (command.includes('1000.5 203 1000.5')) controller.abort(new Error('operator stopped'));
    },
  };
  await assert.rejects(prepareArena(bot, { origin: { x: 1000, y: 200, z: 1000 }, signal: controller.signal, log() {} }), /abort/i);
  assert.equal(flying, false);
  assert.deepEqual(commands.slice(-2), ['/execute in minecraft:overworld run teleport Alex 10 70 20', '/gamemode survival Alex']);
  assert.ok(commands.every((command) => !command.includes('setblock') && !command.includes(' fill ')));
});

test('missing prerequisites skip before opening any window', async () => {
  const bot = { inventory: windows.createWindow(0, 'minecraft:inventory', 'Inventory') };
  const result = await SCENARIOS.find(({ id }) => id === 'smithing').run(bot, context());
  assert.equal(result.status, 'skipped');
  assert.match(result.detail, /netherite_upgrade_smithing_template/);
});

test('container uses one empty slot and returns the same item without touching existing contents', async () => {
  const inventory = windows.createWindow(0, 'minecraft:inventory', 'Inventory');
  inventory.updateSlot(9, item('stone', 9, 8));
  const window = windows.createWindow(1, 'minecraft:generic_9x3', 'Chest');
  window.updateSlot(0, item('diamond', 0, 5));
  window.updateSlot(27, item('stone', 27, 8));
  const calls = [];
  const bot = {
    inventory, registry, entity: { position: new Vec3(0, 1, 0) },
    findBlock: () => ({ name: 'chest', position: new Vec3(1, 1, 0) }),
    async openBlock() { this.currentWindow = window; return window; },
    async transfer(options) { calls.push(options); window.updateSlot(options.destStart, item('stone', options.destStart)); },
    async putAway(slot) { calls.push({ take: slot }); window.updateSlot(slot, null); },
  };
  window.close = async () => { calls.push({ close: true }); bot.currentWindow = null; };
  const result = await SCENARIOS.find(({ id }) => id === 'chest').run(bot, context());
  assert.equal(result.status, 'passed');
  assert.equal(calls[0].destStart, 1);
  assert.equal(calls[0].count, 1);
  assert.equal(calls[0].sourceStart, 27);
  assert.deepEqual(calls.slice(1), [{ take: 1 }, { close: true }]);
  assert.equal(window.slots[0].name, 'diamond');
  assert.equal(window.slots[0].count, 5);
});

test('1.20 smithing template layout is corrected before real transfer APIs are called', async () => {
  const inventory = windows.createWindow(0, 'minecraft:inventory', 'Inventory');
  const names = ['netherite_upgrade_smithing_template', 'diamond_boots', 'netherite_ingot'];
  names.forEach((name, index) => inventory.updateSlot(index + 9, item(name, index + 9)));
  const window = windows.createWindow(1, 'minecraft:smithing', 'Smithing');
  // Actual 1.20 server window_items includes 40 slots even though upstream metadata says 39.
  window.slots = new Array(40).fill(null);
  names.forEach((name, index) => window.updateSlot(index + 4, item(name, index + 4)));
  const destinations = [];
  const bot = {
    inventory, registry, entity: { position: new Vec3(0, 1, 0) },
    findBlock: () => ({ name: 'smithing_table', position: new Vec3(1, 1, 0) }),
    async openBlock() { this.currentWindow = window; return window; },
    async transfer(options) {
      assert.equal(options.sourceStart, 4);
      assert.equal(options.sourceEnd, 40);
      assert.equal(window.craftingResultSlot, 3);
      destinations.push(options.destStart);
      window.updateSlot(options.destStart, item(names[options.destStart], options.destStart));
      if (destinations.length === 3) window.updateSlot(3, item('netherite_boots', 3));
    },
    async putAway(slot) { assert.equal(slot, 3); window.updateSlot(slot, null); },
  };
  window.close = async () => { bot.currentWindow = null; };
  const result = await SCENARIOS.find(({ id }) => id === 'smithing').run(bot, context());
  assert.equal(result.status, 'passed');
  assert.deepEqual(destinations, [0, 1, 2]);
});

test('stopping during a visible container dwell closes its actual window', async () => {
  const inventory = windows.createWindow(0, 'minecraft:inventory', 'Inventory');
  inventory.updateSlot(9, item('stone', 9));
  const window = windows.createWindow(1, 'minecraft:generic_9x3', 'Chest');
  let closed = false;
  const bot = {
    inventory, registry, entity: { position: new Vec3(0, 1, 0) },
    findBlock: () => ({ name: 'chest', position: new Vec3(1, 1, 0) }),
    async openBlock() { this.currentWindow = window; return window; },
  };
  window.close = async () => { closed = true; bot.currentWindow = null; };
  const stopped = { ...context(), wait: async () => { throw new Error('operator stopped'); } };
  await assert.rejects(SCENARIOS.find(({ id }) => id === 'chest').run(bot, stopped), /operator stopped/);
  assert.equal(closed, true);
});

test('batch-drop scene isolates three, emits one tossStack, verifies server entity and recovers all items', async () => {
  const bot = new EventEmitter();
  bot.inventory = windows.createWindow(0, 'minecraft:inventory', 'Inventory');
  bot.inventory.updateSlot(9, item('stone', 9, 8));
  bot.game = { dimension: 'overworld' };
  bot.entity = { position: new Vec3(1000.5, 201, 995.5) };
  bot.entities = {};
  bot.blockAt = (position) => ({ boundingBox: position.y === 200 ? 'block' : 'empty' });
  bot.lookAt = async () => {};
  const calls = [];
  bot.transfer = async (options) => {
    calls.push(['split', options.count]);
    assert.equal(options.sourceStart, 9);
    assert.equal(options.sourceEnd, 10); // leftover must return to its source, not merge into the new batch
    bot.inventory.updateSlot(9, item('stone', 9, 5));
    bot.inventory.updateSlot(options.destStart, item('stone', options.destStart, options.count));
  };
  bot.tossStack = async (batch) => {
    calls.push(['tossStack', batch.count]);
    bot.inventory.updateSlot(batch.slot, null);
    const entity = { id: 77, name: 'item', position: new Vec3(1002.5, 201, 995.5), getDroppedItem: () => item('stone', -1, 3) };
    bot.entities[77] = entity;
    bot.emit('itemDrop', entity);
  };
  bot.pathfinder = { setGoal(goal, dynamic) {
    if (!goal) return;
    assert.equal(dynamic, true);
    calls.push(['pickup', 3]);
    bot.inventory.updateSlot(9, item('stone', 9, 8));
    delete bot.entities[77];
  } };
  const result = await SCENARIOS.find(({ id }) => id === 'toss').run(bot, { ...context(), origin: { x: 1000, y: 200, z: 1000 } });
  assert.equal(result.status, 'passed');
  assert.deepEqual(calls, [['split', 3], ['tossStack', 3], ['pickup', 3]]);
  assert.equal(bot.inventory.items().filter((entry) => entry.name === 'stone').reduce((sum, entry) => sum + entry.count, 0), 8);
  assert.equal(bot.listenerCount('itemDrop'), 0);
});

test('batch-drop skips an obstructed recovery aisle without changing inventory', async () => {
  const inventory = windows.createWindow(0, 'minecraft:inventory', 'Inventory');
  inventory.updateSlot(9, item('stone', 9, 8));
  const bot = { inventory, game: { dimension: 'overworld' }, entity: { position: new Vec3(1000.5, 201, 995.5) },
    blockAt: () => ({ boundingBox: 'block' }) };
  const result = await SCENARIOS.find(({ id }) => id === 'toss').run(bot, { ...context(), origin: { x: 1000, y: 200, z: 1000 } });
  assert.equal(result.status, 'skipped');
  assert.match(result.detail, /clear/);
  assert.equal(inventory.slots[9].count, 8);
});

test('three single-item entities do not pass as one three-item batch', async () => {
  const bot = new EventEmitter();
  bot.inventory = windows.createWindow(0, 'minecraft:inventory', 'Inventory');
  bot.inventory.updateSlot(9, item('stone', 9, 8));
  bot.game = { dimension: 'overworld' };
  bot.entity = { position: new Vec3(1000.5, 201, 995.5) };
  bot.entities = {};
  bot.blockAt = (position) => ({ boundingBox: position.y === 200 ? 'block' : 'empty' });
  bot.lookAt = async () => {};
  bot.transfer = async (options) => {
    bot.inventory.updateSlot(9, item('stone', 9, 5));
    bot.inventory.updateSlot(options.destStart, item('stone', options.destStart, 3));
  };
  bot.tossStack = async (batch) => {
    bot.inventory.updateSlot(batch.slot, null);
    for (const id of [77, 78, 79]) bot.emit('itemDrop', {
      id, name: 'item', position: new Vec3(1002.5, 201, 995.5), getDroppedItem: () => item('stone', -1, 1),
    });
  };
  await assert.rejects(SCENARIOS.find(({ id }) => id === 'toss').run(bot, { ...context(), origin: { x: 1000, y: 200, z: 1000 } }), /one dropped entity/);
  assert.equal(bot.listenerCount('itemDrop'), 0);
});

test('villager scenario leaves selection to bot.trade so automatic input filling does not hide payment', async () => {
  const inventory = windows.createWindow(0, 'minecraft:inventory', 'Inventory');
  inventory.updateSlot(9, item('emerald', 9, 32));
  const entity = { id: 42, name: 'villager', position: new Vec3(1, 1, 0) };
  const trade = { inputs: [item('emerald', 0)], realPrice: 1, tradeDisabled: false, nbTradeUses: 0 };
  const window = { trades: [trade], close: async () => { bot.currentWindow = null; } };
  let traded = false;
  const bot = {
    inventory, entity: { position: new Vec3(0, 1, 0) }, nearestEntity: () => entity,
    _client: { write() { assert.fail('Do not send a direct select_trade before bot.trade'); } },
    async openVillager(target) { assert.equal(target, entity); this.currentWindow = window; return window; },
    async trade(target, index, count) {
      assert.equal(target, window);
      assert.equal(index, 0);
      assert.equal(count, 1);
      assert.equal(inventory.items().find((entry) => entry.name === 'emerald').count, 32);
      trade.nbTradeUses++;
      traded = true;
    },
  };
  const result = await SCENARIOS.find(({ id }) => id === 'villager').run(bot, context());
  assert.equal(result.status, 'passed');
  assert.equal(traded, true);
  assert.equal(bot.currentWindow, null);
});

test('paced drop scene exercises the public toss API with an exact count of three', async () => {
  const bot = new EventEmitter();
  bot.inventory = windows.createWindow(0, 'minecraft:inventory', 'Inventory');
  bot.inventory.updateSlot(9, item('stone', 9, 8));
  bot.game = { dimension: 'overworld' };
  bot.entity = { position: new Vec3(1000.5, 201, 995.5) };
  bot.entities = {};
  bot.blockAt = (position) => ({ boundingBox: position.y === 200 ? 'block' : 'empty' });
  bot.lookAt = async () => {};
  let count;
  bot.toss = async (type, metadata, amount) => {
    assert.equal(type, registry.itemsByName.stone.id);
    count = amount;
    bot.inventory.updateSlot(9, item('stone', 9, 8 - amount));
    bot.emit('itemDrop', { id: 77, name: 'item', position: new Vec3(1002.5, 201, 995.5), getDroppedItem: () => item('stone', -1, amount) });
  };
  bot.pathfinder = { setGoal(goal, dynamic) { if (goal) {
    assert.equal(dynamic, true);
    bot.inventory.updateSlot(9, item('stone', 9, 8));
  } } };
  const result = await SCENARIOS.find(({ id }) => id === 'toss').run(bot, { ...context(), actionPacing: true, origin: { x: 1000, y: 200, z: 1000 } });
  assert.equal(result.status, 'passed');
  assert.equal(count, 3);
});

test('drop recovery tracks the moving item entity and stops only after inventory confirms pickup', async () => {
  const bot = new EventEmitter();
  bot.inventory = windows.createWindow(0, 'minecraft:inventory', 'Inventory');
  bot.inventory.updateSlot(9, item('stone', 9, 8));
  bot.game = { dimension: 'overworld' };
  bot.entity = { position: new Vec3(1000.5, 201, 995.5) };
  bot.entities = {};
  bot.blockAt = (position) => ({ boundingBox: position.y === 200 ? 'block' : 'empty' });
  bot.lookAt = async () => {};
  const drop = { id: 77, name: 'item', position: new Vec3(1001.2, 202, 995.5), getDroppedItem: () => item('stone', -1, 3) };
  bot.toss = async () => {
    bot.inventory.updateSlot(9, item('stone', 9, 5));
    bot.emit('itemDrop', drop);
  };
  let followed = false;
  let stopped = false;
  bot.pathfinder = {
    async goto() { assert.fail('Recovery must not navigate to a stale item position'); },
    setGoal(goal, dynamic) {
      if (!goal) { stopped = true; return; }
      assert.equal(dynamic, true);
      assert.equal(goal.entity, drop);
      assert.equal(goal.rangeSq, 0);
      assert.equal(bot.inventory.slots[9].count, 5);
      followed = true;
      setImmediate(() => {
        drop.position = new Vec3(1003.4, 201, 995.5);
        assert.equal(goal.hasChanged(), true);
        assert.equal(goal.x, 1003);
        assert.equal(goal.y, 201);
        bot.inventory.updateSlot(9, item('stone', 9, 8));
      });
    },
  };
  const result = await SCENARIOS.find(({ id }) => id === 'toss').run(bot, { ...context(), actionPacing: true, origin: { x: 1000, y: 200, z: 1000 } });
  assert.equal(result.status, 'passed');
  assert.equal(followed, true);
  assert.equal(stopped, true);
});
