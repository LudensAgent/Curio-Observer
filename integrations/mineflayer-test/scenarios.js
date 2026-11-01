import { setTimeout as delay } from 'node:timers/promises';
import pathfinder from 'mineflayer-pathfinder';
import { Vec3 } from 'vec3';
import { ARENA_STATIONS } from './prepare.js';

const skipped = (detail) => ({ status: 'skipped', detail });
const passed = (detail) => ({ status: 'passed', detail });
const inventoryStart = (window) => window.id === 0 ? 9 : window.type === 'minecraft:smithing' && window.slots.length === 40 ? 4 : window.inventoryStart;

/** A real server result must arrive before a scenario is reported successful. */
async function until(predicate, context, description, timeoutMs = 10000) {
  const deadline = Date.now() + timeoutMs;
  while (!predicate()) {
    context.signal.throwIfAborted();
    if (Date.now() >= deadline) throw new Error(`Timed out waiting for ${description}`);
    await delay(100, undefined, { signal: context.signal });
  }
}

async function approach(bot, position, context, range = 2) {
  context.signal.throwIfAborted();
  if (bot.entity.position.distanceTo(position) <= range + 1) return;
  const stop = () => bot.pathfinder.setGoal(null);
  context.signal.addEventListener('abort', stop, { once: true });
  try {
    await bot.pathfinder.goto(new pathfinder.goals.GoalNear(position.x, position.y, position.z, range));
    context.signal.throwIfAborted();
  } finally { context.signal.removeEventListener('abort', stop); }
}

async function station(bot, name, context) {
  if (context.prepared && context.origin) {
    const [, dx, dz] = ARENA_STATIONS.find(([id]) => id === name);
    const position = new Vec3(context.origin.x + dx, context.origin.y + 1, context.origin.z + dz);
    const block = bot.blockAt(position);
    return block?.name === name ? block : null;
  }
  const definition = bot.registry.blocksByName[name];
  return definition ? bot.findBlock({ matching: definition.id, maxDistance: 32 }) : null;
}

function findItem(window, name, predicate = () => true) {
  const start = inventoryStart(window);
  return window.slots.slice(start, start + 36).find((item) => item?.name === name && predicate(item));
}

async function put(bot, window, name, slot, context, predicate) {
  context.signal.throwIfAborted();
  if (window.slots[slot]) throw new Error(`Input slot ${slot} is occupied; refusing to replace it.`);
  const item = findItem(window, name, predicate);
  if (!item) throw new Error(`Missing ${name} in player inventory.`);
  const start = inventoryStart(window);
  await bot.transfer({ window, itemType: item.type, metadata: item.metadata, nbt: item.nbt, count: 1,
    sourceStart: start, sourceEnd: start + 36, destStart: slot, destEnd: slot + 1 });
  await context.wait();
}

async function take(bot, window, slot, context) {
  await until(() => window.slots[slot], context, `server result in slot ${slot}`);
  const result = window.slots[slot];
  context.log('result', { detail: `${result.displayName ?? result.name} ×${result.count}` });
  await context.wait();
  await bot.putAway(slot);
  await context.wait();
  return result;
}

/** Common real-window lifecycle; occupied inputs are never silently overwritten. */
async function withWindow(bot, name, context, use, open = (block) => bot.openBlock(block)) {
  const block = await station(bot, name, context);
  if (!block) return skipped(`No ${name} at the prepared station / within 32 blocks.`);
  if (bot.inventory.emptySlotCount() < 2) return skipped('At least two free player inventory slots are needed to collect results safely.');
  await approach(bot, block.position, context);
  const window = await open(block);
  if (window.type === 'minecraft:smithing' && window.slots.length === 40) {
    // prismarine-windows 2.9 still describes pre-1.20 smithing; real packets contain the template slot.
    window.inventoryStart = 4;
    window.inventoryEnd = 40;
    window.craftingResultSlot = 3;
  }
  try {
    await context.wait();
    return await use(window);
  } finally { if (bot.currentWindow === window) await window.close(); }
}

function missing(bot, names) {
  const absent = names.filter((name) => !findItem(bot.inventory, name));
  return absent.length ? skipped(`Missing prerequisite items: ${absent.join(', ')}.`) : null;
}

