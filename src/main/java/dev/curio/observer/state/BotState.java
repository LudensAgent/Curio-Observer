package dev.curio.observer.state;

import dev.curio.observer.model.Protocol;
import dev.curio.observer.rcon.RconSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/** An immutable snapshot of the spectated player, built on the client thread each frame. */
public record BotState(Protocol.Hello hello, Protocol.State state, Actions actions, boolean hasState) {
    public record Actions(String lastAction, long changedAt, long swingAt, boolean eating, long useAt) {
        public static Actions empty() { return new Actions("", 0, 0, false, 0); }
    }

    public static BotState empty() {
        return new BotState(null, Protocol.State.empty(), Actions.empty(), false);
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
        Protocol.State state = rcon != null && rcon.forPlayer(name) && rcon.fresh(System.currentTimeMillis())
                ? new Protocol.State(health, absorption, rcon.food(), rcon.saturation(), 20,
                        new Protocol.Xp(rcon.xpLevel(), rcon.xpProgress()),
                        rcon.armor(), rcon.hotbar(), rcon.offhand(), rcon.selected(), rcon.main())
                : clientState(player, health, absorption);
        return new BotState(hello, state, Actions.empty(), true);
    }

    private static Protocol.State clientState(PlayerEntity player, float health, float absorption) {
        List<Protocol.ItemData> armor = List.of(
                ItemCodec.toData(player.getEquippedStack(EquipmentSlot.HEAD)),
                ItemCodec.toData(player.getEquippedStack(EquipmentSlot.CHEST)),
                ItemCodec.toData(player.getEquippedStack(EquipmentSlot.LEGS)),
                ItemCodec.toData(player.getEquippedStack(EquipmentSlot.FEET)));
        Protocol.ItemData offhand = ItemCodec.toData(player.getEquippedStack(EquipmentSlot.OFFHAND));
        Protocol.ItemData held = ItemCodec.toData(player.getEquippedStack(EquipmentSlot.MAINHAND));
        int selected = MathHelper.clamp(player.getInventory().selectedSlot, 0, 8);
        List<Protocol.ItemData> hotbar = new ArrayList<>(Protocol.emptySlots(9));
        hotbar.set(selected, held);
        return new Protocol.State(health, absorption,
                MathHelper.clamp(player.getHungerManager().getFoodLevel(), 0, 20),
                MathHelper.clamp(player.getHungerManager().getSaturationLevel(), 0, 20),
                20, new Protocol.Xp(player.experienceLevel, player.experienceProgress),
                armor, hotbar, offhand, selected, Protocol.emptySlots(27));
    }

    /** True when the camera follows a player whose data this client can render. */
    public boolean renderable() { return hasState; }

    public float health() { return state.health(); }
    public float maxHealth() { return Math.max(20, health()); }
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
}
