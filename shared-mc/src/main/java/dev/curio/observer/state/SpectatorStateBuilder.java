package dev.curio.observer.state;

import dev.curio.observer.compat.ClientEnv;
import dev.curio.observer.compat.ItemCodec;
import dev.curio.observer.compat.McCompatImpl;
import dev.curio.observer.model.Protocol;
import dev.curio.observer.rcon.RconSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/** Merges the actual spectated player entity with optional RCON and SSE BotState snapshots. */
public final class SpectatorStateBuilder {
    private SpectatorStateBuilder() {}

    public static BotState ofSpectator(Player player, RconSnapshot rcon, BotState feed) {
        BotState local = ofSpectator(player, rcon);
        if (feed == null || !feed.forPlayer(McCompatImpl.playerName(player))) return local;
        return new BotState(feed.hello(), feed.hasState() ? feed.state() : local.state(), feed.window(), feed.cursor(),
                "online", BotState.Connection.CONNECTED, feed.message(), local.actions(), true);
    }

    public static BotState ofSpectator(Player player, RconSnapshot rcon) {
        String name = McCompatImpl.playerName(player);
        Protocol.Hello hello = new Protocol.Hello(Protocol.VERSION, name, McCompatImpl.minecraftVersion(), "pure");
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

    private static BotState.Actions actionsOf(Player player, long now) {
        float swingProgress = ClientEnv.swingProgress(player, 1.0F);
        long swingAt = swingProgress > 0.0F && swingProgress < 1.0F ? now - (long) (swingProgress * 350.0F) : 0L;
        boolean using = player.isUsingItem();
        boolean eating = using && McCompatImpl.isFood(player.getUseItem());
        long changedAt = eating ? now - player.getTicksUsingItem() * 50L : 0L;
        long useAt = using ? now : 0L;
        return new BotState.Actions("", changedAt, swingAt, eating, useAt);
    }

    private static Protocol.State clientState(Player player, float health, float absorption, float maxHealth) {
        List<Protocol.ItemData> armor = Arrays.asList(
                ItemCodec.toData(player.getItemBySlot(EquipmentSlot.HEAD)),
                ItemCodec.toData(player.getItemBySlot(EquipmentSlot.CHEST)),
                ItemCodec.toData(player.getItemBySlot(EquipmentSlot.LEGS)),
                ItemCodec.toData(player.getItemBySlot(EquipmentSlot.FEET)));
        Protocol.ItemData offhand = ItemCodec.toData(player.getItemBySlot(EquipmentSlot.OFFHAND));
        Protocol.ItemData held = ItemCodec.toData(player.getItemBySlot(EquipmentSlot.MAINHAND));
        int selected = Mth.clamp(McCompatImpl.selectedHotbarSlot(player), 0, 8);
        List<Protocol.ItemData> hotbar = new ArrayList<>(Protocol.emptySlots(9));
        hotbar.set(selected, held);
        return new Protocol.State(health, absorption, maxHealth,
                Mth.clamp(player.getFoodData().getFoodLevel(), 0, 20),
                Mth.clamp(player.getFoodData().getSaturationLevel(), 0, 20),
                20, new Protocol.Xp(player.experienceLevel, player.experienceProgress),
                armor, hotbar, offhand, selected, Protocol.emptySlots(27));
    }
}
