package dev.curio.observer.compat;

import dev.curio.observer.model.Protocol;
import dev.curio.observer.rcon.SnbtParser;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** ItemStack codec using DataComponents for MC 1.20.5–26.3, with legacy SNBT migration for older feeds. */
public final class ItemCodec {
    private static final int CACHE_SIZE = 512;
    private static final Map<Protocol.ItemData, ItemStack> CACHE = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Protocol.ItemData, ItemStack> entry) { return size() > CACHE_SIZE; }
    };

    private ItemCodec() {}

    public static ItemStack stack(Protocol.ItemData data) {
        if (data == null) return ItemStack.EMPTY;
        return CACHE.computeIfAbsent(data, ItemCodec::decode).copy();
    }

    public static void clear() { CACHE.clear(); }

    public static Protocol.ItemData toData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String id = McCompatImpl.idOfItem(stack.getItem());
        String name = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : null;
        boolean enchanted = stack.isEnchanted() || Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        int damage = stack.isDamageableItem() ? stack.getDamageValue() : 0;
        int maxDamage = stack.isDamageableItem() ? stack.getMaxDamage() : 0;
        return new Protocol.ItemData(id, stack.getCount(), damage, maxDamage, enchanted, name, null);
    }

    private static ItemStack decode(Protocol.ItemData data) {
        Item item = McCompatImpl.itemById(data.id());
        if (item == null) item = Items.BARRIER;
        ItemStack stack = new ItemStack(item, Math.min(data.count(), 9999));

        int damage = data.damage();
        boolean enchanted = data.enchanted();
        String customName = data.name();

        if (data.nbt() != null) {
            try {
                Map<String, Object> tag = SnbtParser.parseCompound(data.nbt());
                if (tag.containsKey("Damage")) damage = Math.max(0, SnbtParser.asInt(tag.get("Damage")));
                if (!SnbtParser.asList(tag.get("Enchantments")).isEmpty()
                        || !SnbtParser.asList(tag.get("StoredEnchantments")).isEmpty()) {
                    enchanted = true;
                }
                Map<String, Object> display = SnbtParser.asCompound(tag.get("display"));
                if ((customName == null || customName.isBlank()) && display.containsKey("Name")) {
                    customName = SnbtParser.extractPlainText(display.get("Name"));
                }
            } catch (RuntimeException ignored) {
                // Keep basic item visible when optional legacy SNBT is malformed.
            }
        }

        if (stack.isDamageableItem() && damage > 0) {
            stack.setDamageValue(Math.min(damage, stack.getMaxDamage()));
        }
        if (customName != null && !customName.isBlank()) {
            stack.set(DataComponents.CUSTOM_NAME, McCompatImpl.literal(customName));
        }
        if (enchanted) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }
}
