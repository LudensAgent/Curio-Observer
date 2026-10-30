package dev.curio.observer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Immutable HUD data shapes, read from the spectated player on the client. No wire format, no
 * Minecraft classes and no networking: this is a pure presentation model for the spectator HUD.
 */
public final class Protocol {
    public static final int VERSION = 1;
    public static final int MAX_SLOTS = 256;
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    /** Panels the spectator HUD can show, each independently toggleable. */
    public static final List<String> HUD_PANELS = List.of("vitals", "inventory", "view", "hands");

    private Protocol() {}

    public record ItemData(String id, int count, int damage, int maxDamage, boolean enchanted, String name) {
        public ItemData {
            if (id == null || id.length() > 256 || !ITEM_ID.matcher(id).matches()) {
                throw invalid("Invalid item identifier");
            }
            range(count, 1, 9999, "item count");
            range(damage, 0, 10_000_000, "item damage");
            range(maxDamage, 0, 10_000_000, "maximum item damage");
            if (name != null && name.length() > 1024) throw invalid("Item name is too long");
        }
    }
    public record Xp(int level, float progress) {
        public Xp { range(level, 0, 10_000_000, "experience level"); finiteRange(progress, 0, 1, "experience progress"); }
    }
    public record Hello(int protocol, String bot, String mcVersion, String agentVersion) {
        public Hello {
            range(protocol, 0, 65535, "protocol version");
            bounded(bot, 64, "bot name");
            bounded(mcVersion, 64, "Minecraft version");
            bounded(agentVersion, 128, "agent version");
        }
    }
    public record State(float health, float absorption, int food, float saturation, int oxygen, Xp xp,
                        List<ItemData> armor, List<ItemData> hotbar, ItemData offhand,
                        int selected, List<ItemData> main) {
        public State {
            finiteRange(health, 0, 1024, "health");
            finiteRange(absorption, 0, 1024, "absorption");
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
            return new State(0, 0, 0, 0, 20, new Xp(0, 0), emptySlots(4), emptySlots(9), null, 0, emptySlots(27));
        }
    }

    public static List<ItemData> emptySlots(int count) { return Collections.nCopies(count, null); }
    private static List<ItemData> immutableSlots(List<ItemData> slots, int expected) {
        if (slots == null || slots.size() > MAX_SLOTS || expected >= 0 && slots.size() != expected) throw invalid("Invalid slot count");
        return Collections.unmodifiableList(new ArrayList<>(slots));
    }
    private static void bounded(String value, int max, String name) { if (value == null || value.length() > max) throw invalid("Invalid " + name); }
    private static void range(int value, int min, int max, String name) { if (value < min || value > max) throw invalid("Invalid " + name); }
    private static void finiteRange(float value, float min, float max, String name) { if (!Float.isFinite(value) || value < min || value > max) throw invalid("Invalid " + name); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
