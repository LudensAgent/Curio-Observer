package dev.curio.observer.state;

import dev.curio.observer.model.Protocol;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable feed snapshots, independent of Minecraft runtime classes. */
public record BotState(Protocol.Hello hello, Protocol.State state, Window window, Protocol.ItemData cursor,
                       String status, Connection connection, String message, Actions actions, boolean hasState) {
    public BotState(Protocol.Hello hello, Protocol.State state, Actions actions, boolean hasState) {
        this(hello, state, null, null, "online", Connection.CONNECTED, "", actions, hasState);
    }
    public enum Connection { DISCONNECTED, CONNECTING, CONNECTED, PROTOCOL_MISMATCH }
    public record Actions(String lastAction, long changedAt, long swingAt, boolean eating, long useAt) {
        public static Actions empty() { return new Actions("", 0, 0, false, 0); }
    }
    public record Furnace(Protocol.FurnaceData data, long receivedAt, boolean cooking, long cookReceivedAt,
                          long burnReceivedAt, Protocol.FurnaceData recipeBeforeChange) {
        public Furnace(Protocol.FurnaceData data, long receivedAt, boolean cooking) {
            this(data, receivedAt, cooking, receivedAt, receivedAt, null);
        }
        private Furnace update(Protocol.FurnaceData next, long now) {
            Protocol.FurnaceData priorRecipe = recipeBeforeChange == null ? data : recipeBeforeChange;
            boolean cookChanged = !Objects.equals(priorRecipe.cookTime(), next.cookTime())
                    || !Objects.equals(priorRecipe.cookTimeTotal(), next.cookTimeTotal());
            boolean advancing = recipeBeforeChange == null && now >= receivedAt && next.burnTime() != null && next.burnTime() > 0
                    && data.cookTime() != null && next.cookTime() != null && next.cookTime() > data.cookTime()
                    && next.cookTimeTotal() != null && next.cookTimeTotal() > 0
                    && Objects.equals(data.cookTimeTotal(), next.cookTimeTotal());
            boolean burning = next.burnTime() != null && next.burnTime() > 0;
            long burnAt = !Objects.equals(data.burnTime(), next.burnTime()) ? now : burnReceivedAt;
            Protocol.FurnaceData invalidatedRecipe = recipeBeforeChange != null && !cookChanged ? recipeBeforeChange : null;
            Protocol.FurnaceData visible = invalidatedRecipe == null ? next : withoutRecipe(next);
            return new Furnace(visible, now, cookChanged ? advancing : cooking && burning,
                    cookChanged ? now : cookReceivedAt, burnAt, invalidatedRecipe);
        }
        private Furnace slotChanged(boolean changedMaterial) {
            Protocol.FurnaceData invalidatedRecipe = changedMaterial && recipeBeforeChange == null ? data : recipeBeforeChange;
            return new Furnace(changedMaterial ? withoutRecipe(data) : data, receivedAt, false,
                    cookReceivedAt, burnReceivedAt, invalidatedRecipe);
        }
        private static Protocol.FurnaceData withoutRecipe(Protocol.FurnaceData data) {
            return new Protocol.FurnaceData(data.burnTime(), data.fuelTime(), null, null);
        }
    }
    /** Enchanting options plus the last button the bot pressed, kept after the server forgets it so the press stays visible. */
    public record Enchanting(Protocol.EnchantingData data, int lastChosen, long chosenAt) {
        public Enchanting(Protocol.EnchantingData data, long now) {
            this(data, data.chosen() == null ? -1 : data.chosen(), data.chosen() == null ? 0 : now);
        }
        private Enchanting update(Protocol.EnchantingData next, long now) {
            boolean pressed = next.chosen() != null && (data.chosen() == null || !next.chosen().equals(data.chosen()));
            return new Enchanting(next, pressed ? next.chosen() : lastChosen, pressed ? now : chosenAt);
        }
        /** Highlights the pressed button briefly even though the server re-rolls the options at once. */
        public boolean recentlyChosen(int option, long now) {
            return option == lastChosen && lastChosen >= 0 && now >= chosenAt && now - chosenAt < 2000;
        }
    }
    public record Pointer(Protocol.Pointer data, long receivedAt) {}
    public record Transfer(Protocol.InventoryTransfer data, long receivedAt) {}
    public record Window(int id, String kind, String title, List<Protocol.ItemData> slots,
                         Map<Integer, Long> changedAt, long openedAt, long closedAt,
                         int previousSlot, int lastSlot, long cursorMovedAt, Furnace furnace,
                         Enchanting enchanting, Protocol.AnvilData anvil, Protocol.MerchantData merchant, Protocol.BrewingData brewing,
                         Pointer pointer, Transfer transfer, List<Transfer> transfers) {
        public static final long TRANSFER_LIFETIME_MS = 1200;
        public static final int MAX_TRANSFERS = 8;
        public static final long CLOSE_FADE_MS = 200;
        public Window(int id, String kind, String title, List<Protocol.ItemData> slots,
                      Map<Integer, Long> changedAt, long openedAt, long closedAt,
                      int previousSlot, int lastSlot, long cursorMovedAt, Furnace furnace,
                      Enchanting enchanting, Protocol.AnvilData anvil, Protocol.MerchantData merchant, Protocol.BrewingData brewing,
                      Pointer pointer, Transfer transfer) {
            this(id, kind, title, slots, changedAt, openedAt, closedAt, previousSlot, lastSlot, cursorMovedAt,
                    furnace, enchanting, anvil, merchant, brewing, pointer, transfer,
                    transfer == null ? List.of() : List.of(transfer));
        }
        public Window(int id, String kind, String title, List<Protocol.ItemData> slots,
                      Map<Integer, Long> changedAt, long openedAt, long closedAt,
                      int previousSlot, int lastSlot, long cursorMovedAt, Furnace furnace,
                      Enchanting enchanting, Protocol.AnvilData anvil, Protocol.MerchantData merchant, Protocol.BrewingData brewing) {
            this(id, kind, title, slots, changedAt, openedAt, closedAt, previousSlot, lastSlot, cursorMovedAt,
                    furnace, enchanting, anvil, merchant, brewing, null, null);
        }
        public Window(int id, String kind, String title, List<Protocol.ItemData> slots,
                      Map<Integer, Long> changedAt, long openedAt, long closedAt,
                      int previousSlot, int lastSlot, long cursorMovedAt) {
            this(id, kind, title, slots, changedAt, openedAt, closedAt, previousSlot, lastSlot, cursorMovedAt, null, null, null, null, null);
        }
        public Window(int id, String kind, String title, List<Protocol.ItemData> slots,
                      Map<Integer, Long> changedAt, long openedAt, long closedAt,
                      int previousSlot, int lastSlot, long cursorMovedAt, Furnace furnace) {
            this(id, kind, title, slots, changedAt, openedAt, closedAt, previousSlot, lastSlot, cursorMovedAt, furnace, null, null, null, null);
        }
        public Window {
            slots = Collections.unmodifiableList(new ArrayList<>(slots));
            changedAt = Map.copyOf(changedAt);
            transfers = List.copyOf(transfers);
        }
        private Window with(List<Protocol.ItemData> slots, Map<Integer, Long> changedAt, int previousSlot, int lastSlot, long cursorMovedAt,
                            Furnace furnace, Enchanting enchanting, Protocol.AnvilData anvil) {
            return new Window(id, kind, title, slots, changedAt, openedAt, 0, previousSlot, lastSlot, cursorMovedAt, furnace, enchanting, anvil, merchant, brewing, pointer, transfer, transfers);
        }
        public boolean open() { return closedAt == 0; }
        public boolean visible(long now) { return open() || now - closedAt < CLOSE_FADE_MS; }
        public long slotChangedAt(int slot) { return changedAt.getOrDefault(slot, 0L); }
    }

    public static BotState empty() {
        return new BotState(null, Protocol.State.empty(), null, null, "offline",
                Connection.DISCONNECTED, "Agent disconnected", Actions.empty(), false);
    }

    /** Accepts Minecraft 1.18.2+ (including 1.19–1.21.x and year-based 26.x releases). */
    public static boolean isSupportedMcVersion(String version) {
        if (version == null || version.isBlank()) return false;
        String base = version.split("[-+ ]", 2)[0];
        String[] parts = base.split("\\.");
        if (parts.length < 2 || parts.length > 3) return false;
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
            if (major < 0 || minor < 0 || patch < 0) return false;
            if (major >= 26) return true;
            if (major == 1) return minor > 18 || (minor == 18 && patch >= 2);
            return false;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /** Retain only the bot identity on disconnect so camera-gated connection warnings remain visible. */
    public BotState withConnection(Connection value, String detail) {
        if (value == Connection.CONNECTED) return new BotState(hello, state, window, cursor, status, value, detail, actions, hasState);
        return new BotState(hello, Protocol.State.empty(), null, null,
                "offline", value, detail, Actions.empty(), false);
    }

    public BotState apply(Protocol.Event event, long now) {
        if (event == null) return this;
        if (event instanceof Protocol.Hello value) {
            boolean compatible = value.protocol() == Protocol.VERSION && isSupportedMcVersion(value.mcVersion());
            return new BotState(value, Protocol.State.empty(), null, null, "online",
                    compatible ? Connection.CONNECTED : Connection.PROTOCOL_MISMATCH,
                    compatible ? "Connected to " + value.bot() : "Observer protocol or Minecraft version mismatch", Actions.empty(), false);
        }
        if (!protocolCompatible() || connection != Connection.CONNECTED) return this;
        if (event instanceof Protocol.BotStatus value) {
            if (!"online".equals(value.status())) return new BotState(hello, Protocol.State.empty(), null, null,
                    value.status(), connection, value.status().equals("dead") ? "Bot is dead" : "Bot is offline", Actions.empty(), false);
            return new BotState(hello, state, window, cursor, value.status(), connection, "Connected to " + hello.bot(), actions, hasState);
        }
        if (!"online".equals(status)) return this;
        if (event instanceof Protocol.State value) return new BotState(hello, value, window, cursor, status, connection, message, actions, true);
        if (event instanceof Protocol.WindowOpen value) {
            Furnace furnace = List.of("furnace", "blast_furnace", "smoker").contains(value.kind()) && value.furnace() != null ? new Furnace(value.furnace(), now, false) : null;
            Enchanting enchanting = "enchanting_table".equals(value.kind())
                    ? new Enchanting(value.enchanting() == null ? Protocol.EnchantingData.EMPTY : value.enchanting(), now) : null;
            Protocol.AnvilData anvil = "anvil".equals(value.kind()) ? (value.anvil() == null ? Protocol.AnvilData.EMPTY : value.anvil()) : null;
            return new BotState(hello, state, new Window(value.windowId(), value.kind(), value.title(), value.slots(), Map.of(), now, 0, -1, -1, now, furnace, enchanting, anvil, value.merchant(), value.brewing()), null, status, connection, message, actions, hasState);
        }
        if (event instanceof Protocol.Enchanting value) {
            if (window == null || !window.open() || window.id() != value.windowId() || !"enchanting_table".equals(window.kind())) return this;
            Enchanting enchanting = window.enchanting() == null ? new Enchanting(value.data(), now) : window.enchanting().update(value.data(), now);
            Window next = window.with(window.slots(), window.changedAt(), window.previousSlot(), window.lastSlot(), window.cursorMovedAt(), window.furnace(), enchanting, window.anvil());
            return new BotState(hello, state, next, cursor, status, connection, message, actions, hasState);
        }
        if (event instanceof Protocol.Anvil value) {
            if (window == null || !window.open() || window.id() != value.windowId() || !"anvil".equals(window.kind())) return this;
            Window next = window.with(window.slots(), window.changedAt(), window.previousSlot(), window.lastSlot(), window.cursorMovedAt(), window.furnace(), window.enchanting(), value.data());
            return new BotState(hello, state, next, cursor, status, connection, message, actions, hasState);
        }
        if (event instanceof Protocol.Merchant value) {
            if (window == null || !window.open() || window.id() != value.windowId() || !"merchant".equals(window.kind())) return this;
            Window next = new Window(window.id(), window.kind(), window.title(), window.slots(), window.changedAt(), window.openedAt(), 0,
                    window.previousSlot(), window.lastSlot(), window.cursorMovedAt(), window.furnace(), window.enchanting(), window.anvil(), value.data(), window.brewing(), window.pointer(), window.transfer(), window.transfers());
            return new BotState(hello, state, next, cursor, status, connection, message, actions, hasState);
        }
        if (event instanceof Protocol.Brewing value) {
            if (window == null || !window.open() || window.id() != value.windowId() || !"brewing_stand".equals(window.kind())) return this;
            Window next = new Window(window.id(), window.kind(), window.title(), window.slots(), window.changedAt(), window.openedAt(), 0,
                    window.previousSlot(), window.lastSlot(), window.cursorMovedAt(), window.furnace(), window.enchanting(), window.anvil(), window.merchant(), value.data(), window.pointer(), window.transfer(), window.transfers());
            return new BotState(hello, state, next, cursor, status, connection, message, actions, hasState);
        }
        if (event instanceof Protocol.Furnace value) {
            if (window == null || !window.open() || window.id() != value.windowId() || !List.of("furnace", "blast_furnace", "smoker").contains(window.kind())) return this;
            Furnace furnace = window.furnace() == null ? new Furnace(value.data(), now, false) : window.furnace().update(value.data(), now);
            Window next = window.with(window.slots(), window.changedAt(), window.previousSlot(), window.lastSlot(), window.cursorMovedAt(),
                    furnace, window.enchanting(), window.anvil());
            return new BotState(hello, state, next, cursor, status, connection, message, actions, hasState);
        }
        if (event instanceof Protocol.Slot value) {
            if (window == null || !window.open() || window.id() != value.windowId() || value.slot() >= window.slots().size()) return this;
            List<Protocol.ItemData> slots = new ArrayList<>(window.slots());
            slots.set(value.slot(), value.item());
            Map<Integer, Long> changed = new HashMap<>(window.changedAt());
            changed.put(value.slot(), now);
            int previous = window.lastSlot() < 0 ? value.slot() : window.lastSlot();
            Furnace furnace = window.furnace();
            if (furnace != null && value.slot() <= 2 && !Objects.equals(window.slots().get(value.slot()), value.item())) {
                furnace = furnace.slotChanged(value.slot() == 0 && !sameMaterial(window.slots().get(0), value.item()));
            }
            Window next = window.with(slots, changed, previous, value.slot(), now, furnace, window.enchanting(), window.anvil());
            return new BotState(hello, state, next, cursor, status, connection, message, actions, hasState);
        }
        if (event instanceof Protocol.Pointer value) {
            if (window == null || !window.open() || window.id() != value.windowId() || value.slot() >= window.slots().size()) return this;
            Window next = new Window(window.id(), window.kind(), window.title(), window.slots(), window.changedAt(), window.openedAt(), 0,
                    window.previousSlot(), window.lastSlot(), window.cursorMovedAt(), window.furnace(), window.enchanting(), window.anvil(), window.merchant(), window.brewing(),
                    new Pointer(value, now), window.transfer(), window.transfers());
            return new BotState(hello, state, next, cursor, status, connection, message, actions, hasState);
        }
        if (event instanceof Protocol.InventoryTransfer value) {
            if (window == null || !window.open() || window.id() != value.windowId() || value.slot() >= window.slots().size()) return this;
            Transfer transfer = new Transfer(value, now);
            List<Transfer> transfers = new ArrayList<>(window.transfers().stream()
                    .filter(prior -> now - prior.receivedAt() < Window.TRANSFER_LIFETIME_MS)
                    .toList());
            transfers.add(transfer);
            if (transfers.size() > Window.MAX_TRANSFERS) transfers = transfers.subList(transfers.size() - Window.MAX_TRANSFERS, transfers.size());
            Window next = new Window(window.id(), window.kind(), window.title(), window.slots(), window.changedAt(), window.openedAt(), 0,
                    window.previousSlot(), window.lastSlot(), window.cursorMovedAt(), window.furnace(), window.enchanting(), window.anvil(), window.merchant(), window.brewing(),
                    window.pointer(), transfer, transfers);
            return new BotState(hello, state, next, cursor, status, connection, message, actions, hasState);
        }
        if (event instanceof Protocol.Cursor value) return new BotState(hello, state, window, value.item(), status, connection, message, actions, hasState);
        if (event instanceof Protocol.WindowClose value) {
            if (window == null || !window.open() || window.id() != value.windowId()) return this;
            Window closed = new Window(window.id(), window.kind(), window.title(), window.slots(), window.changedAt(), window.openedAt(), now,
                    window.previousSlot(), window.lastSlot(), window.cursorMovedAt(), null, window.enchanting(), window.anvil(), window.merchant(), window.brewing(), window.pointer(), window.transfer(), window.transfers());
            return new BotState(hello, state, closed, null, status, connection, message, actions, hasState);
        }
        return this;
    }

    public boolean protocolCompatible() { return hello != null && hello.protocol() == Protocol.VERSION && isSupportedMcVersion(hello.mcVersion()); }
    public boolean connected() { return connection == Connection.CONNECTED && protocolCompatible(); }
    public boolean renderable() { return connected() && hasState && "online".equals(status); }

    /** A single feed describes one bot; never attach its private inventory to a different player. */
    public boolean forPlayer(String name) {
        return connected() && "online".equals(status) && hello.bot().equals(name);
    }

    public float health() { return state.health(); }
    public float maxHealth() { return state.maxHealth(); }
    public float absorption() { return state.absorption(); }
    public int food() { return state.food(); }
    public float saturation() { return state.saturation(); }
    public int oxygen() { return state.oxygen(); }
    public Protocol.Xp xp() { return state.xp(); }
    public List<Protocol.ItemData> armor() { return state.armor(); }
    public List<Protocol.ItemData> hotbar() { return state.hotbar(); }
    public Protocol.ItemData offhand() { return state.offhand(); }
    public int selected() { return state.selected(); }
    public List<Protocol.ItemData> main() { return state.main(); }
    public Protocol.ItemData mainHand() { return hotbar().get(selected()); }
    private static boolean sameMaterial(Protocol.ItemData first, Protocol.ItemData second) {
        if (first == null || second == null) return first == second;
        return first.id().equals(second.id()) && first.damage() == second.damage()
                && first.enchanted() == second.enchanted() && Objects.equals(first.name(), second.name()) && Objects.equals(first.nbt(), second.nbt());
    }
}