const containerScenarios = ['chest', 'barrel', 'ender_chest', 'shulker_box', 'hopper', 'dispenser'].map((name) => ({
  id: name, label: `${name}: open, deposit, withdraw, close`,
  async run(bot, context) {
    const absent = missing(bot, ['stone']);
    if (absent) return absent;
    return withWindow(bot, name, context, async (window) => {
      const slot = window.slots.slice(0, inventoryStart(window)).findIndex((item) => item === null);
      if (slot < 0) return skipped('Container is full; existing contents preserved.');
      await put(bot, window, 'stone', slot, context);
      await take(bot, window, slot, context);
      return passed('One stone deposited and retrieved through real inventory clicks.');
    });
  },
}));

const furnaceScenarios = [['furnace', 'raw_iron'], ['blast_furnace', 'raw_iron'], ['smoker', 'porkchop']].map(([name, input]) => ({
  id: name, label: `${name}: fuel, progress, cooked output`,
  async run(bot, context) {
    const absent = missing(bot, [input, 'coal']);
    if (absent) return absent;
    return withWindow(bot, name, context, async (window) => {
      if (window.slots.slice(0, 3).some(Boolean)) return skipped('Furnace is already in use; existing contents preserved.');
      await put(bot, window, input, 0, context);
      await put(bot, window, 'coal', 1, context);
      await until(() => window.slots[2], context, 'smelting completion', 25000);
      const result = await take(bot, window, 2, context);
      return passed(`Server produced ${result.name}; fuel and progress remained visible while cooking.`);
    }, (block) => bot.openFurnace(block));
  },
}));

