package dev.curio.observer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.curio.observer.model.Protocol;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.LoggerFactory;

/**
 * Configuration I/O occurs at initialization or on a background worker, never in rendering.
 * Keybindings flip these switches at runtime; this file keeps the last values for offline use.
 */
public final class ObserverConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean hudEnabled = true;
    public double hudScale = 1.0;
    /** Text cards have their own multiplier so compact information does not shrink item slots or vital bars. */
    public double infoScale = 0.7;
    /** Visibility and position of each HUD element, keyed by {@link Protocol#HUD_PANELS}. */
    public Map<String, PanelConfig> panels = new LinkedHashMap<>();
    /** Adaptive third/first-person camera while spectating. */
    public Boolean autoCamera;
    public double broadcastDistance = 3.0;
    /** Camera rotation's smoothing time in seconds; zero follows the target immediately. */
    public double broadcastSmoothing = 0.18;
    /** Raise dark areas by replacing the lightmap gamma while spectating. */
    public Boolean brighten;
    /** Vanilla gamma scale: 0 = Moody, 1 = Bright, larger values brighten caves further. */
    public double gamma = 3.0;
    /** Pull the full inventory and hunger/experience from the server over RCON (server must enable RCON). */
    public boolean rconEnabled = false;
    public String rconHost = "127.0.0.1";
    public int rconPort = 25575;
    public String rconPassword = "";
    public int rconPollSeconds = 1;
    /** Pre-0.3 settings, read once for migration and then dropped. */
    public Boolean showHands;
    public Boolean broadcastEnabled;
    private static CompletableFuture<Void> pendingSave = CompletableFuture.completedFuture(null);

    public static final class PanelConfig {
        public boolean visible = true;
        /** Top-left corner as a fraction of the screen; null keeps the default place. */
        public Double x;
        public Double y;

        public PanelConfig() {}
        PanelConfig(boolean visible, Double x, Double y) { this.visible = visible; this.x = x; this.y = y; }
    }

    public static ObserverConfig load(Path path) {
        ObserverConfig result = new ObserverConfig();
        if (Files.exists(path)) {
            try {
                ObserverConfig parsed = GSON.fromJson(Files.readString(path), ObserverConfig.class);
                if (parsed != null) result = parsed;
            } catch (IOException | RuntimeException exception) {
                LoggerFactory.getLogger("curio-observer").warn("Cannot read observer config; using defaults", exception);
            }
        }
        result.normalize();
        if (!Files.exists(path)) result.saveAsync(path);
        return result;
    }

    public void normalize() {
        hudScale = Double.isFinite(hudScale) ? Math.max(0.5, Math.min(3, hudScale)) : 1.0;
        infoScale = Double.isFinite(infoScale) ? Math.max(0.5, Math.min(1.5, infoScale)) : 0.7;
        broadcastDistance = Double.isFinite(broadcastDistance) ? Math.max(1.5, Math.min(6, broadcastDistance)) : 3.0;
        broadcastSmoothing = Double.isFinite(broadcastSmoothing) ? Math.max(0, Math.min(0.8, broadcastSmoothing)) : 0.18;
        gamma = Double.isFinite(gamma) ? Math.max(0, Math.min(15, gamma)) : 3.0;
        rconPort = rconPort > 0 && rconPort <= 65535 ? rconPort : 25575;
        rconPollSeconds = Math.max(1, Math.min(60, rconPollSeconds <= 0 ? 1 : rconPollSeconds));
        if (rconHost == null) rconHost = "127.0.0.1";
        // The old single broadcast switch controlled both the camera and the lighting.
        if (autoCamera == null) autoCamera = Boolean.TRUE.equals(broadcastEnabled);
        if (brighten == null) brighten = Boolean.TRUE.equals(broadcastEnabled);
        broadcastEnabled = null;
        Map<String, PanelConfig> normalized = new LinkedHashMap<>();
        for (String name : Protocol.HUD_PANELS) {
            PanelConfig panel = panels == null ? null : panels.get(name);
            if (panel == null) panel = new PanelConfig();
            panel.x = fraction(panel.x);
            panel.y = fraction(panel.y);
            normalized.put(name, panel);
        }
        if (showHands != null) normalized.get("hands").visible = showHands;
        showHands = null;
        panels = normalized;
    }

    private static Double fraction(Double value) {
        return value == null || !Double.isFinite(value) ? null : Math.max(0, Math.min(1, value));
    }

    public PanelConfig panel(String name) {
        return panels.computeIfAbsent(name, ignored -> new PanelConfig());
    }

    public boolean visible(String name) { return panel(name).visible; }

    public double infoHudScale() { return Math.max(0.25, Math.min(3, hudScale * infoScale)); }

    public synchronized void saveAsync(Path path) {
        String json = GSON.toJson(this);
        synchronized (ObserverConfig.class) {
            pendingSave = pendingSave.handle((ignored, failure) -> null).thenRunAsync(() -> {
                try {
                    Files.createDirectories(path.getParent());
                    Files.writeString(path, json, StandardCharsets.UTF_8);
                } catch (IOException exception) {
                    LoggerFactory.getLogger("curio-observer").warn("Cannot save observer config", exception);
                }
            });
        }
    }

    /** Called on orderly client shutdown, outside render callbacks. */
    public static void awaitPendingSaves() {
        CompletableFuture<Void> pending;
        synchronized (ObserverConfig.class) { pending = pendingSave; }
        pending.join();
    }
}
