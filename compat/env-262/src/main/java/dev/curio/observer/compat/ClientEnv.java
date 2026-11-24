package dev.curio.observer.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;

/** Client environment bridge for MC 26.2 (Gui.screen/setScreen, Hud.isHidden, GameRenderer.mainCamera). */
public final class ClientEnv {
    private ClientEnv() {}

    public static InputConstants.Type keyboardType() { return InputConstants.Type.KEYSYM; }
    public static Screen currentScreen(Minecraft client) { return client.gui.screen(); }
    public static void setScreen(Minecraft client, Screen screen) { client.gui.setScreen(screen); }
    public static boolean isHudHidden(Minecraft client) { return client.gui.hud.isHidden(); }
    public static Camera mainCamera(Minecraft client) { return client.gameRenderer.mainCamera(); }
    public static float swingProgress(Player player, float tickDelta) { return player.getAttackAnim(tickDelta); }
}
