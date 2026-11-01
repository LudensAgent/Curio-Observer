import { setTimeout as delay } from 'node:timers/promises';
import { isDeepStrictEqual } from 'node:util';
import { itemData } from './items.js';

const installed = new WeakMap();

/**
 * Opt-in real inventory click pacing for Mineflayer 4.39 / Minecraft 1.20.1.
 * Leaves networking and server updates synchronous. Callers still serialize high-level operations.
 * @param {object} bot An initialized Mineflayer bot.
 * @param {{intervalMs?:number,onPointer?:Function,onTransfer?:Function}} [options]
 */
export function installMineflayerActionPacing(bot, options = {}) {
  const intervalMs = options.intervalMs ?? 220;
  if (!Number.isInteger(intervalMs) || intervalMs < 0 || intervalMs > 2000) throw new RangeError('intervalMs must be an integer from 0 to 2000');
  if (!bot.inventory || typeof bot.clickWindow !== 'function') throw new TypeError('Action pacing requires an initialized Mineflayer inventory plugin');
  if (installed.has(bot)) throw new Error('This bot already has action pacing installed');
  const cleanup = [];
  let disposed = false;
  let generation = 0;
  let waiting = new AbortController();
  let tail = Promise.resolve();
  const current = () => bot.currentWindow || bot.inventory;
  const check = (window, token) => {
    if (disposed || generation !== token || current() !== window) throw new Error('Inventory action cancelled: session or window changed');
  };
  const replace = (owner, name, wrapped) => {
    const original = owner[name];
    owner[name] = wrapped;
    cleanup.push(() => { if (owner[name] === wrapped) owner[name] = original; });
    return original;
  };
  const same = (a, b) => a && b && a.type === b.type && a.metadata === b.metadata && isDeepStrictEqual(a.nbt, b.nbt);
  const matches = (item, type, metadata, nbt) => item && item.type === type
    && (metadata == null || item.metadata === metadata) && (nbt == null || isDeepStrictEqual(item.nbt, nbt));

  const originalClick = bot.clickWindow;
  replace(bot, 'clickWindow', function (slot, mouseButton, mode) {
    const window = current();
    const token = generation;
    const signal = waiting.signal;
    const execute = async () => {
      check(window, token);
      // A pointer carries only the real cursor, never the stack it is approaching.
      options.onPointer?.({ windowId: window.id, slot, durationMs: intervalMs, item: itemData(window.selectedItem) });
      await delay(intervalMs, undefined, { signal });
      check(window, token);
      const held = itemData(window.selectedItem);
      const source = itemData(window.slots[slot]);
      const result = await originalClick.call(bot, slot, mouseButton, mode);
      check(window, token);
      const after = itemData(window.selectedItem);
      const remaining = itemData(window.slots[slot]);
      let item;
      let kind;
      let count = 0;
      if (slot === -999 && held) { item = held; kind = 'drop'; count = mouseButton === 1 ? 1 : held.count; }
      else if (mode === 4 && source) { item = source; kind = 'drop'; count = mouseButton === 0 ? 1 : source.count; }
      else if (mode === 0 && held) {
        item = held; kind = 'put';
        count = held.count - (after?.id === held.id && after.nbt === held.nbt ? after.count : 0);
        if (count < 0) { item = after; kind = 'take'; count = -count; }
      } else if (mode === 0 && after) { item = after; kind = 'take'; count = after.count; }
      else if (mode === 1 && source) {
        item = source; kind = 'take'; count = source.count - (remaining?.id === source.id && remaining.nbt === source.nbt ? remaining.count : 0);
      }
      if (item && count > 0) options.onTransfer?.({ windowId: window.id, slot, kind, item: { ...item, count } });
      return result;
    };
    const result = tail.then(execute);
    tail = result.then(() => undefined, () => undefined);
    return result;
  });

  // Mineflayer's stock transfer/putAway capture its unwrapped local clickWindow.
  // These small replacements route genuine clicks through the public paced method.
  const returnCursor = async (start, end, window, fallback, token) => {
    while (window.selectedItem) {
      check(window, token);
      const held = window.selectedItem;
      let slot = window.slots.findIndex((item, index) => index >= start && index < end && same(item, held) && item.count < item.stackSize);
      if (slot < 0) slot = window.slots.findIndex((item, index) => index >= start && index < end && !item);
      if (slot < 0 && fallback != null && (!window.slots[fallback] || same(window.slots[fallback], held))) slot = fallback;
      if (slot < 0) throw new Error('No room to return the cursor stack; it remains on the cursor');
      await bot.clickWindow(slot, 0, 0);
    }
  };
  replace(bot, 'putSelectedItemRange', (start, end, window = current(), fallback = null) => returnCursor(start, end, window, fallback, generation));

  replace(bot, 'transfer', async (input) => {
    const window = input.window || current();
    const token = generation;
    check(window, token);
    const { itemType, metadata, nbt, sourceStart, destStart } = input;
    const sourceEnd = input.sourceEnd == null ? sourceStart + 1 : input.sourceEnd;
    const destEnd = input.destEnd == null ? destStart + 1 : input.destEnd;
    let count = input.count ?? 1;
    if (!Number.isSafeInteger(count) || count < 0) throw new RangeError('transfer count must be a non-negative integer');
    if (count === 0) return;
    if (!Number.isInteger(sourceStart) || sourceStart < 0 || sourceEnd > window.slots.length || sourceEnd <= sourceStart
        || !Number.isInteger(destStart) || (destStart !== -999 && (destStart < 0 || destEnd > window.slots.length || destEnd <= destStart))) {
      throw new RangeError('transfer requires valid source and destination slot ranges');
    }
    if (destStart === -999) return tossBatch(itemType, metadata, count, nbt, window, sourceStart, sourceEnd, token);
    const selected = window.selectedItem;
    if (selected && !matches(selected, itemType, metadata, nbt)) throw new Error('Clear the cursor before transferring a different item');
    const sources = window.slots.slice(sourceStart, sourceEnd).filter((item) => matches(item, itemType, metadata, nbt));
    if (sources.reduce((sum, item) => sum + item.count, selected?.count ?? 0) < count) throw new Error(`Not enough matching items to transfer ${count}`);
    const stackSize = (selected || sources[0]).stackSize;
    const capacity = window.slots.slice(destStart, destEnd).reduce((sum, item) => sum + (!item ? stackSize : matches(item, itemType, metadata, nbt) ? stackSize - item.count : 0), 0);
    if (capacity < count) throw new Error(`Destination has insufficient room for ${count} items`);
    let firstSource = null;
    while (count > 0) {
      check(window, token);
      if (!window.selectedItem) {
        const source = window.slots.findIndex((item, index) => index >= sourceStart && index < sourceEnd && matches(item, itemType, metadata, nbt));
        if (source < 0) throw new Error('Transfer source changed while clicking');
        firstSource ??= source;
        await bot.clickWindow(source, count === Math.ceil(window.slots[source].count / 2) ? 1 : 0, 0);
      }
      check(window, token);
      const held = window.selectedItem;
      if (!held) throw new Error('Server did not provide the transfer cursor stack');
      let dest = window.slots.findIndex((item, index) => index >= destStart && index < destEnd && same(item, held) && item.count < item.stackSize);
      if (dest < 0) dest = window.slots.findIndex((item, index) => index >= destStart && index < destEnd && !item);
      if (dest < 0) throw new Error('Transfer destination became full');
      const amount = Math.min(held.count, held.stackSize - (window.slots[dest]?.count ?? 0));
      const left = amount <= count;
      await bot.clickWindow(dest, left ? 0 : 1, 0);
      count -= left ? amount : 1;
    }
    await returnCursor(sourceStart, sourceEnd, window, firstSource, token);
  });

  replace(bot, 'putAway', async (slot) => {
    const window = current();
    const token = generation;
    const item = window.slots[slot];
    if (!item) return;
    const start = window.inventoryStart;
    const end = window.inventoryEnd;
    const room = window.slots.slice(start, end).reduce((sum, value) => sum + (!value ? item.stackSize : same(item, value) ? item.stackSize - value.count : 0), 0);
    if (room < item.count) throw new Error('No room to put away the item');
    const result = slot === window.craftingResultSlot || (window.type === 'minecraft:merchant' && slot === 2);
    await bot.clickWindow(slot, 0, result ? 0 : 1);
    await returnCursor(start, end, window, null, token);
  });
  replace(bot, 'moveSlotItem', async (source, dest) => {
    const window = current();
    const token = generation;
    await bot.clickWindow(source, 0, 0);
    check(window, token);
    await bot.clickWindow(dest, 0, 0);
    check(window, token);
    if (window.selectedItem) await bot.clickWindow(source, 0, 0);
    bot.updateHeldItem?.();
  });

  let patchedWindow;
  let windowCleanup = [];
  const restoreWindow = () => {
    windowCleanup.reverse().forEach((remove) => remove());
    windowCleanup = [];
    patchedWindow = undefined;
  };
  const rebindWindow = (window) => {
    if (!window || patchedWindow === window) return window;
    restoreWindow();
    patchedWindow = window;
    for (const name of ['deposit', 'withdraw']) {
      if (typeof window[name] !== 'function') continue;
      const original = window[name];
      const wrapped = async (itemType, metadata, count, nbt) => {
        const deposit = name === 'deposit';
        return bot.transfer({ window, itemType, metadata, count, nbt,
          sourceStart: deposit ? window.inventoryStart : 0, sourceEnd: deposit ? window.inventoryEnd : window.inventoryStart,
          destStart: deposit ? 0 : window.inventoryStart, destEnd: deposit ? window.inventoryStart : window.inventoryEnd });
      };
      window[name] = wrapped;
      windowCleanup.push(() => { if (window[name] === wrapped) window[name] = original; });
    }
    return window;
  };
  for (const name of ['openBlock', 'openEntity']) {
    if (typeof bot[name] !== 'function') continue;
    const original = bot[name];
    replace(bot, name, async function (...args) { return rebindWindow(await original.apply(this, args)); });
  }
  rebindWindow(bot.currentWindow);
  bot.on('windowClose', restoreWindow);
  cleanup.push(() => { bot.removeListener('windowClose', restoreWindow); restoreWindow(); });

  // A partial vanilla toss uses one outside right-click per item. Assemble the
  // requested batch first, then make one real outside left-click for that stack.
  const tossBatch = async (itemType, metadata, count, nbt, window, start, end, token) => {
    if (window.selectedItem) throw new Error('Clear the cursor before tossing a batch');
    const sources = window.slots.slice(start, end).filter((item) => matches(item, itemType, metadata, nbt));
    if (sources.reduce((sum, item) => sum + item.count, 0) < count) throw new Error(`Not enough matching items to toss ${count}`);
    const stackSize = sources[0].stackSize;
    const needsAssembly = !sources.some((item) => item.count === Math.min(count, stackSize));
    if (needsAssembly && window.slots.slice(window.inventoryStart, window.inventoryEnd).every(Boolean)) {
      throw new Error('Batch toss requires one empty inventory slot; no items were dropped');
    }
    while (count > 0) {
      check(window, token);
      const batch = Math.min(count, stackSize);
      let slot = window.slots.findIndex((item, index) => index >= start && index < end && matches(item, itemType, metadata, nbt) && item.count === batch);
      if (slot < 0) {
        slot = window.slots.findIndex((item, index) => index >= window.inventoryStart && index < window.inventoryEnd && !item);
        if (slot < 0) throw new Error('Batch toss requires one empty inventory slot; remaining items were not dropped');
        let needed = batch;
        while (needed > 0) {
          check(window, token);
          const source = window.slots.findIndex((item, index) => index !== slot && index >= start && index < end && matches(item, itemType, metadata, nbt));
          if (source < 0) throw new Error('Toss source changed while assembling the batch');
          const amount = Math.min(needed, window.slots[source].count);
          await bot.transfer({ window, itemType, metadata, nbt, count: amount,
            sourceStart: source, sourceEnd: source + 1, destStart: slot, destEnd: slot + 1 });
          needed -= amount;
        }
      }
      check(window, token);
      await bot.clickWindow(slot, 0, 0);
      check(window, token);
      await bot.clickWindow(-999, 0, 0);
      count -= batch;
    }
  };
  if (typeof bot.toss === 'function') replace(bot, 'toss', (itemType, metadata, count = 1) => {
    const window = current();
    return bot.transfer({ window, itemType, metadata, count, sourceStart: window.inventoryStart,
      sourceEnd: window.inventoryEnd, destStart: -999 });
  });

  const cancelPending = () => {
    generation++;
    waiting.abort(new Error('Inventory actions cancelled'));
    waiting = new AbortController();
  };
  bot.on('windowClose', cancelPending);
  bot.on('end', cancelPending);
  cleanup.push(() => { bot.removeListener('windowClose', cancelPending); bot.removeListener('end', cancelPending); });
  const control = {
    intervalMs,
    cancelPending,
    dispose() {
      if (disposed) return;
      disposed = true;
      cancelPending();
      cleanup.reverse().forEach((remove) => remove());
      installed.delete(bot);
    },
  };
  installed.set(bot, control);
  return control;
}
