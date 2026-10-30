package dev.curio.observer.rcon;

import dev.curio.observer.model.Protocol;
import java.util.List;

/** The latest RCON poll for the spectated player, or empty when RCON is off, unreachable or stale. */
public record RconSnapshot(String playerName, long receivedAt,
                           List<Protocol.ItemData> armor, List<Protocol.ItemData> hotbar, Protocol.ItemData offhand,
                           List<Protocol.ItemData> main, int selected, int food, float saturation,
                           int xpLevel, float xpProgress) {
    private static final long STALE_MS = 5_000;

    public static RconSnapshot empty() {
        return new RconSnapshot("", 0, Protocol.emptySlots(4), Protocol.emptySlots(9), null,
                Protocol.emptySlots(27), 0, 0, 0, 0, 0);
    }

    public boolean fresh(long now) { return receivedAt > 0 && now - receivedAt < STALE_MS; }
    public boolean forPlayer(String name) { return name != null && name.equals(playerName); }
}
