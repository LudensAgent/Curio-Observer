package dev.curio.observer.state;

import dev.curio.observer.model.Protocol;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Registry lookup and NBT construction are cached on the rendering thread. */
public final class ItemCodec {
    private static final int CACHE_SIZE = 512;
    private static final Map<Protocol.ItemData, ItemStack> CACHE = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Protocol.ItemData, ItemStack> entry) { return size() > CACHE_SIZE; }
    };

    private ItemCodec() {}

    /** Copies protect cached templates from vanilla's mutable ItemStack rendering helpers. */
    public static ItemStack stack(Protocol.ItemData data) {
        if (data == null) return ItemStack.EMPTY;
        return CACHE.computeIfAbsent(data, ItemCodec::decode).copy();
    }

    public static void clear() { CACHE.clear(); }

    /** Reads a live {@link ItemStack} from the spectated player into the HUD's immutable data shape. */
    public static Protocol.ItemData toData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Identifier id = Registries.ITEM.getId(stack.getItem());
        String name = stack.hasCustomName() ? stack.getName().getString() : null;
        boolean enchanted = stack.hasEnchantments();
        int damage = stack.isDamageable() ? stack.getDamage() : 0;
        int maxDamage = stack.isDamageable() ? stack.getMaxDamage() : 0;
        String nbt = stack.getNbt() == null ? null : stack.getNbt().toString();
        return new Protocol.ItemData(id.toString(), stack.getCount(), damage, maxDamage, enchanted, name,
                nbt != null && nbt.length() <= 65_536 ? nbt : null);
    }

    private static ItemStack decode(Protocol.ItemData data) {
        Identifier id = Identifier.tryParse(data.id());
        Item item = id != null && Registries.ITEM.containsId(id) ? Registries.ITEM.get(id) : Items.BARRIER;
        ItemStack stack = new ItemStack(item, Math.min(data.count(), 9999));
        boolean taggedDamage = false;
        if (data.nbt() != null) {
            try {
                var tag = StringNbtReader.parse(data.nbt());
                taggedDamage = tag.contains("Damage");
                stack.setNbt(tag);
            } catch (CommandSyntaxException ignored) {
                // Keep the item visible when an adapter supplies malformed optional tag data.
            }
        }
        if (stack.isDamageable() && !taggedDamage)
            stack.setDamage(Math.min(data.damage(), stack.getMaxDamage()));
        if (!stack.hasCustomName() && data.name() != null && !data.name().isBlank()) stack.setCustomName(Text.literal(data.name()));
        // Legacy feeds contain only a glint flag. Full NBT must never acquire a synthetic enchantment.
        if (data.nbt() == null && data.enchanted()) stack.addEnchantment(Enchantments.UNBREAKING, 1);
        return stack;
    }
}
