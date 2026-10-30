package dev.curio.observer.smoke;

import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.launch.knot.Knot;

/** Exercises the released JAR and actual renderer mods without opening an OpenGL window. */
public final class ProductionMixinSmoke {
    private ProductionMixinSmoke() {}

    public static void main(String[] args) throws Exception {
        boolean withRendererMods = Boolean.parseBoolean(args[1]);
        Knot knot = new Knot(EnvType.CLIENT);
        ClassLoader runtime = knot.init(new String[] { "--gameDir", args[0], "--version", "1.20.1" });

        for (String id : new String[] { "sodium", "iris", "indium" }) {
            if (FabricLoader.getInstance().isModLoaded(id) != withRendererMods) {
                throw new AssertionError("Unexpected " + id + " discovery; renderer-mod runtime=" + withRendererMods);
            }
            if (withRendererMods) {
                System.out.println("RENDERER_MOD " + id + " " + FabricLoader.getInstance()
                        .getModContainer(id).orElseThrow().getMetadata().getVersion().getFriendlyString());
            }
        }

        // Loading this target reproduces the original setupTerrain overwrite/redirect conflict.
        Class<?> worldRenderer = Class.forName("net.minecraft.class_761", false, runtime);
        for (String method : new String[] { "curio$terrainX", "curio$terrainY", "curio$terrainZ" }) {
            assertMethod(worldRenderer, method, !withRendererMods);
        }
        if (withRendererMods) assertMethod(worldRenderer, "sodium$getWorldRenderer", true);

        assertInjected(runtime, "net.minecraft.class_365", "curio$hideMenu", "curio$hideSpectatorPrompt");
        assertInjected(runtime, "net.minecraft.class_329", "curio$keepBotCrosshair");
        assertInjected(runtime, "net.minecraft.class_757", "curio$renderBotHands");
        assertInjected(runtime, "net.minecraft.class_4184", "curio$frameBroadcast", "curio$resetBroadcast");
        assertInjected(runtime, "net.minecraft.class_757", "curio$steadyWalk", "curio$steadyDamage", "curio$shaderBrightness");
        assertInjected(runtime, "net.minecraft.class_765", "curio$prepareBroadcastLight", "curio$broadcastGamma");
        System.out.println("PRODUCTION_MIXIN_OK: " + (withRendererMods
                ? "Sodium terrain renderer retained; remaining Curio hooks applied with Iris and Indium"
                : "all Curio HUD, terrain and broadcast hooks applied to vanilla Minecraft"));
    }

    private static void assertInjected(ClassLoader runtime, String name, String... methods) throws Exception {
        Class<?> target = Class.forName(name, false, runtime);
        for (String method : methods) assertMethod(target, method, true);
    }

    private static void assertMethod(Class<?> target, String name, boolean expected) {
        boolean injected = Arrays.stream(target.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains(name));
        if (injected != expected) {
            throw new AssertionError((expected ? "Missing " : "Unexpected ") + name + " in " + target.getName());
        }
    }
}
