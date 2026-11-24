package dev.curio.observer.compat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curio.observer.config.ObserverConfig;
import dev.curio.observer.config.ObserverConfigScreen;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/** Core Minecraft compat layer for MC 1.20.2–1.20.4. */
public final class McCompatImpl {
    private McCompatImpl() {}

    public static String minecraftVersion() { return SharedConstants.getCurrentVersion().getName(); }
    public static Component literal(String text) { return Component.literal(text); }
    public static Component translatable(String key, Object... args) { return Component.translatable(key, args); }
    public static ResourceLocation mcResource(String path) { return new ResourceLocation("minecraft", path); }
    public static String playerName(Player player) { return player.getGameProfile().getName(); }
    public static String playerInfoName(PlayerInfo info) { return info.getProfile().getName(); }
    public static Level entityLevel(Entity entity) { return entity.level(); }
    public static int selectedHotbarSlot(Player player) { return player.getInventory().selected; }
    public static boolean isFood(ItemStack stack) { return stack.isEdible(); }
    public static int armorProtection(ItemStack stack) { return stack.getItem() instanceof ArmorItem a ? a.getDefense() : 0; }
    public static boolean isSubmergedInWater(Entity entity) { return entity.isEyeInFluid(FluidTags.WATER); }
    public static int guiTicks(Minecraft client) { return client.gui.getGuiTicks(); }
    public static KeyMapping registerKeyBinding(String key, InputConstants.Type type, int code, String category) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(key, type, code, category));
    }
    public static void sendCommand(Minecraft client, String command) {
        if (client.getConnection() != null) client.getConnection().sendCommand(command);
    }
    public static void registerHudRenderer(BiConsumer<HudCanvas, Float> renderer) {
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> renderer.accept(new GuiGraphicsCanvas1202(graphics), tickDelta));
    }
    public static Screen createConfigScreen(Screen parent, ObserverConfig config, Runnable onSave, Supplier<Component> status) {
        return new ObserverConfigScreen(parent, config, onSave, status);
    }
    public static String enchantmentName(int rawId, int level) {
        Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.byId(rawId);
        return enchantment == null ? "" : enchantment.getFullname(level).getString();
    }
    public static Item itemById(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return id != null && BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
    }
    public static String idOfItem(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
