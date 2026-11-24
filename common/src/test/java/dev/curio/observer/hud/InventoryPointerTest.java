package dev.curio.observer.hud;

import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryPointerTest {
    private final Protocol.ItemData stack = new Protocol.ItemData("minecraft:oak_planks", 32, 0, 0, false, null);
    private final ContainerLayout layout = ContainerLayout.forWindow("inventory", 46);
    private BotState open() {
        return BotState.empty().apply(new Protocol.Hello(1, "Curio", "1.20.1", "test"), 1)
                .apply(new Protocol.WindowOpen(0, "inventory", "", Protocol.emptySlots(46)), 10);
    }

    @Test void dragKeepsTheStackUntilArrivalAndIgnoresUnrelatedSlotUpdates() {
        var pointer = new InventoryPointer();
        var state = open().apply(new Protocol.Pointer(0, 9, 300, null), 100);
        pointer.sample(layout, state.window(), null, 100);
        state = state.apply(new Protocol.Cursor(stack), 400).apply(new Protocol.Pointer(0, 36, 300, stack), 500);
        var start = pointer.sample(layout, state.window(), stack, 500);
        state = state.apply(new Protocol.Slot(0, 0, stack), 510).apply(new Protocol.Slot(0, 36, stack), 600)
                .apply(new Protocol.Cursor(null), 600);
        var halfway = pointer.sample(layout, state.window(), null, 650);
        var arriving = pointer.sample(layout, state.window(), null, 750);
        var end = pointer.sample(layout, state.window(), null, 800);
        var target = layout.slots().stream().filter(s -> s.index() == 36).findFirst().orElseThrow();
        assertEquals(stack, halfway.item());
        assertEquals(32, arriving.item().count());
        assertEquals((start.y() + end.y()) / 2, halfway.y(), 0.01);
        assertEquals(target.x() + 8, end.x());
        assertEquals(target.y() + 8, end.y());
        assertNull(end.item());
        assertTrue(end.visible());
    }

    @Test void outsideDropTargetsOutsideTheWindowAndDoesNotRepeatPerItem() {
        var pointer = new InventoryPointer();
        var state = open().apply(new Protocol.Pointer(0, 9, 0, null), 100);
        pointer.sample(layout, state.window(), null, 100);
        state = state.apply(new Protocol.Pointer(0, -999, 300, stack), 200);
        pointer.sample(layout, state.window(), stack, 200);
        var end = pointer.sample(layout, state.window(), null, 500);
        assertTrue(end.x() > layout.width());
        assertNull(end.item());
        assertEquals(end, pointer.sample(layout, state.window(), null, 1500));
    }

    @Test void transferSurvivesSlotAndCloseUpdatesButNotNewWindowsOrDisconnects() {
        var transfer = new Protocol.InventoryTransfer(0, 0, "craft", stack);
        var state = open().apply(transfer, 100).apply(new Protocol.Slot(0, 36, stack), 110)
                .apply(new Protocol.WindowClose(0), 200);
        assertEquals(32, state.window().transfer().data().item().count());
        assertSame(state, state.apply(transfer, 300));
        assertNull(state.apply(new Protocol.WindowOpen(0, "inventory", "", Protocol.emptySlots(46)), 400).window().transfer());
        assertNull(state.withConnection(BotState.Connection.DISCONNECTED, "test").window());
    }

    @Test void oldFeedsStillRenderTheirCursorWithoutPointerEvents() {
        var frame = new InventoryPointer().sample(layout, open().window(), stack, 100);
        assertTrue(frame.visible());
        assertEquals(stack, frame.item());
    }

    @Test void pickupApproachIsEmptyAndSingleItemDropLeavesTheRemainingCursorStack() {
        var pointer = new InventoryPointer();
        var state = open().apply(new Protocol.Pointer(0, 9, 220, null), 100);
        assertNull(pointer.sample(layout, state.window(), null, 210).item());
        state = state.apply(new Protocol.Cursor(stack), 320);
        assertEquals(32, pointer.sample(layout, state.window(), state.cursor(), 320).item().count());
        state = state.apply(new Protocol.Pointer(0, -999, 220, stack), 400);
        pointer.sample(layout, state.window(), stack, 400);
        var remainder = new Protocol.ItemData(stack.id(), 31, 0, 0, false, null);
        state = state.apply(new Protocol.Cursor(remainder), 620);
        assertEquals(32, pointer.sample(layout, state.window(), state.cursor(), 619).item().count());
        assertEquals(31, pointer.sample(layout, state.window(), state.cursor(), 620).item().count());
    }
}
