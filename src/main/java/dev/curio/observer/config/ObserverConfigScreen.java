package dev.curio.observer.config;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** In-game connection settings, feature toggles and HUD panels. */
public final class ObserverConfigScreen extends Screen {
    private static final int PAD = 10;
    private static final int ROW = 24;
    private static final int LABEL_W = 138;
    private static final int FIELD_W = 150;
    private static final int CONTENT_TOP = 32;
    private static final int FOOTER_HEIGHT = 56;

    private final Screen parent;
    private final ObserverConfig config;
    private final Runnable onSave;
    private final Supplier<Text> connectionStatus;

    private final List<ClickableWidget> widgets = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final List<Runnable> refreshLabels = new ArrayList<>();
    private final Map<String, String> editedText = new HashMap<>();
    private TextFieldWidget feedUrlField, hostField, portField, passwordField, pollField, hudScaleField, gammaField;
    private TextFieldWidget targetField, observeIntervalField;
    private ButtonWidget doneButton, upButton, downButton;
    private int scroll;

    private boolean feedEnabled, rconEnabled, hudEnabled, autoCamera, brighten;
    private boolean vitals, inventory, containers, view, hands;
    private boolean autoObserve;
    private String language;

    public ObserverConfigScreen(Screen parent, ObserverConfig config, Runnable onSave, Supplier<Text> connectionStatus) {
        super(ObserverText.text("curio-observer.config.title"));
        this.parent = parent;
        this.config = config;
        this.onSave = onSave;
        this.connectionStatus = connectionStatus;
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

    @Override
    protected void init() {
        widgets.clear();
        labels.clear();
        refreshLabels.clear();

        ButtonWidget languageButton = ButtonWidget.builder(languageLabel(), button -> {
            language = switch (language) { case "auto" -> "en_us"; case "en_us" -> "zh_cn"; default -> "auto"; };
            ObserverText.setLanguage(language);
            refreshLabels.forEach(Runnable::run);
        }).dimensions(0, 0, 100, 20).build();
        widgets.add(languageButton);
        labels.add(null);
        refreshLabels.add(() -> languageButton.setMessage(languageLabel()));

        targetField = textField("default_target", 16, "[A-Za-z0-9_]*", config.defaultTarget);
        toggle("auto_observe", () -> autoObserve, value -> autoObserve = value);
        observeIntervalField = textField("observe_interval", 3, "[0-9]*", Integer.toString(config.autoObserveSeconds));
        toggle("feed_enabled", () -> feedEnabled, v -> feedEnabled = v);
        feedUrlField = textField("feed_url", 2048, null, config.feedUrl);
        toggle("rcon_enabled", () -> rconEnabled, v -> rconEnabled = v);
        hostField = textField("rcon_host", 256, null, config.rconHost);
        portField = textField("rcon_port", 5, "[0-9]*", Integer.toString(config.rconPort));
        passwordField = textField("rcon_password", 256, null, config.rconPassword == null ? "" : config.rconPassword);
        pollField = textField("rcon_poll", 2, "[0-9]*", Integer.toString(config.rconPollSeconds));

        toggle("hud_enabled", () -> hudEnabled, v -> hudEnabled = v);
        hudScaleField = textField("hud_scale", 5, "[0-9.]*", Double.toString(config.hudScale));
        gammaField = textField("gamma", 5, "[0-9.]*", Double.toString(config.gamma));

        toggle("auto_camera", () -> autoCamera, v -> autoCamera = v);
        toggle("brighten", () -> brighten, v -> brighten = v);

        toggle("show_vitals", () -> vitals, v -> vitals = v);
        toggle("show_inventory", () -> inventory, v -> inventory = v);
        toggle("show_containers", () -> containers, v -> containers = v);
        toggle("show_view", () -> view, v -> view = v);
        toggle("show_hands", () -> hands, v -> hands = v);

        // Content is drawn and clipped manually so it stays inside the scroll region; the bottom
        // buttons remain drawable children and are rendered by the parent on top.
        for (ClickableWidget widget : widgets) addSelectableChild(widget);

        doneButton = ButtonWidget.builder(ObserverText.text("curio-observer.config.done"), b -> done())
                .dimensions(0, 0, 120, 20).build();
        refreshLabels.add(() -> doneButton.setMessage(ObserverText.text("curio-observer.config.done")));
        upButton = ButtonWidget.builder(Text.literal("▲"), b -> scrollBy(-ROW * 3)).dimensions(0, 0, 40, 20).build();
        downButton = ButtonWidget.builder(Text.literal("▼"), b -> scrollBy(ROW * 3)).dimensions(0, 0, 40, 20).build();
        addDrawableChild(doneButton);
        addDrawableChild(upButton);
        addDrawableChild(downButton);

        reposition();
    }

    private void toggle(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        ButtonWidget button = ButtonWidget.builder(toggleLabel(label, getter.getAsBoolean()), b -> {
            setter.accept(!getter.getAsBoolean());
            b.setMessage(toggleLabel(label, getter.getAsBoolean()));
        }).dimensions(0, 0, 100, 20).build();
        widgets.add(button);
        labels.add(null);
        refreshLabels.add(() -> button.setMessage(toggleLabel(label, getter.getAsBoolean())));
    }

    private TextFieldWidget textField(String label, int maxLength, String predicate, String initial) {
        TextFieldWidget field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, 0, 0, FIELD_W, 20,
                ObserverText.text("curio-observer.config." + label));
        field.setMaxLength(maxLength);
        if (predicate != null) field.setTextPredicate(value -> value.matches(predicate));
        field.setText(editedText.getOrDefault(label, initial));
        field.setChangedListener(value -> editedText.put(label, value));
        widgets.add(field);
        labels.add(label);
        refreshLabels.add(() -> field.setMessage(ObserverText.text("curio-observer.config." + label)));
        return field;
    }

