import { setTimeout as delay } from 'node:timers/promises';
import { Vec3 } from 'vec3';

export const ARENA_STATIONS = [
  ['crafting_table', -9, -8], ['chest', -5, -8], ['barrel', -1, -8], ['ender_chest', 3, -8], ['shulker_box', 7, -8],
  ['hopper', -9, -3], ['dispenser', -5, -3], ['grindstone', -1, -3], ['anvil', 3, -3], ['smithing_table', 7, -3],
  ['enchanting_table', -9, 2], ['furnace', -5, 2], ['blast_furnace', -1, 2], ['smoker', 3, 2], ['brewing_stand', 7, 2],
  ['stonecutter', -9, 7], ['loom', -5, 7], ['cartography_table', -1, 7], ['beacon', 3, 7], ['villager', 7, 7],
];

/** A reviewable plan: every block mutation is bounded and preserves non-air blocks. */
export function arenaPlan(origin, username) {
  if (!origin || !['x', 'y', 'z'].every((axis) => Number.isInteger(origin[axis]))) throw new Error('Preparation requires explicit integer origin coordinates.');
  if (Math.abs(origin.x) > 29999970 || Math.abs(origin.z) > 29999970 || origin.y < -60 || origin.y > 310) throw new Error('Arena origin is outside the Minecraft build limits.');
  if (!/^[A-Za-z0-9_]{1,16}$/.test(username)) throw new Error('Preparation requires a Minecraft Java username.');
  const { x, y, z } = origin;
  const tag = `curio_hud_${x}_${y}_${z}`;
  const selector = `@e[type=minecraft:villager,tag=${tag},limit=1]`;
  const at = (dx, dy, dz) => `${x + dx} ${y + dy} ${z + dz}`;
  const commands = [
    // Beacon base is placed before the air-only floor, so the stone cannot replace it.
    ...[-1, 0, 1].flatMap((dx) => [-1, 0, 1].map((dz) => `setblock ${at(3 + dx, 0, 7 + dz)} minecraft:iron_block keep`)),
    `fill ${at(-12, 0, -12)} ${at(11, 0, 11)} minecraft:stone replace minecraft:air`,
    ...ARENA_STATIONS.filter(([name]) => name !== 'villager').map(([name, dx, dz]) => `setblock ${at(dx, 1, dz)} minecraft:${name} keep`),
    ...[[-11, -11], [10, -11], [-11, 10], [10, 10]].map(([dx, dz]) => `setblock ${at(dx, 1, dz)} minecraft:torch keep`),
    `execute unless entity ${selector} run summon minecraft:villager ${at(7, 1, 7)} {Tags:["${tag}"]}`,
    `data merge entity ${selector} {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,VillagerData:{type:"minecraft:plains",profession:"minecraft:librarian",level:2}}`,
    `data modify entity ${selector} Offers.Recipes set value []`,
    `data modify entity ${selector} Offers.Recipes append value {maxUses:1000,rewardExp:0b,priceMultiplier:0.0f}`,
    `data modify entity ${selector} Offers.Recipes[0].buy set value {id:"minecraft:emerald",Count:1b}`,
    `data modify entity ${selector} Offers.Recipes[0].sell set value {id:"minecraft:enchanted_book",Count:1b}`,
    `data modify entity ${selector} Offers.Recipes[0].sell.tag set value {StoredEnchantments:[{id:"minecraft:mending",lvl:1s}]}`,
    ...[
      ['oak_log', 2], ['oak_planks', 16], ['stick', 8], ['stone', 8], ['coal', 8], ['raw_iron', 2], ['porkchop', 2],
      ['iron_sword{Damage:10,Enchantments:[{id:"minecraft:sharpness",lvl:3s}]}', 1], ['iron_pickaxe{Damage:20}', 1],
      ['diamond_boots', 1], ['netherite_upgrade_smithing_template', 1], ['netherite_ingot', 1],
      ['book', 2], ['lapis_lazuli', 8], ['potion{Potion:"minecraft:water"}', 1], ['nether_wart', 2], ['blaze_powder', 4],
      ['emerald', 32], ['white_banner', 2], ['red_dye', 2], ['paper', 8], ['map', 2], ['iron_ingot', 2],
    ].map(([item, count]) => `give ${username} minecraft:${item} ${count}`),
    `experience add ${username} 30 levels`,
  ];
  return { tag, bounds: { minX: x - 12, maxX: x + 11, floorY: y, minZ: z - 12, maxZ: z + 11 }, commands };
}

