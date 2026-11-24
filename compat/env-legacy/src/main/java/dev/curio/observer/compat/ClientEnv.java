package dev.curio.observer.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;

/** Client environment bridge for MC 1.18.2–26.1.2. */
public final class ClientEnv {
    private ClientEnv() {}

    public static InputConstants.Type keyboardType() { return InputConstants.Type.KEYSYM; }
    public static Screen currentScreen(Minecraft client) { return client.screen; }
    public static void setScreen(Minecraft client, Screen screen) { client.setScreen(screen); }
    public static boolean isHudHidden(Minecraft client) { return client.options.hideGui; }
    public static Camera mainCamera(Minecraft client) { return client.gameRenderer.getMainCamera(); }
    public static float swingProgress(Player player, float tickDelta) { return player.getAttackAnim(tickDelta); }
}
