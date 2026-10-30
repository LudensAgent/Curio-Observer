package dev.curio.observer;

import dev.curio.observer.config.ObserverConfig;
import dev.curio.observer.config.ObserverConfigScreen;
import dev.curio.observer.hud.BotHudRenderer;
import dev.curio.observer.hud.InventoryOverlay;
import dev.curio.observer.hud.ViewModeOverlay;
import dev.curio.observer.rcon.RconLink;
import dev.curio.observer.state.BotState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Path;

/**
 * Client-only spectator HUD. No packets are sent to the Minecraft server; the HUD, camera and lighting are
 * toggled by keybindings. When enabled, an optional RCON channel reads the spectated player's full inventory
 * and hunger/experience from the server (server-side RCON must be enabled).
 */
public final class CurioObserverClient implements ClientModInitializer {
    private static final BotHudRenderer HUD = new BotHudRenderer();
    private static final InventoryOverlay INVENTORY = new InventoryOverlay();
    private static final ViewModeOverlay VIEW_MODE = new ViewModeOverlay();
    private static java.lang.reflect.Method irisInstance;
    private static java.lang.reflect.Method irisShaderInUse;
    private static boolean irisChecked;
    private static ObserverConfig config = new ObserverConfig();
    private static Path configPath;
    private static RconLink rcon;
    private static volatile String rconTarget;
    private static KeyBinding hudKey;
    private static KeyBinding brightenKey;
    private static KeyBinding cameraKey;
    private static KeyBinding configKey;

    @Override
    public void onInitializeClient() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("curio-observer.json");
        config = ObserverConfig.load(configPath);
        hudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.curio-observer.hud", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.curio-observer"));
        brightenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.curio-observer.brighten", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.curio-observer"));
        cameraKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.curio-observer.camera", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.curio-observer"));
        configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.curio-observer.config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.curio-observer"));
        restartRcon();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (rcon != null) rcon.close();
            ObserverConfig.awaitPendingSaves();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (hudKey.wasPressed()) { config.hudEnabled = !config.hudEnabled; config.saveAsync(configPath); }
            while (brightenKey.wasPressed()) { config.brighten = !Boolean.TRUE.equals(config.brighten); config.saveAsync(configPath); }
            while (cameraKey.wasPressed()) { config.autoCamera = !Boolean.TRUE.equals(config.autoCamera); config.saveAsync(configPath); }
            while (configKey.wasPressed()) {
                client.setScreen(new ObserverConfigScreen(client.currentScreen, config, CurioObserverClient::saveAndRestartRcon));
            }
            PlayerEntity target = spectatedPlayer();
            rconTarget = target == null ? null : target.getGameProfile().getName();
        });
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            BotState state = snapshot();
            MinecraftClient client = MinecraftClient.getInstance();
            if (!isObserving(state) || client.options.hudHidden) return;
            if (config.visible("vitals")) HUD.render(context, state, tickDelta, config.hudScale, config.panel("vitals"));
            ObserverConfig.PanelConfig inventory = config.panel("inventory");
            if (inventory.visible) INVENTORY.render(context, state, config.hudScale, inventory);
            if (config.visible("view")) VIEW_MODE.render(context, viewStatus(), config.infoHudScale(), config.panel("view"), null);
        });
    }

    private static void saveAndRestartRcon() {
        config.saveAsync(configPath);
        restartRcon();
    }

    private static void restartRcon() {
        RconLink previous = rcon;
        rcon = createRcon();
        if (previous != null) previous.close();
        if (rcon != null) rcon.start();
    }

    private static RconLink createRcon() {
        if (!config.rconEnabled || config.rconPassword == null || config.rconPassword.isBlank()) return null;
        return new RconLink(config.rconHost, config.rconPort, config.rconPassword,
                config.rconPollSeconds * 1000, () -> rconTarget);
    }

    public static BotState snapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return BotState.empty();
        PlayerEntity player = spectatedPlayer();
        if (player == null) return BotState.empty();
        return BotState.ofSpectator(player, rcon == null ? null : rcon.snapshot());
    }

    private static PlayerEntity spectatedPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;
        if (client.getCameraEntity() instanceof PlayerEntity player && player != client.player) return player;
        return null;
    }

    public static boolean isObserving(BotState state) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!config.hudEnabled || client.world == null) return false;
        return state.renderable();
    }

    public static boolean replacesSpectatorHud() { return isObserving(snapshot()); }

    /** True while this client spectates another player; camera and lighting features apply only then. */
    private static boolean spectatingPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = spectatedPlayer();
        return player != null && client.player != null && client.player.isSpectator()
                && player.getWorld() == client.world && player.isAlive() && !player.isRemoved() && !player.isSleeping();
    }

    /** Adaptive camera: independent of the HUD switch and of the lighting switch. */
    public static boolean cameraActive() { return Boolean.TRUE.equals(config.autoCamera) && spectatingPlayer(); }

    /** Brighten dark areas: independent of the HUD switch and of the camera switch. */
    public static boolean brightenActive() { return Boolean.TRUE.equals(config.brighten) && spectatingPlayer(); }

    public static double gamma() { return config.gamma; }

    private static ViewModeOverlay.Status viewStatus() {
        boolean camera = cameraActive();
        return new ViewModeOverlay.Status(Boolean.TRUE.equals(config.autoCamera), camera,
                camera && MinecraftClient.getInstance().gameRenderer.getCamera().isThirdPerson(),
                Boolean.TRUE.equals(config.brighten), brightenActive(), config.gamma, shaderPackInUse());
    }

    /** Iris shader packs light the world themselves and usually ignore the lightmap; read via reflection (optional mod). */
    private static boolean shaderPackInUse() {
        if (!irisChecked) {
            irisChecked = true;
            if (FabricLoader.getInstance().isModLoaded("iris")) {
                try {
                    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    irisInstance = api.getMethod("getInstance");
                    irisShaderInUse = api.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException | LinkageError ignored) { irisInstance = null; }
            }
        }
        if (irisInstance == null) return false;
        try { return Boolean.TRUE.equals(irisShaderInUse.invoke(irisInstance.invoke(null))); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    public static double broadcastDistance() { return config.broadcastDistance; }
    public static double broadcastSmoothing() { return config.broadcastSmoothing; }

    public static boolean firstPersonView() {
        MinecraftClient client = MinecraftClient.getInstance();
        return cameraActive() ? !client.gameRenderer.getCamera().isThirdPerson()
                : client.options.getPerspective().isFirstPerson();
    }

    public static boolean shouldRenderHands(BotState state) {
        return config.visible("hands") && isObserving(state) && state.renderable();
    }
}
