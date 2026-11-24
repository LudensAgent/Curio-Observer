package dev.curio.observer.compat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curio.observer.config.ObserverConfig;
import dev.curio.observer.config.ObserverConfigScreen;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/** Core Minecraft compat layer for MC 1.21.11 (Identifier). */
public final class McCompatImpl {
    private static KeyMapping.Category KEY_CATEGORY;
    private McCompatImpl() {}

    public static String minecraftVersion() { return SharedConstants.getCurrentVersion().name(); }
    public static Component literal(String text) { return Component.literal(text); }
    public static Component translatable(String key, Object... args) { return Component.translatable(key, args); }
    public static String playerName(Player player) { return player.getGameProfile().name(); }
    public static String playerInfoName(PlayerInfo info) { return info.getProfile().name(); }
    public static Level entityLevel(Entity entity) { return entity.level(); }
    public static int selectedHotbarSlot(Player player) { return player.getInventory().getSelectedSlot(); }
    public static boolean isFood(ItemStack stack) { return stack.has(DataComponents.FOOD); }
    public static int armorProtection(ItemStack stack) {
        ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods == null) return 0;
        double sum = 0;
        for (var entry : mods.modifiers()) {
            if (entry.attribute().equals(Attributes.ARMOR)) sum += entry.modifier().amount();
        }
        return (int) Math.round(sum);
    }
    public static boolean isSubmergedInWater(Entity entity) { return entity.isEyeInFluid(FluidTags.WATER); }
    public static int guiTicks(Minecraft client) { return client.gui.getGuiTicks(); }
    public static KeyMapping registerKeyBinding(String key, InputConstants.Type type, int code, String category) {
        if (KEY_CATEGORY == null) KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("curio-observer", "main"));
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(key, type, code, KEY_CATEGORY));
    }
    public static void sendCommand(Minecraft client, String command) {
        if (client.getConnection() != null) client.getConnection().sendCommand(command);
    }
    public static void registerHudRenderer(BiConsumer<HudCanvas, Float> renderer) {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("curio-observer", "hud"),
                (graphics, deltaTracker) -> renderer.accept(new GuiGraphicsCanvas12111(graphics), deltaTracker.getGameTimeDeltaPartialTick(true)));
    }
    public static Screen createConfigScreen(Screen parent, ObserverConfig config, Runnable onSave, Supplier<Component> status) {
        return new ObserverConfigScreen(parent, config, onSave, status);
    }
    public static String enchantmentName(int rawId, int level) {
        var world = Minecraft.getInstance().level;
        if (world == null) return "";
        var reg = world.registryAccess().lookup(Registries.ENCHANTMENT).orElse(null);
        if (reg == null) return "";
        var holder = reg.get(rawId).orElse(null);
        return holder == null ? "" : Enchantment.getFullname(holder, level).getString();
    }
    public static Item itemById(String raw) {
        Identifier id = Identifier.tryParse(raw);
        return id != null && BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
    }
    public static String idOfItem(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
