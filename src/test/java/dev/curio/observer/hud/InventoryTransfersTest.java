package dev.curio.observer.hud;

import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryTransfersTest {
    private final ContainerLayout layout = ContainerLayout.forWindow("inventory", 46);

    private BotState open() {
        return BotState.empty().apply(new Protocol.Hello(1, "Curio", "1.20.1", "test"), 1)
                .apply(new Protocol.WindowOpen(0, "inventory", "", Protocol.emptySlots(46)), 10);
    }

    private Protocol.InventoryTransfer transfer(String kind, int count) {
        return new Protocol.InventoryTransfer(0, 9, kind,
                new Protocol.ItemData("minecraft:oak_planks", count, 0, 0, false, null));
    }

    @Test void rendersOneDepartingStackPerActualDropWithExactCounts() {
        var state = open().apply(transfer("drop", 64), 100).apply(transfer("drop", 1), 110)
                .apply(transfer("put", 12), 120);
        var departures = InventoryTransfers.departures(layout, state.window(), 150);
        assertEquals(2, departures.size());
        assertEquals(64, departures.get(0).item().count());
        assertEquals(1, departures.get(1).item().count());
        assertEquals("put", state.window().transfer().data().kind());
        assertNull(state.window().slots().get(9), "visual events must not fabricate an inventory snapshot");
    }

    @Test void burstHistoryHasBothACountLimitAndATimeLimit() {
        var state = open();
        for (int i = 1; i <= 16; i++) state = state.apply(transfer("drop", i), 100 + i);
        assertEquals(BotState.Window.MAX_TRANSFERS, state.window().transfers().size());
        assertEquals(9, state.window().transfers().get(0).data().item().count());
        assertEquals(8, InventoryTransfers.departures(layout, state.window(), 150).size());
        assertTrue(InventoryTransfers.departures(layout, state.window(), 1000).isEmpty());
        state = state.apply(transfer("take", 3), 2000);
        assertEquals(1, state.window().transfers().size());
        assertEquals(3, state.window().transfer().data().item().count());
    }

    @Test void slotAndPointerUpdatesDoNotEraseOperationsButNewSessionsDo() {
        var state = open().apply(transfer("drop", 1), 100).apply(transfer("drop", 32), 110)
                .apply(new Protocol.Slot(0, 9, null), 120)
                .apply(new Protocol.Pointer(0, 10, 220, null), 130)
                .apply(new Protocol.Cursor(null), 140)
                .apply(new Protocol.WindowClose(0), 150);
        assertEquals(2, InventoryTransfers.departures(layout, state.window(), 200).size());
        assertTrue(InventoryTransfers.departures(layout, state.window(), 350).isEmpty());
        state = state.apply(new Protocol.WindowOpen(0, "inventory", "", Protocol.emptySlots(46)), 360);
        assertTrue(state.window().transfers().isEmpty());
        assertNull(state.window().transfer());
        assertNull(state.withConnection(BotState.Connection.DISCONNECTED, "test").window());
    }

    @Test void protocolAcceptsActualPickupAndPlacementCounts() {
        var pickup = (Protocol.InventoryTransfer) Protocol.parse("inventory_transfer",
                "{\"windowId\":0,\"slot\":9,\"kind\":\"take\",\"item\":{\"id\":\"minecraft:oak_planks\",\"count\":32}}");
        var placement = (Protocol.InventoryTransfer) Protocol.parse("inventory_transfer",
                "{\"windowId\":0,\"slot\":10,\"kind\":\"put\",\"item\":{\"id\":\"minecraft:oak_planks\",\"count\":1}}");
        assertEquals(32, pickup.item().count());
        assertEquals(1, placement.item().count());
    }
}
