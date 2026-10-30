package dev.curio.observer.smoke;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Loading targets through Fabric's JUnit launcher verifies real mixin application without an OpenGL window. */
class ClientMixinTest {
    @Test void clientMixinsApplyToMinecraft1201() throws ClassNotFoundException {
        assertInjected("net.minecraft.client.gui.hud.SpectatorHud", "curio$hideMenu");
        assertInjected("net.minecraft.client.gui.hud.SpectatorHud", "curio$hideSpectatorPrompt");
        assertInjected("net.minecraft.client.gui.hud.InGameHud", "curio$keepBotCrosshair");
        assertInjected("net.minecraft.client.render.GameRenderer", "curio$renderBotHands");
        assertInjected("net.minecraft.client.render.WorldRenderer", "curio$terrainX");
        assertInjected("net.minecraft.client.render.WorldRenderer", "curio$terrainY");
        assertInjected("net.minecraft.client.render.WorldRenderer", "curio$terrainZ");
        assertInjected("net.minecraft.client.render.Camera", "curio$frameBroadcast");
        assertInjected("net.minecraft.client.render.Camera", "curio$resetBroadcast");
        assertInjected("net.minecraft.client.render.GameRenderer", "curio$steadyWalk");
        assertInjected("net.minecraft.client.render.GameRenderer", "curio$steadyDamage");
        assertInjected("net.minecraft.client.render.LightmapTextureManager", "curio$prepareBroadcastLight");
        assertInjected("net.minecraft.client.render.LightmapTextureManager", "curio$broadcastGamma");
        assertInjected("net.minecraft.client.render.GameRenderer", "curio$shaderBrightness");
    }

    private void assertInjected(String target, String method) throws ClassNotFoundException {
        Class<?> type = Class.forName(target, false, getClass().getClassLoader());
        assertTrue(Arrays.stream(type.getDeclaredMethods()).anyMatch(candidate -> candidate.getName().contains(method)),
                () -> "Fabric did not apply " + method + " to " + target);
    }
}
