package dev.curio.observer.config;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;

/** Shared form state and parsing for the in-game observer settings screen across all MC versions. */
public final class ObserverConfigScreenState {
    public static final int PAD = 10;
    public static final int ROW = 24;
    public static final int LABEL_W = 138;
    public static final int FIELD_W = 150;
    public static final int CONTENT_TOP = 32;
    public static final int FOOTER_HEIGHT = 56;

    public final ObserverConfig config;
    public final Runnable onSave;
    public final Map<String, String> editedText = new HashMap<>();
    public int scroll;

    public boolean feedEnabled, rconEnabled, hudEnabled, autoCamera, brighten;
    public boolean vitals, inventory, containers, view, hands;
    public boolean autoObserve;
    public String language;

    public ObserverConfigScreenState(ObserverConfig config, Runnable onSave) {
        this.config = config;
        this.onSave = onSave;
        this.feedEnabled = config.feedEnabled;
        this.rconEnabled = config.rconEnabled;
        this.hudEnabled = config.hudEnabled;
        this.autoCamera = Boolean.TRUE.equals(config.autoCamera);
        this.brighten = Boolean.TRUE.equals(config.brighten);
        this.vitals = config.visible("vitals");
        this.inventory = config.visible("inventory");
        this.containers = config.visible("containers");
        this.view = config.visible("view");
        this.hands = config.visible("hands");
        this.autoObserve = config.autoObserve;
        this.language = config.language;
    }

    public void cycleLanguage() {
        language = switch (language) {
            case "auto" -> "en_us";
            case "en_us" -> "zh_cn";
            default -> "auto";
        };
        ObserverText.setLanguage(language);
    }

    public Component languageLabel() {
        return ObserverText.text("curio-observer.config.language",
                ObserverText.text("curio-observer.config.language." + language));
    }

    public static Component toggleLabel(String label, boolean on) {
        return ObserverText.text("curio-observer.config.toggle",
                ObserverText.text("curio-observer.config." + label),
                ObserverText.text(on ? "curio-observer.config.on" : "curio-observer.config.off"));
    }

    public void save(String target, String observeInterval, String feedUrl, String rconHost, String rconPort,
                     String rconPassword, String rconPoll, String hudScale, String gamma) {
        config.defaultTarget = target;
        config.autoObserve = autoObserve;
        config.autoObserveSeconds = parseInt(observeInterval, 5);
        config.language = language;
        config.feedEnabled = feedEnabled;
        config.feedUrl = feedUrl.trim();
        config.rconEnabled = rconEnabled;
        config.hudEnabled = hudEnabled;
        config.autoCamera = autoCamera;
        config.brighten = brighten;
        config.panel("vitals").visible = vitals;
        config.panel("inventory").visible = inventory;
        config.panel("containers").visible = containers;
        config.panel("view").visible = view;
        config.panel("hands").visible = hands;
        config.rconHost = rconHost.trim();
        config.rconPort = parseInt(rconPort, 25575);
        config.rconPassword = rconPassword;
        config.rconPollSeconds = parseInt(rconPoll, 1);
        config.hudScale = parseDouble(hudScale, 1.0);
        config.gamma = parseDouble(gamma, 3.0);
        config.normalize();
        ObserverText.setLanguage(config.language);
        onSave.run();
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException exception) { return fallback; }
    }

    private static double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value.trim()); } catch (NumberFormatException exception) { return fallback; }
    }
}
