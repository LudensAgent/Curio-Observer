package dev.curio.observer.rcon;

import static org.junit.jupiter.api.Assertions.*;

import dev.curio.observer.state.BotState;
import org.junit.jupiter.api.Test;

class RconSnbtTest {
    @Test void parsesPre1205TagInventoryAndModernComponentInventory() {
        String legacy = "Alex has the following entity data: {foodLevel:18,foodSaturationLevel:4.5f,XpLevel:7,XpProgress:0.25f,SelectedItemSlot:1,"
                + "Inventory:[{Slot:0b,id:\"minecraft:diamond_sword\",Count:1b,tag:{Damage:12,Enchantments:[{id:\"minecraft:sharpness\",lvl:3s}],display:{Name:'{\"text\":\"Blade\"}'}}},"
                + "{Slot:103b,id:\"minecraft:iron_helmet\",Count:1b},{Slot:-106b,id:\"minecraft:shield\",Count:1b}]}";
        RconSnapshot snap = RconLink.parse("Alex", legacy, 1000);
        assertNotNull(snap);
        assertEquals(18, snap.food());
        assertEquals(7, snap.xpLevel());
        assertEquals(1, snap.selected());
        assertEquals("minecraft:diamond_sword", snap.hotbar().get(0).id());
        assertEquals(12, snap.hotbar().get(0).damage());
        assertTrue(snap.hotbar().get(0).enchanted());
        assertEquals("Blade", snap.hotbar().get(0).name());
        assertEquals("minecraft:iron_helmet", snap.armor().get(0).id());
        assertEquals("minecraft:shield", snap.offhand().id());

        String modern = "Alex has the following entity data: {foodLevel:20,foodSaturationLevel:5.0f,XpLevel:10,XpProgress:0.5f,SelectedItemSlot:0,"
                + "Inventory:[{Slot:0b,id:\"minecraft:netherite_pickaxe\",count:1,components:{\"minecraft:damage\":5,\"minecraft:enchantments\":{\"minecraft:efficiency\":5},\"minecraft:custom_name\":{text:\"Digger\"}}}],"
                + "equipment:{head:{id:\"minecraft:diamond_helmet\",count:1},offhand:{id:\"minecraft:totem_of_undying\",count:1}}}";
        RconSnapshot modernSnap = RconLink.parse("Alex", modern, 1000);
        assertNotNull(modernSnap);
        assertEquals("minecraft:netherite_pickaxe", modernSnap.hotbar().get(0).id());
        assertEquals(5, modernSnap.hotbar().get(0).damage());
        assertTrue(modernSnap.hotbar().get(0).enchanted());
        assertEquals("Digger", modernSnap.hotbar().get(0).name());
        assertEquals("minecraft:diamond_helmet", modernSnap.armor().get(0).id());
        assertEquals("minecraft:totem_of_undying", modernSnap.offhand().id());
    }

    @Test void supportedMcVersionCovers1182Through26xAndRejectsOlder() {
        for (String v : new String[] { "1.18.2", "1.19", "1.19.4", "1.20.1", "1.20.6", "1.21.1", "1.21.11", "26.1", "26.1.2", "26.2", "26.3" }) {
            assertTrue(BotState.isSupportedMcVersion(v), "Expected supported: " + v);
        }
        for (String v : new String[] { null, "", "1.18.1", "1.17.1", "1.16.5", "not-a-version" }) {
            assertFalse(BotState.isSupportedMcVersion(v), "Expected unsupported: " + v);
        }
    }
}