    private static Text toggleLabel(String label, boolean on) {
        return ObserverText.text("curio-observer.config.toggle", ObserverText.text("curio-observer.config." + label),
                ObserverText.text(on ? "curio-observer.config.on" : "curio-observer.config.off"));
    }

    private Text languageLabel() {
        return ObserverText.text("curio-observer.config.language", ObserverText.text("curio-observer.config.language." + language));
    }

    private void reposition() {
        int contentHeight = widgets.size() * ROW + CONTENT_TOP;
        int maxScroll = Math.max(0, contentHeight - (height - FOOTER_HEIGHT));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int y = CONTENT_TOP - scroll;
        for (int i = 0; i < widgets.size(); i++) {
            ClickableWidget widget = widgets.get(i);
            if (labels.get(i) == null) { widget.setX(PAD); widget.setWidth(width - PAD * 2); }
            else { widget.setX(PAD + LABEL_W); widget.setWidth(Math.max(40, width - PAD * 2 - LABEL_W)); }
            widget.setY(y);
            y += ROW;
        }
        doneButton.setX(width / 2 - 60);
        doneButton.setY(height - 24);
        upButton.setX(width - 100);
        upButton.setY(height - 24);
        downButton.setX(width - 56);
        downButton.setY(height - 24);
    }

    private void scrollBy(int amount) {
        scroll += amount;
        reposition();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Bottom controls sit above the scrollable content and must win the click.
        if (click(doneButton, mouseX, mouseY, button)
                || click(upButton, mouseX, mouseY, button)
                || click(downButton, mouseX, mouseY, button)) return true;
        int bottom = height - FOOTER_HEIGHT;
        for (ClickableWidget widget : widgets) {
            if (widget.getY() >= bottom || widget.getY() + widget.getHeight() <= CONTENT_TOP) continue;
            if (click(widget, mouseX, mouseY, button)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scrollBy((int) (-amount * ROW * 3));
        return true;
    }

    private boolean click(ClickableWidget widget, double mouseX, double mouseY, int button) {
        if (!widget.mouseClicked(mouseX, mouseY, button)) return false;
        setFocused(widget);
        if (button == 0) setDragging(true);
        return true;
    }

    private void done() {
        config.defaultTarget = targetField.getText();
        config.autoObserve = autoObserve;
        config.autoObserveSeconds = parseInt(observeIntervalField.getText(), 5);
        config.language = language;
        config.feedEnabled = feedEnabled;
        config.feedUrl = feedUrlField.getText().trim();
        config.rconEnabled = rconEnabled;
        config.hudEnabled = hudEnabled;
        config.autoCamera = autoCamera;
        config.brighten = brighten;
        config.panel("vitals").visible = vitals;
        config.panel("inventory").visible = inventory;
        config.panel("containers").visible = containers;
        config.panel("view").visible = view;
        config.panel("hands").visible = hands;
        config.rconHost = hostField.getText().trim();
        config.rconPort = parseInt(portField.getText(), 25575);
        config.rconPassword = passwordField.getText();
        config.rconPollSeconds = parseInt(pollField.getText(), 1);
        config.hudScale = parseDouble(hudScaleField.getText(), 1.0);
        config.gamma = parseDouble(gammaField.getText(), 3.0);
        config.normalize();
        ObserverText.setLanguage(config.language);
        onSave.run();
        MinecraftClient.getInstance().setScreen(parent);
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException exception) { return fallback; }
    }

    private static double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value.trim()); } catch (NumberFormatException exception) { return fallback; }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        MinecraftClient client = MinecraftClient.getInstance();
        context.drawCenteredTextWithShadow(client.textRenderer, getTitle(), width / 2, 4, 0xFFFFFFFF);
        context.drawText(client.textRenderer, client.textRenderer.trimToWidth(connectionStatus.get().getString(), width - PAD * 2),
                PAD, 18, 0xFFE0E0E0, true);
        int bottom = height - FOOTER_HEIGHT;
        // Content is clipped to the scroll region so it cannot draw over the title or the bottom buttons.
        context.enableScissor(0, CONTENT_TOP, width, bottom);
        for (int i = 0; i < widgets.size(); i++) {
            ClickableWidget widget = widgets.get(i);
            String label = labels.get(i);
            if (label != null) {
                String caption = ObserverText.text("curio-observer.config." + label).getString();
                context.drawText(client.textRenderer, client.textRenderer.trimToWidth(caption, LABEL_W - 4),
                        PAD, widget.getY() + 6, 0xFFA0A0A0, true);
            }
            widget.render(context, mouseX, mouseY, delta);
        }
        context.disableScissor();
        if (autoObserve) context.drawText(client.textRenderer,
                client.textRenderer.trimToWidth(ObserverText.text("curio-observer.config.observe_hint").getString(), width - PAD * 2),
                PAD, height - 39, 0xFFFFCC77, true);
    }

    @Override
    public Text getTitle() { return ObserverText.text("curio-observer.config.title"); }

    @Override
    public void removed() { ObserverText.setLanguage(config.language); }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
