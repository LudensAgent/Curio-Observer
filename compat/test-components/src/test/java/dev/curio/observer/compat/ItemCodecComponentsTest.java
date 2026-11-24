package dev.curio.observer.compat;

import static org.junit.jupiter.api.Assertions.*;

import dev.curio.observer.model.Protocol;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ItemCodecComponentsTest {
    @BeforeAll static void bootstrapRegistries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void unknownItemsUseBarrierAndPreserveDamageNameAndGlint() {
        assertTrue(ItemCodec.stack(new Protocol.ItemData("unknown:missing_item", 1, 0, 0, false, null)).is(Items.BARRIER));
        assertTrue(ItemCodec.stack(null).isEmpty());

        var data = new Protocol.ItemData("minecraft:stone_pickaxe", 1, 12, 131, true, "Curio's tool");
        var stack = ItemCodec.stack(data);
        assertTrue(stack.is(Items.STONE_PICKAXE));
        assertEquals(12, stack.getDamageValue());
        assertEquals("Curio's tool", stack.getHoverName().getString());
        assertTrue(stack.hasFoil());
        ItemCodec.clear();
    }
}