/** Called only by explicit --prepare. Never clears inventory, existing blocks, or other entities. */
export async function prepareArena(bot, { origin, signal, log }) {
  const plan = arenaPlan(origin, bot.username);
  const previousMode = bot.game.gameMode;
  const previousPosition = bot.entity.position.clone();
  const previousDimension = bot.game.dimension;
  let flying = false;
  let completed = false;
  const waitFor = async (predicate, message, ms = 7000) => {
    const deadline = Date.now() + ms;
    while (!predicate()) {
      signal.throwIfAborted();
      if (Date.now() >= deadline) throw new Error(message);
      await delay(100, undefined, { signal });
    }
  };
  const command = async (value) => {
    signal.throwIfAborted();
    bot.chat('/' + value);
    await delay(160, undefined, { signal });
  };
  log('prepare', { detail: `Air-only arena ${JSON.stringify(plan.bounds)}; temporarily creative, then restore ${previousMode}.` });
  try {
    await command(`gamemode creative ${bot.username}`);
    await waitFor(() => bot.game.gameMode === 'creative', 'Preparation requires OP: /gamemode creative was not applied. Grant OP to this test bot, then retry.');
    bot.creative.startFlying();
    flying = true;
    await command(`execute in minecraft:overworld run teleport ${bot.username} ${origin.x + 0.5} ${origin.y + 3} ${origin.z + 0.5}`);
    await waitFor(() => bot.game.dimension === 'overworld' && Math.abs(bot.entity.position.x - origin.x - 0.5) < 2 && Math.abs(bot.entity.position.z - origin.z - 0.5) < 2,
      'Teleport to the approved overworld arena failed. Check OP permissions.');
    await waitFor(() => bot.blockAt(new Vec3(origin.x, origin.y, origin.z)) !== null, 'Arena chunks did not load.', 15000);
    // The supplied items occupy 23 slots on a fresh account. Do not create dropped overflow items.
    if (bot.inventory.emptySlotCount() < 23) throw new Error('Preparation needs 23 free inventory slots for supplies. Move existing items to your own storage; the script will not clear them.');
    for (const value of plan.commands) await command(`execute in minecraft:overworld run ${value}`);
    await waitFor(() => bot.blockAt(new Vec3(origin.x, origin.y, origin.z))?.boundingBox === 'block', 'The arena floor was not placed. Check OP and build protection.');
    await command(`execute in minecraft:overworld run teleport ${bot.username} ${origin.x + 0.5} ${origin.y + 1} ${origin.z + 0.5}`);
    completed = true;
    log('prepare', { detail: 'Stations, a tagged librarian, supplies, and 30 added XP levels prepared. Existing blocks were preserved.' });
    return { status: 'passed', detail: `Prepared 24×24 arena centered at ${origin.x},${origin.y},${origin.z}; villager tag ${plan.tag}.` };
  } finally {
    if (!completed && flying) {
      bot.chat(`/execute in minecraft:${previousDimension} run teleport ${bot.username} ${previousPosition.x} ${previousPosition.y} ${previousPosition.z}`);
      await delay(300);
    }
    if (flying) bot.creative.stopFlying();
    // Restoration also runs after stop/failure; these two actions complete the authorized temporary mode change.
    bot.chat(`/gamemode ${previousMode} ${bot.username}`);
    await delay(300);
  }
}
