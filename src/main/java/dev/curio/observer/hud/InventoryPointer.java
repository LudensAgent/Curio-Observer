package dev.curio.observer.hud;

import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;

/** Follows real click targets; slot updates (including result slots) never redirect a drag. */
final class InventoryPointer {
    record Frame(float x, float y, Protocol.ItemData item, boolean visible) {}
    private BotState.Pointer pointer;
    private long openedAt = -1;
    private int windowId = -1;
    private long startedAt;
    private long legacyMovement;
    private int duration;
    private float fromX, fromY, toX, toY;

    void reset() { windowId = -1; openedAt = -1; pointer = null; }

    Frame sample(ContainerLayout layout, BotState.Window window, Protocol.ItemData cursor, long now) {
        BotState.Pointer next = window.pointer();
        int target = next == null ? window.lastSlot() : next.data().slot();
        var slot = layout.slots().stream().filter(s -> s.index() == target).findFirst().orElse(layout.slots().get(0));
        float x = target == -999 ? layout.width() + 12 : slot.x() + 8;
        float y = target == -999 ? layout.height() / 2.0F : slot.y() + 8;
        if (windowId != window.id() || openedAt != window.openedAt()) {
            reset();
            windowId = window.id();
            openedAt = window.openedAt();
            fromX = toX = x;
            fromY = toY = y;
            startedAt = now;
            duration = 0;
            legacyMovement = -1;
        }
        if (next != pointer || next == null && legacyMovement != window.cursorMovedAt()) {
            float progress = progress(now);
            fromX += (toX - fromX) * progress;
            fromY += (toY - fromY) * progress;
            toX = x;
            toY = y;
            startedAt = next == null ? now : next.receivedAt();
            duration = next == null ? 160 : next.data().durationMs();
            pointer = next;
            legacyMovement = window.cursorMovedAt();
        }
        float progress = progress(now);
        // Retain the entire carried stack until it reaches the release point,
        // even if the acknowledgement arrives between two render frames.
        Protocol.ItemData item = next != null && progress < 1 ? next.data().item() : cursor;
        return new Frame(fromX + (toX - fromX) * progress, fromY + (toY - fromY) * progress,
                item, next != null || cursor != null);
    }

    private float progress(long now) {
        float linear = duration == 0 ? 1 : Math.max(0, Math.min(1, (now - startedAt) / (float) duration));
        return linear * linear * (3 - 2 * linear);
    }
}
