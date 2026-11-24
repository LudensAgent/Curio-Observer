package dev.curio.observer.smoke;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies real Fabric Loader Mixin application against the target Minecraft version without OpenGL. */
class ClientMixinTest {
    @Test void clientMixinsApplyToTargetMinecraft() throws ClassNotFoundException {
        String prop = System.getProperty("curio.expectedMixins", "");
        Set<String> enabled = prop.isBlank() ? Set.of() : Set.of(prop.split(","));
        if (enabled.contains("SpectatorHudMixin")) {
            assertInjected("net.minecraft.client.gui.components.spectator.SpectatorGui", "curio$hideMenu");
        }
        if (enabled.contains("InGameHudMixin")) {
            String target = classExists("net.minecraft.client.gui.Hud") && !hasMethod("net.minecraft.client.gui.Gui", "curio$keepBotCrosshair")
                    ? "net.minecraft.client.gui.Hud" : "net.minecraft.client.gui.Gui";
            assertInjected(target, "curio$keepBotCrosshair");
        }
        if (enabled.contains("HeldItemRendererMixin")) {
            assertInjected("net.minecraft.client.renderer.GameRenderer", "curio$renderBotHands");
        }
        if (enabled.contains("WorldRendererMixin")) {
            assertInjected("net.minecraft.client.renderer.LevelRenderer", "curio$terrainX");
            assertInjected("net.minecraft.client.renderer.LevelRenderer", "curio$terrainY");
            assertInjected("net.minecraft.client.renderer.LevelRenderer", "curio$terrainZ");
        }
        if (enabled.contains("BroadcastCameraMixin")) {
            assertInjected("net.minecraft.client.Camera", "curio$frameBroadcast");
            assertInjected("net.minecraft.client.Camera", "curio$resetBroadcast");
        }
        if (enabled.contains("BroadcastStabilityMixin")) {
            assertInjected("net.minecraft.client.renderer.GameRenderer", "curio$steadyWalk");
            assertInjected("net.minecraft.client.renderer.GameRenderer", "curio$steadyDamage");
        }
        if (enabled.contains("BroadcastLightmapMixin")) {
            String target = classExists("net.minecraft.client.renderer.LightmapRenderStateExtractor")
                    ? "net.minecraft.client.renderer.LightmapRenderStateExtractor"
                    : "net.minecraft.client.renderer.LightTexture";
            assertInjected(target, "curio$prepareBroadcastLight");
        }
        if (enabled.contains("BroadcastNightVisionMixin")) {
            assertInjected("net.minecraft.client.renderer.GameRenderer", "curio$shaderBrightness");
        }
    }

    private boolean classExists(String name) {
        try { Class.forName(name, false, getClass().getClassLoader()); return true; }
        catch (ClassNotFoundException ignored) { return false; }
    }

    private boolean hasMethod(String target, String method) {
        try {
            Class<?> type = Class.forName(target, false, getClass().getClassLoader());
            return Arrays.stream(type.getDeclaredMethods()).anyMatch(c -> c.getName().contains(method));
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private void assertInjected(String target, String method) throws ClassNotFoundException {
        Class<?> type = Class.forName(target, false, getClass().getClassLoader());
        assertTrue(Arrays.stream(type.getDeclaredMethods()).anyMatch(candidate -> candidate.getName().contains(method)),
                () -> "Fabric did not apply " + method + " to " + target);
    }
}