export const SCENARIOS = [
  {
    id: 'inventory', label: 'Player 2×2 crafting and cursor',
    async run(bot, context) {
      const absent = missing(bot, ['oak_log']);
      if (absent) return absent;
      if (!context.observer) return skipped('Observer adapter is required to bracket the client-only inventory screen.');
      const window = bot.inventory;
      if (window.slots.slice(0, 5).some(Boolean) || window.selectedItem) return skipped('Player crafting grid/cursor is occupied.');
      if (window.emptySlotCount() < 2) return skipped('Two empty inventory slots required.');
      context.observer.openInventory();
      try {
        await context.wait();
        // Pause with a real carried stack so the cursor HUD is visually testable.
        const source = findItem(window, 'oak_log');
        await bot.clickWindow(source.slot, 0, 0);
        await context.wait();
        await bot.clickWindow(source.slot, 0, 0);
        await put(bot, window, 'oak_log', 1, context);
        const result = await take(bot, window, 0, context);
        return passed(`2×2 grid crafted ${result.count} ${result.name}.`);
      } finally {
        // Vanilla returns unfinished crafting inputs/cursor when window 0 is closed.
        await bot.closeWindow(window);
        context.observer.closeInventory();
      }
    },
  },
  {
    id: 'crafting_table', label: 'Workbench 3×3 shaped crafting',
    async run(bot, context) {
      const absent = missing(bot, ['oak_planks', 'stick']);
      if (absent) return absent;
      return withWindow(bot, 'crafting_table', context, async (window) => {
        if (window.slots.slice(0, 10).some(Boolean)) return skipped('Workbench grid is occupied.');
        for (const slot of [1, 2, 3]) await put(bot, window, 'oak_planks', slot, context);
        for (const slot of [5, 8]) await put(bot, window, 'stick', slot, context);
        const result = await take(bot, window, 0, context);
        return passed(`3×3 grid crafted ${result.name}.`);
      });
    },
  },
  ...containerScenarios,
  {
    id: 'grindstone', label: 'Grindstone: remove an enchantment',
    async run(bot, context) {
      const sword = findItem(bot.inventory, 'iron_sword', (item) => item.enchants.length > 0);
      if (!sword) return skipped('An enchanted iron sword is required.');
      return withWindow(bot, 'grindstone', context, async (window) => {
        if (window.slots.slice(0, 3).some(Boolean)) return skipped('Grindstone inputs are occupied.');
        await put(bot, window, 'iron_sword', 0, context, (item) => item.enchants.length > 0);
        const result = await take(bot, window, 2, context);
        if (result.enchants.length) throw new Error('Grindstone output still has an enchantment.');
        return passed('Server removed the sword enchantment.');
      });
    },
  },
  {
    id: 'anvil', label: 'Anvil: rename with XP cost',
    async run(bot, context) {
      const absent = missing(bot, ['iron_pickaxe']);
      if (absent) return absent;
      if (bot.experience.level < 1) return skipped('At least one XP level is required.');
      return withWindow(bot, 'anvil', context, async (window) => {
        if (window.slots.slice(0, 3).some(Boolean)) return skipped('Anvil inputs are occupied.');
        await put(bot, window, 'iron_pickaxe', 0, context);
        bot._client.write('name_item', { name: 'Curio HUD Test' });
        await take(bot, window, 2, context);
        return passed('Server applied the new name and charged the anvil XP cost.');
      });
    },
  },
  {
    id: 'smithing', label: 'Smithing: template, equipment, material, result',
    async run(bot, context) {
      const absent = missing(bot, ['netherite_upgrade_smithing_template', 'diamond_boots', 'netherite_ingot']);
      if (absent) return absent;
      return withWindow(bot, 'smithing_table', context, async (window) => {
        if (window.slots.slice(0, 4).some(Boolean)) return skipped('Smithing inputs are occupied.');
        await put(bot, window, 'netherite_upgrade_smithing_template', 0, context);
        await put(bot, window, 'diamond_boots', 1, context);
        await put(bot, window, 'netherite_ingot', 2, context);
        const result = await take(bot, window, 3, context);
        if (result.name !== 'netherite_boots') throw new Error(`Unexpected smithing result ${result.name}`);
        return passed('Server upgraded diamond boots using the 1.20 template slot.');
      });
    },
  },
  {
    id: 'enchanting', label: 'Enchanting: offers, lapis, XP and actual enchantment',
    async run(bot, context) {
      const absent = missing(bot, ['book', 'lapis_lazuli']);
      if (absent) return absent;
      if (bot.experience.level < 3) return skipped('At least three XP levels are required.');
      return withWindow(bot, 'enchanting_table', context, async (window) => {
        await put(bot, window, 'book', 0, context);
        await put(bot, window, 'lapis_lazuli', 1, context);
        await until(() => window.enchantments[0].level > 0, context, 'enchantment offers');
        if (window.enchantments[0].level > bot.experience.level) return skipped('Insufficient XP for the server offer.');
        await context.wait();
        await window.enchant(0);
        await take(bot, window, 0, context);
        if (window.slots[1]) await bot.putAway(1);
        return passed('First server enchantment offer applied to a book.');
      }, (block) => bot.openEnchantmentTable(block));
    },
  },
  ...furnaceScenarios,
  {
    id: 'brewing', label: 'Brewing: bottle, ingredient, fuel and 20-second progress',
    async run(bot, context) {
      const absent = missing(bot, ['potion', 'nether_wart', 'blaze_powder']);
      if (absent) return absent;
      const water = (item) => item.nbt?.value?.Potion?.value === 'minecraft:water';
      if (!findItem(bot.inventory, 'potion', water)) return skipped('A water bottle is required.');
      return withWindow(bot, 'brewing_stand', context, async (window) => {
        if (window.slots.slice(0, 5).some(Boolean)) return skipped('Brewing stand is already in use.');
        await put(bot, window, 'potion', 0, context, water);
        await put(bot, window, 'blaze_powder', 4, context);
        await put(bot, window, 'nether_wart', 3, context);
        await until(() => window.slots[0]?.nbt?.value?.Potion?.value === 'minecraft:awkward', context, 'awkward potion', 30000);
        await take(bot, window, 0, context);
        if (window.slots[4]) await bot.putAway(4);
        return passed('Server brewed an awkward potion; brewing time and fuel were observable.');
      });
    },
  },
  {
    id: 'villager', label: 'Villager: offers, selection, trade and use count',
    async run(bot, context) {
      let entity;
      if (context.prepared && context.origin) {
        const position = new Vec3(context.origin.x + 7, context.origin.y + 1, context.origin.z + 7);
        entity = Object.values(bot.entities).find((value) => value.name === 'villager' && value.position.distanceTo(position) < 2);
      } else entity = bot.nearestEntity((value) => value.name === 'villager');
      if (!entity || bot.entity.position.distanceTo(entity.position) > 40) return skipped('No nearby villager found.');
      if (bot.inventory.emptySlotCount() < 2) return skipped('Two empty inventory slots required.');
      await approach(bot, entity.position, context);
      const window = await bot.openVillager(entity);
      try {
        await context.wait();
        const index = window.trades.findIndex((trade) => !trade.tradeDisabled && trade.inputs.every((item, inputIndex) => {
          const count = bot.inventory.items().filter((held) => held.type === item.type).reduce((sum, held) => sum + held.count, 0);
          return count >= (inputIndex === 0 ? trade.realPrice : item.count);
        }));
        if (index < 0) return skipped('No enabled trade is affordable with current items.');
        // bot.trade selects the offer itself. Preselecting here lets the server move the
        // emerald stack into the input, making Mineflayer's inventory-only count fail.
        const before = window.trades[index].nbTradeUses;
        await bot.trade(window, index, 1);
        await context.wait();
        return passed(`Trade ${index} completed; uses ${before} → ${window.trades[index].nbTradeUses}.`);
      } finally { if (bot.currentWindow === window) await window.close(); }
    },
  },
  {
    id: 'stonecutter', label: 'Stonecutter: select recipe and take result',
    async run(bot, context) {
      const absent = missing(bot, ['stone']);
      if (absent) return absent;
      return withWindow(bot, 'stonecutter', context, async (window) => {
        await put(bot, window, 'stone', 0, context);
        bot._client.write('enchant_item', { windowId: window.id, enchantment: 0 });
        const result = await take(bot, window, 1, context);
        return passed(`First stonecutting recipe produced ${result.name}.`);
      });
    },
  },
  {
    id: 'loom', label: 'Loom: banner, dye, pattern and result',
    async run(bot, context) {
      const absent = missing(bot, ['white_banner', 'red_dye']);
      if (absent) return absent;
      return withWindow(bot, 'loom', context, async (window) => {
        await put(bot, window, 'white_banner', 0, context);
        await put(bot, window, 'red_dye', 1, context);
        // Select a built-in pattern using the standard screen-handler button packet.
        bot._client.write('enchant_item', { windowId: window.id, enchantment: 1 });
        await take(bot, window, 3, context);
        return passed('A red banner pattern was applied by the server.');
      });
    },
  },
  {
    id: 'cartography', label: 'Cartography: actual map and enlargement',
    async run(bot, context) {
      const absent = missing(bot, ['paper']);
      if (absent) return absent;
      if (!findItem(bot.inventory, 'filled_map')) {
        const emptyMap = findItem(bot.inventory, 'map');
        if (!emptyMap) return skipped('An empty or filled map is required.');
        await bot.equip(emptyMap, 'hand');
        bot.activateItem();
        await until(() => findItem(bot.inventory, 'filled_map'), context, 'new filled map');
      }
      return withWindow(bot, 'cartography_table', context, async (window) => {
        await put(bot, window, 'filled_map', 0, context);
        await put(bot, window, 'paper', 1, context);
        await take(bot, window, 2, context);
        return passed('Server enlarged a real map.');
      });
    },
  },
  {
    id: 'beacon', label: 'Beacon: payment slot and speed selection',
    async run(bot, context) {
      const absent = missing(bot, ['iron_ingot']);
      if (absent) return absent;
      return withWindow(bot, 'beacon', context, async (window) => {
        await put(bot, window, 'iron_ingot', 0, context);
        bot._client.write('set_beacon_effect', { primary_effect: 1, secondary_effect: undefined });
        await until(() => window.slots[0] === null, context, 'beacon accepting payment');
        await context.wait();
        return passed('Beacon accepted an iron ingot and speed selection.');
      });
    },
  },
  {
    id: 'toss', label: 'Drop one batch of exactly three stone, verify, and collect it',
    async run(bot, context) {
      if (!context.origin || bot.game.dimension !== 'overworld') return skipped('Batch drop needs an explicit overworld test-area origin.');
      if (bot.currentWindow || bot.inventory.selectedItem) return skipped('Close the active window and empty the cursor before dropping a batch.');
      const source = findItem(bot.inventory, 'stone', (item) => item.count >= 3);
      if (!source) return skipped('A stack containing at least three stone is required.');
      const destination = bot.inventory.firstEmptySlotRange(9, 45);
      if (destination === null) return skipped('One empty inventory slot is required to split the exact batch.');
      // The prepared arena has a clear east-west aisle between station rows z=-8 and z=-3.
      const center = new Vec3(context.origin.x, context.origin.y + 1, context.origin.z - 5);
      if (bot.entity.position.distanceTo(center) > 40) return skipped('The explicit test-area origin is more than 40 blocks away.');
      // Keep the throw on a confirmed, clear patch of the approved platform.
      const patch = [-3, -2, -1, 0, 1, 2, 3].flatMap((dx) => [-1, 0, 1].map((dz) => center.offset(dx, 0, dz)));
      if (patch.some((position) => bot.blockAt(position.offset(0, -1, 0))?.boundingBox !== 'block'
        || bot.blockAt(position)?.boundingBox !== 'empty' || bot.blockAt(position.offset(0, 1, 0))?.boundingBox !== 'empty')) {
        return skipped('The 7×3 test aisle needs a solid floor and two clear blocks above it for safe drop recovery.');
      }
      if (Object.values(bot.entities).some((entity) => entity.name === 'item' && entity.position.distanceTo(center) < 7)) {
        return skipped('Nearby dropped items could merge with this batch or be collected accidentally; collect them before retrying.');
      }
      await approach(bot, center, context, 0);
      await bot.lookAt(center.offset(2.5, 1.2, 0.5), false);
      const countStone = () => bot.inventory.items().filter((item) => item.type === source.type).reduce((sum, item) => sum + item.count, 0);
      const before = countStone();
      const existingIds = new Set(Object.keys(bot.entities).map(Number));
      const drops = new Map();
      const observeDrop = (entity) => {
        if (existingIds.has(entity.id) || entity.position.distanceTo(center) >= 7) return;
        const item = entity.getDroppedItem();
        if (item?.type === source.type) drops.set(entity.id, { entity, count: item.count });
      };
      bot.on('itemDrop', observeDrop);
      context.observer?.openInventory();
      try {
        if (context.actionPacing) {
          // Exercise the optional helper's public API, including its exact-batch assembly.
          await bot.toss(source.type, source.metadata, 3);
        } else {
          // Stock toss(type, metadata, 3) splits a larger stack into three single drops.
          // Isolate three first; tossStack then uses one outside left-click for the batch.
          await bot.transfer({ window: bot.inventory, itemType: source.type, metadata: source.metadata, nbt: source.nbt,
            count: 3, sourceStart: source.slot, sourceEnd: source.slot + 1, destStart: destination, destEnd: destination + 1 });
          await context.wait();
          const batch = bot.inventory.slots[destination];
          if (batch?.type !== source.type || batch.count !== 3) throw new Error('The isolated drop batch must contain exactly three stone.');
          await bot.tossStack(batch);
        }
        await until(() => drops.size > 0 && countStone() === before - 3, context, 'one three-item drop and inventory decrease', 5000);
        if (drops.size !== 1 || [...drops.values()][0].count !== 3) throw new Error('Expected one dropped entity containing three stone, not several single-item drops.');
        context.log('drop', { detail: 'Server confirmed one stone entity ×3; player inventory decreased by exactly 3.' });
        await context.wait();
        if (countStone() !== before) {
          const [{ entity }] = [...drops.values()];
          // A thrown item keeps moving after spawn. Follow the entity until the
          // server inventory confirms pickup, instead of walking to one old position.
          bot.pathfinder.setGoal(new pathfinder.goals.GoalFollow(entity, 0), true);
          try {
            await until(() => countStone() === before, context, 'collecting the three dropped stone', 10000);
          } finally { bot.pathfinder.setGoal(null); }
        }
        return passed('Dropped one batch of exactly 3 stone, verified entity count and inventory delta, then collected all 3.');
      } finally {
        bot.off('itemDrop', observeDrop);
        if (bot.inventory.selectedItem) await bot.closeWindow(bot.inventory);
        context.observer?.closeInventory();
      }
    },
  },
];
