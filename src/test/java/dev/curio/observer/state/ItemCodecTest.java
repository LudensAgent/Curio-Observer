package dev.curio.observer.state;

import static org.junit.jupiter.api.Assertions.*;

import dev.curio.observer.model.Protocol;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ItemCodecTest {
    @BeforeAll static void bootstrapRegistries() { SharedConstants.createGameVersion(); Bootstrap.initialize(); }

    @Test void unknownItemsUseBarrierWithoutThrowing() {
        assertTrue(ItemCodec.stack(new Protocol.ItemData("unknown:missing_item", 1, 0, 0, false, null)).isOf(Items.BARRIER));
        assertTrue(ItemCodec.stack(null).isEmpty());
    }

    @Test void preservesDamageNamesGlintAndProtectsCachedTemplates() {
        var data = new Protocol.ItemData("minecraft:stone_pickaxe", 1, 12, 131, true, "Curio's tool");
        var first = ItemCodec.stack(data);
        assertEquals(12, first.getDamage());
        assertEquals("Curio's tool", first.getName().getString());
        assertTrue(first.hasGlint());
        first.setCount(7);
        assertEquals(1, ItemCodec.stack(data).getCount());
        ItemCodec.clear();
    }

    @Test void preservesRealEnchantmentsAndNestedItemTags() {
        var data = new Protocol.ItemData("minecraft:enchanted_book", 1, 0, 0, true, null,
                "{StoredEnchantments:[{id:\"minecraft:mending\",lvl:1s}],display:{Lore:['{\"text\":\"Trade reward\"}']}}");
        var book = ItemCodec.stack(data);
        assertTrue(book.hasGlint());
        assertEquals("minecraft:mending", book.getNbt().getList("StoredEnchantments", 10).getCompound(0).getString("id"));
        assertFalse(book.getNbt().contains("Enchantments"), "A real book must not gain a fake Unbreaking enchantment");
        assertEquals("{\"text\":\"Trade reward\"}", book.getNbt().getCompound("display").getList("Lore", 8).getString(0));
        var roundTrip = ItemCodec.stack(ItemCodec.toData(book));
        assertEquals(book.getNbt(), roundTrip.getNbt());
        book.getNbt().remove("StoredEnchantments");
        assertTrue(ItemCodec.stack(data).getNbt().contains("StoredEnchantments"));
    }

    @Test void preservesPotionAndDamageTagsAndHandlesMalformedOptionalNbt() {
        var potion = ItemCodec.stack(new Protocol.ItemData("minecraft:potion", 1, 0, 0, false, null,
                "{Potion:\"minecraft:strong_healing\",CustomPotionColor:16711680}"));
        assertEquals("minecraft:strong_healing", potion.getNbt().getString("Potion"));
        var tool = ItemCodec.stack(new Protocol.ItemData("minecraft:iron_pickaxe", 1, 0, 250, false, null, "{Damage:42}"));
        assertEquals(42, tool.getDamage());
        var malformed = ItemCodec.stack(new Protocol.ItemData("minecraft:stone", 3, 0, 0, false, null, "{broken"));
        assertTrue(malformed.isOf(Items.STONE));
        assertEquals(3, malformed.getCount());
    }

    @Test void oversizedLocalTagsKeepTheItemVisible() {
        var stack = new net.minecraft.item.ItemStack(Items.WRITTEN_BOOK);
        stack.getOrCreateNbt().putString("pages", "x".repeat(65_537));
        var data = ItemCodec.toData(stack);
        assertEquals("minecraft:written_book", data.id());
        assertNull(data.nbt());
        assertTrue(ItemCodec.stack(data).isOf(Items.WRITTEN_BOOK));
    }
}
