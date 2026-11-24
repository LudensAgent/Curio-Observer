package dev.curio.observer.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** The version-one, read-only observer wire protocol. No Minecraft classes are used here. */
public final class Protocol {
    public static final int VERSION = 1;
    public static final int MAX_JSON_LENGTH = 262_144;
    public static final int MAX_SLOTS = 256;
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Set<String> EVENTS = Set.of("hello", "state", "window_open", "slot", "cursor", "pointer", "inventory_transfer", "window_close", "furnace", "enchanting", "anvil", "merchant", "brewing", "bot_status");
    private static final Set<String> STATUSES = Set.of("online", "offline", "dead");
    private static final Set<String> KINDS = Set.of("inventory", "crafting_table", "furnace", "enchanting_table", "anvil", "chest", "large_chest", "generic", "merchant", "brewing_stand", "hopper", "smithing", "grindstone", "stonecutter", "loom", "generic_3x3", "shulker_box", "cartography_table", "beacon", "blast_furnace", "smoker");
    public static final int ENCHANT_OPTIONS = 3;

    private Protocol() {}

    public sealed interface Event permits Hello, State, WindowOpen, Slot, Cursor, Pointer, InventoryTransfer, WindowClose, Furnace, Enchanting, Anvil, Merchant, Brewing, BotStatus {}
    public static final List<String> HUD_PANELS = List.of("vitals", "inventory", "view", "containers", "hands");
    public record ItemData(String id, int count, int damage, int maxDamage, boolean enchanted, String name, String nbt) {
        public ItemData(String id, int count, int damage, int maxDamage, boolean enchanted, String name) {
            this(id, count, damage, maxDamage, enchanted, name, null);
        }
        public ItemData {
            if (id == null || id.length() > 256 || !ITEM_ID.matcher(id).matches()) {
                throw invalid("Invalid item identifier");
            }
            range(count, 1, 9999, "item count");
            range(damage, 0, 10_000_000, "item damage");
            range(maxDamage, 0, 10_000_000, "maximum item damage");
            if (name != null && name.length() > 1024) throw invalid("Item name is too long");
            if (nbt != null && nbt.length() > 65_536) throw invalid("Item NBT is too long");
        }
    }
    public record Xp(int level, float progress) {
        public Xp { range(level, 0, 10_000_000, "experience level"); finiteRange(progress, 0, 1, "experience progress"); }
    }
    public record Hello(int protocol, String bot, String mcVersion, String agentVersion) implements Event {
        public Hello {
            range(protocol, 0, 65535, "protocol version");
            bounded(bot, 64, "bot name");
            bounded(mcVersion, 64, "Minecraft version");
            bounded(agentVersion, 128, "agent version");
        }
    }
    public record State(float health, float absorption, float maxHealth, int food, float saturation, int oxygen,
                        Xp xp, List<ItemData> armor, List<ItemData> hotbar, ItemData offhand,
                        int selected, List<ItemData> main) implements Event {
        public State {
            finiteRange(health, 0, 1024, "health");
            finiteRange(absorption, 0, 1024, "absorption");
            finiteRange(maxHealth, 0, 1024, "maximum health");
            range(food, 0, 20, "food");
            finiteRange(saturation, 0, 20, "saturation");
            range(oxygen, 0, 20, "oxygen");
            if (xp == null) throw invalid("Missing experience");
            armor = immutableSlots(armor, 4);
            hotbar = immutableSlots(hotbar, 9);
            main = immutableSlots(main, 27);
            range(selected, 0, 8, "selected hotbar slot");
        }
        public static State empty() {
            return new State(0, 0, 20, 0, 0, 20, new Xp(0, 0), emptySlots(4), emptySlots(9), null, 0, emptySlots(27));
        }
    }

