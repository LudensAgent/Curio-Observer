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
}
