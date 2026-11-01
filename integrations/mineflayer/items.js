/** Encode Prismarine's typed NBT as Minecraft 1.20.1 SNBT without losing numeric tag types. */
export function snbt(tag) {
  if (!tag) return null;
  const quote = (value) => '"' + value.replaceAll('\\', '\\\\').replaceAll('"', '\\"') + '"';
  const long = (value) => typeof value === 'bigint' ? value.toString()
    : Array.isArray(value) ? BigInt.asIntN(64, (BigInt(value[0]) << 32n) | BigInt(value[1] >>> 0)).toString()
    : String(value);
  const encode = (type, value) => {
    switch (type) {
      case 'compound': return '{' + Object.entries(value).map(([key, entry]) => `${quote(key)}:${encode(entry.type, entry.value)}`).join(',') + '}';
      case 'list': return '[' + value.value.map((entry) => encode(value.type, entry)).join(',') + ']';
      case 'string': return quote(value);
      case 'byte': return `${value}b`;
      case 'short': return `${value}s`;
      case 'int': return String(value);
      case 'long': return `${long(value)}L`;
      case 'float': return `${value}f`;
      case 'double': return `${value}d`;
      case 'byteArray': return '[B;' + Array.from(value, (entry) => `${(entry << 24) >> 24}b`).join(',') + ']';
      case 'intArray': return '[I;' + Array.from(value).join(',') + ']';
      case 'longArray': return '[L;' + value.map((entry) => `${long(entry)}L`).join(',') + ']';
      default: throw new TypeError(`Unsupported NBT tag ${type}`);
    }
  };
  return encode(tag.type, tag.value);
}

export function text(value) {
  if (typeof value === 'string') {
    if (!value.startsWith('{') && !value.startsWith('[') && !value.startsWith('"')) return value;
    try { return text(JSON.parse(value)); } catch { return value; }
  }
  if (Array.isArray(value)) return value.map(text).join('');
  if (!value) return '';
  return (value.text ?? value.translate ?? '') + (value.extra ?? []).map(text).join('');
}

/** This boundary accepts Mineflayer Item objects, including their NBT-derived getters. */
export function itemData(item) {
  if (!item || item.count <= 0) return null;
  const rawId = typeof item.id === 'string' ? item.id : item.name;
  if (!rawId) return null;
  const tag = item.nbt;
  const encoded = tag ? snbt(tag) : null;
  return {
    id: rawId.includes(':') ? rawId : `minecraft:${rawId}`,
    count: item.count ?? 1,
    damage: item.durabilityUsed ?? item.damage ?? tag?.value?.Damage?.value ?? 0,
    maxDamage: item.maxDurability ?? item.maxDamage ?? 0,
    enchanted: item.enchanted === true || (item.enchants?.length ?? 0) > 0
      || (tag?.value?.Enchantments?.value?.value?.length ?? 0) > 0
      || (tag?.value?.StoredEnchantments?.value?.value?.length ?? 0) > 0,
    name: text(item.customName ?? tag?.value?.display?.value?.Name?.value).slice(0, 1024) || null,
    // The receiver has the same per-item bound. Oversized tags retain the ordinary item fields.
    nbt: encoded?.length <= 65_536 ? encoded : null,
  };
}

export function windowKind(type = '') {
  const kind = String(type).replace(/^minecraft:/, '');
  return ({ crafting: 'crafting_table', enchantment: 'enchanting_table', villager: 'merchant',
    generic_9x3: 'chest', generic_9x6: 'large_chest', cartography: 'cartography_table',
    barrel: 'chest', ender_chest: 'chest', dispenser: 'generic_3x3', dropper: 'generic_3x3' })[kind] ?? (kind || 'generic');
}
