package dev.curio.observer.hud;

import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import java.util.List;

/** Short visual traces of real operations; never a replay queue for inventory snapshots. */
final class InventoryTransfers {
    private static final long DEPARTURE_MS = 450;
    record Departure(Protocol.ItemData item, float x, float y) {}

    static List<Departure> departures(ContainerLayout layout, BotState.Window window, long now) {
        if (!window.visible(now)) return List.of();
        return window.transfers().stream()
                .filter(transfer -> transfer.data().kind().equals("drop")
                        && now >= transfer.receivedAt() && now - transfer.receivedAt() < DEPARTURE_MS)
                .map(transfer -> {
                    var slot = layout.slots().stream().filter(s -> s.index() == transfer.data().slot()).findFirst().orElse(null);
                    float progress = (now - transfer.receivedAt()) / (float) DEPARTURE_MS;
                    float startX = slot == null ? layout.width() + 4 : slot.x();
                    float startY = slot == null ? layout.height() / 2.0F : slot.y();
                    return new Departure(transfer.data().item(),
                            startX + (layout.width() + 24 - startX) * progress,
                            startY + (layout.height() / 2.0F + 24 - startY) * progress);
                }).toList();
    }
}