    /** Raw server property values in game ticks; null means that property has not arrived yet. */
    public record FurnaceData(Integer burnTime, Integer fuelTime, Integer cookTime, Integer cookTimeTotal) {
        public FurnaceData {
            for (Integer value : new Integer[] {burnTime, fuelTime, cookTime, cookTimeTotal}) {
                if (value != null) range(value, 0, Integer.MAX_VALUE, "furnace time");
            }
        }
    }
    /** One enchanting-table button; null fields mean the server has not offered that value. */
    public record EnchantOption(Integer cost, Integer enchant, Integer level) {
        public static final EnchantOption EMPTY = new EnchantOption(null, null, null);
        public EnchantOption {
            if (cost != null) range(cost, 1, Integer.MAX_VALUE, "enchantment cost");
            if (enchant != null) range(enchant, 0, Integer.MAX_VALUE, "enchantment id");
            if (level != null) range(level, 1, Integer.MAX_VALUE, "enchantment level");
        }
        public boolean offered() { return cost != null; }
    }
    /** The three options top to bottom; chosen is the button the bot pressed until the server re-rolls them. */
    public record EnchantingData(List<EnchantOption> options, Integer chosen) {
        public static final EnchantingData EMPTY = new EnchantingData(Collections.nCopies(ENCHANT_OPTIONS, EnchantOption.EMPTY), null);
        public EnchantingData {
            if (options == null || options.size() != ENCHANT_OPTIONS) throw invalid("Invalid enchantment options");
            for (EnchantOption option : options) if (option == null) throw invalid("Invalid enchantment options");
            options = List.copyOf(options);
            if (chosen != null) range(chosen, 0, ENCHANT_OPTIONS - 1, "chosen enchantment");
        }
    }
    /** Anvil repair cost in levels (null until the server reports one) and the pending rename. */
    public record AnvilData(Integer cost, String name) {
        public static final AnvilData EMPTY = new AnvilData(null, null);
        public AnvilData {
            if (cost != null) range(cost, 0, Integer.MAX_VALUE, "anvil cost");
            if (name != null && name.length() > 64) throw invalid("Anvil name is too long");
        }
    }
    public record MerchantOffer(ItemData input1, ItemData input2, ItemData output, boolean disabled, int uses, int maxUses) {
        public MerchantOffer { range(uses, 0, Integer.MAX_VALUE, "trade uses"); range(maxUses, 0, Integer.MAX_VALUE, "maximum trade uses"); }
    }
    public record MerchantData(List<MerchantOffer> offers, Integer selected, Integer level, Integer experience,
                               Boolean regular, Boolean canRestock) {
        public MerchantData {
            if (offers == null || offers.size() > 256) throw invalid("Invalid trade count");
            offers = List.copyOf(offers);
            if (selected != null) range(selected, 0, 255, "selected trade");
            if (level != null) range(level, 0, Integer.MAX_VALUE, "villager level");
            if (experience != null) range(experience, 0, Integer.MAX_VALUE, "villager experience");
        }
    }
    public record BrewingData(Integer brewTime, Integer fuel) {
        public BrewingData {
            if (brewTime != null) range(brewTime, 0, Integer.MAX_VALUE, "brewing time");
            if (fuel != null) range(fuel, 0, 20, "brewing fuel");
        }
    }
    public record WindowOpen(int windowId, String kind, String title, List<ItemData> slots, FurnaceData furnace,
                             EnchantingData enchanting, AnvilData anvil, MerchantData merchant, BrewingData brewing) implements Event {
        public WindowOpen(int windowId, String kind, String title, List<ItemData> slots) {
            this(windowId, kind, title, slots, null, null, null, null, null);
        }
        public WindowOpen(int windowId, String kind, String title, List<ItemData> slots, FurnaceData furnace) {
            this(windowId, kind, title, slots, furnace, null, null, null, null);
        }
        public WindowOpen(int windowId, String kind, String title, List<ItemData> slots, FurnaceData furnace, EnchantingData enchanting, AnvilData anvil) {
            this(windowId, kind, title, slots, furnace, enchanting, anvil, null, null);
        }
        public WindowOpen {
            range(windowId, 0, Integer.MAX_VALUE, "window id");
            bounded(kind, 128, "window kind");
            kind = switch (kind) { case "generic_9x3" -> "chest"; case "generic_9x6" -> "large_chest"; default -> KINDS.contains(kind) ? kind : "generic"; };
            bounded(title, 256, "window title");
            slots = immutableSlots(slots, -1);
        }
    }
    /** A full snapshot of the currently known properties, rather than a partial update. */
    public record Furnace(int windowId, FurnaceData data) implements Event {
        public Furnace {
            range(windowId, 0, Integer.MAX_VALUE, "window id");
            if (data == null) throw invalid("Missing furnace properties");
        }
    }
    public record Enchanting(int windowId, EnchantingData data) implements Event {
        public Enchanting {
            range(windowId, 0, Integer.MAX_VALUE, "window id");
            if (data == null) throw invalid("Missing enchanting options");
        }
    }
    public record Anvil(int windowId, AnvilData data) implements Event {
        public Anvil {
            range(windowId, 0, Integer.MAX_VALUE, "window id");
            if (data == null) throw invalid("Missing anvil properties");
        }
    }
    public record Merchant(int windowId, MerchantData data) implements Event {
        public Merchant {
            range(windowId, 0, Integer.MAX_VALUE, "window id");
            if (data == null) throw invalid("Missing trade data");
        }
    }
    public record Brewing(int windowId, BrewingData data) implements Event {
        public Brewing {
            range(windowId, 0, Integer.MAX_VALUE, "window id");
            if (data == null) throw invalid("Missing brewing data");
        }
    }
    public record Slot(int windowId, int slot, ItemData item) implements Event {
        public Slot { range(windowId, 0, Integer.MAX_VALUE, "window id"); range(slot, 0, MAX_SLOTS - 1, "slot"); }
    }
    public record Cursor(ItemData item) implements Event {}
    public record Pointer(int windowId, int slot, int durationMs, ItemData item) implements Event {
        public Pointer {
            range(windowId, 0, Integer.MAX_VALUE, "window id");
            if (slot != -999) range(slot, 0, MAX_SLOTS - 1, "pointer slot");
            range(durationMs, 0, 10_000, "pointer duration");
        }
    }
    public record InventoryTransfer(int windowId, int slot, String kind, ItemData item) implements Event {
        public InventoryTransfer {
            range(windowId, 0, Integer.MAX_VALUE, "window id");
            if (slot != -999) range(slot, 0, MAX_SLOTS - 1, "transfer slot");
            if (!Set.of("take", "put", "drop", "quick_move", "craft").contains(kind) || item == null) throw invalid("Invalid transfer");
        }
    }
    public record WindowClose(int windowId) implements Event {
        public WindowClose { range(windowId, 0, Integer.MAX_VALUE, "window id"); }
    }
    public record BotStatus(String status) implements Event {
        public BotStatus { if (!STATUSES.contains(status)) throw invalid("Unknown bot status"); }
    }

