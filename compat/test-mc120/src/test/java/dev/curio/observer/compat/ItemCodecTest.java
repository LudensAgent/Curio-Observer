package dev.curio.observer.compat;

import static org.junit.jupiter.api.Assertions.*;

import dev.curio.observer.model.Protocol;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ItemCodecTest {
    @BeforeAll static void bootstrapRegistries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void unknownItemsUseBarrierWithoutThrowing() {
        assertTrue(ItemCodec.stack(new Protocol.ItemData("unknown:missing_item", 1, 0, 0, false, null)).is(Items.BARRIER));
        assertTrue(ItemCodec.stack(null).isEmpty());
    }

    @Test void preservesDamageNamesGlintAndProtectsCachedTemplates() {
        var data = new Protocol.ItemData("minecraft:stone_pickaxe", 1, 12, 131, true, "Curio's tool");
        var first = ItemCodec.stack(data);
        assertEquals(12, first.getDamageValue());
        assertEquals("Curio's tool", first.getHoverName().getString());
        assertTrue(first.hasFoil());
        first.setCount(7);
        assertEquals(1, ItemCodec.stack(data).getCount());
        ItemCodec.clear();
    }

    @Test void preservesRealEnchantmentsAndNestedItemTags() {
        var data = new Protocol.ItemData("minecraft:enchanted_book", 1, 0, 0, true, null,
                "{StoredEnchantments:[{id:\"minecraft:mending\",lvl:1s}],display:{Lore:['{\"text\":\"Trade reward\"}']}}");
        var book = ItemCodec.stack(data);
        assertTrue(book.hasFoil());
        assertEquals("minecraft:mending", book.getTag().getList("StoredEnchantments", 10).getCompound(0).getString("id"));
        assertFalse(book.getTag().contains("Enchantments"), "A real book must not gain a fake Unbreaking enchantment");
        assertEquals("{\"text\":\"Trade reward\"}", book.getTag().getCompound("display").getList("Lore", 8).getString(0));
        var roundTrip = ItemCodec.stack(ItemCodec.toData(book));
        assertEquals(book.getTag(), roundTrip.getTag());
        book.getTag().remove("StoredEnchantments");
        assertTrue(ItemCodec.stack(data).getTag().contains("StoredEnchantments"));
    }

    @Test void preservesPotionAndDamageTagsAndHandlesMalformedOptionalNbt() {
        var potion = ItemCodec.stack(new Protocol.ItemData("minecraft:potion", 1, 0, 0, false, null,
                "{Potion:\"minecraft:strong_healing\",CustomPotionColor:16711680}"));
        assertEquals("minecraft:strong_healing", potion.getTag().getString("Potion"));
        var tool = ItemCodec.stack(new Protocol.ItemData("minecraft:iron_pickaxe", 1, 0, 250, false, null, "{Damage:42}"));
        assertEquals(42, tool.getDamageValue());
        var malformed = ItemCodec.stack(new Protocol.ItemData("minecraft:stone", 3, 0, 0, false, null, "{broken"));
        assertTrue(malformed.is(Items.STONE));
        assertEquals(3, malformed.getCount());
    }

    @Test void oversizedLocalTagsKeepTheItemVisible() {
        var stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.getOrCreateTag().putString("pages", "x".repeat(65_537));
        var data = ItemCodec.toData(stack);
        assertEquals("minecraft:written_book", data.id());
        assertNull(data.nbt());
        assertTrue(ItemCodec.stack(data).is(Items.WRITTEN_BOOK));
    }
}
