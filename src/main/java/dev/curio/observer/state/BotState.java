package dev.curio.observer.state;

import dev.curio.observer.model.Protocol;
import dev.curio.observer.rcon.RconSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/** Immutable feed snapshots, merged with the actual spectated player on the client thread. */
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
            // Each server property arrives separately. A fuel-only snapshot must neither stop cooking
            // nor refresh the deadline for predicting the last observed cook-time change.
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

    /** Retain only the bot identity on disconnect so camera-gated connection warnings remain visible. */
    public BotState withConnection(Connection value, String detail) {
        if (value == Connection.CONNECTED) return new BotState(hello, state, window, cursor, status, value, detail, actions, hasState);
        return new BotState(hello, Protocol.State.empty(), null, null,
                "offline", value, detail, Actions.empty(), false);
    }

    public BotState apply(Protocol.Event event, long now) {
        if (event == null) return this;
        if (event instanceof Protocol.Hello value) {
            boolean compatible = value.protocol() == Protocol.VERSION && "1.20.1".equals(value.mcVersion());
            return new BotState(value, Protocol.State.empty(), null, null, "online",
                    compatible ? Connection.CONNECTED : Connection.PROTOCOL_MISMATCH,
                    compatible ? "Connected to " + value.bot() : "Observer protocol or Minecraft version mismatch", Actions.empty(), false);
        }
        // An initial hello is mandatory. Incompatible sessions remain visible only as a status message.
        if (!protocolCompatible() || connection != Connection.CONNECTED) return this;
        if (event instanceof Protocol.BotStatus value) {
            if (!"online".equals(value.status())) return new BotState(hello, Protocol.State.empty(), null, null,
                    value.status(), connection, value.status().equals("dead") ? "Bot is dead" : "Bot is offline", Actions.empty(), false);
            return new BotState(hello, state, window, cursor, value.status(), connection, "Connected to " + hello.bot(), actions, hasState);
        }
        // Offline/dead bots can still send a final state packet; it must not revive stale rendering.
        if (!"online".equals(status)) return this;
        if (event instanceof Protocol.State value) return new BotState(hello, value, window, cursor, status, connection, message, actions, true);
        if (event instanceof Protocol.WindowOpen value) {
            Furnace furnace = List.of("furnace", "blast_furnace", "smoker").contains(value.kind()) && value.furnace() != null ? new Furnace(value.furnace(), now, false) : null;
            // Workstations always carry their panel so the overlay can show "no option yet" before the first property.
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
                // A material switch invalidates recipe properties; any furnace slot change stops prediction
                // until the server confirms that cooking is advancing again (including full output slots).
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
            // Keep actual operations that can arrive between frames, without delaying authoritative slots.
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

    public boolean protocolCompatible() { return hello != null && hello.protocol() == Protocol.VERSION && "1.20.1".equals(hello.mcVersion()); }
    public boolean connected() { return connection == Connection.CONNECTED && protocolCompatible(); }
    public boolean renderable() { return connected() && hasState && "online".equals(status); }

    /** A single feed describes one bot; never attach its private inventory to a different player. */
    public boolean forPlayer(String name) {
        return connected() && "online".equals(status) && hello.bot().equals(name);
    }

    public static BotState ofSpectator(PlayerEntity player, RconSnapshot rcon, BotState feed) {
        BotState local = ofSpectator(player, rcon);
        if (feed == null || !feed.forPlayer(player.getGameProfile().getName())) return local;
        return new BotState(feed.hello(), feed.hasState() ? feed.state() : local.state(), feed.window(), feed.cursor(),
                "online", Connection.CONNECTED, feed.message(), local.actions(), true);
    }

    /**
     * Builds the snapshot from the player the camera follows. When a fresh RCON reading for that player
     * exists, its authoritative inventory, hunger and experience replace the client-side view; otherwise
     * only the state this client receives directly is shown.
     */
    public static BotState ofSpectator(PlayerEntity player, RconSnapshot rcon) {
        String name = player.getGameProfile().getName();
        Protocol.Hello hello = new Protocol.Hello(Protocol.VERSION, name, "1.20.1", "pure");
        float health = player.getHealth();
        float absorption = player.getAbsorptionAmount();
        float maxHealth = player.getMaxHealth();
        long now = System.currentTimeMillis();
        Protocol.State state = rcon != null && rcon.forPlayer(name) && rcon.fresh(now)
                ? new Protocol.State(health, absorption, maxHealth, rcon.food(), rcon.saturation(), 20,
                        new Protocol.Xp(rcon.xpLevel(), rcon.xpProgress()),
                        rcon.armor(), rcon.hotbar(), rcon.offhand(), rcon.selected(), rcon.main())
                : clientState(player, health, absorption, maxHealth);
        return new BotState(hello, state, actionsOf(player, now), true);
    }

    /** Reads the spectated player's swing/eat/use timings that drive the first-person hand pass. */
    private static Actions actionsOf(PlayerEntity player, long now) {
        float swingProgress = player.getHandSwingProgress(1.0F);
        long swingAt = swingProgress > 0.0F && swingProgress < 1.0F ? now - (long) (swingProgress * 350.0F) : 0L;
        boolean using = player.isUsingItem();
        boolean eating = using && player.getActiveItem().isFood();
        long changedAt = eating ? now - player.getItemUseTime() * 50L : 0L;
        long useAt = using ? now : 0L;
        return new Actions("", changedAt, swingAt, eating, useAt);
    }

    private static Protocol.State clientState(PlayerEntity player, float health, float absorption, float maxHealth) {
        // Empty slots decode to null, so this must be a null-tolerant list (List.of rejects nulls).
        List<Protocol.ItemData> armor = Arrays.asList(
                ItemCodec.toData(player.getEquippedStack(EquipmentSlot.HEAD)),
                ItemCodec.toData(player.getEquippedStack(EquipmentSlot.CHEST)),
                ItemCodec.toData(player.getEquippedStack(EquipmentSlot.LEGS)),
                ItemCodec.toData(player.getEquippedStack(EquipmentSlot.FEET)));
        Protocol.ItemData offhand = ItemCodec.toData(player.getEquippedStack(EquipmentSlot.OFFHAND));
        Protocol.ItemData held = ItemCodec.toData(player.getEquippedStack(EquipmentSlot.MAINHAND));
        int selected = MathHelper.clamp(player.getInventory().selectedSlot, 0, 8);
        List<Protocol.ItemData> hotbar = new ArrayList<>(Protocol.emptySlots(9));
        hotbar.set(selected, held);
        return new Protocol.State(health, absorption, maxHealth,
                MathHelper.clamp(player.getHungerManager().getFoodLevel(), 0, 20),
                MathHelper.clamp(player.getHungerManager().getSaturationLevel(), 0, 20),
                20, new Protocol.Xp(player.experienceLevel, player.experienceProgress),
                armor, hotbar, offhand, selected, Protocol.emptySlots(27));
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