    /** Unknown SSE event names are ignored so future optional events remain compatible. */
    public static Event parse(String event, String json) {
        if (event == null || !EVENTS.contains(event)) return null;
        if (json == null || json.length() > MAX_JSON_LENGTH) throw invalid("Observer event exceeds the size limit");
        try {
            validateDepth(json);
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            return switch (event) {
                case "hello" -> new Hello(integer(o, "protocol"), string(o, "bot", 64), string(o, "mcVersion", 64), string(o, "agentVersion", 128));
                case "state" -> {
                    JsonObject xp = object(o, "xp");
                    yield new State(number(o, "health"), optionalNumber(o, "absorption", 0), optionalNumber(o, "maxHealth", 20), integer(o, "food"), optionalNumber(o, "saturation", 0),
                            optionalInteger(o, "oxygen", 20),
                            new Xp(integer(xp, "level"), number(xp, "progress")), slots(o, "armor", 4), slots(o, "hotbar", 9), item(o.get("offhand")), integer(o, "selected"), slots(o, "main", 27));
                }
                case "window_open" -> new WindowOpen(integer(o, "windowId"), string(o, "kind", 128), string(o, "title", 256), slots(o, "slots", -1),
                        o.has("furnace") && !o.get("furnace").isJsonNull() ? furnace(object(o, "furnace")) : null,
                        o.has("enchanting") && !o.get("enchanting").isJsonNull() ? enchanting(object(o, "enchanting")) : null,
                        o.has("anvil") && !o.get("anvil").isJsonNull() ? anvil(object(o, "anvil")) : null,
                        o.has("merchant") && !o.get("merchant").isJsonNull() ? merchant(object(o, "merchant")) : null,
                        o.has("brewing") && !o.get("brewing").isJsonNull() ? brewing(object(o, "brewing")) : null);
                case "furnace" -> new Furnace(integer(o, "windowId"), furnace(o));
                case "enchanting" -> new Enchanting(integer(o, "windowId"), enchanting(o));
                case "anvil" -> new Anvil(integer(o, "windowId"), anvil(o));
                case "merchant" -> new Merchant(integer(o, "windowId"), merchant(o));
                case "brewing" -> new Brewing(integer(o, "windowId"), brewing(o));
                case "slot" -> new Slot(integer(o, "windowId"), integer(o, "slot"), item(required(o, "item")));
                case "cursor" -> new Cursor(item(required(o, "item")));
                case "pointer" -> new Pointer(integer(o, "windowId"), integer(o, "slot"), integer(o, "durationMs"), item(required(o, "item")));
                case "inventory_transfer" -> new InventoryTransfer(integer(o, "windowId"), integer(o, "slot"), string(o, "kind", 32), item(required(o, "item")));
                case "window_close" -> new WindowClose(integer(o, "windowId"));
                case "bot_status" -> new BotStatus(string(o, "status", 32));
                default -> null;
            };
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid observer " + event + " event: " + exception.getMessage(), exception);
        }
    }

