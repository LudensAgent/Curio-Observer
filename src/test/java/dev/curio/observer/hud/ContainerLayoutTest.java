package dev.curio.observer.hud;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class ContainerLayoutTest {
    @Test void inventoryKeepsCraftingArmorOffhandAndHotbarAtNativeCoordinates() {
        var layout = ContainerLayout.forWindow("inventory", 46);
        assertEquals(46, layout.slots().size());
        assertSlot(layout, 0, 154, 28, -1);
        assertSlot(layout, 1, 98, 18, -1);
        assertSlot(layout, 4, 116, 36, -1);
        assertSlot(layout, 5, 8, 8, 5);
        assertSlot(layout, 8, 8, 62, 8);
        assertSlot(layout, 9, 8, 84, 9);
        assertSlot(layout, 36, 8, 142, 36);
        assertSlot(layout, 44, 152, 142, 44);
        assertSlot(layout, 45, 77, 62, 45);
    }

    @Test void tableAndFurnaceOffsetPlayerSlotsAfterTheirOwnSlots() {
        var table = ContainerLayout.forWindow("crafting_table", 46);
        assertEquals(240, table.width());
        assertEquals(64, table.backgroundX());
        assertEquals(92, table.titleX());
        assertEquals(72, table.inventoryX());
        assertSlot(table, 0, 188, 35, -1);
        assertSlot(table, 1, 94, 17, -1);
        assertSlot(table, 9, 130, 53, -1);
        assertSlot(table, 10, 72, 84, 9);
        assertSlot(table, 37, 72, 142, 36);
        var furnace = ContainerLayout.forWindow("furnace", 39);
        assertSlot(furnace, 0, 56, 17, -1);
        assertSlot(furnace, 1, 56, 53, -1);
        assertSlot(furnace, 2, 116, 35, -1);
        assertSlot(furnace, 3, 8, 84, 9);
        assertSlot(furnace, 30, 8, 142, 36);
    }

    @Test void enchantingTableAndAnvilUseNativeWorkstationCoordinates() {
        var table = ContainerLayout.forWindow("enchanting_table", 38);
        assertEquals(ContainerLayout.Background.ENCHANTING_TABLE, table.background());
        assertEquals(176, table.width());
        assertEquals("container.enchant", table.titleKey());
        assertSlot(table, 0, 15, 47, -1);
        assertSlot(table, 1, 35, 47, -1);
        assertSlot(table, 2, 8, 84, 9);
        assertSlot(table, 29, 8, 142, 36);
        assertSlot(table, 37, 152, 142, 44);
        assertComplete(table, 38);
        assertNull(table.playerPreview());
        var anvil = ContainerLayout.forWindow("anvil", 39);
        assertEquals(ContainerLayout.Background.ANVIL, anvil.background());
        assertEquals(60, anvil.titleX());
        assertEquals("container.repair", anvil.titleKey());
        assertSlot(anvil, 0, 27, 47, -1);
        assertSlot(anvil, 1, 76, 47, -1);
        assertSlot(anvil, 2, 134, 47, -1);
        assertSlot(anvil, 3, 8, 84, 9);
        assertSlot(anvil, 38, 152, 142, 44);
        assertComplete(anvil, 39);
        assertNull(anvil.playerPreview());
    }

    @Test void workstationSlotsFollowMinecraft1201ScreenHandlers() {
        var brewing = ContainerLayout.forWindow("brewing_stand", 41);
        assertSlot(brewing, 0, 56, 51, -1);
        assertSlot(brewing, 1, 79, 58, -1);
        assertSlot(brewing, 2, 102, 51, -1);
        assertSlot(brewing, 3, 79, 17, -1);
        assertSlot(brewing, 4, 17, 17, -1);
        assertSlot(brewing, 5, 8, 84, 9);
        assertComplete(brewing, 41);
        var hopper = ContainerLayout.forWindow("hopper", 41);
        assertEquals(133, hopper.height());
        assertSlot(hopper, 4, 116, 20, -1);
        assertSlot(hopper, 5, 8, 51, 9);
        assertComplete(hopper, 41);
        var smithing = ContainerLayout.forWindow("smithing", 40);
        assertSlot(smithing, 0, 8, 48, -1);
        assertSlot(smithing, 2, 44, 48, -1);
        assertSlot(smithing, 3, 98, 48, -1);
        assertComplete(smithing, 40);
        var grindstone = ContainerLayout.forWindow("grindstone", 39);
        assertSlot(grindstone, 0, 49, 19, -1);
        assertSlot(grindstone, 1, 49, 40, -1);
        assertSlot(grindstone, 2, 129, 34, -1);
        assertComplete(grindstone, 39);
        var stonecutter = ContainerLayout.forWindow("stonecutter", 38);
        assertSlot(stonecutter, 0, 20, 33, -1);
        assertSlot(stonecutter, 1, 143, 33, -1);
        assertComplete(stonecutter, 38);
        var loom = ContainerLayout.forWindow("loom", 40);
        assertSlot(loom, 0, 13, 26, -1);
        assertSlot(loom, 1, 33, 26, -1);
        assertSlot(loom, 2, 23, 45, -1);
        assertSlot(loom, 3, 143, 58, -1);
        assertComplete(loom, 40);
        var dispenser = ContainerLayout.forWindow("generic_3x3", 45);
        assertSlot(dispenser, 8, 98, 53, -1);
        assertComplete(dispenser, 45);
        var shulker = ContainerLayout.forWindow("shulker_box", 63);
        assertSlot(shulker, 27, 8, 84, 9);
        assertComplete(shulker, 63);
        var cartography = ContainerLayout.forWindow("cartography_table", 39);
        assertSlot(cartography, 0, 15, 15, -1);
        assertSlot(cartography, 1, 15, 52, -1);
        assertSlot(cartography, 2, 145, 39, -1);
        assertComplete(cartography, 39);
        var beacon = ContainerLayout.forWindow("beacon", 37);
        assertEquals(230, beacon.width());
        assertEquals(219, beacon.height());
        assertSlot(beacon, 0, 136, 110, -1);
        assertSlot(beacon, 1, 36, 137, 9);
        assertComplete(beacon, 37);
        var merchant = ContainerLayout.forWindow("merchant", 39);
        assertEquals(276, merchant.width());
        assertSlot(merchant, 0, 136, 37, -1);
        assertSlot(merchant, 1, 162, 37, -1);
        assertSlot(merchant, 2, 220, 37, -1);
        assertSlot(merchant, 3, 108, 84, 9);
        assertComplete(merchant, 39);
        assertEquals(ContainerLayout.Background.BLAST_FURNACE, ContainerLayout.forWindow("blast_furnace", 39).background());
        assertEquals(ContainerLayout.Background.SMOKER, ContainerLayout.forWindow("smoker", 39).background());
    }

    @Test void characterPreviewsStayInsideTheirPanelWithoutCoveringSlots() {
        for (String kind : new String[] { "inventory", "crafting_table" }) {
            var layout = ContainerLayout.forWindow(kind, 46);
            var preview = layout.playerPreview();
            assertNotNull(preview);
            assertTrue(preview.x() >= 0 && preview.y() >= 0);
            assertTrue(preview.x() + preview.width() <= layout.width());
            assertTrue(preview.y() + preview.height() <= layout.height());
            assertTrue(preview.centerX() > preview.x() && preview.centerX() < preview.x() + preview.width());
            assertTrue(preview.baseY() > preview.y() && preview.baseY() < preview.y() + preview.height());
            for (var slot : layout.slots()) {
                assertTrue(slot.x() + 16 <= preview.x() || slot.x() >= preview.x() + preview.width()
                        || slot.y() + 16 <= preview.y() || slot.y() >= preview.y() + preview.height(),
                        "Player preview covers slot " + slot.index());
            }
        }
        assertEquals(new ContainerLayout.Preview(26, 8, 50, 70, 51, 75, 30),
                ContainerLayout.forWindow("inventory", 46).playerPreview());
        assertNull(ContainerLayout.forWindow("furnace", 39).playerPreview());
        assertNull(ContainerLayout.forWindow("chest", 63).playerPreview());
    }

    @Test void chestRowsMoveThePlayerInventoryWithoutLosingAnyProtocolSlot() {
        var chest = ContainerLayout.forWindow("chest", 63);
        assertEquals(168, chest.height());
        assertSlot(chest, 26, 152, 54, -1);
        assertSlot(chest, 27, 8, 85, 9);
        assertSlot(chest, 54, 8, 143, 36);
        var large = ContainerLayout.forWindow("large_chest", 90);
        assertEquals(222, large.height());
        assertSlot(large, 53, 152, 108, -1);
        assertSlot(large, 54, 8, 139, 9);
        assertSlot(large, 81, 8, 197, 36);
        assertComplete(chest, 63);
        assertComplete(large, 90);
    }

    @Test void unknownSmallAndMaximumSizeWindowsRemainWithinTheirBackground() {
        for (int count : new int[] { 0, 5, 41, 45, 72, 256 }) {
            var layout = ContainerLayout.forWindow("unrecognized", count);
            int expected = count < 36 ? count + 36 : count;
            assertComplete(layout, expected);
            for (var slot : layout.slots()) {
                assertTrue(slot.x() >= 0 && slot.x() + 16 < layout.width());
                assertTrue(slot.y() >= 0 && slot.y() + 16 < layout.height());
            }
        }
        assertSlot(ContainerLayout.forWindow("generic", 41), 5, 8, 49, 9);
    }

    private static void assertComplete(ContainerLayout layout, int count) {
        var indices = new HashSet<Integer>();
        for (var slot : layout.slots()) assertTrue(indices.add(slot.index()), "Duplicate slot " + slot.index());
        assertEquals(count, indices.size());
        for (int index = 0; index < count; index++) assertTrue(indices.contains(index), "Missing slot " + index);
    }

    private static void assertSlot(ContainerLayout layout, int index, int x, int y, int playerSlot) {
        var actual = layout.slots().stream().filter(slot -> slot.index() == index).findFirst().orElseThrow();
        assertEquals(new ContainerLayout.Slot(index, x, y, playerSlot), actual);
    }
}
