// Regenerate with: node integrations/mineflayer/fixtures/generate.js
// These are actual SSE frames emitted by the adapter, used by the Java contract test.
import { EventEmitter } from 'node:events';
import http from 'node:http';
import { writeFile } from 'node:fs/promises';
import { installMineflayerObserver } from '../index.js';

const makeWindow = (id, type, count) => Object.assign(new EventEmitter(), {
  id, type, title: type, slots: Array(count).fill(null), inventoryStart: count - 36, selectedItem: null,
});
const bot = Object.assign(new EventEmitter(), {
  username: 'Curio', version: '1.20.1', entity: {}, health: 20, food: 18, foodSaturation: 4,
  experience: { level: 12, progress: 0.5 }, inventory: makeWindow(0, 'inventory', 46), currentWindow: null,
  _client: Object.assign(new EventEmitter(), { write() {} }),
  registry: { items: { 1: { name: 'emerald', stackSize: 64 }, 2: { name: 'enchanted_book', stackSize: 1 } } },
});
const observer = installMineflayerObserver(bot);
const server = await observer.listen({ port: 0 });
const frames = [];
let pending = '';
let resolveInitial;
let resolveFinal;
const initial = new Promise((resolve) => { resolveInitial = resolve; });
const final = new Promise((resolve) => { resolveFinal = resolve; });
const request = http.get({ host: '127.0.0.1', port: server.address().port, path: '/api/observer/stream' }, (response) => {
  response.setEncoding('utf8');
  response.on('data', (chunk) => {
    pending += chunk;
    let end;
    while ((end = pending.indexOf('\n\n')) !== -1) {
      const lines = pending.slice(0, end).split('\n');
      pending = pending.slice(end + 2);
      if (lines[0].startsWith(':')) continue;
      const event = { event: lines[0].slice(7), data: JSON.parse(lines[1].slice(6)) };
      frames.push(event);
      if (event.event === 'state') resolveInitial();
      if (event.event === 'bot_status' && event.data.status === 'offline') resolveFinal();
    }
  });
});
const timeout = setTimeout(() => { throw new Error('Fixture stream did not complete'); }, 5_000);
await initial;
const grindstone = makeWindow(1, 'minecraft:grindstone', 39);
grindstone.slots[0] = { name: 'diamond_sword', count: 1, nbt: { type: 'compound', value: {
  Damage: { type: 'int', value: 10 }, Enchantments: { type: 'list', value: { type: 'compound', value: [
    { id: { type: 'string', value: 'minecraft:sharpness' }, lvl: { type: 'short', value: 3 } },
  ] } },
} } };
bot.currentWindow = grindstone;
bot.emit('windowOpen', grindstone);
grindstone.slots[2] = { name: 'diamond_sword', count: 1, damage: 2 };
grindstone.selectedItem = { name: 'diamond_sword', count: 1, damage: 2 };
grindstone.emit('updateSlot', 2);
bot.emit('windowClose', grindstone);

const furnace = makeWindow(2, 'minecraft:furnace', 39);
bot.currentWindow = furnace;
bot._client.emit('craft_progress_bar', { windowId: 2, property: 3, value: 200 });
bot.emit('windowOpen', furnace);
bot._client.emit('craft_progress_bar', { windowId: 2, property: 2, value: 40 });
bot.emit('windowClose', furnace);

const merchant = makeWindow(3, 'minecraft:merchant', 39);
bot.currentWindow = merchant;
bot.emit('windowOpen', merchant);
bot._client.emit('trade_list', { windowId: 3, villagerLevel: 2, experience: 25, isRegularVillager: true, canRestock: true,
  trades: [{ inputItem1: { present: true, itemId: 1, itemCount: 10 }, inputItem2: { present: false },
    outputItem: { present: true, itemId: 2, itemCount: 1, nbtData: { type: 'compound', value: {
      StoredEnchantments: { type: 'list', value: { type: 'compound', value: [
        { id: { type: 'string', value: 'minecraft:mending' }, lvl: { type: 'short', value: 1 } },
      ] } },
    } } }, tradeDisabled: false, nbTradeUses: 1, maximumNbTradeUses: 12,
    demand: 2, priceMultiplier: 0.2, specialPrice: -1 }],
});
bot._client.write('select_trade', { slot: 0 });
bot.emit('end');
await final;
clearTimeout(timeout);
request.destroy();
await observer.dispose();
await writeFile(new URL('./generated-events.json', import.meta.url), JSON.stringify(frames, null, 2) + '\n');
console.log(`Wrote ${frames.length} real adapter frames`);