    private static ItemData item(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        JsonObject o = element.getAsJsonObject();
        JsonElement enchanted = o.get("enchanted");
        if (enchanted != null && (!enchanted.isJsonPrimitive() || !enchanted.getAsJsonPrimitive().isBoolean())) throw invalid("Invalid enchanted flag");
        return new ItemData(string(o, "id", 256), optionalInteger(o, "count", 1), optionalInteger(o, "damage", 0), optionalInteger(o, "maxDamage", 0),
                enchanted != null && enchanted.getAsBoolean(), optionalString(o, "name", 1024), optionalString(o, "nbt", 65_536));
    }
    private static FurnaceData furnace(JsonObject o) {
        return new FurnaceData(nullableInteger(o, "burnTime"), nullableInteger(o, "fuelTime"),
                nullableInteger(o, "cookTime"), nullableInteger(o, "cookTimeTotal"));
    }
    private static EnchantingData enchanting(JsonObject o) {
        JsonArray array = required(o, "options").getAsJsonArray();
        if (array.size() != ENCHANT_OPTIONS) throw invalid("Invalid enchantment option count");
        List<EnchantOption> options = new ArrayList<>(ENCHANT_OPTIONS);
        for (JsonElement element : array) {
            JsonObject option = element.getAsJsonObject();
            options.add(new EnchantOption(nullableInteger(option, "cost"), nullableInteger(option, "enchant"), nullableInteger(option, "level")));
        }
        return new EnchantingData(options, nullableInteger(o, "chosen"));
    }
    private static AnvilData anvil(JsonObject o) {
        return new AnvilData(nullableInteger(o, "cost"), optionalString(o, "name", 64));
    }
    private static MerchantData merchant(JsonObject o) {
        JsonArray array = required(o, "offers").getAsJsonArray();
        if (array.size() > 256) throw invalid("Invalid trade count");
        List<MerchantOffer> offers = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            JsonObject offer = element.getAsJsonObject();
            offers.add(new MerchantOffer(item(required(offer, "input1")), item(required(offer, "input2")),
                    item(required(offer, "output")), bool(offer, "disabled"), integer(offer, "uses"), integer(offer, "maxUses")));
        }
        return new MerchantData(offers, nullableInteger(o, "selected"), nullableInteger(o, "level"), nullableInteger(o, "experience"),
                optionalBoolean(o, "regular"), optionalBoolean(o, "canRestock"));
    }
    private static BrewingData brewing(JsonObject o) {
        return new BrewingData(nullableInteger(o, "brewTime"), nullableInteger(o, "fuel"));
    }
    private static Integer nullableInteger(JsonObject o, String key) {
        return !o.has(key) || o.get(key).isJsonNull() ? null : integer(o, key);
    }
    private static boolean bool(JsonObject o, String key) {
        JsonElement value = required(o, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw invalid("Invalid " + key);
        return value.getAsBoolean();
    }
    private static String optionalString(JsonObject o, String key, int max) {
        return !o.has(key) || o.get(key).isJsonNull() ? null : string(o, key, max);
    }
    private static Boolean optionalBoolean(JsonObject o, String key) {
        return !o.has(key) || o.get(key).isJsonNull() ? null : bool(o, key);
    }
    private static void validateDepth(String json) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char value = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (value == '\\') escaped = true;
                else if (value == '"') quoted = false;
            } else if (value == '"') quoted = true;
            else if (value == '{' || value == '[') { if (++depth > 32) throw invalid("Observer JSON is nested too deeply"); }
            else if (value == '}' || value == ']') depth--;
        }
    }
    private static List<ItemData> slots(JsonObject o, String key, int expected) {
        JsonArray array = required(o, key).getAsJsonArray();
        if (array.size() > MAX_SLOTS || expected >= 0 && array.size() != expected) throw invalid("Invalid " + key + " slot count");
        List<ItemData> values = new ArrayList<>(array.size());
        for (JsonElement value : array) values.add(item(value));
        return values;
    }
    public static List<ItemData> emptySlots(int count) { return Collections.nCopies(count, null); }
    private static List<ItemData> immutableSlots(List<ItemData> slots, int expected) {
        if (slots == null || slots.size() > MAX_SLOTS || expected >= 0 && slots.size() != expected) throw invalid("Invalid slot count");
        return Collections.unmodifiableList(new ArrayList<>(slots));
    }
    private static JsonElement required(JsonObject o, String key) {
        if (!o.has(key)) throw invalid("Missing " + key);
        return o.get(key);
    }
    private static JsonObject object(JsonObject o, String key) { return required(o, key).getAsJsonObject(); }
    private static int integer(JsonObject o, String key) {
        JsonElement value = required(o, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("Invalid " + key);
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw invalid("Invalid " + key);
        return (int) number;
    }
    private static int optionalInteger(JsonObject o, String key, int fallback) { return o.has(key) ? integer(o, key) : fallback; }
    private static float number(JsonObject o, String key) {
        JsonElement value = required(o, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("Invalid " + key);
        float number = value.getAsFloat();
        if (!Float.isFinite(number)) throw invalid("Invalid " + key);
        return number;
    }
    private static float optionalNumber(JsonObject o, String key, float fallback) { return o.has(key) ? number(o, key) : fallback; }
    private static String string(JsonObject o, String key, int max) {
        JsonElement value = required(o, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("Invalid " + key);
        String result = value.getAsString();
        bounded(result, max, key);
        return result;
    }
    private static void bounded(String value, int max, String name) { if (value == null || value.length() > max) throw invalid("Invalid " + name); }
    private static void range(int value, int min, int max, String name) { if (value < min || value > max) throw invalid("Invalid " + name); }
    private static void finiteRange(float value, float min, float max, String name) { if (!Float.isFinite(value) || value < min || value > max) throw invalid("Invalid " + name); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
