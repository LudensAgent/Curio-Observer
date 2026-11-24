package dev.curio.observer.state;

import static org.junit.jupiter.api.Assertions.*;

import dev.curio.observer.model.Protocol;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObserverStateTest {
    private final Protocol.Hello hello = new Protocol.Hello(1, "Curio", "1.20.1", "test");
    private final Protocol.ItemData stone = new Protocol.ItemData("minecraft:stone", 3, 0, 0, false, null);

    @Test void privateWindowsRequireTheCorrectLiveBotIdentity() {
        var state = BotState.empty();
        assertSame(state, state.apply(new Protocol.WindowOpen(1, "chest", "Chest", Protocol.emptySlots(63)), 1));
        var ready = state.apply(hello, 2);
        assertTrue(ready.forPlayer("Curio"));
        assertFalse(ready.forPlayer("AnotherPlayer"));
        assertFalse(ready.withConnection(BotState.Connection.DISCONNECTED, "Lost").forPlayer("Curio"));
        assertFalse(ready.apply(new Protocol.BotStatus("dead"), 3).forPlayer("Curio"));
        var mismatch = ready.apply(new Protocol.Hello(2, "Curio", "1.20.1", "newer"), 4);
        assertFalse(mismatch.forPlayer("Curio"));
        assertEquals(BotState.Connection.PROTOCOL_MISMATCH, mismatch.connection());
        assertSame(mismatch, mismatch.apply(new Protocol.WindowOpen(1, "chest", "Chest", Protocol.emptySlots(63)), 5));
    }

    @Test void windowEventsAreImmutableScopedAndClearedOnCloseOrDisconnect() {
        var opened = BotState.empty().apply(hello, 1).apply(new Protocol.WindowOpen(3, "crafting_table", "Crafting", Protocol.emptySlots(46)), 100);
        assertSame(opened, opened.apply(new Protocol.Slot(9, 1, stone), 120));
        assertSame(opened, opened.apply(new Protocol.Slot(3, 99, stone), 120));
        var changed = opened.apply(new Protocol.Slot(3, 1, stone), 150).apply(new Protocol.Cursor(stone), 160).apply(new Protocol.Slot(3, 5, stone), 200);
        assertNull(opened.window().slots().get(1));
        assertEquals(stone, changed.window().slots().get(1));
        assertEquals(150, changed.window().slotChangedAt(1));
        assertThrows(UnsupportedOperationException.class, () -> changed.window().slots().set(0, stone));
        var closed = changed.apply(new Protocol.WindowClose(3), 300);
        assertTrue(closed.window().visible(499));
        assertFalse(closed.window().visible(500));
        assertNull(closed.cursor());
        assertSame(closed, closed.apply(new Protocol.Slot(3, 1, null), 350));
        var disconnected = changed.withConnection(BotState.Connection.DISCONNECTED, "Lost");
        assertNull(disconnected.window());
        assertNull(disconnected.cursor());
        assertFalse(disconnected.hasState());
        var fresh = changed.apply(new Protocol.Hello(1, "OtherBot", "1.20.1", "test"), 400);
        assertNull(fresh.window());
        assertFalse(fresh.forPlayer("Curio"));
    }

    @Test void merchantAndBrewingUpdatesFollowTheMatchingWindow() {
        var trades = new Protocol.MerchantData(List.of(new Protocol.MerchantOffer(stone, null, stone, false, 0, 12)), 0, 1, 0, true, true);
        var opened = BotState.empty().apply(hello, 1).apply(new Protocol.WindowOpen(5, "merchant", "", Protocol.emptySlots(39)), 10);
        assertSame(opened, opened.apply(new Protocol.Merchant(6, trades), 20));
        var updated = opened.apply(new Protocol.Merchant(5, trades), 20).apply(new Protocol.Slot(5, 0, stone), 30);
        assertEquals(trades, updated.window().merchant());
        var closed = updated.apply(new Protocol.WindowClose(5), 40);
        assertSame(closed, closed.apply(new Protocol.Merchant(5, trades), 50));
        var brewing = opened.apply(new Protocol.WindowOpen(6, "brewing_stand", "", Protocol.emptySlots(41)), 60)
                .apply(new Protocol.Brewing(6, new Protocol.BrewingData(200, 8)), 70);
        assertEquals(8, brewing.window().brewing().fuel());
        assertNull(brewing.window().merchant());
    }

    @Test void furnacePredictionRequiresObservedProgressAndStopsWhenInputChanges() {
        var opened = BotState.empty().apply(hello, 1).apply(new Protocol.WindowOpen(2, "furnace", "Furnace", Protocol.emptySlots(39),
                new Protocol.FurnaceData(100, 1600, 30, 200)), 100);
        assertFalse(opened.window().furnace().cooking());
        var progressing = opened.apply(new Protocol.Furnace(2, new Protocol.FurnaceData(90, 1600, 40, 200)), 600);
        assertTrue(progressing.window().furnace().cooking());
        var changed = progressing.apply(new Protocol.Slot(2, 0, stone), 610);
        assertFalse(changed.window().furnace().cooking());
        assertNull(changed.window().furnace().data().cookTimeTotal());
        var fuelOnly = changed.apply(new Protocol.Furnace(2, new Protocol.FurnaceData(80, 1600, 40, 200)), 1100);
        assertNull(fuelOnly.window().furnace().data().cookTimeTotal());
    }
}
