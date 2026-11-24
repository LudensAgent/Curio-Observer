package dev.curio.observer.compat;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.curio.observer.model.Protocol;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/** Registry lookup and NBT construction for pre-1.20.5 Minecraft (1.18.2–1.20.4). */
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
        String name = stack.hasCustomHoverName() ? stack.getHoverName().getString() : null;
        boolean enchanted = stack.isEnchanted();
        int damage = stack.isDamageableItem() ? stack.getDamageValue() : 0;
        int maxDamage = stack.isDamageableItem() ? stack.getMaxDamage() : 0;
        CompoundTag tag = stack.getTag();
        String nbt = tag == null ? null : tag.toString();
        return new Protocol.ItemData(id, stack.getCount(), damage, maxDamage, enchanted, name,
                nbt != null && nbt.length() <= 65_536 ? nbt : null);
    }

    private static ItemStack decode(Protocol.ItemData data) {
        Item item = McCompatImpl.itemById(data.id());
        if (item == null) item = Items.BARRIER;
        ItemStack stack = new ItemStack(item, Math.min(data.count(), 9999));
        boolean taggedDamage = false;
        if (data.nbt() != null) {
            try {
                CompoundTag tag = TagParser.parseTag(data.nbt());
                taggedDamage = tag.contains("Damage");
                stack.setTag(tag);
            } catch (CommandSyntaxException ignored) {
            }
        }
        if (stack.isDamageableItem() && !taggedDamage)
            stack.setDamageValue(Math.min(data.damage(), stack.getMaxDamage()));
        if (!stack.hasCustomHoverName() && data.name() != null && !data.name().isBlank())
            stack.setHoverName(McCompatImpl.literal(data.name()));
        if (data.nbt() == null && data.enchanted())
            stack.enchant(Enchantments.UNBREAKING, 1);
        return stack;
    }
}
